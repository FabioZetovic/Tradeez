package com.tradeez.identity;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

/**
 * Builds the authentication cookies. Centralised so the security attributes are defined exactly
 * once and cannot drift between the login, refresh and logout paths.
 *
 * <p>DEC-032:
 * <ul>
 *   <li>{@code HttpOnly} — JavaScript cannot read the token, so XSS cannot exfiltrate it.</li>
 *   <li>{@code Secure} in deployed environments; relaxed on plain-HTTP localhost only, because a
 *       {@code Secure} cookie is silently dropped over HTTP and local development would appear
 *       broken for a non-obvious reason.</li>
 *   <li>{@code SameSite=None} when deployed, because the SPA on Vercel and the API on its own host
 *       are genuinely cross-site; {@code Lax} locally, where Vite proxies the API to same-origin.
 *       {@code None} requires {@code Secure}, which is why the two settings move together.</li>
 *   <li>The refresh cookie is scoped to {@code /api/auth}, so it is not attached to ordinary API
 *       calls and has a smaller exposure surface than the access cookie.</li>
 * </ul>
 *
 * <p><b>Both cookies carry the refresh lifetime, not the access-token lifetime.</b> That looks
 * wrong at first glance and is deliberate: the JWT inside the access cookie expires in minutes, but
 * the cookie itself must survive longer so the browser keeps sending it. The server rejects the
 * expired JWT, the SPA calls {@code /api/auth/refresh}, and the user never notices. A cookie that
 * vanished when its JWT expired would make silent refresh impossible.
 */
@Service
public class CookieService {

	public static final String ACCESS_TOKEN_COOKIE = "tradeez_access";
	public static final String REFRESH_TOKEN_COOKIE = "tradeez_refresh";
	public static final String REFRESH_COOKIE_PATH = "/api/auth";

	private final boolean secure;
	private final Duration cookieTtl;

	public CookieService(
			@Value("${tradeez.cookies.secure}") boolean secure,
			@Value("${tradeez.jwt.refresh-token-ttl}") Duration refreshTokenTtl) {
		this.secure = secure;
		this.cookieTtl = refreshTokenTtl;
	}

	public ResponseCookie accessCookie(String token) {
		return base(ACCESS_TOKEN_COOKIE, token, "/").maxAge(cookieTtl).build();
	}

	public ResponseCookie refreshCookie(String token) {
		return base(REFRESH_TOKEN_COOKIE, token, REFRESH_COOKIE_PATH).maxAge(cookieTtl).build();
	}

	public ResponseCookie clearAccessCookie() {
		return base(ACCESS_TOKEN_COOKIE, "", "/").maxAge(0).build();
	}

	public ResponseCookie clearRefreshCookie() {
		return base(REFRESH_TOKEN_COOKIE, "", REFRESH_COOKIE_PATH).maxAge(0).build();
	}

	private ResponseCookie.ResponseCookieBuilder base(String name, String value, String path) {
		return ResponseCookie.from(name, value)
			.httpOnly(true)
			.secure(secure)
			.sameSite(secure ? "None" : "Lax")
			.path(path);
	}
}
