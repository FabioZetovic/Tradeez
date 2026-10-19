package com.tradeez.practice;

import java.util.List;

/**
 * ICT's entry models (Part 9 of ICT_CONCEPTS.md) a learner can name when placing a trade (T22, DEC-066).
 * Each is a composition of rules the app already has, so its checklist can be checked against what those
 * rules had confirmed at the moment of the decision. The market maker models (ICT-77) are judgement, and
 * are not on the list.
 */
public enum TradingModel {

	MODEL_2022("model-2022-1.0.0", "ICT-70", "The 2022 model", List.of("MSS", "FVG", "LIQUIDITY")),
	SILVER_BULLET("model-sb-1.0.0", "ICT-71", "Silver Bullet", List.of("FVG", "LIQUIDITY")),
	OTE("model-ote-1.0.0", "ICT-72", "OTE model", List.of("MSS", "OTE")),
	UNICORN("model-unicorn-1.0.0", "ICT-73", "Unicorn model", List.of("UNICORN")),
	TURTLE_SOUP("model-turtle-1.0.0", "ICT-74", "Turtle soup model", List.of("TURTLE_SOUP", "LIQUIDITY")),
	BREAKER("model-breaker-1.0.0", "ICT-75", "Breaker or mitigation entry", List.of("BREAKER", "MITIGATION")),
	SESSION_REVERSAL("model-reversal-1.0.0", "ICT-76", "Session open reversal", List.of("JUDAS", "MSS")),
	OPENING_RANGE_GAP("model-org-1.0.0", "ICT-78", "Opening range gap model", List.of("ORG"));

	private final String version;
	private final String conceptId;
	private final String title;
	private final List<String> detectors;

	TradingModel(String version, String conceptId, String title, List<String> detectors) {
		this.version = version;
		this.conceptId = conceptId;
		this.title = title;
		this.detectors = detectors;
	}

	public String version() {
		return version;
	}

	public String conceptId() {
		return conceptId;
	}

	public String title() {
		return title;
	}

	/** The rules its checklist reads. */
	public List<String> detectors() {
		return detectors;
	}
}
