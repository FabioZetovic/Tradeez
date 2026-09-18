package com.tradeez;

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
		SpringApplication.run(TradeezApplication.class, args);
	}
}
