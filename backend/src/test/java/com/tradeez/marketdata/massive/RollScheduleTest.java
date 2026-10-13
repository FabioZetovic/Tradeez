package com.tradeez.marketdata.massive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.tradeez.marketdata.massive.MassiveClient.SessionBar;

/** DEC-042 — naming contracts the way Massive does, and following the front one by volume. */
class RollScheduleTest {

	private static final QuarterlyContract Z4 = new QuarterlyContract("NQ", 2024, 12);
	private static final QuarterlyContract H5 = new QuarterlyContract("NQ", 2025, 3);
	private static final QuarterlyContract M5 = new QuarterlyContract("NQ", 2025, 6);

	@Nested
	class Contracts {

		@Test
		@DisplayName("tickers use the month code and one year digit; ids are year and month")
		void naming() {
			assertThat(Z4.ticker()).isEqualTo("NQZ4");
			assertThat(H5.ticker()).isEqualTo("NQH5");
			assertThat(new QuarterlyContract("NQ", 2026, 9).ticker()).isEqualTo("NQU6");
			assertThat(Z4.id()).isEqualTo(202412L);
		}

		@Test
		@DisplayName("a contract expires on the third Friday of its month")
		void expiry() {
			assertThat(Z4.expiry()).isEqualTo(LocalDate.of(2024, 12, 20));
			assertThat(H5.expiry()).isEqualTo(LocalDate.of(2025, 3, 21));
			assertThat(new QuarterlyContract("NQ", 2026, 3).expiry()).isEqualTo(LocalDate.of(2026, 3, 20));
		}

		@Test
		@DisplayName("only March, June, September and December exist")
		void quarterlyOnly() {
			assertThatThrownBy(() -> new QuarterlyContract("NQ", 2025, 4))
				.isInstanceOf(IllegalArgumentException.class);
			assertThat(Z4.next()).isEqualTo(H5);
		}

		@Test
		@DisplayName("the first contract still trading includes expiry day itself")
		void firstStillTrading() {
			assertThat(QuarterlyContract.firstExpiringOnOrAfter("NQ", LocalDate.of(2024, 12, 20))).isEqualTo(Z4);
			assertThat(QuarterlyContract.firstExpiringOnOrAfter("NQ", LocalDate.of(2024, 12, 21))).isEqualTo(H5);
			assertThat(QuarterlyContract.firstExpiringOnOrAfter("NQ", LocalDate.of(2025, 1, 5))).isEqualTo(H5);
		}

		@Test
		@DisplayName("two years of history need ten candidate contracts")
		void covering() {
			List<QuarterlyContract> contracts = QuarterlyContract.covering("NQ",
				LocalDate.of(2024, 10, 4), LocalDate.of(2026, 10, 2));
			assertThat(contracts).extracting(QuarterlyContract::ticker).containsExactly(
				"NQZ4", "NQH5", "NQM5", "NQU5", "NQZ5", "NQH6", "NQM6", "NQU6", "NQZ6", "NQH7");
		}

		@Test
		@DisplayName("a range whose contracts would share a ticker is refused, not guessed")
		void decade() {
			assertThatThrownBy(() -> QuarterlyContract.covering("NQ",
					LocalDate.of(2016, 1, 4), LocalDate.of(2026, 10, 2)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("less than a decade");
		}
	}

	@Nested
	class FrontByVolume {

		@Test
		@DisplayName("the front contract changes the day after the volume moves — no lookahead")
		void rollsTheDayAfter() {
			Map<QuarterlyContract, List<SessionBar>> sessions = new LinkedHashMap<>();
			sessions.put(Z4, List.of(
				bar("NQZ4", "2024-12-11", 500_000), bar("NQZ4", "2024-12-12", 480_000),
				bar("NQZ4", "2024-12-13", 200_000), bar("NQZ4", "2024-12-16", 90_000)));
			sessions.put(H5, List.of(
				bar("NQH5", "2024-12-11", 6_000), bar("NQH5", "2024-12-12", 9_000),
				bar("NQH5", "2024-12-13", 400_000), bar("NQH5", "2024-12-16", 520_000)));

			NavigableMap<LocalDate, QuarterlyContract> front = RollSchedule.frontByVolume(sessions);

			// The first day has no previous session in the data, so no decision.
			assertThat(front).doesNotContainKey(LocalDate.of(2024, 12, 11));
			assertThat(front.get(LocalDate.of(2024, 12, 12))).isEqualTo(Z4);
			// H5 out-traded Z4 on the 13th, but that is only known once the 13th is over.
			assertThat(front.get(LocalDate.of(2024, 12, 13))).isEqualTo(Z4);
			assertThat(front.get(LocalDate.of(2024, 12, 16))).isEqualTo(H5);
		}

		@Test
		@DisplayName("on equal volume the nearer expiry wins")
		void tie() {
			Map<QuarterlyContract, List<SessionBar>> sessions = new LinkedHashMap<>();
			sessions.put(M5, List.of(bar("NQM5", "2025-03-12", 100), bar("NQM5", "2025-03-13", 1)));
			sessions.put(H5, List.of(bar("NQH5", "2025-03-12", 100), bar("NQH5", "2025-03-13", 1)));

			assertThat(RollSchedule.frontByVolume(sessions).get(LocalDate.of(2025, 3, 13))).isEqualTo(H5);
		}

		@Test
		@DisplayName("a contract that no longer trades is never chosen, whatever it did yesterday")
		void expiredIsSkipped() {
			Map<QuarterlyContract, List<SessionBar>> sessions = new LinkedHashMap<>();
			sessions.put(Z4, List.of(bar("NQZ4", "2024-12-20", 900_000)));
			sessions.put(H5, List.of(bar("NQH5", "2024-12-20", 10), bar("NQH5", "2024-12-23", 500_000)));

			assertThat(RollSchedule.frontByVolume(sessions).get(LocalDate.of(2024, 12, 23))).isEqualTo(H5);
		}
	}

	private static SessionBar bar(String ticker, String tradeDate, long volume) {
		return new SessionBar(ticker, LocalDate.parse(tradeDate), volume);
	}
}
