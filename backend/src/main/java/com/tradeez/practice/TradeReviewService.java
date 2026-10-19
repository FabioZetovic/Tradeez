package com.tradeez.practice;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;

import org.springframework.stereotype.Service;

import com.tradeez.analysis.DetectedSetup;
import com.tradeez.analysis.RuleText;
import com.tradeez.analysis.SetupService;
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Instrument;
import com.tradeez.marketdata.MarketDataService;
import com.tradeez.marketdata.Timeframe;
import com.tradeez.practice.FillEngine.Outcome;
import com.tradeez.practice.PracticeDtos.ReviewMomentView;
import com.tradeez.practice.PracticeDtos.ReviewView;
import com.tradeez.practice.TradeReview.Facts;
import com.tradeez.practice.TradeReview.Hit;
import com.tradeez.practice.TradeReview.Review;

/**
 * Gathers what a finished trade's review (T24, DEC-068) is told from: the stored hits of the rules it reads,
 * confirmed by the replay's cursor — the replay's own scan — and the one-minute candles after the entry. The
 * telling itself is {@link TradeReview}'s.
 */
@Service
public class TradeReviewService {

	/** The rules every review reads — liquidity, its sweeps, the shift, the gaps — and a named model's own. */
	static final List<String> CORE = List.of("LIQUIDITY", "SWEEP", "MSS", "FVG");

	private final SetupService setups;
	private final MarketDataService marketData;

	public TradeReviewService(SetupService setups, MarketDataService marketData) {
		this.setups = setups;
		this.marketData = marketData;
	}

	/**
	 * @param close     the learner's close, or null when the bracket ended it
	 * @param r         the trade's result in R, as the trade log shows it
	 * @param plannedR  the bracket's own result in R, which differs from {@code r} only after a close
	 * @param checklist the model's checklist as placed, in one line, or null
	 */
	public ReviewView review(PracticeSession session, Instrument instrument, TradePlan plan, PracticeResult planned,
			PracticeClose close, double r, double plannedR, String checklist) {
		Instant cursor = session.getCursorTime();
		Timeframe timeframe = Timeframe.fromCode(session.getTimeframe());
		long seed = session.getSeed() == null ? 0 : session.getSeed();
		LinkedHashSet<String> ids = new LinkedHashSet<>(CORE);
		if (plan.getModel() != null) {
			ids.addAll(plan.getModel().detectors());
		}
		List<String> rules = List.copyOf(ids);

		Instant exit = close != null ? close.getExitTime() : planned.getExitTime();
		Instant since = plan.getSubmittedCursor().minus(timeframe.duration().multipliedBy(TradeReview.BEFORE_CANDLES));
		Instant replayEnd = session.getStartTime().plus(PracticeService.MAX_REPLAY);
		// The replay's own scan; only the hits a review can tell are put into words.
		List<DetectedSetup> window = setups.visibleForReplay(instrument, timeframe, seed, session.getHistoryFrom(),
			cursor.isAfter(replayEnd) ? cursor : replayEnd, cursor, rules).stream()
			.filter(s -> s.getConfirmedAt().isAfter(since) && !s.getConfirmedAt().isAfter(exit))
			.toList();
		List<Hit> hits = window.stream().map(s -> new Hit(s.getDetector(), s.getConfirmedAt(), RuleText.label(s),
			RuleText.explain(s, instrument.getTickSize()))).toList();
		List<Bar> bars = plan.getDolTicks() == null ? List.of()
			: marketData.internalBars(instrument, Timeframe.M1, planned.getEntryTime().minusSeconds(60), cursor, seed).bars();

		Review review = TradeReview.build(new Facts(plan.getDirection(), plan.getContracts(), plan.getReferenceTicks(),
			plan.getStopTicks(), plan.getTargetTicks(), instrument.getTickSize(), timeframe.duration(), checklist,
			plan.getBias(), plan.getDolTicks(), plan.getConfidence(), plan.getSubmittedCursor(), planned.getEntryTime(),
			planned.getEntryTicks(), exit, close != null ? close.getExitTicks() : planned.getExitTicks(),
			close != null ? Outcome.CLOSED : planned.getOutcome(), r, close == null && planned.isGapExit(),
			close != null ? planned.getExitTime() : null, planned.getOutcome(), planned.getExitTicks(), plannedR, cursor,
			hits, bars));

		PracticeClock clock = session.getClock();
		return new ReviewView(TradeReview.VERSION, rules,
			review.moments().stream().map(m -> new ReviewMomentView(clock.show(m.at()), m.phase(), m.kind(), m.title(),
				m.lines())).toList(),
			review.summary(),
			setups.layerOf(instrument, rules, review.hitsUsed().stream().map(window::get).toList(), session.getHistoryFrom(),
				cursor, clock::show));
	}
}
