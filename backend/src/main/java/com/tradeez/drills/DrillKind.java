package com.tradeez.drills;

import java.util.List;
import java.util.Set;

import com.tradeez.analysis.DetectedSetup;

/**
 * Every drill there is (T13, DEC-054): one question a versioned rule can answer about a chart.
 *
 * <p>Three modes, from the curriculum's teaching modes. <b>Identify</b> (A): "mark every …" — the rule's
 * hits in the window are the answer key, and the learner's marks are compared with it. <b>Classify</b>
 * (B): one structure is drawn and the learner names it — the rule's own name for it is the answer.
 * Both are graded against the rule, which is named on screen; neither says the structure matters.
 * <b>Predict</b> (C, T15, DEC-058): two targets are drawn and the chart stops — which does price reach
 * first? Graded by what real price did ({@link Prediction}), never by whether a reading was "right".
 *
 * <p>A new drill is a new constant here (and in V14's {@code ck_drill_kind}): the engine — window,
 * answer key, grading, explanations — is the same for all of them.
 */
public enum DrillKind {

	FVG_IDENTIFICATION(Mode.IDENTIFY, "FVG", "ICT-20", "Fair value gaps", Set.of(), List.of(), 0, 1, 6,
		"Mark the middle candle of every fair value gap in this chart, then check your answer. The middle "
			+ "candle is the one whose move left the gap.",
		"the middle candle of a gap"),

	SWING_HIGH_IDENTIFICATION(Mode.IDENTIFY, "SWING", "ICT-01", "Swing highs", Set.of("HIGH"), List.of(), 0, 2, 10,
		"Mark every swing high in this chart, then check your answer: a candle whose high stands above "
			+ "its neighbours'.",
		"a swing high"),

	SWING_LOW_IDENTIFICATION(Mode.IDENTIFY, "SWING", "ICT-01", "Swing lows", Set.of("LOW"), List.of(), 0, 2, 10,
		"Mark every swing low in this chart, then check your answer: a candle whose low stands below "
			+ "its neighbours'.",
		"a swing low"),

	VI_IDENTIFICATION(Mode.IDENTIFY, "VI", "ICT-25", "Volume imbalances", Set.of(), List.of(), 0, 1, 5,
		"Mark the second candle of every volume imbalance: two candles in a row whose bodies do not touch "
			+ "while their wicks overlap.",
		"the second candle of a volume imbalance"),

	DISPLACEMENT_IDENTIFICATION(Mode.IDENTIFY, "DISPLACEMENT", "ICT-04", "Displacement", Set.of(), List.of(), 0, 1, 4,
		"Mark the middle candle of every displacement: a move whose bodies are large against the candles "
			+ "before it, and that leaves a fair value gap behind.",
		"the middle candle of a displacement"),

	EQHL_IDENTIFICATION(Mode.IDENTIFY, "EQHL", "ICT-12", "Relative equal highs and lows", Set.of(), List.of(), 1, 1, 5,
		"Mark the second swing of every pair of relative equal highs or lows — the swing that completes "
			+ "the pair.",
		"the second swing of an equal pair"),

	SWEEP_IDENTIFICATION(Mode.IDENTIFY, "SWEEP", "ICT-13", "Liquidity sweeps", Set.of("SWEEP"), List.of(), 1, 1, 6,
		"Mark every candle that sweeps a swing high or low shown on this chart: it trades beyond the level "
			+ "and closes back inside, in the same candle.",
		"a sweep of a level on this chart"),

	STRUCTURE_CLASSIFICATION(Mode.CLASSIFY, "STRUCTURE", "ICT-03", "Market structure", Set.of(),
		List.of(List.of("HH", "LH", "EH"), List.of("HL", "LL", "EL")), 0, 0, 0,
		"Name the marked swing against the previous swing of its kind: higher, lower or equal.", null),

	BOS_CHOCH_CLASSIFICATION(Mode.CLASSIFY, "BOS_CHOCH", "ICT-06", "Break of structure or change of character",
		Set.of(), List.of(List.of("BOS", "CHOCH")), 1, 0, 0,
		"The marked candle is the first to close beyond the swing level drawn. Read the structure before "
			+ "it: is this a break of structure, with the trend, or a change of character, against it?",
		null),

	SWEEP_RUN_CLASSIFICATION(Mode.CLASSIFY, "SWEEP", "ICT-13", "Sweep or run", Set.of(),
		List.of(List.of("SWEEP", "RUN")), 1, 0, 0,
		"The marked candle traded beyond the swing level drawn. Did it sweep the level, or run through it?",
		null),

	LIQUIDITY_FIRST_PREDICTION(Mode.PREDICT, "LIQUIDITY", "ICT-16", "Which liquidity is taken first", Set.of(),
		List.of(List.of("BSL", "SSL")), 1, 0, 0,
		"The chart stops here. Above price rests the nearest buy-side liquidity, below it the nearest "
			+ "sell-side: the edges of the range price is in. Which does price take first?",
		null),

	ERL_IRL_PREDICTION(Mode.PREDICT, "LIQUIDITY", "ICT-15", "External or internal liquidity next", Set.of(),
		List.of(List.of("IRL", "ERL")), 1, 0, 0,
		"The last candle took resting liquidity — external liquidity. Does price next reach the gap left "
			+ "behind (internal), or the next resting level beyond (external)?",
		null);

	public enum Mode {
		/** Mode A: mark every instance. */
		IDENTIFY,
		/** Mode B: name the marked structure. */
		CLASSIFY,
		/** Mode C: say what price does next; the future decides. */
		PREDICT;

		/** Candles shown: a glance for A; for B and C, room for the context the answer depends on. */
		int windowBars() {
			return this == IDENTIFY ? DrillService.WINDOW_BARS : DrillService.CLASSIFY_WINDOW_BARS;
		}
	}

	private final Mode mode;
	private final String detector;
	private final String conceptId;
	private final String title;
	private final Set<String> kinds;
	private final List<List<String>> options;
	private final int lead;
	private final int minHits;
	private final int maxHits;
	private final String instructions;
	private final String markName;

	/**
	 * @param kinds   for A, the hit kinds that count (empty: every hit of the rule)
	 * @param options for B, the names offered — one group per side, so a swing high is never offered
	 *                "lower low"
	 * @param lead    candles that must be on screen before a hit's start: one where the start is a
	 *                swing, whose left neighbour is what makes it one
	 */
	DrillKind(Mode mode, String detector, String conceptId, String title, Set<String> kinds,
			List<List<String>> options, int lead, int minHits, int maxHits, String instructions, String markName) {
		this.mode = mode;
		this.detector = detector;
		this.conceptId = conceptId;
		this.title = title;
		this.kinds = kinds;
		this.options = options;
		this.lead = lead;
		this.minHits = minHits;
		this.maxHits = maxHits;
		this.instructions = instructions;
		this.markName = markName;
	}

	public Mode mode() {
		return mode;
	}

	public String detector() {
		return detector;
	}

	public String conceptId() {
		return conceptId;
	}

	public String title() {
		return title;
	}

	public String instructions() {
		return instructions;
	}

	/** For A: what a correct mark is, for "marked, but not … under this rule". */
	public String markName() {
		return markName;
	}

	int lead() {
		return lead;
	}

	int minHits() {
		return minHits;
	}

	int maxHits() {
		return maxHits;
	}

	/** Whether a hit of this drill's rule is one the drill asks about. */
	boolean counts(DetectedSetup hit) {
		if (mode == Mode.IDENTIFY) {
			return kinds.isEmpty() || kinds.contains(hit.getKind());
		}
		return options.stream().anyMatch(group -> group.contains(hit.getKind()));
	}

	/** For C: the two answers, in the order offered. */
	List<String> predictions() {
		return options.getFirst();
	}

	/** For B: the names offered for this subject — the group its own name is in. */
	List<String> optionsFor(DetectedSetup subject) {
		return options.stream().filter(group -> group.contains(subject.getKind())).findFirst().orElseThrow();
	}

	/** For B: whether the subject is at a high (otherwise a low) — what the chart shows, not the answer. */
	boolean subjectIsHigh(DetectedSetup subject) {
		return switch (this) {
			case STRUCTURE_CLASSIFICATION -> options.getFirst().contains(subject.getKind());
			case BOS_CHOCH_CLASSIFICATION -> "BULLISH".equals(subject.getDirection());
			case SWEEP_RUN_CLASSIFICATION -> "BSL".equals(subject.getEvidence().get("side"));
			default -> throw new IllegalStateException(this + " has no subject");
		};
	}

	static DrillKind parse(String code) {
		try {
			return valueOf(code.trim());
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Unknown drill kind '" + code + "'. Supported: "
				+ List.of(values()));
		}
	}
}
