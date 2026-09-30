package com.tradeez.marketdata.databento;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

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
 * imports on its own. Always start with a dry run — it prices the whole range and downloads nothing:
 *
 * <pre>
 * ./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.main.web-application-type=none
 *     --tradeez.import.symbol=NQ --tradeez.import.resolution=1m
 *     --tradeez.import.from=2016-01-01 --tradeez.import.to=2026-09-30 --tradeez.import.dry-run=true"
 * </pre>
 *
 * Dates are UTC days; {@code to} is exclusive.
 */
@Component
public class ImportCommand implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(ImportCommand.class);

	private final Environment env;
	private final DatabentoImporter importer;
	private final MarketDataService marketData;
	private final ApplicationContext context;

	public ImportCommand(Environment env, DatabentoImporter importer, MarketDataService marketData,
			ApplicationContext context) {
		this.env = env;
		this.importer = importer;
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
			Instrument instrument = marketData.requireInstrument(symbol);
			String resolution = env.getProperty("tradeez.import.resolution", "1m");
			Instant from = day(env.getRequiredProperty("tradeez.import.from"));
			Instant to = day(env.getRequiredProperty("tradeez.import.to"));
			boolean dryRun = Boolean.parseBoolean(env.getProperty("tradeez.import.dry-run", "true"));

			DatabentoImporter.Report report =
				importer.importRange(instrument, resolution, from, to, dryRun);
			log.info("{} {} {} {} → {}: {} chunks, estimated ${}, {} bars stored",
				dryRun ? "DRY RUN" : "IMPORT", symbol, resolution, from, to,
				report.chunks().size(), report.costUsd(), report.barsStored());
			report.chunks().forEach(c -> log.info("  {} → {}  {}  ${}  {} bars",
				c.from(), c.to(), c.outcome(), c.costUsd(), c.barsStored()));
		} catch (RuntimeException e) {
			log.error("Import stopped: {}", e.getMessage());
			exitCode = 1;
		}
		int code = exitCode;
		System.exit(SpringApplication.exit(context, (ExitCodeGenerator) () -> code));
	}

	private static Instant day(String iso) {
		return LocalDate.parse(iso).atStartOfDay(ZoneOffset.UTC).toInstant();
	}
}
