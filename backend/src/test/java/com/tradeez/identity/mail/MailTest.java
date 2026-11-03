package com.tradeez.identity.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.json.JsonMapper;

/** T56: how mail is sent, and the deployed app's refusal to run without real sending. */
class MailTest {

	private static MailGuard guard(String profile, boolean verify, String provider, String appUrl) {
		MockEnvironment env = new MockEnvironment();
		if (profile != null) {
			env.setActiveProfiles(profile);
		}
		return new MailGuard(env, verify, provider, appUrl);
	}

	@Test
	@DisplayName("deployed, the app will not start without confirmation, with the local outbox, or with an http:// link")
	void guard() {
		assertThatThrownBy(() -> guard("prod", false, "resend", "https://x.example").verify()).hasMessageContaining("verify-email");
		assertThatThrownBy(() -> guard("prod", true, "outbox", "https://x.example").verify()).hasMessageContaining("MAIL_PROVIDER");
		assertThatThrownBy(() -> guard("prod", true, "resend", "http://x.example").verify()).hasMessageContaining("APP_URL");
		assertThatCode(() -> guard("prod", true, "resend", "https://x.example").verify()).doesNotThrowAnyException();
		// Locally and in tests, anything goes.
		assertThatCode(() -> guard(null, false, "outbox", "http://localhost:5173").verify()).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("Resend gets the mail as JSON with the key in the Authorization header, and a refusal is not thrown")
	void resend() throws Exception {
		AtomicReference<String> auth = new AtomicReference<>();
		AtomicReference<String> body = new AtomicReference<>();
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/emails", exchange -> {
			auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
			body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			exchange.sendResponseHeaders(body.get().contains("refuse") ? 422 : 200, -1);
			exchange.close();
		});
		server.start();
		try {
			ResendMailer mailer = new ResendMailer(JsonMapper.builder().build(), "re_test_key", "Tradeez <hello@tradeez.example>",
				"http://127.0.0.1:" + server.getAddress().getPort() + "/");
			mailer.send(new Mailer.Mail("ana@example.com", "Confirm your email for Tradeez", "Open https://x/#t"));
			assertThat(auth.get()).isEqualTo("Bearer re_test_key");
			assertThat(body.get()).contains("\"to\":[\"ana@example.com\"]", "\"from\":\"Tradeez <hello@tradeez.example>\"",
				"\"subject\":\"Confirm your email for Tradeez\"", "\"text\":\"Open https://x/#t\"");
			assertThatCode(() -> mailer.send(new Mailer.Mail("a@example.com", "refuse", "x"))).doesNotThrowAnyException();
		} finally {
			server.stop(0);
		}
		assertThatThrownBy(() -> new ResendMailer(JsonMapper.builder().build(), "", "x@example.com", "http://x"))
			.hasMessageContaining("RESEND_API_KEY");
	}

	@Test
	@DisplayName("the outbox keeps the last fifty in memory and writes nothing when it has no folder")
	void outbox() {
		OutboxMailer outbox = new OutboxMailer("");
		for (int i = 0; i < 60; i++) {
			outbox.send(new Mailer.Mail("a@example.com", "n" + i, "x"));
		}
		assertThat(outbox.recent()).hasSize(50).first().extracting(Mailer.Mail::subject).isEqualTo("n10");
	}
}
