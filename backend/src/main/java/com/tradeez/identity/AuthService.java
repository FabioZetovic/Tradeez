package com.tradeez.identity;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tradeez.identity.PasswordPolicy.WeakPasswordException;
import com.tradeez.identity.dto.AuthDtos.ChangePasswordRequest;
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

	/**
	 * How long after a rotation a replay is still assumed to be the same client racing itself
	 * rather than a thief (BUG-001). Long enough to cover a browser firing parallel requests,
	 * far shorter than any realistic steal-and-replay.
	 */
	public static final Duration REUSE_GRACE = Duration.ofSeconds(20);

	/** Failed sign-ins allowed against a single account before it is throttled (BUG-002). */
	public static final int ACCOUNT_ATTEMPTS = 10;
	public static final Duration ACCOUNT_WINDOW = Duration.ofMinutes(15);

	/** Password changes allowed per account in {@link #ACCOUNT_WINDOW}, right or wrong (DEC-064). */
	public static final int PASSWORD_CHANGES = 5;

	/**
	 * Hashed in place of a password too long for BCrypt to hash (BUG-066), so such a sign-in takes the time
	 * any other does. Any string of at most 72 bytes would do.
	 */
	private static final String TIMING_STAND_IN = "a stand-in of BCrypt's length";

	private final AppUserRepository users;
	private final RefreshTokenRepository refreshTokens;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final RateLimiter rateLimiter;
	private final PasswordPolicy passwordPolicy;

	public AuthService(AppUserRepository users, RefreshTokenRepository refreshTokens,
			PasswordEncoder passwordEncoder, JwtService jwtService, RateLimiter rateLimiter,
			PasswordPolicy passwordPolicy) {
		this.users = users;
		this.refreshTokens = refreshTokens;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.rateLimiter = rateLimiter;
		this.passwordPolicy = passwordPolicy;
	}

	/** Issued credentials. The raw refresh token exists only long enough to become a cookie. */
	public record IssuedTokens(AppUser user, String accessToken, String refreshToken) {
	}

	@Transactional
	public IssuedTokens register(RegisterRequest request, String userAgent) {
		// password-1.0.0 first: a weak password is refused before anything is looked up (DEC-064).
		passwordPolicy.require(request.password(), request.email(), request.displayName());
		if (users.existsByEmailIgnoreCase(request.email())) {
			throw new EmailAlreadyRegisteredException();
		}
		AppUser user;
		try {
			user = users.saveAndFlush(new AppUser(
				request.email().trim(),
				passwordEncoder.encode(request.password()),
				request.displayName().trim()));
		} catch (DataIntegrityViolationException raced) {
			// Two sign-ups for one address at once both pass the check above; the unique index on
			// lower(email) lets one in, and the other is the same answer as a taken address, not a 500.
			throw new EmailAlreadyRegisteredException();
		}
		return issueFor(user, userAgent);
	}

	@Transactional
	public IssuedTokens login(LoginRequest request, String userAgent) {
		// Per-account throttling, on top of the per-IP filter (BUG-002). The filter alone is not
		// enough: an attacker with a pool of addresses can spread guesses at one account across many
		// IPs and never trip a per-IP limit. Keyed on the normalised email so capitalisation cannot
		// be used to get a fresh quota.
		String throttleKey = "login:" + request.email().trim().toLowerCase(java.util.Locale.ROOT);
		if (!rateLimiter.tryAcquire(throttleKey, ACCOUNT_ATTEMPTS, ACCOUNT_WINDOW)) {
			throw new TooManyAttemptsException();
		}

		AppUser user = users.findByEmailIgnoreCase(request.email())
			.orElse(null);

		// Hash even when the account does not exist, so response timing does not reveal which
		// addresses are registered. The error returned is identical either way.
		//
		// BUG-066: BCrypt refuses to hash more than 72 bytes and compares only the first 72. Hashing a long
		// password for an unknown account threw — a 400 where a known account got a 401, which told the
		// two apart — and a known account accepted its password with anything typed after it. No stored
		// password is longer (password-1.0.0), so a longer one is simply wrong, at the usual cost.
		boolean tooLong = PasswordPolicy.tooLong(request.password());
		if (user == null || tooLong) {
			passwordEncoder.encode(tooLong ? TIMING_STAND_IN : request.password());
			throw new InvalidCredentialsException();
		}
		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new InvalidCredentialsException();
		}

		// Clear the counter on success, so someone who mistyped their password a few times is not
		// still locked out after getting it right — that would punish exactly the person the limit
		// exists to protect.
		rateLimiter.reset(throttleKey);
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
			// BUG-001. Rotation alone cannot distinguish an attacker replaying a stolen token from a
			// client that simply issued two refreshes at once — two tabs, a flaky connection, or a
			// page firing several calls on load are all enough. Treating the second as theft logged
			// the user out of every device because their own app raced itself.
			//
			// The discriminator is time. A legitimate duplicate arrives within moments of the
			// rotation it lost; a stolen token is replayed much later, because the thief has to
			// exfiltrate and use it. Inside a narrow grace window we therefore issue fresh tokens
			// and revoke nothing. This is the trade-off the OAuth security BCP recommends, and it is
			// deliberate: an attacker replaying within seconds of a real rotation does get a session,
			// which is a far smaller harm than logging honest users out at random.
			if (withinReuseGrace(stored, now)) {
				log.debug("Refresh token for user {} was already rotated {}ms ago; treating as a "
					+ "concurrent duplicate rather than reuse.",
					stored.getUser().getId(),
					Duration.between(stored.getRevokedAt(), now).toMillis());
				return issueFor(stored.getUser(), userAgent);
			}

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

	/**
	 * Changes the signed-in account's password (DEC-064): the current one must be right and the new one
	 * pass password-1.0.0. Every session is signed out — the reason to change a password is usually that
	 * someone else may know it — and this one is signed back in with fresh tokens.
	 *
	 * <p>Throttled per account as sign-in is, so a stolen session cannot be used to guess the password.
	 */
	@Transactional
	public IssuedTokens changePassword(UUID userId, ChangePasswordRequest request, String userAgent) {
		String throttleKey = "password:" + userId;
		if (!rateLimiter.tryAcquire(throttleKey, PASSWORD_CHANGES, ACCOUNT_WINDOW)) {
			throw new TooManyAttemptsException();
		}
		AppUser user = users.findById(userId).orElseThrow(InvalidRefreshTokenException::new);
		if (PasswordPolicy.tooLong(request.currentPassword())
				|| !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
			throw new WrongCurrentPasswordException();
		}
		if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())
				&& !PasswordPolicy.tooLong(request.newPassword())) {
			throw new WeakPasswordException(List.of("It is the password you have now."));
		}
		passwordPolicy.require(request.newPassword(), user.getEmail(), user.getDisplayName());

		user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
		users.save(user);
		int revoked = refreshTokens.revokeAllForUser(userId, Instant.now());
		log.info("Password changed for user {}; {} session(s) signed out.", userId, revoked);
		rateLimiter.reset(throttleKey);
		return issueFor(user, userAgent);
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

	/** True when this token was rotated so recently that a replay is almost certainly a duplicate. */
	private boolean withinReuseGrace(RefreshToken stored, Instant now) {
		Instant rotatedAt = stored.getRevokedAt();
		return rotatedAt != null
			&& !Duration.between(rotatedAt, now).isNegative()
			&& Duration.between(rotatedAt, now).compareTo(REUSE_GRACE) <= 0;
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

	public static class TooManyAttemptsException extends RuntimeException {
	}

	public static class WrongCurrentPasswordException extends RuntimeException {
	}
}
