package com.tradeez.practice;

import java.util.ArrayList;
import java.util.List;

import com.tradeez.practice.FillEngine.Outcome;
import com.tradeez.practice.PracticeDtos.CalibrationBucketView;
import com.tradeez.practice.PracticeDtos.CalibrationView;

/**
 * {@value #VERSION} (T23, DEC-067): how sure a learner said they were that a trade would reach its target
 * before its stop, against how often it did.
 *
 * <p>Only a trade the bracket decided counts: one closed by hand, one the session's close ended, and one
 * whose stop and target fell inside the same candle answer a different question, or none — they are counted
 * apart and named. A handful of trades proves little, so the hit rate comes with its 95% Wilson interval,
 * and the learner's average confidence is read against that range rather than against one number.
 */
public final class Calibration {

	public static final String VERSION = "calibration-1.0.0";
	public static final String QUESTION = "How sure were you that the trade would reach its target before its stop?";

	/** 95%. */
	private static final double Z = 1.96;

	/** A finished trade with a stated confidence, and how it ended. */
	public record Sample(int confidence, Outcome outcome) {
	}

	private Calibration() {
	}

	public static CalibrationView of(List<Sample> samples) {
		int[] trades = new int[10];
		int[] hits = new int[10];
		int decided = 0;
		int hit = 0;
		long confidenceSum = 0;
		int closedByHand = 0;
		int timeExits = 0;
		int ambiguous = 0;
		for (Sample s : samples) {
			switch (s.outcome()) {
				case TARGET, STOP -> {
					int bucket = s.confidence() / 10;
					int reached = s.outcome() == Outcome.TARGET ? 1 : 0;
					trades[bucket]++;
					hits[bucket] += reached;
					decided++;
					hit += reached;
					confidenceSum += s.confidence();
				}
				case CLOSED -> closedByHand++;
				case TIME_EXIT -> timeExits++;
				case AMBIGUOUS -> ambiguous++;
			}
		}
		List<CalibrationBucketView> buckets = new ArrayList<>();
		for (int b = 1; b < 10; b++) {
			if (trades[b] > 0) {
				buckets.add(new CalibrationBucketView(b * 10, trades[b], hits[b]));
			}
		}

		List<String> summary = new ArrayList<>();
		Double mean = null;
		Integer low = null;
		Integer high = null;
		if (decided == 0) {
			summary.add(samples.isEmpty()
				? "No finished trade says how sure you were yet. Choose a confidence when you place a trade, and this "
					+ "compares it with how often your trades reach their target."
				: "None of your trades with a confidence has reached its target or its stop yet.");
		} else {
			mean = Math.round(10.0 * confidenceSum / decided) / 10.0;
			double[] interval = wilson(hit, decided);
			low = (int) Math.round(100 * interval[0]);
			high = (int) Math.round(100 * interval[1]);
			long said = Math.round(mean);
			summary.add("On " + trades(decided) + " the bracket decided, you said " + said + "% on average; " + hit
				+ " reached the target first (" + Math.round(100.0 * hit / decided) + "%).");
			String reading = said > high ? "above it: so far you have been more sure than your trades."
				: said < low ? "below it: so far you have been less sure than your trades."
				: "inside it: so far your confidence matches your results as closely as " + trades(decided) + " can show.";
			summary.add("A hit rate from " + low + "% to " + high + "% fits these results (95%), and your " + said + "% is "
				+ reading);
		}
		int apart = closedByHand + timeExits + ambiguous;
		if (apart > 0) {
			List<String> why = new ArrayList<>();
			if (closedByHand > 0) {
				why.add(closedByHand + " closed by hand");
			}
			if (timeExits > 0) {
				why.add(timeExits + " ended by the session's close");
			}
			if (ambiguous > 0) {
				why.add(ambiguous + " with both levels inside one candle");
			}
			summary.add("Not counted: " + String.join(", ", why) + ". The bracket did not decide "
				+ (apart == 1 ? "it." : "them."));
		}
		return new CalibrationView(VERSION, QUESTION, samples.size(), decided, hit, mean, low, high, closedByHand,
			timeExits, ambiguous, buckets, summary);
	}

	/** The 95% Wilson score interval for {@code hits} of {@code n}, as proportions. */
	static double[] wilson(int hits, int n) {
		double p = (double) hits / n;
		double z2 = Z * Z;
		double denominator = 1 + z2 / n;
		double centre = (p + z2 / (2 * n)) / denominator;
		double half = Z * Math.sqrt(p * (1 - p) / n + z2 / (4.0 * n * n)) / denominator;
		return new double[] { Math.max(0, centre - half), Math.min(1, centre + half) };
	}

	private static String trades(int n) {
		return n + (n == 1 ? " trade" : " trades");
	}
}
