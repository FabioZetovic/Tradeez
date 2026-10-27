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
import com.tradeez.curriculum.ConceptRegistry.Kind;
import com.tradeez.curriculum.ConceptRegistry.Status;
import com.tradeez.curriculum.ConceptRegistry.Topic;
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
		assertThat(concept("ICT-10").status()).isEqualTo(Status.IMPLEMENTED);
		assertThat(concept("ICT-30").status()).isEqualTo(Status.IMPLEMENTED);
		assertThat(concept("ICT-30").rules()).containsExactly("ob-1.0.0");
		assertThat(concept("ICT-40").status()).isEqualTo(Status.IMPLEMENTED);
		assertThat(concept("ICT-62").status()).isEqualTo(Status.IMPLEMENTED);
		assertThat(concept("ICT-57").rules()).containsExactly("ipda-1.0.0");
		assertThat(concept("ICT-70").status()).isEqualTo(Status.IMPLEMENTED);
		assertThat(concept("ICT-70").statusNote()).isEqualTo("partly (a checklist at the decision moment, DEC-066)");
		assertThat(concept("ICT-70").rules()).containsExactly("model-2022-1.0.0");
		assertThat(concept("ICT-55").status()).isEqualTo(Status.DRAFTED);
		assertThat(concept("ICT-29").status()).isEqualTo(Status.DESCRIBED);
		// Implemented as a question rather than a detector (DEC-058): listed with its rule, partly.
		assertThat(concept("ICT-16").status()).isEqualTo(Status.IMPLEMENTED);
		assertThat(concept("ICT-16").rules()).containsExactly("predict-1.0.0");
		assertThat(concept("ICT-16").statusNote()).isEqualTo("partly (a prediction drill, DEC-058)");
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
	@DisplayName("the topics (DEC-081): nine, in order, 25 lessons and 8 reference cards; the rest merged or removed")
	void topics() {
		assertThat(curriculum.topics()).extracting(Topic::title).containsExactly("Structure", "Liquidity",
			"Gaps & imbalances", "Premium & discount", "Blocks", "Bias", "Models", "Risk & management", "Time & sessions");
		assertThat(curriculum.topics().getFirst().concepts()).containsExactly("ICT-01", "ICT-03", "ICT-04", "ICT-06");
		assertThat(curriculum.topics().get(2).concepts()).startsWith("ICT-20", "ICT-23");
		assertThat(curriculum.concepts()).filteredOn(c -> c.kind() == Kind.LESSON).hasSize(25);
		assertThat(curriculum.concepts()).filteredOn(c -> c.kind() == Kind.REFERENCE).hasSize(8);
		assertThat(curriculum.concepts()).filteredOn(c -> c.kind() == Kind.MERGED).hasSize(26);
		assertThat(curriculum.concepts()).filteredOn(c -> c.kind() == Kind.REMOVED).hasSize(13);
		assertThat(concept("ICT-23")).extracting(Concept::topic, Concept::importance, Concept::taught)
			.containsExactly("Gaps & imbalances", "high", true);
		assertThat(concept("ICT-05")).extracting(Concept::kind, Concept::into, Concept::taught)
			.containsExactly(Kind.MERGED, "ICT-06", false);
		assertThat(concept("F-1")).extracting(Concept::kind, Concept::topic).containsExactly(Kind.REMOVED, "");
	}

	@Test
	@DisplayName("a concept left out of the topics, placed twice, or merged into a page that is not taught is refused")
	void refusesBadTopics() {
		assertThatThrownBy(() -> ConceptRegistry.parse(document.replace("| — | ICT-29 | removed | low | — |\n", "")
			.replace("| — | ICT-29 | removed | low | — |\r\n", "")))
			.hasMessageContaining("ICT-29 is not placed");
		assertThatThrownBy(() -> ConceptRegistry.parse(document.replace("| — | ICT-29 | removed | low | — |",
			"| — | ICT-29 | removed | low | — |\n| — | ICT-29 | removed | low | — |")))
			.hasMessageContaining("placed twice");
		assertThatThrownBy(() -> ConceptRegistry.parse(document.replace("| Structure | ICT-05 | merged | high | ICT-06 |",
			"| Structure | ICT-05 | merged | high | ICT-29 |")))
			.hasMessageContaining("ICT-05 is merged into 'ICT-29'");
		assertThatThrownBy(() -> ConceptRegistry.parse(document.replace("| Structure | ICT-01 | lesson |", "| Liquidity | ICT-01 | lesson |")))
			.hasMessageContaining("split in two");
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
