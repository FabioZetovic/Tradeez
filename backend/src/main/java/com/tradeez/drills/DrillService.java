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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.tradeez.analysis.DetectedSetup;
import com.tradeez.analysis.DetectedSetupRepository;
import com.tradeez.analysis.Detector;
import com.tradeez.analysis.DetectorRegistry;
import com.tradeez.analysis.RuleText;
import com.tradeez.analysis.SetupScanner;
import com.tradeez.drills.DrillDtos.ContextView;
import com.tradeez.drills.DrillDtos.DrillResponse;
import com.tradeez.drills.DrillDtos.KindView;
import com.tradeez.drills.DrillDtos.OptionView;
import com.tradeez.drills.DrillDtos.QuestionView;
import com.tradeez.drills.DrillDtos.ResultView;
import com.tradeez.drills.DrillDtos.RuleView;
import com.tradeez.drills.DrillDtos.SetupView;
import com.tradeez.drills.DrillDtos.SubjectView;
import com.tradeez.drills.DrillDtos.TargetView;
import com.tradeez.drills.DrillKind.Mode;
import com.tradeez.drills.Prediction.Gap;
import com.tradeez.drills.Prediction.Hit;
import com.tradeez.drills.Prediction.Level;
import com.tradeez.drills.Prediction.Outcome;
import com.tradeez.drills.Prediction.Question;
import com.tradeez.drills.Prediction.Target;
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.DataClass;
import com.tradeez.marketdata.DemoSeriesGenerator;
import com.tradeez.marketdata.HiddenStretches;
import com.tradeez.marketdata.Instrument;
import com.tradeez.marketdata.InstrumentRepository;
import com.tradeez.marketdata.MarketDataService;
import com.tradeez.marketdata.SessionCalendar;
import com.tradeez.marketdata.Timeframe;
import com.tradeez.marketdata.dto.MarketDataDtos.CompanionBars;
import com.tradeez.marketdata.dto.MarketDataDtos.BarResponse;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * The drill engine (T13, DEC-054): any {@link DrillKind} — any question a versioned rule answers
 * about a static chart — on a random stretch of real market, graded against the rule.
 *
 * <p>A prediction (mode C, T15, DEC-058) is a chart that stops at a moment, two targets drawn on it, and
 * the question which real price reached first. The targets come from the rules' stored hits as the
 * moment saw them; the answer from the candles after it, decided when the drill is made and sent only
 * with the learner's answer, together with those candles.
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

	/** Mode E: candles searched for a trade and its moment; the chart ends at the moment (T26). */
	static final int MANAGE_WINDOW_BARS = 60;

	static final Set<Timeframe> REAL_TIMEFRAMES = EnumSet.of(Timeframe.M1, Timeframe.M5, Timeframe.M15);
	static final Set<Timeframe> SIMULATED_TIMEFRAMES = EnumSet.of(Timeframe.M5, Timeframe.M15);
	static final String DEFAULT_INSTRUMENT = "NQ";
	/** A simulated fixture's timeframe when none is asked for; a real drill's is drawn at random (T43). */
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

	/**
	 * Moments tried in each stretch of chart a prediction looks at: the window slides over the stretch
	 * until a moment asks a fair question. Most do for "which is taken first"; about one in twenty does
	 * for "external or internal next", which needs the last candle to have just taken a level.
	 */
	static final int PREDICT_SLIDE = 30;

	/** Calendar time searched for a prediction's future candles: enough for any closure on the way. */
	private static final Duration FUTURE_REACH = Duration.ofDays(5);

	private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() {
	};

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
	private final ObjectMapper json;
	private final HiddenStretches hiddenStretches;

	public DrillService(DrillRepository drills, DetectedSetupRepository setups, InstrumentRepository instruments,
			MarketDataService marketData, SetupScanner scanner, DetectorRegistry detectors,
			PlatformTransactionManager transactionManager, ObjectMapper json, HiddenStretches hiddenStretches) {
		this.drills = drills;
		this.setups = setups;
		this.instruments = instruments;
		this.marketData = marketData;
		this.scanner = scanner;
		this.detectors = detectors;
		this.writeTx = new TransactionTemplate(transactionManager);
		this.json = json;
		this.hiddenStretches = hiddenStretches;
	}

	/** Every drill there is, with the rule each is graded by. */
	public List<KindView> kinds() {
		return Arrays.stream(DrillKind.values()).map(k -> {
			// Mode E's rule is the drill's own, not a detector's (T26).
			RuleView rule = k.mode() == Mode.MANAGE ? Management.rule() : rule(detectors.require(k.detector()));
			return new KindView(k.name(), k.mode().name(), k.title(), k.conceptId(), rule.detector(), rule.version(),
				k.instructions());
		}).toList();
	}

	public DrillResponse create(UUID userId, String kindCode, String symbol, String timeframeCode) {
		DrillKind kind = blank(kindCode) ? DEFAULT_KIND : DrillKind.parse(kindCode);
		Instrument instrument = marketData.requireInstrument(blank(symbol) ? DEFAULT_INSTRUMENT : symbol);
		boolean simulated = instrument.getDataClass() == DataClass.SIMULATED;
		Timeframe timeframe = timeframe(timeframeCode, simulated, random);
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

		// BUG-085, BUG-086: not inside an open practice session's replay, whose future it would show.
		List<HiddenStretches.Stretch> avoid = simulated ? List.of() : hiddenStretches.of(userId);
		if (kind.mode() == Mode.MANAGE) {
			Managed managed = findManagement(instrument, timeframe, avoid);
			Drill drill = writeTx.execute(tx -> drills.save(new Drill(userId, kind.name(), instrument.getId(),
				timeframe.code(), instrument.getDataClass(), kind.detector(), Management.VERSION, managed.from(),
				managed.asked().trade().moment(), Set.of(), json.convertValue(managed.asked(), JSON_OBJECT),
				String.join(",", managed.asked().outcome().best().stream().map(Enum::name).toList()),
				managed.asked().outcome().decidedAt(), managed.revealTo())));
			return view(drill, instrument, managed.bars(), List.of());
		}
		Detector detector = detectors.require(kind.detector());
		if (kind.mode() == Mode.PREDICT) {
			Asked asked = findPrediction(kind, detector, instrument, timeframe, avoid);
			Drill drill = writeTx.execute(tx -> drills.save(new Drill(userId, kind.name(), instrument.getId(),
				timeframe.code(), instrument.getDataClass(), detector.id(), detector.ruleVersion(), asked.from(), asked.to(),
				asked.question().targets().stream().map(Target::setupId).collect(Collectors.toSet()),
				json.convertValue(asked.question(), JSON_OBJECT), asked.outcome().value(), asked.outcome().at(),
				asked.revealTo())));
			return view(drill, instrument, asked.bars(), List.of());
		}
		Window window = findWindow(kind, detector, instrument, timeframe, avoid);
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
			List<String> options = kind.mode() == Mode.CLASSIFY ? kind.optionsFor(key.getFirst()) : kind.predictions();
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

	/** A management drill: the chart up to the moment, the trade, and what each choice made (T26). */
	private record Managed(Instant from, List<Bar> bars, Management.Asked asked, Instant revealTo) {
	}

	/**
	 * A stretch of open market in which a trade, opened at a candle's close, stands a whole R in profit with its
	 * stop untouched — and a future, on one contract, that tells what each choice made.
	 */
	private Managed findManagement(Instrument instrument, Timeframe timeframe, List<HiddenStretches.Stretch> avoid) {
		int length = Mode.MANAGE.windowBars();
		long step = timeframe.seconds();
		Instant[] coverage = marketData.coverage(instrument);
		if (coverage == null) {
			throw new IllegalArgumentException("No real " + instrument.getSymbol()
				+ " history has been imported yet, so there is nothing to drill on.");
		}
		Instant earliest = coverage[0].plus(HISTORY_MARGIN);
		Instant latest = coverage[1].minus(timeframe.duration().multipliedBy(length + Management.HORIZON_CANDLES))
			.minus(FUTURE_REACH);
		if (!earliest.isBefore(latest)) {
			throw new IllegalArgumentException("Too little real " + instrument.getSymbol()
				+ " history has been imported to drill on.");
		}
		for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
			long second = earliest.getEpochSecond() + random.nextLong(latest.getEpochSecond() - earliest.getEpochSecond());
			Instant from = Instant.ofEpochSecond(second - Math.floorMod(second, step));
			Instant to = from.plusSeconds(length * step);
			if (HiddenStretches.touches(avoid, from, to.plus(FUTURE_REACH))
					|| !SessionCalendar.isOpenThroughout(from, to) || !marketData.rolls(instrument, from, to).isEmpty()) {
				continue;
			}
			List<Bar> stretch = marketData.internalBars(instrument, timeframe, from, to, SEED).bars();
			if (stretch.size() != length) {
				continue;
			}
			boolean longSide = random.nextBoolean();
			for (int entry = Management.ATR_CANDLES; entry < length - Management.MIN_HELD; entry++) {
				Management.Trade trade = Management.open(stretch, entry, longSide, step);
				if (trade == null) {
					continue;
				}
				List<Bar> future = future(instrument, timeframe, trade.moment());
				Management.Outcome outcome = Management.resolve(trade, future, bar -> minutes(instrument, timeframe, bar));
				if (outcome == null) {
					continue;
				}
				int shown = (int) stretch.stream().filter(b -> b.openTime().isBefore(trade.moment())).count();
				int decided = (int) future.stream().filter(b -> !b.openTime().isAfter(outcome.decidedAt())).count();
				Bar lastRevealed = future.get(Math.min(future.size(), decided + Prediction.REVEAL_AFTER) - 1);
				return new Managed(from, List.copyOf(stretch.subList(0, shown)),
					new Management.Asked(trade, outcome, future.getFirst().openTicks()),
					lastRevealed.openTime().plus(timeframe.duration()));
			}
		}
		throw new NoWindowFoundException();
	}

	/** A prediction: the chart up to the moment, the question it asks, and how the future answered. */
	private record Asked(Instant from, Instant to, List<Bar> bars, Question question, Outcome outcome, Instant revealTo) {
	}

	/**
	 * A stretch of open market with a moment in it that asks a fair question — both targets on the chart,
	 * and a future that answers it before the horizon, on one contract.
	 */
	private Asked findPrediction(DrillKind kind, Detector liquidity, Instrument instrument, Timeframe timeframe,
			List<HiddenStretches.Stretch> avoid) {
		int length = kind.mode().windowBars();
		int stretchBars = length + PREDICT_SLIDE - 1;
		long step = timeframe.seconds();
		Instant[] coverage = marketData.coverage(instrument);
		if (coverage == null) {
			throw new IllegalArgumentException("No real " + instrument.getSymbol()
				+ " history has been imported yet, so there is nothing to drill on.");
		}
		Instant earliest = coverage[0].plus(HISTORY_MARGIN);
		Instant latest = coverage[1].minus(timeframe.duration().multipliedBy(stretchBars + Prediction.HORIZON_CANDLES))
			.minus(FUTURE_REACH);
		if (!earliest.isBefore(latest)) {
			throw new IllegalArgumentException("Too little real " + instrument.getSymbol()
				+ " history has been imported to drill on.");
		}
		Detector gapRule = detectors.require("FVG_STATE");

		for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
			long second = earliest.getEpochSecond() + random.nextLong(latest.getEpochSecond() - earliest.getEpochSecond());
			Instant from = Instant.ofEpochSecond(second - Math.floorMod(second, step));
			Instant to = from.plusSeconds(stretchBars * step);
			if (HiddenStretches.touches(avoid, from, to.plus(FUTURE_REACH))
					|| !SessionCalendar.isOpenThroughout(from, to) || !marketData.rolls(instrument, from, to).isEmpty()) {
				continue;
			}
			List<Bar> stretch = marketData.internalBars(instrument, timeframe, from, to, SEED).bars();
			if (stretch.size() != stretchBars) {
				continue;
			}
			scanner.ensureScanned(List.of(liquidity, gapRule), instrument, timeframe, SEED, from, to);
			List<Hit> levelHits = hits(scanner.visible(liquidity, instrument, timeframe, SEED, from, to, to));
			List<Hit> gapHits = hits(scanner.visible(gapRule, instrument, timeframe, SEED, from, to, to));

			for (int k = 0; k < PREDICT_SLIDE; k++) {
				List<Bar> window = stretch.subList(k, k + length);
				Instant windowFrom = window.getFirst().openTime();
				Instant moment = windowFrom.plusSeconds(length * step);
				List<Level> levels = Prediction.levelsAt(levelHits, windowFrom.plusSeconds(kind.lead() * step), moment);
				List<Gap> gaps = Prediction.gapsAt(gapHits, windowFrom, moment);
				Question question = kind == DrillKind.LIQUIDITY_FIRST_PREDICTION
					? Prediction.liquidityFirst(window.getLast(), levels, gaps)
					: Prediction.externalOrInternal(window.getLast(), levels, gaps);
				if (question == null) {
					continue;
				}
				List<Bar> future = future(instrument, timeframe, moment);
				Outcome outcome = Prediction.resolve(question.targets(), future, bar -> minutes(instrument, timeframe, bar));
				if (outcome == null) {
					continue;
				}
				List<Bar> revealed = Prediction.revealed(future, outcome);
				return new Asked(windowFrom, moment, List.copyOf(window), question, outcome,
					revealed.getLast().openTime().plus(timeframe.duration()));
			}
		}
		throw new NoWindowFoundException();
	}

	/**
	 * ICT-10's drill (T45): the levels resting when the chart ends, read as the prediction drills read them — the nearest
	 * buy-side above the last close and the nearest sell-side below it, each swing on the chart with its left neighbour.
	 * Empty unless there are both.
	 */
	static List<DetectedSetup> nearestLiquidity(List<DetectedSetup> stored, Bar last, Instant earliestSwing, Instant end) {
		List<Level> resting = Prediction.levelsAt(hits(stored), earliestSwing, end).stream()
			.filter(l -> "RESTING".equals(l.state())).toList();
		Optional<Level> above = resting.stream().filter(l -> l.high() && l.price() > last.closeTicks())
			.min(Comparator.comparingLong(Level::price));
		Optional<Level> below = resting.stream().filter(l -> !l.high() && l.price() < last.closeTicks())
			.max(Comparator.comparingLong(Level::price));
		if (above.isEmpty() || below.isEmpty()) {
			return List.of();
		}
		Set<UUID> ids = Set.of(above.get().id(), below.get().id());
		return stored.stream().filter(s -> ids.contains(s.getId())).sorted(Comparator.comparing(DetectedSetup::getAnchorTime))
			.toList();
	}

	/** Stored hits as much as a prediction reads of them. */
	private static List<Hit> hits(List<DetectedSetup> stored) {
		return stored.stream()
			.map(s -> new Hit(s.getId(), s.getKind(), "BULLISH".equals(s.getDirection()),
				s.getEvidence().get("side") instanceof String side ? side : null, s.getStartTime(), s.getAnchorTime(),
				s.getEndTime(), s.getConfirmedAt(), s.getZoneLowTicks(), s.getZoneHighTicks()))
			.toList();
	}

	/** The candles after the moment that may answer: up to the horizon, and never past a contract roll. */
	List<Bar> future(Instrument instrument, Timeframe timeframe, Instant moment) {
		Instant reach = moment.plus(timeframe.duration().multipliedBy(Prediction.HORIZON_CANDLES)).plus(FUTURE_REACH);
		List<Instant> rolls = marketData.rolls(instrument, moment, reach);
		Instant until = rolls.isEmpty() ? reach : rolls.getFirst();
		List<Bar> bars = marketData.internalBars(instrument, timeframe, moment, until, SEED).bars();
		return bars.subList(0, Math.min(bars.size(), Prediction.HORIZON_CANDLES));
	}

	/** A candle's one-minute candles: what decides when it reached both targets. */
	private List<Bar> minutes(Instrument instrument, Timeframe timeframe, Bar bar) {
		if (timeframe == Timeframe.M1) {
			return List.of(bar);
		}
		return marketData.internalBars(instrument, Timeframe.M1, bar.openTime(), bar.openTime().plus(timeframe.duration()),
			SEED).bars();
	}

	/**
	 * A random stretch of open market — every candle there, no contract roll inside — with a fair
	 * question in it.
	 *
	 * <p>Every attempt's hits are stored, including the windows that are rejected: they are true hits
	 * of the rule on this market, and DEC-026's table is meant to hold all of them.
	 */
	private Window findWindow(DrillKind kind, Detector detector, Instrument instrument, Timeframe timeframe,
			List<HiddenStretches.Stretch> avoid) {
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
			if (HiddenStretches.touches(avoid, from, to.plusSeconds(CONFIRM_REACH * step))
					|| !SessionCalendar.isOpenThroughout(from, to)
					|| (!simulated && !marketData.rolls(instrument, from, to).isEmpty())) {
				continue;
			}
			// The calendar knows the hours, not the holidays: the candles themselves must all be there.
			List<Bar> bars = marketData.internalBars(instrument, timeframe, from, to, SEED).bars();
			if (bars.size() != length) {
				continue;
			}
			if (!besideWhole(detector, instrument, timeframe, from, to, length)) {
				continue;
			}

			Instant reach = to.plusSeconds(CONFIRM_REACH * step);
			scanner.ensureScanned(detector, instrument, timeframe, SEED, from, reach);
			if (kind == DrillKind.NEAREST_LIQUIDITY_IDENTIFICATION) {
				List<DetectedSetup> nearest = nearestLiquidity(scanner.visible(detector, instrument, timeframe, SEED, from, to, to),
					bars.getLast(), from.plusSeconds(kind.lead() * step), to);
				if (nearest.size() == 2) {
					return new Window(from, to, bars, nearest, List.of());
				}
				continue;
			}
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
		Instant first = from.plusSeconds(kind.lead() * step);
		if (hit.getStartTime().isBefore(first) || hit.getConfirmedAt().isAfter(to)) {
			return false;
		}
		return !kind.showsItsReasons() || hit.getEvidence().entrySet().stream()
			.filter(e -> e.getKey().endsWith("Time") && e.getValue() instanceof Number)
			.allMatch(e -> ((Number) e.getValue()).longValue() >= first.getEpochSecond());
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

	/**
	 * For a rule that reads a second market (DEC-075): that one too is one contract over the window, with every
	 * candle there. True for a rule that reads one market.
	 */
	boolean besideWhole(Detector detector, Instrument instrument, Timeframe timeframe, Instant from, Instant to,
			int length) {
		Instrument companion = companion(detector, instrument);
		return companion == null || (marketData.rolls(companion, from, to).isEmpty()
			&& marketData.internalBars(companion, timeframe, from, to, SEED).bars().size() == length);
	}

	/** The market a rule reads beside this one (T20), or null. */
	private Instrument companion(Detector detector, Instrument instrument) {
		String symbol = detector.companionOf(instrument.getSymbol());
		return symbol == null ? null : marketData.requireInstrument(symbol);
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
		boolean predict = kind.mode() == Mode.PREDICT;
		boolean manage = kind.mode() == Mode.MANAGE;
		Management.Asked managed = manage ? json.convertValue(drill.getQuestion(), Management.Asked.class) : null;
		DetectedSetup subject = classify ? key.getFirst() : null;
		BigDecimal tickSize = instrument.getTickSize();
		Detector detector = manage ? null : detectors.require(drill.getDetector());
		Instrument companion = detector == null ? null : companion(detector, instrument);
		Question question = predict ? json.convertValue(drill.getQuestion(), Question.class) : null;
		// The future, only once answered: before that, nothing after the moment leaves the server.
		List<Bar> revealed = (predict || manage) && drill.isAnswered()
			? marketData.internalBars(instrument, Timeframe.fromCode(drill.getTimeframe()), drill.getWindowTo(),
				drill.getRevealTo(), SEED).bars()
			: null;
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
			manage ? Management.rule() : rule(detector),
			bars(bars),
			classify ? subject(kind, subject) : null,
			classify ? kind.optionsFor(subject).stream().map(o -> new OptionView(o, RuleText.NAMES.get(o))).toList()
				: predict ? kind.predictions().stream()
					.map(v -> new OptionView(v, PredictionText.label(target(question, v), tickSize))).toList()
				: manage ? kind.predictions().stream()
					.map(v -> new OptionView(v, Management.label(Management.Choice.valueOf(v)))).toList()
				: null,
			predict ? questionView(kind, question, tickSize) : manage ? Management.questionView(managed.trade(), tickSize) : null,
			drill.isAnswered() ? drill.getMarks() : null,
			drill.isAnswered() ? drill.getChoice() : null,
			!drill.isAnswered() ? null
				: predict ? predictionResult(drill, question, revealed, tickSize)
				: manage ? managementResult(drill, managed, tickSize)
				: result(drill, key, tickSize),
			revealed == null ? null : bars(revealed),
			companion == null ? null : new CompanionBars(companion.getSymbol(), companion.getTickSize(),
				bars(marketData.internalBars(companion, Timeframe.fromCode(drill.getTimeframe()), drill.getWindowFrom(),
					drill.getWindowTo(), SEED).bars())));
	}

	private static List<BarResponse> bars(List<Bar> bars) {
		return bars.stream()
			.map(b -> new BarResponse(b.openTime().getEpochSecond(), b.openTicks(), b.highTicks(), b.lowTicks(),
				b.closeTicks(), b.volume()))
			.toList();
	}

	private static Target target(Question question, String value) {
		return question.targets().stream().filter(t -> t.value().equals(value)).findFirst().orElseThrow();
	}

	/** What a prediction asks, drawn from the moment's side of the chart only. */
	private static QuestionView questionView(DrillKind kind, Question q, BigDecimal tickSize) {
		return new QuestionView(Prediction.VERSION, PredictionText.definition(kind), q.closeTicks(),
			q.targets().stream().map(t -> new TargetView(t.value(), t.role().name(), t.level() ? "LEVEL" : "ZONE",
				t.above() ? "ABOVE" : "BELOW", t.fromTime(), t.lowTicks(), t.highTicks(), t.distance(),
				t.obstacles() == null ? null : t.obstacles().swings(), t.obstacles() == null ? null : t.obstacles().gaps(),
				PredictionText.label(t, tickSize))).toList(),
			q.context().stream().map(c -> new ContextView(c.what(), c.level() ? "LEVEL" : "ZONE", c.fromTime(),
				c.lowTicks(), c.highTicks(), PredictionText.label(c, tickSize))).toList());
	}

	/**
	 * How the future answered: the target reached first, the candle that decided it — counted after the
	 * chart, as the drill was decided — and the story in prices.
	 */
	private static ResultView predictionResult(Drill drill, Question question, List<Bar> revealed, BigDecimal tickSize) {
		int candles = (int) revealed.stream().filter(b -> !b.openTime().isAfter(drill.getOutcomeAt())).count();
		Bar deciding = revealed.get(candles - 1);
		boolean minutes = question.targets().stream().filter(t -> t.reachedBy(deciding)).count() > 1;
		return new ResultView(null, null, null, null, null, drill.getOutcome(), drill.getOutcome().equals(drill.getChoice()),
			List.of(), drill.getOutcomeAt().getEpochSecond(), candles,
			PredictionText.explain(question, drill.getOutcome(), candles, minutes, drill.getTimeframe(), tickSize));
	}

	/**
	 * What each choice made: the ones that paid most are the answer, a tie counting for each of them; the story in
	 * prices and R, ending with what one trade proves (T26).
	 */
	private static ResultView managementResult(Drill drill, Management.Asked asked, BigDecimal tickSize) {
		int candles = asked.outcome().exits().stream().mapToInt(Management.Exit::candles).max().orElseThrow();
		return new ResultView(null, null, null, null, null, drill.getOutcome(), Management.paidMost(drill.getOutcome(),
			drill.getChoice()), List.of(),
			drill.getOutcomeAt().getEpochSecond(), candles,
			Management.explain(asked.trade(), asked.outcome(), asked.halfPrice(), t -> RuleText.price(t, tickSize)));
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
				subject.getKind().equals(drill.getChoice()), List.of(setupView(subject, null, tickSize)), null, null, null);
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
		return new ResultView(found, views.size() - found, extra.size(), extra, excusedMarks, null, null, views, null, null,
			null);
	}

	private static SetupView setupView(DetectedSetup s, Boolean found, BigDecimal tickSize) {
		return new SetupView(s.getId(), s.getDirection(), s.getKind(), s.getShape(),
			s.getStartTime().getEpochSecond(), anchor(s), s.getEndTime().getEpochSecond(), s.getZoneLowTicks(),
			s.getZoneHighTicks(), found, RuleText.label(s), RuleText.explain(s, tickSize), s.getEvidence());
	}

	/** The rule a drill is graded by, in words generated from its parameters. */
	static RuleView rule(Detector detector) {
		return new RuleView(detector.id(), detector.ruleVersion(), detector.parameters(), RuleText.definition(detector),
			detector.methodologySource());
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

	/**
	 * The timeframe a drill is set on (T43): the one asked for, or — on real data — one of {@link #REAL_TIMEFRAMES} at
	 * random. Fabio: "the time on a chart doesnt matter" · "it can be random". A simulated fixture keeps
	 * {@link #DEFAULT_TIMEFRAME}.
	 */
	static Timeframe timeframe(String code, boolean simulated, RandomGenerator random) {
		if (!blank(code)) {
			return Timeframe.fromCode(code);
		}
		if (simulated) {
			return DEFAULT_TIMEFRAME;
		}
		List<Timeframe> real = List.copyOf(REAL_TIMEFRAMES);
		return real.get(random.nextInt(real.size()));
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
