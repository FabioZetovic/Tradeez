package com.tradeez.practice;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Sessions are only ever fetched together with their owner (DEC-022). The service never looks a
 * session up by id alone, so an id taken from a URL cannot skip the ownership check.
 */
public interface PracticeSessionRepository extends JpaRepository<PracticeSession, UUID> {

	Optional<PracticeSession> findByIdAndUserId(UUID id, UUID userId);

	List<PracticeSession> findTop50ByUserIdOrderByCreatedAtDesc(UUID userId);

	long countByUserIdAndCreatedAtAfter(UUID userId, Instant after);
}
