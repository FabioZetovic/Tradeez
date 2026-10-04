package com.tradeez.marketdata.massive;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import com.tradeez.marketdata.massive.MassiveClient.SessionBar;

/**
 * Which contract the continuous NQ series follows on each trading day (DEC-042).
 *
 * <p>Massive serves each quarterly contract separately; Tradeez builds the continuous series the
 * way Databento's {@code NQ.v.0} does: <b>the front contract on a day is the one that traded the
 * most in the previous session</b>. Using the previous session, not the same day, means the choice
 * was knowable when the day opened — no lookahead. Traders move to the next contract about a week
 * before expiry, and the series moves with them, a day after the volume does.
 *
 * <p>Raw prices are kept (DEC-024): the jump between contracts on the switch day is real, and the
 * contract id stored with every bar shows where it happens.
 */
final class RollSchedule {

	private RollSchedule() {
	}

	/**
	 * @param sessions each candidate contract's session bars
	 * @return the front contract for every trade date that has a previous session in the data. The
	 *         earliest date has none, which is why the importer fetches sessions from before the
	 *         range it imports.
	 */
	static NavigableMap<LocalDate, QuarterlyContract> frontByVolume(
			Map<QuarterlyContract, List<SessionBar>> sessions) {
		TreeMap<LocalDate, Map<QuarterlyContract, Long>> volumeByDate = new TreeMap<>();
		sessions.forEach((contract, bars) -> bars.forEach(bar -> volumeByDate
			.computeIfAbsent(bar.tradeDate(), d -> new HashMap<>())
			.merge(contract, bar.volume(), Long::sum)));

		TreeMap<LocalDate, QuarterlyContract> front = new TreeMap<>();
		Map<QuarterlyContract, Long> previous = null;
		for (Map.Entry<LocalDate, Map<QuarterlyContract, Long>> day : volumeByDate.entrySet()) {
			if (previous != null) {
				Map<QuarterlyContract, Long> prior = previous;
				// Among the contracts trading today: most volume yesterday; on a tie, the nearer expiry.
				QuarterlyContract best = day.getValue().keySet().stream()
					.max(Comparator.comparingLong((QuarterlyContract c) -> prior.getOrDefault(c, 0L))
						.thenComparing(QuarterlyContract::expiry, Comparator.reverseOrder()))
					.orElseThrow();
				front.put(day.getKey(), best);
			}
			previous = day.getValue();
		}
		return front;
	}
}
