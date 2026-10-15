package com.tradeez.analysis;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** The fixed facts every rule states about itself; a detector adds its rule (T10). */
abstract class RuleDetector implements Detector {

	private final String id;
	private final String ruleVersion;
	private final String conceptId;
	private final String methodologySource;
	private final Map<String, Object> parameters;

	RuleDetector(String id, String ruleVersion, String conceptId, String methodology,
			Map<String, Object> parameters) {
		this.id = id;
		this.ruleVersion = ruleVersion;
		this.conceptId = conceptId;
		this.methodologySource = methodology + ", as defined by rule " + ruleVersion;
		this.parameters = Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
	}

	@Override
	public final String id() {
		return id;
	}

	@Override
	public final String ruleVersion() {
		return ruleVersion;
	}

	@Override
	public final String conceptId() {
		return conceptId;
	}

	@Override
	public final String methodologySource() {
		return methodologySource;
	}

	@Override
	public final Map<String, Object> parameters() {
		return parameters;
	}
}
