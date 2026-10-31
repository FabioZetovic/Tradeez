package com.tradeez.identity;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface RevokedAccessTokenRepository extends JpaRepository<RevokedAccessToken, UUID> {

	/** Past their expiry the tokens are refused by their signature check anyway: the rows are no longer needed. */
	@Modifying
	@Transactional
	@Query("DELETE FROM RevokedAccessToken t WHERE t.expiresAt < :now")
	int deleteExpired(Instant now);
}
