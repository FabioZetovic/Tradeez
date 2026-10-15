package com.tradeez.drills;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.tradeez.analysis.DetectedSetup;
import com.tradeez.analysis.DetectedSetupRepository;
import com.tradeez.analysis.Detector;
import com.tradeez.analysis.DetectorRegistry;
import com.tradeez.analysis.SetupScanner;
import com.tradeez.drills.DrillDtos.DrillResponse;
import com.tradeez.drills.DrillDtos.KindView;
import com.tradeez.drills.DrillDtos.OptionView;
import com.tradeez.drills.DrillDtos.ResultView;
import com.tradeez.drills.DrillDtos.SetupView;
import com.tradeez.drills.DrillDtos.SubjectView;
import com.tradeez.drills.DrillKind.Mode;
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
 * The drill engine (T13, DEC-054): any {@link DrillKind} — any question a versioned rule answers
 * about a static chart — on a random stretch of real market, graded against the rule.
 *
 * <p>The answer key is the rule's stored hits (DEC-026), found by the same scanner the chart and
 * the replay use, so a drill can never disagree with what the overlay draws. A hit is asked about
 * only when everything the rule read to find it is on screen; one the chart cuts — a gap whose first
 * candle is before the window, a swing confirmed after it — is <i>excused</i>: a mark on it counts
 * neither way, because nobody could judge it from what is shown.
 *
 * <h2>DEC-034 — simulated data</h2>
 * On a simulated market the one drill offered is the FVG drill, as a developer fixture: its gaps come
 * from a price formula, so it says so, is never graded (the database refuses otherwise) and feeds no
 * record. Every other drill needs a real market. The generator is <b>not</b> tuned to produce
 * patterns; at one minute it produces almost no qualifying gaps, so one-minute fixtures are refused
 * rather than manufactured.
 */
@Service
public class DrillService {

	/** Candles in an identification drill. Small enough to read at a glance: a drill takes seconds. */
	public static final int WINDOW_BARS = 30;

	/** Candles in a classification drill: room for the structure the name depends on. */
	public static final int CLASSIFY_WINDOW_BARS = 40;

	static final Set<Timeframe> REAL_TIMEFRAMES = EnumSet.of(Timeframe.M1, Timeframe.M5, Timeframe.M15);
	static final Set<Timeframe> SIMULATED_TIMEFRAMES = EnumSet.of(Timeframe.M5, Timeframe.M15);
	static final String DEFAULT_INSTRUMENT = "NQ";
	static final Timeframe DEFAULT_TIMEFRAME = Timeframe.M5;
	static final DrillKind DEFAULT_KIND = DrillKind.FVG_IDENTIFICATION;
	static final int MAX_ATTEMPTS = 80;

	/** Drills are meant to be done by the hundred, so the cap is generous — it stops scripts. */
	static final int MAX_DRILLS_PER_HOUR = 600;

	/**
	 * The public chart's market (DEC-038). Drills are static charts with nothing hidden in the future,
	 * so a fixture needs no secret seed — and one market means one set of stored hits.
	 */
	static final long SEED = MarketDataService.PUBLIC_CHART_SEED;

	/** Candles scanned past a window, so a hit anchored in its last candles is known to confirm after it. */
	private static final int CONFIRM_REACH = 3;

	/** How far into the imported history a real window may start: the rules' lookbacks are there. */
	private static final Duration HISTORY_MARGIN = Duration.ofDays(3);

	private static final Instant SIMULATED_EARLIEST = Instant.parse("2023-01-02T00:00:00Z");
	private static final Instant SIMULATED_LATEST = Instant.parse("2025-12-31T00:00:00Z");

	private static final String DISCLAIMER = "SIMULATED DATA — NOT MARKET HISTORY";

	static final String FIXTURE_NOTICE = "Developer fixture — not a graded exercise. These candles "
		+ "are simulated, so the gaps in them come from a price formula, not from a real market. The "
		+ "drill shows how identification practice works; it does not count toward anything.";

	private final SecureRandom random = new SecureRandom();

	private final DrillRepository drills;
	private final DetectedSetupRepository setups;
	private final InstrumentRepository instruments;
	private final MarketDataService marketData;
	private final SetupScanner scanner;
	private final DetectorRegistry detectors;
	private final TransactionTemplate writeTx;

	public DrillService(DrillRepository drills, DetectedSetupRepository setups, InstrumentRepository instruments,
			MarketDataService marketData, SetupScanner scanner, DetectorRegistry detectors,
			PlatformTransactionManager transactionManager) {
		this.drills = drills;
		this.setups = setups;
		this.instruments = instruments;
		this.marketData = marketData;
		this.scanner = scanner;
		this.detectors = detectors;
		this.writeTx = new TransactionTemplate(transactionManager);
	}

	/** Every drill there is, with the rule each is graded by. */
	public List<KindView> kinds() {
		return Arrays.stream(DrillKind.values()).map(k -> {
			Detector detector = detectors.require(k.detector());
			return new KindView(k.name(), k.mode().name(), k.title(), k.conceptId(), detector.id(),
				detector.ruleVersion(), k.instructions());
		}).toList();
	}

	public DrillResponse create(UUID userId, String kindCode, String symbol, String timeframeCode) {
		DrillKind kind = blank(kindCode) ? DEFAULT_KIND : DrillKind.parse(kindCode);
		Instrument instrument = marketData.requireInstrument(blank(symbol) ? DEFAULT_INSTRUMENT : symbol);
		Timeframe timeframe = blank(timeframeCode) ? DEFAULT_TIMEFRAME : Timeframe.fromCode(timeframeCode);
		boolean simulated = instrument.getDataClass() == DataClass.SIMULATED;
		if (simulated) {
			if (kind != DrillKind.FVG_IDENTIFICATION) {
				throw new IllegalArgumentException("On simulated data only the FVG drill runs, as a developer "
					+ "fixture: a graded drill needs a real market (DEC-034). Choose " + DEFAULT_INSTRUMENT + ".");
			}
			if (timeframe == Timeframe.M1) {
				throw new IllegalArgumentException("One-minute drills are not offered on simulated data: the "
					+ "simulated market produces almost no qualifying gaps at one minute, and it is "
					+ "deliberately not tuned to (DEC-034).");
			}
			requireTimeframe(SIMULATED_TIMEFRAMES, timeframe);
		} else {
			requireTimeframe(REAL_TIMEFRAMES, timeframe);
		}
		if (drills.countByUserIdAndCreatedAtAfter(userId, Instant.now().minus(Duration.ofHours(1)))
				>= MAX_DRILLS_PER_HOUR) {
			throw new TooManyDrillsException();
		}

		Detector detector = detectors.require(kind.detector());
		Window window = findWindow(kind, detector, instrument, timeframe);
		Drill drill = writeTx.execute(tx -> drills.save(new Drill(userId, kind.name(), instrument.getId(),
			timeframe.code(), instrument.getDataClass(), simulated ? SEED : null,
			simulated ? DemoSeriesGenerator.GENERATOR_VERSION : null, detector.id(), detector.ruleVersion(),
			window.from(), window.to(), window.key().stream().map(DetectedSetup::getId).collect(Collectors.toSet()),
			window.excused())));
		return view(drill, instrument, window.bars(), window.key());
	}

	public DrillResponse get(UUID userId, UUID drillId) {
		Drill drill = require(userId, drillId);
		Instrument instrument = instrumentOf(drill);
		return view(drill, instrument, windowBars(drill, instrument), answerKey(drill));
	}

	/**
	 * Judges an answer, once: a drill's answer is a record, not a draft. Identification takes the
	 * marked candles, classification one of the offered names.
	 */
	public DrillResponse answer(UUID userId, UUID drillId, List<Long> marks, String choice) {
		Drill seen = require(userId, drillId);
		if (seen.isAnswered()) {
			throw new DrillAnsweredException();
		}
		DrillKind kind = DrillKind.valueOf(seen.getKind());
		Instrument instrument = instrumentOf(seen);
		List<Bar> bars = windowBars(seen, instrument);
		List<DetectedSetup> key = answerKey(seen);

		Consumer<Drill> record;
		if (kind.mode() == Mode.IDENTIFY) {
			if (marks == null || choice != null) {
				throw new IllegalArgumentException("This drill is answered by marking candles: send marks.");
			}
			Set<Long> candles = bars.stream().map(b -> b.openTime().getEpochSecond()).collect(Collectors.toSet());
			for (Long mark : marks) {
				if (mark == null || !candles.contains(mark)) {
					throw new IllegalArgumentException("Every mark must be a candle in the drill's window.");
				}
			}
			List<Long> distinct = new ArrayList<>(new LinkedHashSet<>(marks));
			distinct.sort(Comparator.naturalOrder());
			record = d -> d.answer(distinct);
		} else {
			if (choice == null || marks != null) {
				throw new IllegalArgumentException("This drill is answered by choosing a name: send a choice.");
			}
			List<String> options = kind.optionsFor(key.getFirst());
			if (!options.contains(choice)) {
				throw new IllegalArgumentException("Choose one of " + options + ".");
			}
			record = d -> d.choose(choice);
		}

		Drill answered = writeTx.execute(tx -> {
			Drill current = require(userId, drillId);
			if (current.isAnswered()) {
				throw new DrillAnsweredException();
			}
			record.accept(current);
			return drills.saveAndFlush(current);
		});
		return view(answered, instrument, bars, key);
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * @param key     A: every hit asked about; B: the one subject
	 * @param excused A: candles a mark on which counts neither way
	 */
	private record Window(Instant from, Instant to, List<Bar> bars, List<DetectedSetup> key, List<Long> excused) {
	}

	/**
	 * A random stretch of open market — every candle there, no contract roll inside — with a fair
	 * question in it.
	 *
	 * <p>Every attempt's hits are stored, including the windows that are rejected: they are true hits
	 * of the rule on this market, and DEC-026's table is meant to hold all of them.
	 */
	private Window findWindow(DrillKind kind, Detector detector, Instrument instrument, Timeframe timeframe) {
		boolean simulated = instrument.getDataClass() == DataClass.SIMULATED;
		int length = kind.mode().windowBars();
		long step = timeframe.seconds();
		Instant earliest = SIMULATED_EARLIEST;
		Instant latest = SIMULATED_LATEST;
		if (!simulated) {
			Instant[] coverage = marketData.coverage(instrument);
			if (coverage == null) {
				throw new IllegalArgumentException("No real " + instrument.getSymbol()
					+ " history has been imported yet, so there is nothing to drill on.");
			}
			earliest = coverage[0].plus(HISTORY_MARGIN);
			latest = coverage[1].minus(timeframe.duration().multipliedBy(length + CONFIRM_REACH));
			if (!earliest.isBefore(latest)) {
				throw new IllegalArgumentException("Too little real " + instrument.getSymbol()
					+ " history has been imported to drill on.");
			}
		}

		for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
			long second = earliest.getEpochSecond()
				+ random.nextLong(latest.getEpochSecond() - earliest.getEpochSecond());
			Instant from = Instant.ofEpochSecond(second - Math.floorMod(second, step));
			Instant to = from.plusSeconds(length * step);
			if (!SessionCalendar.isOpenThroughout(from, to)
					|| (!simulated && !marketData.rolls(instrument, from, to).isEmpty())) {
				continue;
			}
			// The calendar knows the hours, not the holidays: the candles themselves must all be there.
			List<Bar> bars = marketData.internalBars(instrument, timeframe, from, to, SEED).bars();
			if (bars.size() != length) {
				continue;
			}

			Instant reach = to.plusSeconds(CONFIRM_REACH * step);
			scanner.ensureScanned(detector, instrument, timeframe, SEED, from, reach);
			List<DetectedSetup> asked = scanner.visible(detector, instrument, timeframe, SEED, from, to, reach)
				.stream()
				.filter(kind::counts)
				.filter(h -> !h.getAnchorTime().isBefore(from) && h.getAnchorTime().isBefore(to))
				.toList();
			List<DetectedSetup> whole = asked.stream().filter(h -> whole(kind, h, from, to, step)).toList();

			if (kind.mode() == Mode.IDENTIFY) {
				if (whole.size() >= kind.minHits() && whole.size() <= kind.maxHits()) {
					Set<Long> keyed = whole.stream().map(DrillService::anchor).collect(Collectors.toSet());
					List<Long> excused = asked.stream().map(DrillService::anchor).filter(t -> !keyed.contains(t))
						.distinct().sorted().toList();
					return new Window(from, to, bars, whole, excused);
				}
				continue;
			}
			List<DetectedSetup> subjects = kind == DrillKind.BOS_CHOCH_CLASSIFICATION
				? withStructureOnScreen(whole, instrument, timeframe, from, to)
				: whole;
			if (!subjects.isEmpty()) {
				return new Window(from, to, bars, List.of(subjects.get(random.nextInt(subjects.size()))), List.of());
			}
		}
		throw new NoWindowFoundException();
	}

	/**
	 * Everything the rule read to find the hit is on screen: from the candle before its start when
	 * the start is a swing (its left neighbour is what makes it one), to the candle that confirmed it.
	 */
	private static boolean whole(DrillKind kind, DetectedSetup hit, Instant from, Instant to, long step) {
		return !hit.getStartTime().isBefore(from.plusSeconds(kind.lead() * step)) && !hit.getConfirmedAt().isAfter(to);
	}

	/**
	 * A break is BOS or CHoCH by the structure before it: the latest swing high and low, each
	 * labelled against the one before it. Only breaks whose four swings are all on screen can be named
	 * from the chart, so only those are asked about. The labels are the stored {@code STRUCTURE} hits
	 * under the same rule version, the latest confirmed by the breaking candle's open on each side.
	 */
	private List<DetectedSetup> withStructureOnScreen(List<DetectedSetup> breaks, Instrument instrument,
			Timeframe timeframe, Instant from, Instant to) {
		if (breaks.isEmpty()) {
			return breaks;
		}
		Detector structure = detectors.require("STRUCTURE");
		scanner.ensureScanned(structure, instrument, timeframe, SEED, from, to);
		List<DetectedSetup> labels = scanner.visible(structure, instrument, timeframe, SEED, from, to, to);
		return breaks.stream().filter(b -> {
			DetectedSetup high = latestLabel(labels, true, b.getAnchorTime());
			DetectedSetup low = latestLabel(labels, false, b.getAnchorTime());
			return high != null && low != null
				&& !high.getStartTime().isBefore(from) && !low.getStartTime().isBefore(from)
				&& high.getKind().equals(b.getEvidence().get("highLabel"))
				&& low.getKind().equals(b.getEvidence().get("lowLabel"));
		}).toList();
	}

	private static DetectedSetup latestLabel(List<DetectedSetup> labels, boolean high, Instant by) {
		return labels.stream()
			.filter(l -> l.getKind().endsWith("H") == high && !l.getConfirmedAt().isAfter(by))
			.max(Comparator.comparing(DetectedSetup::getConfirmedAt))
			.orElse(null);
	}

	private List<Bar> windowBars(Drill drill, Instrument instrument) {
		return marketData.internalBars(instrument, Timeframe.fromCode(drill.getTimeframe()),
			drill.getWindowFrom(), drill.getWindowTo(), drill.getSeed() == null ? SEED : drill.getSeed()).bars();
	}

	private List<DetectedSetup> answerKey(Drill drill) {
		return setups.findAllById(drill.getSetupIds()).stream()
			.sorted(Comparator.comparing(DetectedSetup::getAnchorTime).thenComparing(DetectedSetup::getDirection))
			.toList();
	}

	private DrillResponse view(Drill drill, Instrument instrument, List<Bar> bars, List<DetectedSetup> key) {
		DrillKind kind = DrillKind.valueOf(drill.getKind());
		boolean fixture = drill.getDataClass() == DataClass.SIMULATED;
		boolean classify = kind.mode() == Mode.CLASSIFY;
		DetectedSetup subject = classify ? key.getFirst() : null;
		return new DrillResponse(
			drill.getId(),
			kind.name(),
			kind.mode().name(),
			kind.title(),
			kind.conceptId(),
			instrument.getSymbol(),
			instrument.getTickSize(),
			instrument.getExchangeTimezone(),
			drill.getTimeframe(),
			drill.getDataClass().name(),
			fixture ? DISCLAIMER : null,
			fixture,
			drill.isGraded(),
			fixture ? FIXTURE_NOTICE : null,
			kind.instructions(),
			kind.markName(),
			DrillText.rule(detectors.require(drill.getDetector())),
			bars.stream()
				.map(b -> new BarResponse(b.openTime().getEpochSecond(), b.openTicks(),
					b.highTicks(), b.lowTicks(), b.closeTicks(), b.volume()))
				.toList(),
			classify ? subject(kind, subject) : null,
			classify ? kind.optionsFor(subject).stream().map(o -> new OptionView(o, DrillKind.NAMES.get(o))).toList() : null,
			drill.isAnswered() ? drill.getMarks() : null,
			drill.isAnswered() ? drill.getChoice() : null,
			drill.isAnswered() ? result(drill, key, instrument.getTickSize()) : null);
	}

	/** What the chart shows of a classification subject: where, and which side — not its name. */
	private static SubjectView subject(DrillKind kind, DetectedSetup s) {
		long time = anchor(s);
		long fromTime = "POINT".equals(s.getShape()) ? time : s.getStartTime().getEpochSecond();
		return new SubjectView(kind.subjectIsHigh(s) ? "HIGH" : "LOW", fromTime, time, s.getZoneLowTicks());
	}

	private static ResultView result(Drill drill, List<DetectedSetup> key, BigDecimal tickSize) {
		if (drill.getChoice() != null) {
			DetectedSetup subject = key.getFirst();
			return new ResultView(null, null, null, null, null, subject.getKind(),
				subject.getKind().equals(drill.getChoice()), List.of(setupView(subject, null, tickSize)));
		}
		Set<Long> marked = new HashSet<>(drill.getMarks());
		Set<Long> anchors = key.stream().map(DrillService::anchor).collect(Collectors.toSet());
		Set<Long> excused = new HashSet<>(drill.getExcused());
		List<SetupView> views = key.stream()
			.map(s -> setupView(s, marked.contains(anchor(s)), tickSize))
			.toList();
		List<Long> unasked = drill.getMarks().stream().filter(m -> !anchors.contains(m)).toList();
		List<Long> extra = unasked.stream().filter(m -> !excused.contains(m)).toList();
		List<Long> excusedMarks = unasked.stream().filter(excused::contains).toList();
		int found = (int) views.stream().filter(SetupView::found).count();
		return new ResultView(found, views.size() - found, extra.size(), extra, excusedMarks, null, null, views);
	}

	private static SetupView setupView(DetectedSetup s, Boolean found, BigDecimal tickSize) {
		return new SetupView(s.getId(), s.getDirection(), s.getKind(), s.getShape(),
			s.getStartTime().getEpochSecond(), anchor(s), s.getEndTime().getEpochSecond(), s.getZoneLowTicks(),
			s.getZoneHighTicks(), found, DrillText.label(s), DrillText.explain(s, tickSize), s.getEvidence());
	}

	private static long anchor(DetectedSetup s) {
		return s.getAnchorTime().getEpochSecond();
	}

	private static void requireTimeframe(Set<Timeframe> allowed, Timeframe timeframe) {
		if (!allowed.contains(timeframe)) {
			throw new IllegalArgumentException("Drills run on " + allowed.stream().map(Timeframe::code).toList()
				+ " charts here.");
		}
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
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
