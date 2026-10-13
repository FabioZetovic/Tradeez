package com.tradeez.marketdata.massive;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.DataClass;
import com.tradeez.marketdata.Instrument;
import com.tradeez.marketdata.MarketBarStore;
import com.tradeez.marketdata.MarketBarStore.StoredBar;
import com.tradeez.marketdata.importing.ImportChunks;
import com.tradeez.marketdata.importing.ImportLedger;
import com.tradeez.marketdata.importing.ImportReport;
import com.tradeez.marketdata.importing.ImportReport.Chunk;
import com.tradeez.marketdata.importing.ImportReport.Outcome;
import com.tradeez.marketdata.importing.MarketDataImporter;
import com.tradeez.marketdata.importing.OffGridPriceException;
import com.tradeez.marketdata.massive.MassiveClient.SessionBar;

/**
 * Downloads real one-minute bars from Massive's free plan into {@code market_bar} (DEC-042).
 *
 * <h2>How a range is imported</h2>
 * <ol>
 *   <li>List every quarterly contract that could be the front contract in the range
 *       ({@link QuarterlyContract#covering}).</li>
 *   <li>Fetch each one's daily session bars — one request per contract — and decide the front
 *       contract for every trading day by the previous session's volume ({@link RollSchedule}).</li>
 *   <li>Walk the range in calendar months. For each month, fetch the minute bars of whichever
 *       contract was front on each of its days, and keep only bars from those days.</li>
 * </ol>
 *
 * <p>Each finished month is recorded in {@code market_data_import}, so an interrupted run resumes
 * where it stopped and a repeated run downloads nothing it already has. A month for which Massive
 * has nothing — outside the plan's two-year window, Massive answers "OK" with no results — is
 * reported as {@link Outcome#NO_DATA} and <b>not</b> recorded as covered, so a wider plan later can
 * still fill it.
 *
 * <h2>Correctness</h2>
 * Prices are converted to ticks exactly; a price off the tick grid fails the month rather than
 * being rounded (INVARIANT 3). Every bar keeps the id of the contract it came from, and prices stay
 * raw, so the jump at each roll is visible (DEC-010, DEC-024).
 *
 * <p>The free plan has no one-second bars, so only {@code 1m} is accepted.
 */
@Service
public class MassiveImporter implements MarketDataImporter {

	private static final Logger log = LoggerFactory.getLogger(MassiveImporter.class);

	static final String SOURCE = "massive";
	static final String DATASET = "CME";

	/** Tradeez instrument → Massive product root. */
	static final Map<String, String> ROOTS = Map.of("NQ", "NQ");

	/** Sessions fetched before the range, so its first day has a previous session to roll by. */
	static final int LOOKBACK_DAYS = 14;

	private static final int BATCH = 10_000;

	private final MassiveClient client;
	private final ImportLedger ledger;
	private final MarketBarStore store;

	public MassiveImporter(MassiveClient client, ImportLedger ledger, MarketBarStore store) {
		this.client = client;
		this.ledger = ledger;
		this.store = store;
	}

	/** A stretch of one month served by one contract, and the trade dates it covers. */
	record Span(QuarterlyContract contract, Instant from, Instant to, Set<LocalDate> tradeDates) {
	}

	@Override
	public String source() {
		return SOURCE;
	}

	@Override
	public ImportReport importRange(Instrument instrument, String resolution, Instant from,
			Instant to, boolean dryRun) {
		if (instrument.getDataClass() == DataClass.SIMULATED) {
			throw new IllegalArgumentException(instrument.getSymbol() + " is simulated; there is "
				+ "nothing to import.");
		}
		String root = ROOTS.get(instrument.getSymbol());
		if (root == null) {
			throw new IllegalArgumentException("No Massive product for " + instrument.getSymbol());
		}
		if ("1s".equals(resolution)) {
			throw new IllegalArgumentException("Massive's free plan has no one-second bars "
				+ "(DEC-042); import 1m from Massive.");
		}
		if (!"1m".equals(resolution)) {
			throw new IllegalArgumentException("Resolution must be 1m");
		}
		if (!from.isBefore(to)) {
			throw new IllegalArgumentException("'from' must be before 'to'");
		}

		List<Instant[]> chunks = ImportChunks.of(resolution, from, to);
		List<Boolean> covered = chunks.stream()
			.map(c -> ledger.covered(instrument.getId(), resolution, c[0], c[1]))
			.toList();
		// The schedule costs a request per contract; with everything already imported, skip it.
		NavigableMap<LocalDate, QuarterlyContract> front = covered.contains(false)
			? schedule(root, tradingDate(instrument, from), tradingDate(instrument, to.minusNanos(1)))
			: new TreeMap<>();

		List<Chunk> reports = new ArrayList<>();
		long stored = 0;
		for (int i = 0; i < chunks.size(); i++) {
			Instant[] chunk = chunks.get(i);
			if (covered.get(i)) {
				reports.add(new Chunk(chunk[0], chunk[1], Outcome.ALREADY_IMPORTED, BigDecimal.ZERO,
					0, null));
				continue;
			}
			List<Span> spans = spans(instrument, front, chunk[0], chunk[1]);
			if (spans.isEmpty()) {
				reports.add(new Chunk(chunk[0], chunk[1], Outcome.NO_DATA, BigDecimal.ZERO, 0,
					"no sessions from Massive (outside the plan's history?)"));
				continue;
			}
			String contracts = spans.stream().map(s -> s.contract().ticker()).distinct()
				.collect(Collectors.joining(","));
			if (dryRun) {
				reports.add(new Chunk(chunk[0], chunk[1], Outcome.ESTIMATED, BigDecimal.ZERO, 0,
					contracts));
				continue;
			}

			ImportLedger.Request request = new ImportLedger.Request(instrument.getId(), SOURCE,
				DATASET, contracts, resolution, chunk[0], chunk[1]);
			long[] result = download(request, spans, instrument.getTickSize());
			stored += result[1];
			Outcome outcome = result[0] == 0 ? Outcome.NO_DATA : Outcome.IMPORTED;
			reports.add(new Chunk(chunk[0], chunk[1], outcome, BigDecimal.ZERO, result[1], contracts));
			log.info("{} {} → {}: {} bars from {}", outcome, chunk[0], chunk[1], result[1], contracts);
		}
		return new ImportReport(SOURCE, dryRun, reports, BigDecimal.ZERO, stored);
	}

	/** The front contract for every trade date in {@code [firstDate, lastDate]}, and a few before. */
	private NavigableMap<LocalDate, QuarterlyContract> schedule(String root, LocalDate firstDate,
			LocalDate lastDate) {
		Map<QuarterlyContract, List<SessionBar>> sessions = new LinkedHashMap<>();
		for (QuarterlyContract contract : QuarterlyContract.covering(root, firstDate, lastDate)) {
			// Opening dates: a session opens the evening before its trade date.
			sessions.put(contract, client.sessions(contract.ticker(),
				firstDate.minusDays(LOOKBACK_DAYS + 1L), lastDate.plusDays(1)));
		}
		return RollSchedule.frontByVolume(sessions);
	}

	/**
	 * Splits one chunk into stretches served by a single contract. A trade date's session runs from
	 * the instrument's session open on the evening before to its close on the day; each stretch is
	 * that, clipped to the chunk.
	 */
	static List<Span> spans(Instrument instrument, NavigableMap<LocalDate, QuarterlyContract> front,
			Instant chunkFrom, Instant chunkTo) {
		List<Span> spans = new ArrayList<>();
		QuarterlyContract current = null;
		Instant spanFrom = null;
		Instant spanTo = null;
		Set<LocalDate> dates = null;
		for (Map.Entry<LocalDate, QuarterlyContract> day : front.entrySet()) {
			Instant open = sessionOpen(instrument, day.getKey());
			Instant close = sessionClose(instrument, day.getKey());
			if (!open.isBefore(chunkTo) || !close.isAfter(chunkFrom)) {
				continue;
			}
			Instant clippedFrom = open.isBefore(chunkFrom) ? chunkFrom : open;
			Instant clippedTo = close.isAfter(chunkTo) ? chunkTo : close;
			if (day.getValue().equals(current)) {
				spanTo = clippedTo;
				dates.add(day.getKey());
			} else {
				if (current != null) {
					spans.add(new Span(current, spanFrom, spanTo, dates));
				}
				current = day.getValue();
				spanFrom = clippedFrom;
				spanTo = clippedTo;
				dates = new HashSet<>();
				dates.add(day.getKey());
			}
		}
		if (current != null) {
			spans.add(new Span(current, spanFrom, spanTo, dates));
		}
		return spans;
	}

	/** @return {bars received from the front contract, bars newly stored} */
	private long[] download(ImportLedger.Request request, List<Span> spans, BigDecimal tickSize) {
		UUID id = ledger.start(request, BigDecimal.ZERO);
		List<StoredBar> buffer = new ArrayList<>(BATCH);
		long[] counts = { 0, 0 };
		try {
			for (Span span : spans) {
				client.minuteBars(span.contract().ticker(), span.from(), span.to(), raw -> {
					if (!span.tradeDates().contains(raw.sessionEndDate())) {
						return;
					}
					buffer.add(new StoredBar(toBar(raw, tickSize), span.contract().id()));
					counts[0]++;
					if (buffer.size() >= BATCH) {
						counts[1] += store.insert(request.instrumentId(), request.resolution(), buffer);
						buffer.clear();
					}
				});
			}
			counts[1] += store.insert(request.instrumentId(), request.resolution(), buffer);
			if (counts[0] == 0) {
				ledger.failed(id, 0, "Massive returned no minute bars for " + request.symbol());
			} else {
				ledger.done(id, counts[1]);
			}
			return counts;
		} catch (RuntimeException e) {
			ledger.failed(id, counts[1], e.getMessage());
			throw e;
		}
	}

	static Bar toBar(MassiveClient.RawBar raw, BigDecimal tickSize) {
		Instant openTime = Instant.ofEpochSecond(0, raw.windowStartNanos());
		return new Bar(openTime,
			toTicks(raw.open(), tickSize, openTime),
			toTicks(raw.high(), tickSize, openTime),
			toTicks(raw.low(), tickSize, openTime),
			toTicks(raw.close(), tickSize, openTime),
			raw.volume());
	}

	static long toTicks(BigDecimal price, BigDecimal tickSize, Instant at) {
		BigDecimal[] quotientAndRemainder = price.divideAndRemainder(tickSize);
		if (quotientAndRemainder[1].signum() != 0) {
			throw new OffGridPriceException("Price " + price.toPlainString() + " at " + at
				+ " is not a whole number of " + tickSize.toPlainString()
				+ "-point ticks; refusing to round a market price.");
		}
		return quotientAndRemainder[0].longValueExact();
	}

	/** The exchange trade date of an instant: a session that opens in the evening belongs to the next day. */
	static LocalDate tradingDate(Instrument instrument, Instant instant) {
		ZonedDateTime local = instant.atZone(ZoneId.of(instrument.getExchangeTimezone()));
		return local.toLocalTime().isBefore(instrument.getSessionOpenLocal())
			? local.toLocalDate()
			: local.toLocalDate().plusDays(1);
	}

	static Instant sessionOpen(Instrument instrument, LocalDate tradeDate) {
		return tradeDate.minusDays(1).atTime(instrument.getSessionOpenLocal())
			.atZone(ZoneId.of(instrument.getExchangeTimezone())).toInstant();
	}

	static Instant sessionClose(Instrument instrument, LocalDate tradeDate) {
		return tradeDate.atTime(instrument.getSessionCloseLocal())
			.atZone(ZoneId.of(instrument.getExchangeTimezone())).toInstant();
	}
}
