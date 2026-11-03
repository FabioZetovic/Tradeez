package com.tradeez.identity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** BUG-126 (T57): an access token signed out before it expired, kept until it would have expired anyway. */
@Entity
@Table(name = "revoked_access_token")
public class RevokedAccessToken {

	@Id
	private UUID jti;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	protected RevokedAccessToken() {
	}

	public RevokedAccessToken(UUID jti, Instant expiresAt) {
		this.jti = jti;
		this.expiresAt = expiresAt;
	}
}
