package com.tradeez.curriculum;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * The words of each lesson (T45, R-15): one Markdown file per lesson and reference card in {@code lessons/{id}.md},
 * in plain trader language, Fabio's to review. The file's shape is the lesson's template:
 *
 * <pre>
 * &gt; The one-line summary.
 *
 * ## What it is
 * Two or three plain sentences.
 *
 * ## How to spot it
 * - what to look for on the chart, one bullet each
 *
 * ## How it is traded
 * Entry, stop and target in plain words.
 * </pre>
 *
 * The start fails when a page Learn teaches has no file, or a file lacks a part of the template.
 */
@Component
public class LessonTexts {

	static final String FOLDER = "lessons/";

	/** A lesson's words, as the template sections them. */
	public record Text(String summary, String whatItIs, List<String> howToSpot, String howToTrade) {
	}

	private final Map<String, Text> texts = new LinkedHashMap<>();

	public LessonTexts(ConceptRegistry concepts) {
		for (ConceptRegistry.Concept concept : concepts.concepts()) {
			if (concept.taught()) {
				texts.put(concept.id(), parse(concept.id(), read(concept.id())));
			}
		}
	}

	/** The page's words; null for a concept Learn does not teach as a page. */
	public Text of(String id) {
		return texts.get(id);
	}

	private static String read(String id) {
		try (InputStream in = LessonTexts.class.getClassLoader().getResourceAsStream(FOLDER + id + ".md")) {
			if (in == null) {
				throw new IllegalStateException(id + " is taught but has no " + FOLDER + id + ".md");
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new IllegalStateException("Could not read " + FOLDER + id + ".md", e);
		}
	}

	static Text parse(String id, String markdown) {
		String summary = null;
		Map<String, List<String>> sections = new LinkedHashMap<>();
		List<String> current = null;
		for (String raw : markdown.split("\\R")) {
			String line = raw.strip();
			if (line.startsWith("> ") && summary == null && current == null) {
				summary = line.substring(2).strip();
			} else if (line.startsWith("## ")) {
				current = new ArrayList<>();
				sections.put(line.substring(3).strip(), current);
			} else if (current != null && !line.isEmpty()) {
				current.add(line);
			}
		}
		List<String> what = sections.get("What it is");
		List<String> spot = sections.get("How to spot it");
		List<String> trade = sections.get("How it is traded");
		if (summary == null || what == null || spot == null || trade == null || what.isEmpty() || spot.isEmpty()
				|| trade.isEmpty()) {
			throw new IllegalStateException(FOLDER + id + ".md needs a '> ' summary and the sections 'What it is', "
				+ "'How to spot it' and 'How it is traded'");
		}
		List<String> bullets = spot.stream().map(l -> l.startsWith("- ") ? l.substring(2).strip() : l).toList();
		return new Text(summary, String.join(" ", what), bullets, String.join(" ", trade));
	}
}
