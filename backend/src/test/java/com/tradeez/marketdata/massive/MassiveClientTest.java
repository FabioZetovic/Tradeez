package com.tradeez.marketdata.massive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tradeez.marketdata.massive.MassiveClient.MassiveException;
import com.tradeez.marketdata.massive.MassiveClient.RawBar;
import com.tradeez.marketdata.massive.MassiveClient.SessionBar;

/** DEC-042 — the Massive client against {@link FakeMassive}: parsing, paging, and where the key goes. */
class MassiveClientTest {

	private static final String KEY = "massive-client-test-key";
	private static final FakeMassive FAKE = new FakeMassive();

	/** Tuesday 2025-03-04, a whole CST session: 23:00 UTC Monday to 22:00 UTC Tuesday. */
	private static final LocalDate TRADE_DATE = LocalDate.of(2025, 3, 4);
	private static final Instant SESSION_OPEN = Instant.parse("2025-03-03T23:00:00Z");
	private static final Instant SESSION_CLOSE = Instant.parse("2025-03-04T22:00:00Z");

	private final List<Duration> backOffs = new ArrayList<>();
	private MassiveClient client;

	@AfterAll
	static void stopFake() {
		FAKE.close();
	}

	@BeforeEach
	void setUp() {
		FAKE.reset();
		FAKE.sessions.put("NQH5", new HashMap<>(Map.of(
			LocalDate.of(2025, 3, 3), 400_000L, TRADE_DATE, 450_000L)));
		backOffs.clear();
		client = client(KEY, 1000);
	}

	private MassiveClient client(String key, int pageLimit) {
		RequestPacer pacer = new RequestPacer(0, Clock.systemUTC(), backOffs::add);
		return new MassiveClient(new MassiveSettings(key, FAKE.url(), 0, pageLimit), pacer);
	}

	@Test
	@DisplayName("session bars come back with their trade date and volume, requested by opening date")
	void sessions() {
		List<SessionBar> bars = client.sessions("NQH5", LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 5));

		assertThat(bars).containsExactly(
			new SessionBar("NQH5", LocalDate.of(2025, 3, 3), 400_000),
			new SessionBar("NQH5", TRADE_DATE, 450_000));
		assertThat(FAKE.requests.getFirst().query())
			.containsEntry("resolution", "1session")
			.containsEntry("window_start.gte", "2025-03-01")
			.containsEntry("window_start.lt", "2025-03-05");
	}

	@Test
	@DisplayName("minute bars are followed across every page, prices read exactly as decimals")
	void minuteBarsAcrossPages() {
		List<RawBar> bars = new ArrayList<>();
		long count = client.minuteBars("NQH5", SESSION_OPEN, SESSION_CLOSE, bars::add);

		assertThat(count).isEqualTo(1380).isEqualTo(bars.size());
		assertThat(FAKE.requests("1min")).hasSize(2); // 1000 + 380
		RawBar first = bars.getFirst();
		assertThat(first.windowStartNanos()).isEqualTo(SESSION_OPEN.getEpochSecond() * 1_000_000_000L);
		assertThat(first.sessionEndDate()).isEqualTo(TRADE_DATE);
		long ticks = FAKE.openTicks("NQH5", SESSION_OPEN);
		assertThat(first.open()).isEqualByComparingTo(FakeMassive.TICK.multiply(BigDecimal.valueOf(ticks)));
		assertThat(first.low()).isEqualByComparingTo(FakeMassive.TICK.multiply(BigDecimal.valueOf(ticks - 2)));
		assertThat(bars.getLast().windowStartNanos())
			.isEqualTo(SESSION_CLOSE.minusSeconds(60).getEpochSecond() * 1_000_000_000L);
	}

	@Test
	@DisplayName("the key travels only in the Authorization header — on every page, never in a URL")
	void keyOnlyInTheHeader() {
		client.minuteBars("NQH5", SESSION_OPEN, SESSION_CLOSE, bar -> { });

		assertThat(FAKE.requests).hasSize(2).allSatisfy(r -> {
			assertThat(r.authorization()).isEqualTo("Bearer " + KEY);
			assertThat(r.rawQuery()).doesNotContain(KEY).doesNotContain("apiKey");
		});
	}

	@Test
	@DisplayName("refuses to follow a next_url to another host, which would carry the key with it")
	void nextUrlToAnotherHost() {
		FAKE.nextUrlOverride = "https://attacker.example/futures/v1/aggs/NQH5?cursor=1000";

		assertThatThrownBy(() -> client.minuteBars("NQH5", SESSION_OPEN, SESSION_CLOSE, bar -> { }))
			.isInstanceOf(MassiveException.class)
			.hasMessageContaining("another host");
		assertThat(FAKE.requests).hasSize(1);
	}

	@Test
	@DisplayName("a 429 is waited out and retried")
	void tooManyRequests() {
		FAKE.tooManyRequests.set(1);

		List<SessionBar> bars = client.sessions("NQH5", LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 5));

		assertThat(bars).hasSize(2);
		assertThat(FAKE.requests).hasSize(2);
		assertThat(backOffs).containsExactly(Duration.ZERO); // the fake's Retry-After: 0
	}

	@Test
	@DisplayName("an HTTP error says what happened and never contains the key")
	void httpError() {
		FAKE.status = 403;
		assertThatThrownBy(() -> client.sessions("NQH5", LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 5)))
			.isInstanceOf(MassiveException.class)
			.hasMessageContaining("HTTP 403")
			.hasMessageContaining("fake refusal")
			.hasMessageNotContaining(KEY);
	}

	@Test
	@DisplayName("a 200 that reports an error status is still an error")
	void errorStatus() {
		FAKE.minuteBody = r -> "{\"status\":\"ERROR\",\"error\":\"plan does not include this\"}";
		assertThatThrownBy(() -> client.minuteBars("NQH5", SESSION_OPEN, SESSION_CLOSE, bar -> { }))
			.isInstanceOf(MassiveException.class)
			.hasMessageContaining("plan does not include this");
	}

	@Test
	@DisplayName("without a key nothing is sent, and the message says where the key goes")
	void noKey() {
		assertThatThrownBy(() -> client("", 1000).sessions("NQH5", LocalDate.of(2025, 3, 1),
				LocalDate.of(2025, 3, 5)))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("backend/.env");
		assertThat(FAKE.requests).isEmpty();
	}

	@Test
	@DisplayName("a consumer's own exception reaches the caller unchanged")
	void consumerExceptionPropagates() {
		IllegalStateException boom = new IllegalStateException("consumer failed");
		assertThatThrownBy(() -> client.minuteBars("NQH5", SESSION_OPEN, SESSION_CLOSE, bar -> {
			throw boom;
		})).isSameAs(boom);
	}

	@Test
	@DisplayName("a bar missing a price is reported as unreadable, not silently skipped")
	void malformedBar() {
		FAKE.minuteBody = r -> "{\"status\":\"OK\",\"results\":[{\"ticker\":\"NQH5\","
			+ "\"window_start\":1741042800000000000,\"session_end_date\":\"2025-03-04\","
			+ "\"open\":20000,\"low\":19999.5,\"close\":20000.25,\"volume\":3}]}";
		assertThatThrownBy(() -> client.minuteBars("NQH5", SESSION_OPEN, SESSION_CLOSE, bar -> { }))
			.isInstanceOf(MassiveException.class)
			.hasMessageContaining("'high'");
	}

	@Test
	@DisplayName("settings never print the key")
	void settingsHideTheKey() {
		assertThat(new MassiveSettings(KEY, FAKE.url(), 5, 50_000).toString())
			.doesNotContain(KEY)
			.contains("apiKey=(set)");
	}
}
