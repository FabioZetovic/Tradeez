package com.tradeez.curriculum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tradeez.analysis.Detection;
import com.tradeez.analysis.Detector;
import com.tradeez.analysis.FvgDetector;
import com.tradeez.curriculum.ConceptRegistry.Concept;
import com.tradeez.curriculum.ConceptRegistry.Curriculum;
import com.tradeez.curriculum.ConceptRegistry.Status;
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Timeframe;

/**
 * S-2 — the concept registry is ICT_CONCEPTS.md read as data. These pin the document's shape: if it
 * changes in a way the reader no longer understands, this fails rather than the app teaching a
 * shorter curriculum without anyone noticing.
 */
class ConceptRegistryTest {

	private static String document;
	private static Curriculum curriculum;

	@BeforeAll
	static void read() throws IOException {
		try (InputStream in = ConceptRegistryTest.class.getClassLoader().getResourceAsStream(ConceptRegistry.SOURCE)) {
			document = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		curriculum = ConceptRegistry.parse(document);
	}

	private static Concept concept(String id) {
		return curriculum.concepts().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
	}

	@Test
	@DisplayName("every row of the master table, in order, each with its summary")
	void everyConcept() {
		long rows = document.lines().filter(l -> l.matches("^\\| (F-\\d|ICT-\\d{2}) \\|.*")).count();
		List<Concept> concepts = curriculum.concepts();
		assertThat(concepts).hasSize(72);
		assertThat(rows).isGreaterThanOrEqualTo(concepts.size());
		assertThat(concepts.getFirst().id()).isEqualTo("F-1");
		assertThat(concepts.getLast().id()).isEqualTo("ICT-84");
		assertThat(concepts).extracting(Concept::id).doesNotHaveDuplicates();
		assertThat(concepts).allSatisfy(c -> {
			assertThat(c.summary()).isNotBlank().doesNotContain("**").doesNotContain("`");
			assertThat(c.name()).isNotBlank();
		});
	}

	@Test
	@DisplayName("the FVG reads as the document states it")
	void fairValueGap() {
		Concept fvg = concept("ICT-20");
		assertThat(fvg.name()).isEqualTo("Fair value gap (BISI / SIBI)");
		assertThat(fvg.level()).isEqualTo(3);
		assertThat(fvg.detectability()).isEqualTo("D");
		assertThat(fvg.status()).isEqualTo(Status.IMPLEMENTED);
		assertThat(fvg.rules()).containsExactly("fvg-1.0.0");
		assertThat(fvg.summary()).startsWith("Three consecutive candles where the middle candle moved");
		assertThat(concept("ICT-67").status()).isEqualTo(Status.OUT_OF_SCOPE);
		assertThat(concept("ICT-01").status()).isEqualTo(Status.IMPLEMENTED);
		assertThat(concept("ICT-10").status()).isEqualTo(Status.DRAFTED);
		assertThat(concept("ICT-15").status()).isEqualTo(Status.DESCRIBED);
		assertThat(concept("F-4").statusNote()).isEqualTo("partly (New York time axis, T3)");
	}

	@Test
	@DisplayName("prerequisites are the earlier concepts a section refers to, never later ones")
	void prerequisites() {
		assertThat(concept("ICT-70").prerequisites())
			.contains("ICT-04", "ICT-05", "ICT-13", "ICT-20", "ICT-52")
			.doesNotContain("ICT-70", "ICT-71");
		// ICT-01 mentions liquidity (ICT-10) and MSS (ICT-05), both taught after it.
		assertThat(concept("ICT-01").prerequisites()).isEmpty();
	}

	@Test
	@DisplayName("the ladder has levels 0 to 9, and every concept sits on one")
	void ladder() {
		assertThat(curriculum.levels()).extracting(ConceptRegistry.Level::level)
			.containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
		assertThat(curriculum.levels().get(3).theme()).isEqualTo("Imbalances and gaps");
		assertThat(curriculum.levels().get(6).unlocksWhen()).isEqualTo("always open — it is reference knowledge");
	}

	@Test
	@DisplayName("a detector must run a rule the document lists for its concept")
	void detectorsMatchTheDocument() {
		ConceptRegistry.check(curriculum, List.of(new FvgDetector()));
		assertThatThrownBy(() -> ConceptRegistry.check(curriculum, List.of(fake("ICT-20", "fvg-9.9.9"))))
			.hasMessageContaining("fvg-9.9.9");
		assertThatThrownBy(() -> ConceptRegistry.check(curriculum, List.of(fake("ICT-99", "fvg-1.0.0"))))
			.hasMessageContaining("ICT-99");
	}

	@Test
	@DisplayName("a document that loses a concept's summary or its master table is refused")
	void refusesAnUnreadableDocument() {
		assertThatThrownBy(() -> ConceptRegistry.parse(document.replace("**What.** Three consecutive", "Three consecutive")))
			.hasMessageContaining("ICT-20");
		assertThatThrownBy(() -> ConceptRegistry.parse(document.replace("## Master table", "## Table")))
			.hasMessageContaining("Master table");
	}

	private static Detector fake(String conceptId, String ruleVersion) {
		return new Detector() {
			public String id() {
				return "FAKE";
			}

			public String ruleVersion() {
				return ruleVersion;
			}

			public String methodologySource() {
				return "test";
			}

			public String conceptId() {
				return conceptId;
			}

			public Map<String, Object> parameters() {
				return Map.of();
			}

			public int lookbackBars(Timeframe timeframe) {
				return 1;
			}

			public List<Detection> detect(List<Bar> segment, Timeframe timeframe) {
				return List.of();
			}
		};
	}
}
