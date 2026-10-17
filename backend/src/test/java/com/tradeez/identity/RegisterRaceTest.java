package com.tradeez.identity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.tradeez.identity.dto.AuthDtos.RegisterRequest;

/**
 * Two sign-ups for one address at the same moment: both find it free, and the unique index on
 * {@code lower(email)} lets one row in. The other was a 500; it is the answer a taken address gets.
 * A race is not reproducible through the API, so the repository loses it on cue here.
 */
class RegisterRaceTest {

	@Test
	@DisplayName("the sign-up that loses the race is told the address is taken, and gets no session")
	void loserIsTold() {
		AppUserRepository users = mock(AppUserRepository.class);
		RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
		when(users.existsByEmailIgnoreCase("marta@example.com")).thenReturn(false);
		when(users.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("ux_app_user_email_lower"));
		AuthService auth = new AuthService(users, refreshTokens, new BCryptPasswordEncoder(4), null, new RateLimiter(),
			new PasswordPolicy(new PwnedPasswords("http://localhost:1/", false)));

		assertThatThrownBy(() -> auth.register(
				new RegisterRequest("marta@example.com", "a-perfectly-fine-passphrase", "Marta"), "test"))
			.isInstanceOf(AuthService.EmailAlreadyRegisteredException.class);
		verify(refreshTokens, never()).save(any());
	}
}
