package com.tradeez.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.tradeez.AbstractIntegrationTest;

/**
 * V12 — the FVGs stored before the detector framework get the shape and confirmation time the
 * framework gives every new hit, so the overlay can read old and new rows alike.
 *
 * <p>Runs the migrations into a schema of its own, stops at V11, stores hits as V11 knew them, then
 * applies V12 to them.
 */
class DetectorFrameworkMigrationTest extends AbstractIntegrationTest {

	private static final String SCHEMA = "v12_backfill";
	private static final Instant C1 = Instant.parse("2024-03-12T14:00:00Z");

	@Autowired
	DataSource dataSource;

	private Flyway upTo(String version) {
		return Flyway.configure().dataSource(dataSource).schemas(SCHEMA).locations("classpath:db/migration")
			.target(version).cleanDisabled(false).load();
	}

	@AfterEach
	void dropSchema() {
		upTo("12").clean();
	}

	@Test
	@DisplayName("an FVG stored before V12 is a ZONE confirmed when its third candle closed")
	void backfill() {
		upTo("11").migrate();
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		UUID demo = jdbc.queryForObject("SELECT id FROM " + SCHEMA + ".instrument WHERE symbol = 'DEMO-NQ'", UUID.class);
		for (Map.Entry<String, Long> tf : Map.of("5m", 300L, "15m", 900L).entrySet()) {
			jdbc.update("INSERT INTO " + SCHEMA + """
				.detected_setup (instrument_id, data_class, seed, generator_version, timeframe, detector,
				    rule_version, params, methodology_source, direction, start_time, anchor_time, end_time,
				    zone_low_ticks, zone_high_ticks, evidence)
				VALUES (?, 'SIMULATED', 42, 'gen', ?, 'FVG', 'fvg-1.0.0', '{}', 'test', 'BULLISH', ?, ?, ?, 10, 20, '{}')""",
				demo, tf.getKey(), Timestamp.from(C1), Timestamp.from(C1.plusSeconds(tf.getValue())),
				Timestamp.from(C1.plusSeconds(2 * tf.getValue())));
		}

		upTo("12").migrate();

		for (Map<String, Object> row : jdbc.queryForList(
				"SELECT timeframe, shape, end_time, confirmed_at FROM " + SCHEMA + ".detected_setup")) {
			long step = row.get("timeframe").equals("5m") ? 300 : 900;
			Instant end = ((Timestamp) row.get("end_time")).toInstant();
			assertThat(row.get("shape")).isEqualTo("ZONE");
			assertThat(((Timestamp) row.get("confirmed_at")).toInstant()).isEqualTo(end.plusSeconds(step));
		}
		assertThat(jdbc.queryForObject("SELECT count(*) FROM " + SCHEMA + ".detected_setup", Integer.class))
			.isEqualTo(2);
	}
}
