package com.tradeez.marketdata.massive;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * A stand-in for Massive's futures aggregates endpoint, so tests never use the free plan's requests.
 *
 * <p>Behaves like the real API where Tradeez depends on it (checked against real responses in
 * session 017): session bars are indexed by the date the session <i>opens</i> and carry their trade
 * date in {@code session_end_date}; minute bars have nanosecond {@code window_start}; prices are JSON
 * decimals; long results are paged with a {@code next_url} that does not contain the key.
 *
 * <p>Minute bars exist for every minute of every session a contract has a session row for, CME
 * hours (17:00–16:00 Chicago). Prices are predictable: {@code base(ticker) + (minute % 100)} ticks of
 * 0.25, so a test can compute any bar, and each contract trades at a different level, so a roll is a
 * visible jump.
 */
public final class FakeMassive implements AutoCloseable {

	static final ZoneId CHICAGO = ZoneId.of("America/Chicago");
	static final BigDecimal TICK = new BigDecimal("0.25");

	public record Request(String ticker, Map<String, String> query, String authorization,
			String rawQuery) {
	}

	public final List<Request> requests = new CopyOnWriteArrayList<>();

	/** Trade date → volume, per ticker. Tickers without rows have no sessions at all. */
	public final Map<String, Map<LocalDate, Long>> sessions = new ConcurrentHashMap<>();

	/** Base price in ticks per ticker; others default to 80,000. */
	public final Map<String, Long> basePrice = new ConcurrentHashMap<>();

	public volatile int status = 200;
	public final AtomicInteger tooManyRequests = new AtomicInteger();
	public volatile String nextUrlOverride;
	public volatile Function<Request, String> minuteBody;

	private final HttpServer server;

	public FakeMassive() {
		try {
			server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		server.createContext("/futures/v1/aggs/", this::aggs);
		server.start();
	}

	public String url() {
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	public void reset() {
		requests.clear();
		sessions.clear();
		basePrice.clear();
		status = 200;
		tooManyRequests.set(0);
		nextUrlOverride = null;
		minuteBody = null;
	}

	public List<Request> requests(String resolution) {
		return requests.stream().filter(r -> resolution.equals(r.query().get("resolution"))).toList();
	}

	/** The price, in ticks, of the bar opening at {@code openTime} for {@code ticker}. */
	public long openTicks(String ticker, Instant openTime) {
		return basePrice.getOrDefault(ticker, 80_000L) + (openTime.getEpochSecond() / 60) % 100;
	}

	private void aggs(HttpExchange exchange) throws IOException {
		String ticker = exchange.getRequestURI().getPath().substring("/futures/v1/aggs/".length());
		String rawQuery = exchange.getRequestURI().getRawQuery();
		Map<String, String> query = parse(rawQuery);
		Request request = new Request(ticker, query,
			exchange.getRequestHeaders().getFirst("Authorization"), rawQuery);
		requests.add(request);

		if (tooManyRequests.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
			exchange.getResponseHeaders().add("Retry-After", "0");
			respond(exchange, 429, "{\"status\":\"ERROR\",\"error\":\"too many requests\"}");
			return;
		}
		if (status >= 400) {
			respond(exchange, status, "{\"status\":\"ERROR\",\"request_id\":\"x\",\"error\":\"fake refusal\"}");
			return;
		}
		if ("1min".equals(query.get("resolution")) && minuteBody != null) {
			respond(exchange, 200, minuteBody.apply(request));
			return;
		}

		List<String> rows = "1session".equals(query.get("resolution"))
			? sessionRows(ticker, query)
			: minuteRows(ticker, query);

		int limit = Integer.parseInt(query.getOrDefault("limit", "1000"));
		int offset = Integer.parseInt(query.getOrDefault("cursor", "0"));
		int end = Math.min(rows.size(), offset + limit);
		StringBuilder body = new StringBuilder("{\"results\":[");
		body.append(String.join(",", rows.subList(Math.min(offset, rows.size()), end)));
		body.append("],\"status\":\"OK\",\"request_id\":\"fake\"");
		if (nextUrlOverride != null) {
			body.append(",\"next_url\":\"").append(nextUrlOverride).append('"');
		} else if (end < rows.size()) {
			String rest = rawQuery.replaceAll("(^|&)cursor=\\d+", "");
			body.append(",\"next_url\":\"").append(url()).append("/futures/v1/aggs/").append(ticker)
				.append("?cursor=").append(end).append(rest.startsWith("&") ? rest : "&" + rest)
				.append('"');
		}
		body.append('}');
		respond(exchange, 200, body.toString());
	}

	private List<String> sessionRows(String ticker, Map<String, String> query) {
		LocalDate gte = LocalDate.parse(query.get("window_start.gte"));
		LocalDate lt = LocalDate.parse(query.get("window_start.lt"));
		List<String> rows = new ArrayList<>();
		sessions.getOrDefault(ticker, Map.of()).entrySet().stream()
			.sorted(Map.Entry.comparingByKey())
			.forEach(e -> {
				LocalDate openDate = e.getKey().minusDays(1);
				if (!openDate.isBefore(gte) && openDate.isBefore(lt)) {
					long windowStart = openDate.atStartOfDay(ZoneId.of("UTC")).toEpochSecond()
						* 1_000_000_000L;
					rows.add("{\"ticker\":\"" + ticker + "\",\"window_start\":" + windowStart
						+ ",\"session_end_date\":\"" + e.getKey() + "\",\"open\":20000,\"high\":20100.5,"
						+ "\"low\":19900.25,\"close\":20050,\"transactions\":10,\"volume\":"
						+ e.getValue() + ",\"dollar_volume\":1.5,\"settlement_price\":20050.25}");
				}
			});
		return rows;
	}

	private List<String> minuteRows(String ticker, Map<String, String> query) {
		long gte = Long.parseLong(query.get("window_start.gte"));
		long lt = Long.parseLong(query.get("window_start.lt"));
		Map<LocalDate, Long> days = sessions.getOrDefault(ticker, Map.of());
		List<String> rows = new ArrayList<>();
		for (long second = ceilMinute(gte / 1_000_000_000L); second * 1_000_000_000L < lt; second += 60) {
			Instant t = Instant.ofEpochSecond(second);
			var local = t.atZone(CHICAGO);
			LocalDate tradeDate = local.toLocalTime().isBefore(LocalTime.of(17, 0))
				? local.toLocalDate() : local.toLocalDate().plusDays(1);
			boolean inSession = local.toLocalTime().isBefore(LocalTime.of(16, 0))
				|| !local.toLocalTime().isBefore(LocalTime.of(17, 0));
			if (!inSession || !days.containsKey(tradeDate)) {
				continue;
			}
			long base = openTicks(ticker, t);
			rows.add("{\"ticker\":\"" + ticker + "\",\"window_start\":" + second * 1_000_000_000L
				+ ",\"session_end_date\":\"" + tradeDate + "\",\"open\":" + price(base)
				+ ",\"high\":" + price(base + 3) + ",\"low\":" + price(base - 2)
				+ ",\"close\":" + price(base + 1) + ",\"transactions\":3,\"volume\":" + (5 + second % 7)
				+ ",\"dollar_volume\":100.5}");
		}
		return rows;
	}

	/** Ticks as a JSON decimal the way Massive writes it: 20000, 20000.25, 20000.5. */
	static String price(long ticks) {
		return TICK.multiply(BigDecimal.valueOf(ticks)).stripTrailingZeros().toPlainString();
	}

	private static long ceilMinute(long second) {
		return second % 60 == 0 ? second : second + (60 - Math.floorMod(second, 60));
	}

	private static Map<String, String> parse(String rawQuery) {
		Map<String, String> out = new LinkedHashMap<>();
		if (rawQuery == null) {
			return out;
		}
		for (String pair : rawQuery.split("&")) {
			if (pair.isEmpty()) {
				continue;
			}
			String[] kv = pair.split("=", 2);
			out.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
				kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
		}
		return out;
	}

	private static void respond(HttpExchange exchange, int code, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		exchange.sendResponseHeaders(code, bytes.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(bytes);
		}
	}

	@Override
	public void close() {
		server.stop(0);
	}
}
