package com.tradeez.practice;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.tradeez.analysis.DetectedSetup;
import com.tradeez.analysis.SetupService;
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.DataClass;
import com.tradeez.marketdata.Instrument;
import com.tradeez.marketdata.Killzone;
import com.tradeez.marketdata.MarketDataService;
import com.tradeez.marketdata.Timeframe;
import com.tradeez.practice.FillEngine.Direction;
import com.tradeez.practice.ModelChecklist.Checklist;
import com.tradeez.practice.ModelChecklist.Hit;
import com.tradeez.practice.ModelChecklist.Moment;

/**
 * What the app sees at the moment of a decision (T22, DEC-066): the killzone, the {@code daily-bias-1.0.0}
 * suggestion, and — when the learner names a model — its checklist against the rules confirmed by the
 * cursor. Read for the plan panel while a trade is drafted, and again, and stored, when it is placed.
 *
 * <p>Nothing here looks past the cursor: the hits are the replay's own (confirmed by the cursor, DEC-009),
 * the days are the whole ones before the cursor's trading day, the candles end at the cursor.
 */
@Service
public class PlanContextService {

	/** The whole trading days before the moment are looked for this far back, past a weekend and a holiday. */
	private static final Duration DAYS_BACK = Duration.ofDays(6);

	private final MarketDataService marketData;
	private final SetupService setups;

	public PlanContextService(MarketDataService marketData, SetupService setups) {
		this.marketData = marketData;
		this.setups = setups;
	}

	/** @param checklist null when no model was named */
	public record PlanContext(Killzone killzone, DailyBias.Suggestion suggestion, Checklist checklist) {
	}

	public PlanContext at(PracticeSession session, Instrument instrument, Direction direction, long reference, long stop,
			long target, TradingModel model) {
		Instant cursor = session.getCursorTime();
		Timeframe timeframe = Timeframe.fromCode(session.getTimeframe());
		long seed = session.getSeed() == null ? 0 : session.getSeed();
		Killzone killzone = null;
		for (Killzone zone : Killzone.values()) {
			if (zone.contains(cursor)) {
				killzone = zone;
			}
		}
		Checklist checklist = model == null ? null : ModelChecklist.check(model, moment(session, instrument, timeframe, seed,
			direction, reference, stop, target, model));
		return new PlanContext(killzone, suggestion(instrument, cursor, seed), checklist);
	}

	private Moment moment(PracticeSession session, Instrument instrument, Timeframe timeframe, long seed, Direction direction,
			long reference, long stop, long target, TradingModel model) {
		Instant cursor = session.getCursorTime();
		Instant replayEnd = session.getStartTime().plus(PracticeService.MAX_REPLAY);
		// The same stretch the replay's Setups layer scans, so the two read one stored scan.
		List<DetectedSetup> stored = setups.visibleForReplay(instrument, timeframe, seed, session.getHistoryFrom(),
			cursor.isAfter(replayEnd) ? cursor : replayEnd, cursor, model.detectors());
		Map<String, List<Hit>> hits = new LinkedHashMap<>();
		for (DetectedSetup s : stored) {
			hits.computeIfAbsent(s.getDetector(), k -> new ArrayList<>()).add(new Hit(s.getDetector(), s.getKind(),
				s.getDirection(), s.getStartTime(), s.getAnchorTime(), s.getEndTime(), s.getConfirmedAt(), s.getZoneLowTicks(),
				s.getZoneHighTicks(), s.getEvidence()));
		}
		Duration recent = timeframe.duration().multipliedBy(ModelChecklist.SOUP_CANDLES + 1);
		List<Bar> bars = marketData.internalBars(instrument, timeframe, cursor.minus(recent), cursor, seed).bars();
		return new Moment(cursor, timeframe, direction, reference, stop, target, instrument.getTickSize(), bars, hits);
	}

	/** The last two whole trading days before the cursor's, read as daily-bias-1.0.0 reads them. */
	private DailyBias.Suggestion suggestion(Instrument instrument, Instant cursor, long seed) {
		if (instrument.getDataClass() == DataClass.SIMULATED) {
			// A suggestion teaches; nothing is taught from invented data (INVARIANT 8b).
			return new DailyBias.Suggestion(null, "A simulated market: " + DailyBias.VERSION + " reads real trading days only.");
		}
		Instant today = Timeframe.D1.bucketStart(cursor);
		List<Bar> days = marketData.internalBars(instrument, Timeframe.D1, today.minus(DAYS_BACK), today, seed).bars();
		if (days.size() < 2) {
			return new DailyBias.Suggestion(null, "Fewer than two whole trading days before this moment: nothing to read.");
		}
		return DailyBias.suggest(days.get(days.size() - 2), days.getLast(), instrument.getTickSize());
	}
}
