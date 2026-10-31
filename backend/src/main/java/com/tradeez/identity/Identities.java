package com.tradeez.identity;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * BUG-127 (T57): what a display name and an email address may be. Both were stored exactly as typed: a name could carry
 * a right-to-left override ("Admin‮gnp.exe" shows as "Adminexe.png"), zero-width spaces, newlines; an address
 * could use Cyrillic letters that look Latin, or have no domain ending at all.
 */
final class Identities {

	static final int MAX_NAME = 40;

	/** Words a name may not contain, so nobody can pass for the app or the people running it. */
	private static final Set<String> RESERVED = Set.of("tradeez", "admin", "administrator", "support", "moderator", "staff",
		"official", "system", "root");

	/** Control, format (zero-width, bidi overrides), unassigned and private-use characters. */
	private static final Pattern INVISIBLE = Pattern.compile("\\p{C}");

	/** Letters (any script), marks, digits, spaces and the punctuation names use. */
	private static final Pattern NAME = Pattern.compile("[\\p{L}\\p{M}\\p{Nd} .'\\-_]+");

	/** ASCII only (no look-alike letters from other scripts), a dot in the domain, no dot at either end of a part. */
	private static final Pattern EMAIL = Pattern.compile(
		"[a-z0-9!#$%&'*+/=?^_`{|}~-]+(\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*@[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)*\\.[a-z]{2,}");

	private Identities() {
	}

	/** The name as stored: NFC, spaces collapsed. Refused when empty, too long, invisible or impersonating. */
	static String displayName(String typed) {
		String name = Normalizer.normalize(typed == null ? "" : typed, Normalizer.Form.NFC);
		if (INVISIBLE.matcher(name).find()) {
			throw new InvalidIdentityException("A name can't contain invisible or control characters.");
		}
		name = name.strip().replaceAll("\\s+", " ");
		if (name.isEmpty() || name.length() > MAX_NAME) {
			throw new InvalidIdentityException("A name is 1 to " + MAX_NAME + " characters.");
		}
		if (!NAME.matcher(name).matches()) {
			throw new InvalidIdentityException("A name can use letters, digits, spaces and . ' - _ only.");
		}
		for (String word : name.toLowerCase(Locale.ROOT).split("[ .'\\-_]+")) {
			if (RESERVED.contains(word)) {
				throw new InvalidIdentityException("That name is kept for the app itself. Please choose another.");
			}
		}
		return name;
	}

	/** The address as stored: trimmed, NFC, lower case. Refused unless plain ASCII with a real domain ending. */
	static String email(String typed) {
		String email = Normalizer.normalize(typed == null ? "" : typed.strip(), Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
		if (email.length() > 254 || !EMAIL.matcher(email).matches()) {
			throw new InvalidIdentityException("Please enter a real email address, like name@example.com, in plain letters.");
		}
		return email;
	}

	public static class InvalidIdentityException extends RuntimeException {
		public InvalidIdentityException(String message) {
			super(message);
		}
	}
}
