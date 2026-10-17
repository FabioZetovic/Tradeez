package com.tradeez.clock;

import java.time.Instant;
import java.util.List;
import java.util.function.ToLongFunction;

import com.tradeez.clock.ClockLayer.LineKind;
import com.tradeez.clock.ClockLayer.WindowKind;

import io.swagger.v3.oas.annotations.media.Schema;

/** Wire shapes for the clock layer. Times are epoch seconds on the chart's own time basis. */
public final class ClockDtos {

	private ClockDtos() {
	}

	@Schema(description = """
		The New York clock over a chart: windows to shade and prices to draw (T7, DEC-047). Times are \
		on the same basis as the bars they go with — real epoch seconds on the Chart tab, the session's \
		fictional week in practice (DEC-045).""")
	public record ClockLayerResponse(
		Rules rules,
		List<WindowResponse> windows,
		List<LineResponse> lines,
		@Schema(description = """
			Practice only: when the layer can next change. Ask again once the cursor reaches it.""")
		Long nextChange) {
	}

	@Schema(description = "The rule versions this layer was built under (DEC-026)")
	public record Rules(String sessions, String opens, String levels, String liquidity, String ipda) {
	}

	@Schema(description = "TRADING_DAY (separator and weekday) | KILLZONE | SILVER_BULLET | MACRO, over [from, to)")
	public record WindowResponse(WindowKind kind, String label, long from, long to) {
	}

	@Schema(description = """
		OPEN | PREVIOUS_DAY | PREVIOUS_WEEK | SESSION: a price, in INTEGER TICKS, that matters over \
		[from, to). takenAt, for a high or low: the first minute that traded beyond it (liquidity-1.0.0); \
		absent while it rests.""")
	public record LineResponse(LineKind kind, String label, long from, long to, long priceTicks, Long takenAt) {
	}

	static final Rules RULES = new Rules(ClockLayerBuilder.SESSIONS, ClockLayerBuilder.OPENS,
		ClockLayerBuilder.LEVELS, ClockLayerBuilder.LIQUIDITY, ClockLayerBuilder.IPDA);

	/**
	 * @param time        how an instant is sent: as is, or behind a practice session's hidden date
	 * @param withNextChange whether the caller is a replay that asks again as its cursor moves
	 */
	public static ClockLayerResponse of(ClockLayer layer, ToLongFunction<Instant> time,
			boolean withNextChange) {
		return new ClockLayerResponse(
			RULES,
			layer.windows().stream()
				.map(w -> new WindowResponse(w.kind(), w.label(), time.applyAsLong(w.from()),
					time.applyAsLong(w.to())))
				.toList(),
			layer.lines().stream()
				.map(l -> new LineResponse(l.kind(), l.label(), time.applyAsLong(l.from()),
					time.applyAsLong(l.to()), l.priceTicks(),
					l.takenAt() == null ? null : time.applyAsLong(l.takenAt())))
				.toList(),
			withNextChange ? time.applyAsLong(layer.nextChange()) : null);
	}
}
