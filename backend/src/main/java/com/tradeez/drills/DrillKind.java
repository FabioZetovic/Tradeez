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

	/** DEC-075: two charts over the same minutes; the marks go on the upper one, where the rule's hits are. */
	SMT_IDENTIFICATION(Mode.IDENTIFY, "SMT", "ICT-66", "SMT divergence", Set.of(), List.of(), 0, 1, 4,
		"Two charts over the same minutes: the market drilled above, the one it is compared with below. Mark the "
			+ "upper chart's candle of every SMT divergence — a swing high or low where one market went beyond its "
			+ "previous one and the other did not.",
		"the swing of a divergence on the upper chart"),

	/** T45: where the stops rest when the chart ends (DrillService#nearestLiquidity). */
	NEAREST_LIQUIDITY_IDENTIFICATION(Mode.IDENTIFY, "LIQUIDITY", "ICT-10", "Buy-side and sell-side liquidity", Set.of("RESTING"),
		List.of(), 1, 2, 2,
		"Mark the two swings that hold the nearest resting liquidity when the chart ends: the nearest swing high above the "
			+ "last close, with buy-side liquidity above it, and the nearest swing low below it, with sell-side liquidity "
			+ "below it — ones price has not traded beyond since.",
		"a swing holding the nearest resting liquidity"),

	/** T45: Fabio's "much much much more important" concept. */
	IFVG_IDENTIFICATION(Mode.IDENTIFY, "IFVG", "ICT-23", "Inversion fair value gaps", Set.of(), List.of(), 0, 1, 4,
		"Mark the middle candle of every fair value gap that a later candle closes straight through — beyond its far "
			+ "edge. That gap has failed, and ICT reads it the other way round from then on.",
		"the middle candle of a gap price closed through"),

	/** T45: the block, the move away from it and the swing it broke all on screen ({@link #showsItsReasons()}). */
	ORDER_BLOCK_IDENTIFICATION(Mode.IDENTIFY, "OB", "ICT-30", "Order blocks", Set.of("FORMED"), List.of(), 1, 1, 3,
		"Mark the order block of every move that broke structure: the last candle closing against the move before a "
			+ "displacement that closes beyond the latest swing.",
		"the last candle of an order block"),

	BREAKER_IDENTIFICATION(Mode.IDENTIFY, "BREAKER", "ICT-31", "Breaker blocks", Set.of("FORMED"), List.of(), 1, 1, 3,
		"Mark the breaker of every failed swing: price takes the liquidity beyond a low (or high), then displaces back "
			+ "through the swing between — the candles that made that swing are the breaker.",
		"the candle of a breaker"),

	UNICORN_IDENTIFICATION(Mode.IDENTIFY, "UNICORN", "ICT-36", "Unicorns", Set.of("FORMED"), List.of(), 1, 1, 3,
		"Mark every unicorn: a breaker that the fair value gap of its own displacement overlaps in price.",
		"the candle of a unicorn's breaker"),

	/** T45: where in its range the last close sits ({@link Judgement#premiumDiscount}). */
	PREMIUM_DISCOUNT_JUDGEMENT(Mode.JUDGE, "PD", "ICT-41", "Premium or discount", Set.of(), List.of(), 1, 0, 0,
		"A dealing range is drawn: its high and its low. Is the last close in premium — above the middle of the range — or "
			+ "in discount, below it? Work the middle out yourself.",
		null),

	/** T45: inside the optimal trade entry or not ({@link Judgement#ote}). */
	OTE_JUDGEMENT(Mode.JUDGE, "OTE", "ICT-42", "Optimal trade entry", Set.of(), List.of(), 1, 0, 0,
		"A leg is drawn: where it started and where it ended. Is the last close inside its optimal trade entry — 62% to "
			+ "79% of the way back from the end toward the start? Work the zone out yourself.",
		null),

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
		null),

	/**
	 * Mode E (T26, DEC-071): a trade is open and in profit — hold, stop to breakeven, or half off? What price did
	 * with each decides ({@link Management}).
	 */
	OPEN_TRADE_MANAGEMENT(Mode.MANAGE, "MANAGE", "ICT-82", "Manage an open trade", Set.of(),
		List.of(List.of("HOLD", "BREAKEVEN", "HALF")), 0, 0, 0,
		"A trade is open and in profit. Hold it to its stop and target, move its stop to the entry, or take half off "
			+ "at the next open and hold the rest?",
		null);

	public enum Mode {
		/** Mode A: mark every instance. */
		IDENTIFY,
		/** Mode B: name the marked structure. */
		CLASSIFY,
		/** Mode C: say what price does next; the future decides. */
		PREDICT,
		/** Mode E: manage an open trade; the future decides (T26). */
		MANAGE,
		/** Mode J: a question about the chart as it stands; a rule decides when it is asked (T45, {@link Judgement}). */
		JUDGE;

		/** Candles shown: a glance for A; for B and C, room for the context the answer depends on; for E, the trade's life. */
		int windowBars() {
			return switch (this) {
				case IDENTIFY -> DrillService.WINDOW_BARS;
				case MANAGE -> DrillService.MANAGE_WINDOW_BARS;
				default -> DrillService.CLASSIFY_WINDOW_BARS;
			};
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

	/**
	 * Every candle its rule read is on the chart (T45): each time in a hit's evidence — the swing a block broke, the
	 * displacement, the gap — at or after the first candle that leaves a swing its left neighbour. The older drills'
	 * rules read nothing before their own start.
	 */
	boolean showsItsReasons() {
		return this == ORDER_BLOCK_IDENTIFICATION || this == BREAKER_IDENTIFICATION || this == UNICORN_IDENTIFICATION;
	}

	/** For J: the answers drawn evenly before a chart is found for one; empty where the market balances them itself. */
	List<String> balancedAnswers() {
		return switch (this) {
			case OTE_JUDGEMENT -> List.of("INSIDE", "OUTSIDE");
			default -> List.of();
		};
	}

	/** Whether a hit of this drill's rule is one the drill asks about. */
	boolean counts(DetectedSetup hit) {
		if (mode == Mode.IDENTIFY) {
			return kinds.isEmpty() || kinds.contains(hit.getKind());
		}
		return options.stream().anyMatch(group -> group.contains(hit.getKind()));
	}

	/** For C: the two answers, in the order offered; for E, the three choices. */
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
