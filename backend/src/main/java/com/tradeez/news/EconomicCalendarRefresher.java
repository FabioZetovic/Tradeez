package com.tradeez.news;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps the economic calendar current (T40, DEC-079): once the app is serving, in the background, and every six hours
 * after — so a release an agency moves is moved here the same day, with nobody updating anything by hand.
 *
 * <p>{@code tradeez.news.refresh-enabled}: on by default, off in tests, which never call FRED.
 */
@Component
public class EconomicCalendarRefresher {

	private static final Logger log = LoggerFactory.getLogger(EconomicCalendarRefresher.class);

	private final EconomicCalendarService calendar;
	private final boolean enabled;

	public EconomicCalendarRefresher(EconomicCalendarService calendar,
			@Value("${tradeez.news.refresh-enabled}") boolean enabled) {
		this.calendar = calendar;
		this.enabled = enabled;
	}

	@EventListener(ApplicationReadyEvent.class)
	void refreshOnStart() {
		if (enabled) {
			Thread.ofVirtual().name("economic-calendar").start(this::refresh);
		}
	}

	@Scheduled(fixedDelayString = "PT6H", initialDelayString = "PT6H")
	void refreshEverySixHours() {
		if (enabled) {
			refresh();
		}
	}

	private void refresh() {
		try {
			var failed = calendar.refresh();
			if (failed.isEmpty()) {
				log.info("Economic calendar brought up to date");
			}
		} catch (RuntimeException e) {
			// The calendar is a convenience: an old one says how old it is. Never a reason to stop the app.
			log.warn("Economic calendar could not be brought up to date: {}", e.getClass().getSimpleName());
		}
	}
}
