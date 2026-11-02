package com.tradeez;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code backend/.env.example} is committed; {@code backend/.env} is not. A real key typed into the
 * example by mistake would be pushed to GitHub with the next commit.
 *
 * <p>Session 017: that nearly happened — a Massive key was pasted into the example file instead of
 * {@code .env}. This test makes the mistake fail the build before it can be committed.
 */
class EnvExampleTest {

	@Test
	@DisplayName(".env.example carries no API key values — keys belong in the gitignored .env")
	void noKeysInTheExample() throws IOException {
		List<String> filled = Files.readAllLines(Path.of(".env.example"), StandardCharsets.UTF_8)
			.stream()
			.map(String::trim)
			.filter(line -> !line.startsWith("#"))
			.filter(line -> line.matches("[A-Z0-9_]*API_KEY\\s*=.*"))
			.filter(line -> !line.substring(line.indexOf('=') + 1).isBlank())
			// Only the variable name reaches the failure message, never the value.
			.map(line -> line.substring(0, line.indexOf('=')).trim())
			.toList();

		assertThat(filled)
			.as("move these to backend/.env (gitignored) and leave them empty in .env.example")
			.isEmpty();
	}

	@Test
	@DisplayName("BUG-133 .env.example names no test account's address or password, not even in a comment")
	void noTestAccount() throws IOException {
		List<String> filled = Files.readAllLines(Path.of(".env.example"), StandardCharsets.UTF_8)
			.stream()
			.map(line -> line.replaceFirst("^#\\s*", "").trim())
			.filter(line -> line.matches("DEV_TEST_[A-Z]+\\s*=.*"))
			.map(line -> line.substring(0, line.indexOf('=')).trim())
			.toList();
		assertThat(filled).as("these belong in backend/.env only").isEmpty();
	}
}
