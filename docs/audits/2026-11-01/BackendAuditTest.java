package com.tradeez.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.tradeez.AbstractIntegrationTest;
import com.tradeez.identity.AccountEmails;
import com.tradeez.identity.AccountExport;
import com.tradeez.identity.AuthService;
import com.tradeez.identity.CookieService;
import com.tradeez.identity.RateLimiter;
import com.tradeez.identity.dto.AuthDtos.ChangePasswordRequest;
import com.tradeez.identity.dto.AuthDtos.LoginRequest;
import com.tradeez.identity.dto.AuthDtos.RegisterRequest;
import com.tradeez.identity.mail.OutboxMailer;
import com.tradeez.marketdata.Bar;
import com.tradeez.marketdata.MarketBarStore;
import com.tradeez.marketdata.MarketBarStore.StoredBar;
import com.tradeez.marketdata.MarketDataService;
import com.tradeez.marketdata.SessionCalendar;
import com.tradeez.practice.FillEngine.Direction;
import com.tradeez.practice.PracticeDtos.PlanRequest;
import com.tradeez.practice.PracticeService;
import com.tradeez.drills.RiskDrillService;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.ObjectMapper;

/**
 * Bounded forensic probes, outside src/test so they do not become permanent assertions of unsafe behavior.
 * Passing means the documented defect was observed, NOT that the app is secure.
 * Uses AbstractIntegrationTest's disposable PostgreSQL container; no user database or real email.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = { "server.forward-headers-strategy=framework" })
public class BackendAuditTest extends AbstractIntegrationTest {
    private static final String PASSWORD = "a-perfectly-fine-passphrase";
    @Autowired AuthService auth;
    @Autowired RateLimiter limits;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired AccountExport exports;
    @Autowired AccountEmails emails;
    @Autowired OutboxMailer mail;
    @Autowired RiskDrillService risk;
    @Autowired PracticeService practice;
    @Autowired MarketDataService markets;
    @Autowired MarketBarStore barStore;
    @Autowired DataSource datasource;
    @Value("${local.server.port}") int port;
    private final HttpClient http = HttpClient.newHttpClient();

    private AuthService.IssuedTokens account() {
        return auth.register(new RegisterRequest("audit-" + UUID.randomUUID() + "@example.test", PASSWORD, "Audit"), "audit").signedIn();
    }

    private void validAccess(AuthService.IssuedTokens tokens) throws Exception {
        assertThat(mvc.perform(get("/api/me").cookie(new Cookie(CookieService.ACCESS_TOKEN_COOKIE, tokens.accessToken())))
            .andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    private int attempt(String forwardedHeader, String ip, String email) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/auth/login"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(new LoginRequest(email, PASSWORD))));
        if (forwardedHeader != null) builder.header(forwardedHeader, ip);
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    @Test void forwardedHeadersBypassBothLoginLimits() throws Exception {
        String unknown = "absent-" + UUID.randomUUID() + "@example.test";
        List<Integer> plain = new ArrayList<>();
        for (int n = 0; n < 22; n++) plain.add(attempt(null, null, unknown));
        assertThat(plain).contains(429);
        limits.clear();
        List<Integer> spoofed = new ArrayList<>();
        for (int n = 1; n <= 25; n++) spoofed.add(attempt("X-Forwarded-For", "198.51.100." + n, unknown));
        System.out.println("AUDIT forwarded login statuses: " + spoofed);
        assertThat(spoofed).allMatch(status -> status == 401);
        limits.clear();
        List<Integer> rfc = new ArrayList<>();
        for (int n = 1; n <= 25; n++) rfc.add(attempt("Forwarded", "for=203.0.113." + n, unknown));
        System.out.println("AUDIT RFC Forwarded login statuses: " + rfc);
        assertThat(rfc).allMatch(status -> status == 401);
    }

    @Test void rotatedTokenReturnsAfterLogoutEverywhere() throws Exception {
        var original = account();
        auth.refresh(original.refreshToken(), "audit");
        auth.logoutEverywhere(original.user().getId());
        var resurrected = auth.refresh(original.refreshToken(), "audit");
        validAccess(resurrected);
        System.out.println("AUDIT logout-all bypass: fresh authenticated access from revoked ancestor");
    }

    @Test void rotatedTokenReturnsAfterPasswordChange() throws Exception {
        var original = account();
        auth.refresh(original.refreshToken(), "audit");
        auth.changePassword(original.user().getId(), new ChangePasswordRequest(PASSWORD, "a-different-safe-passphrase"), "audit");
        validAccess(auth.refresh(original.refreshToken(), "audit"));
        System.out.println("AUDIT password-change bypass: fresh authenticated access from revoked ancestor");
    }

    @Test void rotatedTokenReturnsAfterOrdinaryLogout() throws Exception {
        var original = account();
        var successor = auth.refresh(original.refreshToken(), "audit");
        auth.logout(successor.refreshToken());
        validAccess(auth.refresh(original.refreshToken(), "audit"));
        System.out.println("AUDIT ordinary logout bypass: rotated ancestor creates another session");
    }

    @Test void detectedTokenTheftLeavesAccessTokensAlive() throws Exception {
        var original = account();
        var successor = auth.refresh(original.refreshToken(), "audit");
        jdbc.update("UPDATE refresh_token SET revoked_at = now() - interval '30 seconds' WHERE token_hash = ?",
            sha(original.refreshToken()));
        assertThatThrownBy(() -> auth.refresh(original.refreshToken(), "audit")).isInstanceOf(AuthService.TokenReuseDetectedException.class);
        validAccess(successor);
        System.out.println("AUDIT detected refresh theft: successor access still returns 200");
    }

    private String sha(String value) throws Exception {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
            .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test void accountExportHandsOutUnansweredRiskAnswerAndWorking() throws Exception {
        var a = account();
        var question = risk.create(a.user().getId(), "STREAK");
        assertThat(question.result()).isNull();
        var body = mvc.perform(get("/api/me/export").cookie(new Cookie(CookieService.ACCESS_TOKEN_COOKIE, a.accessToken())))
            .andReturn().getResponse();
        assertThat(body.getStatus()).isEqualTo(200);
        var rows = json.readTree(body.getContentAsString()).get("riskDrills");
        assertThat(rows.get(0).get("answer")).isNotNull();
        assertThat(rows.get(0).get("question").get("working")).isNotNull();
        double answer = rows.get(0).get("answer").asDouble();
        assertThat(risk.answer(a.user().getId(), question.id(), answer).result().correct()).isTrue();
        System.out.println("AUDIT exported unanswered risk key: exact answer accepted as correct");
    }

    @Test void accountExportLeaksUnrevealedPracticeOutcomeAndRealDate() throws Exception {
        var a = account();
        UUID nq = markets.requireInstrument("NQ").getId();
        Instant from = Instant.parse("2025-03-03T00:00:00Z"), to = Instant.parse("2025-03-22T00:00:00Z");
        Random random = new Random(20250303L);
        List<StoredBar> stored = new ArrayList<>();
        long price = 80000;
        for (Instant t = from; t.isBefore(to); t = t.plusSeconds(60)) {
            if (!SessionCalendar.isOpen(t)) continue;
            long close = price + random.nextInt(17) - 8;
            stored.add(new StoredBar(new Bar(t, price, Math.max(price, close) + 3, Math.min(price, close) - 3, close, 10), 202503));
            price = close;
        }
        barStore.insert(nq, "1m", stored);
        jdbc.update("""
            INSERT INTO market_data_import (instrument_id, source, dataset, symbol, resolution,
                range_from, range_to, estimated_cost_usd, bars_stored, status)
            VALUES (?, 'massive', 'CME', 'NQH5', '1m', ?, ?, 0, ?, 'DONE')
            """, nq, Timestamp.from(from), Timestamp.from(to), stored.size());
        var created = practice.create(a.user().getId(), "NQ", "5m", null, null);
        assertThat(created.state().revealedStartTime()).isNull();
        long reference = created.bars().getLast().close();
        var state = practice.submitPlan(a.user().getId(), created.state().id(),
            new PlanRequest(Direction.LONG, reference - 100, reference + 100, 1));
        assertThat(state.trades().getLast().result()).isNull();
        var exported = json.valueToTree(exports.of(a.user().getId()));
        assertThat(exported.get("tradeResults").get(0).get("outcome")).isNotNull();
        assertThat(exported.get("tradeResults").get(0).get("exit_time")).isNotNull();
        assertThat(exported.get("practiceSessions").get(0).get("start_time").asString()).contains("2025-03");
        System.out.println("AUDIT export leaks active real practice date, future exit and outcome");
    }

    @Test void resetLinkSurvivesAuthenticatedPasswordChange() throws Exception {
        var a = account();
        emails.sendReset(a.user());
        String text = mail.recent().getLast().text();
        String raw = text.substring(text.indexOf("/reset-password#") + "/reset-password#".length()).split("\\s")[0];
        auth.changePassword(a.user().getId(), new ChangePasswordRequest(PASSWORD, "a-different-safe-passphrase"), "audit");
        validAccess(auth.resetPassword(raw, "a-third-safe-passphrase", "audit"));
        System.out.println("AUDIT old reset link changes password after authenticated password change");
    }

    @Test void newsExtremeEpochEscapesValidationAsServerError() throws Exception {
        var a = account();
        var response = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/news?from=9223372036854775807"))
            .header("Cookie", CookieService.ACCESS_TOKEN_COOKIE + "=" + a.accessToken()).GET().build(), HttpResponse.BodyHandlers.ofString());
        System.out.println("AUDIT extreme news epoch: " + response.statusCode());
        assertThat(response.statusCode()).isEqualTo(500);
    }

    @Test void oneResetLinkCanBeRedeemedTwiceConcurrently() throws Exception {
        var a = account();
        emails.sendReset(a.user());
        String text = mail.recent().getLast().text();
        String raw = text.substring(text.indexOf("/reset-password#") + "/reset-password#".length()).split("\\s")[0];
        CompletableFuture<AuthService.IssuedTokens> first, second;
        try (var lock = datasource.getConnection()) {
            lock.setAutoCommit(false);
            try (var select = lock.prepareStatement("SELECT id FROM email_token WHERE token_hash = ? FOR UPDATE")) {
                select.setString(1, sha(raw));
                try (var ignored = select.executeQuery()) { assertThat(ignored.next()).isTrue(); }
            }
            first = CompletableFuture.supplyAsync(() -> auth.resetPassword(raw, "a-second-safe-passphrase", "audit-first"));
            second = CompletableFuture.supplyAsync(() -> auth.resetPassword(raw, "a-third-safe-passphrase", "audit-second"));
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            int waiting = 0;
            while (System.nanoTime() < until && waiting < 2) {
                waiting = jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname = current_database() "
                    + "AND wait_event_type = 'Lock' AND lower(query) LIKE '%update email_token%'", Integer.class);
                if (waiting < 2) Thread.sleep(25);
            }
            lock.commit();
            assertThat(waiting).as("both requests validated the same unused token before its spend").isEqualTo(2);
        }
        var one = first.get(5, TimeUnit.SECONDS);
        var two = second.get(5, TimeUnit.SECONDS);
        List<Integer> statuses = new ArrayList<>();
        for (var issued : List.of(one, two)) {
            statuses.add(mvc.perform(get("/api/me").cookie(new Cookie(CookieService.ACCESS_TOKEN_COOKIE, issued.accessToken())))
                .andReturn().getResponse().getStatus());
        }
        assertThat(statuses).contains(200).allMatch(s -> s == 200 || s == 401);
        System.out.println("AUDIT single-use reset link accepted by both concurrent calls; access statuses " + statuses);
    }

    @Test void signupNoticesCanExhaustPasswordRecoveryMailBudget() {
        var a = account();
        for (int n = 0; n < 3; n++) emails.sendAlreadyRegistered(a.user());
        int before = mail.recent().size();
        auth.forgotPassword(a.user().getEmail());
        assertThat(mail.recent()).hasSize(before);
        System.out.println("AUDIT three unsolicited signup notices prevent a password-reset email");
    }

    @Test void nullDrawingAnchorCrashesBeforeValidationResponse() {
        var a = account();
        String id = UUID.randomUUID().toString();
        assertThatThrownBy(() -> mvc.perform(put("/api/charts/NQ/drawings/" + id)
            .cookie(new Cookie(CookieService.ACCESS_TOKEN_COOKIE, a.accessToken())).with(csrf())
            .contentType("application/json").content("{\"id\":\"" + id + "\",\"kind\":\"horizontal-line\",\"anchors\":[null]}")))
            .hasRootCauseInstanceOf(NullPointerException.class);
        System.out.println("AUDIT null drawing anchor throws an unhandled NullPointerException");
    }
}
