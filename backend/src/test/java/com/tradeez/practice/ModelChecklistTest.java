package com.tradeez.practice;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Timeframe;
import com.tradeez.practice.FillEngine.Direction;
import com.tradeez.practice.ModelChecklist.Checklist;
import com.tradeez.practice.ModelChecklist.Hit;
import com.tradeez.practice.ModelChecklist.Item;
import com.tradeez.practice.ModelChecklist.Moment;

/**
 * Each model's checklist (T22, DEC-066) on a moment built by hand: every step met, and each step missed on
 * its own, with what it says. Prices in ticks at NQ's 0.25.
 */
class ModelChecklistTest {

	private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
	private static final Timeframe M5 = Timeframe.M5;
	private static final BigDecimal TICK = new BigDecimal("0.25");

	private static Instant ny(String local) {
		return LocalDateTime.parse(local).atZone(NEW_YORK).toInstant();
	}

	private static Hit hit(String detector, String kind, String direction, Instant at, long low, long high,
			Map<String, Object> evidence) {
		return new Hit(detector, kind, direction, at, at, at, at.plus(M5.duration()), low, high, evidence);
	}

	/** A bullish shift at 09:50: the swing low at 1000 swept the one before it; a close above 1040. */
	private static Hit shift(Instant at) {
		return hit("MSS", null, "BULLISH", at, 1040, 1040, Map.of("sweptTime", at.minusSeconds(1200).getEpochSecond(),
			"sweptPrice", 1000L, "brokenPrice", 1040L));
	}

	private static Hit gap(Instant at, long low, long high) {
		return hit("FVG", null, "BULLISH", at, low, high, Map.of());
	}

	private static Hit buySide(Instant at, String kind, long price) {
		return hit("LIQUIDITY", kind, "BEARISH", at, price, price, Map.of("side", "BSL", "price", price));
	}

	private static Moment moment(String cursor, Direction direction, long reference, long stop, long target, List<Hit> hits) {
		Map<String, List<Hit>> byRule = new HashMap<>();
		hits.forEach(h -> byRule.computeIfAbsent(h.detector(), k -> new java.util.ArrayList<>()).add(h));
		List<Bar> bars = List.of(new Bar(ny(cursor).minus(M5.duration()), 1030, 1032, 990, 1031, 1));
		return new Moment(ny(cursor), M5, direction, reference, stop, target, TICK, bars, byRule);
	}

	private static Map<String, Boolean> met(Checklist checklist) {
		Map<String, Boolean> out = new java.util.LinkedHashMap<>();
		checklist.items().forEach(i -> out.put(i.id(), i.met()));
		return out;
	}

	private static String text(Checklist checklist, String id) {
		return checklist.items().stream().filter(i -> i.id().equals(id)).map(Item::text).findFirst().orElseThrow();
	}

	/** The 2022 model, every step met: NY AM, a shift, its gap with price back in it, the stop below the sweep, buy-side above. */
	private static List<Hit> model2022() {
		return List.of(shift(ny("2025-05-06T09:50")), gap(ny("2025-05-06T09:45"), 1020, 1030),
			buySide(ny("2025-05-06T08:00"), "RESTING", 1080));
	}

	@Test
	@DisplayName("2022 model: every step met, in words")
	void model2022Met() {
		Checklist c = ModelChecklist.check(TradingModel.MODEL_2022, moment("2025-05-06T10:05", Direction.LONG, 1025, 995,
			1090, model2022()));
		assertThat(c.version()).isEqualTo("model-2022-1.0.0");
		assertThat(met(c)).containsExactly(Map.entry("killzone", true), Map.entry("shift", true), Map.entry("gap", true),
			Map.entry("stop", true), Map.entry("target", true));
		assertThat(c.met()).isEqualTo(5);
		assertThat(text(c, "killzone")).isEqualTo("Inside a killzone: New York AM, 08:30–11:00.");
		assertThat(text(c, "gap")).isEqualTo("Price, at 256.25, is in the bullish gap the shift left, 255.00 to 257.50.");
		assertThat(text(c, "stop")).isEqualTo("The stop, at 248.75, is below the low that made the sweep, at 250.00.");
		assertThat(text(c, "target")).isEqualTo("The target, at 272.50, reaches resting buy-side liquidity at 270.00.");
	}

	@Test
	@DisplayName("2022 model: each step missed on its own")
	void model2022Missed() {
		// Outside the killzones; Asia does not count for this model.
		assertThat(met(ModelChecklist.check(TradingModel.MODEL_2022, moment("2025-05-06T12:05", Direction.LONG, 1025, 995,
			1090, model2022())))).containsEntry("killzone", false);
		assertThat(text(ModelChecklist.check(TradingModel.MODEL_2022, moment("2025-05-06T21:05", Direction.LONG, 1025, 995,
			1090, model2022())), "killzone")).contains("it is 21:05 New York");
		// The shift too old: 31 candles before the cursor.
		Checklist old = ModelChecklist.check(TradingModel.MODEL_2022, moment("2025-05-06T12:30", Direction.LONG, 1025, 995,
			1090, model2022()));
		assertThat(met(old)).containsEntry("shift", false).containsEntry("gap", false).containsEntry("stop", false);
		// Price not back in the gap; the stop above the sweep; the buy-side already taken.
		Checklist late = ModelChecklist.check(TradingModel.MODEL_2022, moment("2025-05-06T10:05", Direction.LONG, 1035, 1005,
			1090, List.of(shift(ny("2025-05-06T09:50")), gap(ny("2025-05-06T09:45"), 1020, 1030),
				buySide(ny("2025-05-06T08:00"), "RESTING", 1080), new Hit("LIQUIDITY", "TAKEN", "BEARISH", ny("2025-05-06T08:00"),
					ny("2025-05-06T08:00"), ny("2025-05-06T09:30"), ny("2025-05-06T09:35"), 1080, 1080, Map.of("side", "BSL", "price", 1080L)))));
		assertThat(met(late)).containsEntry("gap", false).containsEntry("stop", false).containsEntry("target", false);
		assertThat(text(late, "gap")).endsWith("— the model enters on the retrace into it.");
		// A short is checked the other way: the bullish shift is not its shift.
		assertThat(met(ModelChecklist.check(TradingModel.MODEL_2022, moment("2025-05-06T10:05", Direction.SHORT, 1025, 1060,
			990, model2022())))).containsEntry("shift", false);
	}

	@Test
	@DisplayName("2022 model at its edges: price on the gap's edge is in it, a stop on the sweep's low is not beyond it, a target on the liquidity reaches it, a gap from before the sweep is not the shift's")
	void model2022Edges() {
		Checklist edges = ModelChecklist.check(TradingModel.MODEL_2022, moment("2025-05-06T10:05", Direction.LONG, 1030, 1000,
			1080, model2022()));
		assertThat(met(edges)).containsEntry("gap", true).containsEntry("stop", false).containsEntry("target", true);
		Checklist before = ModelChecklist.check(TradingModel.MODEL_2022, moment("2025-05-06T10:05", Direction.LONG, 1025, 995,
			1090, List.of(shift(ny("2025-05-06T09:50")), gap(ny("2025-05-06T09:25"), 1020, 1030))));
		assertThat(met(before)).containsEntry("gap", false);
		assertThat(text(before, "gap")).isEqualTo("The shift left no bullish fair value gap to enter in.");
	}

	@Test
	@DisplayName("Silver Bullet: in the hour, a gap formed in it, price back in it, room to buy-side, the stop below the gap")
	void silverBullet() {
		List<Hit> hits = List.of(gap(ny("2025-05-06T10:15"), 1020, 1030), buySide(ny("2025-05-06T08:00"), "RESTING", 1080));
		Checklist c = ModelChecklist.check(TradingModel.SILVER_BULLET, moment("2025-05-06T10:30", Direction.LONG, 1025, 1015,
			1090, hits));
		assertThat(met(c).values()).containsOnly(true);
		assertThat(text(c, "window")).isEqualTo("Inside the 10:00–11:00 Silver Bullet hour.");
		// A gap from before the hour is not the hour's; and too little room to the target.
		Checklist early = ModelChecklist.check(TradingModel.SILVER_BULLET, moment("2025-05-06T10:30", Direction.LONG, 1025,
			1015, 1090, List.of(gap(ny("2025-05-06T09:50"), 1020, 1030))));
		assertThat(met(early)).containsEntry("gap", false).containsEntry("retrace", false);
		Checklist close = ModelChecklist.check(TradingModel.SILVER_BULLET, moment("2025-05-06T10:30", Direction.LONG, 1025,
			1015, 1060, List.of(gap(ny("2025-05-06T10:15"), 1020, 1030), buySide(ny("2025-05-06T08:00"), "RESTING", 1050))));
		assertThat(met(close)).containsEntry("target", false);
		assertThat(text(close, "target")).contains("only 8.75 points away; the model asks for 10.00");
		assertThat(met(ModelChecklist.check(TradingModel.SILVER_BULLET, moment("2025-05-06T11:05", Direction.LONG, 1025,
			1015, 1090, hits)))).containsEntry("window", false);
	}

	@Test
	@DisplayName("OTE model: a shift, the leg's OTE zone still standing, price in it, the stop below the leg's start")
	void ote() {
		Instant formed = ny("2025-05-06T09:55");
		Hit zone = new Hit("OTE", "FORMED", "BULLISH", formed, formed, formed, formed.plus(M5.duration()), 1010, 1020,
			Map.of("startPrice", 1000L));
		List<Hit> hits = List.of(shift(ny("2025-05-06T09:50")), zone);
		assertThat(met(ModelChecklist.check(TradingModel.OTE, moment("2025-05-06T10:05", Direction.LONG, 1015, 995, 1060, hits)))
			.values()).containsOnly(true);
		Hit broken = new Hit("OTE", "BROKEN_LOW", "BULLISH", formed, formed, ny("2025-05-06T10:00"), ny("2025-05-06T10:05"),
			1010, 1020, Map.of("startPrice", 1000L));
		assertThat(met(ModelChecklist.check(TradingModel.OTE, moment("2025-05-06T10:05", Direction.LONG, 1015, 995, 1060,
			List.of(shift(ny("2025-05-06T09:50")), zone, broken))))).containsEntry("zone", false);
	}

	@Test
	@DisplayName("unicorn and breaker entries: a zone still standing, price in it, the stop beyond it")
	void zones() {
		Instant formed = ny("2025-05-06T09:00");
		Hit unicorn = new Hit("UNICORN", "TAPPED", "BULLISH", formed, formed, formed, formed.plus(M5.duration()), 1020, 1030,
			Map.of());
		assertThat(met(ModelChecklist.check(TradingModel.UNICORN, moment("2025-05-06T10:05", Direction.LONG, 1025, 1015, 1060,
			List.of(unicorn)))).values()).containsOnly(true);
		Hit invalidated = new Hit("UNICORN", "INVALIDATED", "BULLISH", formed, formed, ny("2025-05-06T10:00"),
			ny("2025-05-06T10:05"), 1020, 1030, Map.of());
		assertThat(met(ModelChecklist.check(TradingModel.UNICORN, moment("2025-05-06T10:05", Direction.LONG, 1025, 1015, 1060,
			List.of(unicorn, invalidated))))).containsEntry("zone", false);
		Hit mitigation = new Hit("MITIGATION", "FORMED", "BULLISH", formed, formed, formed, formed.plus(M5.duration()), 1020,
			1030, Map.of());
		Checklist breaker = ModelChecklist.check(TradingModel.BREAKER, moment("2025-05-06T10:05", Direction.LONG, 1025, 1025,
			1060, List.of(mitigation)));
		assertThat(met(breaker)).containsExactly(Map.entry("zone", true), Map.entry("retrace", true), Map.entry("stop", false));
	}

	@Test
	@DisplayName("turtle soup: in the last three candles, the stop below the sweeping candle, buy-side above")
	void turtleSoup() {
		Instant candle = ny("2025-05-06T10:00");
		Hit soup = new Hit("TURTLE_SOUP", null, "BULLISH", candle, candle, candle, candle.plus(M5.duration()), 1000, 1000,
			Map.of("price", 1000L, "closeTicks", 1031L));
		List<Hit> hits = List.of(soup, buySide(ny("2025-05-06T08:00"), "RESTING", 1080));
		Checklist c = ModelChecklist.check(TradingModel.TURTLE_SOUP, moment("2025-05-06T10:05", Direction.LONG, 1031, 985,
			1090, hits));
		assertThat(met(c).values()).containsOnly(true);
		// The sweeping candle's low is 990: a stop at 992 is not below it.
		assertThat(text(ModelChecklist.check(TradingModel.TURTLE_SOUP, moment("2025-05-06T10:05", Direction.LONG, 1031, 992,
			1090, hits)), "stop")).isEqualTo("The stop, at 248.00, is not below the sweeping candle's low, at 247.50.");
		assertThat(met(ModelChecklist.check(TradingModel.TURTLE_SOUP, moment("2025-05-06T10:25", Direction.LONG, 1031, 985,
			1090, hits)))).containsEntry("soup", false);
	}

	@Test
	@DisplayName("session open reversal: a Judas swing today, a shift after it inside the window, the stop beyond its extreme")
	void sessionReversal() {
		Instant judasAt = ny("2025-05-06T09:30");
		Hit judas = new Hit("JUDAS", "NEW_YORK", "BULLISH", judasAt, judasAt, ny("2025-05-06T09:40"), ny("2025-05-06T09:45"),
			990, 1030, Map.of("levelPrice", 995L, "extremeTicks", 990L));
		Checklist c = ModelChecklist.check(TradingModel.SESSION_REVERSAL, moment("2025-05-06T10:05", Direction.LONG, 1035, 985,
			1090, List.of(judas, shift(ny("2025-05-06T09:50")))));
		assertThat(met(c).values()).containsOnly(true);
		assertThat(text(c, "judas")).contains("in the New York window");
		// A shift after the window closed is not part of it.
		assertThat(met(ModelChecklist.check(TradingModel.SESSION_REVERSAL, moment("2025-05-06T10:35", Direction.LONG, 1035, 985,
			1090, List.of(judas, shift(ny("2025-05-06T10:20"))))))).containsEntry("shift", false);
	}

	@Test
	@DisplayName("opening range gap: the first half hour, toward the gap's middle, the target at or short of it")
	void openingRangeGap() {
		Instant open = ny("2025-05-06T09:30");
		// Yesterday's 16:15 close at 1040, today's open at 1000: the middle at 1020.
		Hit org = new Hit("ORG", null, "BULLISH", ny("2025-05-05T16:10"), open, open, open.plus(M5.duration()), 1000, 1040,
			Map.of("ceHalfTicks", 2040L));
		Checklist c = ModelChecklist.check(TradingModel.OPENING_RANGE_GAP, moment("2025-05-06T09:40", Direction.LONG, 1002,
			990, 1020, List.of(org)));
		assertThat(met(c).values()).containsOnly(true);
		assertThat(text(c, "toward")).isEqualTo("The trade points toward the gap's middle at 255.00.");
		assertThat(met(ModelChecklist.check(TradingModel.OPENING_RANGE_GAP, moment("2025-05-06T09:40", Direction.LONG, 1002,
			990, 1030, List.of(org))))).containsEntry("target", false);
		assertThat(met(ModelChecklist.check(TradingModel.OPENING_RANGE_GAP, moment("2025-05-06T09:40", Direction.SHORT, 1002,
			1010, 990, List.of(org))))).containsEntry("toward", false);
		assertThat(met(ModelChecklist.check(TradingModel.OPENING_RANGE_GAP, moment("2025-05-06T10:05", Direction.LONG, 1002,
			990, 1020, List.of(org))))).containsEntry("window", false);
	}
}
