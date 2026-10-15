package com.tradeez.curriculum;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.tradeez.analysis.SetupDtos.RuleView;
import com.tradeez.analysis.SetupDtos.SetupView;
import com.tradeez.marketdata.dto.MarketDataDtos.BarResponse;

import io.swagger.v3.oas.annotations.media.Schema;

/** Wire shapes for lesson cards (T14, DEC-055). */
public final class LessonDtos {

	private LessonDtos() {
	}

	@Schema(description = "A concept named, to link to")
	public record ConceptRef(String id, String name) {
	}

	@Schema(description = "A rule the concept is detected by, in words generated from its parameters")
	public record RuleCard(
		String detector,
		String version,
		Map<String, Object> parameters,
		String definition,
		String methodologySource) {
	}

	@Schema(description = "A drill that practises the concept")
	public record DrillRef(String kind, String mode, String title) {
	}

	@Schema(description = "One real example, found by the rule at random — never hand-picked")
	public record Example(
		String detector,
		@Schema(description = "\"Bullish gap\", \"Higher high (HH)\"") String label,
		@Schema(description = "Why it qualified, rendered from its evidence") List<String> explanation,
		String timeframe,
		@Schema(description = "The candles around it: up to the candle it is known by, and a few after")
		List<BarResponse> bars,
		@Schema(description = "The hit, drawn as the chart's Setups layer draws it") SetupView setup,
		RuleView rule) {
	}

	public record LessonResponse(
		String id,
		String name,
		int level,
		@Schema(description = "D, R, J or a combination: detectable, rule-based or judgement") String detectability,
		@Schema(description = "IMPLEMENTED, DRAFTED, DESCRIBED or OUT_OF_SCOPE") String status,
		String statusNote,
		@Schema(description = "What it is, from ICT_CONCEPTS.md") String summary,
		@Schema(description = "Concepts to know first") List<ConceptRef> prerequisites,
		@Schema(description = "Concepts that build on this one") List<ConceptRef> unlocks,
		List<RuleCard> rules,
		@Schema(description = "Where the app draws it: SETUPS (the chart's Setups layer) or CLOCK (the New "
			+ "York clock layer); absent when it does not") String shownOn,
		List<DrillRef> drills,
		@Schema(description = "The market the examples come from") String instrument,
		BigDecimal tickSize,
		String exchangeTimezone,
		String timeframe,
		List<Example> examples,
		@Schema(description = "Why there are no examples, or fewer than asked for; absent otherwise")
		String examplesNote) {
	}
}
