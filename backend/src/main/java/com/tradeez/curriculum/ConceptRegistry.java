package com.tradeez.curriculum;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.tradeez.analysis.Detector;
import com.tradeez.analysis.DetectorRegistry;

/**
 * The curriculum of {@code docs/ICT_CONCEPTS.md} as data (T9, S-2, DEC-049).
 *
 * <p>The document is the only source: the build copies it onto the classpath and this reads it at
 * startup, so the app cannot teach a concept list the document no longer has. Three parts are read —
 * the master table, each concept's "What." paragraph, and the curriculum ladder — and the start fails
 * loudly if the document stops having the shape they are read from.
 *
 * <p>It also refuses to start when a detector names a concept or a rule the document does not list:
 * every rule the app runs is a reading of a concept the document describes.
 */
@Component
public class ConceptRegistry {

	static final String SOURCE = "curriculum/ICT_CONCEPTS.md";

	private static final Pattern ID = Pattern.compile("\\b(F-\\d|ICT-\\d{2})\\b");
	private static final Pattern TABLE_ROW = Pattern.compile("^\\| (F-\\d|ICT-\\d{2}) \\|.*");
	private static final Pattern HEADING = Pattern.compile("^### (F-\\d|ICT-\\d{2}) · .*");
	private static final Pattern RULE = Pattern.compile("`([a-z0-9-]+-\\d+\\.\\d+\\.\\d+)`");
	private static final Pattern LADDER_ROW = Pattern.compile("^\\| (\\d+) \\|.*");

	public enum Status {
		IMPLEMENTED, DRAFTED, DESCRIBED, OUT_OF_SCOPE
	}

	/**
	 * @param detectability D, R, J or a combination, as the document writes it ("D / J")
	 * @param statusNote    what follows the status mark ("partly (journal)"), or empty
	 * @param rules         the versioned rule names the document links to the concept
	 * @param summary       the "What." paragraph, without its markup
	 * @param prerequisites concepts the section refers to that come earlier in the curriculum: a lower
	 *                      level, or the same level and earlier in the table
	 */
	public record Concept(String id, String name, int level, String detectability, Status status,
			String statusNote, List<String> rules, String summary, List<String> prerequisites) {
	}

	public record Level(int level, String theme, String concepts, String unlocksWhen) {
	}

	record Curriculum(List<Concept> concepts, List<Level> levels) {
	}

	private final Curriculum curriculum;

	public ConceptRegistry(DetectorRegistry detectors) {
		this.curriculum = parse(read());
		check(curriculum, detectors.all());
	}

	public List<Concept> concepts() {
		return curriculum.concepts();
	}

	public List<Level> levels() {
		return curriculum.levels();
	}

	// ---------------------------------------------------------------------------------------------

	private static String read() {
		try (InputStream in = ConceptRegistry.class.getClassLoader().getResourceAsStream(SOURCE)) {
			if (in == null) {
				throw new IllegalStateException(SOURCE + " is not on the classpath; the build copies it from docs/.");
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new IllegalStateException("Could not read " + SOURCE, e);
		}
	}

	static Curriculum parse(String markdown) {
		List<String> lines = List.of(markdown.split("\\R", -1));

		record Row(String id, String name, int level, String detect, String status, String rule) {
		}
		List<Row> rows = new ArrayList<>();
		for (String line : section(lines, "## Master table")) {
			if (TABLE_ROW.matcher(line).matches()) {
				List<String> c = cells(line);
				rows.add(new Row(c.get(0), c.get(1), Integer.parseInt(c.get(2)), c.get(3), c.get(4), c.get(5)));
			}
		}

		Map<String, String> summaries = new LinkedHashMap<>();
		Map<String, Set<String>> references = new LinkedHashMap<>();
		String current = null;
		List<String> paragraph = null;
		for (String line : lines) {
			Matcher heading = HEADING.matcher(line);
			if (heading.matches()) {
				current = heading.group(1);
				references.put(current, new LinkedHashSet<>());
				paragraph = null;
				continue;
			}
			if (line.startsWith("#")) {
				current = null;
			}
			if (current == null) {
				continue;
			}
			Matcher id = ID.matcher(line);
			while (id.find()) {
				references.get(current).add(id.group(1));
			}
			if (line.startsWith("**What.**") && !summaries.containsKey(current)) {
				paragraph = new ArrayList<>();
			}
			if (paragraph != null) {
				if (line.isBlank()) {
					summaries.put(current, plain(String.join(" ", paragraph).substring("**What.**".length())));
					paragraph = null;
				} else {
					paragraph.add(line.strip());
				}
			}
		}

		Map<String, Integer> order = new LinkedHashMap<>();
		rows.forEach(r -> order.put(r.id(), order.size()));
		List<Concept> concepts = new ArrayList<>();
		for (Row row : rows) {
			String summary = summaries.get(row.id());
			if (summary == null) {
				throw new IllegalStateException(row.id() + " has no '### " + row.id() + " · …' section with a "
					+ "**What.** paragraph in ICT_CONCEPTS.md");
			}
			List<String> prerequisites = references.get(row.id()).stream()
				.filter(order::containsKey)
				.filter(ref -> earlier(rows.get(order.get(ref)).level(), order.get(ref), row.level(), order.get(row.id())))
				.toList();
			List<String> rules = new ArrayList<>();
			Matcher rule = RULE.matcher(row.rule());
			while (rule.find()) {
				rules.add(rule.group(1));
			}
			concepts.add(new Concept(row.id(), row.name(), row.level(), row.detect(), status(row.id(), row.status()),
				plain(row.status().substring(row.status().offsetByCodePoints(0, 1))), List.copyOf(rules), summary,
				prerequisites));
		}
		for (String id : summaries.keySet()) {
			if (!order.containsKey(id)) {
				throw new IllegalStateException(id + " has a section but no row in the master table");
			}
		}

		List<Level> levels = new ArrayList<>();
		for (String line : section(lines, "## Part 12 — The curriculum ladder")) {
			if (LADDER_ROW.matcher(line).matches()) {
				List<String> c = cells(line);
				levels.add(new Level(Integer.parseInt(c.get(0)), c.get(1), c.get(2), plain(c.get(3))));
			}
		}
		Set<Integer> known = new LinkedHashSet<>(levels.stream().map(Level::level).toList());
		for (Concept concept : concepts) {
			if (!known.contains(concept.level())) {
				throw new IllegalStateException(concept.id() + " is on level " + concept.level()
					+ ", which the curriculum ladder does not have");
			}
		}
		return new Curriculum(List.copyOf(concepts), List.copyOf(levels));
	}

	static void check(Curriculum curriculum, Collection<Detector> detectors) {
		Map<String, Concept> byId = new LinkedHashMap<>();
		curriculum.concepts().forEach(c -> byId.put(c.id(), c));
		for (Detector detector : detectors) {
			Concept concept = byId.get(detector.conceptId());
			if (concept == null || !concept.rules().contains(detector.ruleVersion())) {
				throw new IllegalStateException("Detector " + detector.id() + " runs " + detector.ruleVersion()
					+ " for " + detector.conceptId() + ", which ICT_CONCEPTS.md does not list for that concept");
			}
		}
	}

	/** The lines from a heading to the next heading of the same depth or less. */
	private static List<String> section(List<String> lines, String heading) {
		int start = lines.indexOf(heading);
		if (start < 0) {
			throw new IllegalStateException("ICT_CONCEPTS.md has no '" + heading + "' section");
		}
		int end = start + 1;
		while (end < lines.size() && !lines.get(end).startsWith("## ") && !lines.get(end).startsWith("# ")) {
			end++;
		}
		return lines.subList(start + 1, end);
	}

	private static List<String> cells(String row) {
		String[] parts = row.split("\\|", -1);
		List<String> cells = new ArrayList<>();
		for (int i = 1; i < parts.length - 1; i++) {
			cells.add(parts[i].strip());
		}
		return cells;
	}

	private static boolean earlier(int level, int position, int thanLevel, int thanPosition) {
		return level < thanLevel || (level == thanLevel && position < thanPosition);
	}

	private static Status status(String id, String cell) {
		if (cell.startsWith("✅")) {
			return Status.IMPLEMENTED;
		}
		if (cell.startsWith("📐")) {
			return Status.DRAFTED;
		}
		if (cell.startsWith("📝")) {
			return Status.DESCRIBED;
		}
		if (cell.startsWith("⛔")) {
			return Status.OUT_OF_SCOPE;
		}
		throw new IllegalStateException(id + " has an unknown status mark: " + cell);
	}

	/** Markdown emphasis and code marks dropped; the words kept. */
	private static String plain(String markdown) {
		return markdown.replace("️", "").replace("**", "").replace("`", "").replaceAll("(?<![\\w*])\\*(\\S[^*]*?)\\*", "$1").strip();
	}
}
