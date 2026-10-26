package com.tradeez.news;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.tradeez.news.EconomicRelease.Impact;
import com.tradeez.news.FredClient.FredException;

/**
 * Tradeez's own economic calendar (T40, DEC-079): when each US release that moves NQ comes out, kept in the database
 * and brought up to date from the agencies' schedules — FRED's release calendar, and the Fed's for the FOMC.
 *
 * <p>A refresh replaces a release's days with what its source says now, so a release that is moved (a shutdown, a
 * holiday) is moved here too at the next refresh; one that fails keeps the days it had, and says how old they are. An
 * answer with no days at all is taken as a fault, not as "the release was abolished": it never wipes a schedule.
 */
@Service
public class EconomicCalendarService {

	private static final Logger log = LoggerFactory.getLogger(EconomicCalendarService.class);

	static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

	/** Older than this, the schedule is said to be possibly out of date. A refresh runs every six hours. */
	static final Duration STALE_AFTER = Duration.ofDays(2);

	/** The FOMC list is said to run short this long before its last date, so there is time to extend it. */
	static final Duration FOMC_WARNING = Duration.ofDays(120);

	private final JdbcClient jdbc;
	private final TransactionTemplate tx;
	private final FredClient fred;
	private final Clock clock;

	@Autowired
	public EconomicCalendarService(JdbcClient jdbc, PlatformTransactionManager transactions, FredClient fred) {
		this(jdbc, transactions, fred, Clock.systemUTC());
	}

	EconomicCalendarService(JdbcClient jdbc, PlatformTransactionManager transactions, FredClient fred, Clock clock) {
		this.jdbc = jdbc;
		this.tx = new TransactionTemplate(transactions);
		this.fred = fred;
		this.clock = clock;
	}

	public record Event(String key, String title, String agency, Impact impact, Instant at, String link) {
	}

	/**
	 * The releases between two moments, and how fresh the schedule is: {@code checkedAt} is the oldest of the releases'
	 * last good refreshes (null until every one has had one); {@code stale} when that is too old or missing.
	 */
	public record Calendar(List<Event> events, Instant checkedAt, boolean stale, LocalDate fomcKnownUntil,
			boolean fomcRunningOut) {
	}

	/** Brings every release up to date; the keys of the ones that could not be, each logged without its URL. */
	public List<String> refresh() {
		List<String> failed = new ArrayList<>();
		for (EconomicRelease release : EconomicRelease.values()) {
			try {
				OptionalInt id = release.fredReleaseId();
				store(release, id.isPresent() ? fred.releaseDates(id.getAsInt()) : FomcCalendar.DECISIONS);
			} catch (FredException e) {
				failed.add(release.name());
				log.warn("Economic calendar: {} kept as it was — {}", release.name(), e.getMessage());
			}
		}
		return failed;
	}

	void store(EconomicRelease release, List<LocalDate> days) {
		if (days.isEmpty()) {
			throw new FredException(release.name() + ": no dates in the answer");
		}
		OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
		tx.executeWithoutResult(status -> {
			jdbc.sql("DELETE FROM economic_release WHERE release_key = :key").param("key", release.name()).update();
			for (LocalDate day : new TreeSet<>(days)) {
				Instant at = ZonedDateTime.of(day, release.newYorkTime(), NEW_YORK).toInstant();
				jdbc.sql("INSERT INTO economic_release (release_key, release_date, at) VALUES (:key, :day, :at)")
					.param("key", release.name())
					.param("day", day)
					.param("at", OffsetDateTime.ofInstant(at, ZoneOffset.UTC))
					.update();
			}
			jdbc.sql("""
					INSERT INTO economic_release_check (release_key, checked_at) VALUES (:key, :now)
					ON CONFLICT (release_key) DO UPDATE SET checked_at = EXCLUDED.checked_at""")
				.param("key", release.name())
				.param("now", now)
				.update();
		});
	}

	public Calendar between(Instant from, Instant to) {
		List<Event> events = jdbc.sql("""
				SELECT release_key, at FROM economic_release
				WHERE at >= :from AND at < :to ORDER BY at, release_key""")
			.param("from", OffsetDateTime.ofInstant(from, ZoneOffset.UTC))
			.param("to", OffsetDateTime.ofInstant(to, ZoneOffset.UTC))
			.query((rs, row) -> {
				Instant at = rs.getObject("at", OffsetDateTime.class).toInstant();
				// A key this version no longer knows is left out, not an error.
				return EconomicRelease.byKey(rs.getString("release_key"))
					.map(r -> new Event(r.name(), r.title(), r.agency(), r.impact(), at, r.link()))
					.orElse(null);
			})
			.list()
			.stream()
			.filter(e -> e != null)
			.toList();

		Map<String, Instant> checks = jdbc.sql("SELECT release_key, checked_at FROM economic_release_check")
			.query((rs, row) -> Map.entry(rs.getString("release_key"),
				rs.getObject("checked_at", OffsetDateTime.class).toInstant()))
			.list()
			.stream()
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
		Instant checkedAt = null;
		for (EconomicRelease release : EconomicRelease.values()) {
			Instant at = checks.get(release.name());
			if (at == null) {
				checkedAt = null;
				break;
			}
			checkedAt = checkedAt == null || at.isBefore(checkedAt) ? at : checkedAt;
		}
		Instant now = clock.instant();
		boolean stale = checkedAt == null || checkedAt.isBefore(now.minus(STALE_AFTER));
		LocalDate fomcUntil = FomcCalendar.knownUntil();
		boolean runningOut = ZonedDateTime.of(fomcUntil.atStartOfDay(), NEW_YORK).toInstant()
			.isBefore(now.plus(FOMC_WARNING));
		return new Calendar(events, checkedAt, stale, fomcUntil, runningOut);
	}
}
