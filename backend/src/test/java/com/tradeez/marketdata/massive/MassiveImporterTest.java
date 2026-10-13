package com.tradeez.marketdata.massive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import com.tradeez.marketdata.BarSeries;
import com.tradeez.marketdata.DataClass;
import com.tradeez.marketdata.Instrument;
import com.tradeez.marketdata.MarketDataService;
import com.tradeez.marketdata.Timeframe;
import com.tradeez.marketdata.importing.ImportReport;
import com.tradeez.marketdata.importing.ImportReport.Outcome;
import com.tradeez.marketdata.importing.OffGridPriceException;
import com.tradeez.marketdata.massive.MassiveClient.MassiveException;

/**
 * DEC-042 — importing real minutes from Massive, against {@link FakeMassive} and a real PostgreSQL.
 *
 * <p>The scenario spans a roll: NQ March 2025 (NQH5) is the front contract until the volume moves to
 * June (NQM5) on Friday 28 February; the series follows it from the next session, Monday 3 March.
 * The range also crosses a month boundary, so it is two chunks.
 */
class MassiveImporterTest extends AbstractIntegrationTest {

	private static final String KEY = "massive-importer-test-key";
	private static final FakeMassive FAKE = new FakeMassive();

	private static final Instant FROM = Instant.parse("2025-02-27T00:00:00Z");
	private static final Instant MID = Instant.parse("2025-03-01T00:00:00Z");
	private static final Instant TO = Instant.parse("2025-03-04T00:00:00Z");

	/** Thu 27 Feb from 00:00 UTC (1320) + Fri 28 Feb (1380). */
	private static final long FEBRUARY_BARS = 2700;
	/** Mon 3 Mar (1380) + the first hour of Tue 4 Mar's session (60). */
	private static final long MARCH_BARS = 1440;

	@DynamicPropertySource
	static void massive(DynamicPropertyRegistry registry) {
		registry.add("massive.base-url", FAKE::url);
		registry.add("massive.api-key", () -> KEY);
		registry.add("massive.requests-per-minute", () -> "0");
		registry.add("massive.page-limit", () -> "1000");
	}

	@AfterAll
	static void stopFake() {
		FAKE.close();
	}

	@Autowired
	MassiveImporter importer;

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
		Map<LocalDate, Long> march = new HashMap<>();
		Map<LocalDate, Long> june = new HashMap<>();
		for (LocalDate d = LocalDate.of(2025, 2, 10); !d.isAfter(LocalDate.of(2025, 3, 7)); d = d.plusDays(1)) {
			if (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) {
				continue;
			}
			boolean rolled = d.isAfter(LocalDate.of(2025, 2, 27));
			march.put(d, rolled ? 100_000L : 500_000L);
			june.put(d, rolled ? 600_000L : 10_000L);
		}
		FAKE.sessions.put("NQH5", march);
		FAKE.sessions.put("NQM5", june);
		FAKE.basePrice.put("NQH5", 80_000L);
		FAKE.basePrice.put("NQM5", 84_000L);
		nq = marketData.requireInstrument("NQ");
	}

	private long storedBars() {
		return jdbc.queryForObject("SELECT count(*) FROM market_bar", Long.class);
	}

	private long barsFrom(long contractId) {
		return jdbc.queryForObject("SELECT count(*) FROM market_bar WHERE contract_id = ?", Long.class,
			contractId);
	}

	private List<String> ledgerStatuses() {
		return jdbc.queryForList("SELECT status FROM market_data_import ORDER BY range_from",
			String.class);
	}

	@Test
	@DisplayName("a dry run works out the contracts per month and downloads no minutes")
	void dryRun() {
		ImportReport report = importer.importRange(nq, "1m", FROM, TO, true);

		assertThat(report.chunks()).extracting(ImportReport.Chunk::outcome)
			.containsExactly(Outcome.ESTIMATED, Outcome.ESTIMATED);
		assertThat(report.chunks()).extracting(ImportReport.Chunk::detail)
			.containsExactly("NQH5", "NQM5");
		assertThat(report.costUsd()).isZero();
		assertThat(FAKE.requests("1session")).hasSize(2);
		assertThat(FAKE.requests("1min")).isEmpty();
		assertThat(storedBars()).isZero();
		assertThat(ledgerStatuses()).isEmpty();
	}

	@Test
	@DisplayName("imports the front contract's minutes in whole ticks, with the roll kept visible")
	void imports() {
		ImportReport report = importer.importRange(nq, "1m", FROM, TO, false);

		assertThat(report.chunks()).extracting(ImportReport.Chunk::outcome)
			.containsExactly(Outcome.IMPORTED, Outcome.IMPORTED);
		assertThat(report.barsStored()).isEqualTo(FEBRUARY_BARS + MARCH_BARS);

		// Up to Friday's session the series is NQH5; from Monday it is NQM5, a day after the volume moved.
		assertThat(barsFrom(202503)).isEqualTo(FEBRUARY_BARS);
		assertThat(barsFrom(202506)).isEqualTo(MARCH_BARS);

		var first = jdbc.queryForMap("""
			SELECT open_ticks, high_ticks, low_ticks, close_ticks, contract_id FROM market_bar
			WHERE open_time = ?""", Timestamp.from(FROM));
		long ticks = FAKE.openTicks("NQH5", FROM);
		assertThat(first).containsEntry("open_ticks", ticks)
			.containsEntry("high_ticks", ticks + 3)
			.containsEntry("low_ticks", ticks - 2)
			.containsEntry("close_ticks", ticks + 1)
			.containsEntry("contract_id", 202503L);

		// Monday's first minute comes from June, at June's price level: the jump is real (DEC-024).
		Instant mondayOpen = Instant.parse("2025-03-02T23:00:00Z");
		assertThat(jdbc.queryForObject("SELECT open_ticks FROM market_bar WHERE open_time = ?",
			Long.class, Timestamp.from(mondayOpen))).isEqualTo(FAKE.openTicks("NQM5", mondayOpen));

		assertThat(jdbc.queryForList("""
			SELECT source || ' ' || dataset || ' ' || symbol || ' ' || estimated_cost_usd || ' ' || bars_stored
			FROM market_data_import WHERE status = 'DONE' ORDER BY range_from""", String.class))
			.containsExactly("massive CME NQH5 0.0000 " + FEBRUARY_BARS, "massive CME NQM5 0.0000 " + MARCH_BARS);

		// Paged at 1000: 2700 bars in three requests, 1440 in two — each carrying the key in the header only.
		assertThat(FAKE.requests("1min")).hasSize(5);
		assertThat(FAKE.requests).allSatisfy(r -> {
			assertThat(r.authorization()).isEqualTo("Bearer " + KEY);
			assertThat(r.rawQuery()).doesNotContain(KEY);
		});
	}

	@Test
	@DisplayName("running the same import again sends no requests at all")
	void resumable() {
		importer.importRange(nq, "1m", FROM, TO, false);
		FAKE.requests.clear();

		ImportReport again = importer.importRange(nq, "1m", FROM, TO, false);

		assertThat(again.chunks()).extracting(ImportReport.Chunk::outcome)
			.containsExactly(Outcome.ALREADY_IMPORTED, Outcome.ALREADY_IMPORTED);
		assertThat(FAKE.requests).isEmpty();
		assertThat(storedBars()).isEqualTo(FEBRUARY_BARS + MARCH_BARS);
	}

	@Test
	@DisplayName("a month Massive has nothing for is reported, and not recorded as done")
	void outsideThePlan() {
		Instant from = Instant.parse("2023-06-01T00:00:00Z");
		Instant to = Instant.parse("2023-06-03T00:00:00Z");

		ImportReport report = importer.importRange(nq, "1m", from, to, false);

		assertThat(report.chunks()).extracting(ImportReport.Chunk::outcome).containsExactly(Outcome.NO_DATA);
		assertThat(ledgerStatuses()).isEmpty();
		assertThat(FAKE.requests("1min")).isEmpty();

		// Nothing was marked covered, so a later run (a wider plan) asks again.
		FAKE.requests.clear();
		importer.importRange(nq, "1m", from, to, false);
		assertThat(FAKE.requests("1session")).isNotEmpty();
	}

	@Test
	@DisplayName("an off-grid price fails the month instead of being rounded")
	void offGrid() {
		FAKE.minuteBody = r -> "{\"status\":\"OK\",\"results\":[{\"ticker\":\"NQH5\",\"window_start\":"
			+ FROM.getEpochSecond() + "000000000,\"session_end_date\":\"2025-02-27\",\"open\":20000.1,"
			+ "\"high\":20001,\"low\":19999.5,\"close\":20000.5,\"volume\":4}]}";

		assertThatThrownBy(() -> importer.importRange(nq, "1m", FROM, MID, false))
			.isInstanceOf(OffGridPriceException.class)
			.hasMessageContaining("20000.1");

		assertThat(storedBars()).isZero();
		assertThat(ledgerStatuses()).containsExactly("FAILED");
	}

	@Test
	@DisplayName("a Massive error stops the import without revealing the key")
	void massiveError() {
		FAKE.status = 403;
		assertThatThrownBy(() -> importer.importRange(nq, "1m", FROM, MID, false))
			.isInstanceOf(MassiveException.class)
			.hasMessageContaining("HTTP 403")
			.hasMessageNotContaining(KEY);
		assertThat(storedBars()).isZero();
	}

	@Test
	@DisplayName("refuses one-second bars, which the free plan does not have")
	void noSeconds() {
		assertThatThrownBy(() -> importer.importRange(nq, "1s", FROM, MID, true))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("one-second");
		assertThat(FAKE.requests).isEmpty();
	}

	@Test
	@DisplayName("will not import into a simulated instrument")
	void notSimulated() {
		Instrument demo = marketData.requireInstrument("DEMO-NQ");
		assertThatThrownBy(() -> importer.importRange(demo, "1m", FROM, TO, true))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("simulated");
		assertThat(FAKE.requests).isEmpty();
	}

	@Test
	@DisplayName("imported minutes reach the chart as real data, aggregated")
	void servedToTheChart() {
		importer.importRange(nq, "1m", FROM, TO, false);

		BarSeries fiveMinute = marketData.internalBars(nq, Timeframe.M5, FROM, FROM.plusSeconds(3600), 0);
		assertThat(fiveMinute.dataClass()).isEqualTo(DataClass.IMPORTED_PRIVATE);
		assertThat(fiveMinute.bars()).hasSize(12);
		assertThat(fiveMinute.bars().getFirst().openTicks()).isEqualTo(FAKE.openTicks("NQH5", FROM));
	}

	@Test
	@DisplayName("the API reports NQ's real data range and names Massive as the source (INVARIANT 7)")
	void chartApi() throws Exception {
		importer.importRange(nq, "1m", FROM, TO, false);

		refreshTokens.deleteAllInBatch();
		users.deleteAllInBatch();
		var cookie = mockMvc.perform(post("/api/auth/register").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"massive@example.com\",\"password\":\"a-perfectly-fine-passphrase\","
					+ "\"displayName\":\"T\"}"))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getCookie(CookieService.ACCESS_TOKEN_COOKIE);

		// The last stored minute opens at 23:59 UTC on 3 March, so the data runs until 00:00 on the 4th.
		mockMvc.perform(get("/api/market/instruments").cookie(cookie))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].symbol").value("NQ"))
			.andExpect(jsonPath("$[0].dataFrom").value((int) FROM.getEpochSecond()))
			.andExpect(jsonPath("$[0].dataTo").value((int) TO.getEpochSecond()));

		mockMvc.perform(get("/api/market/instruments/NQ/bars").cookie(cookie)
				.param("timeframe", "15m")
				.param("from", String.valueOf(FROM.getEpochSecond()))
				.param("to", String.valueOf(FROM.getEpochSecond() + 3600)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.source").value("massive"))
			.andExpect(jsonPath("$.dataClass").value("IMPORTED_PRIVATE"))
			.andExpect(jsonPath("$.bars.length()").value(4))
			.andExpect(jsonPath("$.disclaimer").doesNotExist());
	}
}
