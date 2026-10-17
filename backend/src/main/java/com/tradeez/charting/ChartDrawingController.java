package com.tradeez.charting;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tradeez.identity.JwtAuthenticationFilter.AuthenticatedUser;
import com.tradeez.marketdata.Instrument;
import com.tradeez.marketdata.MarketDataService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Saved chart drawings, scoped to the signed-in user and one instrument.
 *
 * <p>DEC-022 — the owner comes from the authenticated principal and never from the request. A
 * client cannot name whose drawings it wants, which is the pattern every user-scoped endpoint in
 * this project follows.
 */
@RestController
@RequestMapping("/api/charts/{symbol}/drawings")
@Tag(name = "Drawings", description = "Saved chart annotations")
public class ChartDrawingController {

	/**
	 * Cap per user per instrument.
	 *
	 * <p>Drawings are created by dragging, so a stuck mouse or a scripted client could otherwise
	 * insert without limit. The number is far above what anyone marks up by hand.
	 */
	static final int MAX_DRAWINGS_PER_CHART = 500;

	/**
	 * Mirrors the database's CHECK constraint.
	 *
	 * <p>Duplicated on purpose. The constraint is the guarantee — nothing reaches the table without
	 * passing it — but letting it be the only check turns a client typo into a 500 and a stack
	 * trace. An unknown tool name is the client's mistake, and should read as one.
	 */
	static final Map<String, Integer> ANCHOR_COUNT = Map.of(
		"horizontal-line", 1,
		"trend-line", 2,
		"rectangle", 2,
		"position", 3,
		// DEC-048 (T8): ICT's Fibonacci levels, a zone with its consequent encroachment, a text label.
		"fibonacci", 2,
		"zone", 2,
		"label", 1);

	/** Only these carry text: a label's words, a zone's name. Mirrors {@code ck_chart_drawing_text}. */
	private static final java.util.Set<String> TEXT_KINDS = java.util.Set.of("label", "zone");

	static final int MAX_TEXT = 80;

	/**
	 * A style is a colour, a width and a fill. Unbounded, it was any JSON of any size, stored per drawing up to
	 * {@link #MAX_DRAWINGS_PER_CHART} per chart — room for one account to fill the database (BUG-072).
	 */
	static final int MAX_STYLE_ENTRIES = 8;
	static final int MAX_STYLE_CHARS = 512;

	private final ChartDrawingRepository drawings;
	private final MarketDataService marketData;

	public ChartDrawingController(ChartDrawingRepository drawings, MarketDataService marketData) {
		this.drawings = drawings;
		this.marketData = marketData;
	}

	@Schema(description = "A point in market space. priceTicks is an INTEGER TICK COUNT (DEC-024).")
	public record AnchorDto(
		@NotNull Long time,
		@NotNull Long priceTicks) {
	}

	@Schema(description = "A drawing. The client supplies the id so a shape has a stable identity "
		+ "from the moment the drag starts.")
	public record DrawingDto(
		@NotNull UUID id,
		@NotBlank String kind,
		@NotEmpty List<@Valid AnchorDto> anchors,
		Map<String, Object> style,
		@Schema(description = "A label's words (required) or a zone's name (optional); at most 80 characters")
		@Size(max = MAX_TEXT) String text) {
	}

	@GetMapping
	@Operation(summary = "Every drawing this user has saved on this instrument")
	public List<DrawingDto> list(
			@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable String symbol) {

		Instrument instrument = marketData.requireInstrument(symbol);
		return drawings
			.findAllByUserIdAndInstrumentIdOrderByCreatedAtAsc(principal.id(), instrument.getId())
			.stream()
			.map(ChartDrawingController::toDto)
			.toList();
	}

	@PutMapping("/{id}")
	@Operation(summary = "Create or update a drawing",
		description = "Idempotent by id, so a retried save after a dropped connection cannot "
			+ "produce a duplicate shape.")
	@Transactional
	public ResponseEntity<DrawingDto> save(
			@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable String symbol,
			@PathVariable UUID id,
			@Valid @RequestBody DrawingDto body) {

		if (!id.equals(body.id())) {
			throw new IllegalArgumentException("Drawing id in the path and body must match.");
		}
		Integer anchorCount = ANCHOR_COUNT.get(body.kind());
		if (anchorCount == null) {
			throw new IllegalArgumentException(
				"Unknown drawing kind '" + body.kind() + "'. Supported: " + ANCHOR_COUNT.keySet());
		}
		// A shape with the wrong number of points cannot be drawn, and saved it would break the chart
		// every time it loaded. The database insists too (V11); this makes it a 400, not a 500.
		if (body.anchors().size() != anchorCount) {
			throw new IllegalArgumentException("A " + body.kind() + " has " + anchorCount
				+ (anchorCount == 1 ? " anchor" : " anchors") + ", not " + body.anchors().size() + ".");
		}
		String text = body.text() == null || body.text().isBlank() ? null : body.text().strip();
		if (text != null && !TEXT_KINDS.contains(body.kind())) {
			throw new IllegalArgumentException("Only labels and zones carry text.");
		}
		if (text == null && "label".equals(body.kind())) {
			throw new IllegalArgumentException("A label needs some text.");
		}

		Instrument instrument = marketData.requireInstrument(symbol);
		List<Map<String, Object>> anchors = body.anchors().stream()
			.map(a -> Map.<String, Object>of("time", a.time(), "priceTicks", a.priceTicks()))
			.toList();
		Map<String, Object> style = body.style() == null ? Map.of() : body.style();
		if (style.size() > MAX_STYLE_ENTRIES || style.toString().length() > MAX_STYLE_CHARS) {
			throw new IllegalArgumentException("A drawing's style is a few colours and widths: at most "
				+ MAX_STYLE_ENTRIES + " entries and " + MAX_STYLE_CHARS + " characters.");
		}

		var existing = drawings.findByIdAndUserId(id, principal.id());

		// The owner-scoped lookup above protects the READ path, and on its own it is not enough.
		//
		// The id is client-assigned, so Spring Data sees a non-null id, decides the entity is not
		// new, and routes save() through merge() — which matches on the primary key alone and
		// happily rewrites a row belonging to someone else, user_id included. A second user writing
		// to a UUID they did not own therefore took ownership of that drawing. A test caught it.
		//
		// So ownership is checked against the whole table before creating anything. 404 rather than
		// 409: an id the caller cannot write to should be indistinguishable from one that does not
		// exist, or the endpoint becomes a way to probe which ids are in use.
		if (existing.isEmpty() && drawings.existsById(id)) {
			throw new DrawingNotYoursException();
		}

		if (existing.isPresent()) {
			existing.get().update(body.kind(), anchors, style, text);
			return ResponseEntity.ok(toDto(drawings.save(existing.get())));
		}

		long count = drawings
			.findAllByUserIdAndInstrumentIdOrderByCreatedAtAsc(principal.id(), instrument.getId())
			.size();
		if (count >= MAX_DRAWINGS_PER_CHART) {
			throw new TooManyDrawingsException();
		}

		ChartDrawing created = new ChartDrawing(
			id, principal.id(), instrument.getId(), body.kind(), anchors, style, text);
		return ResponseEntity.status(HttpStatus.CREATED).body(toDto(drawings.save(created)));
	}

	@DeleteMapping("/{id}")
	@Operation(summary = "Delete a drawing")
	@Transactional
	public ResponseEntity<Void> delete(
			@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable String symbol,
			@PathVariable UUID id) {

		marketData.requireInstrument(symbol);
		// Deleting something already gone is a success: the client asked for it to be absent, and it
		// is. Retries after a dropped response must not fail.
		drawings.deleteByIdAndUserId(id, principal.id());
		return ResponseEntity.noContent().build();
	}

	private static DrawingDto toDto(ChartDrawing drawing) {
		List<AnchorDto> anchors = drawing.getAnchors().stream()
			.map(a -> new AnchorDto(
				((Number) a.get("time")).longValue(),
				((Number) a.get("priceTicks")).longValue()))
			.toList();
		return new DrawingDto(drawing.getId(), drawing.getKind(), anchors, drawing.getStyle(),
			drawing.getText());
	}

	/** Raised when an id exists but belongs to someone else. Surfaced as 404, never 403. */
	public static class DrawingNotYoursException extends RuntimeException {
	}

	public static class TooManyDrawingsException extends RuntimeException {
		public TooManyDrawingsException() {
			super("This chart already has the maximum of " + MAX_DRAWINGS_PER_CHART + " drawings.");
		}
	}
}
