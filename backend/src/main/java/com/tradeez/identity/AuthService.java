package com.tradeez.identity;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tradeez.identity.dto.AuthDtos.LoginRequest;
import com.tradeez.identity.dto.AuthDtos.RegisterRequest;

/**
 * Registration, login, refresh-with-rotation, and logout.
 *
 * <p>Implements DEC-032. The interesting part is {@link #refresh}: refresh tokens rotate on every
 * use, and presenting an already-rotated token is treated as evidence of theft rather than as a
 * harmless mistake.
 */
@Service
public class AuthService {

	private static final Logger log = LoggerFactory.getLogger(AuthService.class);

	private final AppUserRepository users;
	private final RefreshTokenRepository refreshTokens;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;

	public AuthService(AppUserRepository users, RefreshTokenRepository refreshTokens,
			PasswordEncoder passwordEncoder, JwtService jwtService) {
		this.users = users;
		this.refreshTokens = refreshTokens;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
	}

	/** Issued credentials. The raw refresh token exists only long enough to become a cookie. */
	public record IssuedTokens(AppUser user, String accessToken, String refreshToken) {
	}

	@Transactional
	public IssuedTokens register(RegisterRequest request, String userAgent) {
		if (users.existsByEmailIgnoreCase(request.email())) {
			throw new EmailAlreadyRegisteredException();
		}
		AppUser user = users.save(new AppUser(
			request.email().trim(),
			passwordEncoder.encode(request.password()),
			request.displayName().trim()));
		return issueFor(user, userAgent);
	}

	@Transactional
	public IssuedTokens login(LoginRequest request, String userAgent) {
		AppUser user = users.findByEmailIgnoreCase(request.email())
			.orElse(null);

		// Hash even when the account does not exist, so response timing does not reveal which
		// addresses are registered. The error returned is identical either way.
		if (user == null) {
			passwordEncoder.encode(request.password());
			throw new InvalidCredentialsException();
		}
		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new InvalidCredentialsException();
		}
		return issueFor(user, userAgent);
	}

	/**
	 * Rotates a refresh token: the presented token is revoked and a fresh pair is issued.
	 *
	 * <p>If the presented token has <b>already been rotated</b>, someone is replaying an old token.
	 * We cannot tell whether the legitimate user or an attacker holds the newer one, so the only
	 * safe response is to revoke the entire family and force a fresh login. This is the reason
	 * {@code replaced_by} exists in the schema rather than a simple boolean.
	 */
	/*
	 * noRollbackFor is load-bearing, not a workaround.
	 *
	 * The reuse branch below revokes every token for the account and *then* throws. Without this,
	 * the throw rolls the transaction back and undoes the revocation: the attacker would receive a
	 * 401 while the victim's sessions quietly stayed alive, and the security response would be a
	 * no-op that still looked correct from the outside. A test asserts exactly this.
	 *
	 * Nothing else is written on that path, so committing the transaction commits only the
	 * revocation.
	 */
	@Transactional(noRollbackFor = TokenReuseDetectedException.class)
	public IssuedTokens refresh(String rawRefreshToken, String userAgent) {
		String hash = jwtService.hash(rawRefreshToken);
		RefreshToken stored = refreshTokens.findByTokenHash(hash)
			.orElseThrow(InvalidRefreshTokenException::new);

		Instant now = Instant.now();

		if (stored.isAlreadyRotated()) {
			UUID userId = stored.getUser().getId();
			int revoked = refreshTokens.revokeAllForUser(userId, now);
			log.warn("Refresh token reuse detected for user {}; revoked {} active token(s). "
				+ "All sessions for this account have been invalidated.", userId, revoked);
			throw new TokenReuseDetectedException();
		}

		if (!stored.isActive(now)) {
			throw new InvalidRefreshTokenException();
		}

		AppUser user = stored.getUser();
		IssuedTokens issued = issueFor(user, userAgent);

		RefreshToken successor = refreshTokens.findByTokenHash(jwtService.hash(issued.refreshToken()))
			.orElseThrow(() -> new IllegalStateException("successor token was not persisted"));
		stored.rotateTo(successor.getId(), now);
		refreshTokens.save(stored);

		return issued;
	}

	/** Revokes the presented token only. Other devices stay signed in. */
	@Transactional
	public void logout(String rawRefreshToken) {
		if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
			return;
		}
		refreshTokens.findByTokenHash(jwtService.hash(rawRefreshToken))
			.ifPresent(token -> {
				token.revoke(Instant.now());
				refreshTokens.save(token);
			});
	}

	@Transactional
	public void logoutEverywhere(UUID userId) {
		refreshTokens.revokeAllForUser(userId, Instant.now());
	}

	private IssuedTokens issueFor(AppUser user, String userAgent) {
		String rawRefresh = jwtService.generateRefreshToken();
		refreshTokens.save(new RefreshToken(
			user,
			jwtService.hash(rawRefresh),
			Instant.now().plus(jwtService.getRefreshTokenTtl()),
			userAgent));
		return new IssuedTokens(user, jwtService.issueAccessToken(user), rawRefresh);
	}

	// --- Failures. Deliberately distinct types; the controller maps them all to opaque responses.

	public static class EmailAlreadyRegisteredException extends RuntimeException {
	}

	public static class InvalidCredentialsException extends RuntimeException {
	}

	public static class InvalidRefreshTokenException extends RuntimeException {
	}

	public static class TokenReuseDetectedException extends RuntimeException {
	}
}
