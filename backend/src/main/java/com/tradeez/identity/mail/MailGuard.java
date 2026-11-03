package com.tradeez.identity.mail;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * T56 (BUG-119): the deployed app ({@code prod} profile) refuses to start when sign-up would not prove addresses — the
 * check switched off, or mail kept in a local outbox nobody reads, which would leave every new account stuck — or when
 * the links would not lead back to a secure page.
 */
@Component
public class MailGuard {

	private final Environment environment;
	private final boolean verifyEmail;
	private final String provider;
	private final String appUrl;

	public MailGuard(Environment environment,
			@Value("${tradeez.signup.verify-email:true}") boolean verifyEmail,
			@Value("${tradeez.mail.provider:outbox}") String provider,
			@Value("${tradeez.app-url}") String appUrl) {
		this.environment = environment;
		this.verifyEmail = verifyEmail;
		this.provider = provider;
		this.appUrl = appUrl;
	}

	@PostConstruct
	public void verify() {
		if (!Arrays.asList(environment.getActiveProfiles()).contains("prod")) {
			return;
		}
		if (!verifyEmail) {
			throw new IllegalStateException("Refusing to start: deployed, sign-up must confirm email addresses "
				+ "(tradeez.signup.verify-email is false). Anyone could otherwise sign up as anyone (BUG-119).");
		}
		if ("outbox".equals(provider)) {
			throw new IllegalStateException("Refusing to start: deployed, mail must really be sent. Set MAIL_PROVIDER=resend "
				+ "with RESEND_API_KEY and MAIL_FROM; the local outbox would leave every sign-up waiting for an email.");
		}
		if (!appUrl.startsWith("https://")) {
			throw new IllegalStateException("Refusing to start: APP_URL must be the app's https:// address — the links in "
				+ "its emails lead there.");
		}
	}
}
