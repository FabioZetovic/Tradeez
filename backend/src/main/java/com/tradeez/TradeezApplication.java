package com.tradeez;

import java.time.ZoneOffset;
import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Tradeez — futures day-trading practice platform.
 *
 * <p>Before changing anything structural here, read {@code docs/DECISIONS.md}. It is the
 * authoritative architecture decision record and supersedes both analysis documents.
 */
@SpringBootApplication
@EnableScheduling // rate-limiter window eviction (BUG-002)
public class TradeezApplication {

	public static void main(String[] args) {
		useUtc();
		SpringApplication.run(TradeezApplication.class, args);
	}

	/**
	 * INVARIANT 4 — the JVM runs in UTC, whatever the host's timezone.
	 *
	 * <p>BUG-036: {@code application.yml} said this was set "at the JVM level", but nothing set it,
	 * so the app ran in the host's zone (Europe/Zagreb on Fabio's machine). Hibernate reads SQL
	 * {@code TIME} columns as UTC ({@code hibernate.jdbc.time_zone}) and converts them to the JVM
	 * zone, so NQ's 17:00 session open came back as 18:00 in winter and 19:00 in summer. Instants
	 * were never affected; local times and dates were.
	 */
	public static void useUtc() {
		TimeZone.setDefault(TimeZone.getTimeZone(ZoneOffset.UTC));
	}
}
