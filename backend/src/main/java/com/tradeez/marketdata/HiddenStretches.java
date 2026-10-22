package com.tradeez.marketdata;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * The stretches of real history a learner may not open right now, because something they are still answering is
 * graded by them (BUG-085, BUG-086).
 *
 * <ul>
 * <li>The practice session on real data the learner is in — the open one they touched last: from its cursor to a day
 * past the end of its replay. The date is hidden (DEC-025), but every price is real to the tick, and the Chart tab's two
 * years held the day — and everything the replay had not shown yet. What the replay has shown stays open: finding the
 * day by it shows nothing new. Sessions left open behind it are not hidden: starting another does not end the last,
 * and a learner who had left a few dozen would find the chart full of holes.</li>
 * <li>The learner's newest drill, while unanswered: from its first candle to the last its answer is decided by. A
 * prediction or a management choice is graded by what price did next, and the Chart tab showed it. A drill left behind
 * by "Next drill" is not hidden: the app never goes back to it.</li>
 * </ul>
 *
 * <p>Every real market at once — ES beside NQ tells the same story. Read straight from the two tables, so market data
 * depends on neither module.
 */
@Service
public class HiddenStretches {

	/** A replay's length ({@code PracticeService.MAX_REPLAY}) and a day for a trade still running at its end. */
	public static final Duration PAST_REPLAY_START = Duration.ofDays(4);

	public enum Why {
		PRACTICE, DRILL
	}

	/** {@code [from, to)} on the real timeline. */
	public record Stretch(Instant from, Instant to, Why why) {

		/** Whether {@code [a, b)} touches this stretch. */
		public boolean overlaps(Instant a, Instant b) {
			return a.isBefore(to) && from.isBefore(b);
		}
	}

	private final JdbcTemplate jdbc;

	public HiddenStretches(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public List<Stretch> of(UUID userId) {
		List<Stretch> hidden = new ArrayList<>(jdbc.query("""
				SELECT s.cursor_time, s.start_time FROM practice_session s JOIN instrument i ON i.id = s.instrument_id
				WHERE s.user_id = ? AND s.status <> 'COMPLETE' AND i.data_class <> 'SIMULATED'
				ORDER BY s.updated_at DESC LIMIT 1""",
			(rs, n) -> new Stretch(rs.getTimestamp(1).toInstant(), rs.getTimestamp(2).toInstant().plus(PAST_REPLAY_START),
				Why.PRACTICE),
			userId));
		hidden.addAll(jdbc.query("""
				SELECT window_from, GREATEST(window_to, reveal_to) FROM drill
				WHERE id = (SELECT id FROM drill WHERE user_id = ? ORDER BY created_at DESC LIMIT 1)
				  AND answered_at IS NULL AND data_class <> 'SIMULATED'""",
			(rs, n) -> new Stretch(rs.getTimestamp(1).toInstant(), rs.getTimestamp(2).toInstant(), Why.DRILL), userId));
		return hidden;
	}

	/** Whether {@code [from, to)} touches any of {@code hidden}. */
	public static boolean touches(List<Stretch> hidden, Instant from, Instant to) {
		return hidden.stream().anyMatch(s -> s.overlaps(from, to));
	}
}
