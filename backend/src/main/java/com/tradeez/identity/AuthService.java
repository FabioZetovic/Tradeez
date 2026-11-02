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

	/**
	 * Failed sign-ins allowed against a single account from a single address before that address is throttled for it
	 * (BUG-002, BUG-076).
	 */
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
	private RevokedAccessTokenRepository revokedAccessTokens;

	/** BUG-126. Set by Spring; a test that builds this service by hand has no access tokens to sign out. */
	@org.springframework.beans.factory.annotation.Autowired(required = false)
	void setRevokedAccessTokens(RevokedAccessTokenRepository revokedAccessTokens) {
		this.revokedAccessTokens = revokedAccessTokens;
	}

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

	/** BUG-128: sign-ups the whole site takes in an hour, from everyone together. */
	static final int SIGN_UPS_PER_HOUR = 300;

	private AccountEmails emails;
	private boolean verifyEmail;

	/**
	 * T56 (BUG-119). Set by Spring; a test that builds this service by hand signs accounts in at once, as before. Off
	 * only where a test says so — the prod profile refuses to start without it (MailGuard).
	 */
	@org.springframework.beans.factory.annotation.Autowired(required = false)
	void setEmailVerification(AccountEmails emails,
			@org.springframework.beans.factory.annotation.Value("${tradeez.signup.verify-email:true}") boolean verifyEmail) {
		this.emails = emails;
		this.verifyEmail = verifyEmail;
	}

	/**
	 * What a sign-up ends in: signed in at once ({@code signedIn}), or — T56 — waiting for the link sent to the address
	 * ({@code signedIn} null). The second is the same answer whether the address was new or already had an account.
	 */
	public record SignUp(IssuedTokens signedIn, String email) {
	}

	/** Issued credentials. The raw refresh token exists only long enough to become a cookie. */
	public record IssuedTokens(AppUser user, String accessToken, String refreshToken) {
	}

	@Transactional
	public SignUp register(RegisterRequest request, String userAgent) {
		// BUG-127: the address and the name as they will be stored, or refused.
		String email = Identities.email(request.email());
		String displayName = Identities.displayName(request.displayName());
		// password-1.0.0 first: a weak password is refused before anything is looked up (DEC-064).
		passwordPolicy.require(request.password(), email, displayName);
		if (verifyEmail) {
			return signUpByEmail(email, displayName, request.password());
		}
		if (users.existsByEmailIgnoreCase(email)) {
			throw new EmailAlreadyRegisteredException();
		}
		AppUser user;
		try {
			user = new AppUser(email, passwordEncoder.encode(request.password()), displayName);
			user.verifyEmail(Instant.now());
			user = users.saveAndFlush(user);
		} catch (DataIntegrityViolationException raced) {
			// Two sign-ups for one address at once both pass the check above; the unique index on
			// lower(email) lets one in, and the other is the same answer as a taken address, not a 500.
			throw new EmailAlreadyRegisteredException();
		}
		return new SignUp(issueFor(user, userAgent), email);
	}

	/**
	 * T56 (BUG-119): nobody is signed in by signing up. A new address gets an unverified account and a link; an address
	 * that already has one gets an email telling its owner (or the link again, if it was never confirmed). Either way
	 * the answer is the same, and takes the same time — a password is hashed on both paths — so sign-up no longer says
	 * which addresses have accounts.
	 */
	private SignUp signUpByEmail(String email, String displayName, String password) {
		if (!rateLimiter.tryAcquire("sign-ups", SIGN_UPS_PER_HOUR, java.time.Duration.ofHours(1))) {
			throw new TooManyAttemptsException();
		}
		String hash = passwordEncoder.encode(password);
		var existing = users.findByEmailIgnoreCase(email);
		if (existing.isPresent()) {
			if (existing.get().isEmailVerified()) {
				emails.sendAlreadyRegistered(existing.get());
			} else {
				// Never confirmed: this sign-up's own link, carrying this sign-up's password (pre-hijacking, AccountEmails).
				emails.sendVerification(existing.get(), hash);
			}
			return new SignUp(null, email);
		}
		try {
			emails.sendVerification(users.saveAndFlush(new AppUser(email, hash, displayName)), hash);
		} catch (DataIntegrityViolationException raced) {
			// The same address signing up twice at once: one account, and the same answer to both.
		}
		return new SignUp(null, email);
	}

	/** T56: the address is proven — the account is confirmed, and signed in. */
	@Transactional
	public IssuedTokens verify(String rawToken, String userAgent) {
		EmailToken link = emails.use(rawToken, EmailToken.Purpose.VERIFY);
		AppUser user = link.getUser();
		// The password chosen at the sign-up this link came from (pre-hijacking, AccountEmails).
		if (link.getPasswordHash() != null) {
			user.changePasswordHash(link.getPasswordHash());
		}
		user.verifyEmail(Instant.now());
		users.save(user);
		return issueFor(user, userAgent);
	}

	/**
	 * T56: the link again, for an account never confirmed — only to someone who knows its password, so it cannot be used
	 * to send its owner a link to an account with someone else's password. Says nothing either way, in the same time.
	 */
	@Transactional
	public void resendVerification(String email, String password) {
		var user = known(email);
		if (user.isEmpty() || PasswordPolicy.tooLong(password)) {
			passwordEncoder.encode(TIMING_STAND_IN);
			return;
		}
		if (passwordEncoder.matches(password, user.get().getPasswordHash()) && !user.get().isEmailVerified()) {
			emails.sendVerification(user.get(), null);
		}
	}

	/**
	 * T56: a link to set a new password — or, for an account never confirmed, a note to sign up again, with no link:
	 * only a proven address may set a password. Says nothing either way.
	 */
	@Transactional
	public void forgotPassword(String email) {
		known(email).ifPresent(user -> {
			if (user.isEmailVerified()) {
				emails.sendReset(user);
			} else {
				emails.sendUnconfirmed(user);
			}
		});
	}

	/**
	 * T56: a new password from a reset link. Every session ends, as with a password change — the reason for a reset is
	 * often that someone else has the old one — and this one starts. Opening the link also proves the address.
	 */
	@Transactional
	public IssuedTokens resetPassword(String rawToken, String newPassword, String userAgent) {
		AppUser user = emails.use(rawToken, EmailToken.Purpose.RESET).getUser();
		passwordPolicy.require(newPassword, user.getEmail(), user.getDisplayName());
		user.changePasswordHash(passwordEncoder.encode(newPassword));
		user.verifyEmail(Instant.now());
		user.revokeAccessTokens();
		users.save(user);
		refreshTokens.revokeAllForUser(user.getId(), Instant.now());
		log.info("Password reset for user {} from an emailed link; every session signed out.", user.getId());
		return issueFor(user, userAgent);
	}

	/** The account at an address, if the address is one at all. */
	private java.util.Optional<AppUser> known(String email) {
		try {
			return users.findByEmailIgnoreCase(Identities.email(email));
		} catch (Identities.InvalidIdentityException notAnAddress) {
			return java.util.Optional.empty();
		}
	}

	/**
	 * @param clientAddress where the attempt comes from ({@link ClientAddress})
	 */
	@Transactional
	public IssuedTokens login(LoginRequest request, String userAgent, String clientAddress) {
		// Per-account throttling, on top of the per-IP filter (BUG-002), keyed on the account *and* the address
		// (BUG-076). Keyed on the account alone, ten wrong guesses from anywhere locked its owner out as well: the
		// limit is checked before the password, so the right one was refused too, and ten more every fifteen
		// minutes kept them out. Now the guesser is stopped and the owner, elsewhere, is not. Guessing from many
		// addresses still costs ten tries an address a window, against password-1.0.0's twelve characters and the
		// breach check. The email is normalised so capitalisation cannot buy a fresh quota.
		String throttleKey = "login:" + request.email().trim().toLowerCase(java.util.Locale.ROOT) + ":" + clientAddress;
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
		// T56: the right password, but the address was never proven. Told only to someone who knows the password.
		if (verifyEmail && !user.isEmailVerified()) {
			throw new EmailNotVerifiedException();
		}
		// BUG-132: a hash made at a lower cost than today's is redone now, while the password is in hand.
		if (passwordEncoder.upgradeEncoding(user.getPasswordHash())) {
			user.changePasswordHash(passwordEncoder.encode(request.password()));
			users.save(user);
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
			// BUG-138: only while the session it was rotated into is still alive. After a sign-out, a sign-out
			// everywhere or a password change that session is gone, and the old token must not start a new one.
			if (withinReuseGrace(stored, now) && sessionLives(stored, now)) {
				log.debug("Refresh token for user {} was already rotated {}ms ago; treating as a "
					+ "concurrent duplicate rather than reuse.",
					stored.getUser().getId(),
					Duration.between(stored.getRevokedAt(), now).toMillis());
				return issueFor(stored.getUser(), userAgent);
			}

			if (withinReuseGrace(stored, now)) {
				// A duplicate of a session already ended: refused, and nothing else to end.
				throw new InvalidRefreshTokenException();
			}

			UUID userId = stored.getUser().getId();
			// BUG-139: the access tokens already handed out end too, as on a password change — whoever holds the
			// successor's would otherwise keep reading and writing for up to fifteen minutes.
			AppUser owner = stored.getUser();
			owner.revokeAccessTokens();
			users.save(owner);
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
		// BUG-077: the access tokens already handed out end too, not only the refresh tokens.
		user.revokeAccessTokens();
		users.save(user);
		emails.spendResets(user);
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

	/**
	 * BUG-120: the account and everything hanging off it — sessions, trades, drills, drawings, sign-ins — deleted for
	 * good (the schema cascades from app_user). Needs the current password, and is throttled as a password change is, so
	 * a session left open on a shared computer cannot be used to destroy the account or to guess its password.
	 */
	@Transactional
	public void deleteAccount(UUID userId, String password) {
		String throttleKey = "password:" + userId;
		if (!rateLimiter.tryAcquire(throttleKey, PASSWORD_CHANGES, ACCOUNT_WINDOW)) {
			throw new TooManyAttemptsException();
		}
		AppUser user = users.findById(userId).orElseThrow(InvalidRefreshTokenException::new);
		if (PasswordPolicy.tooLong(password) || !passwordEncoder.matches(password, user.getPasswordHash())) {
			throw new WrongCurrentPasswordException();
		}
		users.delete(user);
		rateLimiter.reset(throttleKey);
		log.info("Account {} deleted at its owner's request.", userId);
	}

	/** BUG-126: this access token is refused from now until it would have expired. */
	@Transactional
	public void signOutAccessToken(JwtService.AccessToken token) {
		if (token.jti() != null && revokedAccessTokens != null && token.expiresAt().isAfter(Instant.now())) {
			revokedAccessTokens.save(new RevokedAccessToken(token.jti(), token.expiresAt()));
		}
	}

	/** Every session: the refresh tokens, and the access tokens already handed out (BUG-077). */
	@Transactional
	public void logoutEverywhere(UUID userId) {
		refreshTokens.revokeAllForUser(userId, Instant.now());
		users.findById(userId).ifPresent(user -> {
			user.revokeAccessTokens();
			users.save(user);
		});
	}

	/** True when this token was rotated so recently that a replay is almost certainly a duplicate. */
	private boolean withinReuseGrace(RefreshToken stored, Instant now) {
		Instant rotatedAt = stored.getRevokedAt();
		return rotatedAt != null
			&& !Duration.between(rotatedAt, now).isNegative()
			&& Duration.between(rotatedAt, now).compareTo(REUSE_GRACE) <= 0;
	}

	/** BUG-138: the newest token in this one's rotation chain is still usable — the session was not ended since. */
	private boolean sessionLives(RefreshToken stored, Instant now) {
		RefreshToken newest = stored;
		// Bounded: a chain is a few rotations long inside the grace window, and a cycle cannot be written.
		for (int hop = 0; hop < 32 && newest.isAlreadyRotated(); hop++) {
			newest = refreshTokens.findById(newest.getReplacedBy()).orElse(null);
			if (newest == null) {
				return false;
			}
		}
		return newest.isActive(now);
	}

	private IssuedTokens issueFor(AppUser user, String userAgent) {
		String rawRefresh = jwtService.generateRefreshToken();
		refreshTokens.save(new RefreshToken(
			user,
			jwtService.hash(rawRefresh),
			Instant.now().plus(jwtService.getRefreshTokenTtl()),
			Devices.describe(userAgent)));
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

	public static class EmailNotVerifiedException extends RuntimeException {
	}
}
