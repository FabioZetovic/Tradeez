package com.tradeez.practice;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tradeez.practice.FillEngine.Direction;
import com.tradeez.practice.PracticeDtos.ChecklistItemView;
import com.tradeez.practice.PracticeDtos.ChecklistView;
import com.tradeez.practice.PracticeDtos.PartialView;
import com.tradeez.practice.PracticeDtos.PlanView;
import com.tradeez.practice.PracticeDtos.ResultView;
import com.tradeez.practice.PracticeDtos.ScoreLineView;
import com.tradeez.practice.PracticeDtos.ScorecardView;

/**
 * scorecard-1.1.0 (T23, DEC-067, BUG-088): each line its own statement, a verdict only on the learner's own rules, and
 * nothing added up. NQ throughout: a quarter-point tick worth $5.
 */
class ScorecardTest {

	private static final BigDecimal TICK = new BigDecimal("0.25");
	private static final long TICK_VALUE = 500;
	private static final ChecklistView SILVER_BULLET = new ChecklistView(TradingModel.SILVER_BULLET, "Silver Bullet",
		"model-sb-1.0.0", List.of(new ChecklistItemView("window", "In the window", true),
			new ChecklistItemView("gap", "A gap", false)), 1, 2);

	/** A long from 80,000 ticks, stop 40 below, target 80 above: 2 : 1, $200 risked on one contract. */
	private static PlanView plan(Direction direction, Long limit, TradingModel model, String bias, Long dol,
			String suggested, String killzone, Integer confidence) {
		long sign = direction == Direction.LONG ? 1 : -1;
		return new PlanView(direction, 80_000 - sign * 40, 80_000 + sign * 80, 1, 80_000, 0, 2.0, model, bias, dol,
			killzone, suggested, model == null ? null : SILVER_BULLET, confidence, limit, null, null, null);
	}

	/** T28: two contracts long, one off at 80,040 with the stop then to the entry; filled at {@code partialAt}. */
	private static String partialLine(long partialAt, long exitAt) {
		PlanView plan = new PlanView(Direction.LONG, 79_960, 80_080, 2, 80_000, 0, 1.5, null, null, null, null, null, null,
			null, null, 80_040L, 1, true);
		ResultView result = new ResultView("TARGET", null, 0, 80_000, exitAt, 80_080, 80, 59_100, 900, 0, 80, false, 1.5, "M1",
			"fill-1.4.0", "mkt-1t/slip-1t/thru-1t/comm-450c", new PartialView(partialAt, 80_040, 1, 40, false));
		return lines(Scorecard.score(plan, result, TICK, TICK_VALUE).outcome()).get("partial").text();
	}

	private static PlanView bare(Direction direction) {
		return plan(direction, null, null, null, null, null, null, null);
	}

	private static ResultView result(String outcome, long entry, long mae, long mfe, double r) {
		return new ResultView(outcome, null, 0, entry, 0, entry, 0, 0, 250, mae, mfe, false, r, "M1", "fill-1.2.0",
			"slip-1t/comm-250c", null);
	}

	private static Map<String, ScoreLineView> lines(List<ScoreLineView> lines) {
		return lines.stream().collect(Collectors.toMap(ScoreLineView::id, Function.identity()));
	}

	@Test
	@DisplayName("a target reached with everything stated: outcome and process side by side, each in words")
	void everything() {
		ScorecardView card = Scorecard.score(plan(Direction.LONG, 50_000L, TradingModel.SILVER_BULLET, "BULLISH", 80_060L,
			"BEARISH", "NEW_YORK_AM", 70), result("TARGET", 80_000, 8, 80, 2.0), TICK, TICK_VALUE);

		assertThat(card.version()).isEqualTo("scorecard-1.1.0");
		assertThat(card.outcome()).extracting(ScoreLineView::id).containsExactly("adverse", "favourable", "costs", "draw");
		Map<String, ScoreLineView> outcome = lines(card.outcome());
		assertThat(outcome.get("adverse").text()).isEqualTo("0.20R against you (8 ticks).");
		assertThat(outcome.get("favourable").text()).isEqualTo("2.00R in your favour (80 ticks).");
		assertThat(outcome.get("draw").text()).isEqualTo("Price reached your draw at 20015.00 while the trade was open.");
		// The outcome side judges nothing.
		assertThat(card.outcome()).extracting(ScoreLineView::verdict).containsOnlyNulls();

		assertThat(card.process()).extracting(ScoreLineView::id)
			.containsExactly("risk", "plan", "model", "bias", "reading", "killzone", "reward", "execution", "confidence");
		Map<String, ScoreLineView> process = lines(card.process());
		assertThat(process.get("risk").text())
			.isEqualTo("Risked $200.00; $207.50 at the stop with slippage and fees, within your $500.00 limit.");
		assertThat(process.get("risk").verdict()).isEqualTo("KEPT");
		assertThat(process.get("plan").text())
			.isEqualTo("4 of 4 stated: a model, a bias, a draw on liquidity and a confidence.");
		assertThat(process.get("model").text()).isEqualTo("Silver Bullet: 1 of 2 checks met when placed.");
		assertThat(process.get("bias").text()).isEqualTo("Long, with your bullish bias.");
		assertThat(process.get("bias").verdict()).isEqualTo("KEPT");
		assertThat(process.get("reading").text())
			.isEqualTo("It suggested bearish; the trade was long, against it. A suggestion, never graded.");
		assertThat(process.get("killzone").text()).isEqualTo("Placed in the New York AM killzone.");
		assertThat(process.get("reward").text()).isEqualTo("Planned 2.00 : 1.");
		assertThat(process.get("execution").text()).isEqualTo("Held to the bracket: the target closed it.");
		assertThat(process.get("confidence").text()).isEqualTo("70% sure it would reach the target before the stop: it did.");
		// Verdicts only on the learner's own rules; the reading, the reward to risk and the rest are stated.
		assertThat(card.process()).filteredOn(l -> l.verdict() != null).extracting(ScoreLineView::id)
			.containsExactly("risk", "bias");
	}

	@Test
	@DisplayName("T27, costs: the fees charged and the ticks slipped by the result's own model, against what was risked")
	void costs() {
		// fill-1.2.0's model: a target exit slips nothing, a stop one tick; $2.50 a round trip.
		assertThat(lines(Scorecard.score(bare(Direction.LONG), result("TARGET", 80_000, 0, 80, 2.0), TICK, TICK_VALUE)
			.outcome()).get("costs").text()).isEqualTo("Fees $2.50: $2.50, 0.01R of what was risked.");
		assertThat(lines(Scorecard.score(bare(Direction.LONG), result("STOP", 80_000, 41, 0, -1.03), TICK, TICK_VALUE)
			.outcome()).get("costs").text()).isEqualTo("Fees $2.50 and 1 tick of slippage ($5.00): $7.50, 0.04R of what was risked.");
		// The standard model on three contracts closed by hand: a tick in, a tick out, $4.50 a contract.
		PlanView three = new PlanView(Direction.LONG, 79_960, 80_080, 3, 80_000, 0, 2.0, null, null, null, null, null, null,
			null, null, null, null, null);
		ResultView closed = new ResultView("CLOSED", null, 0, 80_001, 0, 80_010, 9, 0, 1_350, 0, 12, false, 0.23, "M1",
			"fill-1.3.0", "mkt-1t/slip-1t/thru-1t/comm-450c", null);
		assertThat(lines(Scorecard.score(three, closed, TICK, TICK_VALUE).outcome()).get("costs").text())
			.isEqualTo("Fees $13.50 and 2 ticks of slippage ($30.00): $43.50, 0.07R of what was risked.");
		// The standard model at the target: the entry slipped a tick, the limit filled at its own price.
		ResultView target = new ResultView("TARGET", null, 0, 80_001, 0, 80_080, 79, 0, 450, 0, 79, false, 1.98, "M1",
			"fill-1.3.0", "mkt-1t/slip-1t/thru-1t/comm-450c", null);
		assertThat(lines(Scorecard.score(bare(Direction.LONG), target, TICK, TICK_VALUE).outcome()).get("costs").text())
			.isEqualTo("Fees $4.50 and 1 tick of slippage ($5.00): $9.50, 0.05R of what was risked.");
		ResultView free = new ResultView("TARGET", null, 0, 80_000, 0, 80_080, 80, 0, 0, 0, 80, false, 2.0, "M1", "fill-1.3.0",
			"slip-0t/comm-0c", null);
		assertThat(lines(Scorecard.score(bare(Direction.LONG), free, TICK, TICK_VALUE).outcome()).get("costs").text())
			.isEqualTo("No fees or slippage.");
		// A model this version cannot read never breaks the card: the fees, and what is not known.
		ResultView odd = new ResultView("STOP", null, 0, 80_000, 0, 79_959, -41, 0, 250, 41, 0, false, -1.03, "M1", "test",
			"test", null);
		assertThat(lines(Scorecard.score(bare(Direction.LONG), odd, TICK, TICK_VALUE).outcome()).get("costs").text())
			.isEqualTo("Fees $2.50; the slippage cannot be read from its fill model (test).");
	}

	@Test
	@DisplayName("the risk limit: kept at exactly the limit, broken a cent over it, stated without one")
	void riskLimit() {
		// 40 ticks to the stop at $5, and slip-1t/comm-250c's costs: no market slippage in, a tick out, $2.50 a round
		// trip — $207.50 lost at the stop (BUG-088).
		ResultView stop = result("STOP", 80_000, 41, 3, -1.03);
		ScoreLineView at = lines(Scorecard.score(plan(Direction.LONG, 20_750L, null, null, null, null, null, null), stop, TICK,
			TICK_VALUE).process()).get("risk");
		assertThat(at.verdict()).isEqualTo("KEPT");
		assertThat(at.text()).isEqualTo("Risked $200.00; $207.50 at the stop with slippage and fees, within your $207.50 limit.");

		ScoreLineView over = lines(Scorecard.score(plan(Direction.LONG, 20_749L, null, null, null, null, null, null), stop, TICK,
			TICK_VALUE).process()).get("risk");
		assertThat(over.verdict()).isEqualTo("BROKEN");
		assertThat(over.text()).isEqualTo("Risked $200.00; $207.50 at the stop with slippage and fees, $0.01 over your $207.49 limit.");

		// BUG-088: a limit the price risk keeps is broken by what the stop costs.
		ScoreLineView priceOnly = lines(Scorecard.score(plan(Direction.LONG, 20_000L, null, null, null, null, null, null), stop,
			TICK, TICK_VALUE).process()).get("risk");
		assertThat(priceOnly.verdict()).isEqualTo("BROKEN");
		assertThat(priceOnly.text())
			.isEqualTo("Risked $200.00; $207.50 at the stop with slippage and fees, $7.50 over your $200.00 limit.");

		// Every contract counts: three of them risk $600, and lose $622.50 at the stop.
		PlanView three = new PlanView(Direction.LONG, 79_960, 80_080, 3, 80_000, 0, 2.0, null, null, null, null, null, null, null,
			50_000L, null, null, null);
		ScoreLineView contracts = lines(Scorecard.score(three, stop, TICK, TICK_VALUE).process()).get("risk");
		assertThat(contracts.text())
			.isEqualTo("Risked $600.00; $622.50 at the stop with slippage and fees, $122.50 over your $500.00 limit.");

		ScoreLineView none = lines(Scorecard.score(bare(Direction.LONG), stop, TICK, TICK_VALUE).process()).get("risk");
		assertThat(none.verdict()).isNull();
		assertThat(none.text()).isEqualTo("Risked $200.00; $207.50 at the stop with slippage and fees. No limit was set: set "
			+ "one before a session to have it checked.");

		// A fill model this version cannot read: the price risk alone is judged, and nothing is made up.
		ResultView unread = new ResultView("STOP", null, 0, 80_000, 0, 80_000, 0, 0, 250, 41, 3, false, -1.03, "M1",
			"fill-1.2.0", "a model from later", null);
		ScoreLineView alone = lines(Scorecard.score(plan(Direction.LONG, 20_000L, null, null, null, null, null, null), unread,
			TICK, TICK_VALUE).process()).get("risk");
		assertThat(alone.verdict()).isEqualTo("KEPT");
		assertThat(alone.text()).isEqualTo("Risked $200.00, within your $200.00 limit.");
	}

	@Test
	@DisplayName("a bare bracket: nothing stated, so no model, bias or confidence lines, and the plan line says so")
	void bare() {
		ScorecardView card = Scorecard.score(bare(Direction.SHORT), result("STOP", 80_000, 0, 0, -1.0), TICK, TICK_VALUE);
		assertThat(card.outcome()).extracting(ScoreLineView::id).containsExactly("adverse", "favourable", "costs");
		assertThat(lines(card.outcome()).get("adverse").text()).isEqualTo("It never went against you.");
		assertThat(lines(card.outcome()).get("favourable").text()).isEqualTo("It never went your way.");
		assertThat(card.process()).extracting(ScoreLineView::id).containsExactly("risk", "plan", "killzone", "reward", "execution");
		Map<String, ScoreLineView> process = lines(card.process());
		assertThat(process.get("plan").text())
			.isEqualTo("0 of 4 stated. Not stated: a model, a bias, a draw on liquidity and a confidence.");
		assertThat(process.get("killzone").text()).isEqualTo("Placed outside the killzones.");
		assertThat(process.get("execution").text()).isEqualTo("Held to the bracket: the stop closed it.");
	}

	@Test
	@DisplayName("a bias is kept by trading with it and broken by trading against it, on either side")
	void bias() {
		ResultView stop = result("STOP", 80_000, 41, 3, -1.03);
		ScoreLineView shortBearish = lines(Scorecard.score(plan(Direction.SHORT, null, null, "BEARISH", null, null, null, null),
			stop, TICK, TICK_VALUE).process()).get("bias");
		assertThat(shortBearish.text()).isEqualTo("Short, with your bearish bias.");
		assertThat(shortBearish.verdict()).isEqualTo("KEPT");
		ScoreLineView longBearish = lines(Scorecard.score(plan(Direction.LONG, null, null, "BEARISH", null, "BEARISH",
			"LONDON", null), stop, TICK, TICK_VALUE).process()).get("bias");
		assertThat(longBearish.text()).isEqualTo("Long, against your bearish bias.");
		assertThat(longBearish.verdict()).isEqualTo("BROKEN");
		ScoreLineView shortBullish = lines(Scorecard.score(plan(Direction.SHORT, null, null, "BULLISH", null, "BEARISH", null,
			null), stop, TICK, TICK_VALUE).process()).get("bias");
		assertThat(shortBullish.verdict()).isEqualTo("BROKEN");
		// The reading agrees with a short, and is still not graded.
		ScoreLineView reading = lines(Scorecard.score(plan(Direction.SHORT, null, null, null, null, "BEARISH", null, null),
			stop, TICK, TICK_VALUE).process()).get("reading");
		assertThat(reading.text()).isEqualTo("It suggested bearish; the trade was short, with it. A suggestion, never graded.");
		assertThat(reading.verdict()).isNull();
	}

	@Test
	@DisplayName("the draw: reached on the edge, missed by so many ticks, and one behind the entry reached by going against")
	void draw() {
		// A long filled at 80,002: the draw 50 above the fill, the best point exactly 50.
		PlanView fifty = plan(Direction.LONG, null, null, null, 80_052L, null, null, null);
		assertThat(lines(Scorecard.score(fifty, result("TIME_EXIT", 80_002, 10, 50, 0.3), TICK, TICK_VALUE).outcome())
			.get("draw").text()).startsWith("Price reached your draw at 20013.00");
		assertThat(lines(Scorecard.score(fifty, result("TIME_EXIT", 80_002, 10, 49, 0.3), TICK, TICK_VALUE).outcome())
			.get("draw").text()).isEqualTo("Price did not reach your draw at 20013.00 while the trade was open: it came within 1 tick.");
		// A short whose draw lies above the fill: reached only by going 30 ticks against it.
		PlanView above = plan(Direction.SHORT, null, null, null, 80_030L, null, null, null);
		assertThat(lines(Scorecard.score(above, result("STOP", 80_000, 30, 90, -1.0), TICK, TICK_VALUE).outcome())
			.get("draw").text()).startsWith("Price reached");
		assertThat(lines(Scorecard.score(above, result("STOP", 80_000, 20, 90, -1.0), TICK, TICK_VALUE).outcome())
			.get("draw").text()).endsWith("it came within 10 ticks.");
	}

	@Test
	@DisplayName("how it ended, for the execution and confidence lines: every outcome in its own words")
	void endings() {
		PlanView sure = plan(Direction.LONG, null, null, null, null, null, null, 60);
		Map<String, String[]> expected = Map.of(
			"STOP", new String[] { "Held to the bracket: the stop closed it.", ": it did not." },
			"AMBIGUOUS", new String[] { "Held to the bracket: both levels were touched inside one candle.",
				"which came first is unknown: not counted in your calibration." },
			"TIME_EXIT", new String[] { "Held to the bracket: neither level was reached, and it was closed at the session's close.",
				"Neither was reached before the session closed: not counted in your calibration." },
			"CLOSED", new String[] { "Closed by hand at +0.40R, before the stop or the target.",
				"You closed it before either: not counted in your calibration." });
		expected.forEach((outcome, texts) -> {
			Map<String, ScoreLineView> process = lines(Scorecard.score(sure, result(outcome, 80_000, 5, 20, 0.4), TICK, TICK_VALUE)
				.process());
			assertThat(process.get("execution").text()).as(outcome).isEqualTo(texts[0]);
			assertThat(process.get("confidence").text()).as(outcome).startsWith("60% sure").endsWith(texts[1]);
		});
		assertThat(Scorecard.r(-1.0)).isEqualTo("−1.00R");
		assertThat(Scorecard.r(0)).isEqualTo("0.00R");
		assertThat(Scorecard.money(123_456_789)).isEqualTo("$1,234,567.89");
		assertThat(Scorecard.money(-5)).isEqualTo("−$0.05");
	}

	@Test
	@DisplayName("T28: the partial taken, in R on its contracts; the stop on the rest moved only if the trade outlived its minute")
	void partialMove() {
		assertThat(partialLine(600, 1_200))
			.isEqualTo("1 of 2 contracts taken off at 20010.00: +1.00R on each; the stop on the rest then moved to the entry.");
		assertThat(partialLine(600, 600)).isEqualTo("1 of 2 contracts taken off at 20010.00: +1.00R on each.");
	}
}
