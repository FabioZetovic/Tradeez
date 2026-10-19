package com.tradeez.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tradeez.practice.Calibration.Sample;
import com.tradeez.practice.FillEngine.Outcome;
import com.tradeez.practice.PracticeDtos.CalibrationBucketView;
import com.tradeez.practice.PracticeDtos.CalibrationView;

/** calibration-1.0.0 (T23, DEC-067): stated confidence against how often the bracket's target came first. */
class CalibrationTest {

	private static List<Sample> samples(int confidence, int targets, int stops) {
		List<Sample> samples = new ArrayList<>();
		for (int i = 0; i < targets; i++) {
			samples.add(new Sample(confidence, Outcome.TARGET));
		}
		for (int i = 0; i < stops; i++) {
			samples.add(new Sample(confidence, Outcome.STOP));
		}
		return samples;
	}

	@Test
	@DisplayName("the Wilson interval: 2 of 10 is 6% to 51%; none and all stay inside 0 and 1")
	void wilson() {
		double[] two = Calibration.wilson(2, 10);
		assertThat(two[0]).isCloseTo(0.0567, within(0.0005));
		assertThat(two[1]).isCloseTo(0.5098, within(0.0005));
		assertThat(Calibration.wilson(0, 5)[0]).isZero();
		assertThat(Calibration.wilson(0, 5)[1]).isCloseTo(0.4345, within(0.0005));
		assertThat(Calibration.wilson(5, 5)[1]).isEqualTo(1.0);
	}

	@Test
	@DisplayName("nothing yet: no average, no interval, and a sentence on how to start")
	void empty() {
		CalibrationView none = Calibration.of(List.of());
		assertThat(none.version()).isEqualTo("calibration-1.0.0");
		assertThat(none.trades()).isZero();
		assertThat(none.meanConfidence()).isNull();
		assertThat(none.low()).isNull();
		assertThat(none.buckets()).isEmpty();
		assertThat(none.summary()).singleElement().asString().startsWith("No finished trade says how sure you were yet.");
	}

	@Test
	@DisplayName("trades the bracket did not decide are counted apart, each reason named")
	void undecided() {
		CalibrationView view = Calibration.of(List.of(new Sample(50, Outcome.CLOSED), new Sample(50, Outcome.CLOSED),
			new Sample(70, Outcome.TIME_EXIT), new Sample(90, Outcome.AMBIGUOUS)));
		assertThat(view.trades()).isEqualTo(4);
		assertThat(view.decided()).isZero();
		assertThat(view.closedByHand()).isEqualTo(2);
		assertThat(view.timeExits()).isEqualTo(1);
		assertThat(view.ambiguous()).isEqualTo(1);
		assertThat(view.buckets()).isEmpty();
		assertThat(view.summary()).containsExactly(
			"None of your trades with a confidence has reached its target or its stop yet.",
			"Not counted: 2 closed by hand, 1 ended by the session's close, 1 with both levels inside one candle. "
				+ "The bracket did not decide them.");
		assertThat(Calibration.of(List.of(new Sample(50, Outcome.CLOSED))).summary().getLast())
			.isEqualTo("Not counted: 1 closed by hand. The bracket did not decide it.");
	}

	@Test
	@DisplayName("sure of 90% and right twice in ten: above the range the results allow — more sure than the trades")
	void overconfident() {
		CalibrationView view = Calibration.of(samples(90, 2, 8));
		assertThat(view.decided()).isEqualTo(10);
		assertThat(view.hits()).isEqualTo(2);
		assertThat(view.meanConfidence()).isEqualTo(90.0);
		assertThat(view.low()).isEqualTo(6);
		assertThat(view.high()).isEqualTo(51);
		assertThat(view.buckets()).containsExactly(new CalibrationBucketView(90, 10, 2));
		assertThat(view.summary()).containsExactly(
			"On 10 trades the bracket decided, you said 90% on average; 2 reached the target first (20%).",
			"A hit rate from 6% to 51% fits these results (95%), and your 90% is above it: so far you have been more sure "
				+ "than your trades.");
	}

	@Test
	@DisplayName("sure of 10% and right nine times in ten: below the range — less sure than the trades")
	void underconfident() {
		CalibrationView view = Calibration.of(samples(10, 9, 1));
		assertThat(view.summary().get(1)).endsWith("your 10% is below it: so far you have been less sure than your trades.");
	}

	@Test
	@DisplayName("two of four at 50%: inside the range, and the buckets stay apart and in order")
	void inside() {
		List<Sample> mixed = new ArrayList<>(samples(70, 1, 1));
		mixed.addAll(samples(30, 1, 1));
		CalibrationView view = Calibration.of(mixed);
		assertThat(view.meanConfidence()).isEqualTo(50.0);
		assertThat(view.buckets()).containsExactly(new CalibrationBucketView(30, 2, 1), new CalibrationBucketView(70, 2, 1));
		assertThat(view.summary().get(1))
			.endsWith("your 50% is inside it: so far your confidence matches your results as closely as 4 trades can show.");
		// One trade reads in the singular.
		CalibrationView one = Calibration.of(samples(20, 0, 1));
		assertThat(one.summary().getFirst()).startsWith("On 1 trade the bracket decided, you said 20% on average; 0 reached");
		assertThat(one.low()).isZero();
		assertThat(one.high()).isEqualTo(79);
	}

	@Test
	@DisplayName("on the range's edge is inside it: 70% with 1 of 4 (5% to 70%), and 30% with 3 of 4 (30% to 95%)")
	void edges() {
		CalibrationView top = Calibration.of(samples(70, 1, 3));
		assertThat(top.high()).isEqualTo(70);
		assertThat(top.summary().get(1)).contains("your 70% is inside it");
		CalibrationView bottom = Calibration.of(samples(30, 3, 1));
		assertThat(bottom.low()).isEqualTo(30);
		assertThat(bottom.summary().get(1)).contains("your 30% is inside it");
	}
}
