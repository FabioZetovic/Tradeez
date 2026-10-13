package com.tradeez.marketdata.importing;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits an import range into fixed chunks, so a finished chunk is recognised on the next run:
 * calendar months (UTC) for one-minute bars, days for one-second bars. The first and last chunk are
 * trimmed to the range.
 */
public final class ImportChunks {

	private ImportChunks() {
	}

	public static List<Instant[]> of(String resolution, Instant from, Instant to) {
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
}
