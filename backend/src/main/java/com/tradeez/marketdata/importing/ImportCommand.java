package com.tradeez.marketdata.importing;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.tradeez.marketdata.Instrument;
import com.tradeez.marketdata.MarketDataService;

/**
 * Runs an import from the command line, then exits.
 *
 * <p>Does nothing unless {@code tradeez.import.symbol} is set, so the normal application never
 * imports on its own. Always start with a dry run — it reports what would be downloaded (and, for
 * Databento, prices it) without downloading:
 *
 * <pre>
 * ./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.main.web-application-type=none
 *     --tradeez.import.source=massive --tradeez.import.symbol=NQ --tradeez.import.resolution=1m
 *     --tradeez.import.from=2024-10-07 --tradeez.import.to=2026-10-03 --tradeez.import.dry-run=true"
 * </pre>
 *
 * Dates are UTC days; {@code to} is exclusive. The source defaults to {@code massive} (DEC-042).
 */
@Component
public class ImportCommand implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(ImportCommand.class);

	static final String DEFAULT_SOURCE = "massive";

	private final Environment env;
	private final List<MarketDataImporter> importers;
	private final MarketDataService marketData;
	private final ApplicationContext context;

	public ImportCommand(Environment env, List<MarketDataImporter> importers,
			MarketDataService marketData, ApplicationContext context) {
		this.env = env;
		this.importers = importers;
		this.marketData = marketData;
		this.context = context;
	}

	@Override
	public void run(ApplicationArguments args) {
		String symbol = env.getProperty("tradeez.import.symbol");
		if (symbol == null || symbol.isBlank()) {
			return;
		}
		int exitCode = 0;
		try {
			MarketDataImporter importer =
				select(importers, env.getProperty("tradeez.import.source", DEFAULT_SOURCE));
			Instrument instrument = marketData.requireInstrument(symbol);
			String resolution = env.getProperty("tradeez.import.resolution", "1m");
			Instant from = day(env.getRequiredProperty("tradeez.import.from"));
			Instant to = day(env.getRequiredProperty("tradeez.import.to"));
			boolean dryRun = Boolean.parseBoolean(env.getProperty("tradeez.import.dry-run", "true"));

			ImportReport report = importer.importRange(instrument, resolution, from, to, dryRun);
			log.info("{} {} {} {} {} → {}: {} chunks, estimated ${}, {} bars stored",
				dryRun ? "DRY RUN" : "IMPORT", report.source(), symbol, resolution, from, to,
				report.chunks().size(), report.costUsd(), report.barsStored());
			report.chunks().forEach(c -> log.info("  {} → {}  {}  ${}  {} bars  {}",
				c.from(), c.to(), c.outcome(), c.costUsd(), c.barsStored(),
				c.detail() == null ? "" : c.detail()));
		} catch (RuntimeException e) {
			log.error("Import stopped: {}", e.getMessage());
			exitCode = 1;
		}
		int code = exitCode;
		System.exit(SpringApplication.exit(context, (ExitCodeGenerator) () -> code));
	}

	/** The importer for {@code source}, or a message naming the ones that exist. */
	static MarketDataImporter select(List<MarketDataImporter> importers, String source) {
		return importers.stream()
			.filter(i -> i.source().equalsIgnoreCase(source.trim()))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("Unknown import source '" + source
				+ "'. Known: " + importers.stream().map(MarketDataImporter::source).sorted()
					.collect(Collectors.joining(", "))));
	}

	private static Instant day(String iso) {
		return LocalDate.parse(iso).atStartOfDay(ZoneOffset.UTC).toInstant();
	}
}
