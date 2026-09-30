package com.tradeez.analysis;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DetectedSetupRepository extends JpaRepository<DetectedSetup, UUID> {

	/**
	 * Hits for one simulated market, rule and timeframe, anchored inside {@code [from, to)}.
	 *
	 * <p>Parameters are compared by the caller: JPQL has no JSONB equality, and today each rule
	 * version has exactly one parameter set, so the filter is a formality that keeps the claim honest
	 * rather than a performance concern.
	 */
	@Query("""
		select s from DetectedSetup s
		where s.instrumentId = :instrumentId and s.seed = :seed
		  and s.generatorVersion = :generatorVersion and s.timeframe = :timeframe
		  and s.detector = :detector and s.ruleVersion = :ruleVersion
		  and s.anchorTime >= :from and s.anchorTime < :to
		order by s.anchorTime""")
	List<DetectedSetup> findSimulated(
		@Param("instrumentId") UUID instrumentId,
		@Param("seed") long seed,
		@Param("generatorVersion") String generatorVersion,
		@Param("timeframe") String timeframe,
		@Param("detector") String detector,
		@Param("ruleVersion") String ruleVersion,
		@Param("from") Instant from,
		@Param("to") Instant to);
}
