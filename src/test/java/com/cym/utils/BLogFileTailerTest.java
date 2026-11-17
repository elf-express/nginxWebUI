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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cym.utils.BLogFileTailer.LineUpdate;

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
				if (line.line().equals("initial")) {
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
		expired.handler.accept(new LineUpdate("late callback from expired file", false));
		assertEquals(List.of("new"), reader.readLines("viewer-a", replacement.toString()));
		assertEquals(List.of("old"), old);
		assertEquals(2, tailers.size());
	}

	@Test
	void unterminatedLineGrowsWithoutChangingOldSnapshot() throws Exception {
		Path file = directory.resolve("fragment.log");
		Files.writeString(file, "prefix", StandardCharsets.UTF_8);
		BLogFileTailer reader = reader(20, TimeUnit.SECONDS);
		List<String> old = reader.readLines("viewer-a", file.toString());
		assertEquals(List.of("prefix"), old);
		Files.writeString(file, " suffix\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
		await(() -> tailers.get(0).deliveries.get() >= 2);
		assertEquals(List.of("prefix suffix"), reader.readLines("viewer-a", file.toString()));
		assertEquals(List.of("prefix"), old);
	}

	@Test
	void splitUtf8CharacterIsReconciledAfterIntermediateRead() throws Exception {
		Path file = write("split-utf8.log", List.of());
		BLogFileTailer reader = reader(20, TimeUnit.SECONDS);
		reader.readLines("viewer-a", file.toString());
		Files.write(file, new byte[] { 'p', 'r', 'e', 'f', 'i', 'x', ' ', (byte) 0xe4 }, StandardOpenOption.APPEND);
		await(() -> tailers.get(0).deliveries.get() >= 1);
		List<String> intermediate = reader.readLines("viewer-a", file.toString());
		List<String> preserved = List.copyOf(intermediate);
		Files.write(file, new byte[] { (byte) 0xb8, (byte) 0xad, '\n' }, StandardOpenOption.APPEND);
		await(() -> tailers.get(0).deliveries.get() >= 2);
		assertEquals(List.of("prefix 中"), reader.readLines("viewer-a", file.toString()));
		assertEquals(preserved, intermediate);
	}

	@Test
	void splitCrLfDoesNotInventBlankLines() throws Exception {
		Path file = directory.resolve("split-crlf.log");
		Files.writeString(file, "first\r", StandardCharsets.UTF_8);
		BLogFileTailer reader = reader(20, TimeUnit.SECONDS);
		List<String> old = reader.readLines("viewer-a", file.toString());
		Files.writeString(file, "\nsecond\r", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
		await(() -> tailers.get(0).deliveries.get() >= 2);
		assertEquals(List.of("first", "second"), reader.readLines("viewer-a", file.toString()));
		Files.writeString(file, "\nthird\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
		await(() -> reader.readLines("viewer-a", file.toString()).contains("third"));
		assertEquals(List.of("first", "second", "third"), reader.readLines("viewer-a", file.toString()));
		assertEquals(List.of("first"), old);
	}

	@Test
	void completingFragmentRetains500LineLimit() throws Exception {
		Path file = write("fragment-cap.log", List.of());
		BLogFileTailer reader = reader(20, TimeUnit.SECONDS);
		reader.readLines("viewer-a", file.toString());
		Files.writeString(file, String.join("\n", IntStream.rangeClosed(1, 500)
			.mapToObj(i -> "line-" + i).toList()), StandardCharsets.UTF_8, StandardOpenOption.APPEND);
		await(() -> reader.readLines("viewer-a", file.toString()).size() == 500);
		List<String> old = reader.readLines("viewer-a", file.toString());
		Files.writeString(file, " suffix\nline-501\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
		await(() -> reader.readLines("viewer-a", file.toString()).contains("line-501"));
		List<String> snapshot = reader.readLines("viewer-a", file.toString());
		assertEquals(500, snapshot.size());
		assertEquals("line-2", snapshot.get(0));
		assertEquals("line-500 suffix", snapshot.get(498));
		assertEquals("line-501", snapshot.get(499));
		assertEquals("line-500", old.get(499));
	}

	@Test
	void backgroundReadErrorClearsSessionAndRetries() throws Exception {
		Path file = write("background-error.log", List.of("old"));
		BLogFileTailer reader = reader(20, TimeUnit.SECONDS);
		List<String> old = reader.readLines("viewer-a", file.toString());
		TrackedTailer failed = tailers.get(0);
		java.lang.reflect.Field cursorField = BLogFileTailer.FileTailer.class.getDeclaredField("cursor");
		cursorField.setAccessible(true);
		((java.io.RandomAccessFile) cursorField.get(failed)).close();
		assertTrue(failed.stopped.await(3, TimeUnit.SECONDS), "real closed-cursor read must fail in background");
		assertThrows(java.io.UncheckedIOException.class, () -> reader.readLines("viewer-a", file.toString()));
		Files.writeString(file, "replacement\n", StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
		assertEquals(List.of("replacement"), reader.readLines("viewer-a", file.toString()));
		failed.handler.accept(new LineUpdate("late callback from failed reader", false));
		assertEquals(List.of("replacement"), reader.readLines("viewer-a", file.toString()));
		assertEquals(List.of("old"), old);
		assertEquals(2, tailers.size());
		assertEquals(1, failed.stops);
	}

	@Test
	void sameSizeReplacementReopensOnlyOneReaderAndRejectsOldCallbacks() throws Exception {
		assertSameSizeReplacement(false);
	}

	@Test
	void sameSizeReplacementWithoutFileKeysOrDistinctCreationTimesRecovers() throws Exception {
		assertSameSizeReplacement(true);
	}

	private void assertSameSizeReplacement(boolean keyless) throws Exception {
		// Equal head/tail samples cannot detect this middle-only change on a keyless provider.
		String padding = keyless ? "same".repeat(3000) : "";
		List<String> originalLines = keyless ? List.of(padding, "old", padding) : List.of("old");
		List<String> replacementLines = keyless ? List.of(padding, "new", padding) : List.of("new");
		List<String> appendedLines = new ArrayList<>(replacementLines);
		appendedLines.add("appended");
		Path file = Files.writeString(directory.resolve("rotated.log"), String.join("\n", originalLines) + "\n");
		BLogFileTailer reader = new BLogFileTailer(20, TimeUnit.SECONDS, (source, handler) -> {
			TrackedTailer tailer = new TrackedTailer(source, handler) {
				@Override java.nio.file.attribute.BasicFileAttributes readAttributes() throws java.io.IOException {
					var actual = super.readAttributes();
					if (!keyless) return actual;
					return (java.nio.file.attribute.BasicFileAttributes) java.lang.reflect.Proxy.newProxyInstance(
						getClass().getClassLoader(), new Class<?>[] { java.nio.file.attribute.BasicFileAttributes.class },
						(proxy, method, args) -> switch (method.getName()) {
							case "fileKey" -> null;
							case "creationTime" -> java.nio.file.attribute.FileTime.fromMillis(0);
							default -> method.invoke(actual, args);
						});
				}
			};
			tailers.add(tailer);
			return tailer;
		});
		List<String> old = reader.readLines("viewer-a", file.toString());
		TrackedTailer previous = tailers.get(0);
		long originalSize = Files.size(file);
		rotateOpenFile(previous, file, String.join("\n", replacementLines) + "\n");
		assertEquals(originalSize, Files.size(file), "replacement deliberately has the same size");
		assertEquals(replacementLines, reader.readLines("viewer-a", file.toString()));
		Files.writeString(file, "appended\n", StandardOpenOption.APPEND);
		await(() -> reader.readLines("viewer-a", file.toString()).contains("appended"));
		assertEquals(appendedLines, reader.readLines("viewer-a", file.toString()));
		assertEquals(1, previous.stops);
		assertEquals(2, tailers.size());
		previous.handler.accept(new LineUpdate("late old generation", false));
		assertEquals(appendedLines, reader.readLines("viewer-a", file.toString()));
		assertEquals(originalLines, old);
		Path other = write("other-generation.log", List.of("other"));
		assertThrows(IllegalArgumentException.class, () -> reader.readLines("viewer-a", other.toString()));
	}

	// Windows RAF denies rename while open. Recreate exactly the POSIX state:
	// the cursor still addresses the old inode at its old offset, P addresses a new file.
	// This is a deterministic I/O seam, not a skip or a claim that Windows rotated it.
	private void rotateOpenFile(TrackedTailer tailer, Path file, String replacement) throws Exception {
		synchronized (tailer) {
			var field = BLogFileTailer.FileTailer.class.getDeclaredField("cursor");
			field.setAccessible(true);
			var cursor = (java.io.RandomAccessFile) field.get(tailer);
			long position = cursor.getFilePointer();
			cursor.close();
			Path rotated = file.resolveSibling(file.getFileName() + ".1");
			Files.move(file, rotated);
			Files.writeString(file, replacement);
			var oldCursor = new java.io.RandomAccessFile(rotated.toFile(), "r");
			oldCursor.seek(position);
			field.set(tailer, oldCursor);
		}
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
		private final Consumer<LineUpdate> handler;
		private final CountDownLatch stopped = new CountDownLatch(1);
		private volatile int starts;
		private volatile int stops;
		private final AtomicInteger deliveries;

		TrackedTailer(File file, Consumer<LineUpdate> handler) {
			this(file, handler, new AtomicInteger());
		}

		private TrackedTailer(File file, Consumer<LineUpdate> handler, AtomicInteger deliveries) {
			super(file, line -> {
				handler.accept(line);
				deliveries.incrementAndGet();
			}, 10);
			this.handler = handler;
			this.deliveries = deliveries;
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
