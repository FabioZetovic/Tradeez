package com.tradeez;

import java.util.function.BooleanSupplier;

/**
 * ENV-001 — fail with the actual reason when Docker is down.
 *
 * <p>The integration tests start PostgreSQL in Docker. When the daemon has stopped — which happens
 * on this machine on its own — the container start failed deep inside a static initialiser, and
 * every test reported {@code ExceptionInInitializerError} / {@code NoClassDefFoundError} on
 * {@link AbstractIntegrationTest}. That reads as a compilation or wiring fault, and twice cost time
 * being debugged as one. Checking first turns it into a sentence that says what to do.
 */
final class DockerCheck {

	static final String MESSAGE = "ENV-001: Docker is not reachable, and the integration tests need "
		+ "it to start PostgreSQL (Testcontainers). This is not a code failure. Start Docker Desktop, "
		+ "wait until `docker version` prints a Server section, and run the tests again.";

	private DockerCheck() {
	}

	static void require(BooleanSupplier dockerAvailable) {
		if (!dockerAvailable.getAsBoolean()) {
			throw new IllegalStateException(MESSAGE);
		}
	}
}
