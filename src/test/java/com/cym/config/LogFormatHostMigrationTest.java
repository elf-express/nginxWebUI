package com.cym.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.cym.model.Http;
import com.cym.testsupport.InMemorySqlHelper;

/** log_format main 補 "$host"：只改仍等於種子值的列，可重複執行。 */
class LogFormatHostMigrationTest {

	private String add(InMemorySqlHelper sql, String name, String value) {
		return sql.insertOrUpdate(new Http(name, value, 1L, "logging"));
	}

	@Test
	void seedValueGetsHost() {
		InMemorySqlHelper sql = new InMemorySqlHelper();
		String id = add(sql, "log_format", LogFormatHostMigration.SEED_LEGACY);

		assertEquals(1, LogFormatHostMigration.run(sql));
		assertEquals(LogFormatHostMigration.SEED_WITH_HOST, sql.findById(id, Http.class).getValue());
	}

	@Test
	void seedWithLfLineEndingsAlsoMigrates() {
		InMemorySqlHelper sql = new InMemorySqlHelper();
		String id = add(sql, "log_format", LogFormatHostMigration.SEED_LEGACY.replace("\r\n", "\n"));

		assertEquals(1, LogFormatHostMigration.run(sql));
		assertEquals(LogFormatHostMigration.SEED_WITH_HOST, sql.findById(id, Http.class).getValue());
	}

	@Test
	void editedValueAndOtherDirectivesUntouched() {
		InMemorySqlHelper sql = new InMemorySqlHelper();
		String edited = add(sql, "log_format", "main '$remote_addr $status'");
		String other = add(sql, "access_log", LogFormatHostMigration.SEED_LEGACY);

		assertEquals(0, LogFormatHostMigration.run(sql));
		assertEquals("main '$remote_addr $status'", sql.findById(edited, Http.class).getValue());
		assertEquals(LogFormatHostMigration.SEED_LEGACY, sql.findById(other, Http.class).getValue());
	}

	@Test
	void rerunIsNoop() {
		InMemorySqlHelper sql = new InMemorySqlHelper();
		String id = add(sql, "log_format", LogFormatHostMigration.SEED_LEGACY);
		LogFormatHostMigration.run(sql);

		assertEquals(0, LogFormatHostMigration.run(sql));
		assertEquals(LogFormatHostMigration.SEED_WITH_HOST, sql.findById(id, Http.class).getValue());
	}
}
