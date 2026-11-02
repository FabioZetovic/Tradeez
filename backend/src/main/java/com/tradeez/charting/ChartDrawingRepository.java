package com.tradeez.charting;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChartDrawingRepository extends JpaRepository<ChartDrawing, UUID> {

	List<ChartDrawing> findAllByUserIdAndInstrumentIdOrderByCreatedAtAsc(
		UUID userId, UUID instrumentId);

	/**
	 * Always looked up with the owner, never by id alone.
	 *
	 * <p>DEC-022 — this is what stops one user editing another's drawing by guessing a UUID. The
	 * ownership check belongs in the query rather than in a controller {@code if}, because the
	 * query is impossible to forget.
	 */
	Optional<ChartDrawing> findByIdAndUserId(UUID id, UUID userId);

	/** BUG-152: the market too, so a delete sent to one chart's address cannot remove a drawing on another. */
	long deleteByIdAndUserIdAndInstrumentId(UUID id, UUID userId, UUID instrumentId);
}
