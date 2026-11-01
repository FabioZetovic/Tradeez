package com.tradeez.identity;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

	/**
	 * Case-insensitive lookup, matching the {@code ux_app_user_email_lower} unique index. Registering
	 * as {@code Fabio@x.com} and logging in as {@code fabio@x.com} must be the same account.
	 */
	@Query("SELECT u FROM AppUser u WHERE lower(u.email) = lower(:email)")
	Optional<AppUser> findByEmailIgnoreCase(String email);

	@Query("SELECT COUNT(u) > 0 FROM AppUser u WHERE lower(u.email) = lower(:email)")
	boolean existsByEmailIgnoreCase(String email);

	/** T56 (BUG-128): accounts whose address was never confirmed, made before the given moment. Children cascade. */
	@Modifying
	@Transactional
	@Query("DELETE FROM AppUser u WHERE u.emailVerifiedAt IS NULL AND u.createdAt < :before")
	int deleteUnconfirmedBefore(Instant before);
}
