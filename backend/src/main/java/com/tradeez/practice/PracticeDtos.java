package com.tradeez.practice;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.tradeez.marketdata.dto.MarketDataDtos.BarResponse;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Wire shapes for the practice API.
 *
 * <p>Every field that could reveal the future is nullable and documented as such. The rule for
 * each is the same: it is null until the replay cursor has moved past the moment it describes
 * (DEC-009), or — for the seed and a real session's date — until the session is complete (DEC-038,
 * DEC-025).
 *
 * <p>Times on a real session are shown on a fictional week ({@code timeBasis} =
 * {@code NEW_YORK_WALL_CLOCK}): New York wall-clock time written as if it were UTC, weekday kept,
 * date hidden ({@link PracticeClock}).
 */
public final class PracticeDtos {

	private PracticeDtos() {
	}

	@Schema(description = "Start a practice session at a hidden moment")
	public record CreateSessionRequest(
		@Schema(description = "Defaults to NQ, real history") String instrument,
		@Schema(description = "1m, 5m or 15m. Defaults to 5m") String timeframe,
		@Schema(description = "Optional: ASIA, LONDON, NEW_YORK_AM or NEW_YORK_PM (ICT killzones, New York time)")
		String killzone,
		@Schema(description = "Optional: MONDAY … FRIDAY, the New York weekday of the hidden moment")
		String weekday) {
	}

	@Schema(description = """
		A bracket trade. Prices are INTEGER TICK COUNTS. Entry is at market: the first price after \
		the plan is locked. Once submitted, a plan cannot be changed (DEC-013).""")
	public record PlanRequest(
		@NotNull FillEngine.Direction direction,
		@NotNull Long stopTicks,
		@NotNull Long targetTicks,
		@NotNull @Min(1) @Max(100) Integer contracts) {
	}

	@Schema(description = "How many bars to reveal")
	public record AdvanceRequest(
		@NotNull @Min(1) @Max(PracticeService.MAX_ADVANCE_BARS) Integer bars) {
	}

	public record PlanView(
		FillEngine.Direction direction,
		long stopTicks,
		long targetTicks,
		int contracts,
		@Schema(description = "The last visible close when the plan was locked")
		long referenceTicks,
		@Schema(description = "The cursor when the plan was locked; entry is the next price after it")
		long lockedAt,
		@Schema(description = "Reward:risk as planned against the reference price")
		double plannedRewardRisk) {
	}

	@Schema(description = "Where the trade was entered. Null until the replay has revealed it.")
	public record EntryView(long time, long ticks) {
	}

	@Schema(description = "What happened. Present only once the session is COMPLETE.")
	public record ResultView(
		@Schema(description = "TARGET | STOP | AMBIGUOUS | TIME_EXIT") String outcome,
		@Schema(description = """
			Present when the outcome is an assumption rather than a fact (INVARIANT 6). Must be \
			shown to the learner wherever the result is.""")
		String assumption,
		long entryTime,
		long entryTicks,
		long exitTime,
		long exitTicks,
		@Schema(description = "Per contract, after slippage, before commission") long pnlTicks,
		@Schema(description = "All contracts, commission included") long netPnlCents,
		long commissionCents,
		@Schema(description = "Maximum adverse excursion, ticks") long maeTicks,
		@Schema(description = "Maximum favourable excursion, ticks") long mfeTicks,
		@Schema(description = "Filled at an opening price beyond the order") boolean gapExit,
		@Schema(description = "Result in multiples of the planned risk") double rMultiple,
		String resolution,
		String engineVersion,
		String fillModelVersion) {
	}

	public record PracticeState(
		UUID id,
		String instrument,
		BigDecimal tickSize,
		long tickValueCents,
		@Schema(description = "IANA zone for labelling times, e.g. America/Chicago")
		String exchangeTimezone,
		String timeframe,
		@Schema(description = "ANALYSING | IN_TRADE | COMPLETE") String status,
		long startTime,
		@Schema(description = "Nothing at or after this instant has been revealed") long cursor,
		@Schema(description = """
			UTC: times are real epoch seconds. NEW_YORK_WALL_CLOCK: New York wall-clock time written \
			as UTC on a fictional date — render in UTC, label it New York, never show the date \
			(DEC-025).""")
		String timeBasis,
		String dataClass,
		String disclaimer,
		PlanView plan,
		EntryView entry,
		ResultView result,
		@Schema(description = """
			Withheld until COMPLETE: the seed IS the future (DEC-038). A decimal STRING, because a \
			64-bit seed does not survive being a JSON number: JavaScript rounds anything above 2^53, \
			and a rounded seed reproduces a different market (BUG-028).""")
		String seed,
		@Schema(description = "Simulated data only")
		String generatorVersion,
		@Schema(description = "Real data only: where the bars came from, e.g. massive")
		String dataSource,
		@Schema(description = """
			Real data only, and only once COMPLETE: the hidden moment's real time, UTC epoch \
			seconds — the date reveal (DEC-025).""")
		Long revealedStartTime) {
	}

	@Schema(description = "A session with every bar revealed so far")
	public record SessionResponse(PracticeState state, List<BarResponse> bars) {
	}

	@Schema(description = "The bars just revealed, and the session after revealing them")
	public record AdvanceResponse(PracticeState state, List<BarResponse> bars) {
	}

	@Schema(description = "A journal row. Outcome fields are null until the session is COMPLETE.")
	public record SessionSummary(
		UUID id,
		String instrument,
		String timeframe,
		String status,
		long createdAt,
		FillEngine.Direction direction,
		String outcome,
		Long netPnlCents,
		Double rMultiple) {
	}
}
