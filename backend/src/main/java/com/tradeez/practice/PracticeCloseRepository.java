package com.tradeez.practice;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PracticeCloseRepository extends JpaRepository<PracticeClose, UUID> {

	List<PracticeClose> findAllBySessionId(UUID sessionId);

	List<PracticeClose> findAllBySessionIdIn(Collection<UUID> sessionIds);
}
