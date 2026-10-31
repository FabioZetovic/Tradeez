package com.tradeez;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.tradeez.identity.AppUserRepository;
import com.tradeez.identity.CookieService;
import com.tradeez.identity.RefreshTokenRepository;

/**
 * A real server on a real port, configured as deployed ({@code tradeez.cookies.secure=true}).
 *
 * <p>MockMvc never forwards a refused request to {@code /error}, so it could not see BUG-078: on the real server
 * that forward was a second pass through the security chain, refused as anonymous, and every refusal came back as an
 * empty 401. And the API's description is closed when deployed (BUG-082), which only this configuration shows.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
	// As the prod profile has it (BUG-125): the host's proxy says the request was HTTPS.
	properties = { "tradeez.cookies.secure=true", "server.forward-headers-strategy=framework" })
class DeployedServerTest extends AbstractIntegrationTest {

	/** A fresh secret per run: the guard refuses the development one when deployed. */
	@DynamicPropertySource
	static void deployedSecret(DynamicPropertyRegistry registry) {
		byte[] secret = new byte[32];
		new SecureRandom().nextBytes(secret);
		registry.add("tradeez.jwt.secret", () -> Base64.getEncoder().encodeToString(secret));
	}

	@Value("${local.server.port}")
	int port;

	@Autowired
	AppUserRepository users;

	@Autowired
	RefreshTokenRepository refreshTokens;

	private final HttpClient http = HttpClient.newHttpClient();

	private String accessCookie;

	/** Every cookie the sign-up set, with its attributes. */
	private java.util.List<String> signUpCookies;

	@BeforeEach
	void signUp() throws Exception {
		refreshTokens.deleteAllInBatch();
		users.deleteAllInBatch();
		HttpResponse<String> registered = send(HttpRequest.newBuilder(uri("/api/auth/register"))
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(
				"{\"email\":\"deployed@example.com\",\"password\":\"a-perfectly-fine-passphrase\",\"displayName\":\"D\"}")));
		assertThat(registered.statusCode()).isEqualTo(201);
		signUpCookies = registered.headers().allValues("Set-Cookie");
		accessCookie = registered.headers().allValues("Set-Cookie").stream()
			.filter(c -> c.startsWith(CookieService.ACCESS_TOKEN_COOKIE + "="))
			.map(c -> c.substring(0, c.indexOf(';')))
			.findFirst().orElseThrow();
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + port + path);
	}

	private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
		return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> get(String path, boolean signedIn) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(uri(path)).GET();
		if (signedIn) {
			request.header("Cookie", accessCookie);
		}
		return send(request);
	}

	@Test
	@DisplayName("BUG-078 a refusal reaches the client with its own status, not as an expired session")
	void refusalsKeepTheirStatus() throws Exception {
		// Signed in, without the CSRF token: Spring answers 403 by forwarding to /error.
		UUID id = UUID.randomUUID();
		HttpResponse<String> noCsrf = send(HttpRequest.newBuilder(uri("/api/charts/DEMO-NQ/drawings/" + id))
			.header("Cookie", accessCookie)
			.header("Content-Type", "application/json")
			.PUT(HttpRequest.BodyPublishers.ofString("{\"id\":\"" + id + "\",\"kind\":\"horizontal-line\","
				+ "\"anchors\":[{\"time\":1710338400,\"priceTicks\":80000}]}")));
		assertThat(noCsrf.statusCode()).isEqualTo(403);
		assertThat(noCsrf.body()).contains("\"status\":403");

		// A body that is not JSON: a 400 in words, not a 401.
		HttpResponse<String> notJson = send(HttpRequest.newBuilder(uri("/api/auth/login"))
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString("not json")));
		assertThat(notJson.statusCode()).isEqualTo(400);
		assertThat(notJson.body()).contains("The request body could not be read");

		// Still a 401 where it should be: no session.
		assertThat(get("/api/me", false).statusCode()).isEqualTo(401);
		assertThat(get("/api/me", true).statusCode()).isEqualTo(200);
	}

	@Test
	@DisplayName("BUG-082 deployed, the API's description and its UI are given to nobody, signed in or not")
	void docsClosed() throws Exception {
		for (String path : java.util.List.of("/v3/api-docs", "/swagger-ui.html", "/swagger-ui/index.html")) {
			assertThat(get(path, false).statusCode()).as(path).isEqualTo(401);
			assertThat(get(path, true).statusCode()).as("signed in, " + path).isEqualTo(403);
		}
	}

	@Test
	@DisplayName("BUG-081 health says up or down, and nothing about the server, to a signed-in learner too")
	void healthWithoutDetails() throws Exception {
		for (boolean signedIn : new boolean[] { false, true }) {
			HttpResponse<String> health = get("/actuator/health", signedIn);
			assertThat(health.statusCode()).isEqualTo(200);
			// The probe groups' names are all that is added; the components named the database, the disk and its path.
			assertThat(health.body()).contains("\"status\":\"UP\"")
				.doesNotContain("components", "details", "db", "diskSpace", "path");
		}
	}

	@Test
	@DisplayName("BUG-132 deployed, /api/health says only whether it is up — not what it is or when it started")
	void apiHealthSaysLittle() throws Exception {
		HttpResponse<String> health = get("/api/health", false);
		assertThat(health.statusCode()).isEqualTo(200);
		assertThat(health.body()).contains("\"status\":\"UP\"").doesNotContain("startedAt", "builtAt", "application");
	}

	@Test
	@DisplayName("BUG-124 deployed, the session cookies are Secure, HttpOnly and SameSite=Strict; the CSRF cookie Secure and Strict")
	void cookiesAreFirstPartyOnly() throws Exception {
		assertThat(signUpCookies).filteredOn(c -> c.startsWith("tradeez_")).hasSize(2)
			.allSatisfy(c -> assertThat(c).contains("Secure", "HttpOnly", "SameSite=Strict").doesNotContain("SameSite=None"));
		HttpResponse<String> page = send(HttpRequest.newBuilder(uri("/api/health")).header("X-Forwarded-Proto", "https").GET());
		assertThat(page.headers().allValues("Set-Cookie")).filteredOn(c -> c.startsWith("XSRF-TOKEN=")).singleElement()
			.satisfies(c -> assertThat(c).contains("Secure", "SameSite=Strict"));
	}

	@Test
	@DisplayName("BUG-125 behind the host's HTTPS proxy: HSTS, a CSP, a referrer policy and a permissions policy")
	void securityHeaders() throws Exception {
		HttpResponse<String> overHttps = send(HttpRequest.newBuilder(uri("/api/health")).header("X-Forwarded-Proto", "https").GET());
		assertThat(overHttps.headers().firstValue("Strict-Transport-Security")).hasValueSatisfying(
			v -> assertThat(v).contains("max-age=31536000", "includeSubDomains"));
		assertThat(overHttps.headers().firstValue("Content-Security-Policy")).hasValueSatisfying(
			v -> assertThat(v).contains("default-src 'self'", "frame-ancestors 'none'", "object-src 'none'"));
		assertThat(overHttps.headers().firstValue("Referrer-Policy")).hasValue("strict-origin-when-cross-origin");
		assertThat(overHttps.headers().firstValue("Permissions-Policy")).hasValueSatisfying(v -> assertThat(v).contains("camera=()"));
		// Over plain HTTP no HSTS: a browser must only ever learn it over HTTPS.
		assertThat(get("/api/health", false).headers().firstValue("Strict-Transport-Security")).isEmpty();
	}
}
