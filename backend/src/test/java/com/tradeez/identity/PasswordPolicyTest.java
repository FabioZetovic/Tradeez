package com.tradeez.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

/** password-1.0.0 (DEC-064), each rule on its own; {@link PwnedPasswordsTest} checks the breach client. */
class PasswordPolicyTest {

	private static final String EMAIL = "marta.kovac@example.com";
	private static final String NAME = "Marta";

	private final PasswordPolicy policy = new PasswordPolicy(new PwnedPasswords("http://localhost:1/", false));

	@Test
	@DisplayName("a long, ordinary passphrase passes")
	void passes() {
		assertThat(policy.problems("blue lanterns over the harbour", EMAIL, NAME)).isEmpty();
		assertThat(policy.problems("a-perfectly-fine-passphrase", EMAIL, NAME)).isEmpty();
	}

	@Test
	@DisplayName("at least 12 characters, counting an emoji as one, and at most 72 bytes")
	void length() {
		assertThat(policy.problems("short phrase", EMAIL, NAME)).isEmpty();                 // exactly 12
		assertThat(policy.problems("short phras", EMAIL, NAME)).containsExactly("It is too short: at least 12 characters.");
		// Eleven emoji are eleven characters, though 22 Java chars.
		assertThat(policy.problems("🌊🌙🚀🌿🌵🐟🍋🎸"
			+ "💡🧲🌋", EMAIL, NAME)).anyMatch(p -> p.startsWith("It is too short"));
		assertThat(policy.problems("the harbour lanterns burned " + "ñ".repeat(23), EMAIL, NAME))
			.anyMatch(p -> p.startsWith("It is too long"));                                  // 74 bytes
		assertThat(PasswordPolicy.tooLong("a".repeat(72))).isFalse();
		assertThat(PasswordPolicy.tooLong("a".repeat(73))).isTrue();
		assertThat(PasswordPolicy.tooLong("é".repeat(37))).isTrue();                          // 74 bytes, 37 letters
	}

	@Test
	@DisplayName("five different characters, and no straight run")
	void variety() {
		assertThat(policy.problems("aaaaaaaaaaaaaaaa", EMAIL, NAME)).containsExactly("It uses too few different characters: at least 5.");
		assertThat(policy.problems("abababababababab", EMAIL, NAME)).containsExactly("It uses too few different characters: at least 5.");
		assertThat(policy.problems("aabbccddaabbccdd", EMAIL, NAME)).containsExactly("It uses too few different characters: at least 5.");
		assertThat(policy.problems("aabbccddeaabbccdd", EMAIL, NAME)).isEmpty();                // five
		assertThat(policy.problems("abcdefghijklmnop", EMAIL, NAME)).containsExactly("It is a straight run of characters, like abcdef or 123456.");
		assertThat(policy.problems("zyxwvutsrqponmlk", EMAIL, NAME)).containsExactly("It is a straight run of characters, like abcdef or 123456.");
		// A run inside an otherwise ordinary password is not the whole password.
		assertThat(policy.problems("lanterns 1234 harbour", EMAIL, NAME)).isEmpty();
	}

	@Test
	@DisplayName("not the email's name, the display name or the app's name, in any case")
	void context() {
		assertThat(policy.problems("my name is marta.kovac ok", EMAIL, NAME)).contains("It contains your email address.");
		assertThat(policy.problems("lanterns for MARTA forever", EMAIL, NAME)).containsExactly("It contains your name.");
		assertThat(policy.problems("I practise on Tradeez daily", EMAIL, NAME)).containsExactly("It contains the app's name.");
		// A two-letter name inside a word is no giveaway; three letters are.
		assertThat(policy.problems("totally fine harbour lanterns", "al@example.com", "Al")).isEmpty();
		assertThat(policy.problems("totally fine harbour lanterns", "tot@example.com", "Tot"))
			.containsExactly("It contains your email address.", "It contains your name.");
	}

	@Test
	@DisplayName("not a common password, in any case, and every problem is named at once")
	void commonAndAll() {
		assertThat(policy.problems("Password1234", EMAIL, NAME)).containsExactly("It is one of the most common passwords.");
		assertThat(policy.problems("CorrectHorseBatteryStaple", EMAIL, NAME)).containsExactly("It is one of the most common passwords.");
		assertThatThrownBy(() -> policy.require("aaaa", EMAIL, NAME))
			.isInstanceOf(PasswordPolicy.WeakPasswordException.class)
			.hasMessage("Choose a different password. It is too short: at least 12 characters. It uses too few different "
				+ "characters: at least 5.")
			.satisfies(e -> assertThat(((PasswordPolicy.WeakPasswordException) e).problems()).hasSize(2));
	}

	@Test
	@DisplayName("a breached password is refused with how often it was seen; the check runs only when all else passes")
	void breached() throws IOException {
		HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		int[] calls = { 0 };
		server.createContext("/range/", exchange -> {
			calls[0]++;
			String suffix = PwnedPasswords.sha1("blue lanterns over the harbour").substring(5);
			byte[] body = (suffix + ":1234567\r\n0000000000000000000000000000000000A:0\r\n").getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		server.start();
		try {
			PasswordPolicy checked = new PasswordPolicy(
				new PwnedPasswords("http://localhost:" + server.getAddress().getPort() + "/range/", true));
			assertThat(checked.problems("blue lanterns over the harbour", EMAIL, NAME))
				.containsExactly("It has appeared in known data breaches 1,234,567 times, and attackers try those first.");
			assertThat(checked.problems("a-perfectly-fine-passphrase", EMAIL, NAME)).isEmpty();
			int before = calls[0];
			assertThat(checked.problems("short", EMAIL, NAME)).hasSize(1);
			assertThat(calls[0]).as("no network call for a password already refused").isEqualTo(before);
		} finally {
			server.stop(0);
		}
	}
}
