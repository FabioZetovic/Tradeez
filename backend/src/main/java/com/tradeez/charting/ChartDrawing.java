package com.tradeez.charting;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * A drawing on a chart. Maps {@code chart_drawing} from V4.
 *
 * <p>DEC-024 — anchors hold <b>raw</b> prices as integer ticks. Never pixels, which depend on zoom
 * and window size; never adjusted prices, because a later contract roll would shift every line the
 * user had drawn.
 *
 * <p>The id is supplied by the client rather than generated here. The browser creates a drawing the
 * instant the user presses the mouse and needs a stable identity for it immediately — through the
 * drag, the undo history and the eventual save. Letting the server rename it on first persist would
 * mean reconciling ids mid-gesture.
 */
@Entity
@Table(name = "chart_drawing")
public class ChartDrawing {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "instrument_id", nullable = false)
	private UUID instrumentId;

	@Column(name = "kind", nullable = false)
	private String kind;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "anchors", nullable = false)
	private List<Map<String, Object>> anchors;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "style", nullable = false)
	private Map<String, Object> style;

	/** A label's words, or a zone's name (V11). Null for every other kind. */
	@Column(name = "text")
	private String text;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected ChartDrawing() {
		// for JPA
	}

	public ChartDrawing(UUID id, UUID userId, UUID instrumentId, String kind,
			List<Map<String, Object>> anchors, Map<String, Object> style, String text) {
		this.id = id;
		this.userId = userId;
		this.instrumentId = instrumentId;
		this.kind = kind;
		this.anchors = anchors;
		this.style = style;
		this.text = text;

		// Set here, not only in @PrePersist. The id is client-assigned, so Spring Data sees a
		// non-null id, concludes the entity is not new, and routes save() through merge() — where
		// the persist callback does not reliably fire. Relying on it produced a null created_at.
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	public void update(String kind, List<Map<String, Object>> anchors, Map<String, Object> style,
			String text) {
		this.kind = kind;
		this.anchors = anchors;
		this.style = style;
		this.text = text;
	}

	@PrePersist
	void onCreate() {
		Instant now = Instant.now(); // INVARIANT 4 — UTC.
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public UUID getInstrumentId() {
		return instrumentId;
	}

	public String getKind() {
		return kind;
	}

	public List<Map<String, Object>> getAnchors() {
		return anchors;
	}

	public Map<String, Object> getStyle() {
		return style;
	}

	public String getText() {
		return text;
	}
}
