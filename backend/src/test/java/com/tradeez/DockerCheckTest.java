package com.tradeez;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DockerCheckTest {

	@Test
	@DisplayName("ENV-001: a missing Docker daemon is reported as exactly that")
	void explainsMissingDocker() {
		assertThatThrownBy(() -> DockerCheck.require(() -> false))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Docker is not reachable")
			.hasMessageContaining("not a code failure");
	}

	@Test
	@DisplayName("and stays out of the way when Docker is running")
	void silentWhenAvailable() {
		assertThatCode(() -> DockerCheck.require(() -> true)).doesNotThrowAnyException();
	}
}
