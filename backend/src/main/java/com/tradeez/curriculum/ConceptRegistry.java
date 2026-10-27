package com.tradeez.curriculum;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
 * the master table, each concept's "What." paragraph, and the topics (T45, DEC-081) — and the start fails
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
	private static final Pattern TOPIC_ROW = Pattern.compile("^\\| [^|]+ \\| (F-\\d|ICT-\\d{2}) \\|.*");

	public enum Status {
		IMPLEMENTED, DRAFTED, DESCRIBED, OUT_OF_SCOPE
	}

	/**
	 * How Learn teaches a concept (DEC-081): a lesson with a drill, a reference card that never locks, a section of
	 * another lesson, or not at all.
	 */
	public enum Kind {
		LESSON, REFERENCE, MERGED, REMOVED
	}

	/**
	 * @param detectability D, R, J or a combination, as the document writes it ("D / J")
	 * @param statusNote    what follows the status mark ("partly (journal)"), or empty
	 * @param rules         the versioned rule names the document links to the concept
	 * @param summary       the "What." paragraph, without its markup
	 * @param prerequisites concepts the section refers to that come earlier in the curriculum: a lower
	 *                      level, or the same level and earlier in the table
	 * @param kind          how Learn teaches it
	 * @param topic         its topic; empty for a removed concept
	 * @param importance    high, medium or low (T44)
	 * @param into          for a merged concept, the lesson it is a section of; otherwise empty
	 */
	public record Concept(String id, String name, int level, String detectability, Status status,
			String statusNote, List<String> rules, String summary, List<String> prerequisites, Kind kind, String topic,
			String importance, String into) {

		/** Learn shows it as a page of its own: a lesson or a reference card. */
		public boolean taught() {
			return kind == Kind.LESSON || kind == Kind.REFERENCE;
		}
	}

	/** A topic of Learn, its lessons and reference cards in the order taught. */
	public record Topic(String title, List<String> concepts) {
	}

	record Curriculum(List<Concept> concepts, List<Topic> topics) {
	}

	private final Curriculum curriculum;

	public ConceptRegistry(DetectorRegistry detectors) {
		this.curriculum = parse(read());
		check(curriculum, detectors.all());
	}

	public List<Concept> concepts() {
		return curriculum.concepts();
	}

	public List<Topic> topics() {
		return curriculum.topics();
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

		record Placement(String topic, Kind kind, String importance, String into) {
		}
		Map<String, Placement> placements = new LinkedHashMap<>();
		List<Topic> topics = new ArrayList<>();
		for (String line : section(lines, "## Topics")) {
			if (!TOPIC_ROW.matcher(line).matches()) {
				continue;
			}
			List<String> c = cells(line);
			String id = c.get(1);
			Kind kind = Kind.valueOf(c.get(2).toUpperCase(Locale.ROOT));
			String topic = kind == Kind.REMOVED ? "" : c.get(0);
			String into = kind == Kind.MERGED ? c.get(4) : "";
			if (placements.put(id, new Placement(topic, kind, c.get(3), into)) != null) {
				throw new IllegalStateException(id + " is placed twice in the topics of ICT_CONCEPTS.md");
			}
			if (kind == Kind.LESSON || kind == Kind.REFERENCE) {
				if (topics.isEmpty() || !topics.getLast().title().equals(topic)) {
					if (topics.stream().anyMatch(t -> t.title().equals(topic))) {
						throw new IllegalStateException("Topic '" + topic + "' is split in two in ICT_CONCEPTS.md");
					}
					topics.add(new Topic(topic, new ArrayList<>()));
				}
				topics.getLast().concepts().add(id);
			}
		}

		Map<String, Integer> order = new LinkedHashMap<>();
		rows.forEach(r -> order.put(r.id(), order.size()));
		for (String id : placements.keySet()) {
			if (!order.containsKey(id)) {
				throw new IllegalStateException(id + " is placed in a topic but has no row in the master table");
			}
		}
		for (Map.Entry<String, Placement> e : placements.entrySet()) {
			Placement into = placements.get(e.getValue().into());
			if (e.getValue().kind() == Kind.MERGED && (into == null || into.kind() != Kind.LESSON && into.kind() != Kind.REFERENCE)) {
				throw new IllegalStateException(e.getKey() + " is merged into '" + e.getValue().into()
					+ "', which is not a lesson or a reference card");
			}
		}
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
			Placement placement = placements.get(row.id());
			if (placement == null) {
				throw new IllegalStateException(row.id() + " is not placed in the topics of ICT_CONCEPTS.md: every concept is a "
					+ "lesson, a reference, merged or removed");
			}
			concepts.add(new Concept(row.id(), row.name(), row.level(), row.detect(), status(row.id(), row.status()),
				plain(row.status().substring(row.status().offsetByCodePoints(0, 1))), List.copyOf(rules), summary,
				prerequisites, placement.kind(), placement.topic(), placement.importance(), placement.into()));
		}
		for (String id : summaries.keySet()) {
			if (!order.containsKey(id)) {
				throw new IllegalStateException(id + " has a section but no row in the master table");
			}
		}

		return new Curriculum(List.copyOf(concepts),
			topics.stream().map(t -> new Topic(t.title(), List.copyOf(t.concepts()))).toList());
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
