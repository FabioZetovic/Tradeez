package com.tradeez.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import com.tradeez.AbstractIntegrationTest;

import jakarta.servlet.http.Cookie;

/**
 * BUG-034 — two refreshes of the same token at the same instant.
 *
 * <p>BUG-001's grace window made a duplicate refresh safe when it arrived <i>after</i> the first
 * had committed. Found in session 015: when the two genuinely overlap, both read the token
 * unrotated, the version column lets one commit, and the other failed with an optimistic-lock
 * error that reached the browser as a 401. The app's own page load does exactly this in React's
 * development mode, so a reload after the access token expired signed the user out.
 */
class ConcurrentRefreshTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	AppUserRepository users;

	@Autowired
	RefreshTokenRepository refreshTokens;

	@MockitoSpyBean
	AuthService authService;

	@BeforeEach
	void cleanSlate() {
		refreshTokens.deleteAllInBatch();
		users.deleteAllInBatch();
	}

	private Cookie registerAndGetRefreshCookie() throws Exception {
		return mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + UUID.randomUUID() + "@example.com\","
					+ "\"password\":\"a-perfectly-fine-passphrase\",\"displayName\":\"T\"}"))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getCookie(CookieService.REFRESH_TOKEN_COOKIE);
	}

	@Test
	@DisplayName("a refresh that loses the rotation race is retried and succeeds")
	void losingTheRaceIsRetried() throws Exception {
		Cookie refresh = registerAndGetRefreshCookie();

		// Deterministic stand-in for the race: the first attempt fails exactly as the loser did —
		// the winner has already rotated the token — so the retry must find it inside the grace
		// window. The winner's rotation is performed for real, just before the failure.
		doCallRealMethod().when(authService).refresh(anyString(), any());
		mockMvc.perform(post("/api/auth/refresh").cookie(refresh)).andExpect(status().isOk());
		doThrow(new ObjectOptimisticLockingFailureException(RefreshToken.class, "lost the race"))
			.doCallRealMethod()
			.when(authService).refresh(anyString(), any());

		var response = mockMvc.perform(post("/api/auth/refresh").cookie(refresh))
			.andExpect(status().isOk())
			.andReturn().getResponse();

		verify(authService, times(3)).refresh(anyString(), any());
		// And the session it hands back works.
		mockMvc.perform(get("/api/me")
				.cookie(response.getCookie(CookieService.ACCESS_TOKEN_COOKIE)))
			.andExpect(status().isOk());
	}

	@Test
	@DisplayName("several refreshes of one token fired at once all succeed")
	void simultaneousRefreshesAllSucceed() throws Exception {
		Cookie refresh = registerAndGetRefreshCookie();
		int parallel = 6;
		ExecutorService pool = Executors.newFixedThreadPool(parallel);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Integer>> results = new ArrayList<>();
		try {
			for (int i = 0; i < parallel; i++) {
				results.add(pool.submit(() -> {
					start.await();
					return mockMvc.perform(post("/api/auth/refresh").cookie(refresh))
						.andReturn().getResponse().getStatus();
				}));
			}
			start.countDown();
			for (Future<Integer> result : results) {
				assertThat(result.get(30, TimeUnit.SECONDS))
					.as("every duplicate is a concurrent duplicate, not a stolen token")
					.isEqualTo(200);
			}
		} finally {
			pool.shutdownNow();
		}
	}
}
