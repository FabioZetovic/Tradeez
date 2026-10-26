package com.tradeez.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;
import com.tradeez.news.FredClient.FredException;

/**
 * FRED's release calendar client (DEC-079), against a local stand-in: what it asks for, what it reads back, and that
 * the key — which FRED takes only in the URL — never shows in a failure or in {@code toString}.
 */
class FredClientTest {

	private static final String KEY = "abcdef0123456789abcdef0123456789";

	private HttpServer server;
	private final List<Map<String, String>> queries = new ArrayList<>();
	private int status = 200;
	private String body = "";
	private String location;

	@BeforeEach
	void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		server.createContext("/fred/release/dates", exchange -> {
			Map<String, String> query = new HashMap<>();
			for (String pair : exchange.getRequestURI().getRawQuery().split("&")) {
				String[] kv = pair.split("=", 2);
				query.put(kv[0], URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
			}
			queries.add(query);
			if (location != null) {
				exchange.getResponseHeaders().add("Location", location);
			}
			byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
			if (bytes.length > 0) {
				exchange.getResponseBody().write(bytes);
			}
			exchange.close();
		});
		server.start();
	}

	@AfterEach
	void stop() {
		server.stop(0);
	}

	private FredClient client(String key) {
		return new FredClient(key, "http://localhost:" + server.getAddress().getPort() + "/");
	}

	@Test
	@DisplayName("asks for every date of the release from 2024 on, the announced future ones included, and reads them in order")
	void dates() {
		body = """
			{"realtime_start":"2024-01-01","count":3,"release_dates":[
			  {"release_id":10,"date":"2026-10-14"},{"release_id":10,"date":"2026-11-10"},{"release_id":10,"date":"2026-12-10"}]}""";
		assertThat(client(KEY).releaseDates(10))
			.containsExactly(LocalDate.of(2026, 10, 14), LocalDate.of(2026, 11, 10), LocalDate.of(2026, 12, 10));
		Map<String, String> query = queries.getFirst();
		assertThat(query).containsEntry("release_id", "10")
			.containsEntry("realtime_start", "2024-01-01")
			.containsEntry("include_release_dates_with_no_data", "true")
			.containsEntry("file_type", "json")
			.containsEntry("api_key", KEY);
	}

	@Test
	@DisplayName("a refusal, an unreadable answer, or a server that is down names the release and the reason — never the key")
	void failuresKeepTheKeyOut() {
		status = 400;
		body = "{\"error_message\":\"Bad Request. The value for variable api_key is not registered.\"}";
		assertThatThrownBy(() -> client(KEY).releaseDates(10))
			.isInstanceOf(FredException.class)
			.hasMessage("FRED: release 10: HTTP 400")
			.hasNoCause();

		status = 200;
		body = "not json";
		assertThatThrownBy(() -> client(KEY).releaseDates(50)).hasMessage("FRED: release 50: unreadable answer");

		FredClient down = new FredClient(KEY, "http://localhost:1");
		assertThatThrownBy(() -> down.releaseDates(53))
			.isInstanceOf(FredException.class)
			.hasMessageStartingWith("FRED: release 53: ")
			.hasMessageNotContaining(KEY)
			.hasNoCause();
		assertThat(client(KEY).toString()).doesNotContain(KEY).contains("(set)");
	}

	@Test
	@DisplayName("does not follow a redirect: the key goes only to the configured host")
	void noRedirects() {
		status = 302;
		location = "http://example.com/steal";
		assertThatThrownBy(() -> client(KEY).releaseDates(10)).hasMessage("FRED: release 10: HTTP 302");
		assertThat(queries).hasSize(1);
	}

	@Test
	@DisplayName("without a key it asks nothing and says what is missing")
	void noKey() {
		assertThat(client(" ").hasApiKey()).isFalse();
		assertThatThrownBy(() -> client("").releaseDates(10)).hasMessage("FRED: FRED_API_KEY is not set");
		assertThat(queries).isEmpty();
	}
}
