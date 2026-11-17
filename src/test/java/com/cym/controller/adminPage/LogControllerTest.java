package com.cym.controller.adminPage;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.io.UncheckedIOException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.noear.solon.annotation.Post;
import org.noear.solon.core.handle.ModelAndView;

import com.cym.ext.LogTailData;
import com.cym.model.Log;
import com.cym.sqlhelper.utils.SqlHelper;
import com.cym.utils.BLogFileTailer;
import com.cym.utils.BaseController;
import com.cym.utils.JsonResult;
import com.cym.utils.MessageUtils;

import cn.hutool.json.JSONUtil;

/** Endpoint behavior uses stored records and real files; only DB lookup and read failures are controlled.
 * Neither id nor guid has a specified maximum length or token format.
 */
class LogControllerTest {

	@TempDir Path directory;
	private final List<BLogFileTailer> readers = new ArrayList<>();

	@AfterEach
	void closeReaders() throws Exception {
		Field field = BLogFileTailer.class.getDeclaredField("sessions");
		field.setAccessible(true);
		for (BLogFileTailer reader : readers) {
			Map<?, ?> sessions = (Map<?, ?>) field.get(reader);
			for (Object session : sessions.values()) {
				var stop = session.getClass().getDeclaredMethod("stop");
				stop.setAccessible(true);
				stop.invoke(session);
			}
			sessions.clear();
		}
	}

	@Test
	void tailDataReturnsRawLinesAndSavedPathAsJson() throws Exception {
		// Arrange
		Path path = Files.writeString(directory.resolve("raw.log"), "  <img src=x onerror=alert(1)>\n\tend\n");
		LogController controller = controller(Map.of("saved-id", path.toString()), new BLogFileTailer(), "messages_en_US.properties");
		// Act
		JsonResult result = controller.tailData("saved-id", "viewer-a");
		// Assert
		assertTrue(result.isSuccess());
		LogTailData data = (LogTailData) result.getObj();
		assertEquals("ready", data.getState());
		assertEquals(500, data.getLimit());
		assertEquals(path.toString(), data.getPath());
		assertEquals(List.of("  <img src=x onerror=alert(1)>", "\tend"), data.getLines());
		var json = JSONUtil.parseObj(JSONUtil.toJsonStr(result)).getJSONObject("obj");
		assertEquals("ready", json.getStr("state"));
		assertEquals(path.toString(), json.getStr("path"));
		assertEquals(500, json.getInt("limit"));
		assertEquals(List.of("  <img src=x onerror=alert(1)>", "\tend"), json.getJSONArray("lines").toList(String.class));
	}

	@Test
	void missingFileDoesNotStartReader() throws Exception {
		Path missing = directory.resolve("missing.log");
		BLogFileTailer reader = new BLogFileTailer() {
			@Override public List<String> readLines(String guid, String path) {
				fail("Missing files must not start a tailer");
				return List.of();
			}
		};
		LogController controller = controller(Map.of("missing", missing.toString()), reader, "messages_en_US.properties");

		LogTailData data = data(controller.tailData("missing", "viewer-a"));

		assertEquals("missing", data.getState());
		assertEquals(missing.toString(), data.getPath());
		assertEquals(List.of(), data.getLines());
	}

	@Test
	void emptyFileIsReady() throws Exception {
		Path empty = Files.createFile(directory.resolve("empty.log"));
		LogController controller = controller(Map.of("empty", empty.toString()), new BLogFileTailer(), "messages_en_US.properties");

		LogTailData data = data(controller.tailData("empty", "viewer-a"));

		assertEquals("ready", data.getState());
		assertEquals(List.of(), data.getLines());
	}

	@Test
	void readerFailureReturnsUnreadableWithoutDetailsAndCanRetry() throws Exception {
		// Arrange
		Path path = Files.writeString(directory.resolve("denied.log"), "recovered\n");
		BLogFileTailer reader = new BLogFileTailer() {
			private boolean failNext = true;
			@Override public List<String> readLines(String guid, String path) {
				if (failNext) {
					failNext = false;
					throw new UncheckedIOException(new IOException("private stack trace detail"));
				}
				return super.readLines(guid, path);
			}
		};
		LogController controller = controller(Map.of("denied", path.toString()), reader, "messages_en_US.properties");
		// Act
		JsonResult result = controller.tailData("denied", "viewer-a");
		// Assert
		assertEquals("unreadable", data(result).getState());
		assertEquals(List.of(), data(result).getLines());
		assertFalse(JSONUtil.toJsonStr(result).contains("private stack trace detail"));
		assertEquals(List.of("recovered"), data(controller.tailData("denied", "viewer-a")).getLines());
	}

	@Test
	void directoryIsUnreadableWithoutStartingReader() throws Exception {
		BLogFileTailer reader = new BLogFileTailer() {
			@Override public List<String> readLines(String guid, String path) {
				throw new AssertionError("Directories must not start a tailer");
			}
		};
		LogController controller = controller(Map.of("directory", directory.toString()), reader, "messages_en_US.properties");

		LogTailData data = data(controller.tailData("directory", "viewer-a"));

		assertEquals("unreadable", data.getState());
		assertEquals(List.of(), data.getLines());
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "   ", "unknown-id" })
	void invalidIdReturnsLocalizedError(String id) throws Exception {
		LogController controller = controller(Map.of(), new BLogFileTailer(), "messages_en_US.properties");

		JsonResult result = controller.tailData(id, "viewer-a");

		assertFalse(result.isSuccess());
		assertEquals("500", result.getStatus());
		assertEquals("Invalid log file", result.getMsg());
		assertNull(result.getObj());
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "   " })
	void invalidGuidReturnsLocalizedError(String guid) throws Exception {
		Path path = Files.createFile(directory.resolve("valid.log"));
		LogController controller = controller(Map.of("valid", path.toString()), new BLogFileTailer(), "messages_en_US.properties");

		JsonResult result = controller.tailData("valid", guid);

		assertFalse(result.isSuccess());
		assertEquals("500", result.getStatus());
		assertEquals("Invalid log viewer session", result.getMsg());
		assertNull(result.getObj());
	}

	@Test
	void sameGuidCannotReadAnotherStoredPath() throws Exception {
		// Arrange
		Path first = Files.writeString(directory.resolve("first.log"), "first\n");
		Path second = Files.writeString(directory.resolve("second.log"), "secret second\n");
		LogController controller = controller(Map.of("first", first.toString(), "second", second.toString()), new BLogFileTailer(), "messages_en_US.properties");
		assertEquals(List.of("first"), data(controller.tailData("first", "viewer-a")).getLines());
		// Act
		JsonResult result = controller.tailData("second", "viewer-a");
		// Assert
		assertFalse(result.isSuccess());
		assertEquals("Invalid log viewer session", result.getMsg());
		assertNull(result.getObj());
		assertEquals(List.of("first"), data(controller.tailData("first", "viewer-a")).getLines());
		assertEquals(List.of("secret second"), data(controller.tailData("second", "viewer-b")).getLines());
	}

	@Test
	void legacyTailRouteAndEscapedHtmlRemainCompatible() throws Exception {
		Path path = Files.writeString(directory.resolve("legacy.log"), "<script>alert(1)</script>\n");
		LogController controller = controller(Map.of("legacy", path.toString()), new BLogFileTailer(), "messages_en_US.properties");

		JsonResult result = controller.tailCmd("legacy", "viewer-a");

		assertTrue(result.isSuccess());
		assertEquals("<div>&lt;script&gt;alert(1)&lt;/script&gt;</div>", result.getObj());
		ModelAndView view = controller.tail(new ModelAndView(), "legacy", "http");
		assertEquals("/adminPage/log/tail.html", view.view());
		assertEquals("legacy", view.model().get("id"));
	}

	@Test
	void tailDataIsPostAndAcceptsOnlyStoredIdAndViewerToken() throws Exception {
		assertNotNull(LogController.class.getMethod("tailData", String.class, String.class).getAnnotation(Post.class));
	}

	@ParameterizedTest
	@ValueSource(strings = { "messages.properties", "messages_zh_TW.properties", "messages_en_US.properties" })
	void errorsAndStatesHaveTranslationsInEveryLanguage(String file) throws Exception {
		Properties properties = load(file);
		LogController controller = controller(Map.of(), new BLogFileTailer(), file);

		JsonResult result = controller.tailData("unknown", "viewer-a");

		assertEquals(properties.getProperty("logStr.invalidId"), result.getMsg());
		assertEquals(properties.getProperty("logStr.invalidGuid"), controller.tailData("unknown", " ").getMsg());
		for (String key : List.of("logStr.invalidId", "logStr.invalidGuid", "logStr.ready", "logStr.missing", "logStr.unreadable")) {
			assertNotNull(properties.getProperty(key), file + ": " + key);
			assertFalse(properties.getProperty(key).isBlank(), file + ": " + key);
		}
	}

	private LogTailData data(JsonResult result) {
		assertTrue(result.isSuccess());
		return assertInstanceOf(LogTailData.class, result.getObj());
	}

	private LogController controller(Map<String, String> paths, BLogFileTailer reader, String language) throws Exception {
		LogController controller = new LogController();
		SqlHelper db = new SqlHelper() {
			@Override public <T> T findById(String id, Class<T> type) {
				String path = paths.get(id);
				if (path == null) return null;
				Log log = new Log();
				log.setId(id);
				log.setPath(path);
				return type.cast(log);
			}
		};
		Field dbField = BaseController.class.getDeclaredField("sqlHelper");
		dbField.setAccessible(true);
		dbField.set(controller, db);
		Properties messages = load(language);
		MessageUtils translations = new MessageUtils() {
			@Override public String get(String key) { return messages.getProperty(key); }
		};
		Field messageField = BaseController.class.getDeclaredField("m");
		messageField.setAccessible(true);
		messageField.set(controller, translations);
		controller.bLogFileTailer = reader;
		readers.add(reader);
		return controller;
	}

	private Properties load(String file) throws Exception {
		Properties properties = new Properties();
		try (InputStream stream = getClass().getClassLoader().getResourceAsStream(file)) {
			properties.load(stream);
		}
		return properties;
	}
}
