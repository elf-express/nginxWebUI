package com.cym.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.cym.ext.ConfExt;
import com.cym.model.Stream;
import com.cym.model.DenyAllow;
import com.cym.model.Param;
import com.cym.model.Server;
import com.cym.model.Template;
import com.cym.model.Upstream;
import com.cym.model.UpstreamServer;
import com.cym.controller.adminPage.StreamController;
import com.cym.utils.JsonResult;
import com.cym.sqlhelper.bean.Sort;
import com.cym.sqlhelper.utils.Condition;
import com.cym.sqlhelper.utils.ConditionWrapper;
import com.cym.sqlhelper.utils.SqlHelper;

import cn.hutool.core.util.ReflectUtil;

class BlockEditorConfTest {
	private MemorySqlHelper sql;
	private ConfService conf;
	private UpstreamService upstreams;
	private StreamService streams;
	private final List<Server> streamServers = new ArrayList<>();
	private final Map<String, List<Param>> paramsByTypeId = new LinkedHashMap<>();

	@BeforeEach
	void setUp() {
		streamServers.clear();
		paramsByTypeId.clear();
		sql = new MemorySqlHelper();
		upstreams = new UpstreamService();
		ReflectUtil.setFieldValue(upstreams, "sqlHelper", sql);
		streams = new StreamService();
		ReflectUtil.setFieldValue(streams, "sqlHelper", sql);
		conf = new ConfService();
		ReflectUtil.setFieldValue(conf, "sqlHelper", sql);
		ReflectUtil.setFieldValue(conf, "upstreamService", upstreams);
		ReflectUtil.setFieldValue(conf, "settingService", new SettingService() {
			@Override public String get(String key) { return null; }
		});
		ReflectUtil.setFieldValue(conf, "serverService", new ServerService() {
			@Override public List<Server> getListByProxyType(String[] types) {
				return streamServers.stream().filter(server -> List.of(types).contains(String.valueOf(server.getProxyType()))).toList();
			}
		});
		ReflectUtil.setFieldValue(conf, "paramService", new ParamService() {
			@Override public List<Param> getListByTypeId(String id, String type) {
				return paramsByTypeId.getOrDefault(id + "|" + type, List.of());
			}
		});
		TemplateService templates = new TemplateService();
		ReflectUtil.setFieldValue(templates, "sqlHelper", sql);
		ReflectUtil.setFieldValue(conf, "templateService", templates);
		DenyAllowService denyAllow = new DenyAllowService();
		ReflectUtil.setFieldValue(denyAllow, "sqlHelper", sql);
		ReflectUtil.setFieldValue(conf, "denyAllowService", denyAllow);
	}

	@Test
	void threeBlockUpstreamsAreRenderedExactlyOnce() {
		String rawBody = "\n    least_conn; # keep {comment}\n    server 10.0.0.1:8080; # token ${upstream};\n";
		upstreams.saveBlock(null, "website_api", 0, rawBody, false);
		upstreams.saveBlock(null, "website_assets", 0, "\n    server 10.0.0.2:8080;\n", false);
		upstreams.saveBlock(null, "website_jobs", 0, "\n    server 10.0.0.3:8080;\n", false);

		String output = conf.buildConf(false, false).getConf();

		assertEquals(1, count(output, "upstream website_api {"));
		assertEquals(1, count(output, "upstream website_assets {"));
		assertEquals(1, count(output, "upstream website_jobs {"));
		assertTrue(output.contains(rawBody));
		assertFalse(output.contains("__NGX_RAW_BLOCK_"));
	}

	@Test
	void blockStreamChildLivesInsideOneStreamWrapperAfterLegacyRecords() {
		Stream legacy = new Stream();
		legacy.setName("log_format");
		legacy.setValue("basic '$remote_addr';");
		legacy.setSeq(1L);
		sql.insertOrUpdate(legacy);
		streams.saveBlock(null, "server", "\n    listen 9001; # preserve { brace }\n");
		streams.saveBlock(null, "server", "\n    listen 9002;\n");

		String output = conf.buildConf(false, false).getConf();

		assertEquals(1, count(output, "stream {"));
		assertEquals(2, count(output, "server {"));
		assertTrue(output.indexOf("log_format basic") < output.indexOf("server {"));
		assertTrue(output.contains("listen 9001; # preserve { brace }"));
		assertTrue(output.contains("listen 9002;"));
	}

	@Test
	void blockUpstreamIsSameInCombinedAndDecomposedOutput() {
		String rawBody = "\r\n    server 10.0.0.1:8080; # {backend}\r\n";
		upstreams.saveBlock(null, "website_api", 0, rawBody, false);

		ConfExt combined = conf.buildConf(false, false);
		ConfExt decomposed = conf.buildConf(true, false);

		assertTrue(combined.getConf().contains(rawBody));
		assertTrue(decomposed.getConf().contains("include conf.d/upstreams.website_api.conf"));
		assertTrue(decomposed.getFileList().stream().anyMatch(file ->
				file.getName().equals("upstreams.website_api.conf")
						&& file.getConf().contains(rawBody)));
	}

	@Test
	void legacyUpstreamStillUsesExistingFormGeneration() {
		Upstream legacy = new Upstream();
		legacy.setName("legacy_api");
		legacy.setProxyType(0);
		legacy.setDescr("old description");
		legacy.setTactics("least_conn");
		sql.insertOrUpdate(legacy);

		String output = conf.buildConf(false, false).getConf();

		assertTrue(output.contains("# old description"));
		assertTrue(output.contains("least_conn;"));
		assertTrue(output.contains("upstream legacy_api {"));
		assertFalse(output.contains("__NGX_RAW_BLOCK_"));
	}

	@Test
	void streamSourcesKeepTheirEstablishedOrder() {
		Stream legacy = new Stream();
		legacy.setName("log_format");
		legacy.setValue("basic '$remote_addr';");
		legacy.setSeq(1L);
		sql.insertOrUpdate(legacy);
		streams.saveBlock(null, "server", "\n    listen 9001;\n");

		Template template = new Template();
		template.setName("stream-limit");
		template.setDef("stream");
		sql.insertOrUpdate(template);
		Param automatic = new Param();
		automatic.setTemplateId(template.getId());
		automatic.setName("limit_conn_zone");
		automatic.setValue("$binary_remote_addr zone=stream_limit:10m");
		sql.insertOrUpdate(automatic);

		DenyAllow deny = new DenyAllow();
		deny.setType("deny");
		deny.setIp("192.0.2.1");
		sql.insertOrUpdate(deny);
		upstreams.saveBlock(null, "tcp_api", 1, "\n    server 10.0.0.1:9000;\n", false);

		String output = conf.buildConf(false, false).getConf();

		int legacyAt = output.indexOf("log_format basic");
		int childAt = output.indexOf("listen 9001;");
		int automaticAt = output.indexOf("limit_conn_zone");
		int denyAllowAt = output.indexOf("include conf.d/deny_stream.conf");
		int upstreamAt = output.indexOf("upstream tcp_api {");
		assertTrue(legacyAt >= 0 && legacyAt < childAt);
		assertTrue(childAt < automaticAt);
		assertTrue(automaticAt < denyAllowAt);
		assertTrue(denyAllowAt < upstreamAt);
	}

	@Test
	void streamPreviewReplacesExistingDraftWithoutChangingStoredSources() {
		Stream legacy = new Stream();
		legacy.setName("log_format");
		legacy.setValue("audit '$remote_addr';");
		legacy.setSeq(10L);
		sql.insertOrUpdate(legacy);
		Stream persistedChild = streams.saveBlock(null, "map $ssl_preread_server_name $backend",
				"\n    default api#suffix;\n    hash\\{escaped\\};\n    default ${backend};\n");
		persistedChild.setSeq(20L);
		Stream replaceMe = streams.saveBlock(null, "server", "\n    listen 9100;\n");
		replaceMe.setSeq(30L);
		Upstream upstream = upstreams.saveBlock(null, "tcp_api", 1, "\n    server 10.0.0.5:9000;\n", false);
		DenyAllow deny = new DenyAllow();
		deny.setType("deny");
		deny.setIp("192.0.2.9");
		sql.insertOrUpdate(deny);
		Template template = new Template();
		template.setName("preview-stream-limit");
		template.setDef("stream");
		sql.insertOrUpdate(template);
		Param auto = new Param();
		auto.setTemplateId(template.getId());
		auto.setName("limit_conn_zone");
		auto.setValue("$binary_remote_addr zone=preview_limit:10m");
		sql.insertOrUpdate(auto);
		Server tcp = new Server();
		tcp.setProxyType(1);
		tcp.setListen("9200");
		tcp.setEnable(true);
		tcp.setIpv6(0);
		tcp.setSsl(0);
		tcp.setProxyProtocol(0);
		tcp.setSeq(40L);
		sql.insertOrUpdate(tcp);
		streamServers.add(tcp);

		List<String> before = streamSnapshot();
		String preview = conf.previewStreamBlock(replaceMe.getId(), "server", "\n    listen 9300; # draft\n");

		assertEquals(1, count(preview, "listen 9300; # draft"));
		assertEquals(1, count(preview, "listen 9200;"));
		assertTrue(preview.contains("listen 9300; # draft"));
		assertFalse(preview.contains("listen 9100;"));
		assertTrue(preview.contains("audit '$remote_addr';"));
		assertTrue(preview.contains("default api#suffix;"));
		assertTrue(preview.contains("hash\\{escaped\\};"));
		assertTrue(preview.contains("default ${backend};"));
		assertTrue(preview.contains("limit_conn_zone"));
		assertTrue(preview.contains("include conf.d/deny_stream.conf"));
		assertTrue(preview.contains("upstream tcp_api {"));
		assertTrue(preview.contains("listen 9200;"));
		assertTrue(preview.indexOf("audit '$remote_addr';") < preview.indexOf("default api#suffix;"));
		assertTrue(preview.indexOf("default api#suffix;") < preview.indexOf("listen 9300; # draft"));
		assertTrue(preview.indexOf("listen 9300; # draft") < preview.indexOf("limit_conn_zone"));
		assertTrue(preview.indexOf("limit_conn_zone") < preview.indexOf("include conf.d/deny_stream.conf"));
		assertTrue(preview.indexOf("include conf.d/deny_stream.conf") < preview.indexOf("upstream tcp_api {"));
		assertTrue(preview.indexOf("upstream tcp_api {") < preview.indexOf("listen 9200;"));
		assertEquals(before, streamSnapshot());
	}

	@Test
	void streamPreviewInsertsUnsavedDraftAtEndAndRepeatedCallsDoNotPersist() {
		Stream legacy = new Stream();
		legacy.setName("proxy_timeout");
		legacy.setValue("5s;");
		legacy.setSeq(10L);
		sql.insertOrUpdate(legacy);
		List<String> before = streamSnapshot();

		String first = conf.previewStreamBlock(null, "server", "\n    listen 9400;\n");
		String second = conf.previewStreamBlock(null, "server", "\n    listen 9400;\n");

		assertTrue(first.indexOf("proxy_timeout 5s") < first.indexOf("listen 9400;"));
		assertEquals(first, second);
		assertEquals(before, streamSnapshot());
	}

	@Test
	void streamControllerPreviewReturnsFullContextAndRejectsInvalidDraftWithoutSaving() {
		StreamController controller = new StreamController();
		ReflectUtil.setFieldValue(controller, "confService", conf);
		ReflectUtil.setFieldValue(controller, "m", new com.cym.utils.MessageUtils() {
			@Override public String get(String key) { return key; }
		});
		List<String> before = streamSnapshot();

		JsonResult<?> result = controller.previewBlock(null, "server", "\n    listen 9500;\n");
		JsonResult<?> invalid = controller.previewBlock(null, "server", "stream { server { listen 9501; } }");

		assertTrue(result.isSuccess(), result.getMsg());
		assertInstanceOf(String.class, result.getObj());
		assertTrue(((String) result.getObj()).startsWith("stream {"));
		assertFalse(invalid.isSuccess());
		assertEquals(before, streamSnapshot());
	}

	@Test
	void streamPreviewDoesNotMutateLegacyUpstreamServersOrParameters() {
		Upstream legacy = new Upstream();
		legacy.setName("legacy_ipv6");
		legacy.setProxyType(0);
		sql.insertOrUpdate(legacy);
		UpstreamServer server = new UpstreamServer();
		server.setUpstreamId(legacy.getId());
		server.setServer("2001:db8::5");
		server.setPort(8080);
		server.setEnable(1);
		server.setStatus("none");
		sql.insertOrUpdate(server);

		Param ordinary = new Param();
		ordinary.setName("keepalive");
		ordinary.setValue("4");
		ordinary.setPosition(0);
		Template template = new Template();
		template.setName("read-only-param-template");
		sql.insertOrUpdate(template);
		Param templateEntry = new Param();
		templateEntry.setTemplateId(template.getId());
		templateEntry.setName("least_time");
		templateEntry.setValue("header");
		sql.insertOrUpdate(templateEntry);
		Param templateRef = new Param();
		templateRef.setName("");
		templateRef.setValue("");
		templateRef.setTemplateValue(template.getId());
		templateRef.setPosition(1);
		paramsByTypeId.put(legacy.getId() + "|upstream", List.of(templateRef, ordinary));

		String preview = conf.previewStreamBlock(null, "server", "\n    listen 9600;\n");

		assertTrue(preview.contains("listen 9600;"));
		assertEquals("2001:db8::5", server.getServer());
		assertEquals("keepalive", ordinary.getName());
		assertEquals("least_time", templateEntry.getName());
	}

	private static int count(String text, String needle) {
		int count = 0;
		for (int at = 0; (at = text.indexOf(needle, at)) >= 0; at += needle.length()) count++;
		return count;
	}

	private List<String> streamSnapshot() {
		return streams.findAll().stream().map(stream -> stream.getId() + "|" + stream.getName() + "|" + stream.getValue()
				+ "|" + stream.getConfigMode() + "|" + stream.getRawContent() + "|" + stream.getSeq()).toList();
	}

	/** Small in-memory SqlHelper exercising ConfService through its normal service boundary. */
	private static class MemorySqlHelper extends SqlHelper {
		private final Map<Class<?>, LinkedHashMap<String, Object>> rows = new LinkedHashMap<>();
		private long nextId = 1;

		@Override public String insertOrUpdate(Object object) {
			String id = (String) ReflectUtil.getFieldValue(object, "id");
			if (id == null) {
				id = Long.toString(nextId++);
				ReflectUtil.setFieldValue(object, "id", id);
			}
			rows.computeIfAbsent(object.getClass(), ignored -> new LinkedHashMap<>()).put(id, object);
			return id;
		}

		@Override @SuppressWarnings("unchecked") public <T> T findById(String id, Class<T> type) {
			return (T) rows.getOrDefault(type, new LinkedHashMap<>()).get(id);
		}

		@Override @SuppressWarnings("unchecked") public <T> List<T> findAll(Sort sort, Class<T> type) {
			return new ArrayList<>((List<T>) rows.getOrDefault(type, new LinkedHashMap<>()).values().stream().toList());
		}

		@Override public <T> List<T> findAll(Class<T> type) { return findAll((Sort) null, type); }

		@Override public <T> List<T> findListByQuery(ConditionWrapper conditions, Sort sort, Class<T> type) {
			return findAll(sort, type).stream().filter(row -> matches(row, conditions)).toList();
		}

		@Override public <T> T findOneByQuery(ConditionWrapper conditions, Class<T> type) {
			return findListByQuery(conditions, null, type).stream().findFirst().orElse(null);
		}

		@Override public Long findCountByQuery(ConditionWrapper conditions, Class<?> type) {
			return findListByQuery(conditions, null, type).size() * 1L;
		}

		private boolean matches(Object row, ConditionWrapper wrapper) {
			if (wrapper == null) return true;
			@SuppressWarnings("unchecked") List<Object> conditions = (List<Object>) ReflectUtil.getFieldValue(wrapper, "list");
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
