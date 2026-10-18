package com.cym.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.cym.ext.AsycPack;
import com.cym.model.Stream;
import com.cym.model.Upstream;
import com.cym.sqlhelper.bean.Sort;
import com.cym.sqlhelper.utils.ConditionWrapper;
import com.cym.sqlhelper.utils.SqlHelper;

import cn.hutool.core.util.ReflectUtil;
import cn.hutool.json.JSONUtil;

class BlockEditorTransferTest {
	@Test
	void blockModelsKeepRawTextThroughAsycPackJsonBackupAndRestore() {
		MemorySqlHelper sql = new MemorySqlHelper();
		ConfService conf = new ConfService();
		ReflectUtil.setFieldValue(conf, "sqlHelper", sql);

		String upstreamRaw = "\r\n    hash $remote_addr; # keep {comment}\r\n    server backend#one:8080;\r\n";
		Upstream upstream = new Upstream();
		upstream.setId("block-upstream");
		upstream.setName("api");
		upstream.setProxyType(0);
		upstream.setConfigMode("block");
		upstream.setRawContent(upstreamRaw);
		sql.insertOrUpdate(upstream);

		String streamRaw = "\n    listen 9001; # stream body\n    proxy_pass $backend;\n";
		Stream stream = new Stream();
		stream.setId("block-stream");
		stream.setName("server");
		stream.setConfigMode("block");
		stream.setRawContent(streamRaw);
		sql.insertOrUpdate(stream);

		AsycPack backup = conf.getAsycPack(new String[] { "upstream", "stream" });
		AsycPack restored = JSONUtil.toBean(JSONUtil.toJsonStr(backup), AsycPack.class);
		conf.setAsycPack(restored);

		Upstream restoredUpstream = sql.findById("block-upstream", Upstream.class);
		Stream restoredStream = sql.findById("block-stream", Stream.class);
		assertEquals("block", restoredUpstream.getConfigMode());
		assertEquals(upstreamRaw, restoredUpstream.getRawContent());
		assertEquals("block", restoredStream.getConfigMode());
		assertEquals(streamRaw, restoredStream.getRawContent());
	}

	@Test
	void oldBackupWithoutBlockFieldsRestoresAsLegacyModels() {
		MemorySqlHelper sql = new MemorySqlHelper();
		ConfService conf = new ConfService();
		ReflectUtil.setFieldValue(conf, "sqlHelper", sql);
		String oldBackup = """
				{"upstreamList":[{"id":"old-upstream","name":"old_api","proxyType":0}],
				 "upstreamServerList":[],
				 "streamList":[{"id":"old-stream","name":"log_format","value":"basic '$remote_addr';"}]}
				""";

		conf.setAsycPack(JSONUtil.toBean(oldBackup, AsycPack.class));

		Upstream legacyUpstream = sql.findById("old-upstream", Upstream.class);
		Stream legacyStream = sql.findById("old-stream", Stream.class);
		assertEquals("legacy", legacyUpstream.getConfigMode());
		assertNull(legacyUpstream.getRawContent());
		assertEquals("legacy", legacyStream.getConfigMode());
		assertNull(legacyStream.getRawContent());
	}

	/** In-memory SqlHelper for the same list/delete/insert boundary used by AsycPack restore. */
	private static final class MemorySqlHelper extends SqlHelper {
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

		@Override public void deleteByQuery(ConditionWrapper conditions, Class<?> type) {
			rows.computeIfAbsent(type, ignored -> new LinkedHashMap<>()).clear();
		}

		@Override public <T> void insertAll(List<T> objects) {
			if (objects != null) objects.forEach(this::insertOrUpdate);
		}

		@Override @SuppressWarnings("unchecked") public <T> T findById(String id, Class<T> type) {
			return (T) rows.getOrDefault(type, new LinkedHashMap<>()).get(id);
		}

		@Override @SuppressWarnings("unchecked") public <T> List<T> findAll(Class<T> type) {
			return new ArrayList<>((List<T>) rows.getOrDefault(type, new LinkedHashMap<>()).values().stream().toList());
		}

		@Override public <T> List<T> findAll(Sort sort, Class<T> type) { return findAll(type); }
	}
}
