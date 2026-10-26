package com.tradeez.drills;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.random.RandomGenerator;

/**
 * {@value #VERSION} (T26, DEC-070): the arithmetic of risk, against the clock — mode F of the drill engine
 * (DEC-023). Three questions a trader answers before every trade or after a bad run (ICT-83):
 * <ul>
 * <li><b>Position size</b> — an account, a share of it to risk, a stop in ticks on NQ or MNQ: how many
 * contracts? Always rounded down, or the risk is over the limit; sometimes none at all.</li>
 * <li><b>A losing streak</b> — the same share of what is left risked on each trade: how deep is the drawdown
 * after so many losses in a row?</li>
 * <li><b>Recovery</b> — an account down so much: what gain brings it back?</li>
 * </ul>
 * Each is generated from a random draw and answered with its working, step by step. Commission and slippage are
 * left out, and the question says so.
 */
public final class RiskQuestion {

	public static final String VERSION = "risk-1.0.0";

	/** The lesson the risk drills practise: risk rules and drawdown (DEC-081). */
	public static final String CONCEPT = "ICT-83";

	/** Each with its clock: a streak takes a power in the head, so more time. */
	public enum Kind {
		CONTRACTS(30), STREAK(45), RECOVERY(30);

		private final int limitSeconds;

		Kind(int limitSeconds) {
			this.limitSeconds = limitSeconds;
		}

		public int limitSeconds() {
			return limitSeconds;
		}
	}

	/**
	 * @param answer    the right answer, in {@code unit}
	 * @param tolerance how far from it still counts: none for contracts, a little for percentages worked in the
	 *                  head
	 */
	public record Question(Kind kind, Map<String, Object> givens, String text, String unit, double answer,
			double tolerance, int limitSeconds, List<String> working) {
	}

	private static final int[] ACCOUNTS = { 25_000, 50_000, 100_000, 150_000, 250_000 };
	private static final double[] RISKS = { 0.5, 1, 2 };
	private static final double[] STREAK_RISKS = { 0.5, 1, 2, 3, 5 };
	private static final int[] DRAWDOWNS = { 10, 15, 20, 25, 30, 33, 40, 50, 60, 75 };

	/** The most contracts a position-size question asks for: more is not a size anyone sizes by hand. */
	static final int MAX_CONTRACTS = 30;

	private RiskQuestion() {
	}

	public static Question of(Kind kind, RandomGenerator random) {
		return switch (kind) {
			case CONTRACTS -> contracts(random);
			case STREAK -> streak(random);
			case RECOVERY -> recovery(random);
		};
	}

	static Question contracts(RandomGenerator random) {
		while (true) {
			int account = ACCOUNTS[random.nextInt(ACCOUNTS.length)];
			double risk = RISKS[random.nextInt(RISKS.length)];
			boolean micro = random.nextBoolean();
			int stop = 8 + random.nextInt(113);
			Question q = contracts(account, risk, micro, stop);
			if (q.answer() <= MAX_CONTRACTS) {
				return q;
			}
		}
	}

	/** @param micro MNQ, a tenth of NQ: $0.50 a tick rather than $5.00 */
	static Question contracts(int account, double risk, boolean micro, int stopTicks) {
		long riskCents = Math.round(account * risk);
		long tickCents = micro ? 50 : 500;
		long perContract = stopTicks * tickCents;
		long contracts = riskCents / perContract;
		String product = micro ? "MNQ" : "NQ";
		Map<String, Object> givens = new LinkedHashMap<>();
		givens.put("account", account);
		givens.put("riskPercent", risk);
		givens.put("product", product);
		givens.put("stopTicks", stopTicks);
		List<String> working = new ArrayList<>();
		working.add(percent(risk) + " of " + money(account * 100L) + " is " + money(riskCents) + ".");
		working.add(stopTicks + " ticks × " + money(tickCents) + " a tick = " + money(perContract) + " a contract.");
		working.add(money(riskCents) + " ÷ " + money(perContract) + " = " + String.format(Locale.ROOT, "%.2f", (double) riskCents
			/ perContract) + ": " + contracts + (contracts == 1 ? " contract" : " contracts") + " — always rounded down, or the "
			+ "risk is over the limit.");
		if (contracts == 0) {
			working.add("None: one " + product + " contract would risk more than the limit."
				+ (micro ? "" : " MNQ, a tenth of the size, may fit."));
		}
		return new Question(Kind.CONTRACTS, givens, "An account of " + money(account * 100L) + ", risking " + percent(risk)
			+ " of it on this trade. The stop is " + stopTicks + " ticks away, on " + product + " (" + money(tickCents)
			+ " a tick). How many contracts? Leave commission and slippage out.", "contracts", contracts, 0, Kind.CONTRACTS.limitSeconds(), working);
	}

	static Question streak(RandomGenerator random) {
		return streak(STREAK_RISKS[random.nextInt(STREAK_RISKS.length)], 3 + random.nextInt(10));
	}

	static Question streak(double risk, int losses) {
		double left = Math.pow(1 - risk / 100, losses);
		double down = 100 * (1 - left);
		Map<String, Object> givens = new LinkedHashMap<>();
		givens.put("riskPercent", risk);
		givens.put("losses", losses);
		List<String> working = List.of(
			"Each loss leaves " + decimal(1 - risk / 100) + " of what was there.",
			decimal(1 - risk / 100) + " to the power of " + losses + " = " + String.format(Locale.ROOT, "%.4f", left) + ": "
				+ String.format(Locale.ROOT, "%.2f%%", 100 * left) + " is left.",
			"Down " + String.format(Locale.ROOT, "%.2f%%", down) + ". Risking the same dollars each time it would be "
				+ String.format(Locale.ROOT, "%.2f%%", Math.min(100, risk * losses))
				+ ": a risk that shrinks with the account keeps the hole shallower.");
		return new Question(Kind.STREAK, givens, "Risking " + percent(risk) + " of what the account holds on every trade, "
			+ "how far down is it after " + losses + " losses in a row? Answer in percent.", "%", round2(down), 0.5, Kind.STREAK.limitSeconds(), working);
	}

	static Question recovery(RandomGenerator random) {
		return recovery(DRAWDOWNS[random.nextInt(DRAWDOWNS.length)]);
	}

	static Question recovery(int drawdown) {
		double gain = 100.0 * drawdown / (100 - drawdown);
		Map<String, Object> givens = new LinkedHashMap<>();
		givens.put("drawdownPercent", drawdown);
		List<String> working = List.of(
			"Down " + drawdown + "%: " + (100 - drawdown) + "% is left.",
			"Back to 100 from " + (100 - drawdown) + ": 100 ÷ " + (100 - drawdown) + " = "
				+ String.format(Locale.ROOT, "%.4f", 100.0 / (100 - drawdown)) + " — a gain of "
				+ String.format(Locale.ROOT, "%.2f%%", gain) + ".",
			"The deeper the hole, the faster this grows: 50% down needs 100% up.");
		return new Question(Kind.RECOVERY, givens, "An account is down " + drawdown + "% from its peak. What gain brings it "
			+ "back? Answer in percent.", "%", round2(gain), 1, Kind.RECOVERY.limitSeconds(), working);
	}

	/** Right when within the tolerance of the answer; the last digit of a decimal does not decide it. */
	public static boolean within(double answer, double tolerance, double given) {
		return Math.abs(given - answer) <= tolerance + 1e-9;
	}

	private static String percent(double value) {
		return (value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value)) + "%";
	}

	private static String decimal(double value) {
		return String.format(Locale.ROOT, "%.3f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
	}

	/** "$1,234.50", whole dollars without cents. */
	private static String money(long cents) {
		return cents % 100 == 0 ? String.format(Locale.ROOT, "$%,d", cents / 100)
			: String.format(Locale.ROOT, "$%,d.%02d", cents / 100, cents % 100);
	}

	private static double round2(double value) {
		return Math.round(value * 100.0) / 100.0;
	}
}
