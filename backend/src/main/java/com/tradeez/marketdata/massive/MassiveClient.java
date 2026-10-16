package com.tradeez.marketdata.massive;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * The Massive (formerly Polygon.io) futures calls Tradeez needs: daily session bars, for choosing
 * the front contract, and one-minute bars (DEC-042).
 *
 * <p>Plain HTTP, like the Databento client: two GET requests do not justify an SDK, and a
 * hand-written client keeps where the key goes visible in one file. The key travels only in the
 * {@code Authorization: Bearer} header, never in a URL — so it cannot appear in a server log, a
 * {@code next_url}, or an error message.
 *
 * <p>Prices arrive as JSON decimals (21669.25). They are read as {@link BigDecimal}, never as
 * {@code double}, so the conversion to ticks is exact (INVARIANT 3).
 *
 * <p>Massive pages long results with a {@code next_url}. It is followed only if it points at the
 * configured host: the Authorization header goes wherever the request goes, and a response must
 * never be able to send the key somewhere else.
 */
@Component
public class MassiveClient {

	private static final Pattern TICKER = Pattern.compile("[A-Z0-9]{2,12}");
	private static final int MAX_RETRIES = 3;
	private static final Duration DEFAULT_BACKOFF = Duration.ofSeconds(60);
	private static final Duration MAX_BACKOFF = Duration.ofSeconds(120);

	private final MassiveSettings settings;
	private final RequestPacer pacer;
	private final HttpClient http = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(20))
		.followRedirects(HttpClient.Redirect.NEVER)
		.build();
	private final ObjectMapper json = JsonMapper.builder()
		.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
		.build();

	@Autowired
	public MassiveClient(MassiveSettings settings) {
		this(settings, RequestPacer.realTime(settings.requestsPerMinute()));
	}

	MassiveClient(MassiveSettings settings, RequestPacer pacer) {
		this.settings = settings;
		this.pacer = pacer;
	}

	/** One trading session of one contract: what the roll decision needs. */
	public record SessionBar(String ticker, LocalDate tradeDate, long volume) {
	}

	/** One one-minute bar exactly as Massive sent it. */
	public record RawBar(String ticker, long windowStartNanos, LocalDate sessionEndDate,
			BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, long volume) {
	}

	/**
	 * Session bars for one contract. Massive indexes a session by the date it <i>opens</i> (the
	 * evening before its trade date), so the range is in opening dates: {@code [fromOpenDate,
	 * toOpenDate)}. Each bar carries its trade date ({@code session_end_date}).
	 */
	public List<SessionBar> sessions(String ticker, LocalDate fromOpenDate, LocalDate toOpenDate) {
		List<SessionBar> out = new ArrayList<>();
		pages(aggsUrl(ticker, "1session", fromOpenDate.toString(), toOpenDate.toString()), r -> {
			SessionBar bar;
			try {
				bar = new SessionBar(text(r, "ticker"), LocalDate.parse(text(r, "session_end_date")),
					integer(r, "volume"));
			} catch (DateTimeParseException e) {
				throw new MassiveException("Unreadable Massive session bar: " + abbreviate(r.toString()));
			}
			out.add(bar);
		});
		return out;
	}

	/**
	 * Streams one-minute bars of one contract with {@code from <= window_start < to} to
	 * {@code sink}, following every page.
	 *
	 * @return how many bars were delivered
	 */
	public long minuteBars(String ticker, Instant from, Instant to, Consumer<RawBar> sink) {
		long[] count = { 0 };
		pages(aggsUrl(ticker, "1min", String.valueOf(nanos(from)), String.valueOf(nanos(to))), r -> {
			RawBar bar = parseBar(r);
			// Outside any try on purpose: the consumer's own failure — an off-grid price, say — must
			// reach the caller as itself, not disguised as a parsing problem (the Databento lesson).
			sink.accept(bar);
			count[0]++;
		});
		return count[0];
	}

	static RawBar parseBar(JsonNode r) {
		try {
			return new RawBar(text(r, "ticker"), integer(r, "window_start"),
				LocalDate.parse(text(r, "session_end_date")),
				decimal(r, "open"), decimal(r, "high"), decimal(r, "low"), decimal(r, "close"),
				integer(r, "volume"));
		} catch (DateTimeParseException e) {
			throw new MassiveException("Unreadable Massive bar: " + abbreviate(r.toString()));
		}
	}

	private void pages(String firstUrl, Consumer<JsonNode> each) {
		String url = firstUrl;
		while (url != null) {
			JsonNode body = get(url);
			JsonNode results = body.path("results");
			if (results.isArray()) {
				for (JsonNode r : results) {
					each.accept(r);
				}
			}
			url = body.hasNonNull("next_url") && !body.get("next_url").asString().isBlank()
				? sameHost(body.get("next_url").asString())
				: null;
		}
	}

	private String sameHost(String nextUrl) {
		URI next;
		try {
			next = URI.create(nextUrl);
		} catch (IllegalArgumentException e) {
			throw new MassiveException("Massive sent an unreadable next_url");
		}
		URI base = URI.create(settings.baseUrl());
		if (next.getScheme() == null || next.getHost() == null
				|| !next.getScheme().equalsIgnoreCase(base.getScheme())
				|| !next.getHost().equalsIgnoreCase(base.getHost())
				|| port(next) != port(base)) {
			throw new MassiveException("Refusing to follow a next_url to another host ("
				+ next.getHost() + "); the API key would go with it.");
		}
		return nextUrl;
	}

	private static int port(URI uri) {
		if (uri.getPort() != -1) {
			return uri.getPort();
		}
		return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
	}

	private String aggsUrl(String ticker, String resolution, String gte, String lt) {
		if (!TICKER.matcher(ticker).matches()) {
			throw new IllegalArgumentException("Not a futures ticker: " + ticker);
		}
		return settings.baseUrl() + "/futures/v1/aggs/" + ticker
			+ "?resolution=" + resolution
			+ "&window_start.gte=" + enc(gte)
			+ "&window_start.lt=" + enc(lt)
			+ "&limit=" + settings.pageLimit()
			+ "&sort=window_start.asc";
	}

	private JsonNode get(String url) {
		if (!settings.hasApiKey()) {
			throw new IllegalStateException("MASSIVE_API_KEY is not set. Put it in backend/.env "
				+ "(see backend/.env.example); that file is gitignored.");
		}
		URI uri = URI.create(url);
		HttpRequest request = HttpRequest.newBuilder(uri)
			.timeout(Duration.ofMinutes(2))
			.header("Authorization", "Bearer " + settings.apiKey())
			.header("Accept", "application/json")
			.GET()
			.build();
		for (int attempt = 0; ; attempt++) {
			pacer.acquire();
			HttpResponse<String> response;
			try {
				response = http.send(request, HttpResponse.BodyHandlers.ofString());
			} catch (IOException e) {
				throw new MassiveException("Could not reach Massive: " + e.getClass().getSimpleName());
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new MassiveException("Interrupted while calling Massive");
			}
			int code = response.statusCode();
			if (code == 429 && attempt < MAX_RETRIES) {
				pacer.backOff(retryAfter(response));
				continue;
			}
			if (code >= 400) {
				throw new MassiveException("Massive returned HTTP " + code + " for " + uri.getPath()
					+ ": " + abbreviate(errorText(response.body())));
			}
			JsonNode body;
			try {
				body = json.readTree(response.body());
			} catch (JacksonException e) {
				throw new MassiveException("Massive sent something that is not JSON for "
					+ uri.getPath() + ": " + abbreviate(response.body()));
			}
			String status = body.path("status").asString("");
			if (!"OK".equals(status) && !"DELAYED".equals(status)) {
				throw new MassiveException("Massive answered status '" + status + "' for "
					+ uri.getPath() + ": " + abbreviate(errorText(response.body())));
			}
			return body;
		}
	}

	private static Duration retryAfter(HttpResponse<?> response) {
		return response.headers().firstValue("Retry-After")
			.map(value -> {
				try {
					Duration d = Duration.ofSeconds(Long.parseLong(value.trim()));
					return d.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : d;
				} catch (NumberFormatException e) {
					return DEFAULT_BACKOFF;
				}
			})
			.orElse(DEFAULT_BACKOFF);
	}

	private String errorText(String body) {
		try {
			JsonNode node = json.readTree(body);
			if (node.hasNonNull("error")) {
				return node.get("error").asString();
			}
			if (node.hasNonNull("message")) {
				return node.get("message").asString();
			}
		} catch (JacksonException e) {
			// not JSON — fall through to the raw text
		}
		return body;
	}

	private static String text(JsonNode r, String field) {
		JsonNode n = r.get(field);
		if (n == null || !n.isTextual()) {
			throw new MassiveException("Massive bar has no '" + field + "': " + abbreviate(r.toString()));
		}
		return n.asString();
	}

	private static long integer(JsonNode r, String field) {
		JsonNode n = r.get(field);
		if (n == null || !n.isIntegralNumber() || !n.canConvertToLong()) {
			throw new MassiveException("Massive bar has no whole-number '" + field + "': "
				+ abbreviate(r.toString()));
		}
		return n.asLong();
	}

	private static BigDecimal decimal(JsonNode r, String field) {
		JsonNode n = r.get(field);
		if (n == null || !n.isNumber()) {
			throw new MassiveException("Massive bar has no number '" + field + "': "
				+ abbreviate(r.toString()));
		}
		return n.decimalValue();
	}

	private static long nanos(Instant t) {
		return Math.addExact(Math.multiplyExact(t.getEpochSecond(), 1_000_000_000L), t.getNano());
	}

	private static String enc(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private static String abbreviate(String text) {
		String single = text == null ? "" : text.replaceAll("\\s+", " ").trim();
		return single.length() <= 300 ? single : single.substring(0, 300) + "…";
	}

	/** Anything that went wrong talking to Massive. Messages never contain the API key. */
	public static class MassiveException extends RuntimeException {
		public MassiveException(String message) {
			super(message);
		}
	}
}
