package com.tradeez.marketdata.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tradeez.marketdata.Instrument;

/** The command line picks an importer by name; Massive is the default (DEC-042). */
class ImportCommandTest {

	private static MarketDataImporter named(String name) {
		return new MarketDataImporter() {
			@Override
			public String source() {
				return name;
			}

			@Override
			public ImportReport importRange(Instrument instrument, String resolution, Instant from,
					Instant to, boolean dryRun) {
				throw new UnsupportedOperationException();
			}
		};
	}

	private final List<MarketDataImporter> importers = List.of(named("databento"), named("massive"));

	@Test
	@DisplayName("selects an importer by name, ignoring case and spaces")
	void selects() {
		assertThat(ImportCommand.select(importers, " Massive ").source()).isEqualTo("massive");
		assertThat(ImportCommand.select(importers, "databento").source()).isEqualTo("databento");
		assertThat(ImportCommand.DEFAULT_SOURCE).isEqualTo("massive");
	}

	@Test
	@DisplayName("an unknown source names the ones that exist")
	void unknown() {
		assertThatThrownBy(() -> ImportCommand.select(importers, "yahoo"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("yahoo")
			.hasMessageContaining("databento, massive");
	}
}
