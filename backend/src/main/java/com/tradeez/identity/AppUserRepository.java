package com.tradeez.identity;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

	/**
	 * Case-insensitive lookup, matching the {@code ux_app_user_email_lower} unique index. Registering
	 * as {@code Fabio@x.com} and logging in as {@code fabio@x.com} must be the same account.
	 */
	@Query("SELECT u FROM AppUser u WHERE lower(u.email) = lower(:email)")
	Optional<AppUser> findByEmailIgnoreCase(String email);

	@Query("SELECT COUNT(u) > 0 FROM AppUser u WHERE lower(u.email) = lower(:email)")
	boolean existsByEmailIgnoreCase(String email);
}
