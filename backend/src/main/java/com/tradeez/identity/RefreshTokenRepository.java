package com.tradeez.identity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

	Optional<RefreshToken> findByTokenHash(String tokenHash);

	List<RefreshToken> findAllByUserId(UUID userId);

	/**
	 * Revokes every live token for a user. Used on logout-everywhere and, importantly, on refresh
	 * token reuse — if a rotated token is presented again we cannot tell whether the attacker or the
	 * legitimate user holds the newer one, so the safe move is to invalidate the whole family and
	 * force a fresh login.
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("UPDATE RefreshToken t SET t.revokedAt = :now WHERE t.user.id = :userId AND t.revokedAt IS NULL")
	int revokeAllForUser(UUID userId, Instant now);
}
