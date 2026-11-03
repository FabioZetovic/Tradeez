package com.tradeez.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Issues and validates access tokens, and mints opaque refresh tokens.
 *
 * <p>DEC-032. Access tokens are short-lived JWTs so that revocation latency is bounded without a
 * database read on every request. Refresh tokens are <b>opaque random strings, not JWTs</b> —
 * they are checked against the database anyway, so there is nothing to gain from making them
 * self-describing and something to lose in leaking claims.
 */
@Service
public class JwtService {

	private static final String ISSUER = "tradeez";

	private final SecretKey key;
	private final Duration accessTokenTtl;
	private final Duration refreshTokenTtl;
	private final SecureRandom random = new SecureRandom();

	public JwtService(
			@Value("${tradeez.jwt.secret}") String secret,
			@Value("${tradeez.jwt.access-token-ttl}") Duration accessTokenTtl,
			@Value("${tradeez.jwt.refresh-token-ttl}") Duration refreshTokenTtl) {

		byte[] keyBytes = Base64.getDecoder().decode(secret);
		if (keyBytes.length < 32) {
			throw new IllegalStateException(
				"tradeez.jwt.secret must decode to at least 32 bytes (256 bits). "
					+ "Generate one with: openssl rand -base64 32");
		}
		this.key = Keys.hmacShaKeyFor(keyBytes);
		this.accessTokenTtl = accessTokenTtl;
		this.refreshTokenTtl = refreshTokenTtl;
	}

	public String issueAccessToken(AppUser user) {
		Instant now = Instant.now();
		return Jwts.builder()
			.issuer(ISSUER)
			// BUG-126: an id of its own, so this one token can be ended at sign-out.
			.id(UUID.randomUUID().toString())
			.subject(user.getId().toString())
			.claim("email", user.getEmail())
			.claim(GENERATION, user.getTokenGeneration())
			.issuedAt(java.util.Date.from(now))
			.expiration(java.util.Date.from(now.plus(accessTokenTtl)))
			.signWith(key)
			.compact();
	}

	/** The claim holding the account's token generation when the token was issued (BUG-077). */
	private static final String GENERATION = "gen";

	/**
	 * A valid access token's subject, the generation it was issued in (BUG-077), and its own id and expiry (BUG-126; a
	 * token issued before ids were added has none and simply runs out).
	 */
	public record AccessToken(UUID userId, int generation, UUID jti, Instant expiresAt) {
	}

	/**
	 * @return the token's user and generation, or empty when the token is absent, malformed, tampered with, expired,
	 *         or carries no generation. Callers must not distinguish between those cases to the client.
	 */
	public java.util.Optional<AccessToken> read(String token) {
		try {
			Claims claims = Jwts.parser()
				.verifyWith(key)
				.requireIssuer(ISSUER)
				.build()
				.parseSignedClaims(token)
				.getPayload();
			Integer generation = claims.get(GENERATION, Integer.class);
			if (generation == null) {
				return java.util.Optional.empty();
			}
			UUID jti = claims.getId() == null ? null : UUID.fromString(claims.getId());
			return java.util.Optional.of(new AccessToken(UUID.fromString(claims.getSubject()), generation, jti,
				claims.getExpiration().toInstant()));
		} catch (JwtException | IllegalArgumentException e) {
			return java.util.Optional.empty();
		}
	}

	/** A 256-bit URL-safe random string. Never stored in plaintext — see {@link #hash(String)}. */
	public String generateRefreshToken() {
		byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	/**
	 * SHA-256, hex encoded. Deliberately not BCrypt: this value is high-entropy random rather than a
	 * human-chosen password, so it is not brute-forceable, and refresh happens often enough that a
	 * deliberately slow hash would be a self-inflicted performance problem. It also needs to be
	 * deterministic so the token can be looked up by hash in one indexed query.
	 */
	public String hash(String rawToken) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
				.digest(rawToken.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder(digest.length * 2);
			for (byte b : digest) {
				sb.append(Character.forDigit((b >> 4) & 0xF, 16));
				sb.append(Character.forDigit(b & 0xF, 16));
			}
			return sb.toString();
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 unavailable", e);
		}
	}

	public Duration getRefreshTokenTtl() {
		return refreshTokenTtl;
	}
}
