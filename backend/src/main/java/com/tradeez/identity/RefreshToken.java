package com.tradeez.identity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A rotating refresh token. Maps {@code refresh_token} from V1__baseline.sql.
 *
 * <p>DEC-032. Two deliberate properties:
 * <ul>
 *   <li>Only a <b>hash</b> of the token is stored, so a database leak yields nothing usable.</li>
 *   <li>{@code replacedBy} records the rotation chain. That turns a replayed old token from
 *       "merely rejected" into <b>detectable theft</b> — see
 *       {@link AuthService#refresh(String, String)}, which revokes the whole family on reuse.</li>
 * </ul>
 */
@Entity
@Table(name = "refresh_token")
public class RefreshToken {

	@Id
	@GeneratedValue
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private AppUser user;

	@Column(name = "token_hash", nullable = false, unique = true)
	private String tokenHash;

	@Column(name = "issued_at", nullable = false)
	private Instant issuedAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@Column(name = "replaced_by")
	private UUID replacedBy;

	@Column(name = "user_agent")
	private String userAgent;

	/**
	 * Optimistic lock (BUG-001). Two refresh requests in genuinely parallel transactions could each
	 * read this row as un-rotated and each mint a successor, leaving one valid but orphaned. With a
	 * version column the second commit fails instead, so exactly one rotation wins.
	 */
	@Version
	@Column(name = "version", nullable = false)
	private long version;

	protected RefreshToken() {
		// for JPA
	}

	public RefreshToken(AppUser user, String tokenHash, Instant expiresAt, String userAgent) {
		this.user = user;
		this.tokenHash = tokenHash;
		this.issuedAt = Instant.now();
		this.expiresAt = expiresAt;
		this.userAgent = userAgent;
	}

	public boolean isActive(Instant now) {
		return revokedAt == null && expiresAt.isAfter(now);
	}

	/** True when this token has already been rotated away — presenting it again means reuse. */
	public boolean isAlreadyRotated() {
		return replacedBy != null;
	}

	public void revoke(Instant when) {
		if (this.revokedAt == null) {
			this.revokedAt = when;
		}
	}

	public void rotateTo(UUID successorId, Instant when) {
		this.replacedBy = successorId;
		revoke(when);
	}

	public UUID getId() {
		return id;
	}

	public AppUser getUser() {
		return user;
	}

	public Instant getRevokedAt() {
		return revokedAt;
	}

	public UUID getReplacedBy() {
		return replacedBy;
	}

}
