package com.tradeez.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.TimeZone;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.tradeez.AbstractIntegrationTest;

/**
 * BUG-036 — session times stored as SQL {@code TIME} must read back exactly as stored.
 *
 * <p>They came back an hour late on a machine in Central European Time, because the JVM was never
 * actually put in UTC while Hibernate read {@code TIME} values as UTC. Found by the Massive importer's
 * tests: Monday's session was requested from 18:00 Chicago time instead of 17:00.
 */
class InstrumentSessionTimesTest extends AbstractIntegrationTest {

	@Autowired
	MarketDataService marketData;

	@Test
	@DisplayName("the JVM runs in UTC (INVARIANT 4)")
	void jvmIsUtc() {
		assertThat(TimeZone.getDefault().toZoneId().normalized()).isEqualTo(ZoneOffset.UTC);
	}

	@Test
	@DisplayName("NQ's session reads back as 17:00 to 16:00 Chicago time, as stored")
	void nqSessionTimes() {
		Instrument nq = marketData.requireInstrument("NQ");
		assertThat(nq.getExchangeTimezone()).isEqualTo("America/Chicago");
		assertThat(nq.getSessionOpenLocal()).isEqualTo(LocalTime.of(17, 0));
		assertThat(nq.getSessionCloseLocal()).isEqualTo(LocalTime.of(16, 0));
	}
}
