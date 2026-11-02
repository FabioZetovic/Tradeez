package com.tradeez.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;

import com.tradeez.TradeezApplication;

/**
 * BUG-121 (T57): the deployed profile has no development secrets to fall back on. Started without them it stops
 * before it opens a port or a database connection, naming what is missing.
 */
class ProdProfileTest {

	@Test
	@DisplayName("the prod profile without its secrets, the database's settings or the mail's refuses to start")
	void refusesWithoutSecrets() {
		assertThatThrownBy(() -> new SpringApplicationBuilder(TradeezApplication.class)
				.profiles("prod")
				.properties("server.port=0", "tradeez.evidence.refresh-on-start=false", "tradeez.news.refresh-enabled=false")
				.run())
			.hasStackTraceContaining("Could not resolve placeholder")
			.satisfies(e -> {
				String trace = org.assertj.core.util.Throwables.getStackTrace(e);
				org.assertj.core.api.Assertions.assertThat(trace).containsAnyOf("JWT_SECRET", "DATABASE_URL", "DATABASE_USER",
					"DATABASE_PASSWORD", "MAIL_PROVIDER", "APP_URL");
			});
	}

	@Test
	@DisplayName("BUG-137 deployed, forwarded addresses are read by Tomcat from trusted proxies only, and by nothing else")
	void forwardedHeadersOnlyFromTheProxy() {
		var yaml = new org.springframework.beans.factory.config.YamlPropertiesFactoryBean();
		yaml.setResources(new org.springframework.core.io.ClassPathResource("application-prod.yml"));
		java.util.Properties prod = yaml.getObject();
		// DeployedServerTest shows what these two do on a real server; this keeps the profile saying them.
		org.assertj.core.api.Assertions.assertThat(prod.getProperty("server.forward-headers-strategy")).isEqualTo("native");
		org.assertj.core.api.Assertions.assertThat(prod.getProperty("tradeez.rate-limit.trusted-proxies")).isEqualTo("0");
	}
}
