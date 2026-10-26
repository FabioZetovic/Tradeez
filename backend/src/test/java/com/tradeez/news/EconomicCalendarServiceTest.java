package com.tradeez.news;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;

import com.tradeez.AbstractIntegrationTest;
import com.tradeez.news.EconomicCalendarService.Calendar;
import com.tradeez.news.EconomicCalendarService.Event;
import com.tradeez.news.EconomicRelease.Impact;

/** The economic calendar against the real schema (DEC-079), with FRED stood in for and the clock fixed. */
class EconomicCalendarServiceTest extends AbstractIntegrationTest {

	private static final Instant NOW = Instant.parse("2026-10-26T12:00:00Z");

	@Autowired
	JdbcClient jdbc;

	@Autowired
	PlatformTransactionManager transactions;

	/** FRED as the test says it is: the days per release, and the releases it fails for. */
	private final Map<Integer, List<LocalDate>> fred = new HashMap<>();
	private final Set<Integer> down = new HashSet<>();

	private EconomicCalendarService service(Instant now) {
		FredClient stand = new FredClient("test-key", "http://localhost:1") {
			@Override
			public List<LocalDate> releaseDates(int releaseId) {
				if (down.contains(releaseId)) {
					throw new FredException("release " + releaseId + ": HTTP 503");
				}
				return fred.getOrDefault(releaseId, List.of(LocalDate.of(2026, 1, 2)));
			}
		};
		return new EconomicCalendarService(jdbc, transactions, stand, Clock.fixed(now, ZoneOffset.UTC));
	}

	@BeforeEach
	void empty() {
		jdbc.sql("DELETE FROM economic_release").update();
		jdbc.sql("DELETE FROM economic_release_check").update();
	}

	private List<Event> week(EconomicCalendarService service, String fromIso, String toIso) {
		return service.between(Instant.parse(fromIso), Instant.parse(toIso)).events();
	}

	@Test
	@DisplayName("each release at its New York time, daylight saving or not; the FOMC at 14:00 on the meeting's second day")
	void newYorkTimes() {
		fred.put(10, List.of(LocalDate.of(2025, 1, 15), LocalDate.of(2025, 3, 12))); // CPI, EST and EDT
		fred.put(192, List.of(LocalDate.of(2026, 11, 3)));                           // JOLTS, 10:00
		EconomicCalendarService service = service(NOW);
		assertThat(service.refresh()).isEmpty();

		assertThat(week(service, "2025-01-13T00:00:00Z", "2025-01-20T00:00:00Z"))
			.extracting(Event::key, Event::at)
			.containsExactly(org.assertj.core.groups.Tuple.tuple("CPI", Instant.parse("2025-01-15T13:30:00Z")));
		assertThat(week(service, "2025-03-10T00:00:00Z", "2025-03-17T00:00:00Z")).extracting(Event::at)
			.containsExactly(Instant.parse("2025-03-12T12:30:00Z"));
		List<Event> fomcWeek = week(service, "2026-10-26T00:00:00Z", "2026-11-05T00:00:00Z");
		assertThat(fomcWeek).extracting(Event::key, Event::at, Event::impact).containsExactly(
			org.assertj.core.groups.Tuple.tuple("FOMC", Instant.parse("2026-10-28T18:00:00Z"), Impact.HIGH),
			org.assertj.core.groups.Tuple.tuple("JOLTS", Instant.parse("2026-11-03T15:00:00Z"), Impact.MEDIUM));
		assertThat(fomcWeek.getFirst().agency()).isEqualTo("Federal Reserve");
	}

	@Test
	@DisplayName("a release the agency moves is moved at the next refresh, and its old day is gone")
	void moved() {
		fred.put(50, List.of(LocalDate.of(2026, 11, 6)));
		service(NOW).refresh();
		fred.put(50, List.of(LocalDate.of(2026, 11, 13)));   // a shutdown pushes the jobs report back a week
		EconomicCalendarService later = service(NOW.plusSeconds(6 * 3600));
		later.refresh();
		assertThat(week(later, "2026-11-01T00:00:00Z", "2026-11-20T00:00:00Z"))
			.filteredOn(e -> e.key().equals("NFP"))
			.extracting(Event::at)
			.containsExactly(Instant.parse("2026-11-13T13:30:00Z"));
	}

	@Test
	@DisplayName("a release FRED fails for keeps its days, and the schedule says it is old once two days have passed")
	void failedKeepsAndAges() {
		fred.put(10, List.of(LocalDate.of(2026, 11, 10)));
		EconomicCalendarService first = service(NOW);
		first.refresh();
		Calendar fresh = first.between(NOW, NOW.plusSeconds(30L * 86_400));
		assertThat(fresh.stale()).isFalse();
		assertThat(fresh.checkedAt()).isEqualTo(NOW);

		down.add(10);
		EconomicCalendarService threeDaysOn = service(NOW.plusSeconds(3L * 86_400));
		assertThat(threeDaysOn.refresh()).containsExactly("CPI");
		Calendar old = threeDaysOn.between(NOW, NOW.plusSeconds(30L * 86_400));
		assertThat(old.events()).extracting(Event::key).contains("CPI");
		// The oldest release's last good refresh is the schedule's age: CPI's, three days back.
		assertThat(old.checkedAt()).isEqualTo(NOW);
		assertThat(old.stale()).isTrue();
	}

	@Test
	@DisplayName("an answer with no days never wipes a schedule; a release never refreshed leaves it unchecked")
	void emptyNeverWipes() {
		fred.put(46, List.of(LocalDate.of(2026, 11, 13)));
		service(NOW).refresh();
		fred.put(46, List.of());
		EconomicCalendarService later = service(NOW.plusSeconds(3600));
		assertThat(later.refresh()).containsExactly("PPI");
		assertThat(week(later, "2026-11-09T00:00:00Z", "2026-11-16T00:00:00Z")).extracting(Event::key).contains("PPI");

		jdbc.sql("DELETE FROM economic_release_check WHERE release_key = 'GDP'").update();
		Calendar unchecked = later.between(NOW, NOW.plusSeconds(86_400));
		assertThat(unchecked.checkedAt()).isNull();
		assertThat(unchecked.stale()).isTrue();
	}

	@Test
	@DisplayName("says how far the FOMC's dates reach, and warns four months before they run out")
	void fomcReach() {
		Calendar now = service(NOW).between(NOW, NOW.plusSeconds(86_400));
		assertThat(now.fomcKnownUntil()).isEqualTo(LocalDate.of(2027, 12, 8));
		assertThat(now.fomcRunningOut()).isFalse();
		Instant august2027 = Instant.parse("2027-08-15T12:00:00Z");
		assertThat(service(august2027).between(august2027, august2027.plusSeconds(86_400)).fomcRunningOut()).isTrue();
	}
}
