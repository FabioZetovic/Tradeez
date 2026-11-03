package com.tradeez.identity;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * T57: what sign-ins leave behind is deleted once it has no use (BUG-130, BUG-126). A refresh token is kept 30 days past
 * its expiry or revocation — reuse detection needs only the last few minutes — and then goes; a signed-out access token
 * goes once it would have expired anyway.
 */
@Component
public class SessionHousekeeping {

	private static final Logger log = LoggerFactory.getLogger(SessionHousekeeping.class);

	static final Duration KEEP_FINISHED_SIGN_INS = Duration.ofDays(30);

	private final RefreshTokenRepository refreshTokens;
	private final RevokedAccessTokenRepository revokedAccessTokens;
	private final EmailTokenRepository emailTokens;
	private final AppUserRepository users;

	/** T56 (BUG-128): an account whose address was never confirmed is deleted after this. */
	static final Duration KEEP_UNCONFIRMED = Duration.ofDays(7);

	public SessionHousekeeping(RefreshTokenRepository refreshTokens, RevokedAccessTokenRepository revokedAccessTokens,
			EmailTokenRepository emailTokens, AppUserRepository users) {
		this.refreshTokens = refreshTokens;
		this.revokedAccessTokens = revokedAccessTokens;
		this.emailTokens = emailTokens;
		this.users = users;
	}

	@Scheduled(fixedDelay = 1, initialDelay = 1, timeUnit = java.util.concurrent.TimeUnit.HOURS)
	public void tidy() {
		Instant now = Instant.now();
		int signIns = refreshTokens.deleteFinishedBefore(now.minus(KEEP_FINISHED_SIGN_INS));
		int signedOut = revokedAccessTokens.deleteExpired(now);
		int links = emailTokens.deleteFinishedBefore(now.minus(Duration.ofDays(1)));
		int unconfirmed = users.deleteUnconfirmedBefore(now.minus(KEEP_UNCONFIRMED));
		if (signIns + signedOut + links + unconfirmed > 0) {
			log.info("Deleted {} finished sign-in(s), {} expired signed-out token(s), {} spent link(s) and {} account(s) "
				+ "never confirmed.", signIns, signedOut, links, unconfirmed);
		}
	}
}
