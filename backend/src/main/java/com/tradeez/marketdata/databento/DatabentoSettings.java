package com.tradeez.marketdata.databento;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Databento connection settings and the spending caps (DEC-040).
 *
 * <p>The API key comes from the environment — in development, {@code backend/.env}, which is
 * gitignored — and is only ever handed to {@link DatabentoClient}. It has no getter, and
 * {@link #toString()} leaves it out, so it cannot end up in a log line or an error message by
 * accident.
 */
@Component
public class DatabentoSettings {

	private final String apiKey;
	private final String baseUrl;
	private final String dataset;
	private final BigDecimal maxCostPerRequestUsd;
	private final BigDecimal maxTotalCostUsd;

	public DatabentoSettings(
			@Value("${databento.api-key:}") String apiKey,
			@Value("${databento.base-url:https://hist.databento.com}") String baseUrl,
			@Value("${databento.dataset:GLBX.MDP3}") String dataset,
			@Value("${databento.max-cost-per-request-usd:5.00}") BigDecimal maxCostPerRequestUsd,
			@Value("${databento.max-total-cost-usd:100.00}") BigDecimal maxTotalCostUsd) {
		this.apiKey = apiKey == null ? "" : apiKey.trim();
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		this.dataset = dataset;
		this.maxCostPerRequestUsd = maxCostPerRequestUsd;
		this.maxTotalCostUsd = maxTotalCostUsd;
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

	public String dataset() {
		return dataset;
	}

	public BigDecimal maxCostPerRequestUsd() {
		return maxCostPerRequestUsd;
	}

	public BigDecimal maxTotalCostUsd() {
		return maxTotalCostUsd;
	}

	@Override
	public String toString() {
		return "DatabentoSettings[baseUrl=" + baseUrl + ", dataset=" + dataset
			+ ", apiKey=" + (hasApiKey() ? "(set)" : "(not set)")
			+ ", maxCostPerRequestUsd=" + maxCostPerRequestUsd
			+ ", maxTotalCostUsd=" + maxTotalCostUsd + "]";
	}
}
