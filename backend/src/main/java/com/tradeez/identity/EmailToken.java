package com.tradeez.identity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** T56: a single-use link sent by email, kept as the SHA-256 of its token. */
@Entity
@Table(name = "email_token")
public class EmailToken {

	public enum Purpose {
		/** Proves the address at sign-up. */
		VERIFY,
		/** Sets a new password. */
		RESET
	}

	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private AppUser user;

	@Enumerated(EnumType.STRING)
	@Column(name = "purpose", nullable = false)
	private Purpose purpose;

	@Column(name = "token_hash", nullable = false, unique = true)
	private String tokenHash;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "used_at")
	private Instant usedAt;

	/** A sign-up's link: the password chosen at that sign-up, set when the link is opened. Null otherwise. */
	@Column(name = "password_hash")
	private String passwordHash;

	protected EmailToken() {
	}

	EmailToken(AppUser user, Purpose purpose, String tokenHash, Instant now, Instant expiresAt, String passwordHash) {
		this.id = UUID.randomUUID();
		this.user = user;
		this.purpose = purpose;
		this.tokenHash = tokenHash;
		this.createdAt = now;
		this.expiresAt = expiresAt;
		this.passwordHash = passwordHash;
	}

	AppUser getUser() {
		return user;
	}

	String getPasswordHash() {
		return passwordHash;
	}

	Purpose getPurpose() {
		return purpose;
	}

	boolean usable(Instant now) {
		return usedAt == null && now.isBefore(expiresAt);
	}

	void use(Instant now) {
		usedAt = now;
	}
}
