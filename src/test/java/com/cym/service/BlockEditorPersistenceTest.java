package com.cym.service;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.cym.model.Stream;
import com.cym.model.Upstream;
import com.cym.sqlhelper.bean.Sort;
import com.cym.sqlhelper.utils.Condition;
import com.cym.sqlhelper.utils.ConditionWrapper;
import com.cym.sqlhelper.utils.SqlHelper;
import com.cym.model.UpstreamServer;
import com.cym.controller.api.BasicApiController;
import com.cym.controller.adminPage.UpstreamController;
import com.cym.model.Param;
import com.cym.utils.MessageUtils;
import com.cym.utils.JsonResult;
import com.cym.ext.NginxBlockDraft;
import com.cym.config.SQLConstants;
import com.cym.sqlhelper.config.DataSourceEmbed;
import com.cym.sqlhelper.utils.JdbcTemplate;
import com.cym.sqlhelper.utils.SqlUtils;
import com.cym.sqlhelper.utils.TableUtils;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import cn.hutool.core.util.ReflectUtil;

class BlockEditorPersistenceTest {
	private MemorySqlHelper sql;
	private UpstreamService upstreams;
	private StreamService streams;

	@BeforeEach
	void setUp() {
		sql = new MemorySqlHelper();
		upstreams = new UpstreamService();
		streams = new StreamService();
		ReflectUtil.setFieldValue(upstreams, "sqlHelper", sql);
		ReflectUtil.setFieldValue(streams, "sqlHelper", sql);
	}

	@Test
	void missingModeReadsAsLegacy() {
		assertEquals("legacy", new Upstream().getConfigMode());
		assertEquals("legacy", new Stream().getConfigMode());
	}

	@Test
	void savedBlockReloadsWithExactRawText() {
		String raw = "\r\n    least_conn; # keep this\r\n    server 127.0.0.1:8080;\r\n";
		Upstream saved = upstreams.saveBlock(null, "api", 0, raw, false);
		Upstream reloaded = upstreams.findById(saved.getId());

		assertEquals("block", reloaded.getConfigMode());
		assertEquals(raw, reloaded.getRawContent());
		assertEquals("api", reloaded.getName());
	}

	@Test
	void blockUpstreamNamesAreTrimmedBeforeUniquenessChecks() {
		Upstream saved = upstreams.saveBlock(null, "  api  ", 0, "server 127.0.0.1:8080;", false);

		assertEquals("api", saved.getName());
		assertThrows(IllegalArgumentException.class,
				() -> upstreams.saveBlock(null, "api ", 0, "server 127.0.0.2:8080;", false));
	}

	@Test
	void legacyUpstreamServiceSaveCannotAcceptBlockModeFromRequest() {
		Upstream request = new Upstream();
		request.setName("api");
		request.setProxyType(0);
		request.setConfigMode("block");
		request.setRawContent("invalid block text");

		upstreams.addOver(request, new ArrayList<>(), "[]");

		Upstream saved = upstreams.findById(request.getId());
		assertEquals("legacy", saved.getConfigMode());
		assertNull(saved.getRawContent());
	}

	@Test
	void sqliteSchemaPersistsRawTextAndKeepsOldRowsLegacy() throws Exception {
		String oldSuffix = SQLConstants.SUFFIX;
		String oldOrderType = SQLConstants.ORDER_TYPE_INT;
		String oldLimitScript = SQLConstants.LIMIT_SCRIPT;
		try (SqliteFixture fixture = new SqliteFixture()) {
			SQLConstants.SUFFIX = "`";
			SQLConstants.ORDER_TYPE_INT = "SIGNED";
			SQLConstants.LIMIT_SCRIPT = " LIMIT {offset},{limit}";
			fixture.init(Upstream.class, Stream.class);
			UpstreamService sqlUpstreams = new UpstreamService();
			StreamService sqlStreams = new StreamService();
			ReflectUtil.setFieldValue(sqlUpstreams, "sqlHelper", fixture.sqlHelper);
			ReflectUtil.setFieldValue(sqlStreams, "sqlHelper", fixture.sqlHelper);

			String raw = "\r\n    least_conn; # preserve\r\n    server 127.0.0.1:8080;\r\n";
			Upstream upstream = sqlUpstreams.saveBlock(null, "sqlite_api", 0, raw, false);
			Stream stream = sqlStreams.saveBlock(null, "server", "\n    listen 9001;\n");
			Upstream old = new Upstream();
			old.setName("old_api");
			fixture.sqlHelper.insertOrUpdate(old);

			Upstream reloadedUpstream = fixture.sqlHelper.findById(upstream.getId(), Upstream.class);
			Stream reloadedStream = fixture.sqlHelper.findById(stream.getId(), Stream.class);
			Upstream reloadedOld = fixture.sqlHelper.findById(old.getId(), Upstream.class);
			assertEquals(raw, reloadedUpstream.getRawContent());
			assertEquals("block", reloadedUpstream.getConfigMode());
			assertEquals("\n    listen 9001;\n", reloadedStream.getRawContent());
			assertEquals("block", reloadedStream.getConfigMode());
			assertNull(ReflectUtil.getFieldValue(reloadedOld, "configMode"));
			assertEquals("legacy", reloadedOld.getConfigMode());
		} finally {
			SQLConstants.SUFFIX = oldSuffix;
			SQLConstants.ORDER_TYPE_INT = oldOrderType;
			SQLConstants.LIMIT_SCRIPT = oldLimitScript;
		}
	}

	@Test
	void invalidUpstreamBlockDoesNotChangeSavedText() {
		Upstream saved = upstreams.saveBlock(null, "api", 0, " server 127.0.0.1:8080;\r\n", false);

		assertThrows(IllegalArgumentException.class,
				() -> upstreams.saveBlock(saved.getId(), "api", 0, "server {", false));
		assertEquals(" server 127.0.0.1:8080;\r\n", upstreams.findById(saved.getId()).getRawContent());
	}

	@Test
	void legacyOverwriteRequiresExplicitConversion() {
		Upstream legacy = new Upstream();
		legacy.setName("api");
		legacy.setId("legacy-1");
		sql.insertOrUpdate(legacy);

		assertThrows(IllegalStateException.class,
				() -> upstreams.saveBlock("legacy-1", "api", 0, "server 127.0.0.1:8080;", false));
		assertEquals("legacy", upstreams.findById("legacy-1").getConfigMode());
		assertEquals("api", upstreams.saveBlock("legacy-1", "api", 0, "server 127.0.0.1:8080;", true).getName());
	}

	@Test
	void duplicateUpstreamNameIsRejectedWithoutChangingExistingRecord() {
		Upstream original = upstreams.saveBlock(null, "api", 0, "server 127.0.0.1:8080;", false);
		Upstream second = upstreams.saveBlock(null, "assets", 0, "server 127.0.0.2:8080;", false);

		assertThrows(IllegalArgumentException.class,
				() -> upstreams.saveBlock(second.getId(), "api", 0, "server 127.0.0.3:8080;", false));
		assertEquals("assets", upstreams.findById(second.getId()).getName());
		assertEquals("api", upstreams.findById(original.getId()).getName());
	}

	@Test
	void repeatedStreamServerBlocksSaveAndLegacyGuideDoesNotReplaceThem() {
		Stream first = streams.saveBlock(null, "server", "listen 9001;\n");
		Stream second = streams.saveBlock(null, "server", "listen 9002;\n");

		assertNotEquals(first.getId(), second.getId());
		streams.setAll(List.of(legacy("log_format basic", "'value';")));
		Stream guide = legacy("log_format basic", "'value';");
		streams.setAll(List.of(guide));

		List<Stream> all = streams.findAll();
		assertEquals(2, all.stream().filter(Stream::isBlockMode).count());
		assertEquals("listen 9001;\n", streams.findById(first.getId()).getRawContent());
		assertEquals("listen 9002;\n", streams.findById(second.getId()).getRawContent());
	}

	@Test
	void blockUpstreamServersAreExcludedFromMonitorAndConnectionTests() throws Exception {
		Upstream block = upstreams.saveBlock(null, "api", 0, "server unreachable.invalid:80;", false);
		Upstream legacy = new Upstream();
		legacy.setName("legacy");
		legacy.setMonitor(1);
		sql.insertOrUpdate(legacy);
		UpstreamServer blockServer = server(block.getId(), "unreachable.invalid");
		UpstreamServer legacyServer = server(legacy.getId(), "127.0.0.1");
		sql.insertOrUpdate(blockServer);
		sql.insertOrUpdate(legacyServer);

		assertEquals(List.of(legacyServer.getId()), upstreams.getAllServer().stream().map(UpstreamServer::getId).toList());
		assertEquals(List.of(legacyServer.getId()), upstreams.getServerListByMonitor(1).stream().map(UpstreamServer::getId).toList());

		ConfService conf = new ConfService();
		ReflectUtil.setFieldValue(conf, "sqlHelper", sql);
		List<Map<String, String>> results = new ArrayList<>();
		Method addUpstreamTestResults = ConfService.class.getDeclaredMethod("addUpstreamTestResults", List.class, String.class, String.class, String.class);
		addUpstreamTestResults.setAccessible(true);
		addUpstreamTestResults.invoke(conf, results, "site", "/", block.getId());
		assertTrue(results.isEmpty());
	}

	@Test
	void legacyUpstreamPreviewMatchesHttpGenerationOrderAndEnabledServers() {
		Upstream upstream = new Upstream();
		upstream.setName("preview_api");
		upstream.setProxyType(0);
		upstream.setId("preview-1");
		sql.insertOrUpdate(upstream);

		UpstreamServer enabled = server(upstream.getId(), "192.0.2.10");
		enabled.setPort(8080);
		enabled.setStatus("none");
		UpstreamServer disabled = server(upstream.getId(), "192.0.2.11");
		disabled.setPort(8081);
		disabled.setEnable(0);
		disabled.setStatus("none");
		sql.insertOrUpdate(enabled);
		sql.insertOrUpdate(disabled);

		Param before = new Param();
		before.setName("hash");
		before.setValue("$remote_addr consistent");
		before.setPosition(1);
		Param after = new Param();
		after.setName("keepalive");
		after.setValue("8");
		after.setPosition(0);

		ParamService params = new ParamService() {
			@Override public List<Param> getListByTypeId(String id, String type) { return List.of(before, after); }
		};
		ConfService conf = new ConfService();
		ReflectUtil.setFieldValue(conf, "sqlHelper", sql);
		ReflectUtil.setFieldValue(conf, "upstreamService", upstreams);
		ReflectUtil.setFieldValue(conf, "paramService", params);
		UpstreamController controller = new UpstreamController();
		ReflectUtil.setFieldValue(controller, "sqlHelper", sql);
		ReflectUtil.setFieldValue(controller, "confService", conf);

		JsonResult<?> result = controller.blockPreview(upstream.getId());
		assertTrue(result.isSuccess(), result.getMsg());
		NginxBlockDraft draft = (NginxBlockDraft) result.getObj();
		String body = draft.getBody();
		int beforeIndex = body.indexOf("hash $remote_addr consistent;");
		int serverIndex = body.indexOf("server 192.0.2.10:8080;");
		int afterIndex = body.indexOf("keepalive 8;");
		assertTrue(beforeIndex >= 0 && serverIndex >= 0 && afterIndex >= 0, body);
		assertTrue(beforeIndex < serverIndex && serverIndex < afterIndex, body);
		assertFalse(body.contains("192.0.2.11"));
	}

	@Test
	void legacyStreamApiCannotOverwriteBlockRawText() {
		Stream block = streams.saveBlock(null, "server", "listen 9001;\n");
		Stream request = new Stream();
		request.setId(block.getId());
		request.setName("server");
		request.setValue("listen 9002;");

		BasicApiController api = new BasicApiController();
		ReflectUtil.setFieldValue(api, "sqlHelper", sql);
		ReflectUtil.setFieldValue(api, "m", new MessageUtils() {
			@Override public String get(String key) { return key; }
		});
		JsonResult<?> result = api.insertOrUpdateStream(request);

		assertFalse(result.isSuccess());
		assertEquals("listen 9001;\n", streams.findById(block.getId()).getRawContent());
	}

	@Test
	void legacyStreamPageSaveCannotAcceptBlockModeFromRequest() {
		Stream request = new Stream();
		request.setName("server");
		request.setValue("listen 9001;");
		request.setConfigMode("block");
		request.setRawContent("invalid block text");

		com.cym.controller.adminPage.StreamController controller = new com.cym.controller.adminPage.StreamController();
		ReflectUtil.setFieldValue(controller, "sqlHelper", sql);
		controller.addOver(request);

		Stream saved = sql.findById(request.getId(), Stream.class);
		assertEquals("legacy", saved.getConfigMode());
		assertNull(saved.getRawContent());
	}

	private static Stream legacy(String name, String value) {
		Stream stream = new Stream();
		stream.setName(name);
		stream.setValue(value);
		return stream;
	}

	private static UpstreamServer server(String upstreamId, String host) {
		UpstreamServer server = new UpstreamServer();
		server.setUpstreamId(upstreamId);
		server.setServer(host);
		server.setPort(80);
		server.setEnable(1);
		return server;
	}

	private static final class SqliteFixture implements AutoCloseable {
		private final HikariDataSource dataSource;
		private final SqlHelper sqlHelper;
		private final TableUtils tableUtils;

		SqliteFixture() {
			HikariConfig config = new HikariConfig();
			config.setJdbcUrl("jdbc:sqlite::memory:");
			config.setDriverClassName("org.sqlite.JDBC");
			config.setMaximumPoolSize(1);
			config.setUsername("");
			config.setPassword("");
			dataSource = new HikariDataSource(config);

			DataSourceEmbed embed = new DataSourceEmbed();
			embed.setDataSource(dataSource);
			JdbcTemplate jdbc = new JdbcTemplate();
			ReflectUtil.setFieldValue(jdbc, "dataSourceEmbed", embed);
			SqlUtils sqlUtils = new SqlUtils();
			ReflectUtil.setFieldValue(sqlUtils, "jdbcTemplate", jdbc);
			ReflectUtil.setFieldValue(sqlUtils, "print", false);
			tableUtils = new TableUtils();
			ReflectUtil.setFieldValue(tableUtils, "jdbcTemplate", jdbc);
			ReflectUtil.setFieldValue(tableUtils, "sqlUtils", sqlUtils);
			sqlHelper = new SqlHelper();
			ReflectUtil.setFieldValue(sqlHelper, "jdbcTemplate", jdbc);
			ReflectUtil.setFieldValue(sqlHelper, "tableUtils", tableUtils);
			ReflectUtil.setFieldValue(sqlHelper, "print", false);
		}

		void init(Class<?>... types) throws Exception {
			for (Class<?> type : types) tableUtils.initTable(type);
		}

		@Override
		public void close() {
			dataSource.close();
		}
	}

	/** In-memory store that preserves the actual SqlHelper service boundary without a filesystem DB. */
	private static class MemorySqlHelper extends SqlHelper {
		private final Map<Class<?>, LinkedHashMap<String, Object>> rows = new LinkedHashMap<>();
		private long nextId = 1;

		@Override
		public String insertOrUpdate(Object object) {
			String id = (String) ReflectUtil.getFieldValue(object, "id");
			if (id == null) {
				id = Long.toString(nextId++);
				ReflectUtil.setFieldValue(object, "id", id);
			}
			rows.computeIfAbsent(object.getClass(), ignored -> new LinkedHashMap<>()).put(id, object);
			return id;
		}

		@Override
		@SuppressWarnings("unchecked")
		public <T> T findById(String id, Class<T> type) {
			return (T) rows.getOrDefault(type, new LinkedHashMap<>()).get(id);
		}

		@Override
		@SuppressWarnings("unchecked")
		public <T> List<T> findAll(Sort sort, Class<T> type) {
			return new ArrayList<>((List<T>) rows.getOrDefault(type, new LinkedHashMap<>()).values().stream().toList());
		}

		@Override
		public <T> List<T> findListByQuery(ConditionWrapper conditions, Sort sort, Class<T> type) {
			return findAll(sort, type).stream().filter(row -> matches(row, conditions)).toList();
		}

		@Override
		public <T> T findOneByQuery(ConditionWrapper conditions, Class<T> type) {
			return findListByQuery(conditions, null, type).stream().findFirst().orElse(null);
		}

		@Override
		public Long findCountByQuery(ConditionWrapper conditions, Class<?> type) {
			return findListByQuery(conditions, null, type).size() * 1L;
		}

		@Override
		public void deleteById(String id, Class<?> type) {
			rows.getOrDefault(type, new LinkedHashMap<>()).remove(id);
		}

		@Override
		public void deleteByQuery(ConditionWrapper conditions, Class<?> type) {
			rows.getOrDefault(type, new LinkedHashMap<>()).values().removeIf(row -> matches(row, conditions));
		}

		private boolean matches(Object row, ConditionWrapper wrapper) {
			if (wrapper == null) return true;
			@SuppressWarnings("unchecked")
			List<Object> conditions = (List<Object>) ReflectUtil.getFieldValue(wrapper, "list");
			for (Object item : conditions) {
				if (!(item instanceof Condition condition)) continue;
				Object actual = ReflectUtil.getFieldValue(row, condition.getColumn());
				Object expected = condition.getValue();
				if ("=".equals(condition.getOperation()) && !java.util.Objects.equals(actual, expected)) return false;
				if ("<>".equals(condition.getOperation()) && java.util.Objects.equals(actual, expected)) return false;
				if ("IN".equals(condition.getOperation()) && expected instanceof java.util.Collection<?> values && !values.contains(actual)) return false;
			}
			return true;
		}
	}
}
