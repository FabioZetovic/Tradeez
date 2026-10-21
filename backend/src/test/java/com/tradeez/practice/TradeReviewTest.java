package com.tradeez.practice;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tradeez.marketdata.Bar;
import com.tradeez.practice.FillEngine.Direction;
import com.tradeez.practice.FillEngine.Outcome;
import com.tradeez.practice.TradeReview.Facts;
import com.tradeez.practice.TradeReview.Hit;
import com.tradeez.practice.TradeReview.Moment;
import com.tradeez.practice.TradeReview.Review;

/**
 * review-1.0.0 (T24, DEC-068): a finished trade told in order — the rules before the decision, the trade, the
 * rules while it ran, the exit — and what hindsight adds. A long on 5-minute candles, placed at 10:00 New York,
 * filled at 10:00:30, out at 10:30.
 */
class TradeReviewTest {

	private static final Instant DECISION = Instant.parse("2025-03-05T15:00:00Z");
	private static final Instant ENTRY = DECISION.plusSeconds(30);
	private static final Instant EXIT = DECISION.plus(Duration.ofMinutes(30));
	private static final Duration CANDLE = Duration.ofMinutes(5);

	private static Hit hit(String detector, Instant at) {
		return new Hit(detector, at, detector + " at " + at, List.of("why " + detector));
	}

	/** A plain long: no model, bias, draw or confidence; the bracket's own exit; the cursor at the exit. */
	private static Facts facts(List<Hit> hits, Long dol, List<Bar> bars, Outcome outcome, Instant plannedExit, Instant cursor) {
		return new Facts(Direction.LONG, 1, 80_000, 79_960, 80_080, new BigDecimal("0.25"), CANDLE, null, null, dol, null,
			DECISION, ENTRY, 80_001, EXIT, outcome == Outcome.CLOSED ? 80_020 : 80_080, outcome, outcome == Outcome.CLOSED ? 0.5 : 1.98,
			false, plannedExit, Outcome.STOP, 79_959, -1.03, cursor, hits, bars, null, null);
	}

	private static Facts plain(List<Hit> hits) {
		return facts(hits, null, List.of(), Outcome.TARGET, null, EXIT);
	}

	private static Bar bar(Instant at, long low, long high) {
		return new Bar(at, low, high, low, high, 1);
	}

	@Test
	@DisplayName("in order: the rules before, the decision, the entry, the rules while it ran, the exit")
	void order() {
		List<Hit> hits = List.of(hit("FVG", DECISION.minus(Duration.ofMinutes(20))), hit("MSS", DECISION.plus(Duration.ofMinutes(10))),
			hit("LIQUIDITY", DECISION.minus(Duration.ofMinutes(5))));
		Review review = TradeReview.build(plain(hits));
		assertThat(review.moments()).extracting(Moment::kind).containsExactly("RULE", "RULE", "DECISION", "ENTRY", "RULE", "EXIT");
		assertThat(review.moments()).extracting(Moment::phase)
			.containsExactly("BEFORE", "BEFORE", "TRADE", "TRADE", "TRADE", "TRADE");
		assertThat(review.moments().get(0).title()).startsWith("FVG");
		assertThat(review.moments().get(4).lines()).containsExactly("why MSS");
		assertThat(review.hitsUsed()).containsExactlyInAnyOrder(0, 1, 2);
		assertThat(review.moments().get(2).title()).isEqualTo("You placed a long");
		assertThat(review.moments().get(2).lines()).containsExactly("Long 1 contract at market from 20000.00: stop 19990.00, target 20020.00, 2.00 : 1.");
		assertThat(review.moments().get(3).title()).isEqualTo("Filled at 20000.25");
		assertThat(review.moments().getLast().title()).isEqualTo("The target filled at 20020.00: +1.98R.");
		assertThat(review.summary()).isEmpty();
	}

	@Test
	@DisplayName("the windows' edges: thirty candles back is out, the decision itself is before it, the exit itself is in")
	void edges() {
		List<Hit> hits = List.of(hit("FVG", DECISION.minus(CANDLE.multipliedBy(30))), hit("SWEEP", DECISION),
			hit("MSS", EXIT), hit("FVG", EXIT.plusSeconds(1)));
		Review review = TradeReview.build(plain(hits));
		assertThat(review.hitsUsed()).containsExactly(1, 2);
		// A hit confirmed at the very moment of the decision was there to be seen: it comes first.
		assertThat(review.moments()).extracting(Moment::kind).containsExactly("RULE", "DECISION", "ENTRY", "RULE", "EXIT");
		assertThat(review.moments().get(0).phase()).isEqualTo("BEFORE");
		// A hit at the exit's instant was seen while the trade was open, and is told before the exit.
		assertThat(review.moments().get(3).phase()).isEqualTo("TRADE");
	}

	@Test
	@DisplayName("before the decision, the latest three of each rule; while it ran, the first twenty-four and a count")
	void caps() {
		List<Hit> hits = new ArrayList<>();
		for (int i = 1; i <= 5; i++) {
			hits.add(hit("FVG", DECISION.minus(CANDLE.multipliedBy(i))));
		}
		for (int i = 1; i <= 26; i++) {
			hits.add(hit("LIQUIDITY", DECISION.plusSeconds(60L * i)));
		}
		Review review = TradeReview.build(facts(hits, null, List.of(), Outcome.TARGET, null, EXIT));
		// The three latest FVGs: one, two and three candles back.
		assertThat(review.hitsUsed().subList(0, 3)).containsExactlyInAnyOrder(0, 1, 2);
		assertThat(review.moments()).filteredOn(m -> m.phase().equals("BEFORE")).hasSize(3);
		assertThat(review.moments()).filteredOn(m -> m.kind().equals("RULE") && m.phase().equals("TRADE")).hasSize(24)
			.first().extracting(Moment::at).isEqualTo(DECISION.plusSeconds(60));
		assertThat(review.summary()).containsExactly(
			"2 more rule hits while it ran are not listed: switch the rules on above the chart to see every one.");
	}

	@Test
	@DisplayName("the draw: reached while it ran, reached after it ended, or not reached — never by a candle before the entry")
	void draw() {
		Instant entryMinute = DECISION;
		List<Bar> before = List.of(bar(entryMinute.minusSeconds(60), 80_090, 80_110));
		List<Bar> during = List.of(bar(entryMinute.minusSeconds(60), 80_090, 80_110), bar(entryMinute, 79_995, 80_010),
			bar(entryMinute.plus(Duration.ofMinutes(12)), 80_095, 80_100));
		Moment open = TradeReview.build(facts(List.of(), 80_100L, during, Outcome.TARGET, null, EXIT)).moments().stream()
			.filter(m -> m.kind().equals("DRAW")).findFirst().orElseThrow();
		assertThat(open.at()).isEqualTo(entryMinute.plus(Duration.ofMinutes(12)));
		assertThat(open.phase()).isEqualTo("TRADE");
		assertThat(open.title()).isEqualTo("Price reached your draw at 20025.00");

		List<Bar> after = List.of(bar(entryMinute, 79_995, 80_010), bar(EXIT.plus(Duration.ofMinutes(1)), 80_098, 80_100));
		Review late = TradeReview.build(facts(List.of(), 80_100L, after, Outcome.TARGET, null, EXIT.plus(Duration.ofHours(1))));
		assertThat(late.moments().getLast().kind()).isEqualTo("DRAW");
		assertThat(late.moments().getLast().phase()).isEqualTo("AFTER");
		assertThat(late.moments().getLast().lines()).containsExactly("After the trade had ended.");

		// A market that opens beyond the draw has reached it, though no candle traded at its price.
		List<Bar> gap = List.of(bar(entryMinute, 79_995, 80_010), bar(entryMinute.plus(Duration.ofMinutes(3)), 80_105, 80_120));
		assertThat(TradeReview.build(facts(List.of(), 80_100L, gap, Outcome.TARGET, null, EXIT)).moments())
			.filteredOn(m -> m.kind().equals("DRAW")).extracting(Moment::at).containsExactly(entryMinute.plus(Duration.ofMinutes(3)));
		// A draw below the fill is reached by a low at or under it — a high above it says nothing.
		List<Bar> down = List.of(bar(entryMinute, 79_990, 80_010), bar(entryMinute.plus(Duration.ofMinutes(4)), 79_980, 79_995));
		assertThat(TradeReview.build(facts(List.of(), 79_980L, down, Outcome.TARGET, null, EXIT)).moments())
			.filteredOn(m -> m.kind().equals("DRAW")).extracting(Moment::at).containsExactly(entryMinute.plus(Duration.ofMinutes(4)));

		Review never = TradeReview.build(facts(List.of(), 80_100L, before, Outcome.TARGET, null, EXIT));
		assertThat(never.moments()).extracting(Moment::kind).doesNotContain("DRAW");
		assertThat(never.summary()).containsExactly("Your draw at 20025.00 was not reached in what the replay has shown.");
	}

	@Test
	@DisplayName("closed by hand: what the bracket would have done, once the replay has passed it — and not a moment before")
	void held() {
		Instant planned = EXIT.plus(Duration.ofMinutes(40));
		Review ahead = TradeReview.build(facts(List.of(), null, List.of(), Outcome.CLOSED, planned, planned));
		assertThat(ahead.moments()).extracting(Moment::kind).doesNotContain("HELD");
		assertThat(ahead.moments().getLast().title()).isEqualTo("You closed it at 20005.00: +0.50R.");
		assertThat(ahead.summary()).containsExactly(
			"What the bracket would have done without your close is still ahead of the replay: step on to see it.");

		Review passed = TradeReview.build(facts(List.of(), null, List.of(), Outcome.CLOSED, planned, planned.plusSeconds(1)));
		Moment held = passed.moments().getLast();
		assertThat(held.kind()).isEqualTo("HELD");
		assertThat(held.phase()).isEqualTo("AFTER");
		assertThat(held.at()).isEqualTo(planned);
		assertThat(held.title()).isEqualTo("Had you held, the stop would have filled at 19989.75: −1.03R.");
		assertThat(passed.summary()).isEmpty();
	}

	@Test
	@DisplayName("the held bracket in its own words: a target, a one-candle tie, the session's close")
	void heldWords() {
		Instant planned = EXIT.plus(Duration.ofMinutes(40));
		Map<Outcome, String> words = Map.of(
			Outcome.TARGET, "Had you held, the target would have filled at 20020.00: +2.00R.",
			Outcome.AMBIGUOUS, "Had you held, stop and target would have been touched inside one candle, scored as the stop, at 20020.00: +2.00R.",
			Outcome.TIME_EXIT, "Had you held, the session's close would have taken it out at 20020.00: +2.00R.");
		words.forEach((outcome, title) -> {
			Facts f = new Facts(Direction.LONG, 1, 80_000, 79_960, 80_080, new BigDecimal("0.25"), CANDLE, null, null, null, null,
				DECISION, ENTRY, 80_001, EXIT, 80_020, Outcome.CLOSED, 0.5, false, planned, outcome, 80_080, 2.0,
				planned.plusSeconds(1), List.of(), List.of(), null, null);
			assertThat(TradeReview.build(f).moments().getLast().title()).as(outcome.name()).isEqualTo(title);
		});
	}

	@Test
	@DisplayName("the decision in the learner's words: the model's checklist, the bias, the draw and the confidence")
	void decision() {
		Facts f = new Facts(Direction.SHORT, 3, 80_000, 80_040, 79_960, new BigDecimal("0.25"), CANDLE,
			"Silver Bullet: 3 of 5 checks met when placed.", "BEARISH", 79_900L, 70, DECISION, ENTRY, 79_999, EXIT, 80_041,
			Outcome.STOP, -1.03, true, null, Outcome.STOP, 80_041, -1.03, EXIT, List.of(), List.of(), null, null);
		Review review = TradeReview.build(f);
		assertThat(review.moments().getFirst().title()).isEqualTo("You placed a short");
		assertThat(review.moments().getFirst().lines()).containsExactly(
			"Short 3 contracts at market from 20000.00: stop 20010.00, target 19990.00, 1.00 : 1.",
			"Silver Bullet: 3 of 5 checks met when placed.", "Your bias: bearish.", "Your draw on liquidity: 19975.00.",
			"70% sure the target would fill before the stop.");
		assertThat(review.moments().getLast().title()).isEqualTo("The stop filled, the market opening beyond it, at 20010.25: −1.03R.");
	}

	@Test
	@DisplayName("T28: a partial in the trade's story; the stop moved only if the trade outlived the partial's candle")
	void partial() {
		FillEngine.Partial planned = new FillEngine.Partial(80_040, 1, true);
		Facts later = new Facts(Direction.LONG, 2, 80_000, 79_960, 80_080, new BigDecimal("0.25"), CANDLE, null, null, null,
			null, DECISION, ENTRY, 80_000, EXIT, 79_999, Outcome.STOP, 0.49, false, null, Outcome.STOP, 79_999, 0.49, EXIT,
			List.of(), List.of(), planned, new FillEngine.Leg(ENTRY.plusSeconds(600), 80_040, 40, 1, false));
		Review review = TradeReview.build(later);
		assertThat(review.moments().getFirst().lines())
			.contains("Take 1 off at 20010.00, then move the stop on the rest to the entry.");
		TradeReview.Moment partial = review.moments().stream().filter(m -> m.kind().equals("PARTIAL")).findFirst().orElseThrow();
		assertThat(partial.title()).isEqualTo("1 of 2 taken off at 20010.00: +1.00R on each.");
		assertThat(partial.lines())
			.containsExactly("The stop on the last contract moved to the entry, 20000.00, once that candle had closed.");

		Facts together = new Facts(Direction.LONG, 2, 80_000, 79_960, 80_080, new BigDecimal("0.25"), CANDLE, null, null, null,
			null, DECISION, ENTRY, 80_000, EXIT, 80_080, Outcome.TARGET, 1.5, false, null, Outcome.TARGET, 80_080, 1.5, EXIT,
			List.of(), List.of(), planned, new FillEngine.Leg(EXIT, 80_040, 40, 1, false));
		List<TradeReview.Moment> moments = TradeReview.build(together).moments();
		assertThat(moments.stream().filter(m -> m.kind().equals("PARTIAL")).findFirst().orElseThrow().lines()).isEmpty();
		assertThat(moments.getLast().kind()).as("the partial before the exit at the same moment").isEqualTo("EXIT");
	}
}
