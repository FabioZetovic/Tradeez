package com.tradeez.identity;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.OptionalLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * How often a password appears in known breaches, from Have I Been Pwned's Pwned Passwords (DEC-064).
 *
 * <p><b>The password never leaves the server, nor anything that identifies it.</b> The range API is
 * k-anonymous: only the first five hex characters of the password's SHA-1 go out, which hundreds of
 * breached passwords share; the answer is every suffix under that prefix, and the match is found here.
 * {@code Add-Padding} asks for decoy entries too, so the response's size does not narrow it down either.
 * Free, and without a key.
 *
 * <p><b>Fails open.</b> When the service cannot be reached in time, the check is skipped and logged:
 * failing closed would make it impossible to sign up whenever a third party is down. The other rules of
 * {@link PasswordPolicy} still apply.
 */
@Component
public class PwnedPasswords {

	private static final Logger log = LoggerFactory.getLogger(PwnedPasswords.class);
	private static final Duration TIMEOUT = Duration.ofSeconds(3);

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
	private final String rangeUrl;
	private final boolean enabled;

	public PwnedPasswords(
			@Value("${tradeez.password.breach-check.url}") String rangeUrl,
			@Value("${tradeez.password.breach-check.enabled}") boolean enabled) {
		this.rangeUrl = rangeUrl;
		this.enabled = enabled;
	}

	/**
	 * @return the number of times the password was seen in breaches — zero when never — or empty when the
	 *         check is off or the service did not answer
	 */
	public OptionalLong timesSeen(String password) {
		if (!enabled) {
			return OptionalLong.empty();
		}
		String hash = sha1(password);
		String prefix = hash.substring(0, 5);
		String suffix = hash.substring(5);
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(rangeUrl + prefix))
				.timeout(TIMEOUT)
				.header("Add-Padding", "true")
				.header("User-Agent", "Tradeez-password-check")
				.GET()
				.build();
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200) {
				log.warn("Pwned Passwords answered {}; the breach check was skipped.", response.statusCode());
				return OptionalLong.empty();
			}
			// "SUFFIX:COUNT" per line; padding lines carry a count of 0.
			for (String line : (Iterable<String>) response.body().lines()::iterator) {
				int colon = line.indexOf(':');
				if (colon > 0 && line.substring(0, colon).strip().equalsIgnoreCase(suffix)) {
					return OptionalLong.of(Long.parseLong(line.substring(colon + 1).strip()));
				}
			}
			return OptionalLong.of(0);
		} catch (IOException | NumberFormatException e) {
			log.warn("Pwned Passwords could not be read ({}); the breach check was skipped.", e.toString());
			return OptionalLong.empty();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return OptionalLong.empty();
		}
	}

	/** Upper-case hex, as the range API writes its suffixes. */
	static String sha1(String password) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-1").digest(password.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().withUpperCase().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-1 unavailable", e);
		}
	}
}
