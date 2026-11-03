package com.tradeez.charting;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
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
	 * A style is a colour, a fill and a line width, each in the form the chart draws (BUG-084). Unbounded, it was any
	 * JSON of any size (BUG-072); counted, it was still any value — markup included — stored for a renderer to trust.
	 */
	private static final java.util.regex.Pattern COLOUR = java.util.regex.Pattern.compile(
		"#[0-9a-fA-F]{3,8}|rgba?\\(\\s*\\d{1,3}\\s*,\\s*\\d{1,3}\\s*,\\s*\\d{1,3}\\s*(,\\s*(0|1|0?\\.\\d{1,3})\\s*)?\\)");
	static final double MAX_LINE_WIDTH = 8;

	/**
	 * Where a drawing's anchors may lie (BUG-084): from 2000 to 2100, UTC epoch seconds, and within a billion ticks of
	 * zero. Any 64-bit number was stored before.
	 */
	static final long EARLIEST_TIME = 946_684_800L;
	static final long LATEST_TIME = 4_102_444_800L;
	static final long MAX_PRICE_TICKS = 1_000_000_000L;

	private final ChartDrawingRepository drawings;
	private final MarketDataService marketData;
	private final TransactionTemplate transactions;

	public ChartDrawingController(ChartDrawingRepository drawings, MarketDataService marketData,
			PlatformTransactionManager transactionManager) {
		this.drawings = drawings;
		this.marketData = marketData;
		this.transactions = new TransactionTemplate(transactionManager);
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
		// BUG-165: @NotNull on the element — @Valid alone let [null] through to a NullPointerException.
		@NotEmpty List<@NotNull @Valid AnchorDto> anchors,
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
	public ResponseEntity<DrawingDto> save(
			@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable String symbol,
			@PathVariable UUID id,
			@Valid @RequestBody DrawingDto body) {
		try {
			return transactions.execute(status -> saveOnce(principal, symbol, id, body));
		} catch (DataIntegrityViolationException raced) {
			// BUG-135: a new drawing's first saves can overlap (a drag sends several): both found no row, both inserted,
			// and the second broke the key — a 500 for a drawing that was saved. Once more, the row is there: an update
			// for its owner, still a 404 for anyone else.
			return transactions.execute(status -> saveOnce(principal, symbol, id, body));
		}
	}

	private ResponseEntity<DrawingDto> saveOnce(AuthenticatedUser principal, String symbol, UUID id, DrawingDto body) {

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
		for (AnchorDto anchor : body.anchors()) {
			// Both sides spelled out: Math.abs(Long.MIN_VALUE) is still negative.
			if (anchor.time() < EARLIEST_TIME || anchor.time() > LATEST_TIME
					|| anchor.priceTicks() > MAX_PRICE_TICKS || anchor.priceTicks() < -MAX_PRICE_TICKS) {
				throw new IllegalArgumentException("A drawing's points lie between the years 2000 and 2100, within "
					+ MAX_PRICE_TICKS + " ticks of zero.");
			}
		}
		Map<String, Object> style = body.style() == null ? Map.of() : body.style();
		if (!validStyle(style)) {
			throw new IllegalArgumentException("A drawing's style is a colour and a fill (#rrggbb or rgba(…)) and a line "
				+ "width up to " + (int) MAX_LINE_WIDTH + ", and nothing else.");
		}

		Instrument instrument = marketData.requireInstrument(symbol);
		List<Map<String, Object>> anchors = body.anchors().stream()
			.map(a -> Map.<String, Object>of("time", a.time(), "priceTicks", a.priceTicks()))
			.toList();

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
			// BUG-172: the row may be the caller's own, committed by an overlapping first save between the two queries
			// above. Asked again, it is visible now; only a row still not theirs is someone else's.
			existing = drawings.findByIdAndUserId(id, principal.id());
			if (existing.isEmpty()) {
				throw new DrawingNotYoursException();
			}
		}

		// BUG-152: the caller's drawing, but on another market — written to this chart's address by a page that had not
		// yet let go of the other market. Not this chart's, so as if it did not exist.
		if (existing.isPresent() && !existing.get().getInstrumentId().equals(instrument.getId())) {
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
		return ResponseEntity.status(HttpStatus.CREATED).body(toDto(drawings.saveAndFlush(created)));
	}

	@DeleteMapping("/{id}")
	@Operation(summary = "Delete a drawing")
	@Transactional
	public ResponseEntity<Void> delete(
			@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable String symbol,
			@PathVariable UUID id) {

		Instrument instrument = marketData.requireInstrument(symbol);
		// Deleting something already gone is a success: the client asked for it to be absent, and it
		// is. Retries after a dropped response must not fail.
		drawings.deleteByIdAndUserIdAndInstrumentId(id, principal.id(), instrument.getId());
		return ResponseEntity.noContent().build();
	}

	/** Only the keys the chart reads, each in the form it draws. */
	static boolean validStyle(Map<String, Object> style) {
		for (Map.Entry<String, Object> entry : style.entrySet()) {
			Object value = entry.getValue();
			boolean ok = switch (entry.getKey()) {
				case "color", "fill" -> value instanceof String colour && COLOUR.matcher(colour).matches();
				case "lineWidth" -> value instanceof Number width && width.doubleValue() > 0
					&& width.doubleValue() <= MAX_LINE_WIDTH;
				default -> false;
			};
			if (!ok) {
				return false;
			}
		}
		return true;
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
