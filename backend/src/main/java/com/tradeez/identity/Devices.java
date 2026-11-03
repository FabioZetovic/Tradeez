package com.tradeez.identity;

import java.util.Locale;

/**
 * BUG-130 (T57): what is kept of a sign-in's browser — "Chrome on Windows", enough to recognise a session, not the full
 * user-agent string, which says far more about the person's machine than the app needs.
 */
final class Devices {

	private Devices() {
	}

	static String describe(String userAgent) {
		if (userAgent == null || userAgent.isBlank()) {
			return null;
		}
		String ua = userAgent.toLowerCase(Locale.ROOT);
		String browser = ua.contains("edg/") ? "Edge"
			: ua.contains("opr/") || ua.contains("opera") ? "Opera"
			: ua.contains("firefox/") || ua.contains("fxios/") ? "Firefox"
			: ua.contains("chrome/") || ua.contains("crios/") ? "Chrome"
			: ua.contains("safari/") ? "Safari"
			: "A browser";
		String system = ua.contains("android") ? "Android"
			: ua.contains("iphone") || ua.contains("ipad") ? "iOS"
			: ua.contains("windows") ? "Windows"
			: ua.contains("mac os") ? "macOS"
			: ua.contains("linux") ? "Linux"
			: null;
		return system == null ? browser : browser + " on " + system;
	}
}
