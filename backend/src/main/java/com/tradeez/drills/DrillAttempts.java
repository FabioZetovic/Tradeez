package com.tradeez.drills;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.tradeez.drills.DrillKind.Mode;
import com.tradeez.learning.LearningRule.Attempt;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * A learner's graded drills as attempts at concepts (T16, DEC-059), scored the way each drill's own
 * result scores it — so the learning state can never disagree with what a drill told the learner.
 *
 * <ul>
 *   <li>Marking (A): the share of the answer found, misses and extra marks counting against it —
 *       found ÷ (found + missed + extra). Excused marks count neither way, as in the result.</li>
 *   <li>Naming (B): 1 for the rule's name, 0 for another.</li>
 *   <li>Predicting (C): 1 when the target chosen came first — practice, never rated.</li>
 *   <li>Managing (E, T26): 1 when the choice was among those that paid most — practice, never rated.</li>
 *   <li>Judging (J, T45): 1 for the rule's answer, 0 for another.</li>
 *   <li>Risk questions (F, T26): 1 when right — practice, never rated; they count toward {@value RiskQuestion#CONCEPT}.</li>
 * </ul>
 *
 * <p>Only graded drills are read, and the database grades a drill only on real data (DEC-034).
 */
@Component
public class DrillAttempts {

	private static final String SQL = """
		SELECT d.kind, d.answered_at, d.marks::text, d.choice, d.excused::text, d.outcome,
		       array_remove(array_agg(extract(epoch FROM s.anchor_time)::bigint ORDER BY s.anchor_time), NULL),
		       array_remove(array_agg(s.kind ORDER BY s.anchor_time), NULL)
		FROM drill d
		LEFT JOIN drill_setup ds ON ds.drill_id = d.id
		LEFT JOIN detected_setup s ON s.id = ds.setup_id
		WHERE d.user_id = ? AND d.graded AND d.answered_at IS NOT NULL
		GROUP BY d.id
		ORDER BY d.answered_at""";

	private final JdbcTemplate jdbc;
	private final ObjectMapper json;

	public DrillAttempts(JdbcTemplate jdbc, ObjectMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	/** Every graded, answered drill of the user and every risk question answered, oldest first. A kind no longer offered is left out. */
	public List<Attempt> of(UUID userId) {
		List<Attempt> attempts = new ArrayList<>();
		jdbc.query(SQL, rs -> {
			DrillKind kind = kind(rs.getString(1));
			if (kind != null) {
				attempts.add(new Attempt(kind.conceptId(), rs.getTimestamp(2).toInstant(), score(kind, rs), rated(kind)));
			}
		}, userId);
		jdbc.query("SELECT answered_at, correct FROM risk_drill WHERE user_id = ? AND answered_at IS NOT NULL", rs -> {
			attempts.add(new Attempt(RiskQuestion.CONCEPT, rs.getTimestamp(1).toInstant(), rs.getBoolean(2) ? 1.0 : 0.0, false));
		}, userId);
		attempts.sort(Comparator.comparing(Attempt::at));
		return attempts;
	}

	/** Whether a drill practises this concept: any drill kind on it, or the risk questions for theirs. */
	public static boolean drills(String conceptId) {
		return RiskQuestion.CONCEPT.equals(conceptId)
			|| Arrays.stream(DrillKind.values()).anyMatch(k -> k.conceptId().equals(conceptId));
	}

	/** Graded against a rule: marking, naming and judging. What the future decides is practice, never a rating. */
	private static boolean rated(DrillKind kind) {
		return kind.mode() == Mode.IDENTIFY || kind.mode() == Mode.CLASSIFY || kind.mode() == Mode.JUDGE;
	}

	private double score(DrillKind kind, ResultSet rs) throws SQLException {
		List<Long> anchors = longs(rs.getArray(7));
		List<String> kinds = strings(rs.getArray(8));
		return switch (kind.mode()) {
			case IDENTIFY -> {
				Set<Long> marked = new HashSet<>(longs(rs.getString(3)));
				Set<Long> excused = new HashSet<>(longs(rs.getString(5)));
				Set<Long> asked = new HashSet<>(anchors);
				long found = anchors.stream().filter(marked::contains).count();
				long missed = anchors.size() - found;
				long extra = marked.stream().filter(m -> !asked.contains(m) && !excused.contains(m)).count();
				long all = found + missed + extra;
				yield all == 0 ? 1.0 : (double) found / all;
			}
			case CLASSIFY -> !kinds.isEmpty() && kinds.getFirst().equals(rs.getString(4)) ? 1.0 : 0.0;
			case PREDICT, JUDGE -> rs.getString(4) != null && rs.getString(4).equals(rs.getString(6)) ? 1.0 : 0.0;
			case MANAGE -> Management.paidMost(rs.getString(6), rs.getString(4)) ? 1.0 : 0.0;
		};
	}

	private static DrillKind kind(String code) {
		try {
			return DrillKind.valueOf(code);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private List<Long> longs(String jsonArray) {
		List<Long> out = new ArrayList<>();
		if (jsonArray == null) {
			return out;
		}
		for (JsonNode n : json.readTree(jsonArray)) {
			out.add(n.asLong());
		}
		return out;
	}

	private static List<Long> longs(Array array) throws SQLException {
		List<Long> out = new ArrayList<>();
		if (array != null) {
			for (Object o : (Object[]) array.getArray()) {
				out.add(((Number) o).longValue());
			}
		}
		return out;
	}

	private static List<String> strings(Array array) throws SQLException {
		List<String> out = new ArrayList<>();
		if (array != null) {
			for (Object o : (Object[]) array.getArray()) {
				out.add((String) o);
			}
		}
		return out;
	}
}
