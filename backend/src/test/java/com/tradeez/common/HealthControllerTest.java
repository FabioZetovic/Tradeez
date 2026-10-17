package com.tradeez.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * BUG-003's other half: the database down. The integration test only ever sees it up, so this path —
 * DEGRADED, the cause in the log and nowhere in the unauthenticated body — had never run.
 */
@ExtendWith(OutputCaptureExtension.class)
class HealthControllerTest {

	@Test
	@DisplayName("a database that cannot be reached is DEGRADED, its cause logged, and no connection detail is sent")
	@SuppressWarnings("unchecked")
	void databaseDown(CapturedOutput output) {
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		when(jdbc.queryForObject("SELECT 1", Integer.class))
			.thenThrow(new CannotGetJdbcConnectionException("jdbc:postgresql://db:5432/tradeez refused, password=hunter2"));
		ObjectProvider<BuildProperties> build = mock(ObjectProvider.class);

		Map<String, Object> body = new HealthController(jdbc, build).health();

		assertThat(body).containsEntry("status", "DEGRADED").containsEntry("database", "DOWN");
		assertThat(body.toString()).doesNotContain("jdbc:").doesNotContain("hunter2");
		assertThat(output.getAll()).contains("Health check could not reach the database").contains("refused");
	}
}
