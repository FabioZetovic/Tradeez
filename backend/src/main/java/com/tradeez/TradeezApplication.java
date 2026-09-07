package com.tradeez;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Tradeez — futures day-trading practice platform.
 *
 * <p>Before changing anything structural here, read {@code docs/DECISIONS.md}. It is the
 * authoritative architecture decision record and supersedes both analysis documents.
 */
@SpringBootApplication
public class TradeezApplication {

	public static void main(String[] args) {
		SpringApplication.run(TradeezApplication.class, args);
	}
}
