package com.tradeez.marketdata;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generated bars, cached one UTC day at a time (BUG-010).
 *
 * <p>The practice slice made this worth doing. Measured in session 015: four days of history is
 * ~170,000 one-second bars and ~130 ms to generate and aggregate — paid again on every session
 * resume, on the same days, for the same seed. A day is the unit because every supported timeframe
 * divides it exactly, so a day's bars never straddle into the next.
 *
 * <h2>Why it cannot leak the future</h2>
 * A cached day holds the <i>whole</i> day, including hours a practice replay has not revealed. The
 * guard is {@link #covers}: the cache only answers ranges whose ends fall on the timeframe's own
 * boundaries. For such a range, every bar starting inside it also ends inside it, so filtering the
 * day by start time returns exactly what direct generation would have — no bar that extends past
 * {@code to}. Any other range goes to the generator, as before. A test compares the two paths bar
 * for bar.
 *
 * <h2>Why it cannot go stale</h2>
 * The generator version is part of the key. Today it is also a compile-time constant, so a new
 * version means a new process and an empty cache anyway — but the key does not rely on that.
 *
 * <p>Bounded by total bars held, not by entries: a day of one-minute bars is five times a day of
 * five-minute bars, and an entry count would let the memory cost vary fivefold. One-second data is
 * never cached — a day of it is 86,400 bars, and its only consumer (resolving a trade) reads each
 * day once.
 */
final class DayBarCache {

	static final long SECONDS_PER_DAY = 86_400;

	/** ~100 bytes a bar, so roughly 20 MB at the limit. */
	static final int DEFAULT_MAX_BARS = 200_000;

	record Key(long seed, String generatorVersion, Timeframe timeframe, long epochDay) {
	}

	private final int maxBars;
	private final LinkedHashMap<Key, List<Bar>> days = new LinkedHashMap<>(64, 0.75f, true);
	private int heldBars;
	private long hits;
	private long misses;

	DayBarCache(int maxBars) {
		this.maxBars = maxBars;
	}

	/** Whether this cache may answer the range — see the class comment for why this is the guard. */
	static boolean covers(Timeframe timeframe, Instant from, Instant to) {
		long step = timeframe.seconds();
		return timeframe != Timeframe.S1
			&& SECONDS_PER_DAY % step == 0
			&& to.isAfter(from)
			&& Math.floorMod(from.getEpochSecond(), step) == 0
			&& Math.floorMod(to.getEpochSecond(), step) == 0;
	}

	/** Bars starting in {@code [from, to)}, built from whole cached days. */
	List<Bar> range(long seed, String generatorVersion, Timeframe timeframe, Instant from,
			Instant to, DayGenerator generator) {
		long firstDay = Math.floorDiv(from.getEpochSecond(), SECONDS_PER_DAY);
		long lastDay = Math.floorDiv(to.getEpochSecond() - 1, SECONDS_PER_DAY);

		List<Bar> out = new ArrayList<>();
		for (long day = firstDay; day <= lastDay; day++) {
			Key key = new Key(seed, generatorVersion, timeframe, day);
			List<Bar> bars = get(key);
			if (bars == null) {
				// Generated outside the lock: two threads missing the same day both do the work, and
				// the second write is identical. Cheaper than making every reader wait.
				Instant dayStart = Instant.ofEpochSecond(day * SECONDS_PER_DAY);
				bars = List.copyOf(generator.day(dayStart, dayStart.plusSeconds(SECONDS_PER_DAY)));
				put(key, bars);
			}
			for (Bar bar : bars) {
				Instant t = bar.openTime();
				if (!t.isBefore(from) && t.isBefore(to)) {
					out.add(bar);
				}
			}
		}
		return out;
	}

	private synchronized List<Bar> get(Key key) {
		List<Bar> bars = days.get(key);
		if (bars == null) {
			misses++;
		} else {
			hits++;
		}
		return bars;
	}

	private synchronized void put(Key key, List<Bar> bars) {
		List<Bar> previous = days.put(key, bars);
		heldBars += bars.size() - (previous == null ? 0 : previous.size());
		// Evict least recently used days until back under the budget. Never the day just added,
		// even if it alone exceeds the budget — the caller is about to use it.
		Iterator<Map.Entry<Key, List<Bar>>> eldest = days.entrySet().iterator();
		while (heldBars > maxBars && eldest.hasNext()) {
			Map.Entry<Key, List<Bar>> entry = eldest.next();
			if (entry.getKey().equals(key)) {
				continue;
			}
			heldBars -= entry.getValue().size();
			eldest.remove();
		}
	}

	synchronized int heldBars() {
		return heldBars;
	}

	synchronized long hits() {
		return hits;
	}

	synchronized long misses() {
		return misses;
	}

	/** Produces one whole day of bars at the cached timeframe. */
	@FunctionalInterface
	interface DayGenerator {
		List<Bar> day(Instant dayStart, Instant dayEnd);
	}
}
