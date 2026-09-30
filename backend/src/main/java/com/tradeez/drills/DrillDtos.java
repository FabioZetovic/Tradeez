package com.tradeez.drills;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.tradeez.marketdata.dto.MarketDataDtos.BarResponse;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Wire shapes for drills.
 *
 * <p>The answer key is never in a response before the drill is answered. On a static chart it is
 * not a secret in the security sense — the candles are all there — but a response that carried it
 * would turn "find the gaps" into "read the JSON", and the rule is simpler kept absolute.
 */
public final class DrillDtos {

	private DrillDtos() {
	}

	@Schema(description = "Start a drill")
	public record CreateDrillRequest(
		@Schema(description = "FVG_IDENTIFICATION (the only kind so far)") String kind,
		@Schema(description = "Defaults to DEMO-NQ") String instrument,
		@Schema(description = "5m or 15m. Defaults to 5m") String timeframe) {
	}

	@Schema(description = "The candles marked as the middle candle of a fair value gap")
	public record AnswerRequest(
		@NotNull @Size(max = DrillService.WINDOW_BARS)
		@Schema(description = "Epoch seconds of each marked candle; must be candles in the window")
		List<Long> marks) {
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

	@Schema(description = "One gap from the answer key, with the numbers that qualified it")
	public record SetupView(
		UUID id,
		@Schema(description = "BULLISH | BEARISH") String direction,
		@Schema(description = "Candle 1, epoch seconds") long startTime,
		@Schema(description = "Candle 2 — the one to mark") long anchorTime,
		@Schema(description = "Candle 3") long endTime,
		long zoneLowTicks,
		long zoneHighTicks,
		boolean found,
		@Schema(description = "Rendered into the explanation; never hand-written")
		Map<String, Object> evidence) {
	}

	@Schema(description = "How the answer compares with the rule. Feedback, not a grade (DEC-034).")
	public record ResultView(
		int found,
		int missed,
		int extra,
		List<SetupView> setups,
		@Schema(description = "Marked candles that are not the middle of a qualifying gap")
		List<Long> extraMarks) {
	}

	public record DrillResponse(
		UUID id,
		String kind,
		String instrument,
		BigDecimal tickSize,
		String exchangeTimezone,
		String timeframe,
		String dataClass,
		String disclaimer,
		@Schema(description = "True on simulated data: a developer fixture, never graded (DEC-034)")
		boolean fixture,
		boolean graded,
		@Schema(description = "Shown wherever the drill is, when it is a fixture")
		String fixtureNotice,
		String instructions,
		RuleView rule,
		List<BarResponse> bars,
		@Schema(description = "Absent until answered") List<Long> marks,
		@Schema(description = "Absent until answered") ResultView result) {
	}
}
