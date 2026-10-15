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

	/**
	 * Hits on a simulated market that touch {@code [from, to)} and were confirmed by
	 * {@code confirmedBy} — what an overlay may show at that moment (DEC-009). In a total order: the
	 * states of one gap share its start, and a replay compares answers in order.
	 */
	@Query("""
		select s from DetectedSetup s
		where s.instrumentId = :instrumentId and s.seed = :seed
		  and s.generatorVersion = :generatorVersion and s.timeframe = :timeframe
		  and s.detector = :detector and s.ruleVersion = :ruleVersion
		  and s.endTime >= :from and s.startTime < :to and s.confirmedAt <= :confirmedBy
		order by s.startTime, s.confirmedAt, s.anchorTime, s.direction, s.kind""")
	List<DetectedSetup> findVisibleSimulated(
		@Param("instrumentId") UUID instrumentId,
		@Param("seed") long seed,
		@Param("generatorVersion") String generatorVersion,
		@Param("timeframe") String timeframe,
		@Param("detector") String detector,
		@Param("ruleVersion") String ruleVersion,
		@Param("from") Instant from,
		@Param("to") Instant to,
		@Param("confirmedBy") Instant confirmedBy);

	/** The same for real data, which has no seed: one market per instrument. */
	@Query("""
		select s from DetectedSetup s
		where s.instrumentId = :instrumentId and s.seed is null and s.timeframe = :timeframe
		  and s.detector = :detector and s.ruleVersion = :ruleVersion
		  and s.endTime >= :from and s.startTime < :to and s.confirmedAt <= :confirmedBy
		order by s.startTime, s.confirmedAt, s.anchorTime, s.direction, s.kind""")
	List<DetectedSetup> findVisibleReal(
		@Param("instrumentId") UUID instrumentId,
		@Param("timeframe") String timeframe,
		@Param("detector") String detector,
		@Param("ruleVersion") String ruleVersion,
		@Param("from") Instant from,
		@Param("to") Instant to,
		@Param("confirmedBy") Instant confirmedBy);
}
