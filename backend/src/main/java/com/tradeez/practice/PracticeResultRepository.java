package com.tradeez.practice;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PracticeResultRepository extends JpaRepository<PracticeResult, UUID> {

	Optional<PracticeResult> findBySessionId(UUID sessionId);

	List<PracticeResult> findAllBySessionIdIn(Collection<UUID> sessionIds);
}
