package com.tradeez.identity;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * A registered learner. Maps the {@code app_user} table from V1__baseline.sql.
 *
 * <p>DEC-022 — every user-owned table references this from its first migration. Retrofitting
 * ownership onto practice sessions, drawings and journals later would be expensive and error-prone.
 *
 * <p>Schema is owned by Flyway (DEC-030) and Hibernate runs in {@code validate} mode, so this class
 * must match the migration exactly rather than the other way round.
 */
@Entity
@Table(name = "app_user")
public class AppUser {

	@Id
	@GeneratedValue
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "email", nullable = false)
	private String email;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Column(name = "display_name", nullable = false)
	private String displayName;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "settings", nullable = false)
	private Map<String, Object> settings = new HashMap<>();

	protected AppUser() {
		// for JPA
	}

	public AppUser(String email, String passwordHash, String displayName) {
		this.email = email;
		this.passwordHash = passwordHash;
		this.displayName = displayName;
	}

	@PrePersist
	void onCreate() {
		// INVARIANT 4 — UTC everywhere.
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
		if (this.settings == null) {
			this.settings = new HashMap<>();
		}
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public String getDisplayName() {
		return displayName;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Map<String, Object> getSettings() {
		return settings;
	}
}
