package com.tradeez.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tradeez.practice.FillEngine.Direction;
import com.tradeez.practice.JournalStatistics.Sample;
import com.tradeez.practice.PracticeDtos.StatisticsDimensionView;
import com.tradeez.practice.PracticeDtos.StatisticsGroupView;
import com.tradeez.practice.PracticeDtos.StatisticsView;

/** journal-stats-1.0.0 (T25, DEC-069): trades added up, the count behind every figure. */
class JournalStatisticsTest {

	private static Sample r(double r) {
		return new Sample(r, Math.round(r * 20_000), null, null, DayOfWeek.MONDAY, Direction.LONG, null, null, 0, 0, false, 0);
	}

	private static StatisticsGroupView group(double... rs) {
		return JournalStatistics.group("g", Arrays.stream(rs).mapToObj(JournalStatisticsTest::r).toList());
	}

	private static StatisticsDimensionView dimension(StatisticsView view, String id) {
		return view.dimensions().stream().filter(d -> d.id().equals(id)).findFirst().orElseThrow();
	}

	@Test
	@DisplayName("two wins and two losses, by hand: 50%, +1.75R and −1.00R, +0.38R expected, 1.75 profit factor")
	void arithmetic() {
		StatisticsGroupView g = group(2, -1, -1, 1.5);
		assertThat(g.trades()).isEqualTo(4);
		assertThat(g.wins()).isEqualTo(2);
		assertThat(g.losses()).isEqualTo(2);
		assertThat(g.winRate()).isEqualTo(50.0);
		assertThat(g.avgWinR()).isEqualTo(1.75);
		assertThat(g.avgLossR()).isEqualTo(-1.0);
		assertThat(g.expectancyR()).isEqualTo(0.38);
		assertThat(g.profitFactor()).isEqualTo(1.75);
		assertThat(g.totalR()).isEqualTo(1.5);
		assertThat(g.netPnlCents()).isEqualTo(30_000);
		// Sample deviation 1.60R over four trades: ±1.57R either side of +0.375R.
		assertThat(g.low()).isEqualTo(-1.19);
		assertThat(g.high()).isEqualTo(1.94);
		assertThat(g.reading()).isEqualTo("Cannot be told from zero yet: the range spans it.");
	}

	@Test
	@DisplayName("the range read in words: above zero, below zero, and none for a single trade; a flat trade is neither")
	void readings() {
		assertThat(group(1, 1, 1, 1.2).reading()).isEqualTo("Positive so far: the whole range is above zero.");
		assertThat(group(-1, -1, -1.1).reading()).isEqualTo("Negative so far: the whole range is below zero.");
		StatisticsGroupView one = group(2);
		assertThat(one.low()).isNull();
		assertThat(one.high()).isNull();
		assertThat(one.reading()).isEqualTo("One trade: no range yet.");
		// No losses: no profit factor and no average loss; no wins: no average win.
		assertThat(one.profitFactor()).isNull();
		assertThat(one.avgLossR()).isNull();
		assertThat(group(-1).avgWinR()).isNull();
		StatisticsGroupView flat = group(0, 1);
		assertThat(flat.wins()).isEqualTo(1);
		assertThat(flat.losses()).isZero();
		assertThat(flat.winRate()).isEqualTo(50.0);
	}

	@Test
	@DisplayName("nothing yet: no figures, a note, and every dimension empty")
	void empty() {
		StatisticsView view = JournalStatistics.of(List.of());
		assertThat(view.version()).isEqualTo("journal-stats-1.0.0");
		assertThat(view.overall().trades()).isZero();
		assertThat(view.overall().expectancyR()).isNull();
		assertThat(view.overall().winRate()).isNull();
		assertThat(view.notes()).containsExactly("No finished trade yet: the figures appear as the replay shows each trade's exit.");
		assertThat(view.dimensions()).extracting(StatisticsDimensionView::id)
			.containsExactly("model", "killzone", "weekday", "side", "checks", "rules");
		assertThat(view.dimensions()).allSatisfy(d -> assertThat(d.groups()).isEmpty());
	}

	@Test
	@DisplayName("grouped six ways, in a fixed order where there is one, and only the groups that have trades")
	void dimensions() {
		List<Sample> samples = List.of(
			new Sample(2, 0, "Silver Bullet", "NEW_YORK_AM", DayOfWeek.TUESDAY, Direction.LONG, 5, 5, 2, 0, false, 0),
			new Sample(-1, 0, null, null, DayOfWeek.MONDAY, Direction.SHORT, null, null, 1, 1, false, 0),
			new Sample(1, 0, "The 2022 model", "ASIA", DayOfWeek.TUESDAY, Direction.LONG, 0, 5, 0, 0, false, 0),
			new Sample(-1, 0, "Silver Bullet", "NEW_YORK_AM", DayOfWeek.FRIDAY, Direction.LONG, 3, 5, 0, 1, false, 0),
			new Sample(0.5, 0, null, "ASIA", DayOfWeek.SUNDAY, Direction.SHORT, null, null, 0, 0, false, 0));
		StatisticsView view = JournalStatistics.of(samples);
		assertThat(view.overall().label()).isEqualTo("All trades");
		assertThat(view.overall().trades()).isEqualTo(5);
		assertThat(view.notes()).isEmpty();

		assertThat(dimension(view, "model").groups()).extracting(StatisticsGroupView::label, StatisticsGroupView::trades)
			.containsExactly(tuple("Silver Bullet", 2), tuple("No model", 2),
				tuple("The 2022 model", 1));
		assertThat(dimension(view, "killzone").groups()).extracting(StatisticsGroupView::label)
			.containsExactly("Asia", "New York AM", "Outside the killzones");
		// The week opens on Sunday evening.
		assertThat(dimension(view, "weekday").groups()).extracting(StatisticsGroupView::label)
			.containsExactly("Sunday", "Monday", "Tuesday", "Friday");
		assertThat(dimension(view, "side").groups()).extracting(StatisticsGroupView::label, StatisticsGroupView::trades)
			.containsExactly(tuple("Long", 3), tuple("Short", 2));
		assertThat(dimension(view, "checks").groups()).extracting(StatisticsGroupView::label)
			.containsExactly("Every check met", "Some checks met", "No check met", "No model named");
		assertThat(dimension(view, "checks").groups().getLast().trades()).isEqualTo(2);
		// Kept only when nothing was broken; a trade with no rule of the learner's to check has none.
		assertThat(dimension(view, "rules").groups()).extracting(StatisticsGroupView::label, StatisticsGroupView::trades)
			.containsExactly(tuple("All kept", 1), tuple("Some broken", 2),
				tuple("None to check", 2));
		assertThat(dimension(view, "rules").groups().get(1).expectancyR()).isEqualTo(-1.0);
	}

	@Test
	@DisplayName("T41: held to plan is every trade not closed by hand; costs add up; the best and worst trade, none without trades")
	void totals() {
		Sample held = new Sample(1.5, 0, null, null, DayOfWeek.MONDAY, Direction.LONG, null, null, 0, 0, false, 950);
		Sample closed = new Sample(-0.4, 0, null, null, DayOfWeek.MONDAY, Direction.SHORT, null, null, 0, 0, true, 1450);
		Sample stopped = new Sample(-1.04, 0, null, null, DayOfWeek.TUESDAY, Direction.LONG, null, null, 0, 0, false, 1450);
		StatisticsView view = JournalStatistics.of(List.of(held, closed, stopped));
		assertThat(view.heldToPlan()).isEqualTo(2);
		assertThat(view.costsPaidCents()).isEqualTo(3_850);
		assertThat(view.bestR()).isEqualTo(1.5);
		assertThat(view.worstR()).isEqualTo(-1.04);

		StatisticsView none = JournalStatistics.of(List.of());
		assertThat(none.heldToPlan()).isZero();
		assertThat(none.costsPaidCents()).isZero();
		assertThat(none.bestR()).isNull();
		assertThat(none.worstR()).isNull();
	}
}
