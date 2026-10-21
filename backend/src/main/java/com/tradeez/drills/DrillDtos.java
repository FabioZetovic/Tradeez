package com.tradeez.drills;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.tradeez.marketdata.dto.MarketDataDtos.BarResponse;
import com.tradeez.marketdata.dto.MarketDataDtos.CompanionBars;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Wire shapes for drills.
 *
 * <p>The answer key is never in a response before the drill is answered. On a static chart it is
 * not a secret in the security sense — the candles are all there — but a response that carried it
 * would turn "find the gaps" into "read the JSON", and the rule is simpler kept absolute. A
 * classification drill's subject is sent as what the chart shows — a price, a candle, which side —
 * and nothing that would give its name away. A prediction drill's candles stop at the moment asked
 * about; the ones after it, and which target they reached, are sent only with the answer (DEC-058).
 */
public final class DrillDtos {

	private DrillDtos() {
	}

	@Schema(description = "Start a risk drill (mode F, T26)")
	public record CreateRiskDrillRequest(
		@Schema(description = "CONTRACTS (position size), STREAK (a losing streak's drawdown) or RECOVERY (the gain a "
			+ "drawdown needs). Defaults to CONTRACTS") String kind) {
	}

	@Schema(description = "A risk drill's answer")
	public record RiskAnswerRequest(@NotNull @Schema(description = "In the question's unit") Double value) {
	}

	@Schema(description = "A risk drill (risk-1.0.0, DEC-070). The answer and its working come only with the result.")
	public record RiskDrillView(
		UUID id,
		String kind,
		@Schema(description = "risk-1.0.0") String version,
		String text,
		@Schema(description = "contracts or %") String unit,
		@Schema(description = "The time to answer in, shown as a clock; an answer after it still counts") int limitSeconds,
		@Schema(description = "Absent until answered") RiskResultView result) {
	}

	public record RiskResultView(
		double given,
		double answer,
		boolean correct,
		@Schema(description = "From the question to the answer, by the server's clock") double seconds,
		boolean inTime,
		@Schema(description = "The answer worked out, step by step") List<String> working,
		@Schema(description = "This learner's questions of this kind answered, this one included") int answered,
		int right) {
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
		@Schema(description = "Classification and prediction: one of the drill's options")
		String choice) {
	}

	@Schema(description = "One drill there is")
	public record KindView(
		String kind,
		@Schema(description = "IDENTIFY (mark every instance), CLASSIFY (name the marked one) or PREDICT "
			+ "(say what price does next)") String mode,
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

	@Schema(description = "What a prediction drill asks: two targets, and what the chart says about each "
		+ "(predict-1.0.0). Built from candles before the moment only")
	public record QuestionView(
		String version,
		@Schema(description = "How the targets are chosen and how what price did answers, in words")
		String definition,
		@Schema(description = "The last close on the chart, in ticks") long closeTicks,
		List<TargetView> targets,
		@Schema(description = "Drawn for context: gaps inside the range (IRL), or the level just taken")
		List<ContextView> context) {
	}

	@Schema(description = "One of a prediction's two answers, as drawn on the chart")
	public record TargetView(
		@Schema(description = "The answer's name: BSL, SSL, IRL or ERL") String value,
		@Schema(description = "ERL (an edge of the range) or IRL (inside it), ICT-15") String role,
		@Schema(description = "LEVEL (taken a tick beyond) or ZONE (a gap, reached when traded into)") String shape,
		@Schema(description = "ABOVE or BELOW the last close") String side,
		@Schema(description = "Where it starts, epoch seconds: its swing, or its gap's first candle") long fromTime,
		long lowTicks,
		long highTicks,
		@Schema(description = "Ticks from the last close") long distanceTicks,
		@Schema(description = "Which is taken first: swing points between price and it (ICT-17)") Integer obstacleSwings,
		@Schema(description = "Which is taken first: open gaps facing the move on the way (ICT-17)") Integer obstacleGaps,
		@Schema(description = "\"Buy-side at 20140.25\"") String label) {
	}

	@Schema(description = "Something a prediction drill draws for context, not to be chosen")
	public record ContextView(
		@Schema(description = "IRL_BULLISH_GAP | IRL_BEARISH_GAP | TAKEN_BSL | TAKEN_SSL") String what,
		@Schema(description = "LEVEL or ZONE") String shape,
		long fromTime,
		long lowTicks,
		long highTicks,
		String label) {
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
		@Schema(description = "Classification: the rule's name for the subject. Prediction: the target price "
			+ "reached first") String answer,
		@Schema(description = "Classification and prediction") Boolean correct,
		List<SetupView> setups,
		@Schema(description = "Prediction: the candle that decided it, epoch seconds") Long outcomeTime,
		@Schema(description = "Prediction: how many candles after the chart that was, from 1") Integer candlesAfter,
		@Schema(description = "Prediction: what happened, in words — prices, never times") List<String> explanation) {
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
		@Schema(description = "Classification and prediction: the answers to choose from") List<OptionView> options,
		@Schema(description = "Prediction: the targets and what the chart says about them") QuestionView question,
		@Schema(description = "Identification, absent until answered") List<Long> marks,
		@Schema(description = "Classification and prediction, absent until answered") String choice,
		@Schema(description = "Absent until answered") ResultView result,
		@Schema(description = "Prediction, absent until answered: the candles after the chart, up to a few past "
			+ "the one that decided it") List<BarResponse> revealed,
		@Schema(description = "A rule that reads two markets (DEC-075): the other one's candles over the same minutes")
		CompanionBars companion) {
	}
}
