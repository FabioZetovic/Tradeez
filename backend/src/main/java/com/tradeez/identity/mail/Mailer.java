package com.tradeez.identity.mail;

/**
 * T56: sends one plain-text email. Plain text only — nothing a mail client renders as markup, so nothing a name or an
 * address could inject. {@code tradeez.mail.provider} picks the implementation: {@code outbox} keeps the mail on this
 * machine (local development and tests), {@code resend} sends it through Resend's HTTP API (deployed).
 */
public interface Mailer {

	void send(Mail mail);

	record Mail(String to, String subject, String text) {
	}
}
