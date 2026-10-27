package com.tradeez.curriculum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tradeez.curriculum.ConceptRegistry.Concept;

/** T45 (R-15): every page Learn teaches has its words, in the lesson template's shape. */
class LessonTextsTest {

	private static String read(String path) throws IOException {
		try (InputStream in = LessonTextsTest.class.getClassLoader().getResourceAsStream(path)) {
			return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	@Test
	@DisplayName("every lesson and reference card has a text with every part of the template; nothing else has one")
	void everyPage() throws IOException {
		ConceptRegistry.Curriculum curriculum = ConceptRegistry.parse(read(ConceptRegistry.SOURCE));
		for (Concept c : curriculum.concepts()) {
			String text = read(LessonTexts.FOLDER + c.id() + ".md");
			if (c.taught()) {
				assertThat(text).as(c.id()).isNotNull();
				LessonTexts.Text parsed = LessonTexts.parse(c.id(), text);
				assertThat(parsed.summary()).as(c.id()).isNotBlank().doesNotContain("**");
				assertThat(parsed.howToSpot()).as(c.id()).isNotEmpty().allSatisfy(b -> assertThat(b).doesNotStartWith("-"));
			} else {
				assertThat(text).as(c.id() + " is not a page").isNull();
			}
		}
	}

	@Test
	@DisplayName("a text missing its summary or a section is refused, by name")
	void refuses() {
		String good = "> One line.\n\n## What it is\nA thing.\n\n## How to spot it\n- Look.\n\n## How it is traded\nLike so.\n";
		assertThat(LessonTexts.parse("ICT-01", good).howToSpot()).containsExactly("Look.");
		assertThatThrownBy(() -> LessonTexts.parse("ICT-01", good.replace("> One line.", ""))).hasMessageContaining("ICT-01");
		assertThatThrownBy(() -> LessonTexts.parse("ICT-01", good.replace("## How to spot it", "## Spotting")))
			.hasMessageContaining("How to spot it");
	}
}
