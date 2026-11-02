package com.tradeez.practice;

import java.security.SecureRandom;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.tradeez.analysis.SetupDtos.SetupLayerResponse;
import com.tradeez.analysis.SetupService;
import com.tradeez.clock.ClockDtos;
import com.tradeez.clock.ClockDtos.ClockLayerResponse;
import com.tradeez.clock.ClockLayer;
import com.tradeez.clock.ClockService;
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.NoHistoryException;
import com.tradeez.marketdata.BarSeries;
import com.tradeez.marketdata.DataClass;
import com.tradeez.marketdata.DemoSeriesGenerator;
import com.tradeez.marketdata.HiddenStretches;
import com.tradeez.marketdata.Instrument;
import com.tradeez.marketdata.InstrumentRepository;
import com.tradeez.marketdata.Killzone;
import com.tradeez.marketdata.MarketDataService;
import com.tradeez.marketdata.SessionCalendar;
import com.tradeez.marketdata.Timeframe;
import com.tradeez.marketdata.dto.MarketDataDtos.BarResponse;
import com.tradeez.practice.FillEngine.Direction;
import com.tradeez.practice.FillEngine.Fill;
import com.tradeez.practice.FillEngine.FillModel;
import com.tradeez.practice.FillEngine.Leg;
import com.tradeez.practice.FillEngine.Outcome;
import com.tradeez.practice.FillEngine.Resolution;
import com.tradeez.practice.PlanContextService.PlanContext;
import com.tradeez.practice.PracticeDtos.AdvanceResponse;
import com.tradeez.practice.PracticeDtos.CalibrationView;
import com.tradeez.practice.PracticeDtos.ChecklistItemView;
import com.tradeez.practice.PracticeDtos.JournalTradeView;
import com.tradeez.practice.PracticeDtos.ChecklistView;
import com.tradeez.practice.PracticeDtos.ContextRequest;
import com.tradeez.practice.PracticeDtos.PlanContextView;
import com.tradeez.practice.PracticeDtos.EntryView;
import com.tradeez.practice.PracticeDtos.PartialView;
import com.tradeez.practice.PracticeDtos.PlanRequest;
import com.tradeez.practice.PracticeDtos.PlanView;
import com.tradeez.practice.PracticeDtos.PracticeState;
import com.tradeez.practice.PracticeDtos.ResultView;
import com.tradeez.practice.PracticeDtos.ReviewView;
import com.tradeez.practice.PracticeDtos.ScoreView;
import com.tradeez.practice.PracticeDtos.SessionResponse;
import com.tradeez.practice.PracticeDtos.SessionSummary;
import com.tradeez.practice.PracticeDtos.SettingsRequest;
import com.tradeez.practice.PracticeDtos.SettingsView;
import com.tradeez.practice.PracticeDtos.ScorecardView;
import com.tradeez.practice.PracticeDtos.StatisticsView;
import com.tradeez.practice.PracticeDtos.TradeView;
import com.tradeez.practice.PracticeSession.Status;

/**
 * The replay: a hidden moment, played forward, with trades placed along the way (DEC-046).
 *
 * <p>A session is flat ({@code ANALYSING}), in a trade ({@code IN_TRADE}), or over
 * ({@code COMPLETE}). Locking a plan opens a trade; the replay passing its exit closes it and leaves
 * the learner flat to place the next. One trade runs at a time. The session ends when the learner
 * ends it, or when a flat replay reaches its limit.
 *
 * <h2>The one rule (DEC-009)</h2>
 * Nothing at or after the session's cursor leaves this class. That is enforced structurally rather
 * than by filtering: bars shown to the learner are <i>generated</i> only up to the cursor, so data
 * past it does not exist in memory to be leaked by a careless DTO. The one place future data is
 * generated — resolving a plan — produces a result row and nothing else, and that row is withheld
 * until the cursor passes its exit.
 *
 * <h2>On real data, the date is part of the future (DEC-025)</h2>
 * A learner who knows the date can look up what happened next. So every time a real session sends —
 * bars, cursor, entry, exit — goes through its {@link PracticeClock}, which keeps the weekday and the
 * New York wall-clock time and hides the date. The real date is revealed when the session ends — not
 * with a trade's result, because another trade could follow it.
 *
 * <h2>Why the result is computed at submission</h2>
 * INVARIANT 2: an outcome is written once. Resolving the trade the moment the plan is locked means
 * the replay is purely a reveal. Nothing the learner does while watching — pausing, reloading,
 * opening a second tab — can affect a result that already exists.
 */
@Service
public class PracticeService {

	/** Timeframes a session can be started on. The engine resolves on the finest bars that exist. */
	static final Set<Timeframe> TIMEFRAMES = EnumSet.of(Timeframe.M1, Timeframe.M5, Timeframe.M15);

	/**
	 * Timeframes a running replay can be looked at in (T34) — the Chart tab's. A candle still forming
	 * at the cursor is built only from the minutes before it, so switching never shows the future.
	 */
	static final Set<Timeframe> VIEW_TIMEFRAMES = EnumSet.of(Timeframe.M1, Timeframe.M3, Timeframe.M5,
		Timeframe.M15, Timeframe.M30, Timeframe.H1, Timeframe.H4, Timeframe.D1);

	/**
	 * History a coarse view shows before the replay's start (T35): four days is four daily candles,
	 * too few to read a higher timeframe from. Real data only — a simulated market is generated a
	 * second at a time, and stays at its own history.
	 */
	static final Map<Timeframe, Duration> VIEW_HISTORY = Map.of(Timeframe.H4, Duration.ofDays(30),
		Timeframe.D1, Duration.ofDays(120));

	/** Real NQ (DEC-040, T4). Simulated markets remain available by symbol. */
	static final String DEFAULT_INSTRUMENT = "NQ";
	static final Timeframe DEFAULT_TIMEFRAME = Timeframe.M5;

	/** Visible history before the hidden moment. Four days so a Monday start still sees Friday. */
	static final Duration HISTORY = Duration.ofDays(4);

	/** Minimum open market after the start, so a new session never opens seconds before a close. */
	static final Duration MIN_SESSION_LEFT = Duration.ofHours(2);

	/** How far past its start a replay can be stepped. Bounds what a GET has to generate. */
	static final Duration MAX_REPLAY = Duration.ofDays(3);

	/** Long enough to step across a weekend when the market is closed. */
	private static final Duration CLOSED_MARKET_LOOKAHEAD = Duration.ofDays(3);

	public static final int MAX_ADVANCE_BARS = 500;

	/**
	 * A bracket further than this from the price is not a plan, it is a way to hold a position
	 * until the session ends. 20,000 ticks is 5,000 NQ points — about a quarter of the index.
	 */
	static final long MAX_BRACKET_TICKS = 20_000;

	/** Sessions a user may start per rolling hour. Each one generates days of one-second data. */
	static final int MAX_SESSIONS_PER_HOUR = 60;

	/** Trades one replay may hold. Each resolves against hours of fine bars when it is locked. */
	static final int MAX_TRADES = 50;

	/**
	 * The range hidden moments are drawn from. The dates of a simulated market carry no meaning —
	 * there is no real history to look up (DEC-025 as refined by DEC-038) — but spreading starts
	 * across years spreads them across the generator's slow seasonal cycle.
	 */
	private static final Instant EARLIEST_START = Instant.parse("2023-01-02T00:00:00Z");
	private static final Instant LATEST_START = Instant.parse("2025-12-31T00:00:00Z");

	private static final String SIMULATED_DISCLAIMER = "SIMULATED DATA — NOT MARKET HISTORY";

	/** Real data: how much trading a candidate moment needs around it to be worth practising on. */
	private static final int MIN_MINUTES_BEFORE = 30;
	private static final int MIN_MINUTES_AFTER = 60;
	private static final int PICK_ATTEMPTS = 2_000;

	private final SecureRandom random = new SecureRandom();

	private final PracticeSessionRepository sessions;
	private final TradePlanRepository plans;
	private final PracticeResultRepository results;
	private final PracticeCloseRepository closes;
	private final InstrumentRepository instruments;
	private final MarketDataService marketData;
	private final ClockService clocks;
	private final SetupService setupService;
	private final PlanContextService contexts;
	private final PracticeSettingsStore settings;
	private final PracticeCosts costs;
	private final TradeReviewService reviews;
	private final HiddenStretches hiddenStretches;

	/**
	 * BUG-033 — transactions are opened by hand, around the database work only.
	 *
	 * <p>Every command here does two kinds of work: a few row reads and writes, and generating market
	 * data — days of one-second bars, tens to hundreds of milliseconds of CPU. Under a method-level
	 * {@code @Transactional} the second ran while holding a pooled connection, so a handful of
	 * learners resuming sessions at once could exhaust the pool while the database sat idle.
	 *
	 * <p>So each command is three steps: read a consistent {@link Snapshot}; generate with no
	 * transaction open; then a short write that re-reads the session and refuses if its version moved
	 * in between. The last step is what keeps two racing requests — a double-click, a second tab —
	 * from both acting on the same cursor.
	 */
	private final TransactionTemplate writeTx;
	private final TransactionTemplate readTx;

	public PracticeService(PracticeSessionRepository sessions, TradePlanRepository plans,
			PracticeResultRepository results, PracticeCloseRepository closes, InstrumentRepository instruments,
			MarketDataService marketData, ClockService clocks, SetupService setupService, PlanContextService contexts,
			PracticeSettingsStore settings, PracticeCosts costs, TradeReviewService reviews, PlatformTransactionManager transactionManager,
			HiddenStretches hiddenStretches) {
		this.sessions = sessions;
		this.plans = plans;
		this.results = results;
		this.closes = closes;
		this.instruments = instruments;
		this.marketData = marketData;
		this.clocks = clocks;
		this.setupService = setupService;
		this.contexts = contexts;
		this.settings = settings;
		this.costs = costs;
		this.reviews = reviews;
		this.hiddenStretches = hiddenStretches;
		this.writeTx = new TransactionTemplate(transactionManager);
		this.readTx = new TransactionTemplate(transactionManager);
		this.readTx.setReadOnly(true);
		// One snapshot for the session, its plan and its result. Under PostgreSQL's default
		// READ COMMITTED each statement sees its own moment, and a plan committed between reading
		// the session and reading the plan would produce a state that never existed.
		this.readTx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
	}

	/**
	 * A plan and its result, written together, so neither exists without the other — and the close,
	 * when the learner left at market before the plan's own exit (DEC-057). Then the close is what
	 * happened, and the planned result stays only as the record of what the plan would have done.
	 */
	private record Trade(TradePlan plan, PracticeResult result, PracticeClose close, long tickValueCents) {

		Instant exitTime() {
			return close != null ? close.getExitTime() : result.getExitTime();
		}

		/** The partial's fill: the close's when closed by hand, otherwise the result's (T28). */
		Leg partial() {
			return close != null ? close.partial() : result.partial();
		}

		long netPnlCents() {
			return close != null ? close.getNetPnlCents() : result.getNetPnlCents();
		}

		/** How it ended: closed by hand, or the plan's own exit. */
		Outcome outcome() {
			return close != null ? Outcome.CLOSED : result.getOutcome();
		}

		/** True once the replay has passed the exit: the result is the past now. */
		boolean closedBy(Instant cursor) {
			return cursor.isAfter(exitTime());
		}

		/**
		 * The result in units of what the learner planned to risk: every contract's, so a partial weighs by
		 * the contracts it took off (T28).
		 *
		 * <p>Measured against the plan's reference price, not the fill: R is a statement about the
		 * decision the learner made, and they made it looking at the reference.
		 *
		 * <p>DEC-076 (BUG-087): the result is the money it made, costs paid — the number shown beside it. Counted in
		 * ticks before commission, a trade out a tick ahead under $50 of fees was "+0.03R, a win" over a $45 loss.
		 */
		double r() {
			return inR(netPnlCents());
		}

		/** What the plan alone would have made, in the same units: a trade closed by hand keeps it for its review. */
		double plannedR() {
			return inR(result.getNetPnlCents());
		}

		/** Money in units of the planned risk: the stop's distance from the reference, every contract. */
		private double inR(long cents) {
			long risk = Math.abs(plan.getReferenceTicks() - plan.getStopTicks());
			return (double) cents / (risk * plan.getContracts() * tickValueCents);
		}

		/** Made money once every cost was paid (DEC-076). */
		boolean won() {
			return netPnlCents() > 0;
		}

		boolean lost() {
			return netPnlCents() < 0;
		}
	}

	/** A session and everything attached to it, read in one transaction. Trades in order. */
	private record Snapshot(PracticeSession session, Instrument instrument, List<Trade> trades) {

		/** The running trade's result, or null when the learner is flat or the session is over. */
		PracticeResult openResult() {
			if (session.getStatus() != Status.IN_TRADE) {
				return null;
			}
			if (trades.isEmpty()) {
				throw new IllegalStateException(
					"Session " + session.getId() + " is IN_TRADE without a trade");
			}
			return trades.getLast().result();
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Commands
	// ---------------------------------------------------------------------------------------------

	/**
	 * Starts a session at a random hidden moment.
	 *
	 * @param killzoneCode optional: only moments inside this ICT killzone ({@link Killzone})
	 * @param weekdayCode  optional: only moments on this New York weekday, e.g. {@code TUESDAY}
	 */
	public SessionResponse create(UUID userId, String symbol, String timeframeCode,
			String killzoneCode, String weekdayCode) {
		Instrument instrument = marketData.requireInstrument(
			symbol == null || symbol.isBlank() ? DEFAULT_INSTRUMENT : symbol);
		Timeframe timeframe = timeframeCode == null || timeframeCode.isBlank()
			? DEFAULT_TIMEFRAME
			: Timeframe.fromCode(timeframeCode);

		if (!TIMEFRAMES.contains(timeframe)) {
			throw new IllegalArgumentException("Practice runs on "
				+ TIMEFRAMES.stream().map(Timeframe::code).toList() + " charts.");
		}
		Killzone killzone = parseEnum(Killzone.class, killzoneCode, "killzone");
		DayOfWeek weekday = parseEnum(DayOfWeek.class, weekdayCode, "weekday");
		// BUG-067. Saturday never trades, and Friday's market shuts at 17:00, before Asia's 20:00: on a
		// simulated market these sent the search through ten thousand misses into a 500.
		if (weekday == DayOfWeek.SATURDAY || weekday == DayOfWeek.SUNDAY) {
			throw new IllegalArgumentException("Practice weekdays are Monday to Friday.");
		}
		if (killzone == Killzone.ASIA && weekday == DayOfWeek.FRIDAY) {
			throw new IllegalArgumentException(
				"The market shuts at 17:00 on Fridays, so no Asia killzone falls on a Friday.");
		}

		if (instrument.getDataClass() == DataClass.SIMULATED) {
			Instant start = pickStart(timeframe, killzone, weekday);
			PracticeSession session = saveCapped(userId, new PracticeSession(
				userId, instrument.getId(), timeframe.code(), newSeed(),
				DemoSeriesGenerator.GENERATOR_VERSION, start.minus(HISTORY), start));
			// Generated after the commit, with no connection held (BUG-033).
			return new SessionResponse(state(session, instrument, List.of()),
				visibleBars(session, instrument, timeframe));
		}

		// Real history: a moment where the market really traded, behind a hidden date (DEC-025).
		Instant start = pickRealStart(instrument, timeframe, killzone, weekday, hiddenStretches.of(userId));
		BarSeries history = marketData.internalBars(instrument, timeframe, start.minus(HISTORY), start,
			0);
		PracticeClock clock = PracticeClock.hidingDateOf(start);
		PracticeSession session = saveCapped(userId, PracticeSession.onRealData(userId,
			instrument.getId(), timeframe.code(), history.source(), clock, start.minus(HISTORY), start));
		return new SessionResponse(state(session, instrument, List.of()),
			toDto(history.bars(), clock));
	}

	/** Saves a new session unless the user has started too many this hour. */
	private PracticeSession saveCapped(UUID userId, PracticeSession session) {
		return writeTx.execute(tx -> {
			if (sessions.countByUserIdAndCreatedAtAfter(userId,
					Instant.now().minus(Duration.ofHours(1))) >= MAX_SESSIONS_PER_HOUR) {
				throw new TooManySessionsException();
			}
			return sessions.save(session);
		});
	}

	/**
	 * Locks a plan and resolves it: the next trade of the replay.
	 *
	 * <p>This is the only method that generates data past the cursor, and none of it is returned:
	 * the response is the state, which withholds the result until the replay reaches it.
	 */
	public PracticeState submitPlan(UUID userId, UUID sessionId, PlanRequest request) {
		Snapshot seen = snapshot(userId, sessionId);
		PracticeSession session = seen.session();
		Instrument instrument = seen.instrument();

		if (session.getStatus() == Status.COMPLETE) {
			throw new SessionEndedException();
		}
		if (session.getStatus() == Status.IN_TRADE) {
			throw new PlanLockedException();
		}
		if (seen.trades().size() >= MAX_TRADES) {
			throw new TooManyTradesException();
		}
		if (!session.getCursorTime().isBefore(replayEnd(session))) {
			throw new ReplayEndException();
		}
		if (!SessionCalendar.isOpen(session.getCursorTime())) {
			throw new MarketClosedException();
		}

		// The finest bars from the cursor to the end of the trading session: one-second where they
		// exist, otherwise one-minute (real data on Massive's free plan, DEC-042). The engine exits at
		// the close if neither level is reached, so nothing beyond it can matter.
		Instant cursor = session.getCursorTime();
		Resolution resolution = Resolution.S1;
		List<Bar> bars = fine(session, instrument, Resolution.S1, cursor, sessionEnd(cursor));
		if (bars.isEmpty() && instrument.getDataClass() != DataClass.SIMULATED) {
			resolution = Resolution.M1;
			bars = fine(session, instrument, resolution, cursor, sessionEnd(cursor));
		}
		if (bars.isEmpty()) {
			throw new MarketClosedException();
		}

		long reference = lastVisibleClose(session, instrument, resolution);
		validateBracket(request, reference);
		validatePartial(request, reference);
		if (request.confidence() != null && request.confidence() % 10 != 0) {
			throw new IllegalArgumentException("Confidence is a whole ten, from 10 to 90 percent.");
		}
		// T22: what the app sees at this moment, kept with the plan. Read before the write, like the fill.
		PlanContext context = contexts.at(session, instrument, request.direction(), reference, request.stopTicks(),
			request.targetTicks(), request.model());

		// T41 (DEC-080): every trade pays the standard costs; the result keeps the model it was resolved with.
		FillModel model = costs.model();
		Fill fill = FillEngine.resolve(new FillEngine.Plan(request.direction(), request.stopTicks(),
			request.targetTicks(), request.contracts(), request.partial()), bars, model, resolution);

		return writeTx.execute(tx -> {
			// The fill was computed for the cursor in the snapshot. If the session moved since —
			// a step in another tab — it is a fill for a moment the learner is no longer at.
			PracticeSession current = requireUnchanged(userId, session);
			// T23: the limit the learner had set when the trade was placed, kept with it.
			Long riskLimit = settings.riskLimitCents(userId);
			TradePlan plan = plans.save(new TradePlan(current.getId(), seen.trades().size() + 1,
				request.direction(), request.stopTicks(), request.targetTicks(), request.contracts(),
				reference, current.getCursorTime(), request.model(),
				request.bias() == null ? null : request.bias().name(), request.dolTicks(),
				context.killzone() == null ? null : context.killzone().name(),
				context.suggestion().bias() == null ? null : context.suggestion().bias().name(),
				context.checklist() == null ? null : stored(context.checklist()), request.confidence(), riskLimit,
				request.partial()));
			PracticeResult result = results.save(new PracticeResult(plan, fill,
				instrument.getTickValueCents(), model, current.getGeneratorVersion(), current.getSeed(),
				current.getDataSource()));
			current.setStatus(Status.IN_TRADE);
			sessions.saveAndFlush(current);
			List<Trade> trades = new ArrayList<>(seen.trades());
			trades.add(new Trade(plan, result, null, instrument.getTickValueCents()));
			return state(current, instrument, trades);
		});
	}

	/**
	 * What the app sees for a bracket being drafted (T22, DEC-066): the killzone, the daily-bias-1.0.0
	 * suggestion, and the named model's checklist — read, never written. Placing the plan reads it again.
	 */
	public PlanContextView context(UUID userId, UUID sessionId, ContextRequest request) {
		Snapshot seen = snapshot(userId, sessionId);
		PracticeSession session = seen.session();
		if (session.getStatus() == Status.COMPLETE) {
			throw new SessionEndedException();
		}
		Resolution resolution = seen.instrument().getDataClass() == DataClass.SIMULATED ? Resolution.S1 : Resolution.M1;
		long reference = lastVisibleClose(session, seen.instrument(), resolution);
		PlanContext context = contexts.at(session, seen.instrument(), request.direction(), reference, request.stopTicks(),
			request.targetTicks(), request.model());
		return new PlanContextView(context.killzone() == null ? null : context.killzone().name(),
			context.suggestion().bias() == null ? null : context.suggestion().bias().name(), context.suggestion().reasoning(),
			DailyBias.VERSION, context.checklist() == null ? null : checklistView(stored(context.checklist())));
	}

	/** A checklist as kept with the plan: its model, version and items. */
	private static Map<String, Object> stored(ModelChecklist.Checklist checklist) {
		return Map.of("model", checklist.model().name(), "version", checklist.version(), "items",
			checklist.items().stream().map(i -> Map.<String, Object>of("id", i.id(), "text", i.text(), "met", i.met())).toList());
	}

	@SuppressWarnings("unchecked")
	private static ChecklistView checklistView(Map<String, Object> stored) {
		TradingModel model = TradingModel.valueOf((String) stored.get("model"));
		List<ChecklistItemView> items = ((List<Map<String, Object>>) stored.get("items")).stream()
			.map(i -> new ChecklistItemView((String) i.get("id"), (String) i.get("text"), Boolean.TRUE.equals(i.get("met"))))
			.toList();
		return new ChecklistView(model, model.title(), (String) stored.get("version"), items,
			(int) items.stream().filter(ChecklistItemView::met).count(), items.size());
	}

	/**
	 * Reveals the next {@code count} candles of {@code timeframeCode} — the session's own when null.
	 * A candle still forming at the cursor counts as the first: stepping completes it. Skips closed
	 * market, so a candle is always a candle.
	 */
	public AdvanceResponse advance(UUID userId, UUID sessionId, int count, String timeframeCode) {
		if (count < 1 || count > MAX_ADVANCE_BARS) {
			throw new IllegalArgumentException(
				"Advance between 1 and " + MAX_ADVANCE_BARS + " bars at a time.");
		}
		Snapshot seen = snapshot(userId, sessionId);
		PracticeSession session = seen.session();
		Instrument instrument = seen.instrument();
		Timeframe timeframe = viewOf(session, timeframeCode);

		Instant cursor = session.getCursorTime();
		Instant limit = replayLimit(seen, timeframe);
		if (!cursor.isBefore(limit)) {
			throw new ReplayEndException();
		}

		// From the start of the candle the cursor is in: when it is still forming, its whole candle
		// goes back to the client, which replaces the part it had.
		Instant from = startOfBarContaining(cursor, timeframe);
		Instant to = min(cursor.plus(timeframe.duration().multipliedBy(count)), limit);
		List<Bar> bars = generate(session, instrument, timeframe, from, to);
		if (bars.size() < count && to.isBefore(limit)) {
			// The window crossed a closed market — a maintenance break or a weekend — so it held
			// fewer bars than asked for. Look far enough ahead to reach the next session.
			to = min(to.plus(CLOSED_MARKET_LOOKAHEAD), limit);
			bars = generate(session, instrument, timeframe, from, to);
		}
		if (bars.isEmpty()) {
			// Closed market all the way to {@code to}: nothing to show, but the replay still moves
			// over it — or a flat session whose last hours are a weekend could never reach its end.
			return reveal(userId, seen, to, bars);
		}
		List<Bar> revealed = bars.size() > count ? bars.subList(0, count) : bars;
		// Never past the limit: a candle the limit cuts through stays forming.
		return reveal(userId, seen, min(revealed.getLast().openTime().plus(timeframe.duration()), limit),
			revealed);
	}

	/** Jumps to the end of the candle, of {@code timeframeCode}, in which the running trade ended. */
	public AdvanceResponse skipToResult(UUID userId, UUID sessionId, String timeframeCode) {
		Snapshot seen = snapshot(userId, sessionId);
		PracticeResult open = seen.openResult();
		if (open == null) {
			throw new NotInTradeException("skip to the end of");
		}
		Timeframe timeframe = viewOf(seen.session(), timeframeCode);
		Instant end = endOfBarContaining(open.getExitTime(), timeframe);
		List<Bar> bars = generate(seen.session(), seen.instrument(), timeframe,
			startOfBarContaining(seen.session().getCursorTime(), timeframe), end);
		return reveal(userId, seen, end, bars);
	}

	/**
	 * Closes the running trade at market (DEC-057), as on any real platform.
	 *
	 * <p>It exits at the next price there is — the open of the first bar of the fill engine's
	 * resolution at or after the cursor, as the entry did — and the replay moves to that moment, one
	 * such bar on, so the fill is seen at once. When that very price reaches the trade's own stop or
	 * target, the resting order fills first: then it is the plan's exit that is revealed, as Skip to
	 * result would. The planned result is left as it was written at the lock.
	 */
	public AdvanceResponse close(UUID userId, UUID sessionId, String timeframeCode) {
		Snapshot seen = snapshot(userId, sessionId);
		PracticeResult open = seen.openResult();
		if (open == null) {
			throw new NotInTradeException("close");
		}
		PracticeSession session = seen.session();
		TradePlan plan = seen.trades().getLast().plan();
		Resolution resolution = Resolution.valueOf(open.getResolution());
		Instant locked = plan.getSubmittedCursor();
		// The same bars the trade was resolved on at the lock: the data does not change.
		List<Bar> walked = fine(session, seen.instrument(), resolution, locked, sessionEnd(locked));
		// Filled as the plan was resolved: its own model, whatever the costs are set to now (T27).
		FillModel model = FillModel.parse(open.getFillModelVersion());
		Fill fill = FillEngine.closeAtMarket(plan.enginePlan(), walked, session.getCursorTime(), model, resolution);
		if (fill == null) {
			return skipToResult(userId, sessionId, timeframeCode);
		}
		Timeframe timeframe = viewOf(session, timeframeCode);
		Instant cursor = fill.exitTime().plus(resolution == Resolution.S1 ? Duration.ofSeconds(1) : Duration.ofMinutes(1));
		List<Bar> bars = generate(session, seen.instrument(), timeframe,
			startOfBarContaining(session.getCursorTime(), timeframe), cursor);
		return writeTx.execute(tx -> {
			PracticeSession current = requireUnchanged(userId, session);
			PracticeClose saved = closes.save(new PracticeClose(plan, fill, seen.instrument().getTickValueCents(), model));
			current.moveCursorTo(cursor);
			current.setStatus(Status.ANALYSING);
			settle(current, null);
			sessions.saveAndFlush(current);
			List<Trade> trades = new ArrayList<>(seen.trades());
			trades.set(trades.size() - 1, new Trade(plan, open, saved, seen.instrument().getTickValueCents()));
			return new AdvanceResponse(state(current, seen.instrument(), trades), toDto(bars, current.getClock()));
		});
	}

	/**
	 * Ends the session. No more trades — and now that nothing can be traded on it, the seed of a
	 * simulated market and the date of a real one are revealed. Ending an ended session changes
	 * nothing, so a double-click is harmless.
	 */
	public PracticeState end(UUID userId, UUID sessionId) {
		Snapshot seen = snapshot(userId, sessionId);
		switch (seen.session().getStatus()) {
			case IN_TRADE -> throw new TradeRunningException();
			case COMPLETE -> {
				return state(seen.session(), seen.instrument(), seen.trades());
			}
			case ANALYSING -> {
				// Flat: nothing to wait for.
			}
		}
		return writeTx.execute(tx -> {
			PracticeSession current = requireUnchanged(userId, seen.session());
			current.setStatus(Status.COMPLETE);
			sessions.saveAndFlush(current);
			return state(current, seen.instrument(), seen.trades());
		});
	}

	/**
	 * The write half of advance and skip: move the cursor, then {@link #settle} the session. Refuses
	 * if the session changed after the bars were generated for it.
	 */
	private AdvanceResponse reveal(UUID userId, Snapshot seen, Instant newCursor, List<Bar> bars) {
		return writeTx.execute(tx -> {
			PracticeSession current = requireUnchanged(userId, seen.session());
			current.moveCursorTo(newCursor);
			settle(current, seen.openResult());
			sessions.saveAndFlush(current);
			// The snapshot's trades are still current: locking one bumps the session's version, and
			// requireUnchanged has just confirmed it did not move.
			return new AdvanceResponse(state(current, seen.instrument(), seen.trades()),
				toDto(bars, current.getClock()));
		});
	}

	// ---------------------------------------------------------------------------------------------
	// Queries
	// ---------------------------------------------------------------------------------------------

	/** The session, and every candle of {@code timeframeCode} up to the cursor — the last perhaps still forming. */
	public SessionResponse get(UUID userId, UUID sessionId, String timeframeCode) {
		Snapshot seen = snapshot(userId, sessionId);
		Timeframe timeframe = viewOf(seen.session(), timeframeCode);
		// Generated outside the snapshot's transaction (BUG-033).
		return new SessionResponse(state(seen.session(), seen.instrument(), seen.trades()),
			visibleBars(seen.session(), seen.instrument(), timeframe));
	}

	/**
	 * The New York clock over the replay so far (T7): built from the bars before the cursor and
	 * nothing else (DEC-009), its times on the session's clock (DEC-045). It carries the moment it can
	 * next change, so a replay asks again only when that moment is passed.
	 */
	public ClockLayerResponse clock(UUID userId, UUID sessionId) {
		Snapshot seen = snapshot(userId, sessionId);
		PracticeSession session = seen.session();
		ClockLayer layer = clocks.layer(seen.instrument(), session.getHistoryFrom(),
			session.getCursorTime(), seedOf(session));
		return ClockDtos.of(layer, session.getClock()::show, true);
	}

	/**
	 * Detected setups over the replay so far (T9): only hits confirmed by the cursor (DEC-009), on the
	 * session's clock (DEC-045). The replay's whole window is scanned once, past the cursor, and
	 * filtered as the cursor moves — what lies beyond it stays on the server.
	 */
	public SetupLayerResponse setups(UUID userId, UUID sessionId, List<String> detectors, String timeframeCode) {
		Snapshot seen = snapshot(userId, sessionId);
		PracticeSession session = seen.session();
		Instant cursor = session.getCursorTime();
		Instant end = replayEnd(session);
		return setupService.forReplay(seen.instrument(), viewOf(session, timeframeCode),
			seedOf(session), historyFrom(session, seen.instrument(), viewOf(session, timeframeCode)),
			cursor.isAfter(end) ? cursor : end, cursor, detectors,
			session.getClock()::show);
	}

	@Transactional(readOnly = true)
	public List<SessionSummary> journal(UUID userId) {
		List<PracticeSession> recent = sessions.findTop50ByUserIdOrderByCreatedAtDesc(userId);
		List<UUID> ids = recent.stream().map(PracticeSession::getId).toList();
		Map<UUID, List<TradePlan>> plansBySession = plans.findAllBySessionIdIn(ids).stream()
			.collect(Collectors.groupingBy(TradePlan::getSessionId));
		Map<UUID, List<PracticeResult>> resultsBySession = results.findAllBySessionIdIn(ids).stream()
			.collect(Collectors.groupingBy(PracticeResult::getSessionId));
		Map<UUID, List<PracticeClose>> closesBySession = closes.findAllBySessionIdIn(ids).stream()
			.collect(Collectors.groupingBy(PracticeClose::getSessionId));
		Map<UUID, Instrument> byId = instruments.findAll().stream()
			.collect(Collectors.toMap(Instrument::getId, Function.identity()));

		return recent.stream().map(s -> {
			Instrument instrument = byId.get(s.getInstrumentId());
			List<Trade> trades = pair(plansBySession.getOrDefault(s.getId(), List.of()),
				resultsBySession.getOrDefault(s.getId(), List.of()), closesBySession.getOrDefault(s.getId(), List.of()),
				instrument.getTickValueCents());
			// The journal is a listing, and a listing is an easy place to leak an outcome by
			// accident. Same rule as everywhere else: a trade counts once its exit is revealed.
			return new SessionSummary(
				s.getId(),
				instrument.getSymbol(),
				s.getTimeframe(),
				s.getStatus().name(),
				s.getCreatedAt().getEpochSecond(),
				trades.size(),
				score(trades, s.getCursorTime()));
		}).toList();
	}

	/**
	 * Calibration (T23, DEC-067): every finished trade of the learner's that says how sure they were, against
	 * how it ended. A trade counts once its exit is revealed — the same rule as the score.
	 */
	@Transactional(readOnly = true)
	public CalibrationView calibration(UUID userId) {
		return Calibration.of(finished(userId).stream()
			.filter(f -> f.trade().plan().getConfidence() != null)
			.map(f -> new Calibration.Sample(f.trade().plan().getConfidence(), f.trade().outcome()))
			.toList());
	}

	/**
	 * Journal statistics (T25, DEC-069): every finished trade of the learner's, added up overall and by model,
	 * killzone, weekday, side, model checks and own rules — the rules judged as the scorecard judges them.
	 */
	@Transactional(readOnly = true)
	public StatisticsView statistics(UUID userId) {
		Map<UUID, Instrument> byId = instruments.findAll().stream()
			.collect(Collectors.toMap(Instrument::getId, Function.identity()));
		return JournalStatistics.of(finished(userId).stream().map(f -> {
			Trade trade = f.trade();
			PracticeClock clock = f.session().getClock();
			Instrument instrument = byId.get(f.session().getInstrumentId());
			PlanView plan = planView(trade.plan(), clock);
			ScorecardView card = Scorecard.score(plan, resultView(trade, clock), instrument.getTickSize(),
				instrument.getTickValueCents());
			return new JournalStatistics.Sample(round2(trade.r()), trade.netPnlCents(),
				plan.model() == null ? null : plan.model().title(), plan.killzone(),
				trade.plan().getSubmittedCursor().atZone(PracticeClock.NEW_YORK).getDayOfWeek(), plan.direction(),
				plan.checklist() == null ? null : plan.checklist().met(),
				plan.checklist() == null ? null : plan.checklist().total(),
				(int) card.process().stream().filter(l -> Scorecard.KEPT.equals(l.verdict())).count(),
				(int) card.process().stream().filter(l -> Scorecard.BROKEN.equals(l.verdict())).count(),
				trade.outcome() == Outcome.CLOSED,
				Scorecard.costsCents(plan, resultView(trade, clock), instrument.getTickValueCents()));
		}).toList());
	}

	/**
	 * The journal (T41): the learner's finished trades across every session, newest placed first. A session still
	 * open shows its own clock, the date hidden; a complete one has revealed its date and shows the real moment.
	 */
	@Transactional(readOnly = true)
	public List<JournalTradeView> journal(UUID userId, int limit) {
		Map<UUID, Instrument> byId = instruments.findAll().stream()
			.collect(Collectors.toMap(Instrument::getId, Function.identity()));
		return finished(userId).stream()
			// BUG-158: newest placed in real time. The replay's moment is a random day of history: sorted by it, today's
			// trade could land behind months of earlier practice, or past the limit.
			.sorted(Comparator.comparing((Finished f) -> f.trade().plan().getSubmittedAt())
				.thenComparing(f -> f.trade().plan().getId()).reversed())
			.limit(limit)
			.map(f -> {
				Trade trade = f.trade();
				PracticeSession session = f.session();
				PracticeClock clock = session.getClock();
				boolean revealed = session.getStatus() == PracticeSession.Status.COMPLETE || !clock.hidesDate();
				Instant placed = trade.plan().getSubmittedCursor();
				Instrument instrument = byId.get(session.getInstrumentId());
				TradingModel model = trade.plan().getModel();
				return new JournalTradeView(session.getId(), trade.plan().getSeq(),
					revealed ? placed.getEpochSecond() : clock.show(placed),
					revealed ? PracticeClock.UTC : PracticeClock.NEW_YORK_WALL_CLOCK, instrument.getSymbol(),
					instrument.getTickSize(), trade.plan().getDirection(), trade.result().getEntryTicks(),
					trade.outcome().name(), round2(trade.r()), trade.netPnlCents(), model == null ? null : model.title());
			})
			.toList();
	}

	/** A trade whose exit the replay has shown, and its session. */
	private record Finished(Trade trade, PracticeSession session) {
	}

	/** Every trade of the learner's, from all their sessions, whose exit the replay has shown (DEC-009). */
	private List<Finished> finished(UUID userId) {
		Map<UUID, List<TradePlan>> bySession = plans.findAllOfUser(userId).stream()
			.collect(Collectors.groupingBy(TradePlan::getSessionId));
		Set<UUID> ids = bySession.keySet();
		Map<UUID, PracticeSession> owned = sessions.findAllById(ids).stream()
			.collect(Collectors.toMap(PracticeSession::getId, Function.identity()));
		Map<UUID, List<PracticeResult>> resultsBySession = results.findAllBySessionIdIn(ids).stream()
			.collect(Collectors.groupingBy(PracticeResult::getSessionId));
		Map<UUID, List<PracticeClose>> closesBySession = closes.findAllBySessionIdIn(ids).stream()
			.collect(Collectors.groupingBy(PracticeClose::getSessionId));
		Map<UUID, Long> tickValues = instruments.findAll().stream()
			.collect(Collectors.toMap(Instrument::getId, Instrument::getTickValueCents));
		List<Finished> done = new ArrayList<>();
		bySession.forEach((id, sessionPlans) -> {
			PracticeSession session = owned.get(id);
			for (Trade trade : pair(sessionPlans, resultsBySession.getOrDefault(id, List.of()),
					closesBySession.getOrDefault(id, List.of()), tickValues.get(session.getInstrumentId()))) {
				if (trade.closedBy(session.getCursorTime())) {
					done.add(new Finished(trade, session));
				}
			}
		});
		return done;
	}

	/**
	 * A finished trade told as it happened (T24, DEC-068) — once the replay has passed its exit, and never with
	 * anything past the cursor.
	 */
	public ReviewView review(UUID userId, UUID sessionId, int number) {
		Snapshot seen = snapshot(userId, sessionId);
		Trade trade = seen.trades().stream().filter(t -> t.plan().getSeq() == number).findFirst()
			.orElseThrow(TradeNotFoundException::new);
		if (!trade.closedBy(seen.session().getCursorTime())) {
			throw new ReviewNotReadyException();
		}
		TradePlan plan = trade.plan();
		ChecklistView checklist = plan.getChecklist() == null ? null : checklistView(plan.getChecklist());
		return reviews.review(seen.session(), seen.instrument(), plan, trade.result(), trade.close(), round2(trade.r()),
			round2(trade.plannedR()), checklist == null ? null
				: checklist.title() + ": " + checklist.met() + " of " + checklist.total() + " checks met when placed.");
	}

	public SettingsView settings(UUID userId) {
		return settingsView(settings.riskLimitCents(userId));
	}

	/**
	 * Sets the risk limit, or unsets it. Costs sent too are refused, not ignored (T41, DEC-080): a client that thinks it
	 * changed them must be told it did not.
	 */
	public SettingsView saveSettings(UUID userId, SettingsRequest request) {
		if (request.feesCents() != null || request.marketSlippageTicks() != null || request.stopSlippageTicks() != null
				|| request.limitThroughTicks() != null) {
			throw new IllegalArgumentException("Costs are fixed: every trade pays the standard fees and slippage.");
		}
		settings.saveRiskLimit(userId, request.riskLimitCents());
		return settingsView(request.riskLimitCents());
	}

	/** The limit as set, and the costs every trade pays — to read, not to set. */
	private SettingsView settingsView(Long riskLimitCents) {
		FillModel model = costs.model();
		return new SettingsView(riskLimitCents, model.commissionCentsPerContractRound(), model.marketSlippageTicks(),
			model.stopSlippageTicks(), model.limitThroughTicks(), model.version());
	}

	// ---------------------------------------------------------------------------------------------
	// Views
	// ---------------------------------------------------------------------------------------------

	/** Built from rows already read — no queries here, so it is safe outside a transaction. */
	private PracticeState state(PracticeSession session, Instrument instrument, List<Trade> trades) {
		boolean complete = session.getStatus() == Status.COMPLETE;
		PracticeClock clock = session.getClock();
		Instant cursor = session.getCursorTime();

		return new PracticeState(
			session.getId(),
			instrument.getSymbol(),
			instrument.getTickSize(),
			instrument.getTickValueCents(),
			instrument.getExchangeTimezone(),
			session.getTimeframe(),
			session.getStatus().name(),
			clock.show(session.getStartTime()),
			clock.show(cursor),
			clock.basis(),
			instrument.getDataClass().name(),
			instrument.getDataClass() == DataClass.SIMULATED ? SIMULATED_DISCLAIMER : null,
			trades.stream().map(t -> tradeView(t, cursor, clock, instrument)).toList(),
			score(trades, cursor),
			// DEC-038 and DEC-025: the seed and the real date are the whole future, revealed only
			// once the session has ended and no trade can use them.
			complete && session.getSeed() != null ? Long.toString(session.getSeed()) : null,
			session.getGeneratorVersion(),
			session.getDataSource(),
			complete && clock.hidesDate() ? session.getStartTime().getEpochSecond() : null);
	}

	private static TradeView tradeView(Trade trade, Instant cursor, PracticeClock clock, Instrument instrument) {
		PracticeResult planned = trade.result();
		// The entry is the open of the first bar after the plan was locked. It is revealed with that
		// bar, not before: the price a market order will fill at is itself the future.
		EntryView entry = cursor.isAfter(planned.getEntryTime())
			? new EntryView(clock.show(planned.getEntryTime()), planned.getEntryTicks())
			: null;
		PlanView plan = planView(trade.plan(), clock);
		// T28: a partial is revealed with the bar it filled in, like the entry — while the rest still runs.
		Leg taken = trade.partial();
		PartialView partial = taken != null && cursor.isAfter(taken.time()) ? partialView(taken, clock) : null;
		if (!trade.closedBy(cursor)) {
			return new TradeView(trade.plan().getSeq(), plan, entry, null, null, partial);
		}
		ResultView result = resultView(trade, clock);
		return new TradeView(trade.plan().getSeq(), plan, entry, result,
			Scorecard.score(plan, result, instrument.getTickSize(), instrument.getTickValueCents()), partial);
	}

	/** The trades the replay has passed the exit of, added up. */
	private static ScoreView score(List<Trade> trades, Instant cursor) {
		int closed = 0;
		int wins = 0;
		int losses = 0;
		double totalR = 0;
		long net = 0;
		for (Trade trade : trades) {
			if (!trade.closedBy(cursor)) {
				continue;
			}
			closed++;
			wins += trade.won() ? 1 : 0;
			losses += trade.lost() ? 1 : 0;
			// The sum of the R column as the learner sees it, so adding it up by hand agrees with
			// the total. Summing the unrounded values could differ in the last digit (BUG-039).
			totalR += round2(trade.r());
			net += trade.netPnlCents();
		}
		return new ScoreView(closed, wins, losses, round2(totalR), net);
	}

	private static PlanView planView(TradePlan plan, PracticeClock clock) {
		long risk = Math.abs(plan.getReferenceTicks() - plan.getStopTicks());
		long reward = Math.abs(plan.getTargetTicks() - plan.getReferenceTicks());
		FillEngine.Partial partial = plan.partial();
		// With a partial, the whole position's: its contracts to their level, the rest to the target (T28).
		long rewardTicks = partial == null ? reward * plan.getContracts()
			: partial.contracts() * Math.abs(partial.ticks() - plan.getReferenceTicks())
				+ (plan.getContracts() - partial.contracts()) * reward;
		return new PlanView(plan.getDirection(), plan.getStopTicks(), plan.getTargetTicks(),
			plan.getContracts(), plan.getReferenceTicks(), clock.show(plan.getSubmittedCursor()),
			round2((double) rewardTicks / (risk * plan.getContracts())), plan.getModel(), plan.getBias(), plan.getDolTicks(), plan.killzoneWhenPlaced(),
			plan.getSuggestedBias(), plan.getChecklist() == null ? null : checklistView(plan.getChecklist()),
			plan.getConfidence(), plan.getRiskLimitCents(), partial == null ? null : partial.ticks(),
			partial == null ? null : partial.contracts(), partial == null ? null : partial.breakeven());
	}

	private static PartialView partialView(Leg leg, PracticeClock clock) {
		return leg == null ? null
			: new PartialView(clock.show(leg.time()), leg.exitTicks(), leg.contracts(), leg.pnlTicks(), leg.gap());
	}

	private static ResultView resultView(Trade trade, PracticeClock clock) {
		PracticeResult result = trade.result();
		PracticeClose close = trade.close();
		if (close != null) {
			return new ResultView(Outcome.CLOSED.name(), null, clock.show(result.getEntryTime()), result.getEntryTicks(),
				clock.show(close.getExitTime()), close.getExitTicks(), close.getPnlTicks(), close.getNetPnlCents(),
				close.getCommissionCents(), close.getMaeTicks(), close.getMfeTicks(), false, round2(trade.r()),
				close.getResolution(), close.getEngineVersion(), close.getFillModelVersion(),
				partialView(close.partial(), clock));
		}
		return new ResultView(
			result.getOutcome().name(),
			result.getOutcome() == Outcome.AMBIGUOUS
				? ambiguousAssumption(Resolution.valueOf(result.getResolution()),
					trade.plan().partial() != null && result.partial() == null)
				: null,
			clock.show(result.getEntryTime()),
			result.getEntryTicks(),
			clock.show(result.getExitTime()),
			result.getExitTicks(),
			result.getPnlTicks(),
			result.getNetPnlCents(),
			result.getCommissionCents(),
			result.getMaeTicks(),
			result.getMfeTicks(),
			result.isGapExit(),
			round2(trade.r()),
			result.getResolution(),
			result.getEngineVersion(),
			result.getFillModelVersion(),
			partialView(result.partial(), clock));
	}

	/**
	 * INVARIANT 6: an assumed outcome says so, in the unit of the bars that could not decide it.
	 *
	 * @param partialLevel the partial was still to be taken, so its level was among those touched (T28)
	 */
	static String ambiguousAssumption(Resolution resolution, boolean partialLevel) {
		return "The stop and " + (partialLevel ? "the price to take part off at" : "the target")
			+ " were both touched inside the same " + resolution.unit()
			+ ", so which came first cannot be known from this data. The result assumes the worst "
			+ "case: the stop" + (partialLevel ? ", for every contract." : ".");
	}

	// ---------------------------------------------------------------------------------------------
	// Internals
	// ---------------------------------------------------------------------------------------------

	private PracticeSession require(UUID userId, UUID sessionId) {
		return sessions.findByIdAndUserId(sessionId, userId)
			.orElseThrow(SessionNotFoundException::new);
	}

	private Instrument instrumentOf(PracticeSession session) {
		return instruments.findById(session.getInstrumentId())
			.orElseThrow(() -> new IllegalStateException(
				"Session " + session.getId() + " refers to a missing instrument"));
	}

	/** Reads a session with its trades and instrument as one consistent snapshot. */
	private Snapshot snapshot(UUID userId, UUID sessionId) {
		return readTx.execute(tx -> {
			PracticeSession session = require(userId, sessionId);
			Instrument instrument = instrumentOf(session);
			return new Snapshot(session, instrument,
				pair(plans.findAllBySessionId(session.getId()), results.findAllBySessionId(session.getId()),
					closes.findAllBySessionId(session.getId()), instrument.getTickValueCents()));
		});
	}

	/** Each plan with its result and any close, in the order the plans were locked. */
	private static List<Trade> pair(List<TradePlan> plans, List<PracticeResult> results, List<PracticeClose> closed,
			long tickValueCents) {
		Map<Integer, PracticeResult> bySeq = results.stream()
			.collect(Collectors.toMap(PracticeResult::getSeq, Function.identity()));
		Map<Integer, PracticeClose> closeBySeq = closed.stream()
			.collect(Collectors.toMap(PracticeClose::getSeq, Function.identity()));
		return plans.stream()
			.sorted(Comparator.comparingInt(TradePlan::getSeq))
			.map(plan -> {
				PracticeResult result = bySeq.get(plan.getSeq());
				if (result == null) {
					throw new IllegalStateException("Plan " + plan.getId() + " has no result");
				}
				return new Trade(plan, result, closeBySeq.get(plan.getSeq()), tickValueCents);
			})
			.toList();
	}

	/**
	 * Re-reads a session inside a write, refusing if it changed since {@code seen} was read. Whatever
	 * was generated in between was generated for the old state, and must not be applied to a new one.
	 */
	private PracticeSession requireUnchanged(UUID userId, PracticeSession seen) {
		PracticeSession current = require(userId, seen.getId());
		if (current.getVersion() != seen.getVersion()) {
			throw new SessionChangedException();
		}
		return current;
	}

	/**
	 * Everything from the start of the history to the cursor — and, by construction, nothing after.
	 * The range handed to the generator ends at the cursor, so no later bar is ever produced.
	 */
	private List<BarResponse> visibleBars(PracticeSession session, Instrument instrument, Timeframe timeframe) {
		return toDto(generate(session, instrument, timeframe, historyFrom(session, instrument, timeframe),
			session.getCursorTime()), session.getClock());
	}

	private List<Bar> generate(PracticeSession session, Instrument instrument, Timeframe timeframe,
			Instant from, Instant to) {
		return marketData.internalBars(instrument, timeframe, from, to, seedOf(session)).bars();
	}

	/** Bars at the fill engine's resolution. */
	private List<Bar> fine(PracticeSession session, Instrument instrument, Resolution resolution,
			Instant from, Instant to) {
		Timeframe timeframe = resolution == Resolution.S1 ? Timeframe.S1 : Timeframe.M1;
		return marketData.internalBars(instrument, timeframe, from, to, seedOf(session)).bars();
	}

	/** Real sessions have no seed; the providers that serve them ignore the argument. */
	private static long seedOf(PracticeSession session) {
		return session.getSeed() == null ? 0 : session.getSeed();
	}

	/**
	 * The last traded price before the cursor: what the learner is looking at. Looks back one bar of
	 * the chart's timeframe on simulated data, which never pauses, and up to an hour on real data,
	 * where a quiet stretch has no bars.
	 */
	private long lastVisibleClose(PracticeSession session, Instrument instrument,
			Resolution resolution) {
		Timeframe timeframe = Timeframe.fromCode(session.getTimeframe());
		Instant cursor = session.getCursorTime();
		Duration lookback = instrument.getDataClass() == DataClass.SIMULATED
			? timeframe.duration()
			: Duration.ofHours(1);
		List<Bar> recent = fine(session, instrument, resolution, cursor.minus(lookback), cursor);
		if (recent.isEmpty()) {
			throw new IllegalStateException("No visible price before the cursor at " + cursor);
		}
		return recent.getLast().closeTicks();
	}

	private static void validateBracket(PlanRequest request, long reference) {
		long stop = request.stopTicks();
		long target = request.targetTicks();
		boolean sidesOk = request.direction() == Direction.LONG
			? stop < reference && target > reference
			: stop > reference && target < reference;
		if (!sidesOk) {
			throw new IllegalArgumentException(request.direction() == Direction.LONG
				? "A long needs its stop below the current price (" + reference
					+ " ticks) and its target above it."
				: "A short needs its stop above the current price (" + reference
					+ " ticks) and its target below it.");
		}
		// Compared without subtracting: stop - reference overflows for a stop near Long.MIN_VALUE, and
		// Math.abs of the overflow is negative — a stop that far away passed the check (BUG-071).
		Long dol = request.dolTicks();
		if (dol != null && (dol < reference - MAX_BRACKET_TICKS || dol > reference + MAX_BRACKET_TICKS)) {
			throw new IllegalArgumentException(
				"The liquidity you expect price to reach must be within " + MAX_BRACKET_TICKS + " ticks of the price.");
		}
		if (stop < reference - MAX_BRACKET_TICKS || stop > reference + MAX_BRACKET_TICKS
				|| target < reference - MAX_BRACKET_TICKS || target > reference + MAX_BRACKET_TICKS) {
			throw new IllegalArgumentException(
				"Stop and target must be within " + MAX_BRACKET_TICKS + " ticks of the price.");
		}
	}

	/** T28: some of the contracts, at a price between the current price and the target. */
	private static void validatePartial(PlanRequest request, long reference) {
		Long at = request.partialTicks();
		if (at == null) {
			if (request.partialContracts() != null || Boolean.TRUE.equals(request.breakevenAfterPartial())) {
				throw new IllegalArgumentException("Taking part off needs a price to take it off at.");
			}
			return;
		}
		Integer some = request.partialContracts();
		if (some == null || some < 1 || some >= request.contracts()) {
			throw new IllegalArgumentException("A partial takes off some of the contracts: at least one, and fewer than all "
				+ request.contracts() + ".");
		}
		boolean between = request.direction() == Direction.LONG
			? at > reference && at < request.targetTicks()
			: at < reference && at > request.targetTicks();
		if (!between) {
			throw new IllegalArgumentException("The partial's price must lie between the current price (" + reference
				+ " ticks) and the target.");
		}
	}

	private long newSeed() {
		long seed;
		do {
			seed = random.nextLong();
		} while (seed == MarketDataService.PUBLIC_CHART_SEED);
		return seed;
	}

	/** A random, bar-aligned instant with open market just before it and for a while after. */
	private Instant pickStart(Timeframe timeframe, Killzone killzone, DayOfWeek weekday) {
		for (int attempt = 0; attempt < 10_000; attempt++) {
			Instant start = candidate(EARLIEST_START, LATEST_START, timeframe, killzone);
			if (matches(start, weekday) && SessionCalendar.isOpenThroughout(
					start.minus(timeframe.duration()), start.plus(MIN_SESSION_LEFT))) {
				return start;
			}
		}
		// The market is open ~70% of the week; ten thousand misses in a row means the filters ask for a
		// moment the market never trades at — a client error, not a server one (BUG-067).
		throw new IllegalArgumentException("No open-market moment matches those filters.");
	}

	/**
	 * A random moment in the imported history where the market really traded: open by the calendar,
	 * and with bars around it — the calendar knows the hours, not the holidays (T4).
	 */
	private Instant pickRealStart(Instrument instrument, Timeframe timeframe, Killzone killzone,
			DayOfWeek weekday, List<HiddenStretches.Stretch> avoid) {
		Instant[] coverage = marketData.coverage(instrument);
		if (coverage == null) {
			throw new NoHistoryException(instrument.getSymbol(), "practise on");
		}
		Instant earliest = coverage[0].plus(HISTORY);
		Instant latest = coverage[1].minus(MIN_SESSION_LEFT);
		if (!earliest.isBefore(latest)) {
			throw new IllegalArgumentException("Too little " + instrument.getSymbol()
				+ " history has been imported to practise on.");
		}
		for (int attempt = 0; attempt < PICK_ATTEMPTS; attempt++) {
			Instant start = candidate(earliest, latest, timeframe, killzone);
			if (!matches(start, weekday) || start.isBefore(earliest) || start.isAfter(latest)
					|| !SessionCalendar.isOpenThroughout(start.minus(timeframe.duration()),
						start.plus(MIN_SESSION_LEFT))) {
				continue;
			}
			// BUG-085, BUG-086: not over another open session's replay or the drill being answered — this one's
			// history and replay would show what those are graded by.
			if (HiddenStretches.touches(avoid, start.minus(HISTORY), start.plus(HiddenStretches.PAST_REPLAY_START))) {
				continue;
			}
			// Only now the database: most candidates fail the cheap checks above.
			if (marketData.storedMinutes(instrument, start.minus(Duration.ofHours(1)), start)
					>= MIN_MINUTES_BEFORE
				&& marketData.storedMinutes(instrument, start, start.plus(MIN_SESSION_LEFT))
					>= MIN_MINUTES_AFTER) {
				return start;
			}
		}
		throw new IllegalArgumentException("No moment in the imported history matches those filters.");
	}

	/**
	 * A random bar-aligned instant in {@code [from, to)} — inside {@code killzone} when one is given,
	 * by choosing a day and then a minute of the window, rather than hoping to land in it.
	 */
	private Instant candidate(Instant from, Instant to, Timeframe timeframe, Killzone killzone) {
		long step = timeframe.seconds();
		if (killzone == null) {
			long second = from.getEpochSecond()
				+ random.nextLong(to.getEpochSecond() - from.getEpochSecond());
			return Instant.ofEpochSecond(second - Math.floorMod(second, step));
		}
		LocalDate first = from.atZone(Killzone.NEW_YORK).toLocalDate();
		long days = Math.max(1, ChronoUnit.DAYS.between(first, to.atZone(Killzone.NEW_YORK).toLocalDate()));
		LocalDate day = first.plusDays(random.nextLong(days));
		long stepMinutes = Math.max(1, step / 60);
		long slots = Math.max(1, killzone.minutes() / stepMinutes);
		LocalTime time = killzone.start().plusMinutes(random.nextLong(slots) * stepMinutes);
		return ZonedDateTime.of(day, time, Killzone.NEW_YORK).toInstant();
	}

	/** True when no weekday is asked for, or {@code start} falls on it in New York. */
	private static boolean matches(Instant start, DayOfWeek weekday) {
		return weekday == null || start.atZone(Killzone.NEW_YORK).getDayOfWeek() == weekday;
	}

	/** An optional enum parameter: null or blank is "any", anything else must name a value. */
	private static <E extends Enum<E>> E parseEnum(Class<E> type, String code, String what) {
		if (code == null || code.isBlank()) {
			return null;
		}
		try {
			return Enum.valueOf(type, code.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Unknown " + what + " '" + code + "'.");
		}
	}

	/** The first closed minute at or after {@code from}: where a trade entered at {@code from} ends. */
	static Instant sessionEnd(Instant from) {
		Instant t = from;
		Instant cap = from.plus(Duration.ofDays(1));
		while (t.isBefore(cap) && SessionCalendar.isOpen(t)) {
			t = t.plus(Duration.ofMinutes(1));
		}
		return t;
	}

	/** Where a replay stops: no new trade from here, and a flat session here is over. */
	private static Instant replayEnd(PracticeSession session) {
		return session.getStartTime().plus(MAX_REPLAY);
	}

	/**
	 * How far this session may be stepped. A running trade can always be watched to its end, even
	 * past the usual limit, or the learner could be left unable to see how it finished.
	 */
	private static Instant replayLimit(Snapshot seen, Timeframe timeframe) {
		Instant limit = replayEnd(seen.session());
		PracticeResult open = seen.openResult();
		if (open != null) {
			Instant exitBarEnd = endOfBarContaining(open.getExitTime(), timeframe);
			return exitBarEnd.isAfter(limit) ? exitBarEnd : limit;
		}
		return limit;
	}

	/**
	 * After the cursor moves: a running trade whose exit is now revealed is closed, leaving the
	 * learner flat for the next one; and a flat session at the end of its replay is over.
	 */
	private static void settle(PracticeSession session, PracticeResult open) {
		if (open != null && session.getCursorTime().isAfter(open.getExitTime())) {
			session.setStatus(Status.ANALYSING);
		}
		if (session.getStatus() == Status.ANALYSING
				&& !session.getCursorTime().isBefore(replayEnd(session))) {
			session.setStatus(Status.COMPLETE);
		}
	}

	private static Instant endOfBarContaining(Instant instant, Timeframe timeframe) {
		return startOfBarContaining(instant, timeframe).plus(timeframe.duration());
	}

	private static Instant startOfBarContaining(Instant instant, Timeframe timeframe) {
		return timeframe.bucketStart(instant);
	}

	/** Where the history shown in {@code timeframe} begins: further back for a coarse view of real data. */
	private static Instant historyFrom(PracticeSession session, Instrument instrument, Timeframe timeframe) {
		Duration longer = VIEW_HISTORY.get(timeframe);
		if (longer == null || instrument.getDataClass() == DataClass.SIMULATED) {
			return session.getHistoryFrom();
		}
		Instant earlier = session.getStartTime().minus(longer);
		return earlier.isBefore(session.getHistoryFrom()) ? earlier : session.getHistoryFrom();
	}

	/** The timeframe a request looks at the replay in: the session's own unless it names another. */
	private static Timeframe viewOf(PracticeSession session, String code) {
		if (code == null || code.isBlank()) {
			return Timeframe.fromCode(session.getTimeframe());
		}
		Timeframe timeframe = Timeframe.fromCode(code);
		if (!VIEW_TIMEFRAMES.contains(timeframe)) {
			throw new IllegalArgumentException("A replay can be viewed on "
				+ VIEW_TIMEFRAMES.stream().map(Timeframe::code).toList() + " charts.");
		}
		return timeframe;
	}

	private static Instant min(Instant a, Instant b) {
		return a.isBefore(b) ? a : b;
	}

	private static double round2(double value) {
		return Math.round(value * 100.0) / 100.0;
	}

	private static List<BarResponse> toDto(List<Bar> bars, PracticeClock clock) {
		return bars.stream()
			.map(b -> new BarResponse(clock.show(b.openTime()),
				b.openTicks(), b.highTicks(), b.lowTicks(), b.closeTicks(), b.volume()))
			.toList();
	}

	// ---------------------------------------------------------------------------------------------
	// Failures
	// ---------------------------------------------------------------------------------------------

	/** Unknown id, or someone else's. Indistinguishable on purpose. */
	public static class SessionNotFoundException extends RuntimeException {
		public SessionNotFoundException() {
			super("No such practice session.");
		}
	}

	/** No trade with that number in this session. */
	public static class TradeNotFoundException extends RuntimeException {
		public TradeNotFoundException() {
			super("No such trade in this session.");
		}
	}

	public static class ReviewNotReadyException extends RuntimeException {
		public ReviewNotReadyException() {
			super("A trade's review opens once the replay has passed its exit.");
		}
	}

	public static class PlanLockedException extends RuntimeException {
		public PlanLockedException() {
			super("A trade is already running, and a locked plan cannot be changed. Watch it to its "
				+ "end, or skip to the result, before placing the next one.");
		}
	}

	public static class TradeRunningException extends RuntimeException {
		public TradeRunningException() {
			super("Your trade is still running. Watch it to its end, or skip to the result, before "
				+ "ending the session.");
		}
	}

	public static class SessionEndedException extends RuntimeException {
		public SessionEndedException() {
			super("This session has ended. Start a new one to keep trading.");
		}
	}

	public static class TooManyTradesException extends RuntimeException {
		public TooManyTradesException() {
			super("A session holds at most " + MAX_TRADES + " trades. End this one and start another.");
		}
	}

	public static class MarketClosedException extends RuntimeException {
		public MarketClosedException() {
			super("The market is closed at this point in the replay. Step forward to the next "
				+ "session to trade.");
		}
	}

	public static class ReplayEndException extends RuntimeException {
		public ReplayEndException() {
			super("This replay has reached its end.");
		}
	}

	public static class NotInTradeException extends RuntimeException {
		/** @param action what was asked of the trade: "close", "skip to the end of" (BUG-057) */
		public NotInTradeException(String action) {
			super("There is no running trade to " + action + ".");
		}
	}

	/** The session moved between reading it and writing to it — a double-click, a second tab. */
	public static class SessionChangedException extends RuntimeException {
		public SessionChangedException() {
			super("This session was changed by another request. Reload it to see where it stands.");
		}
	}

	public static class TooManySessionsException extends RuntimeException {
		public TooManySessionsException() {
			super("You have started " + MAX_SESSIONS_PER_HOUR + " sessions in the last hour. "
				+ "Please wait before starting another.");
		}
	}
}
