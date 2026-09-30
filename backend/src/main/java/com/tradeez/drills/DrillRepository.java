package com.tradeez.drills;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Drills are only ever fetched together with their owner (DEC-022). */
public interface DrillRepository extends JpaRepository<Drill, UUID> {

	Optional<Drill> findByIdAndUserId(UUID id, UUID userId);

	long countByUserIdAndCreatedAtAfter(UUID userId, Instant after);
}
