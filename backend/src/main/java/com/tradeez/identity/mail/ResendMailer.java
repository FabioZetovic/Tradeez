package com.tradeez.identity.mail;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

/**
 * T56: mail through Resend's HTTP API ({@code POST /emails}). The API key travels only in the request's Authorization
 * header and is never logged; a failure is logged by its status alone, without the address or the body.
 *
 * <p>A failed send is not shown to the person signing up: the answer to a sign-up is the same whether or not an email
 * went out, so it cannot be used to learn which addresses have accounts. They can ask for the link again.
 */
@Component
@ConditionalOnProperty(name = "tradeez.mail.provider", havingValue = "resend")
public class ResendMailer implements Mailer {

	private static final Logger log = LoggerFactory.getLogger(ResendMailer.class);

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
	private final ObjectMapper json;
	private final String apiKey;
	private final String from;
	private final URI endpoint;

	public ResendMailer(ObjectMapper json,
			@Value("${tradeez.mail.resend-api-key}") String apiKey,
			@Value("${tradeez.mail.from}") String from,
			@Value("${tradeez.mail.resend-base-url:https://api.resend.com}") String baseUrl) {
		if (apiKey.isBlank() || from.isBlank()) {
			throw new IllegalStateException("tradeez.mail.provider=resend needs RESEND_API_KEY and MAIL_FROM.");
		}
		this.json = json;
		this.apiKey = apiKey;
		this.from = from;
		this.endpoint = URI.create(baseUrl.replaceAll("/+$", "") + "/emails");
	}

	@Override
	public void send(Mail mail) {
		String body = json.writeValueAsString(Map.of(
			"from", from,
			"to", List.of(mail.to()),
			"subject", mail.subject(),
			"text", mail.text()));
		HttpRequest request = HttpRequest.newBuilder(endpoint)
			.timeout(Duration.ofSeconds(10))
			.header("Authorization", "Bearer " + apiKey)
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(body))
			.build();
		try {
			HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
			if (response.statusCode() / 100 != 2) {
				log.warn("Resend refused a mail \"{}\": HTTP {}", mail.subject(), response.statusCode());
			}
		} catch (java.io.IOException e) {
			log.warn("Could not reach Resend to send a mail \"{}\": {}", mail.subject(), e.getClass().getSimpleName());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
