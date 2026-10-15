package com.tradeez.drills;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.tradeez.marketdata.dto.MarketDataDtos.BarResponse;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Wire shapes for drills.
 *
 * <p>The answer key is never in a response before the drill is answered. On a static chart it is
 * not a secret in the security sense — the candles are all there — but a response that carried it
 * would turn "find the gaps" into "read the JSON", and the rule is simpler kept absolute. A
 * classification drill's subject is sent as what the chart shows — a price, a candle, which side —
 * and nothing that would give its name away.
 */
public final class DrillDtos {

	private DrillDtos() {
	}

	@Schema(description = "Start a drill")
	public record CreateDrillRequest(
		@Schema(description = "One of GET /api/drills/kinds. Defaults to FVG_IDENTIFICATION") String kind,
		@Schema(description = "Defaults to NQ (real data). DEMO-NQ is a developer fixture") String instrument,
		@Schema(description = "1m, 5m or 15m on real data. Defaults to 5m") String timeframe) {
	}

	@Schema(description = "Marks for an identification drill, or a choice for a classification drill")
	public record AnswerRequest(
		@Size(max = DrillService.CLASSIFY_WINDOW_BARS)
		@Schema(description = "Identification: epoch seconds of each marked candle in the window")
		List<Long> marks,
		@Size(max = 16)
		@Schema(description = "Classification: one of the drill's options")
		String choice) {
	}

	@Schema(description = "One drill there is")
	public record KindView(
		String kind,
		@Schema(description = "IDENTIFY (mark every instance) or CLASSIFY (name the marked one)") String mode,
		String title,
		String conceptId,
		String detector,
		String ruleVersion,
		String instructions) {
	}

	@Schema(description = "The versioned rule the answer key was produced by (DEC-026)")
	public record RuleView(
		String detector,
		String version,
		Map<String, Object> parameters,
		@Schema(description = "The rule in words, generated from the same parameters")
		String definition,
		String methodologySource) {
	}

	@Schema(description = "A name a classification drill offers")
	public record OptionView(String value, String label) {
	}

	@Schema(description = "What a classification drill asks about, as drawn on the chart")
	public record SubjectView(
		@Schema(description = "HIGH or LOW: the side of price the level or swing is on") String side,
		@Schema(description = "Where the level starts, epoch seconds: the swing it comes from") long fromTime,
		@Schema(description = "The marked candle, epoch seconds") long time,
		long priceTicks) {
	}

	@Schema(description = "One hit from the answer key, with the numbers that qualified it")
	public record SetupView(
		UUID id,
		@Schema(description = "BULLISH | BEARISH | NEUTRAL") String direction,
		@Schema(description = "The rule's name for it, when the rule has several") String kind,
		@Schema(description = "ZONE | LEVEL | POINT") String shape,
		@Schema(description = "Where it starts, epoch seconds") long startTime,
		@Schema(description = "The candle to mark") long anchorTime,
		@Schema(description = "The candle it is known by") long endTime,
		long zoneLowTicks,
		long zoneHighTicks,
		@Schema(description = "Identification: whether a mark found it") Boolean found,
		@Schema(description = "A short name: \"Bullish gap\", \"Higher high (HH)\"") String label,
		@Schema(description = "Why it qualified, rendered from the evidence; never hand-written")
		List<String> explanation,
		Map<String, Object> evidence) {
	}

	@Schema(description = "How the answer compares with the rule")
	public record ResultView(
		@Schema(description = "Identification") Integer found,
		@Schema(description = "Identification") Integer missed,
		@Schema(description = "Identification") Integer extra,
		@Schema(description = "Identification: marked candles that are not in the answer key")
		List<Long> extraMarks,
		@Schema(description = "Identification: marks on candles the rule cannot judge from this chart, "
			+ "counted neither way")
		List<Long> excusedMarks,
		@Schema(description = "Classification: the rule's name for the subject") String answer,
		@Schema(description = "Classification") Boolean correct,
		List<SetupView> setups) {
	}

	public record DrillResponse(
		UUID id,
		String kind,
		String mode,
		String title,
		String conceptId,
		String instrument,
		BigDecimal tickSize,
		String exchangeTimezone,
		String timeframe,
		String dataClass,
		String disclaimer,
		@Schema(description = "True on simulated data: a developer fixture, never graded (DEC-034)")
		boolean fixture,
		@Schema(description = "True on real data: graded against the rule, and kept for the learner's record")
		boolean graded,
		@Schema(description = "Shown wherever the drill is, when it is a fixture")
		String fixtureNotice,
		String instructions,
		@Schema(description = "Identification: what a correct mark is") String markName,
		RuleView rule,
		List<BarResponse> bars,
		@Schema(description = "Classification: the structure to name") SubjectView subject,
		@Schema(description = "Classification: the names to choose from") List<OptionView> options,
		@Schema(description = "Identification, absent until answered") List<Long> marks,
		@Schema(description = "Classification, absent until answered") String choice,
		@Schema(description = "Absent until answered") ResultView result) {
	}
}
