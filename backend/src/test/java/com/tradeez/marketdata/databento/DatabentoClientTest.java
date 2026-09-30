package com.tradeez.marketdata.databento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedReader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.databento.DatabentoClient.DatabentoException;
import com.tradeez.marketdata.databento.DatabentoClient.RawBar;

/**
 * The Databento client, against {@link FakeDatabento} — no network, no account, no cost.
 */
class DatabentoClientTest {

	private static final String KEY = "db-test-key-do-not-leak";
	private static final Instant FROM = Instant.parse("2024-03-13T14:00:00Z");
	private static final Instant TO = Instant.parse("2024-03-13T14:10:00Z");

	private final FakeDatabento fake = new FakeDatabento();

	@AfterEach
	void stop() {
		fake.close();
	}

	private DatabentoClient client(String key) {
		return new DatabentoClient(new DatabentoSettings(key, fake.url(), "GLBX.MDP3",
			new BigDecimal("5.00"), new BigDecimal("100.00")));
	}

	@Test
	@DisplayName("prices a request with the continuous symbol, authenticated with the key as username")
	void cost() {
		fake.cost = form -> new BigDecimal("0.0321");

		BigDecimal cost = client(KEY).cost("NQ.v.0", "ohlcv-1m", FROM, TO);

		assertThat(cost).isEqualByComparingTo("0.0321");
		var form = fake.costRequests.getFirst();
		assertThat(form).containsEntry("dataset", "GLBX.MDP3")
			.containsEntry("symbols", "NQ.v.0")
			.containsEntry("schema", "ohlcv-1m")
			.containsEntry("stype_in", "continuous")
			.containsEntry("start", "2024-03-13T14:00:00Z")
			.containsEntry("end", "2024-03-13T14:10:00Z");
		assertThat(fake.lastAuthorization).isEqualTo("Basic "
			+ Base64.getEncoder().encodeToString((KEY + ":").getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	@DisplayName("asks for bars as raw integers, so nothing is rounded on the way in")
	void barsRequestIsExact() {
		List<RawBar> bars = new ArrayList<>();
		long count = client(KEY).bars("NQ.v.0", "ohlcv-1m", FROM, TO, bars::add);

		assertThat(count).isEqualTo(10);
		assertThat(fake.barRequests.getFirst()).containsEntry("encoding", "csv")
			.containsEntry("compression", "none")
			.containsEntry("pretty_px", "false")
			.containsEntry("pretty_ts", "false");
		RawBar first = bars.getFirst();
		assertThat(first.tsEventNanos()).isEqualTo(FROM.getEpochSecond() * 1_000_000_000L);
		assertThat(first.open() % FakeDatabento.TICK).isZero();
	}

	@Test
	@DisplayName("reads columns by name, whatever order they arrive in")
	void columnsByName() throws Exception {
		String csv = """
			volume,close,low,high,open,instrument_id,publisher_id,rtype,ts_event,extra
			7,4,1,5,2,99,1,33,1710338400000000000,ignored
			""";
		List<RawBar> bars = new ArrayList<>();
		DatabentoClient.parse(new BufferedReader(new StringReader(csv)), bars::add);

		assertThat(bars).containsExactly(new RawBar(1710338400000000000L, 99, 2, 5, 1, 4, 7));
	}

	@Test
	@DisplayName("refuses a response missing a column it needs, and names the column")
	void missingColumn() {
		String csv = "ts_event,instrument_id,open,high,low,volume\n1,1,1,1,1,1\n";
		assertThatThrownBy(() -> DatabentoClient.parse(new BufferedReader(new StringReader(csv)), b -> {
		})).isInstanceOf(DatabentoException.class).hasMessageContaining("'close'");
	}

	@Test
	@DisplayName("reports an HTTP error with its status — and never with the key")
	void httpErrorNeverLeaksTheKey() {
		fake.status = 401;
		assertThatThrownBy(() -> client(KEY).cost("NQ.v.0", "ohlcv-1m", FROM, TO))
			.isInstanceOf(DatabentoException.class)
			.hasMessageContaining("HTTP 401")
			.hasMessageNotContaining(KEY);
	}

	@Test
	@DisplayName("without a key it says where the key goes, and sends nothing")
	void noKey() {
		assertThatThrownBy(() -> client("  ").cost("NQ.v.0", "ohlcv-1m", FROM, TO))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("backend/.env");
		assertThat(fake.costRequests).isEmpty();
	}

	@Test
	@DisplayName("settings never print the key")
	void settingsHideTheKey() {
		String text = new DatabentoSettings(KEY, fake.url(), "GLBX.MDP3", BigDecimal.ONE,
			BigDecimal.TEN).toString();
		assertThat(text).doesNotContain(KEY).contains("(set)");
	}

	@Test
	@DisplayName("converts fixed-point prices to whole ticks, and refuses an off-grid price")
	void ticks() {
		Instant at = FROM;
		assertThat(DatabentoImporter.toTicks(20_000_250_000_000L, FakeDatabento.TICK, at))
			.isEqualTo(80_001);
		assertThatThrownBy(() -> DatabentoImporter.toTicks(20_000_100_000_000L, FakeDatabento.TICK, at))
			.isInstanceOf(DatabentoImporter.OffGridPriceException.class)
			.hasMessageContaining("not a whole number of ticks");

		Bar bar = DatabentoImporter.toBar(new RawBar(FROM.getEpochSecond() * 1_000_000_000L, 7,
			80_000 * FakeDatabento.TICK, 80_004 * FakeDatabento.TICK, 79_998 * FakeDatabento.TICK,
			80_001 * FakeDatabento.TICK, 42), FakeDatabento.TICK);
		assertThat(bar).isEqualTo(new Bar(FROM, 80_000, 80_004, 79_998, 80_001, 42));
	}

	@Test
	@DisplayName("chunks by calendar month for minutes and by day for seconds, trimmed to the range")
	void chunks() {
		List<Instant[]> months = DatabentoImporter.chunks("1m",
			Instant.parse("2024-01-15T00:00:00Z"), Instant.parse("2024-03-10T00:00:00Z"));
		assertThat(months).extracting(c -> c[0] + " " + c[1]).containsExactly(
			"2024-01-15T00:00:00Z 2024-02-01T00:00:00Z",
			"2024-02-01T00:00:00Z 2024-03-01T00:00:00Z",
			"2024-03-01T00:00:00Z 2024-03-10T00:00:00Z");

		List<Instant[]> days = DatabentoImporter.chunks("1s",
			Instant.parse("2024-03-13T12:00:00Z"), Instant.parse("2024-03-15T00:00:00Z"));
		assertThat(days).extracting(c -> c[0] + " " + c[1]).containsExactly(
			"2024-03-13T12:00:00Z 2024-03-14T00:00:00Z",
			"2024-03-14T00:00:00Z 2024-03-15T00:00:00Z");
	}
}
