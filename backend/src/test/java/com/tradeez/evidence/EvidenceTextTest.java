package com.tradeez.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tradeez.analysis.EvidenceCalculator.Count;
import com.tradeez.analysis.EvidenceCalculator.Lives;
import com.tradeez.analysis.EvidenceCalculator.PeriodTotals;
import com.tradeez.analysis.EvidenceCalculator.RuleEvidence;
import com.tradeez.marketdata.Timeframe;

/** A rule's evidence in words (T21, DEC-065): every share says what it is a share of, and the last line what it is not. */
class EvidenceTextTest {

	private static final Instant FROM = Instant.parse("2024-10-08T00:00:00Z");
	private static final Instant TO = Instant.parse("2026-10-03T00:00:00Z");

	private static RuleEvidence evidence(long hits, List<Count> kinds, Lives lives, PeriodTotals periods) {
		return new RuleEvidence(FROM, TO, 139_414, hits, kinds, lives, periods);
	}

	@Test
	@DisplayName("a rule that follows lives: how many, how often, and how the ended ones ended")
	void lives() {
		List<String> lines = EvidenceText.lines("FVG_STATE", "NQ", Timeframe.M5, evidence(15_174, List.of(),
			new Lives(15_092, List.of(new Count("TAPPED", 14_593), new Count("CE", 14_351), new Count("FILLED", 14_136),
				new Count("INVERTED", 13_750), new Count("EXPIRED", 956))), null));
		assertThat(lines).containsExactly(
			"NQ, 5-minute candles, 8 Oct 2024 to 2 Oct 2026: 139,414 candles.",
			"15,174 gaps — about 10.9 per 100 candles.",
			"Of the 15,092 whose life ended within the data, 97% were traded into; 95% reached their middle (CE); 94% were "
				+ "filled; 91% inverted — a close through the far side; 6% were still unfilled after 500 candles.",
			EvidenceText.NOT_A_FORECAST);
	}

	@Test
	@DisplayName("a rule with kinds: how its hits divide; a single kind is not divided")
	void kinds() {
		assertThat(EvidenceText.lines("SWEEP", "NQ", Timeframe.M1, evidence(33_005,
			List.of(new Count("SWEEP", 24_289), new Count("RUN", 8_716)), null, null)))
			.contains("Of them: 74% sweeps (closed back), 26% runs (held beyond).");
		assertThat(EvidenceText.lines("MSS", "NQ", Timeframe.H1, evidence(10, List.of(new Count("BULLISH", 10)), null, null)))
			.hasSize(3).first().asString().startsWith("NQ, 1-hour candles");
	}

	@Test
	@DisplayName("time rules: a share of the days, weeks or windows, and what is left over")
	void periods() {
		assertThat(EvidenceText.lines("DAILY_PROFILE", "NQ", Timeframe.M5, evidence(472, List.of(new Count("NY_REVERSAL", 193),
			new Count("LONDON_LOW", 157), new Count("LONDON_HIGH", 122)), null,
			new PeriodTotals("day", List.of(new Count("ALL", 490)))))).element(1).isEqualTo(
				"Of 490 midnight-to-17:00 days: New York reversal 39%, London low 32%, London high 25%; 4% fit none of them.");
		assertThat(EvidenceText.lines("PO3", "NQ", Timeframe.M5, evidence(257, List.of(new Count("BULLISH", 143),
			new Count("BEARISH", 114)), null, new PeriodTotals("day", List.of(new Count("ALL", 490)))))).element(1)
			.isEqualTo("Of 490 midnight-to-17:00 days, 52% were power of three: 29% bullish, 23% bearish.");
		assertThat(EvidenceText.lines("WEEKLY_PROFILE", "NQ", Timeframe.M15, evidence(72, List.of(new Count("MONDAY", 42),
			new Count("TUESDAY", 15)), null, new PeriodTotals("week", List.of(new Count("ALL", 86)))))).element(1)
			.isEqualTo("Of 86 whole weeks on one contract: Monday 49%, Tuesday 17%; 16% came the other way round and are not named.");
		assertThat(EvidenceText.lines("JUDAS", "NQ", Timeframe.M5, evidence(343, List.of(new Count("LONDON", 292),
			new Count("NEW_YORK", 51)), null, new PeriodTotals("window", List.of(new Count("LONDON", 507),
				new Count("NEW_YORK", 509)))))).element(1)
			.isEqualTo("Of 507 midnight-to-05:00 windows, 58% had a Judas swing; of 509 09:30-to-10:00 windows, 10%.");
	}

	@Test
	@DisplayName("a share of a few in ten thousand is under 1%, and a share of nothing is a dash")
	void percent() {
		assertThat(EvidenceText.percent(3, 10_000)).isEqualTo("under 1%");
		assertThat(EvidenceText.percent(0, 10_000)).isEqualTo("0%");
		assertThat(EvidenceText.percent(1, 0)).isEqualTo("—");
		assertThat(EvidenceText.percent(1, 2)).isEqualTo("50%");
	}
}
