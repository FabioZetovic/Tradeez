package com.tradeez.practice;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PracticeResultRepository extends JpaRepository<PracticeResult, UUID> {

	/** A session's results, in no particular order: {@code PracticeService} pairs them by number. */
	List<PracticeResult> findAllBySessionId(UUID sessionId);

	List<PracticeResult> findAllBySessionIdIn(Collection<UUID> sessionIds);
}
