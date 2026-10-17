package com.tradeez.identity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/**
 * {@value #VERSION} — what a new password must be (DEC-064).
 *
 * <p>The current advice (NIST SP 800-63B): length, and a check against passwords attackers already have,
 * rather than composition rules — "one upper case, one digit, one symbol" pushes people to
 * {@code Password1!}, which every cracking list tries early. So a password must be:
 * <ul>
 * <li>at least {@value #MIN_CHARACTERS} characters;</li>
 * <li>at most {@value #MAX_BYTES} bytes in UTF-8 — all BCrypt reads; a longer one could not be stored, and
 * at sign-in it would be checked on its first {@value #MAX_BYTES} bytes only (BUG-066);</li>
 * <li>made of at least {@value #MIN_DISTINCT} different characters, and not one straight run such as
 * {@code abcdefghijkl} or {@code 123456789012};</li>
 * <li>free of the account's own email name, display name and the app's name;</li>
 * <li>not one of the most common passwords, and not in a known breach ({@link PwnedPasswords}).</li>
 * </ul>
 * Every problem is named, so the person can fix them all at once.
 */
@Component
public class PasswordPolicy {

	public static final String VERSION = "password-1.0.0";
	public static final int MIN_CHARACTERS = 12;
	public static final int MAX_BYTES = 72;
	static final int MIN_DISTINCT = 5;

	/** Words shorter than this are too common inside ordinary passwords to refuse. */
	private static final int MIN_CONTEXT_WORD = 3;

	private static final String COMMON_LIST = "identity/common-passwords.txt";

	private final Set<String> common;
	private final PwnedPasswords breaches;

	public PasswordPolicy(PwnedPasswords breaches) {
		this.breaches = breaches;
		this.common = load();
	}

	/** True when BCrypt could not read all of it. */
	public static boolean tooLong(String password) {
		return password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES;
	}

	/**
	 * @throws WeakPasswordException naming every problem, when there is one
	 */
	public void require(String password, String email, String displayName) {
		List<String> problems = problems(password, email, displayName);
		if (!problems.isEmpty()) {
			throw new WeakPasswordException(problems);
		}
	}

	/** Every way the password falls short, in words; empty when it does not. */
	public List<String> problems(String password, String email, String displayName) {
		List<String> problems = new ArrayList<>();
		int characters = password.codePointCount(0, password.length());
		if (characters < MIN_CHARACTERS) {
			problems.add("It is too short: at least " + MIN_CHARACTERS + " characters.");
		}
		if (tooLong(password)) {
			problems.add("It is too long to be stored safely: at most " + MAX_BYTES
				+ " bytes, which is 72 plain letters and fewer with accents or emoji.");
		}
		if (password.codePoints().distinct().count() < MIN_DISTINCT) {
			problems.add("It uses too few different characters: at least " + MIN_DISTINCT + ".");
		} else if (straightRun(password)) {
			problems.add("It is a straight run of characters, like abcdef or 123456.");
		}
		String lower = password.toLowerCase(Locale.ROOT);
		String emailName = email == null ? "" : email.split("@", 2)[0].toLowerCase(Locale.ROOT);
		if (emailName.length() >= MIN_CONTEXT_WORD && lower.contains(emailName)) {
			problems.add("It contains your email address.");
		}
		String name = displayName == null ? "" : displayName.strip().toLowerCase(Locale.ROOT);
		if (name.length() >= MIN_CONTEXT_WORD && lower.contains(name)) {
			problems.add("It contains your name.");
		}
		if (lower.contains("tradeez")) {
			problems.add("It contains the app's name.");
		}
		if (common.contains(lower)) {
			problems.add("It is one of the most common passwords.");
		}
		// The breach check last, and only for a password that passes everything else: it is a network call.
		if (problems.isEmpty()) {
			OptionalLong seen = breaches.timesSeen(password);
			if (seen.isPresent() && seen.getAsLong() > 0) {
				problems.add("It has appeared in known data breaches " + String.format(Locale.ROOT, "%,d", seen.getAsLong())
					+ (seen.getAsLong() == 1 ? " time" : " times") + ", and attackers try those first.");
			}
		}
		return problems;
	}

	/** Three in four neighbouring characters one apart, up or down: {@code abcdef}, {@code 987654}. */
	private static boolean straightRun(String password) {
		int[] cps = password.codePoints().toArray();
		int steps = 0;
		for (int i = 1; i < cps.length; i++) {
			if (Math.abs(cps[i] - cps[i - 1]) == 1) {
				steps++;
			}
		}
		return cps.length > 1 && steps * 4 >= (cps.length - 1) * 3;
	}

	private static Set<String> load() {
		InputStream in = PasswordPolicy.class.getClassLoader().getResourceAsStream(COMMON_LIST);
		if (in == null) {
			throw new IllegalStateException(COMMON_LIST + " is not on the classpath.");
		}
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
			return reader.lines().map(String::strip).filter(l -> !l.isEmpty() && !l.startsWith("#"))
				.map(l -> l.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
		} catch (IOException e) {
			throw new IllegalStateException("Could not read " + COMMON_LIST, e);
		}
	}

	/** A password the policy refuses; the message names every problem. */
	public static class WeakPasswordException extends RuntimeException {

		private final List<String> problems;

		public WeakPasswordException(List<String> problems) {
			super("Choose a different password. " + String.join(" ", problems));
			this.problems = List.copyOf(problems);
		}

		public List<String> problems() {
			return problems;
		}
	}
}
