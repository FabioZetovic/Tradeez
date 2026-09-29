package com.tradeez.practice;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TradePlanRepository extends JpaRepository<TradePlan, UUID> {

	Optional<TradePlan> findBySessionId(UUID sessionId);

	boolean existsBySessionId(UUID sessionId);

	List<TradePlan> findAllBySessionIdIn(Collection<UUID> sessionIds);
}
