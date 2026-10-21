package com.tradeez.evidence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.tradeez.marketdata.MarketDataService;

/**
 * Brings the evidence up to date once the app is serving (T21, DEC-065): after an import, a new rule
 * version or a change to the counting, the next start recounts what changed — in the background, so no
 * request waits for it. A lesson card shows the counts it has, and says so when there are none yet.
 *
 * <p>{@code tradeez.evidence.refresh-on-start}: on by default, off in tests, which count on purpose.
 */
@Component
public class EvidenceRefresher {

	private static final Logger log = LoggerFactory.getLogger(EvidenceRefresher.class);

	private final EvidenceService evidence;
	private final MarketDataService marketData;
	private final boolean enabled;

	public EvidenceRefresher(EvidenceService evidence, MarketDataService marketData,
			@Value("${tradeez.evidence.refresh-on-start}") boolean enabled) {
		this.evidence = evidence;
		this.marketData = marketData;
		this.enabled = enabled;
	}

	@EventListener(ApplicationReadyEvent.class)
	void refreshInBackground() {
		if (!enabled) {
			return;
		}
		Thread.ofVirtual().name("evidence-refresh").start(() -> {
			try {
				marketData.listInstruments().forEach(evidence::refresh);
			} catch (RuntimeException e) {
				// The counts are a cache: without them a card says it has none yet. Never a reason to stop the app.
				log.warn("Evidence could not be brought up to date", e);
			}
		});
	}
}
