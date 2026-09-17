package com.tradeez.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Refuses to run a deployed instance with the committed development JWT secret.
 *
 * <p>The default secret in {@code application.yml} is public — it is in the repository, and the
 * repository is shared. Anyone holding it can mint valid access tokens for any account. That is
 * fine locally and catastrophic anywhere else.
 *
 * <p>The signal used is {@code tradeez.cookies.secure}: it is false only on plain-HTTP localhost
 * and must be true anywhere actually deployed, which makes it a reliable proxy for "this is a real
 * environment". Failing at startup is deliberate — a warning in a log nobody reads is not a
 * safeguard.
 */
@Component
public class JwtSecretGuard {

	static final String DEVELOPMENT_SECRET =
		"ZGV2LW9ubHktc2VjcmV0LWRvLW5vdC11c2UtaW4tcHJvZC0wMDAwMDAwMA==";

	private final String secret;
	private final boolean cookiesSecure;

	public JwtSecretGuard(
			@Value("${tradeez.jwt.secret}") String secret,
			@Value("${tradeez.cookies.secure}") boolean cookiesSecure) {
		this.secret = secret;
		this.cookiesSecure = cookiesSecure;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void verify() {
		if (cookiesSecure && DEVELOPMENT_SECRET.equals(secret)) {
			throw new IllegalStateException("""
				Refusing to start: this instance is configured for a deployed environment \
				(tradeez.cookies.secure=true) but is still using the development JWT secret \
				committed to the repository. Anyone with repository access could forge tokens for \
				any account.

				Set the JWT_SECRET environment variable to a fresh value:
				    openssl rand -base64 32""");
		}
	}
}
