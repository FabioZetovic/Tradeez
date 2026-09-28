package com.tradeez.practice;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.DataClass;
import com.tradeez.marketdata.DemoSeriesGenerator;
import com.tradeez.marketdata.Instrument;
import com.tradeez.marketdata.InstrumentRepository;
import com.tradeez.marketdata.MarketDataService;
import com.tradeez.marketdata.SessionCalendar;
import com.tradeez.marketdata.Timeframe;
import com.tradeez.marketdata.dto.MarketDataDtos.BarResponse;
import com.tradeez.practice.FillEngine.Direction;
import com.tradeez.practice.FillEngine.Fill;
import com.tradeez.practice.FillEngine.FillModel;
import com.tradeez.practice.FillEngine.Outcome;
import com.tradeez.practice.PracticeDtos.AdvanceResponse;
import com.tradeez.practice.PracticeDtos.EntryView;
import com.tradeez.practice.PracticeDtos.PlanRequest;
import com.tradeez.practice.PracticeDtos.PlanView;
import com.tradeez.practice.PracticeDtos.PracticeState;
import com.tradeez.practice.PracticeDtos.ResultView;
import com.tradeez.practice.PracticeDtos.SessionResponse;
import com.tradeez.practice.PracticeDtos.SessionSummary;
import com.tradeez.practice.PracticeSession.Status;

/**
 * The practice loop: a hidden moment, a committed plan, a replay that reveals what happened.
 *
 * <h2>The one rule (DEC-009)</h2>
 * Nothing at or after the session's cursor leaves this class. That is enforced structurally rather
 * than by filtering: bars shown to the learner are <i>generated</i> only up to the cursor, so data
 * past it does not exist in memory to be leaked by a careless DTO. The one place future data is
 * generated — resolving a plan — produces a result row and nothing else, and that row is withheld
 * until the cursor passes its exit.
 *
 * <h2>Why the result is computed at submission</h2>
 * INVARIANT 2: an outcome is written once. Resolving the trade the moment the plan is locked means
 * the replay is purely a reveal. Nothing the learner does while watching — pausing, reloading,
 * opening a second tab — can affect a result that already exists.
 */
@Service
public class PracticeService {

	/** Timeframes a session can be watched on. The engine always resolves at one second. */
	static final Set<Timeframe> TIMEFRAMES = EnumSet.of(Timeframe.M1, Timeframe.M5, Timeframe.M15);

	static final String DEFAULT_INSTRUMENT = "DEMO-NQ";
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

	/**
	 * The range hidden moments are drawn from. The dates of a simulated market carry no meaning —
	 * there is no real history to look up (DEC-025 as refined by DEC-038) — but spreading starts
	 * across years spreads them across the generator's slow seasonal cycle.
	 */
	private static final Instant EARLIEST_START = Instant.parse("2023-01-02T00:00:00Z");
	private static final Instant LATEST_START = Instant.parse("2025-12-31T00:00:00Z");

	private static final String SIMULATED_DISCLAIMER = "SIMULATED DATA — NOT MARKET HISTORY";

	private static final String AMBIGUOUS_ASSUMPTION = "The stop and the target were both touched "
		+ "inside the same second, so which came first cannot be known from this data. The result "
		+ "assumes the worst case: the stop.";

	private final SecureRandom random = new SecureRandom();

	private final PracticeSessionRepository sessions;
	private final TradePlanRepository plans;
	private final PracticeResultRepository results;
	private final InstrumentRepository instruments;
	private final MarketDataService marketData;

	public PracticeService(PracticeSessionRepository sessions, TradePlanRepository plans,
			PracticeResultRepository results, InstrumentRepository instruments,
			MarketDataService marketData) {
		this.sessions = sessions;
		this.plans = plans;
		this.results = results;
		this.instruments = instruments;
		this.marketData = marketData;
	}

	// ---------------------------------------------------------------------------------------------
	// Commands
	// ---------------------------------------------------------------------------------------------

	@Transactional
	public SessionResponse create(UUID userId, String symbol, String timeframeCode) {
		Instrument instrument = marketData.requireInstrument(
			symbol == null || symbol.isBlank() ? DEFAULT_INSTRUMENT : symbol);
		Timeframe timeframe = timeframeCode == null || timeframeCode.isBlank()
			? DEFAULT_TIMEFRAME
			: Timeframe.fromCode(timeframeCode);

		if (!TIMEFRAMES.contains(timeframe)) {
			throw new IllegalArgumentException("Practice runs on "
				+ TIMEFRAMES.stream().map(Timeframe::code).toList() + " charts.");
		}
		// Real data needs its date hidden (DEC-025), which this slice does not do yet. Refusing is
		// better than quietly running a session whose timestamps give the answer away.
		if (instrument.getDataClass() != DataClass.SIMULATED) {
			throw new IllegalArgumentException(
				"Practice currently runs on simulated instruments only.");
		}
		if (sessions.countByUserIdAndCreatedAtAfter(userId, Instant.now().minus(Duration.ofHours(1)))
				>= MAX_SESSIONS_PER_HOUR) {
			throw new TooManySessionsException();
		}

		Instant start = pickStart(timeframe);
		PracticeSession session = sessions.save(new PracticeSession(
			userId, instrument.getId(), timeframe.code(), newSeed(),
			DemoSeriesGenerator.GENERATOR_VERSION, start.minus(HISTORY), start));

		return new SessionResponse(state(session, instrument), visibleBars(session, instrument));
	}

	/**
	 * Locks the plan and resolves it.
	 *
	 * <p>This is the only method that generates data past the cursor, and none of it is returned:
	 * the response is the state, which withholds the result until the replay reaches it.
	 */
	@Transactional
	public PracticeState submitPlan(UUID userId, UUID sessionId, PlanRequest request) {
		PracticeSession session = require(userId, sessionId);
		Instrument instrument = instrumentOf(session);

		if (session.getStatus() != Status.ANALYSING || plans.existsBySessionId(session.getId())) {
			throw new PlanLockedException();
		}
		if (!SessionCalendar.isOpen(session.getCursorTime())) {
			throw new MarketClosedException();
		}

		long reference = lastVisibleClose(session, instrument);
		validateBracket(request, reference);

		TradePlan plan = plans.save(new TradePlan(session.getId(), request.direction(),
			request.stopTicks(), request.targetTicks(), request.contracts(), reference,
			session.getCursorTime()));

		// Every one-second bar from the cursor to the end of the trading session. The engine exits
		// at the close if neither level is reached, so nothing beyond it can matter.
		List<Bar> seconds = marketData.internalBars(instrument, Timeframe.S1,
			session.getCursorTime(), sessionEnd(session.getCursorTime()), session.getSeed()).bars();

		FillModel model = FillModel.DEFAULT;
		Fill fill = FillEngine.resolve(plan.toEnginePlan(), seconds, model);
		results.save(new PracticeResult(session.getId(), fill, plan.getContracts(),
			instrument.getTickValueCents(), model, session.getGeneratorVersion(), session.getSeed()));

		session.setStatus(Status.IN_TRADE);
		sessions.saveAndFlush(session);
		return state(session, instrument);
	}

	/** Reveals the next {@code count} bars. Skips closed market, so a bar is always a bar. */
	@Transactional
	public AdvanceResponse advance(UUID userId, UUID sessionId, int count) {
		if (count < 1 || count > MAX_ADVANCE_BARS) {
			throw new IllegalArgumentException(
				"Advance between 1 and " + MAX_ADVANCE_BARS + " bars at a time.");
		}
		PracticeSession session = require(userId, sessionId);
		Instrument instrument = instrumentOf(session);
		Timeframe timeframe = Timeframe.fromCode(session.getTimeframe());

		Instant cursor = session.getCursorTime();
		Instant limit = replayLimit(session, timeframe);
		if (!cursor.isBefore(limit)) {
			throw new ReplayEndException();
		}

		Instant to = min(cursor.plus(timeframe.duration().multipliedBy(count)), limit);
		List<Bar> bars = generate(session, instrument, timeframe, cursor, to);
		if (bars.size() < count && to.isBefore(limit)) {
			// The window crossed a closed market — a maintenance break or a weekend — so it held
			// fewer bars than asked for. Look far enough ahead to reach the next session.
			to = min(to.plus(CLOSED_MARKET_LOOKAHEAD), limit);
			bars = generate(session, instrument, timeframe, cursor, to);
		}
		if (bars.isEmpty()) {
			throw new ReplayEndException();
		}
		if (bars.size() > count) {
			bars = bars.subList(0, count);
		}

		session.moveCursorTo(bars.getLast().openTime().plus(timeframe.duration()));
		completeIfExitRevealed(session);
		sessions.saveAndFlush(session);
		return new AdvanceResponse(state(session, instrument), toDto(bars));
	}

	/** Jumps straight to the bar in which the trade ended. */
	@Transactional
	public AdvanceResponse skipToResult(UUID userId, UUID sessionId) {
		PracticeSession session = require(userId, sessionId);
		if (session.getStatus() != Status.IN_TRADE) {
			throw new NotInTradeException();
		}
		Instrument instrument = instrumentOf(session);
		Timeframe timeframe = Timeframe.fromCode(session.getTimeframe());
		PracticeResult result = requireResult(session);

		Instant end = endOfBarContaining(result.getExitTime(), timeframe);
		List<Bar> bars = generate(session, instrument, timeframe, session.getCursorTime(), end);

		session.moveCursorTo(end);
		completeIfExitRevealed(session);
		sessions.saveAndFlush(session);
		return new AdvanceResponse(state(session, instrument), toDto(bars));
	}

	// ---------------------------------------------------------------------------------------------
	// Queries
	// ---------------------------------------------------------------------------------------------

	@Transactional(readOnly = true)
	public SessionResponse get(UUID userId, UUID sessionId) {
		PracticeSession session = require(userId, sessionId);
		Instrument instrument = instrumentOf(session);
		return new SessionResponse(state(session, instrument), visibleBars(session, instrument));
	}

	@Transactional(readOnly = true)
	public List<SessionSummary> journal(UUID userId) {
		List<PracticeSession> recent = sessions.findTop50ByUserIdOrderByCreatedAtDesc(userId);
		List<UUID> ids = recent.stream().map(PracticeSession::getId).toList();
		Map<UUID, TradePlan> planBySession = plans.findAllBySessionIdIn(ids).stream()
			.collect(Collectors.toMap(TradePlan::getSessionId, Function.identity()));
		Map<UUID, PracticeResult> resultBySession = results.findAllBySessionIdIn(ids).stream()
			.collect(Collectors.toMap(PracticeResult::getSessionId, Function.identity()));
		Map<UUID, String> symbols = instruments.findAll().stream()
			.collect(Collectors.toMap(Instrument::getId, Instrument::getSymbol));

		return recent.stream().map(s -> {
			TradePlan plan = planBySession.get(s.getId());
			// The journal is a listing, and a listing is an easy place to leak an outcome by
			// accident. Same rule as everywhere else: nothing about the result before COMPLETE.
			PracticeResult result = s.getStatus() == Status.COMPLETE
				? resultBySession.get(s.getId())
				: null;
			return new SessionSummary(
				s.getId(),
				symbols.get(s.getInstrumentId()),
				s.getTimeframe(),
				s.getStatus().name(),
				s.getCreatedAt().getEpochSecond(),
				plan == null ? null : plan.getDirection(),
				result == null ? null : result.getOutcome().name(),
				result == null ? null : result.getNetPnlCents(),
				result == null || plan == null ? null : rMultiple(result, plan));
		}).toList();
	}

	// ---------------------------------------------------------------------------------------------
	// Views
	// ---------------------------------------------------------------------------------------------

	private PracticeState state(PracticeSession session, Instrument instrument) {
		TradePlan plan = plans.findBySessionId(session.getId()).orElse(null);
		PracticeResult result = plan == null
			? null
			: results.findBySessionId(session.getId()).orElse(null);
		boolean complete = session.getStatus() == Status.COMPLETE;

		// The entry is the open of the first bar after the plan was locked. It is revealed with that
		// bar, not before: the price a market order will fill at is itself one second of the future.
		EntryView entry = result != null && session.getCursorTime().isAfter(result.getEntryTime())
			? new EntryView(result.getEntryTime().getEpochSecond(), result.getEntryTicks())
			: null;

		return new PracticeState(
			session.getId(),
			instrument.getSymbol(),
			instrument.getTickSize(),
			instrument.getTickValueCents(),
			session.getTimeframe(),
			session.getStatus().name(),
			session.getStartTime().getEpochSecond(),
			session.getCursorTime().getEpochSecond(),
			instrument.getDataClass().name(),
			instrument.getDataClass() == DataClass.SIMULATED ? SIMULATED_DISCLAIMER : null,
			plan == null ? null : planView(plan),
			entry,
			complete && result != null ? resultView(result, plan) : null,
			complete ? session.getSeed() : null,
			session.getGeneratorVersion());
	}

	private static PlanView planView(TradePlan plan) {
		long risk = Math.abs(plan.getReferenceTicks() - plan.getStopTicks());
		long reward = Math.abs(plan.getTargetTicks() - plan.getReferenceTicks());
		return new PlanView(plan.getDirection(), plan.getStopTicks(), plan.getTargetTicks(),
			plan.getContracts(), plan.getReferenceTicks(), round2((double) reward / risk));
	}

	private static ResultView resultView(PracticeResult result, TradePlan plan) {
		return new ResultView(
			result.getOutcome().name(),
			result.getOutcome() == Outcome.AMBIGUOUS ? AMBIGUOUS_ASSUMPTION : null,
			result.getEntryTime().getEpochSecond(),
			result.getEntryTicks(),
			result.getExitTime().getEpochSecond(),
			result.getExitTicks(),
			result.getPnlTicks(),
			result.getNetPnlCents(),
			result.getCommissionCents(),
			result.getMaeTicks(),
			result.getMfeTicks(),
			result.isGapExit(),
			rMultiple(result, plan),
			result.getResolution(),
			result.getEngineVersion(),
			result.getFillModelVersion());
	}

	/**
	 * The result in units of what the learner planned to risk.
	 *
	 * <p>Measured against the plan's reference price, not the fill: R is a statement about the
	 * decision the learner made, and they made it looking at the reference.
	 */
	private static double rMultiple(PracticeResult result, TradePlan plan) {
		long risk = Math.abs(plan.getReferenceTicks() - plan.getStopTicks());
		return round2((double) result.getPnlTicks() / risk);
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

	private PracticeResult requireResult(PracticeSession session) {
		return results.findBySessionId(session.getId())
			.orElseThrow(() -> new IllegalStateException(
				"Session " + session.getId() + " is IN_TRADE without a result"));
	}

	/**
	 * Everything from the start of the history to the cursor — and, by construction, nothing after.
	 * The range handed to the generator ends at the cursor, so no later bar is ever produced.
	 */
	private List<BarResponse> visibleBars(PracticeSession session, Instrument instrument) {
		Timeframe timeframe = Timeframe.fromCode(session.getTimeframe());
		return toDto(generate(session, instrument, timeframe, session.getHistoryFrom(),
			session.getCursorTime()));
	}

	private List<Bar> generate(PracticeSession session, Instrument instrument, Timeframe timeframe,
			Instant from, Instant to) {
		return marketData.internalBars(instrument, timeframe, from, to, session.getSeed()).bars();
	}

	/** The close of the last one-second bar before the cursor: the price the learner is looking at. */
	private long lastVisibleClose(PracticeSession session, Instrument instrument) {
		Timeframe timeframe = Timeframe.fromCode(session.getTimeframe());
		Instant cursor = session.getCursorTime();
		List<Bar> lastBar = marketData.internalBars(instrument, Timeframe.S1,
			cursor.minus(timeframe.duration()), cursor, session.getSeed()).bars();
		if (lastBar.isEmpty()) {
			throw new IllegalStateException("No visible price before the cursor at " + cursor);
		}
		return lastBar.getLast().closeTicks();
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
	private Instant pickStart(Timeframe timeframe) {
		long span = LATEST_START.getEpochSecond() - EARLIEST_START.getEpochSecond();
		long step = timeframe.seconds();
		for (int attempt = 0; attempt < 10_000; attempt++) {
			long second = EARLIEST_START.getEpochSecond() + random.nextLong(span);
			Instant start = Instant.ofEpochSecond(second - Math.floorMod(second, step));
			if (openThroughout(start.minus(timeframe.duration()), start.plus(MIN_SESSION_LEFT))) {
				return start;
			}
		}
		// The market is open ~70% of the week; ten thousand misses in a row does not happen.
		throw new IllegalStateException("Could not find an open-market start");
	}

	/** Session boundaries fall on whole hours, so checking each minute is exact. */
	private static boolean openThroughout(Instant from, Instant to) {
		for (Instant t = from; t.isBefore(to); t = t.plus(Duration.ofMinutes(1))) {
			if (!SessionCalendar.isOpen(t)) {
				return false;
			}
		}
		return true;
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

	/**
	 * How far this session may be stepped. A running trade can always be watched to its end, even
	 * past the usual limit, or the learner could be left unable to see how it finished.
	 */
	private Instant replayLimit(PracticeSession session, Timeframe timeframe) {
		Instant limit = session.getStartTime().plus(MAX_REPLAY);
		if (session.getStatus() == Status.IN_TRADE) {
			Instant exitBarEnd = endOfBarContaining(requireResult(session).getExitTime(), timeframe);
			return exitBarEnd.isAfter(limit) ? exitBarEnd : limit;
		}
		return limit;
	}

	private void completeIfExitRevealed(PracticeSession session) {
		if (session.getStatus() != Status.IN_TRADE) {
			return;
		}
		PracticeResult result = requireResult(session);
		if (session.getCursorTime().isAfter(result.getExitTime())) {
			session.setStatus(Status.COMPLETE);
		}
	}

	private static Instant endOfBarContaining(Instant instant, Timeframe timeframe) {
		long step = timeframe.seconds();
		long second = instant.getEpochSecond();
		return Instant.ofEpochSecond(second - Math.floorMod(second, step) + step);
	}

	private static Instant min(Instant a, Instant b) {
		return a.isBefore(b) ? a : b;
	}

	private static double round2(double value) {
		return Math.round(value * 100.0) / 100.0;
	}

	private static List<BarResponse> toDto(List<Bar> bars) {
		return bars.stream()
			.map(b -> new BarResponse(b.openTime().getEpochSecond(),
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
			super("This session already has a plan, and a plan cannot be changed once submitted.");
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

	public static class TooManySessionsException extends RuntimeException {
		public TooManySessionsException() {
			super("You have started " + MAX_SESSIONS_PER_HOUR + " sessions in the last hour. "
				+ "Please wait before starting another.");
		}
	}
}
