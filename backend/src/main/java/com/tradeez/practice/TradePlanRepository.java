package com.tradeez.practice;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TradePlanRepository extends JpaRepository<TradePlan, UUID> {

	/** A session's plans, in no particular order: {@code PracticeService} pairs them by number. */
	List<TradePlan> findAllBySessionId(UUID sessionId);

	List<TradePlan> findAllBySessionIdIn(Collection<UUID> sessionIds);

	/** Every plan of a learner's, from all their sessions: what calibration (T23) and statistics (T25) read. */
	@Query("SELECT p FROM TradePlan p WHERE p.sessionId IN (SELECT s.id FROM PracticeSession s WHERE s.userId = :userId)")
	List<TradePlan> findAllOfUser(UUID userId);
}
