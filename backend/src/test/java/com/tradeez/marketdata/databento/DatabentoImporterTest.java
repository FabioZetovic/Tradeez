package com.tradeez.marketdata.databento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.tradeez.AbstractIntegrationTest;
import com.tradeez.identity.AppUserRepository;
import com.tradeez.identity.CookieService;
import com.tradeez.identity.RefreshTokenRepository;
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.BarSeries;
import com.tradeez.marketdata.DataClass;
import com.tradeez.marketdata.Instrument;
import com.tradeez.marketdata.MarketDataService;
import com.tradeez.marketdata.Timeframe;
import com.tradeez.marketdata.databento.DatabentoImporter.Outcome;

/**
 * DEC-040 — importing real bars, against {@link FakeDatabento} and a real PostgreSQL.
 *
 * <p>The money rules get the most attention: every download is priced first, nothing is fetched
 * past a cap, and what was spent is recorded in the database.
 */
class DatabentoImporterTest extends AbstractIntegrationTest {

	private static final String KEY = "db-importer-test-key";
	private static final FakeDatabento FAKE = new FakeDatabento();

	/** Two hours across a month boundary: two monthly chunks of sixty one-minute bars each. */
	private static final Instant FROM = Instant.parse("2024-01-31T23:00:00Z");
	private static final Instant MID = Instant.parse("2024-02-01T00:00:00Z");
	private static final Instant TO = Instant.parse("2024-02-01T01:00:00Z");

	@DynamicPropertySource
	static void databento(DynamicPropertyRegistry registry) {
		registry.add("databento.base-url", FAKE::url);
		registry.add("databento.api-key", () -> KEY);
		registry.add("databento.max-cost-per-request-usd", () -> "5.00");
		registry.add("databento.max-total-cost-usd", () -> "100.00");
	}

	@AfterAll
	static void stopFake() {
		FAKE.close();
	}

	@Autowired
	DatabentoImporter importer;

	@Autowired
	ImportLedger ledger;

	@Autowired
	MarketDataService marketData;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	MockMvc mockMvc;

	@Autowired
	AppUserRepository users;

	@Autowired
	RefreshTokenRepository refreshTokens;

	private Instrument nq;

	@BeforeEach
	void cleanSlate() {
		jdbc.update("DELETE FROM market_bar");
		jdbc.update("DELETE FROM market_data_import");
		FAKE.reset();
		nq = marketData.requireInstrument("NQ");
	}

	private long storedBars() {
		return jdbc.queryForObject("SELECT count(*) FROM market_bar", Long.class);
	}

	private List<String> ledgerStatuses() {
		return jdbc.queryForList("SELECT status FROM market_data_import ORDER BY range_from",
			String.class);
	}

	@Test
	@DisplayName("a dry run prices every chunk and downloads nothing")
	void dryRun() {
		DatabentoImporter.Report report = importer.importRange(nq, "1m", FROM, TO, true);

		assertThat(report.chunks()).extracting(DatabentoImporter.ChunkReport::outcome)
			.containsExactly(Outcome.ESTIMATED, Outcome.ESTIMATED);
		assertThat(report.costUsd()).isEqualByComparingTo("0.10");
		assertThat(FAKE.costRequests).hasSize(2);
		assertThat(FAKE.barRequests).isEmpty();
		assertThat(storedBars()).isZero();
		assertThat(ledgerStatuses()).isEmpty();
	}

	@Test
	@DisplayName("imports whole ticks, the contract each bar came from, and records what it cost")
	void imports() {
		DatabentoImporter.Report report = importer.importRange(nq, "1m", FROM, TO, false);

		assertThat(report.barsStored()).isEqualTo(120);
		assertThat(report.chunks()).extracting(DatabentoImporter.ChunkReport::outcome)
			.containsExactly(Outcome.IMPORTED, Outcome.IMPORTED);
		assertThat(FAKE.barRequests.getFirst())
			.containsEntry("symbols", "NQ.v.0")
			.containsEntry("schema", "ohlcv-1m")
			.containsEntry("stype_in", "continuous");

		// The first bar: fixed-point price / (0.25 * 1e9) = ticks, exactly.
		long second = FROM.getEpochSecond();
		long base = 80_000 + ((second / 60) % 1000);
		var row = jdbc.queryForMap("""
			SELECT open_ticks, high_ticks, low_ticks, close_ticks, contract_id FROM market_bar
			WHERE open_time = ?""", Timestamp.from(FROM));
		assertThat(row).containsEntry("open_ticks", base)
			.containsEntry("high_ticks", base + 3)
			.containsEntry("low_ticks", base - 2)
			.containsEntry("close_ticks", base + 1)
			.containsEntry("contract_id", 111L);
		// The roll halfway through each chunk is kept, not smoothed over (DEC-024).
		assertThat(jdbc.queryForList("SELECT DISTINCT contract_id FROM market_bar ORDER BY 1",
			Long.class)).containsExactly(111L, 222L);

		assertThat(ledgerStatuses()).containsExactly("DONE", "DONE");
		assertThat(ledger.spentUsd()).isEqualByComparingTo("0.10");
	}

	@Test
	@DisplayName("running the same import again downloads nothing it already has")
	void resumable() {
		importer.importRange(nq, "1m", FROM, TO, false);
		FAKE.costRequests.clear();
		FAKE.barRequests.clear();

		DatabentoImporter.Report again = importer.importRange(nq, "1m", FROM, TO, false);

		assertThat(again.chunks()).extracting(DatabentoImporter.ChunkReport::outcome)
			.containsExactly(Outcome.ALREADY_IMPORTED, Outcome.ALREADY_IMPORTED);
		assertThat(FAKE.costRequests).isEmpty();
		assertThat(FAKE.barRequests).isEmpty();
		assertThat(storedBars()).isEqualTo(120);
	}

	@Test
	@DisplayName("refuses a chunk priced over the per-request cap, before downloading anything")
	void perRequestCap() {
		FAKE.cost = form -> new BigDecimal("6.00");

		assertThatThrownBy(() -> importer.importRange(nq, "1m", FROM, TO, false))
			.isInstanceOf(DatabentoImporter.ImportRefusedException.class)
			.hasMessageContaining("per-request cap");

		assertThat(FAKE.barRequests).isEmpty();
		assertThat(ledgerStatuses()).containsExactly("REFUSED");
		assertThat(ledger.spentUsd()).isEqualByComparingTo("0");
	}

	@Test
	@DisplayName("refuses a chunk that would take total spending over the cap")
	void totalCap() {
		jdbc.update("""
			INSERT INTO market_data_import (instrument_id, source, dataset, symbol, resolution,
			    range_from, range_to, estimated_cost_usd, status)
			VALUES (?, 'databento', 'GLBX.MDP3', 'NQ.v.0', '1m', ?, ?, 99.98, 'DONE')""",
			nq.getId(), Timestamp.from(Instant.parse("2020-01-01T00:00:00Z")),
			Timestamp.from(Instant.parse("2020-02-01T00:00:00Z")));

		assertThatThrownBy(() -> importer.importRange(nq, "1m", FROM, TO, false))
			.isInstanceOf(DatabentoImporter.ImportRefusedException.class)
			.hasMessageContaining("over the cap of $100.00");
		assertThat(FAKE.barRequests).isEmpty();
	}

	@Test
	@DisplayName("fails a chunk with an off-grid price rather than rounding it, and still counts the cost")
	void offGrid() {
		FAKE.csv = form -> "ts_event,instrument_id,open,high,low,close,volume\n"
			+ FROM.getEpochSecond() + "000000000,1,20000100000000,20000250000000,"
			+ "20000000000000,20000250000000,5\n";

		assertThatThrownBy(() -> importer.importRange(nq, "1m", FROM, MID, false))
			.isInstanceOf(DatabentoImporter.OffGridPriceException.class);

		assertThat(storedBars()).isZero();
		assertThat(ledgerStatuses()).containsExactly("FAILED");
		// Databento bills bytes delivered; a failed download still spent them.
		assertThat(ledger.spentUsd()).isEqualByComparingTo("0.05");
	}

	@Test
	@DisplayName("a Databento error fails the import without revealing the key")
	void databentoError() {
		FAKE.status = 403;
		assertThatThrownBy(() -> importer.importRange(nq, "1m", FROM, MID, false))
			.isInstanceOf(DatabentoClient.DatabentoException.class)
			.hasMessageContaining("HTTP 403")
			.hasMessageNotContaining(KEY);
		assertThat(storedBars()).isZero();
	}

	@Test
	@DisplayName("will not import into a simulated instrument")
	void notSimulated() {
		Instrument demo = marketData.requireInstrument("DEMO-NQ");
		assertThatThrownBy(() -> importer.importRange(demo, "1m", FROM, TO, true))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("simulated");
		assertThat(FAKE.costRequests).isEmpty();
	}

	@Test
	@DisplayName("imported minutes are served as real bars, aggregated to any timeframe")
	void servedToTheChart() throws Exception {
		importer.importRange(nq, "1m", FROM, TO, false);

		BarSeries fiveMinute = marketData.internalBars(nq, Timeframe.M5, FROM, TO, 0);
		assertThat(fiveMinute.dataClass()).isEqualTo(DataClass.IMPORTED_PRIVATE);
		assertThat(fiveMinute.seed()).isNull();
		assertThat(fiveMinute.bars()).hasSize(24);

		// Each 5-minute bar agrees with the five minutes inside it.
		List<Bar> minutes = marketData.internalBars(nq, Timeframe.M1, FROM, FROM.plusSeconds(300), 0)
			.bars();
		Bar first = fiveMinute.bars().getFirst();
		assertThat(first.openTicks()).isEqualTo(minutes.getFirst().openTicks());
		assertThat(first.closeTicks()).isEqualTo(minutes.getLast().closeTicks());
		assertThat(first.highTicks()).isEqualTo(minutes.stream().mapToLong(Bar::highTicks).max().orElseThrow());
		assertThat(first.volume()).isEqualTo(minutes.stream().mapToLong(Bar::volume).sum());

		// And over HTTP: real data carries no seed and no simulated-data disclaimer.
		refreshTokens.deleteAllInBatch();
		users.deleteAllInBatch();
		var cookie = mockMvc.perform(post("/api/auth/register").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"bars@example.com\",\"password\":\"a-perfectly-fine-passphrase\","
					+ "\"displayName\":\"T\"}"))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getCookie(CookieService.ACCESS_TOKEN_COOKIE);
		mockMvc.perform(get("/api/market/instruments/NQ/bars").cookie(cookie)
				.param("timeframe", "5m")
				.param("from", String.valueOf(FROM.getEpochSecond()))
				.param("to", String.valueOf(TO.getEpochSecond())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.dataClass").value("IMPORTED_PRIVATE"))
			.andExpect(jsonPath("$.bars.length()").value(24))
			.andExpect(jsonPath("$.seed").doesNotExist())
			.andExpect(jsonPath("$.disclaimer").doesNotExist());
	}
}
