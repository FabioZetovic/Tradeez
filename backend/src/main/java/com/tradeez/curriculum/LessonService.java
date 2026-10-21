package com.tradeez.curriculum;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.tradeez.analysis.DetectedSetup;
import com.tradeez.analysis.Detector;
import com.tradeez.analysis.DetectorRegistry;
import com.tradeez.analysis.EvidenceCalculator;
import com.tradeez.analysis.RuleText;
import com.tradeez.analysis.SetupDtos.RuleView;
import com.tradeez.analysis.SetupDtos.SetupView;
import com.tradeez.analysis.SetupScanner;
import com.tradeez.clock.ClockLayerBuilder;
import com.tradeez.curriculum.ConceptRegistry.Concept;
import com.tradeez.curriculum.LessonDtos.ConceptRef;
import com.tradeez.curriculum.LessonDtos.DrillRef;
import com.tradeez.curriculum.LessonDtos.EvidenceView;
import com.tradeez.curriculum.LessonDtos.Example;
import com.tradeez.curriculum.LessonDtos.LessonResponse;
import com.tradeez.curriculum.LessonDtos.RuleCard;
import com.tradeez.drills.DrillKind;
import com.tradeez.evidence.EvidenceService;
import com.tradeez.evidence.EvidenceText;
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Instrument;
import com.tradeez.marketdata.MarketDataService;
import com.tradeez.marketdata.Timeframe;
import com.tradeez.marketdata.dto.MarketDataDtos.BarResponse;
import com.tradeez.marketdata.dto.MarketDataDtos.CompanionBars;
import com.tradeez.practice.DailyBias;

/**
 * Lesson cards (T14, DEC-055): the first step of the concept loop — what a concept is, the exact rule
 * Tradeez uses for it, and real NQ examples the rule found.
 *
 * <p>The words come from {@code ICT_CONCEPTS.md} (through {@link ConceptRegistry}) and from the rules
 * themselves ({@link RuleText}); nothing on a card is written for the card. The examples are <b>not
 * hand-picked</b>: each is a random hit of the concept's rule in a random stretch of the imported
 * history, so a card shows the rule as it is — its clean cases and its odd ones — and asking again
 * shows others.
 */
@Service
public class LessonService {

	static final int EXAMPLES = 3;
	static final Set<Timeframe> TIMEFRAMES = EnumSet.of(Timeframe.M1, Timeframe.M5, Timeframe.M15);
	static final String INSTRUMENT = "NQ";

	/** Candles shown up to the one a hit is known by, and after it. */
	static final int BEFORE = 32;
	static final int AFTER = 8;

	/** A hit whose start is further back than this is drawn from the chart's left edge. */
	private static final int COMPACT = BEFORE - 2;

	/** One random stretch searched per attempt; a rule found about once a day still turns up. */
	private static final Duration SEARCH = Duration.ofDays(2);
	private static final int MAX_SEARCHES = 12;

	/** How far back to read for the candles before a hit, across weekends and holidays. */
	private static final Duration REACH = Duration.ofDays(5);

	private static final long SEED = MarketDataService.PUBLIC_CHART_SEED;

	/** The rule's base rates on every timeframe counted (T21): its stored evidence, in words. */
	private List<EvidenceView> evidence(Instrument instrument, Detector detector) {
		return evidence.forRule(instrument, detector).stream()
			.map(s -> new EvidenceView(s.timeframe().code(), EvidenceCalculator.VERSION, s.evidence().from().getEpochSecond(),
				s.evidence().to().getEpochSecond(), s.evidence().candles(), s.evidence().hits(),
				EvidenceText.lines(detector.id(), instrument.getSymbol(), s.timeframe(), s.evidence()),
				s.computedAt().getEpochSecond()))
			.toList();
	}

	/** Rules drawn by the New York clock layer rather than detected as setups (DEC-047). */
	private static final Set<String> CLOCK_RULES = Set.of(ClockLayerBuilder.SESSIONS, ClockLayerBuilder.OPENS,
		ClockLayerBuilder.LEVELS, ClockLayerBuilder.IPDA);

	private final SecureRandom random = new SecureRandom();

	private final ConceptRegistry concepts;
	private final DetectorRegistry detectors;
	private final SetupScanner scanner;
	private final MarketDataService marketData;
	private final EvidenceService evidence;

	public LessonService(ConceptRegistry concepts, DetectorRegistry detectors, SetupScanner scanner,
			MarketDataService marketData, EvidenceService evidence) {
		this.concepts = concepts;
		this.detectors = detectors;
		this.scanner = scanner;
		this.marketData = marketData;
		this.evidence = evidence;
	}

	public LessonResponse lesson(String id, String timeframeCode) {
		Map<String, Concept> byId = concepts.concepts().stream()
			.collect(Collectors.toMap(Concept::id, Function.identity()));
		Concept concept = byId.get(id);
		if (concept == null) {
			throw new ConceptNotFoundException(id);
		}
		Timeframe timeframe = timeframeCode == null || timeframeCode.isBlank() ? Timeframe.M5
			: Timeframe.fromCode(timeframeCode);
		if (!TIMEFRAMES.contains(timeframe)) {
			throw new IllegalArgumentException("Examples are shown on " + TIMEFRAMES.stream().map(Timeframe::code).toList()
				+ " charts.");
		}

		List<Detector> rules = detectors.all().stream().filter(d -> d.conceptId().equals(id)).toList();
		boolean clock = concept.rules().stream().anyMatch(CLOCK_RULES::contains);
		Instrument nq = marketData.requireInstrument(INSTRUMENT);
		List<Example> examples = List.of();
		String note;
		boolean model = concept.rules().stream().anyMatch(r -> r.startsWith("model-") || r.equals(DailyBias.VERSION));
		if (rules.isEmpty()) {
			note = clock ? "Drawn by the New York clock layer on the Chart tab — switch it on there to see it."
				: model ? "Read in Practice (T22): name it when you plan a trade, and the app checks it against the chart at that "
					+ "moment and keeps the result with the trade."
				: "Tradeez does not detect this concept yet, so there are no examples of it to show.";
		} else {
			Instant[] coverage = marketData.coverage(nq);
			if (coverage == null) {
				note = "No real NQ history has been imported, so there are no examples to show.";
			} else {
				examples = examples(rules, nq, timeframe, coverage);
				note = examples.isEmpty() ? "None turned up this time. Ask again for another look."
					: examples.size() < EXAMPLES ? "Only " + examples.size() + " turned up this time." : null;
			}
		}

		return new LessonResponse(concept.id(), concept.name(), concept.level(), concept.detectability(),
			concept.status().name(), concept.statusNote(), concept.summary(),
			concept.prerequisites().stream().map(p -> ref(byId.get(p))).toList(),
			concepts.concepts().stream().filter(c -> c.prerequisites().contains(id)).map(LessonService::ref).toList(),
			rules.stream().map(d -> new RuleCard(d.id(), d.ruleVersion(), d.parameters(), RuleText.definition(d),
				d.methodologySource(), evidence(nq, d))).toList(),
			!rules.isEmpty() ? "SETUPS" : clock ? "CLOCK" : null,
			Arrays.stream(DrillKind.values()).filter(k -> k.conceptId().equals(id))
				.map(k -> new DrillRef(k.name(), k.mode().name(), k.title())).toList(),
			nq.getSymbol(), nq.getTickSize(), nq.getExchangeTimezone(), timeframe.code(), examples, note);
	}

	private static ConceptRef ref(Concept concept) {
		return new ConceptRef(concept.id(), concept.name());
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Up to {@value #EXAMPLES} hits, each from its own random stretch of the imported history, the
	 * concept's rules taken in turn. Every search's hits are stored, like any scan's (DEC-026).
	 */
	private List<Example> examples(List<Detector> rules, Instrument nq, Timeframe timeframe, Instant[] coverage) {
		long step = timeframe.seconds();
		Instant earliest = coverage[0].plus(REACH);
		Instant latest = coverage[1].minus(SEARCH);
		if (!earliest.isBefore(latest)) {
			return List.of();
		}
		List<Example> found = new ArrayList<>();
		List<Instant[]> shown = new ArrayList<>();
		for (int search = 0; search < MAX_SEARCHES && found.size() < EXAMPLES; search++) {
			Detector rule = rules.get(search % rules.size());
			long second = earliest.getEpochSecond() + random.nextLong(latest.getEpochSecond() - earliest.getEpochSecond());
			Instant from = Instant.ofEpochSecond(second - Math.floorMod(second, step));
			Instant to = from.plus(SEARCH);
			scanner.ensureScanned(rule, nq, timeframe, SEED, from, to);
			List<DetectedSetup> hits = new ArrayList<>(scanner.visible(rule, nq, timeframe, SEED, from, to, to));
			Collections.shuffle(hits, random);
			// Hits the chart can show whole first; a level taken long after its swing only if nothing else.
			hits.sort(Comparator.comparing((DetectedSetup h) ->
				h.getStartTime().isBefore(h.getEndTime().minusSeconds(COMPACT * step))));
			for (DetectedSetup hit : hits) {
				Example example = around(rule, hit, nq, timeframe, shown);
				if (example != null) {
					found.add(example);
					break;
				}
			}
		}
		return found;
	}

	/**
	 * The hit on {@value #BEFORE} candles up to the one it is known by and {@value #AFTER} after — one
	 * contract, every candle the data has, closures and all — or null when the stretch holds a roll,
	 * or another example already shows it.
	 */
	private Example around(Detector rule, DetectedSetup hit, Instrument nq, Timeframe timeframe, List<Instant[]> shown) {
		long step = timeframe.seconds();
		Instant end = hit.getEndTime();
		List<Bar> upTo = marketData.internalBars(nq, timeframe, end.minus(REACH), end.plusSeconds(step), SEED).bars();
		if (upTo.isEmpty() || !upTo.getLast().openTime().equals(end)) {
			return null;
		}
		List<Bar> bars = new ArrayList<>(upTo.subList(Math.max(0, upTo.size() - BEFORE), upTo.size()));
		List<Bar> after = marketData.internalBars(nq, timeframe, end.plusSeconds(step), end.plus(REACH), SEED).bars();
		bars.addAll(after.subList(0, Math.min(AFTER, after.size())));
		Instant first = bars.getFirst().openTime();
		Instant last = bars.getLast().openTime().plusSeconds(step);
		// A rule that reads a second market (DEC-075) is shown with it, which must not roll either.
		String other = rule.companionOf(nq.getSymbol());
		Instrument companion = other == null ? null : marketData.requireInstrument(other);
		if (!marketData.rolls(nq, first, last).isEmpty()
				|| (companion != null && !marketData.rolls(companion, first, last).isEmpty())
				|| shown.stream().anyMatch(w -> first.isBefore(w[1]) && w[0].isBefore(last))) {
			return null;
		}
		shown.add(new Instant[] { first, last });
		return new Example(rule.id(), RuleText.label(hit), RuleText.explain(hit, nq.getTickSize()), timeframe.code(),
			responses(bars), SetupView.of(hit, Instant::getEpochSecond), RuleView.of(rule),
			companion == null ? null : new CompanionBars(companion.getSymbol(), companion.getTickSize(),
				responses(marketData.internalBars(companion, timeframe, first, last, SEED).bars())));
	}

	private static List<BarResponse> responses(List<Bar> bars) {
		return bars.stream().map(b -> new BarResponse(b.openTime().getEpochSecond(), b.openTicks(), b.highTicks(),
			b.lowTicks(), b.closeTicks(), b.volume())).toList();
	}

	/** Unknown concept id. */
	public static class ConceptNotFoundException extends RuntimeException {
		public ConceptNotFoundException(String id) {
			super("No concept '" + id + "' in the curriculum.");
		}
	}
}
