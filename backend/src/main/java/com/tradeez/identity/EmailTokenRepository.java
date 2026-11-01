package com.tradeez.identity;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface EmailTokenRepository extends JpaRepository<EmailToken, UUID> {

	Optional<EmailToken> findByTokenHash(String tokenHash);

	/** Every unused link of a kind for an account: spent when one is opened, and when a newer reset link is made. */
	// Not cleared after: the caller goes on to change the account it holds (AuthService.verify), which a clear would detach.
	@Modifying(flushAutomatically = true)
	@Query("UPDATE EmailToken t SET t.usedAt = :now WHERE t.user.id = :userId AND t.purpose = :purpose AND t.usedAt IS NULL")
	int spendAll(UUID userId, EmailToken.Purpose purpose, Instant now);

	/** Links used or expired before the given moment: no longer needed for anything. */
	@Modifying
	@Transactional
	@Query("DELETE FROM EmailToken t WHERE t.expiresAt < :before OR t.usedAt < :before")
	int deleteFinishedBefore(Instant before);
}
