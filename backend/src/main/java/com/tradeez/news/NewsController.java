package com.tradeez.news;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tradeez.news.EconomicCalendarService.Calendar;
import com.tradeez.news.EconomicCalendarService.Event;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** The US economic releases that move NQ, from the agencies' own schedules (T40, DEC-079). */
@RestController
@RequestMapping("/api/news")
@Tag(name = "News", description = "The economic calendar: when the US releases that move NQ come out")
public class NewsController {

	/** FRED's terms ask for this wherever its data is used, with a link to them. */
	static final String FRED_NOTICE =
		"This product uses the FRED® API but is not endorsed or certified by the Federal Reserve Bank of St. Louis.";
	static final String FRED_TERMS = "https://fred.stlouisfed.org/docs/api/terms_of_use.html";

	static final Duration DEFAULT_SPAN = Duration.ofDays(14);
	static final Duration MAX_SPAN = Duration.ofDays(62);

	private final EconomicCalendarService calendar;

	public NewsController(EconomicCalendarService calendar) {
		this.calendar = calendar;
	}

	public record EventView(String key, String title, String agency, String impact, long at, String link) {
		static EventView of(Event e) {
			return new EventView(e.key(), e.title(), e.agency(), e.impact().name(), e.at().getEpochSecond(), e.link());
		}
	}

	public record NewsResponse(List<EventView> events, Long checkedAt, boolean stale, LocalDate fomcKnownUntil,
			boolean fomcRunningOut, String sources, String notice, String termsUrl) {
	}

	@GetMapping
	@Operation(summary = "The releases between two moments (UTC epoch seconds), in order",
		description = "Both optional: from defaults to this week's Monday, 00:00 New York; to, two weeks after from. "
			+ "At most 62 days. Times are when each release comes out. checkedAt and stale say how fresh the schedule "
			+ "is; fomcKnownUntil how far the FOMC's dates reach.")
	public NewsResponse news(@RequestParam(required = false) Long from, @RequestParam(required = false) Long to) {
		Instant start = from != null ? Instant.ofEpochSecond(from) : thisMonday();
		Instant end = to != null ? Instant.ofEpochSecond(to) : start.plus(DEFAULT_SPAN);
		if (!end.isAfter(start) || Duration.between(start, end).compareTo(MAX_SPAN) > 0) {
			throw new IllegalArgumentException("from must be before to, at most 62 days apart");
		}
		Calendar c = calendar.between(start, end);
		return new NewsResponse(c.events().stream().map(EventView::of).toList(),
			c.checkedAt() == null ? null : c.checkedAt().getEpochSecond(), c.stale(), c.fomcKnownUntil(),
			c.fomcRunningOut(), "BLS, BEA, the Census Bureau and the Department of Labor via FRED; the Federal Reserve",
			FRED_NOTICE, FRED_TERMS);
	}

	private static Instant thisMonday() {
		return ZonedDateTime.now(EconomicCalendarService.NEW_YORK)
			.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
			.toLocalDate()
			.atStartOfDay(EconomicCalendarService.NEW_YORK)
			.toInstant();
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException e) {
		return ResponseEntity.badRequest().body(Map.of(
			"status", HttpStatus.BAD_REQUEST.value(),
			"message", e.getMessage(),
			"timestamp", Instant.now().toString()));
	}
}
