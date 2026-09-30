package com.tradeez.drills;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.tradeez.analysis.DetectedSetup;
import com.tradeez.analysis.DetectedSetupRepository;
import com.tradeez.analysis.FvgDetector;
import com.tradeez.analysis.SetupScanner;
import com.tradeez.drills.DrillDtos.DrillResponse;
import com.tradeez.drills.DrillDtos.ResultView;
import com.tradeez.drills.DrillDtos.RuleView;
import com.tradeez.drills.DrillDtos.SetupView;
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.DataClass;
import com.tradeez.marketdata.DemoSeriesGenerator;
import com.tradeez.marketdata.Instrument;
import com.tradeez.marketdata.InstrumentRepository;
import com.tradeez.marketdata.MarketDataService;
import com.tradeez.marketdata.SessionCalendar;
import com.tradeez.marketdata.Timeframe;
import com.tradeez.marketdata.dto.MarketDataDtos.BarResponse;

/**
 * The FVG identification drill: a short stretch of chart, "mark every fair value gap".
 *
 * <h2>DEC-034 — what this is on simulated data</h2>
 * A developer fixture. The simulated market's gaps are artifacts of a price formula, and drilling
 * a learner on them would teach the formula, not the market. So on {@code SIMULATED} data every
 * drill says so, is never graded (the database refuses otherwise), and feeds no rating. What it
 * demonstrates is the machinery — detector, stored hits, answer key, explanations — that a drill on
 * permitted real data would run on unchanged.
 *
 * <p>For the same reason the generator is <b>not</b> tuned to produce gaps. On one-minute bars it
 * produces almost none that qualify (measured in session 016), so one-minute drills are refused
 * rather than manufactured.
 */
@Service
public class DrillService {

	/** Candles shown. Small enough to read at a glance: a drill is meant to take seconds. */
	public static final int WINDOW_BARS = 30;

	static final Set<Timeframe> TIMEFRAMES = EnumSet.of(Timeframe.M5, Timeframe.M15);
	static final String DEFAULT_INSTRUMENT = "DEMO-NQ";
	static final Timeframe DEFAULT_TIMEFRAME = Timeframe.M5;

	/** A window with no gap teaches little; one with more than this is a chore. */
	static final int MIN_GAPS = 1;
	static final int MAX_GAPS = 6;
	static final int MAX_ATTEMPTS = 60;

	/** Drills are meant to be done by the hundred, so the cap is generous — it stops scripts. */
	static final int MAX_DRILLS_PER_HOUR = 600;

	/**
	 * The public chart's market (DEC-038). Drills are static charts with nothing hidden in the future,
	 * so they need no secret seed — and one market means one set of stored hits that every drill,
	 * and later every other consumer of {@code detected_setup}, shares.
	 */
	static final long SEED = MarketDataService.PUBLIC_CHART_SEED;

	private static final Instant EARLIEST = Instant.parse("2023-01-02T00:00:00Z");
	private static final Instant LATEST = Instant.parse("2025-12-31T00:00:00Z");

	private static final String DISCLAIMER = "SIMULATED DATA — NOT MARKET HISTORY";

	static final String FIXTURE_NOTICE = "Developer fixture — not a graded exercise. These candles "
		+ "are simulated, so the gaps in them come from a price formula, not from a real market. The "
		+ "drill shows how identification practice works; it does not count toward anything.";

	private static final String INSTRUCTIONS = "Mark the middle candle of every fair value gap in "
		+ "this chart, then check your answer. The middle candle is the one whose move left the gap.";

	private final SecureRandom random = new SecureRandom();

	private final DrillRepository drills;
	private final DetectedSetupRepository setups;
	private final InstrumentRepository instruments;
	private final MarketDataService marketData;
	private final SetupScanner scanner;
	private final TransactionTemplate writeTx;

	public DrillService(DrillRepository drills, DetectedSetupRepository setups,
			InstrumentRepository instruments, MarketDataService marketData, SetupScanner scanner,
			PlatformTransactionManager transactionManager) {
		this.drills = drills;
		this.setups = setups;
		this.instruments = instruments;
		this.marketData = marketData;
		this.scanner = scanner;
		this.writeTx = new TransactionTemplate(transactionManager);
	}

	public DrillResponse create(UUID userId, String kind, String symbol, String timeframeCode) {
		if (kind != null && !kind.isBlank() && !Drill.FVG_IDENTIFICATION.equals(kind)) {
			throw new IllegalArgumentException("Unknown drill kind '" + kind + "'. Supported: ["
				+ Drill.FVG_IDENTIFICATION + "]");
		}
		Instrument instrument = marketData.requireInstrument(
			symbol == null || symbol.isBlank() ? DEFAULT_INSTRUMENT : symbol);
		Timeframe timeframe = timeframeCode == null || timeframeCode.isBlank()
			? DEFAULT_TIMEFRAME
			: Timeframe.fromCode(timeframeCode);
		if (timeframe == Timeframe.M1) {
			throw new IllegalArgumentException("One-minute drills are not offered: the simulated "
				+ "market produces almost no qualifying gaps at one minute, and it is deliberately "
				+ "not tuned to (DEC-034).");
		}
		if (!TIMEFRAMES.contains(timeframe)) {
			throw new IllegalArgumentException("Drills run on "
				+ TIMEFRAMES.stream().map(Timeframe::code).toList() + " charts.");
		}
		if (instrument.getDataClass() != DataClass.SIMULATED) {
			throw new IllegalArgumentException("Drills currently run on simulated instruments only.");
		}
		if (drills.countByUserIdAndCreatedAtAfter(userId, Instant.now().minus(Duration.ofHours(1)))
				>= MAX_DRILLS_PER_HOUR) {
			throw new TooManyDrillsException();
		}

		Window window = findWindow(instrument, timeframe);
		Drill drill = writeTx.execute(tx -> drills.save(new Drill(userId, Drill.FVG_IDENTIFICATION,
			instrument.getId(), timeframe.code(), instrument.getDataClass(), SEED,
			DemoSeriesGenerator.GENERATOR_VERSION, FvgDetector.DETECTOR, FvgDetector.RULE_VERSION,
			window.from(), window.to(),
			window.answerKey().stream().map(DetectedSetup::getId).collect(Collectors.toSet()))));

		return view(drill, instrument, window.bars(), List.of());
	}

	public DrillResponse get(UUID userId, UUID drillId) {
		Drill drill = require(userId, drillId);
		Instrument instrument = instrumentOf(drill);
		List<Bar> bars = windowBars(drill, instrument);
		return view(drill, instrument, bars, drill.isAnswered() ? answerKey(drill) : List.of());
	}

	/** Judges an answer. Once: a drill's answer is a record, not a draft. */
	public DrillResponse answer(UUID userId, UUID drillId, List<Long> marks) {
		Drill seen = require(userId, drillId);
		if (seen.isAnswered()) {
			throw new DrillAnsweredException();
		}
		Instrument instrument = instrumentOf(seen);
		List<Bar> bars = windowBars(seen, instrument);

		Set<Long> candles = bars.stream()
			.map(b -> b.openTime().getEpochSecond())
			.collect(Collectors.toSet());
		for (Long mark : marks) {
			if (mark == null || !candles.contains(mark)) {
				throw new IllegalArgumentException(
					"Every mark must be a candle in the drill's window.");
			}
		}
		List<Long> distinct = new ArrayList<>(new LinkedHashSet<>(marks));
		distinct.sort(Comparator.naturalOrder());

		Drill answered = writeTx.execute(tx -> {
			Drill current = require(userId, drillId);
			if (current.isAnswered()) {
				throw new DrillAnsweredException();
			}
			current.answer(distinct);
			return drills.saveAndFlush(current);
		});
		return view(answered, instrument, bars, answerKey(answered));
	}

	// ---------------------------------------------------------------------------------------------

	private record Window(Instant from, Instant to, List<Bar> bars, List<DetectedSetup> answerKey) {
	}

	/**
	 * A random stretch of open market with a sensible number of gaps in it.
	 *
	 * <p>Every attempt's hits are stored, including the windows that are rejected: they are true hits
	 * of the rule on this market, and DEC-026's table is meant to hold all of them.
	 */
	private Window findWindow(Instrument instrument, Timeframe timeframe) {
		long step = timeframe.seconds();
		long span = LATEST.getEpochSecond() - EARLIEST.getEpochSecond();
		Duration length = timeframe.duration().multipliedBy(WINDOW_BARS);
		Duration history = timeframe.duration()
			.multipliedBy(FvgDetector.Parameters.DEFAULT.averageRangeLookback() + 1L);

		for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
			long second = EARLIEST.getEpochSecond() + random.nextLong(span);
			Instant from = Instant.ofEpochSecond(second - Math.floorMod(second, step));
			Instant to = from.plus(length);
			// The window and the detector's lookback before it are one unbroken piece of trading,
			// so every candle shown is there and every gap near the start is still evaluated.
			if (!SessionCalendar.isOpenThroughout(from.minus(history), to)) {
				continue;
			}

			List<DetectedSetup> hits = scanner.scanFvgs(instrument, timeframe, SEED, from, to);
			// Only gaps whose three candles are all on screen: a learner cannot be asked to find a
			// pattern whose first candle they were never shown.
			List<DetectedSetup> answerKey = hits.stream()
				.filter(s -> !s.getStartTime().isBefore(from) && s.getEndTime().isBefore(to))
				.toList();
			if (answerKey.size() >= MIN_GAPS && answerKey.size() <= MAX_GAPS) {
				List<Bar> bars = marketData.internalBars(instrument, timeframe, from, to, SEED).bars();
				return new Window(from, to, bars, answerKey);
			}
		}
		throw new NoWindowFoundException();
	}

	private List<Bar> windowBars(Drill drill, Instrument instrument) {
		return marketData.internalBars(instrument, Timeframe.fromCode(drill.getTimeframe()),
			drill.getWindowFrom(), drill.getWindowTo(), drill.getSeed()).bars();
	}

	private List<DetectedSetup> answerKey(Drill drill) {
		return setups.findAllById(drill.getSetupIds()).stream()
			.sorted(Comparator.comparing(DetectedSetup::getAnchorTime))
			.toList();
	}

	private DrillResponse view(Drill drill, Instrument instrument, List<Bar> bars,
			List<DetectedSetup> key) {
		boolean fixture = drill.getDataClass() == DataClass.SIMULATED;
		return new DrillResponse(
			drill.getId(),
			drill.getKind(),
			instrument.getSymbol(),
			instrument.getTickSize(),
			instrument.getExchangeTimezone(),
			drill.getTimeframe(),
			drill.getDataClass().name(),
			fixture ? DISCLAIMER : null,
			fixture,
			drill.isGraded(),
			fixture ? FIXTURE_NOTICE : null,
			INSTRUCTIONS,
			rule(),
			bars.stream()
				.map(b -> new BarResponse(b.openTime().getEpochSecond(), b.openTicks(),
					b.highTicks(), b.lowTicks(), b.closeTicks(), b.volume()))
				.toList(),
			drill.isAnswered() ? drill.getMarks() : null,
			drill.isAnswered() ? result(drill.getMarks(), key) : null);
	}

	private static ResultView result(List<Long> marks, List<DetectedSetup> key) {
		Set<Long> marked = new HashSet<>(marks);
		Set<Long> anchors = key.stream()
			.map(s -> s.getAnchorTime().getEpochSecond())
			.collect(Collectors.toSet());
		List<SetupView> views = key.stream()
			.map(s -> new SetupView(s.getId(), s.getDirection(),
				s.getStartTime().getEpochSecond(), s.getAnchorTime().getEpochSecond(),
				s.getEndTime().getEpochSecond(), s.getZoneLowTicks(), s.getZoneHighTicks(),
				marked.contains(s.getAnchorTime().getEpochSecond()), s.getEvidence()))
			.toList();
		List<Long> extra = marks.stream().filter(m -> !anchors.contains(m)).toList();
		int found = (int) views.stream().filter(SetupView::found).count();
		return new ResultView(found, views.size() - found, extra.size(), views, extra);
	}

	/** The rule in words, generated from the parameters so the two can never disagree. */
	static RuleView rule() {
		FvgDetector.Parameters p = FvgDetector.Parameters.DEFAULT;
		String definition = "Bullish: candle 1's high is below candle 3's low. Bearish: candle 1's "
			+ "low is above candle 3's high. The three candles are consecutive, with no market "
			+ "closure between them, and the gap is at least " + p.minGapPercentOfAverageRange()
			+ "% of the average high–low range of the " + p.averageRangeLookback()
			+ " candles before candle 1.";
		return new RuleView(FvgDetector.DETECTOR, FvgDetector.RULE_VERSION, p.asMap(), definition,
			FvgDetector.METHODOLOGY_SOURCE);
	}

	private Drill require(UUID userId, UUID drillId) {
		return drills.findByIdAndUserId(drillId, userId).orElseThrow(DrillNotFoundException::new);
	}

	private Instrument instrumentOf(Drill drill) {
		return instruments.findById(drill.getInstrumentId())
			.orElseThrow(() -> new IllegalStateException(
				"Drill " + drill.getId() + " refers to a missing instrument"));
	}

	// ---------------------------------------------------------------------------------------------

	/** Unknown id, or someone else's. Indistinguishable on purpose. */
	public static class DrillNotFoundException extends RuntimeException {
		public DrillNotFoundException() {
			super("No such drill.");
		}
	}

	public static class DrillAnsweredException extends RuntimeException {
		public DrillAnsweredException() {
			super("This drill has already been answered.");
		}
	}

	public static class TooManyDrillsException extends RuntimeException {
		public TooManyDrillsException() {
			super("You have started " + MAX_DRILLS_PER_HOUR + " drills in the last hour. "
				+ "Please wait before starting another.");
		}
	}

	public static class NoWindowFoundException extends RuntimeException {
		public NoWindowFoundException() {
			super("Could not find a suitable stretch of chart this time. Please try again.");
		}
	}
}
