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
import com.tradeez.marketdata.BarSeries;
import com.tradeez.marketdata.DataClass;
import com.tradeez.marketdata.DemoSeriesGenerator;
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
import com.tradeez.practice.FillEngine.Outcome;
import com.tradeez.practice.FillEngine.Resolution;
import com.tradeez.practice.PracticeDtos.AdvanceResponse;
import com.tradeez.practice.PracticeDtos.EntryView;
import com.tradeez.practice.PracticeDtos.PlanRequest;
import com.tradeez.practice.PracticeDtos.PlanView;
import com.tradeez.practice.PracticeDtos.PracticeState;
import com.tradeez.practice.PracticeDtos.ResultView;
import com.tradeez.practice.PracticeDtos.ScoreView;
import com.tradeez.practice.PracticeDtos.SessionResponse;
import com.tradeez.practice.PracticeDtos.SessionSummary;
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
		Timeframe.M15, Timeframe.M30, Timeframe.H1);

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
	private final InstrumentRepository instruments;
	private final MarketDataService marketData;
	private final ClockService clocks;
	private final SetupService setupService;

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
			PracticeResultRepository results, InstrumentRepository instruments,
			MarketDataService marketData, ClockService clocks, SetupService setupService,
			PlatformTransactionManager transactionManager) {
		this.sessions = sessions;
		this.plans = plans;
		this.results = results;
		this.instruments = instruments;
		this.marketData = marketData;
		this.clocks = clocks;
		this.setupService = setupService;
		this.writeTx = new TransactionTemplate(transactionManager);
		this.readTx = new TransactionTemplate(transactionManager);
		this.readTx.setReadOnly(true);
		// One snapshot for the session, its plan and its result. Under PostgreSQL's default
		// READ COMMITTED each statement sees its own moment, and a plan committed between reading
		// the session and reading the plan would produce a state that never existed.
		this.readTx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
	}

	/** A plan and its result. Written together, so neither exists without the other. */
	private record Trade(TradePlan plan, PracticeResult result) {

		/** True once the replay has passed the exit: the result is the past now. */
		boolean closedBy(Instant cursor) {
			return cursor.isAfter(result.getExitTime());
		}

		/**
		 * The result in units of what the learner planned to risk.
		 *
		 * <p>Measured against the plan's reference price, not the fill: R is a statement about the
		 * decision the learner made, and they made it looking at the reference.
		 */
		double r() {
			long risk = Math.abs(plan.getReferenceTicks() - plan.getStopTicks());
			return (double) result.getPnlTicks() / risk;
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
		Instant start = pickRealStart(instrument, timeframe, killzone, weekday);
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

		FillModel model = FillModel.DEFAULT;
		Fill fill = FillEngine.resolve(new FillEngine.Plan(request.direction(), request.stopTicks(),
			request.targetTicks(), request.contracts()), bars, model, resolution);

		return writeTx.execute(tx -> {
			// The fill was computed for the cursor in the snapshot. If the session moved since —
			// a step in another tab — it is a fill for a moment the learner is no longer at.
			PracticeSession current = requireUnchanged(userId, session);
			TradePlan plan = plans.save(new TradePlan(current.getId(), seen.trades().size() + 1,
				request.direction(), request.stopTicks(), request.targetTicks(), request.contracts(),
				reference, current.getCursorTime()));
			PracticeResult result = results.save(new PracticeResult(plan, fill,
				instrument.getTickValueCents(), model, current.getGeneratorVersion(), current.getSeed(),
				current.getDataSource()));
			current.setStatus(Status.IN_TRADE);
			sessions.saveAndFlush(current);
			List<Trade> trades = new ArrayList<>(seen.trades());
			trades.add(new Trade(plan, result));
			return state(current, instrument, trades);
		});
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
			throw new NotInTradeException();
		}
		Timeframe timeframe = viewOf(seen.session(), timeframeCode);
		Instant end = endOfBarContaining(open.getExitTime(), timeframe);
		List<Bar> bars = generate(seen.session(), seen.instrument(), timeframe,
			startOfBarContaining(seen.session().getCursorTime(), timeframe), end);
		return reveal(userId, seen, end, bars);
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
			seedOf(session), session.getHistoryFrom(), cursor.isAfter(end) ? cursor : end, cursor, detectors,
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
		Map<UUID, String> symbols = instruments.findAll().stream()
			.collect(Collectors.toMap(Instrument::getId, Instrument::getSymbol));

		return recent.stream().map(s -> {
			List<Trade> trades = pair(plansBySession.getOrDefault(s.getId(), List.of()),
				resultsBySession.getOrDefault(s.getId(), List.of()));
			// The journal is a listing, and a listing is an easy place to leak an outcome by
			// accident. Same rule as everywhere else: a trade counts once its exit is revealed.
			return new SessionSummary(
				s.getId(),
				symbols.get(s.getInstrumentId()),
				s.getTimeframe(),
				s.getStatus().name(),
				s.getCreatedAt().getEpochSecond(),
				trades.size(),
				score(trades, s.getCursorTime()));
		}).toList();
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
			trades.stream().map(t -> tradeView(t, cursor, clock)).toList(),
			score(trades, cursor),
			// DEC-038 and DEC-025: the seed and the real date are the whole future, revealed only
			// once the session has ended and no trade can use them.
			complete && session.getSeed() != null ? Long.toString(session.getSeed()) : null,
			session.getGeneratorVersion(),
			session.getDataSource(),
			complete && clock.hidesDate() ? session.getStartTime().getEpochSecond() : null);
	}

	private static TradeView tradeView(Trade trade, Instant cursor, PracticeClock clock) {
		PracticeResult result = trade.result();
		// The entry is the open of the first bar after the plan was locked. It is revealed with that
		// bar, not before: the price a market order will fill at is itself the future.
		EntryView entry = cursor.isAfter(result.getEntryTime())
			? new EntryView(clock.show(result.getEntryTime()), result.getEntryTicks())
			: null;
		return new TradeView(trade.plan().getSeq(), planView(trade.plan(), clock), entry,
			trade.closedBy(cursor) ? resultView(trade, clock) : null);
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
			long ticks = trade.result().getPnlTicks();
			wins += ticks > 0 ? 1 : 0;
			losses += ticks < 0 ? 1 : 0;
			// The sum of the R column as the learner sees it, so adding it up by hand agrees with
			// the total. Summing the unrounded values could differ in the last digit (BUG-039).
			totalR += round2(trade.r());
			net += trade.result().getNetPnlCents();
		}
		return new ScoreView(closed, wins, losses, round2(totalR), net);
	}

	private static PlanView planView(TradePlan plan, PracticeClock clock) {
		long risk = Math.abs(plan.getReferenceTicks() - plan.getStopTicks());
		long reward = Math.abs(plan.getTargetTicks() - plan.getReferenceTicks());
		return new PlanView(plan.getDirection(), plan.getStopTicks(), plan.getTargetTicks(),
			plan.getContracts(), plan.getReferenceTicks(), clock.show(plan.getSubmittedCursor()),
			round2((double) reward / risk));
	}

	private static ResultView resultView(Trade trade, PracticeClock clock) {
		PracticeResult result = trade.result();
		return new ResultView(
			result.getOutcome().name(),
			result.getOutcome() == Outcome.AMBIGUOUS
				? ambiguousAssumption(Resolution.valueOf(result.getResolution()))
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
			result.getFillModelVersion());
	}

	/** INVARIANT 6: an assumed outcome says so, in the unit of the bars that could not decide it. */
	static String ambiguousAssumption(Resolution resolution) {
		return "The stop and the target were both touched inside the same " + resolution.unit()
			+ ", so which came first cannot be known from this data. The result assumes the worst "
			+ "case: the stop.";
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
			return new Snapshot(session, instrumentOf(session),
				pair(plans.findAllBySessionId(session.getId()),
					results.findAllBySessionId(session.getId())));
		});
	}

	/** Each plan with its result, in the order the plans were locked. */
	private static List<Trade> pair(List<TradePlan> plans, List<PracticeResult> results) {
		Map<Integer, PracticeResult> bySeq = results.stream()
			.collect(Collectors.toMap(PracticeResult::getSeq, Function.identity()));
		return plans.stream()
			.sorted(Comparator.comparingInt(TradePlan::getSeq))
			.map(plan -> {
				PracticeResult result = bySeq.get(plan.getSeq());
				if (result == null) {
					throw new IllegalStateException("Plan " + plan.getId() + " has no result");
				}
				return new Trade(plan, result);
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
		return toDto(generate(session, instrument, timeframe, session.getHistoryFrom(),
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
		if (Math.abs(stop - reference) > MAX_BRACKET_TICKS
				|| Math.abs(target - reference) > MAX_BRACKET_TICKS) {
			throw new IllegalArgumentException(
				"Stop and target must be within " + MAX_BRACKET_TICKS + " ticks of the price.");
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
		// The market is open ~70% of the week; ten thousand misses in a row does not happen.
		throw new IllegalStateException("Could not find an open-market start");
	}

	/**
	 * A random moment in the imported history where the market really traded: open by the calendar,
	 * and with bars around it — the calendar knows the hours, not the holidays (T4).
	 */
	private Instant pickRealStart(Instrument instrument, Timeframe timeframe, Killzone killzone,
			DayOfWeek weekday) {
		Instant[] coverage = marketData.coverage(instrument);
		if (coverage == null) {
			throw new IllegalArgumentException("No real " + instrument.getSymbol()
				+ " history has been imported yet, so there is nothing to practise on.");
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
		long second = instant.getEpochSecond();
		return Instant.ofEpochSecond(second - Math.floorMod(second, timeframe.seconds()));
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
		public NotInTradeException() {
			super("There is no running trade to skip to the end of.");
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
