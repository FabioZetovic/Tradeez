package com.tradeez.practice;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import com.tradeez.analysis.RuleText;
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.Killzone;
import com.tradeez.marketdata.Timeframe;
import com.tradeez.practice.FillEngine.Direction;

/**
 * A trading model's checklist, checked at the moment a plan is drafted or placed (T22, DEC-066): each step
 * of the model as ICT_CONCEPTS.md drafts it, met or not, read from what the rules had confirmed by the
 * cursor — never a candle after it (INVARIANT 1). Adherence to the chosen model, scored apart from how the
 * trade turned out (DEC-027); never a judgement of whether the model is any good (INVARIANT 8).
 *
 * <p>A pure function of the moment, so every step can be tested without a market.
 */
public final class ModelChecklist {

	/** How recent a shift must be for a model to start from it, in candles of the session's chart. */
	static final int RECENT_CANDLES = 30;

	/** A turtle soup is traded on the candle that made it, or the next few. */
	static final int SOUP_CANDLES = 3;

	/** The Silver Bullet's minimum room to the target: ten NQ points. */
	static final long SILVER_BULLET_MIN_TICKS = 40;

	private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

	private static final List<LocalTime> SILVER_BULLETS = List.of(LocalTime.of(3, 0), LocalTime.of(10, 0), LocalTime.of(14, 0));

	private static final Set<String> BLOCK_ENDS = Set.of("INVALIDATED", "EXPIRED");
	private static final Set<String> RANGE_ENDS = Set.of("BROKEN_HIGH", "BROKEN_LOW", "EXPIRED");

	private ModelChecklist() {
	}

	/** A stored hit as much as a checklist reads of it. */
	public record Hit(String detector, String kind, String direction, Instant start, Instant anchor, Instant end,
			Instant confirmedAt, long low, long high, Map<String, Object> evidence) {
	}

	/**
	 * The decision: where the replay stands and the bracket drawn, with the hits of the model's rules confirmed
	 * by the cursor and the candles before it.
	 */
	public record Moment(Instant cursor, Timeframe timeframe, Direction direction, long reference, long stop, long target,
			BigDecimal tickSize, List<Bar> bars, Map<String, List<Hit>> hits) {

		boolean up() {
			return direction == Direction.LONG;
		}

		String way() {
			return up() ? "bullish" : "bearish";
		}

		String price(long ticks) {
			return RuleText.price(ticks, tickSize);
		}

		List<Hit> of(String detector) {
			return hits.getOrDefault(detector, List.of());
		}

		/** Hits of {@code detector} pointing the trade's way. */
		List<Hit> ours(String detector) {
			String want = up() ? "BULLISH" : "BEARISH";
			return of(detector).stream().filter(h -> want.equals(h.direction())).toList();
		}

		/** Confirmed in the last {@code candles} candles before the cursor. */
		boolean within(Hit hit, int candles) {
			return !hit.confirmedAt().isBefore(cursor.minus(timeframe.duration().multipliedBy(candles)));
		}

		ZonedDateTime newYork() {
			return cursor.atZone(Killzone.NEW_YORK);
		}
	}

	public record Item(String id, String text, boolean met) {
	}

	public record Checklist(TradingModel model, String version, List<Item> items) {

		public long met() {
			return items.stream().filter(Item::met).count();
		}
	}

	public static Checklist check(TradingModel model, Moment m) {
		List<Item> items = switch (model) {
			case MODEL_2022 -> model2022(m);
			case SILVER_BULLET -> silverBullet(m);
			case OTE -> ote(m);
			case UNICORN -> zoneEntry(m, List.of("UNICORN"), "unicorn");
			case TURTLE_SOUP -> turtleSoup(m);
			case BREAKER -> zoneEntry(m, List.of("BREAKER", "MITIGATION"), "breaker or mitigation block");
			case SESSION_REVERSAL -> sessionReversal(m);
			case OPENING_RANGE_GAP -> openingRangeGap(m);
		};
		return new Checklist(model, model.version(), items);
	}

	// --- the models --------------------------------------------------------------------------------

	private static List<Item> model2022(Moment m) {
		List<Item> items = new ArrayList<>();
		Optional<Killzone> zone = killzone(m.cursor()).filter(k -> k != Killzone.ASIA);
		items.add(zone.isPresent()
			? new Item("killzone", "Inside a killzone: " + name(zone.get()) + ".", true)
			: new Item("killzone", "Not inside the London or a New York killzone — it is " + m.newYork().format(CLOCK)
				+ " New York.", false));
		Optional<Hit> shift = recentShift(m);
		items.add(shiftItem(m, shift));
		Optional<Hit> gap = shift.flatMap(s -> m.ours("FVG").stream()
			.filter(g -> !g.anchor().isBefore(Instant.ofEpochSecond(number(s, "sweptTime"))) && !g.confirmedAt().isAfter(
				s.confirmedAt().plus(m.timeframe().duration())))
			.max(Comparator.comparing(Hit::confirmedAt)));
		if (shift.isEmpty()) {
			items.add(new Item("gap", "No shift, so no gap left by one to enter in.", false));
		} else if (gap.isEmpty()) {
			items.add(new Item("gap", "The shift left no " + m.way() + " fair value gap to enter in.", false));
		} else {
			items.add(atZone(m, gap.get(), "gap", "the " + m.way() + " gap the shift left"));
		}
		items.add(shift.isEmpty() ? new Item("stop", "No sweep to put the stop beyond.", false)
			: beyond(m, number(shift.get(), "sweptPrice"), "the " + (m.up() ? "low" : "high") + " that made the sweep"));
		items.add(liquidityTarget(m, 0));
		return items;
	}

	private static List<Item> silverBullet(Moment m) {
		List<Item> items = new ArrayList<>();
		Optional<LocalTime> hour = SILVER_BULLETS.stream().filter(start -> inWindow(m, start, start.plusHours(1))).findFirst();
		items.add(hour.isPresent()
			? new Item("window", "Inside the " + hour.get().format(CLOCK) + "–" + hour.get().plusHours(1).format(CLOCK)
				+ " Silver Bullet hour.", true)
			: new Item("window", "Not in a Silver Bullet hour — 03:00, 10:00 or 14:00 New York, for an hour; it is "
				+ m.newYork().format(CLOCK) + ".", false));
		Optional<Hit> gap = hour.flatMap(start -> m.ours("FVG").stream()
			.filter(g -> !g.confirmedAt().isBefore(at(m.newYork().toLocalDate(), start)))
			.max(Comparator.comparing(Hit::confirmedAt)));
		items.add(gap.isPresent()
			? new Item("gap", "A " + m.way() + " fair value gap formed in this hour, " + m.price(gap.get().low()) + " to "
				+ m.price(gap.get().high()) + ".", true)
			: new Item("gap", "No " + m.way() + " fair value gap has formed in a Silver Bullet hour yet.", false));
		items.add(gap.isPresent() ? atZone(m, gap.get(), "retrace", "the gap")
			: new Item("retrace", "No gap to enter in.", false));
		items.add(liquidityTarget(m, SILVER_BULLET_MIN_TICKS));
		items.add(gap.isPresent() ? beyond(m, m.up() ? gap.get().low() : gap.get().high(), "the gap")
			: new Item("stop", "No gap to put the stop beyond.", false));
		return items;
	}

	private static List<Item> ote(Moment m) {
		List<Item> items = new ArrayList<>();
		Optional<Hit> shift = recentShift(m);
		items.add(shiftItem(m, shift));
		Optional<Hit> zone = latestStanding(m.ours("OTE"), RANGE_ENDS);
		items.add(zone.isPresent()
			? new Item("zone", "The " + m.way() + " leg's OTE zone, " + m.price(zone.get().low()) + " to "
				+ m.price(zone.get().high()) + ", still stands.", true)
			: new Item("zone", "No " + m.way() + " leg's OTE zone stands at this moment.", false));
		items.add(zone.isPresent() ? atZone(m, zone.get(), "retrace", "the OTE zone")
			: new Item("retrace", "No OTE zone to enter in.", false));
		items.add(zone.isPresent() ? beyond(m, number(zone.get(), "startPrice"), "the leg's start")
			: new Item("stop", "No leg to put the stop beyond its start.", false));
		return items;
	}

	/** Unicorn, breaker and mitigation entries: a zone still standing, price in it, the stop beyond it. */
	private static List<Item> zoneEntry(Moment m, List<String> detectors, String name) {
		List<Hit> zones = detectors.stream().flatMap(d -> m.ours(d).stream()).toList();
		Optional<Hit> zone = latestStanding(zones, BLOCK_ENDS);
		List<Item> items = new ArrayList<>();
		items.add(zone.isPresent()
			? new Item("zone", "A " + m.way() + " " + name + ", " + m.price(zone.get().low()) + " to "
				+ m.price(zone.get().high()) + ", still stands.", true)
			: new Item("zone", "No " + m.way() + " " + name + " stands at this moment.", false));
		items.add(zone.isPresent() ? atZone(m, zone.get(), "retrace", "the " + name)
			: new Item("retrace", "No zone to enter in.", false));
		items.add(zone.isPresent() ? beyond(m, m.up() ? zone.get().low() : zone.get().high(), "the " + name)
			: new Item("stop", "No zone to put the stop beyond.", false));
		return items;
	}

	private static List<Item> turtleSoup(Moment m) {
		List<Item> items = new ArrayList<>();
		Optional<Hit> soup = m.ours("TURTLE_SOUP").stream().filter(s -> m.within(s, SOUP_CANDLES))
			.max(Comparator.comparing(Hit::confirmedAt));
		items.add(soup.isPresent()
			? new Item("soup", "Turtle soup at the equal " + (m.up() ? "lows" : "highs") + " at " + m.price(number(soup.get(), "price"))
				+ ", in the last " + SOUP_CANDLES + " candles: swept, and closed back.", true)
			: new Item("soup", "No turtle soup at equal " + (m.up() ? "lows" : "highs") + " in the last " + SOUP_CANDLES
				+ " candles.", false));
		Optional<Bar> sweeping = soup.flatMap(s -> candle(m, s.end()));
		items.add(sweeping.isPresent()
			? beyond(m, m.up() ? sweeping.get().lowTicks() : sweeping.get().highTicks(), "the sweeping candle's "
				+ (m.up() ? "low" : "high"))
			: new Item("stop", "No sweep to put the stop beyond.", false));
		items.add(liquidityTarget(m, 0));
		return items;
	}

	private static List<Item> sessionReversal(Moment m) {
		List<Item> items = new ArrayList<>();
		LocalDate today = m.newYork().toLocalDate();
		Optional<Hit> judas = m.ours("JUDAS").stream()
			.filter(j -> j.confirmedAt().atZone(Killzone.NEW_YORK).toLocalDate().equals(today))
			.max(Comparator.comparing(Hit::confirmedAt));
		boolean london = judas.map(j -> "LONDON".equals(j.kind())).orElse(true);
		items.add(judas.isPresent()
			? new Item("judas", "A " + m.way() + " Judas swing this morning, in the " + (london ? "London" : "New York")
				+ " window: it went beyond " + m.price(number(judas.get(), "levelPrice")) + " and came back through the open.", true)
			: new Item("judas", "No " + m.way() + " Judas swing today.", false));
		Instant windowEnd = at(today, london ? LocalTime.of(5, 0) : LocalTime.of(10, 0));
		Optional<Hit> shift = judas.flatMap(j -> m.ours("MSS").stream()
			.filter(s -> s.confirmedAt().isAfter(j.anchor()) && !s.confirmedAt().isAfter(windowEnd))
			.max(Comparator.comparing(Hit::confirmedAt)));
		items.add(shift.isPresent()
			? new Item("shift", "A " + m.way() + " market structure shift followed it inside the window.", true)
			: new Item("shift", "No " + m.way() + " market structure shift inside the window after a Judas swing.", false));
		items.add(judas.isPresent() ? beyond(m, number(judas.get(), "extremeTicks"), "the Judas swing's extreme")
			: new Item("stop", "No Judas swing to put the stop beyond.", false));
		return items;
	}

	private static List<Item> openingRangeGap(Moment m) {
		List<Item> items = new ArrayList<>();
		boolean early = inWindow(m, LocalTime.of(9, 30), LocalTime.of(10, 0));
		items.add(early ? new Item("window", "In the first half hour after 09:30.", true)
			: new Item("window", "Not in the first half hour after 09:30 — it is " + m.newYork().format(CLOCK) + ".", false));
		LocalDate today = m.newYork().toLocalDate();
		Optional<Hit> gap = m.of("ORG").stream()
			.filter(g -> g.confirmedAt().atZone(Killzone.NEW_YORK).toLocalDate().equals(today) && g.low() < g.high())
			.max(Comparator.comparing(Hit::confirmedAt));
		if (gap.isEmpty()) {
			items.add(new Item("toward", "No opening range gap today to trade toward.", false));
			items.add(new Item("target", "No gap, so no middle to target.", false));
			return items;
		}
		long ceHalf = number(gap.get(), "ceHalfTicks");
		boolean toward = m.up() ? 2 * m.reference() < ceHalf : 2 * m.reference() > ceHalf;
		String ce = RuleText.halfPrice(ceHalf, m.tickSize());
		items.add(new Item("toward", toward ? "The trade points toward the gap's middle at " + ce + "."
			: "The trade points away from the gap's middle at " + ce + ".", toward));
		boolean atMiddle = m.up() ? 2 * m.target() <= ceHalf : 2 * m.target() >= ceHalf;
		items.add(new Item("target", atMiddle && toward ? "The target, at " + m.price(m.target()) + ", is at or short of it."
			: "The target, at " + m.price(m.target()) + ", reaches past the middle the model aims for.", atMiddle && toward));
		return items;
	}

	// --- shared steps ------------------------------------------------------------------------------

	private static Optional<Hit> recentShift(Moment m) {
		return m.ours("MSS").stream().filter(s -> m.within(s, RECENT_CANDLES)).max(Comparator.comparing(Hit::confirmedAt));
	}

	private static Item shiftItem(Moment m, Optional<Hit> shift) {
		if (shift.isEmpty()) {
			return new Item("shift", "No " + m.way() + " market structure shift in the last " + RECENT_CANDLES
				+ " candles — the sweep, displacement and close through structure the model starts from.", false);
		}
		Hit s = shift.get();
		return new Item("shift", "A " + m.way() + " market structure shift: a swing " + (m.up() ? "low" : "high") + " swept the one before it, "
			+ "and a candle closed " + (m.up() ? "above" : "below") + " " + m.price(number(s, "brokenPrice")) + ".", true);
	}

	private static Item atZone(Moment m, Hit zone, String id, String what) {
		boolean in = m.reference() >= zone.low() && m.reference() <= zone.high();
		return new Item(id, in ? "Price, at " + m.price(m.reference()) + ", is in " + what + ", " + m.price(zone.low()) + " to "
			+ m.price(zone.high()) + "."
			: "Price, at " + m.price(m.reference()) + ", is not in " + what + ", " + m.price(zone.low()) + " to "
				+ m.price(zone.high()) + " — the model enters on the retrace into it.", in);
	}

	private static Item beyond(Moment m, long level, String what) {
		boolean ok = m.up() ? m.stop() < level : m.stop() > level;
		return new Item("stop", "The stop, at " + m.price(m.stop()) + ", is " + (ok ? "" : "not ") + (m.up() ? "below " : "above ")
			+ what + ", at " + m.price(level) + ".", ok);
	}

	/** Resting liquidity on the far side that the target reaches, at least {@code minTicks} away. */
	private static Item liquidityTarget(Moment m, long minTicks) {
		String side = m.up() ? "BSL" : "SSL";
		String name = m.up() ? "buy-side" : "sell-side";
		Predicate<Long> reached = p -> m.up() ? p > m.reference() && p <= m.target() : p < m.reference() && p >= m.target();
		Optional<Long> level = standing(m.of("LIQUIDITY"), Set.of("TAKEN", "EXPIRED")).stream()
			.filter(l -> side.equals(l.evidence().get("side")))
			.map(l -> number(l, "price")).filter(reached)
			.min(Comparator.comparing(p -> Math.abs(p - m.reference())));
		if (level.isEmpty()) {
			return new Item("target", "No resting " + name + " liquidity between the price and the target at "
				+ m.price(m.target()) + " — the model targets the opposing liquidity.", false);
		}
		long room = Math.abs(m.target() - m.reference());
		if (room < minTicks) {
			return new Item("target", "The target reaches resting " + name + " liquidity at " + m.price(level.get())
				+ ", but only " + m.price(room) + " points away; the model asks for " + m.price(minTicks) + ".", false);
		}
		return new Item("target", "The target, at " + m.price(m.target()) + ", reaches resting " + name + " liquidity at "
			+ m.price(level.get()) + ".", true);
	}

	/** The latest state of each life confirmed by the cursor, keeping the lives that have not ended. */
	private static List<Hit> standing(List<Hit> states, Set<String> ends) {
		Map<String, Hit> latest = new LinkedHashMap<>();
		for (Hit h : states) {
			latest.merge(h.anchor() + "|" + h.direction() + "|" + h.low() + "|" + h.high(), h,
				(a, b) -> b.confirmedAt().isAfter(a.confirmedAt()) ? b : a);
		}
		return latest.values().stream().filter(h -> !ends.contains(h.kind())).toList();
	}

	private static Optional<Hit> latestStanding(Collection<Hit> states, Set<String> ends) {
		return standing(List.copyOf(states), ends).stream().max(Comparator.comparing(Hit::anchor));
	}

	private static Optional<Killzone> killzone(Instant at) {
		for (Killzone zone : Killzone.values()) {
			if (zone.contains(at)) {
				return Optional.of(zone);
			}
		}
		return Optional.empty();
	}

	private static String name(Killzone zone) {
		String label = switch (zone) {
			case ASIA -> "Asia";
			case LONDON -> "London";
			case NEW_YORK_AM -> "New York AM";
			case NEW_YORK_PM -> "New York PM";
		};
		return label + ", " + zone.start().format(CLOCK) + "–" + zone.end().format(CLOCK);
	}

	/** The cursor lies in {@code [from, to)} New York time on its own date. */
	private static boolean inWindow(Moment m, LocalTime from, LocalTime to) {
		LocalTime t = m.newYork().toLocalTime();
		return !t.isBefore(from) && t.isBefore(to);
	}

	private static Instant at(LocalDate date, LocalTime time) {
		return date.atTime(time).atZone(Killzone.NEW_YORK).toInstant();
	}

	private static Optional<Bar> candle(Moment m, Instant openTime) {
		return m.bars().stream().filter(b -> b.openTime().equals(openTime)).findFirst();
	}

	private static long number(Hit hit, String key) {
		Object value = hit.evidence().get(key);
		if (!(value instanceof Number n)) {
			throw new IllegalStateException(hit.detector() + " has no " + key + " in its evidence");
		}
		return n.longValue();
	}
}
