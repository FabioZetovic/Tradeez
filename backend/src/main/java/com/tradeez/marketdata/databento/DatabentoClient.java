package com.tradeez.marketdata.databento;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/**
 * The two Databento Historical API calls Tradeez needs: what a download would cost, and the
 * download itself.
 *
 * <p>Plain HTTP rather than Databento's SDK: two POST requests do not justify a dependency, and a
 * hand-written client makes the exact request — and where the key goes — visible in one file.
 * Requests follow Databento's own client ({@code databento-python}): form-encoded POSTs to
 * {@code /v0/metadata.get_cost} and {@code /v0/timeseries.get_range}, authenticated with HTTP Basic
 * using the API key as the username.
 *
 * <p>Bars are requested as uncompressed CSV with raw integers — prices as fixed-point values in
 * units of 1e-9, timestamps as nanoseconds since the epoch — so nothing is rounded on the way in
 * (INVARIANT 3). Columns are located by header name, not position.
 */
@Component
public class DatabentoClient {

	/** Continuous-contract symbology: {@code NQ.v.0} follows the front contract by volume. */
	static final String STYPE_IN = "continuous";

	private static final List<String> REQUIRED_COLUMNS =
		List.of("ts_event", "instrument_id", "open", "high", "low", "close", "volume");

	private final DatabentoSettings settings;
	private final HttpClient http = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(20))
		.build();

	public DatabentoClient(DatabentoSettings settings) {
		this.settings = settings;
	}

	/** One OHLCV bar exactly as Databento sent it. */
	public record RawBar(long tsEventNanos, long instrumentId, long open, long high, long low,
			long close, long volume) {
	}

	/** What Databento says the download would cost, in US dollars. Nothing is downloaded. */
	public BigDecimal cost(String symbol, String schema, Instant start, Instant end) {
		Map<String, String> form = baseForm(symbol, schema, start, end);
		HttpResponse<String> response = send("/v0/metadata.get_cost", form,
			HttpResponse.BodyHandlers.ofString());
		String body = response.body().trim();
		try {
			return new BigDecimal(body);
		} catch (NumberFormatException e) {
			throw new DatabentoException("Unexpected cost response: " + abbreviate(body));
		}
	}

	/**
	 * Streams bars to {@code sink} as they arrive, without holding the whole response in memory.
	 *
	 * @return how many bars were delivered
	 */
	public long bars(String symbol, String schema, Instant start, Instant end, Consumer<RawBar> sink) {
		Map<String, String> form = baseForm(symbol, schema, start, end);
		form.put("encoding", "csv");
		form.put("compression", "none");
		form.put("pretty_px", "false");
		form.put("pretty_ts", "false");
		form.put("map_symbols", "false");

		HttpResponse<InputStream> response = send("/v0/timeseries.get_range", form,
			HttpResponse.BodyHandlers.ofInputStream());
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
			return parse(reader, sink);
		} catch (IOException e) {
			throw new DatabentoException("Reading Databento's response failed: " + e.getMessage());
		}
	}

	static long parse(BufferedReader reader, Consumer<RawBar> sink) throws IOException {
		String header = reader.readLine();
		if (header == null) {
			return 0;
		}
		Map<String, Integer> column = new HashMap<>();
		String[] names = header.split(",", -1);
		for (int i = 0; i < names.length; i++) {
			column.put(names[i].trim(), i);
		}
		for (String required : REQUIRED_COLUMNS) {
			if (!column.containsKey(required)) {
				throw new DatabentoException("Databento's CSV has no '" + required
					+ "' column (header: " + abbreviate(header) + ")");
			}
		}

		long count = 0;
		String line;
		while ((line = reader.readLine()) != null) {
			if (line.isBlank()) {
				continue;
			}
			String[] f = line.split(",", -1);
			RawBar bar;
			try {
				bar = new RawBar(
					Long.parseLong(f[column.get("ts_event")].trim()),
					Long.parseLong(f[column.get("instrument_id")].trim()),
					Long.parseLong(f[column.get("open")].trim()),
					Long.parseLong(f[column.get("high")].trim()),
					Long.parseLong(f[column.get("low")].trim()),
					Long.parseLong(f[column.get("close")].trim()),
					Long.parseLong(f[column.get("volume")].trim()));
			} catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
				throw new DatabentoException("Unreadable Databento row " + (count + 1) + ": "
					+ abbreviate(line));
			}
			// Outside the try on purpose: a consumer's own failure — an off-grid price, say — must
			// reach the caller as itself, not disguised as a parsing problem.
			sink.accept(bar);
			count++;
		}
		return count;
	}

	private Map<String, String> baseForm(String symbol, String schema, Instant start, Instant end) {
		Map<String, String> form = new LinkedHashMap<>();
		form.put("dataset", settings.dataset());
		form.put("symbols", symbol);
		form.put("schema", schema);
		form.put("start", start.toString());
		form.put("end", end.toString());
		form.put("stype_in", STYPE_IN);
		return form;
	}

	private <T> HttpResponse<T> send(String path, Map<String, String> form,
			HttpResponse.BodyHandler<T> handler) {
		if (!settings.hasApiKey()) {
			throw new IllegalStateException("DATABENTO_API_KEY is not set. Put it in backend/.env "
				+ "(see backend/.env.example); the file is gitignored.");
		}
		String body = form.entrySet().stream()
			.map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
				+ URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
			.collect(Collectors.joining("&"));
		String auth = Base64.getEncoder().encodeToString(
			(settings.apiKey() + ":").getBytes(StandardCharsets.UTF_8));

		HttpRequest request = HttpRequest.newBuilder(URI.create(settings.baseUrl() + path))
			.timeout(Duration.ofMinutes(10))
			.header("Authorization", "Basic " + auth)
			.header("Content-Type", "application/x-www-form-urlencoded")
			.header("Accept", "*/*")
			.POST(HttpRequest.BodyPublishers.ofString(body))
			.build();
		try {
			HttpResponse<T> response = http.send(request, handler);
			if (response.statusCode() >= 400) {
				throw new DatabentoException("Databento returned HTTP " + response.statusCode()
					+ " for " + path + ": " + abbreviate(readError(response.body())));
			}
			return response;
		} catch (IOException e) {
			throw new DatabentoException("Could not reach Databento: " + e.getMessage());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new DatabentoException("Interrupted while calling Databento");
		}
	}

	private static String readError(Object body) {
		if (body instanceof String s) {
			return s;
		}
		if (body instanceof InputStream in) {
			try (in) {
				return new String(in.readNBytes(2000), StandardCharsets.UTF_8);
			} catch (IOException e) {
				return "(unreadable error body)";
			}
		}
		return String.valueOf(body);
	}

	private static String abbreviate(String text) {
		String single = text.replaceAll("\\s+", " ").trim();
		return single.length() <= 300 ? single : single.substring(0, 300) + "…";
	}

	/** Anything that went wrong talking to Databento. Messages never contain the API key. */
	public static class DatabentoException extends RuntimeException {
		public DatabentoException(String message) {
			super(message);
		}
	}
}
