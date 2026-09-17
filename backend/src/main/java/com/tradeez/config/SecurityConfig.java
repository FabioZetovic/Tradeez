package com.tradeez.config;

import java.util.List;

import com.tradeez.identity.JwtAuthenticationFilter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Security foundation for Phase 0 — see DEC-022 and DEC-032.
 *
 * <p>Deliberate choices recorded in the ADR:
 * <ul>
 *   <li>JWTs travel in {@code Secure}, {@code HttpOnly}, {@code SameSite} cookies. Bearer tokens
 *       are never persisted in {@code localStorage} or {@code sessionStorage} — that would be an
 *       XSS liability, which is why "use JWT" alone was not accepted as the decision.</li>
 *   <li>Because authentication rides on cookies, CSRF protection is <b>required</b> and is enabled
 *       here, using the cookie repository so the SPA can echo the token back.</li>
 *   <li>CORS lists explicit origins with credentials enabled. Never a wildcard — a wildcard is
 *       incompatible with credentialed requests anyway.</li>
 *   <li>Sessions are stateless; the JWT is the only authentication state.</li>
 * </ul>
 *
 * <p>{@link JwtAuthenticationFilter} reads the access token from its HttpOnly cookie and populates
 * the security context. It runs before the username/password filter, which is itself disabled —
 * this application authenticates by cookie only.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private final List<String> allowedOrigins;
	private final JwtAuthenticationFilter jwtFilter;

	public SecurityConfig(
			@Value("${tradeez.cors.allowed-origins}") String allowedOrigins,
			JwtAuthenticationFilter jwtFilter) {
		this.allowedOrigins = List.of(allowedOrigins.split("\\s*,\\s*"));
		this.jwtFilter = jwtFilter;
	}

	@Bean
	SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
		csrfHandler.setCsrfRequestAttributeName(null);

		http
			.cors(cors -> cors.configurationSource(corsConfigurationSource()))
			.csrf(csrf -> csrf
				.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
				.csrfTokenRequestHandler(csrfHandler)
				// Auth endpoints are exempt only until a session cookie exists to protect.
				.ignoringRequestMatchers("/api/auth/login", "/api/auth/register", "/api/auth/refresh"))
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(
					"/api/health",
					"/actuator/health",
					"/api/auth/login",
					"/api/auth/register",
					// Authenticated by the refresh cookie itself, not by an access token.
					"/api/auth/refresh",
					"/api/auth/logout",
					"/v3/api-docs/**",
					"/swagger-ui/**",
					"/swagger-ui.html")
				.permitAll()
				.anyRequest().authenticated())
			// Return 401 rather than redirecting to a login page; this backend serves an SPA.
			.exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
			.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
			.httpBasic(basic -> basic.disable())
			.formLogin(form -> form.disable());

		return http.build();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(allowedOrigins);
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("*"));
		config.setAllowCredentials(true);
		config.setMaxAge(3600L);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);
		return source;
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
