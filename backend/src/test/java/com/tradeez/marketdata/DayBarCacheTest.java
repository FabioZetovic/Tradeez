package com.tradeez.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * BUG-010 — the day cache.
 *
 * <p>The property that matters is the first one: for every range the cache agrees to answer, its
 * answer is exactly what generation would have produced. If that holds, the cache is invisible —
 * including to the no-lookahead guarantee, which is the part a careless cache would break.
 */
class DayBarCacheTest {

	private static final DemoSeriesGenerator GENERATOR = new DemoSeriesGenerator();
	private static final String VERSION = DemoSeriesGenerator.GENERATOR_VERSION;
	private static final long DAY = DayBarCache.SECONDS_PER_DAY;
	/** 2024-03-11 00:00 UTC, a Monday. */
	private static final long MONDAY = 1_710_115_200L;

	private static List<Bar> direct(long seed, Timeframe tf, Instant from, Instant to) {
		return BarAggregator.aggregate(GENERATOR.generateSeconds(seed, from, to), tf);
	}

	private static DayBarCache.DayGenerator generatorFor(long seed, Timeframe tf, AtomicInteger calls) {
		return (start, end) -> {
			calls.incrementAndGet();
			return direct(seed, tf, start, end);
		};
	}

	@Test
	@DisplayName("answers every range it accepts exactly as generation would, bar for bar")
	void identicalToGeneration() {
		DayBarCache cache = new DayBarCache(DayBarCache.DEFAULT_MAX_BARS);
		Random random = new Random(15);
		Timeframe[] timeframes = { Timeframe.M1, Timeframe.M5, Timeframe.M15, Timeframe.H1,
			Timeframe.H4, Timeframe.D1 };

		for (int i = 0; i < 24; i++) {
			Timeframe tf = timeframes[i % timeframes.length];
			long seed = random.nextLong();
			long step = tf.seconds();
			// Anywhere in a two-week window, spanning up to three days: crosses midnights, the daily
			// break and a weekend.
			long fromSecond = MONDAY + random.nextLong(14 * DAY);
			fromSecond -= Math.floorMod(fromSecond, step);
			long span = step * (1 + random.nextLong(3 * DAY / step));
			Instant from = Instant.ofEpochSecond(fromSecond);
			Instant to = Instant.ofEpochSecond(fromSecond + span);

			assertThat(DayBarCache.covers(tf, from, to)).isTrue();
			List<Bar> cached = cache.range(seed, VERSION, tf, from, to,
				generatorFor(seed, tf, new AtomicInteger()));
			assertThat(cached).as("%s %s → %s", tf, from, to).isEqualTo(direct(seed, tf, from, to));
		}
	}

	@Test
	@DisplayName("never returns a bar that starts at or runs past the end of the range (DEC-009)")
	void noLookahead() {
		DayBarCache cache = new DayBarCache(DayBarCache.DEFAULT_MAX_BARS);
		Timeframe tf = Timeframe.M5;
		// A replay cursor mid-morning. The cached day holds the whole day; none of the rest may leak.
		Instant from = Instant.ofEpochSecond(MONDAY + DAY);
		Instant cursor = Instant.ofEpochSecond(MONDAY + DAY + 15 * 3600 + 35 * 60);

		List<Bar> visible = cache.range(9, VERSION, tf, from, cursor,
			generatorFor(9, tf, new AtomicInteger()));

		assertThat(visible).isNotEmpty();
		assertThat(visible).allSatisfy(bar ->
			assertThat(bar.openTime().plusSeconds(tf.seconds())).isBeforeOrEqualTo(cursor));
		assertThat(visible.getLast().openTime()).isEqualTo(cursor.minusSeconds(tf.seconds()));
	}

	@Test
	@DisplayName("refuses ranges it could not answer exactly, so they go to the generator")
	void coversOnlyAlignedRanges() {
		Instant aligned = Instant.ofEpochSecond(MONDAY + 3600);
		assertThat(DayBarCache.covers(Timeframe.M5, aligned, aligned.plusSeconds(600))).isTrue();

		// An end mid-bar: the whole cached bar would include seconds past the end.
		assertThat(DayBarCache.covers(Timeframe.M5, aligned, aligned.plusSeconds(601))).isFalse();
		// A start mid-bar: the cached bar would include seconds before it.
		assertThat(DayBarCache.covers(Timeframe.M5, aligned.plusSeconds(1), aligned.plusSeconds(900)))
			.isFalse();
		// One-second data is never cached; empty ranges have nothing to cache.
		assertThat(DayBarCache.covers(Timeframe.S1, aligned, aligned.plusSeconds(60))).isFalse();
		assertThat(DayBarCache.covers(Timeframe.M5, aligned, aligned)).isFalse();
	}

	@Test
	@DisplayName("generates each day once, then serves it from memory")
	void reusesDays() {
		DayBarCache cache = new DayBarCache(DayBarCache.DEFAULT_MAX_BARS);
		AtomicInteger calls = new AtomicInteger();
		Instant from = Instant.ofEpochSecond(MONDAY);
		Instant to = Instant.ofEpochSecond(MONDAY + 2 * DAY);

		cache.range(1, VERSION, Timeframe.M5, from, to, generatorFor(1, Timeframe.M5, calls));
		assertThat(calls).hasValue(2);

		// A resume of the same session, and a narrower read inside it: no new generation.
		cache.range(1, VERSION, Timeframe.M5, from, to, generatorFor(1, Timeframe.M5, calls));
		cache.range(1, VERSION, Timeframe.M5, from.plusSeconds(3600), from.plusSeconds(7200),
			generatorFor(1, Timeframe.M5, calls));
		assertThat(calls).hasValue(2);
		assertThat(cache.hits()).isEqualTo(3);
	}

	@Test
	@DisplayName("keeps seeds, timeframes and generator versions apart")
	void keysDoNotCollide() {
		DayBarCache cache = new DayBarCache(DayBarCache.DEFAULT_MAX_BARS);
		Instant from = Instant.ofEpochSecond(MONDAY + DAY);
		Instant to = from.plusSeconds(DAY);

		List<Bar> a = cache.range(1, VERSION, Timeframe.M5, from, to,
			generatorFor(1, Timeframe.M5, new AtomicInteger()));
		List<Bar> b = cache.range(2, VERSION, Timeframe.M5, from, to,
			generatorFor(2, Timeframe.M5, new AtomicInteger()));
		List<Bar> c = cache.range(1, VERSION, Timeframe.M15, from, to,
			generatorFor(1, Timeframe.M15, new AtomicInteger()));
		AtomicInteger bumped = new AtomicInteger();
		cache.range(1, "demo-gen-next", Timeframe.M5, from, to, generatorFor(1, Timeframe.M5, bumped));

		assertThat(a).isNotEqualTo(b);
		assertThat(c).hasSize(a.size() / 3);
		assertThat(bumped).as("a new generator version must regenerate").hasValue(1);
	}

	@Test
	@DisplayName("stays within its budget by evicting the least recently used days")
	void bounded() {
		int oneDayOfFiveMinute = direct(3, Timeframe.M5, Instant.ofEpochSecond(MONDAY + DAY),
			Instant.ofEpochSecond(MONDAY + 2 * DAY)).size();
		DayBarCache cache = new DayBarCache(oneDayOfFiveMinute * 2);

		for (int day = 1; day <= 4; day++) {
			Instant from = Instant.ofEpochSecond(MONDAY + day * DAY);
			cache.range(3, VERSION, Timeframe.M5, from, from.plusSeconds(DAY),
				generatorFor(3, Timeframe.M5, new AtomicInteger()));
			assertThat(cache.heldBars()).isLessThanOrEqualTo(oneDayOfFiveMinute * 2);
		}

		// The oldest day was evicted and has to be generated again.
		AtomicInteger calls = new AtomicInteger();
		Instant first = Instant.ofEpochSecond(MONDAY + DAY);
		cache.range(3, VERSION, Timeframe.M5, first, first.plusSeconds(DAY),
			generatorFor(3, Timeframe.M5, calls));
		assertThat(calls).hasValue(1);
	}

	@Test
	@DisplayName("through the provider: cached and uncached ranges both match generation")
	void providerUsesItTransparently() {
		DayBarCache cache = new DayBarCache(DayBarCache.DEFAULT_MAX_BARS);
		GeneratedMarketDataProvider provider = new GeneratedMarketDataProvider(GENERATOR, cache);
		Instrument nq = mock(Instrument.class);
		when(nq.getSymbol()).thenReturn("DEMO-NQ");
		Instant from = Instant.ofEpochSecond(MONDAY + DAY + 3600);

		// Aligned: served by the cache.
		Instant alignedTo = from.plusSeconds(6 * 3600);
		assertThat(provider.bars(nq, Timeframe.M5, from, alignedTo, 5).bars())
			.isEqualTo(direct(5, Timeframe.M5, from, alignedTo));
		assertThat(cache.misses()).isPositive();

		// Unaligned end: bypasses the cache, and the partial last bar is still correct.
		long before = cache.misses() + cache.hits();
		Instant ragged = alignedTo.plusSeconds(17);
		assertThat(provider.bars(nq, Timeframe.M5, from, ragged, 5).bars())
			.isEqualTo(direct(5, Timeframe.M5, from, ragged));
		assertThat(cache.misses() + cache.hits()).isEqualTo(before);
	}
}
