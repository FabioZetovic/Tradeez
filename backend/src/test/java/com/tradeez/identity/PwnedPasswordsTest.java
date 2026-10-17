package com.tradeez.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

/**
 * The breach check's client (DEC-064), against a local stand-in for the range API: what goes out — five
 * characters of a hash and nothing else — and what comes back, including the service being down.
 */
class PwnedPasswordsTest {

	private static final String PASSWORD = "blue lanterns over the harbour";

	private HttpServer server;
	private final List<String> paths = new ArrayList<>();
	private final List<String> padding = new ArrayList<>();
	private int status = 200;
	private String body = "";

	@BeforeEach
	void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		server.createContext("/range/", exchange -> {
			paths.add(exchange.getRequestURI().getPath());
			padding.add(exchange.getRequestHeaders().getFirst("Add-Padding"));
			byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
			if (bytes.length > 0) {
				exchange.getResponseBody().write(bytes);
			}
			exchange.close();
		});
		server.start();
	}

	@AfterEach
	void stop() {
		server.stop(0);
	}

	private PwnedPasswords client() {
		return new PwnedPasswords("http://localhost:" + server.getAddress().getPort() + "/range/", true);
	}

	@Test
	@DisplayName("only the first five characters of the SHA-1 go out, padded; the count is read from the matching suffix")
	void kAnonymous() {
		String hash = PwnedPasswords.sha1(PASSWORD);
		assertThat(hash).hasSize(40).isUpperCase();
		body = "0018A45C4D1DEF81644B54AB7F969B88D65:1\r\n" + hash.substring(5).toLowerCase() + ":42\r\n";
		assertThat(client().timesSeen(PASSWORD)).hasValue(42);
		assertThat(paths).containsExactly("/range/" + hash.substring(0, 5));
		assertThat(padding).containsExactly("true");
	}

	@Test
	@DisplayName("a suffix that is not listed, or listed only as padding, was never seen")
	void notSeen() {
		body = "0018A45C4D1DEF81644B54AB7F969B88D65:3\r\n" + PwnedPasswords.sha1(PASSWORD).substring(5) + ":0\r\n";
		assertThat(client().timesSeen(PASSWORD)).hasValue(0);
		body = "0018A45C4D1DEF81644B54AB7F969B88D65:3\r\n";
		assertThat(client().timesSeen("another passphrase entirely")).hasValue(0);
	}

	@Test
	@DisplayName("fails open: an error, an unreadable answer or no service at all skip the check")
	void failsOpen() throws IOException {
		status = 503;
		assertThat(client().timesSeen(PASSWORD)).isEmpty();
		status = 200;
		body = PwnedPasswords.sha1(PASSWORD).substring(5) + ":lots\r\n";
		assertThat(client().timesSeen(PASSWORD)).isEmpty();
		int nobody;
		try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
			nobody = socket.getLocalPort();
		}
		assertThat(new PwnedPasswords("http://localhost:" + nobody + "/range/", true).timesSeen(PASSWORD)).isEmpty();
	}

	@Test
	@DisplayName("switched off, it asks nothing")
	void disabled() {
		assertThat(new PwnedPasswords("http://localhost:" + server.getAddress().getPort() + "/range/", false)
			.timesSeen(PASSWORD)).isEqualTo(OptionalLong.empty());
		assertThat(paths).isEmpty();
	}
}
