package com.tradeez.marketdata.massive;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * One quarterly equity-index futures contract — NQ December 2024, say.
 *
 * <p>Massive names contracts the exchange way: root, month code, <b>one</b> year digit
 * ({@code NQZ4}). Equity-index futures list March, June, September and December (H, M, U, Z) and
 * expire on the third Friday of their month.
 *
 * <p>The single year digit repeats every decade — {@code NQZ6} is December 2016 and December 2026 —
 * so a range whose contracts would share a ticker is refused rather than guessed at.
 *
 * @param root  the product, e.g. {@code NQ}
 * @param year  four-digit year
 * @param month 3, 6, 9 or 12
 */
record QuarterlyContract(String root, int year, int month) {

	private static final String MONTH_CODES = "FGHJKMNQUVXZ";

	QuarterlyContract {
		if (month != 3 && month != 6 && month != 9 && month != 12) {
			throw new IllegalArgumentException("Not a quarterly month: " + month);
		}
	}

	/** Massive's ticker: {@code NQZ4}. */
	String ticker() {
		return root + MONTH_CODES.charAt(month - 1) + (year % 10);
	}

	/**
	 * The id stored in {@code market_bar.contract_id}: year and month, {@code 202412} for December
	 * 2024. Readable in the database and stable across imports.
	 */
	long id() {
		return year * 100L + month;
	}

	/** Last trading day: the third Friday of the contract month. */
	LocalDate expiry() {
		return LocalDate.of(year, month, 1)
			.with(TemporalAdjusters.dayOfWeekInMonth(3, DayOfWeek.FRIDAY));
	}

	QuarterlyContract next() {
		return month == 12
			? new QuarterlyContract(root, year + 1, 3)
			: new QuarterlyContract(root, year, month + 3);
	}

	/** The first contract still trading on {@code date}. */
	static QuarterlyContract firstExpiringOnOrAfter(String root, LocalDate date) {
		int quarterMonth = ((date.getMonthValue() + 2) / 3) * 3;
		QuarterlyContract c = new QuarterlyContract(root, date.getYear(), quarterMonth);
		return c.expiry().isBefore(date) ? c.next() : c;
	}

	/**
	 * Every contract that can be the front contract on some day in {@code [from, to]}.
	 *
	 * <p>On any day the front contract by volume is either the nearest one or, in the week or so
	 * before it expires, the next. So the candidates run from the first contract still trading on
	 * {@code from} to the one after the first still trading on {@code to}.
	 */
	static List<QuarterlyContract> covering(String root, LocalDate from, LocalDate to) {
		if (to.isBefore(from)) {
			throw new IllegalArgumentException("'to' is before 'from'");
		}
		QuarterlyContract first = firstExpiringOnOrAfter(root, from);
		QuarterlyContract last = firstExpiringOnOrAfter(root, to).next();
		List<QuarterlyContract> out = new ArrayList<>();
		for (QuarterlyContract c = first; ; c = c.next()) {
			out.add(c);
			if (c.equals(last)) {
				break;
			}
		}
		if (new HashSet<>(out.stream().map(QuarterlyContract::ticker).toList()).size() != out.size()) {
			throw new IllegalArgumentException("A range this long needs two contracts with the same "
				+ "ticker (" + root + " year codes repeat every ten years). Import less than a decade "
				+ "at a time.");
		}
		return out;
	}
}
