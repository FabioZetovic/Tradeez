package com.tradeez.identity;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.tradeez.identity.EmailToken.Purpose;
import com.tradeez.identity.mail.Mailer;
import com.tradeez.identity.mail.Mailer.Mail;

/**
 * T56 (BUG-119): the emails an account gets, and the single-use links in them.
 *
 * <p>A link's token goes after a {@code #}, never in the path or a query: the browser never sends that part to any
 * server, so the token cannot land in a log, a proxy or a {@code Referer} header. The page reads it and posts it.
 *
 * <p>Pre-hijacking: someone could sign up first with another person's address and a password of their own, and wait for
 * the owner to confirm that account. So a sign-up's link carries the password chosen at that sign-up, and opening it sets
 * that password: the owner, opening the link from their own sign-up, ends up with their own password. Earlier sign-ups'
 * links stay good (one does not cancel another's) until one of them is opened; then the rest are spent.
 *
 * <p>Each address gets at most {@link #MAILS_PER_ADDRESS} emails in {@link #MAIL_WINDOW}, whatever asks for them, so the
 * app cannot be used to flood someone's inbox. Past it, nothing is sent — and nothing is said, so the limit does not
 * reveal whether the address has an account. BUG-144: the notes nobody asked for (someone tried to sign up with the
 * address; forgot password on an account never confirmed) have a budget of their own, {@link #NOTICES_PER_ADDRESS}, so a
 * stranger sending them cannot use up the owner's links.
 *
 * <p>BUG-143: an email goes out after the request's transaction commits, on a thread of its own, so the answer does not
 * wait for the mail provider — and an address with an account takes no longer to answer than one without. Tests send
 * on the caller's thread ({@code tradeez.mail.async=false}).
 */
@Service
public class AccountEmails {

	static final Duration VERIFY_FOR = Duration.ofHours(24);
	static final Duration RESET_FOR = Duration.ofHours(1);
	static final int MAILS_PER_ADDRESS = 3;
	static final int NOTICES_PER_ADDRESS = 1;
	static final Duration MAIL_WINDOW = Duration.ofMinutes(15);

	private final EmailTokenRepository tokens;
	private final JwtService jwt;
	private final Mailer mailer;
	private final RateLimiter rateLimiter;
	private final String appUrl;
	private final Executor sending;

	public AccountEmails(EmailTokenRepository tokens, JwtService jwt, Mailer mailer, RateLimiter rateLimiter,
			@Value("${tradeez.app-url}") String appUrl, @Value("${tradeez.mail.async:true}") boolean async) {
		this.tokens = tokens;
		this.jwt = jwt;
		this.mailer = mailer;
		this.rateLimiter = rateLimiter;
		this.appUrl = appUrl.replaceAll("/+$", "");
		this.sending = async ? Executors.newVirtualThreadPerTaskExecutor() : Runnable::run;
	}

	/**
	 * The link that proves the address and finishes sign-up.
	 *
	 * @param passwordHash the password chosen at this sign-up, set when the link is opened; null to keep the account's
	 *                     own (asking again, after proving the password)
	 * @param displayName  the name chosen at this sign-up, likewise (BUG-168); null to keep the account's own
	 */
	@Transactional
	public void sendVerification(AppUser user, String passwordHash, String displayName) {
		if (!allowed(user)) {
			return;
		}
		String link = appUrl + "/verify-email#" + issue(user, Purpose.VERIFY, VERIFY_FOR, passwordHash, displayName);
		deliver(new Mail(user.getEmail(), "Confirm your email for Tradeez", """
			Someone — we hope you — made a Tradeez account with this address. To confirm it is yours, open this link \
			within 24 hours:

			%s

			If it was not you, ignore this email. The account cannot be used without this link, and it is deleted after \
			seven days.
			""".formatted(link)));
	}

	/** Someone signed up with an address that already has an account: its owner is told, and nothing else happens. */
	public void sendAlreadyRegistered(AppUser user) {
		if (!noticeAllowed(user)) {
			return;
		}
		deliver(new Mail(user.getEmail(), "Someone tried to sign up to Tradeez with your address", """
			You already have a Tradeez account with this address, and someone just tried to make another with it.

			If that was you: sign in at %s/sign-in. If you have forgotten your password, set a new one at \
			%s/forgot-password.

			If it was not you, nothing has changed: your account and your password are as they were.
			""".formatted(appUrl, appUrl)));
	}

	/** A link to set a new password, good for an hour. */
	@Transactional
	public void sendReset(AppUser user) {
		if (!allowed(user)) {
			return;
		}
		String link = appUrl + "/reset-password#" + issue(user, Purpose.RESET, RESET_FOR, null, null);
		deliver(new Mail(user.getEmail(), "Set a new Tradeez password", """
			Someone asked to set a new password for the Tradeez account with this address. To do it, open this link \
			within an hour:

			%s

			If it was not you, ignore this email: your password stays as it is.
			""".formatted(link)));
	}

	/** "Forgot password" for an account never confirmed: no link — only a proven address may set a password. */
	public void sendUnconfirmed(AppUser user) {
		if (!noticeAllowed(user)) {
			return;
		}
		deliver(new Mail(user.getEmail(), "Your Tradeez account was never confirmed", """
			Someone asked to set a new password for the Tradeez account with this address, but the account was never \
			confirmed, so it has no password to change yet.

			If it is yours, sign up again at %s/sign-up: you will get a new link to confirm it, with the password you \
			choose then. If it was not you, ignore this email; the account is deleted after seven days.
			""".formatted(appUrl)));
	}

	/**
	 * The link's token, spent — with every other unused link of its kind for the account. Refused alike when the link is unknown, of the other kind, used or
	 * expired — the person needs only to know it no longer works.
	 */
	@Transactional
	EmailToken use(String rawToken, Purpose purpose) {
		Instant now = Instant.now();
		EmailToken token = tokens.findByTokenHash(jwt.hash(rawToken))
			.filter(t -> t.getPurpose() == purpose && t.usable(now))
			.orElseThrow(InvalidLinkException::new);
		// BUG-141: read unused is not enough — two requests can both read it so before either spends it.
		if (tokens.claim(token.getId(), now) != 1) {
			throw new InvalidLinkException();
		}
		tokens.spendAll(token.getUser().getId(), purpose, now);
		return token;
	}

	/** BUG-142: the account's password changed — a reset link sent before must not undo that. */
	@Transactional
	void spendResets(AppUser user) {
		tokens.spendAll(user.getId(), Purpose.RESET, Instant.now());
	}

	private boolean allowed(AppUser user) {
		return rateLimiter.tryAcquire("mail:" + user.getEmail(), MAILS_PER_ADDRESS, MAIL_WINDOW);
	}

	private boolean noticeAllowed(AppUser user) {
		return rateLimiter.tryAcquire("notice:" + user.getEmail(), NOTICES_PER_ADDRESS, MAIL_WINDOW);
	}

	private String issue(AppUser user, Purpose purpose, Duration validFor, String passwordHash, String displayName) {
		Instant now = Instant.now();
		// No link cancels an earlier one of its kind (BUG-169: a reset link that failed to arrive must not take away the
		// one that did); opening any of them spends the rest (use). At most three an address a window, each for an hour.
		String raw = jwt.generateRefreshToken();
		tokens.save(new EmailToken(user, purpose, jwt.hash(raw), now, now.plus(validFor), passwordHash, displayName));
		return raw;
	}

	/** BUG-143: once the transaction has committed — a rolled-back request sends nothing — and off the request's thread. */
	private void deliver(Mail mail) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					sending.execute(() -> mailer.send(mail));
				}
			});
		} else {
			sending.execute(() -> mailer.send(mail));
		}
	}

	public static class InvalidLinkException extends RuntimeException {
	}
}
