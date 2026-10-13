package com.tradeez.marketdata.databento;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * A stand-in for Databento's Historical API, so tests never touch the real account or its credit.
 *
 * <p>Serves the two endpoints Tradeez calls. By default it prices every request at five cents and
 * answers a bar request with one-minute bars across the requested range, on the NQ tick grid.
 */
public final class FakeDatabento implements AutoCloseable {

	/** A 0.25-point tick in Databento's fixed-point units (1e-9). */
	public static final long TICK = 250_000_000L;

	public final List<Map<String, String>> costRequests = new CopyOnWriteArrayList<>();
	public final List<Map<String, String>> barRequests = new CopyOnWriteArrayList<>();
	public volatile String lastAuthorization;

	public volatile int status = 200;
	public volatile Function<Map<String, String>, BigDecimal> cost = form -> new BigDecimal("0.05");
	public volatile Function<Map<String, String>, String> csv = FakeDatabento::minuteBars;

	private final HttpServer server;

	public FakeDatabento() {
		try {
			server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		server.createContext("/v0/metadata.get_cost", exchange -> {
			Map<String, String> form = read(exchange);
			costRequests.add(form);
			respond(exchange, status < 400 ? cost.apply(form).toPlainString()
				: "{\"detail\":\"fake error\"}");
		});
		server.createContext("/v0/timeseries.get_range", exchange -> {
			Map<String, String> form = read(exchange);
			barRequests.add(form);
			respond(exchange, status < 400 ? csv.apply(form) : "{\"detail\":\"fake error\"}");
		});
		server.start();
	}

	public String url() {
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	public void reset() {
		costRequests.clear();
		barRequests.clear();
		lastAuthorization = null;
		status = 200;
		cost = form -> new BigDecimal("0.05");
		csv = FakeDatabento::minuteBars;
	}

	/**
	 * One bar per minute over the requested range. Prices climb one tick a minute from 80000 ticks,
	 * so every bar's values are predictable; the contract id changes halfway, like a roll.
	 */
	public static String minuteBars(Map<String, String> form) {
		Instant start = Instant.parse(form.get("start"));
		Instant end = Instant.parse(form.get("end"));
		StringBuilder out = new StringBuilder(
			"ts_event,rtype,publisher_id,instrument_id,open,high,low,close,volume\n");
		long minutes = (end.getEpochSecond() - start.getEpochSecond()) / 60;
		for (long i = 0; i < minutes; i++) {
			long second = start.getEpochSecond() + i * 60;
			long base = 80_000 + ((second / 60) % 1000);
			long contract = i < minutes / 2 ? 111 : 222;
			out.append(second).append("000000000,33,1,").append(contract).append(',')
				.append(base * TICK).append(',')
				.append((base + 3) * TICK).append(',')
				.append((base - 2) * TICK).append(',')
				.append((base + 1) * TICK).append(',')
				.append(10 + i).append('\n');
		}
		return out.toString();
	}

	private Map<String, String> read(HttpExchange exchange) throws IOException {
		lastAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
		String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
		Map<String, String> form = new LinkedHashMap<>();
		for (String pair : body.split("&")) {
			if (pair.isEmpty()) {
				continue;
			}
			String[] kv = pair.split("=", 2);
			form.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
				kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
		}
		return form;
	}

	private void respond(HttpExchange exchange, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.sendResponseHeaders(status, bytes.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(bytes);
		}
	}

	@Override
	public void close() {
		server.stop(0);
	}
}
