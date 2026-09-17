package com.tradeez.marketdata;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.springframework.stereotype.Component;

/**
 * Deterministic generator for the {@code DEMO-*} instruments (DEC-033, DATA_STRATEGY.md).
 *
 * <p><b>This is not market data and must never be presented as such.</b> It exists so the replay
 * engine, fill engine, chart and drawing layer can be built and tested at zero cost and with zero
 * licensing exposure. It produces price action with the right <i>shape</i> — trends, ranges,
 * volatility regimes, session gaps — but none of the <i>reasons</i> real price action has that
 * shape. DEC-034 is the consequence: detectors may run on this, learners may not be graded on it.
 *
 * <h2>Determinism</h2>
 * The same seed and time range always produce byte-identical output, on any machine and in any
 * order. That property is what DEC-012 rests on: a practice result recorded months ago can be
 * re-derived from its stored seed and generator version.
 *
 * <p>Two things make it hold. Each one-second step is seeded from the <b>absolute timestamp</b>
 * rather than from a running generator state, so asking for 14:00–15:00 gives the same bars whether
 * or not you asked for 13:00–14:00 first. And {@link Random} is used rather than
 * {@link java.util.concurrent.ThreadLocalRandom} or {@code Math.random()} because its algorithm is
 * specified by the JDK and therefore stable across versions and platforms.
 *
 * <p>The trade-off is that price is a deterministic function of time, so there is no cumulative
 * random walk. Instead the path is built from layered sine waves whose phases and amplitudes come
 * from the seed, plus a per-second jitter. This gives convincing structure — swings, pullbacks,
 * consolidations — while remaining stateless and therefore genuinely reproducible.
 */
@Component
public class DemoSeriesGenerator {

	/**
	 * Bump this whenever generated output changes in any way. Stored on every practice result
	 * (DEC-012). Failing to bump it is worse than a wrong value: it silently makes old results
	 * irreproducible while claiming they are not.
	 */
	public static final String GENERATOR_VERSION = "demo-gen-1.1.0";

	/** Where DEMO-NQ starts, in ticks. 80000 ticks x 0.25 = 20000.00 index points. */
	private static final long BASE_PRICE_TICKS = 80_000L;

	private static final long SECONDS_PER_DAY = 86_400L;

	/**
	 * Generates one-second bars covering {@code [from, to)}.
	 *
	 * <p>One second is the base resolution because DEC-008 requires the fill engine to walk finer
	 * data than the user is watching. Everything coarser is aggregated from this by
	 * {@link BarAggregator}, so a 5m candle and the 1s bars inside it can never disagree.
	 */
	public List<Bar> generateSeconds(long seed, Instant from, Instant to) {
		if (!to.isAfter(from)) {
			return List.of();
		}

		long startSecond = from.getEpochSecond();
		long endSecond = to.getEpochSecond();

		List<Bar> bars = new ArrayList<>((int) Math.min(endSecond - startSecond, 500_000));

		for (long second = startSecond; second < endSecond; second++) {
			if (!SessionCalendar.isOpen(second)) {
				continue; // Weekends and the daily maintenance break produce no bars at all.
			}
			bars.add(secondBar(seed, second));
		}
		return bars;
	}

	/**
	 * One second of price action, derived purely from {@code (seed, second)}.
	 *
	 * <p>The open is deliberately the previous second's close where the market was continuously
	 * open, so the series has no artificial one-tick gaps. Across a session boundary the open jumps,
	 * which is intentional — real sessions gap, and the fill engine has to cope with a stop being
	 * jumped rather than touched.
	 */
	private Bar secondBar(long seed, long second) {
		long close = priceAt(seed, second);
		long previousSecond = second - 1;
		long open = SessionCalendar.isOpen(previousSecond)
			? priceAt(seed, previousSecond)
			: close + sessionGap(seed, second);

		Random rng = rngFor(seed, second);
		double volatility = regimeVolatility(seed, second);

		// Wicks extend beyond the body by an amount scaled to the regime. Without this every bar
		// would be a pure body, which is not what price action looks like and would make wick-based
		// analysis meaningless.
		long wickUp = (long) Math.round(rng.nextDouble() * volatility * 2);
		long wickDown = (long) Math.round(rng.nextDouble() * volatility * 2);

		long high = Math.max(open, close) + wickUp;
		long low = Math.min(open, close) - wickDown;

		// Volume correlates with range, which is at least directionally true of real markets. It is
		// still invented: nothing may be concluded from it (see Bar#volume).
		long volume = 1 + Math.round((high - low) * (1 + rng.nextDouble() * 3));

		return new Bar(Instant.ofEpochSecond(second), open, high, low, close, volume);
	}

	/**
	 * Price at an absolute second, in ticks.
	 *
	 * <p>Layered sine waves at different periods produce structure at different scales: a slow one
	 * for the day's trend, a medium one for intraday swings, a fast one for the noise a scalper
	 * sees. Phases derive from the seed so different seeds give genuinely different markets rather
	 * than the same shape shifted along.
	 */
	private long priceAt(long seed, long second) {
		Random phaseRng = new Random(seed * 31);
		double slowPhase = phaseRng.nextDouble() * Math.PI * 2;
		double mediumPhase = phaseRng.nextDouble() * Math.PI * 2;
		double fastPhase = phaseRng.nextDouble() * Math.PI * 2;
		double driftTicksPerDay = (phaseRng.nextDouble() - 0.5) * 800;

		double days = second / (double) SECONDS_PER_DAY;

		double slow = Math.sin(days * Math.PI * 2 / 3 + slowPhase) * 600;
		double medium = Math.sin(days * Math.PI * 2 * 4 + mediumPhase) * 180;
		double fast = Math.sin(days * Math.PI * 2 * 40 + fastPhase) * 45;
		double drift = days * driftTicksPerDay;

		double jitter = (rngFor(seed, second).nextDouble() - 0.5)
			* regimeVolatility(seed, second) * 4;

		// Math.round, not a cast: casting truncates toward zero, which would bias every negative
		// movement by up to a full tick and skew long/short results asymmetrically.
		return BASE_PRICE_TICKS + Math.round(slow + medium + fast + drift + jitter);
	}

	/**
	 * Volatility for the regime this second falls in, in ticks.
	 *
	 * <p>Regimes change every few hours so a generated day contains quiet consolidation and violent
	 * expansion, not one uniform texture. DATA_STRATEGY.md requires trend, range, high-volatility
	 * and low-volatility conditions to exist in the generated data.
	 */
	private double regimeVolatility(long seed, long second) {
		long regimeBlock = Math.floorDiv(second, 3 * 3600L);

		// mix64 rather than new Random(seed + block).nextInt(4). Consecutive LCG seeds produce
		// correlated first outputs, and regime blocks are consecutive by construction — the first
		// version of this code selected regimes that barely varied, and a test caught it. A proper
		// avalanche function has no such dependence on how its input was constructed.
		int regime = (int) Math.floorMod(mix64(seed * 7919L + regimeBlock), 4L);

		return switch (regime) {
			case 0 -> 1.5;   // quiet range
			case 1 -> 4.0;   // normal
			case 2 -> 10.0;  // active
			default -> 22.0; // expansion
		};
	}

	/**
	 * splitmix64 finalizer. Every input bit affects every output bit, so structurally related
	 * inputs (consecutive block numbers, adjacent timestamps) give unrelated outputs.
	 */
	private static long mix64(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	/** The jump across a session boundary, in ticks. Real markets gap; the engine must handle it. */
	private long sessionGap(long seed, long second) {
		Random rng = new Random(seed * 104_729 + Math.floorDiv(second, SECONDS_PER_DAY));
		return Math.round((rng.nextDouble() - 0.5) * 120);
	}

	/**
	 * A generator seeded from {@code (seed, second)}.
	 *
	 * <p>The multiplication by a large prime matters: seeding with {@code seed + second} would make
	 * adjacent seeds produce overlapping sequences, so two different practice sessions could show
	 * recognisably similar price action.
	 */
	private Random rngFor(long seed, long second) {
		return new Random(mix64(seed * 6_364_136_223_846_793_005L + second));
	}
}
