package com.tradeez.marketdata.massive;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Massive connection settings (DEC-042).
 *
 * <p>The API key comes from the environment — in development, {@code backend/.env}, which is
 * gitignored — and is only ever handed to {@link MassiveClient} for its Authorization header. It has
 * no public getter, and {@link #toString()} leaves it out, so it cannot end up in a log line or an
 * error message by accident.
 */
@Component
public class MassiveSettings {

	private final String apiKey;
	private final String baseUrl;
	private final int requestsPerMinute;
	private final int pageLimit;

	public MassiveSettings(
			@Value("${massive.api-key:}") String apiKey,
			@Value("${massive.base-url:https://api.massive.com}") String baseUrl,
			@Value("${massive.requests-per-minute:5}") int requestsPerMinute,
			@Value("${massive.page-limit:50000}") int pageLimit) {
		this.apiKey = apiKey == null ? "" : apiKey.trim();
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		this.requestsPerMinute = requestsPerMinute;
		this.pageLimit = pageLimit;
	}

	public boolean hasApiKey() {
		return !apiKey.isEmpty();
	}

	/** For the client's Authorization header only. */
	String apiKey() {
		return apiKey;
	}

	public String baseUrl() {
		return baseUrl;
	}

	/** The free plan allows five; zero disables pacing (tests against a local fake). */
	public int requestsPerMinute() {
		return requestsPerMinute;
	}

	/** Bars per page; Massive's maximum is 50,000. */
	public int pageLimit() {
		return pageLimit;
	}

	@Override
	public String toString() {
		return "MassiveSettings[baseUrl=" + baseUrl
			+ ", apiKey=" + (hasApiKey() ? "(set)" : "(not set)")
			+ ", requestsPerMinute=" + requestsPerMinute + ", pageLimit=" + pageLimit + "]";
	}
}
