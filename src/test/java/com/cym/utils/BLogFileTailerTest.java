package com.cym.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BLogFileTailerTest {

	@TempDir
	Path directory;
	private final List<TrackedTailer> tailers = new CopyOnWriteArrayList<>();

	@AfterEach
	void stopTailers() {
		tailers.forEach(TrackedTailer::closeForTest);
	}

	@Test
	void initialReadIsLast50() throws Exception {
		Path file = write("initial.log", IntStream.rangeClosed(1, 75).mapToObj(i -> "line-" + i).toList());
		BLogFileTailer reader = reader(20, TimeUnit.SECONDS);
		List<String> snapshot = reader.readLines("viewer-a", file.toString());
		assertEquals(50, snapshot.size());
		assertEquals("line-26", snapshot.get(0));
		assertEquals("line-75", snapshot.get(49));
		assertThrows(UnsupportedOperationException.class, () -> snapshot.add("changed"));
	}

	@Test
	void initialReadWithoutTrailingLineFeedIsLast50() throws Exception {
		Path file = directory.resolve("no-trailing-lf.log");
		Files.writeString(file, String.join("\n", IntStream.rangeClosed(1, 75)
			.mapToObj(i -> "line-" + i).toList()), StandardCharsets.UTF_8);
		List<String> snapshot = reader(20, TimeUnit.SECONDS).readLines("viewer-a", file.toString());
		assertEquals(50, snapshot.size());
		assertEquals("line-26", snapshot.get(0));
		assertEquals("line-75", snapshot.get(49));
	}

	@Test
	void initialReadPreservesShortFilesAndBlankLines() throws Exception {
		BLogFileTailer reader = reader(20, TimeUnit.SECONDS);
		Path terminated = write("short.log", List.of("first", "", "last", ""));
		assertEquals(List.of("first", "", "last", ""), reader.readLines("viewer-a", terminated.toString()));
		Path unterminated = directory.resolve("short-unterminated.log");
		Files.writeString(unterminated, "first\n\nlast", StandardCharsets.UTF_8);
		assertEquals(List.of("first", "", "last"), reader.readLines("viewer-b", unterminated.toString()));
	}

	@Test
	void initialReadPreservesUtf8AndCrLfAcrossBlocks() throws Exception {
		Path file = directory.resolve("utf8-crlf.log");
		List<String> lines = IntStream.rangeClosed(1, 75).mapToObj(i -> i + "-中文🙂".repeat(40)).toList();
		Files.writeString(file, String.join("\r\n", lines) + "\r\n", StandardCharsets.UTF_8);
		List<String> snapshot = reader(20, TimeUnit.SECONDS).readLines("viewer-a", file.toString());
		assertEquals(50, snapshot.size());
		assertEquals("26" + "-中文🙂".repeat(40), snapshot.get(0));
		assertEquals("75" + "-中文🙂".repeat(40), snapshot.get(49));
		Path blanks = directory.resolve("crlf-blanks.log");
		Files.writeString(blanks, "中文\r\n\r\n🙂\r\n", StandardCharsets.UTF_8);
		assertEquals(List.of("中文", "", "🙂"), reader(20, TimeUnit.SECONDS).readLines("viewer-b", blanks.toString()));
	}

	@Test
	void appendDuringInitialDeliveryIsReadOnce() throws Exception {
		Path file = write("handoff.log", List.of("initial"));
		BLogFileTailer reader = new BLogFileTailer(20, TimeUnit.SECONDS, (source, handler) -> {
			TrackedTailer tailer = new TrackedTailer(source, line -> {
				if (line.equals("initial")) {
					try {
						Files.writeString(file, "追加🙂\r\n\r\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
					} catch (java.io.IOException e) {
						throw new java.io.UncheckedIOException(e);
					}
				}
				handler.accept(line);
			});
			tailers.add(tailer);
			return tailer;
		});
		reader.readLines("viewer-a", file.toString());
		await(() -> reader.readLines("viewer-a", file.toString()).size() == 3);
		assertEquals(List.of("initial", "追加🙂", ""), reader.readLines("viewer-a", file.toString()));
	}

	@Test
	void failedStartStopsReaderAndAllowsRetry() throws Exception {
		Path file = directory.resolve("not-yet-present.log");
		BLogFileTailer reader = reader(20, TimeUnit.SECONDS);
		assertThrows(java.io.UncheckedIOException.class, () -> reader.readLines("viewer-a", file.toString()));
		assertEquals(1, tailers.get(0).stops);
		Files.writeString(file, "available\n", StandardCharsets.UTF_8);
		assertEquals(List.of("available"), reader.readLines("viewer-a", file.toString()));
		assertEquals(2, tailers.size());
	}

	@Test
	void legacyEscapesMarkupAndKeepsLineOrder() throws Exception {
		Path file = write("legacy.log", List.of("<b>  literal & text</b>", "second > first", "\"quoted\" 'value'"));
		BLogFileTailer reader = reader(20, TimeUnit.SECONDS);
		assertEquals("<div>&lt;b&gt;  literal &amp; text&lt;/b&gt;</div><div>second &gt; first</div>"
			+ "<div>&quot;quoted&quot; &#039;value&#039;</div>", reader.run("viewer-a", file.toString()));
		assertEquals(List.of("<b>  literal & text</b>", "second > first", "\"quoted\" 'value'"),
			reader.readLines("viewer-a", file.toString()));
	}

	@Test
	void bufferKeepsLast500() throws Exception {
		Path file = write("buffer.log", List.of("<b>  literal & text</b>"));
		BLogFileTailer reader = reader(20, TimeUnit.SECONDS);
		List<String> original = reader.readLines("viewer-a", file.toString());
		assertEquals(List.of("<b>  literal & text</b>"), original);
		Files.write(file, IntStream.rangeClosed(1, 501).mapToObj(i -> "append-" + i).toList(),
			StandardCharsets.UTF_8, StandardOpenOption.APPEND);
		await(() -> reader.readLines("viewer-a", file.toString()).contains("append-501"));
		List<String> snapshot = reader.readLines("viewer-a", file.toString());
		assertEquals(500, snapshot.size());
		assertEquals("append-2", snapshot.get(0));
		assertEquals("append-501", snapshot.get(499));
		assertEquals(List.of("<b>  literal & text</b>"), original);
	}

	@Test
	void sameGuidDifferentPathRejected() throws Exception {
		Path first = write("first.log", List.of("first"));
		Path second = write("second.log", List.of("second"));
		BLogFileTailer reader = reader(20, TimeUnit.SECONDS);
		assertEquals(List.of("first"), reader.readLines("viewer-a", first.toString()));
		assertThrows(IllegalArgumentException.class, () -> reader.readLines("viewer-a", second.toString()));
		assertEquals(List.of("first"), reader.readLines("viewer-a", directory.resolve("./first.log").toString()));
		assertEquals(List.of("second"), reader.readLines("viewer-b", second.toString()));
		assertEquals(2, tailers.size());
	}

	@Test
	void concurrentFirstReadStartsOneTailer() throws Exception {
		Path file = write("concurrent.log", List.of("shared"));
		BLogFileTailer reader = reader(20, TimeUnit.SECONDS);
		ExecutorService workers = Executors.newFixedThreadPool(12);
		CountDownLatch ready = new CountDownLatch(12);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<List<String>>> results = new ArrayList<>();
			for (int i = 0; i < 12; i++) {
				results.add(workers.submit(() -> {
					ready.countDown();
					assertTrue(start.await(3, TimeUnit.SECONDS));
					return reader.readLines("viewer-a", file.toString());
				}));
			}
			assertTrue(ready.await(3, TimeUnit.SECONDS));
			start.countDown();
			for (Future<List<String>> result : results) {
				assertEquals(List.of("shared"), result.get(3, TimeUnit.SECONDS));
			}
			assertEquals(1, tailers.size());
			assertEquals(1, tailers.get(0).starts);
		} finally {
			start.countDown();
			workers.shutdownNow();
			assertTrue(workers.awaitTermination(3, TimeUnit.SECONDS));
		}
	}

	@Test
	void idleExpiryStopsTailerAndDropsBuffer() throws Exception {
		Path file = write("expired.log", List.of("old"));
		Path replacement = write("replacement.log", List.of("new"));
		BLogFileTailer reader = reader(150, TimeUnit.MILLISECONDS);
		List<String> old = reader.readLines("viewer-a", file.toString());
		TrackedTailer expired = tailers.get(0);
		assertTrue(expired.stopped.await(3, TimeUnit.SECONDS));
		assertEquals(1, expired.stops);
		assertEquals(List.of("new"), reader.readLines("viewer-a", replacement.toString()));
		expired.handler.accept("late callback from expired file");
		assertEquals(List.of("new"), reader.readLines("viewer-a", replacement.toString()));
		assertEquals(List.of("old"), old);
		assertEquals(2, tailers.size());
	}

	@Test
	void emptyFileAcceptsAppend() throws Exception {
		Path file = write("empty.log", List.of());
		BLogFileTailer reader = reader(20, TimeUnit.SECONDS);
		assertEquals(List.of(), reader.readLines("viewer-a", file.toString()));
		Files.writeString(file, "first appended line\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
		await(() -> reader.readLines("viewer-a", file.toString()).equals(List.of("first appended line")));
	}

	private Path write(String name, List<String> lines) throws Exception {
		return Files.write(directory.resolve(name), lines, StandardCharsets.UTF_8);
	}

	private BLogFileTailer reader(long expiry, TimeUnit unit) {
		return new BLogFileTailer(expiry, unit, (file, handler) -> {
			TrackedTailer tailer = new TrackedTailer(file, handler);
			tailers.add(tailer);
			return tailer;
		});
	}

	private static void await(BooleanSupplier condition) throws Exception {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
		while (!condition.getAsBoolean()) {
			assertTrue(System.nanoTime() < deadline, "log snapshot did not update within 3 seconds");
			Thread.sleep(10);
		}
	}

	private static class TrackedTailer extends BLogFileTailer.FileTailer {
		private final Consumer<String> handler;
		private final CountDownLatch stopped = new CountDownLatch(1);
		private volatile int starts;
		private volatile int stops;

		TrackedTailer(File file, Consumer<String> handler) {
			super(file, handler, 10);
			this.handler = handler;
		}

		@Override
		public void start() {
			starts++;
			super.start();
		}

		@Override
		public void stop() {
			stops++;
			super.stop();
			stopped.countDown();
		}

		void closeForTest() {
			super.stop();
		}
	}
}
