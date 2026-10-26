package com.tradeez.news;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * FRED's release calendar (T40, DEC-079): the days a release is published, past and announced.
 *
 * <p>FRED takes its key only as a query parameter, so the URL carries it. That URL is therefore never logged, never put
 * in an exception message, and never chained as a cause — a failure says which release and what went wrong, no more.
 * The key itself has no getter and {@link #toString()} leaves it out (as {@code MassiveSettings} does, DEC-042).
 * Redirects are not followed: the key goes only to the configured host.
 */
@Component
public class FredClient {

	/** The calendar is asked for from here: the start of the NQ history Tradeez replays, and a little before. */
	static final LocalDate FROM = LocalDate.of(2024, 1, 1);

	private final String apiKey;
	private final String baseUrl;
	private final HttpClient http = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(10))
		.followRedirects(HttpClient.Redirect.NEVER)
		.build();
	private final ObjectMapper json = JsonMapper.builder().build();

	public FredClient(@Value("${fred.api-key:}") String apiKey,
			@Value("${fred.base-url:https://api.stlouisfed.org}") String baseUrl) {
		this.apiKey = apiKey == null ? "" : apiKey.trim();
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
	}

	public boolean hasApiKey() {
		return !apiKey.isEmpty();
	}

	/** Every day the release is published on, from {@link #FROM} on, announced future days included, in order. */
	public List<LocalDate> releaseDates(int releaseId) {
		if (!hasApiKey()) {
			throw new FredException("FRED_API_KEY is not set");
		}
		String query = "release_id=" + releaseId
			+ "&realtime_start=" + FROM
			+ "&realtime_end=9999-12-31"
			+ "&include_release_dates_with_no_data=true"
			+ "&sort_order=asc&limit=10000&file_type=json"
			+ "&api_key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
		HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/fred/release/dates?" + query))
			.timeout(Duration.ofSeconds(20))
			.header("User-Agent", "Tradeez (graduation project)")
			.GET()
			.build();
		HttpResponse<String> response;
		try {
			response = http.send(request, HttpResponse.BodyHandlers.ofString());
		} catch (IOException e) {
			throw new FredException("release " + releaseId + ": " + e.getClass().getSimpleName());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new FredException("release " + releaseId + ": interrupted");
		}
		if (response.statusCode() != 200) {
			throw new FredException("release " + releaseId + ": HTTP " + response.statusCode());
		}
		return parse(releaseId, response.body());
	}

	List<LocalDate> parse(int releaseId, String body) {
		try {
			JsonNode dates = json.readTree(body).path("release_dates");
			List<LocalDate> days = new ArrayList<>();
			for (JsonNode date : dates) {
				days.add(LocalDate.parse(date.path("date").asString()));
			}
			return days;
		} catch (JacksonException | DateTimeParseException e) {
			throw new FredException("release " + releaseId + ": unreadable answer");
		}
	}

	@Override
	public String toString() {
		return "FredClient[baseUrl=" + baseUrl + ", apiKey=" + (hasApiKey() ? "(set)" : "(not set)") + "]";
	}

	/** A failure that names the release and the reason, never the URL that carries the key. */
	public static class FredException extends RuntimeException {
		FredException(String message) {
			super("FRED: " + message, null, false, false);
		}
	}
}
