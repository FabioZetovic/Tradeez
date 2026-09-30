package com.tradeez.marketdata.databento;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.DataClass;
import com.tradeez.marketdata.Instrument;
import com.tradeez.marketdata.MarketBarStore;
import com.tradeez.marketdata.MarketBarStore.StoredBar;

/**
 * Downloads real bars from Databento into {@code market_bar} (DEC-040).
 *
 * <h2>Money</h2>
 * Fabio's constraint is that nothing is spent without his say-so. So every chunk is priced with
 * Databento's own cost endpoint <i>before</i> it is fetched, and refused — recorded, not silently
 * skipped — if it would exceed the per-request cap or take the running total past the overall cap.
 * A dry run prices everything and downloads nothing, which is how any large import starts.
 *
 * <h2>Correctness</h2>
 * Prices arrive as fixed-point integers (units of 1e-9) and are converted to ticks exactly. A price
 * that is not a whole number of ticks is not rounded: CME prices are always on the grid, so an
 * off-grid value means something is wrong, and the chunk fails (INVARIANT 3: reject, never store
 * an impossible price).
 *
 * <p>Imports are chunked — a month of one-minute bars, a day of one-second bars — and each finished
 * chunk is recorded, so an interrupted run resumes where it stopped and a repeated run downloads
 * nothing it already has.
 */
@Service
public class DatabentoImporter {

	private static final Logger log = LoggerFactory.getLogger(DatabentoImporter.class);

	static final String SOURCE = "databento";

	/** Tradeez instrument → Databento continuous symbol (volume roll, raw prices). */
	static final Map<String, String> SYMBOLS = Map.of("NQ", "NQ.v.0");

	static final Map<String, String> SCHEMAS = Map.of("1m", "ohlcv-1m", "1s", "ohlcv-1s");

	private static final int BATCH = 10_000;

	private final DatabentoClient client;
	private final DatabentoSettings settings;
	private final ImportLedger ledger;
	private final MarketBarStore store;

	public DatabentoImporter(DatabentoClient client, DatabentoSettings settings, ImportLedger ledger,
			MarketBarStore store) {
		this.client = client;
		this.settings = settings;
		this.ledger = ledger;
		this.store = store;
	}

	public enum Outcome { ALREADY_IMPORTED, ESTIMATED, IMPORTED }

	public record ChunkReport(Instant from, Instant to, Outcome outcome, BigDecimal costUsd,
			long barsStored) {
	}

	public record Report(boolean dryRun, List<ChunkReport> chunks, BigDecimal costUsd,
			long barsStored) {
	}

	/**
	 * Imports {@code [from, to)} at {@code resolution} ("1m" or "1s"), or with {@code dryRun}
	 * only prices it.
	 *
	 * @throws ImportRefusedException when a chunk would break a spending cap; nothing further is
	 *                                fetched once that happens
	 */
	public Report importRange(Instrument instrument, String resolution, Instant from, Instant to,
			boolean dryRun) {
		if (instrument.getDataClass() == DataClass.SIMULATED) {
			throw new IllegalArgumentException(instrument.getSymbol() + " is simulated; there is "
				+ "nothing to import.");
		}
		String symbol = SYMBOLS.get(instrument.getSymbol());
		if (symbol == null) {
			throw new IllegalArgumentException("No Databento symbol for " + instrument.getSymbol());
		}
		String schema = SCHEMAS.get(resolution);
		if (schema == null) {
			throw new IllegalArgumentException("Resolution must be one of " + SCHEMAS.keySet());
		}
		if (!from.isBefore(to)) {
			throw new IllegalArgumentException("'from' must be before 'to'");
		}

		long tickNanos = instrument.getTickSize().movePointRight(9).longValueExact();
		List<ChunkReport> reports = new ArrayList<>();
		BigDecimal total = BigDecimal.ZERO;
		long stored = 0;

		for (Instant[] chunk : chunks(resolution, from, to)) {
			ImportLedger.Request request = new ImportLedger.Request(instrument.getId(), SOURCE,
				settings.dataset(), symbol, resolution, chunk[0], chunk[1]);

			if (ledger.covered(instrument.getId(), resolution, chunk[0], chunk[1])) {
				reports.add(new ChunkReport(chunk[0], chunk[1], Outcome.ALREADY_IMPORTED,
					BigDecimal.ZERO, 0));
				continue;
			}

			BigDecimal cost = client.cost(symbol, schema, chunk[0], chunk[1]);
			total = total.add(cost);
			if (dryRun) {
				reports.add(new ChunkReport(chunk[0], chunk[1], Outcome.ESTIMATED, cost, 0));
				continue;
			}

			refuseIfOverBudget(request, cost);
			long bars = download(request, symbol, schema, tickNanos, cost);
			stored += bars;
			reports.add(new ChunkReport(chunk[0], chunk[1], Outcome.IMPORTED, cost, bars));
			log.info("Imported {} {} bars for {} {} → {} (${})", bars, resolution,
				instrument.getSymbol(), chunk[0], chunk[1], cost);
		}
		return new Report(dryRun, reports, total, stored);
	}

	private void refuseIfOverBudget(ImportLedger.Request request, BigDecimal cost) {
		if (cost.compareTo(settings.maxCostPerRequestUsd()) > 0) {
			String reason = "Estimated $" + cost + " for " + request.from() + " → " + request.to()
				+ " exceeds the per-request cap of $" + settings.maxCostPerRequestUsd() + ".";
			ledger.refused(request, cost, reason);
			throw new ImportRefusedException(reason);
		}
		BigDecimal spent = ledger.spentUsd();
		if (spent.add(cost).compareTo(settings.maxTotalCostUsd()) > 0) {
			String reason = "Estimated $" + cost + " would take total spending to $" + spent.add(cost)
				+ ", over the cap of $" + settings.maxTotalCostUsd() + ".";
			ledger.refused(request, cost, reason);
			throw new ImportRefusedException(reason);
		}
	}

	private long download(ImportLedger.Request request, String symbol, String schema,
			long tickNanos, BigDecimal cost) {
		UUID id = ledger.start(request, cost);
		List<StoredBar> buffer = new ArrayList<>(BATCH);
		long[] stored = { 0 };
		try {
			client.bars(symbol, schema, request.from(), request.to(), raw -> {
				buffer.add(new StoredBar(toBar(raw, tickNanos), raw.instrumentId()));
				if (buffer.size() >= BATCH) {
					stored[0] += store.insert(request.instrumentId(), request.resolution(), buffer);
					buffer.clear();
				}
			});
			stored[0] += store.insert(request.instrumentId(), request.resolution(), buffer);
			ledger.done(id, stored[0]);
			return stored[0];
		} catch (RuntimeException e) {
			ledger.failed(id, stored[0], e.getMessage());
			throw e;
		}
	}

	static Bar toBar(DatabentoClient.RawBar raw, long tickNanos) {
		Instant openTime = Instant.ofEpochSecond(0, raw.tsEventNanos());
		return new Bar(openTime,
			toTicks(raw.open(), tickNanos, openTime),
			toTicks(raw.high(), tickNanos, openTime),
			toTicks(raw.low(), tickNanos, openTime),
			toTicks(raw.close(), tickNanos, openTime),
			raw.volume());
	}

	static long toTicks(long fixedPrice, long tickNanos, Instant at) {
		if (fixedPrice % tickNanos != 0) {
			throw new OffGridPriceException("Price " + fixedPrice + "e-9 at " + at
				+ " is not a whole number of ticks; refusing to round a market price.");
		}
		return fixedPrice / tickNanos;
	}

	/**
	 * Fixed chunks, so a finished one is recognised on the next run: calendar months (UTC) for
	 * one-minute bars, days for one-second bars. The first and last are trimmed to the range.
	 */
	static List<Instant[]> chunks(String resolution, Instant from, Instant to) {
		List<Instant[]> chunks = new ArrayList<>();
		Instant cursor = from;
		while (cursor.isBefore(to)) {
			LocalDate day = cursor.atZone(ZoneOffset.UTC).toLocalDate();
			Instant next = "1m".equals(resolution)
				? day.withDayOfMonth(1).plusMonths(1).atStartOfDay(ZoneOffset.UTC).toInstant()
				: cursor.truncatedTo(ChronoUnit.DAYS).plus(1, ChronoUnit.DAYS);
			Instant end = next.isBefore(to) ? next : to;
			chunks.add(new Instant[] { cursor, end });
			cursor = end;
		}
		return chunks;
	}

	public static class ImportRefusedException extends RuntimeException {
		public ImportRefusedException(String message) {
			super(message);
		}
	}

	public static class OffGridPriceException extends RuntimeException {
		public OffGridPriceException(String message) {
			super(message);
		}
	}
}
