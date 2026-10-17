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
import com.cym.model.Template;
import com.cym.model.Upstream;
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

	@BeforeEach
	void setUp() {
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
			@Override public List<com.cym.model.Server> getListByProxyType(String[] types) { return List.of(); }
		});
		ReflectUtil.setFieldValue(conf, "paramService", new ParamService() {
			@Override public List<com.cym.model.Param> getListByTypeId(String id, String type) { return List.of(); }
		});
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

	private static int count(String text, String needle) {
		int count = 0;
		for (int at = 0; (at = text.indexOf(needle, at)) >= 0; at += needle.length()) count++;
		return count;
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
