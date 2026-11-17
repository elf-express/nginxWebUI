package com.cym.utils;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import org.noear.solon.annotation.Component;

import cn.hutool.http.HtmlUtil;
import net.jodah.expiringmap.ExpirationPolicy;
import net.jodah.expiringmap.ExpiringMap;

@Component
public class BLogFileTailer {
	private final Map<String, TailSession> sessions;
	private final BiFunction<File, Consumer<LineUpdate>, TailerHandle> tailerFactory;

	public BLogFileTailer() {
		this(20, TimeUnit.SECONDS, (file, handler) -> new FileTailer(file, handler, 1000));
	}

	BLogFileTailer(long expiry, TimeUnit unit, BiFunction<File, Consumer<LineUpdate>, TailerHandle> tailerFactory) {
		this.tailerFactory = tailerFactory;
		this.sessions = ExpiringMap.builder()
			.expiration(expiry, unit)
			.expirationPolicy(ExpirationPolicy.ACCESSED)
			.<String, TailSession>expirationListener((guid, session) -> session.stop())
			.build();
	}

	public List<String> readLines(String guid, String path) {
		File file;
		try {
			file = new File(path).getCanonicalFile();
		} catch (IOException e) {
			throw new IllegalArgumentException("Cannot resolve log path", e);
		}
		// Bind the path and publish one started Tailer atomically for this viewer.
		synchronized (sessions) {
			TailSession session = sessions.get(guid);
			if (session != null && !session.file.equals(file)) {
				throw new IllegalArgumentException("Viewer is already bound to a different log path");
			}
			if (session != null && !session.stopped) {
				try {
					session.tailer.checkFailure();
				} catch (RuntimeException e) {
					sessions.remove(guid, session);
					session.discardFailed();
					throw e;
				}
				try {
					if (!session.tailer.isCurrentFile()) {
						session.stop();
					}
				} catch (RuntimeException e) {
					session.stop();
					throw e;
				}
			}
			if (session != null && session.stopped) {
				session = null;
			}
			if (session == null) {
				session = new TailSession(file);
				session.tailer = tailerFactory.apply(file, session::append);
				try {
					session.tailer.start();
				} catch (RuntimeException e) {
					session.stop();
					throw e;
				}
				sessions.put(guid, session);
			}
			try {
				session.tailer.checkFailure();
			} catch (RuntimeException e) {
				sessions.remove(guid, session);
				session.discardFailed();
				throw e;
			}
			return session.snapshot();
		}
	}

	/** Release a known unusable file, retaining its GUID/path binding until idle expiry. */
	public void invalidate(String guid, String path) {
		if (path == null) return;
		File file;
		try {
			file = new File(path).getCanonicalFile();
		} catch (IOException e) {
			throw new IllegalArgumentException("Cannot resolve log path", e);
		}
		synchronized (sessions) {
			TailSession session = sessions.get(guid);
			if (session != null && session.file.equals(file)) {
				session.stop();
			}
		}
	}

	public String run(String guid, String path) {
		StringBuilder html = new StringBuilder();
		for (String line : readLines(guid, path)) {
			html.append("<div>").append(HtmlUtil.escape(line)).append("</div>");
		}
		return html.toString();
	}

	private static final class TailSession {
		private final File file;
		private final ArrayDeque<String> lines = new ArrayDeque<>();
		private TailerHandle tailer;
		private volatile boolean stopped;

		private TailSession(File file) {
			this.file = file;
		}

		private synchronized void append(LineUpdate update) {
			if (!stopped) {
				if (update.replaceLast() && !lines.isEmpty()) {
					lines.removeLast();
				}
				lines.addLast(update.line());
				while (lines.size() > 500) {
					lines.removeFirst();
				}
			}
		}

		private synchronized List<String> snapshot() {
			return List.copyOf(lines);
		}

		private synchronized void discardFailed() {
			// checkFailure only reports a failure after the reader has closed itself.
			stopped = true;
			lines.clear();
		}

		private synchronized void stop() {
			if (!stopped) {
				stopped = true;
				try {
					tailer.stop();
				} finally {
					lines.clear();
				}
			}
		}
	}

	interface TailerHandle {
		void start();
		void stop();
		/** Report background failure only after closing this reader's resources. */
		void checkFailure();
		boolean isCurrentFile();
	}

	record LineUpdate(String line, boolean replaceLast) { }

	/** One cursor covers initial history and appends, so the handoff cannot skip bytes. */
	static class FileTailer implements TailerHandle {
		private final File file;
		private final Consumer<LineUpdate> handler;
		private final long periodMillis;
		private RandomAccessFile cursor;
		private ScheduledExecutorService executor;
		private boolean stopped;
		private long unfinishedStart = -1;
		private boolean skipLineFeed;
		private volatile RuntimeException failure;
		private BasicFileAttributes openedAttributes;

		FileTailer(File file, Consumer<LineUpdate> handler, long periodMillis) {
			this.file = file;
			this.handler = handler;
			this.periodMillis = periodMillis;
		}

		@Override
		public synchronized void start() {
			try {
				openedAttributes = readAttributes();
				cursor = new RandomAccessFile(file, "r");
				if (!isCurrentFile()) {
					throw new IOException("Log changed while opening");
				}
				seekInitialStart();
				readAvailable(50).forEach(handler);
				executor = Executors.newSingleThreadScheduledExecutor(task -> {
					Thread thread = new Thread(task, "nginx-log-tailer");
					thread.setDaemon(true);
					return thread;
				});
				executor.scheduleWithFixedDelay(() -> {
					try {
						readAvailable(500).forEach(handler);
					} catch (RuntimeException e) {
						synchronized (this) {
							try {
								stop();
							} finally {
								failure = e;
							}
						}
						throw e;
					}
				}, 0, periodMillis, TimeUnit.MILLISECONDS);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}

		BasicFileAttributes readAttributes() throws IOException {
			return Files.readAttributes(file.toPath(), BasicFileAttributes.class);
		}

		@Override
		public synchronized boolean isCurrentFile() {
			try {
				BasicFileAttributes current = readAttributes();
				Object openedKey = openedAttributes.fileKey();
				Object currentKey = current.fileKey();
				if (openedKey != null && currentKey != null) {
					return openedKey.equals(currentKey);
				}
				if (!openedAttributes.creationTime().equals(current.creationTime())) {
					return false;
				}
				// Providers may omit file keys (and have coarse creation timestamps).
				// Compare the open file with a fresh pathname handle in bounded blocks,
				// including the middle of large files; equal path/size alone proves nothing.
				// Positional reads preserve the tail/partial-line cursor. This slower path
				// is used only when the provider cannot supply an identity key.
				try (FileChannel currentFile = FileChannel.open(file.toPath(), StandardOpenOption.READ)) {
					long length = cursor.length();
					if (length != currentFile.size()) return false;
					ByteBuffer oldBytes = ByteBuffer.allocate(8192);
					ByteBuffer newBytes = ByteBuffer.allocate(8192);
					for (long position = 0; position < length;) {
						int count = (int) Math.min(oldBytes.capacity(), length - position);
						oldBytes.clear().limit(count);
						newBytes.clear().limit(count);
						while (oldBytes.hasRemaining()) {
							if (cursor.getChannel().read(oldBytes, position + oldBytes.position()) < 0) return false;
						}
						while (newBytes.hasRemaining()) {
							if (currentFile.read(newBytes, position + newBytes.position()) < 0) return false;
						}
						if (!oldBytes.flip().equals(newBytes.flip())) return false;
						position += count;
					}
					return cursor.length() == currentFile.size();
				}
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}

		private void seekInitialStart() throws IOException {
			long length = cursor.length();
			long end = length;
			int separators = 0;
			int next = -1;
			byte[] block = new byte[8192];
			while (end > 0) {
				long start = Math.max(0, end - block.length);
				int size = (int) (end - start);
				cursor.seek(start);
				cursor.readFully(block, 0, size);
				for (int i = size - 1; i >= 0; i--) {
					int value = block[i] & 0xff;
					if ((value == '\n' || value == '\r') && !(value == '\r' && next == '\n')
						&& start + i != length - 1 && ++separators == 50) {
						cursor.seek(start + i + 1);
						return;
					}
					next = value;
				}
				end = start;
			}
			cursor.seek(0);
		}

		private synchronized List<LineUpdate> readAvailable(int limit) {
			if (stopped) {
				return List.of();
			}
			try {
				long length = cursor.length();
				if (length < cursor.getFilePointer()) {
					cursor.seek(0);
					unfinishedStart = -1;
					skipLineFeed = false;
				} else if (length == cursor.getFilePointer()) {
					return List.of();
				}
				// Re-read the whole exposed fragment, retaining undecoded UTF-8 bytes in the file.
				boolean replaceLast = unfinishedStart >= 0;
				if (replaceLast) {
					cursor.seek(unfinishedStart);
					unfinishedStart = -1;
				}
				ArrayDeque<LineUpdate> batch = new ArrayDeque<>();
				while (cursor.getFilePointer() < length) {
					if (skipLineFeed) {
						skipLineFeed = false;
						long position = cursor.getFilePointer();
						if (cursor.read() == '\n') {
							continue;
						}
						cursor.seek(position);
					}
					long start = cursor.getFilePointer();
					String line = cursor.readLine();
					if (line == null) {
						break;
					}
					long end = cursor.getFilePointer();
					cursor.seek(end - 1);
					int last = cursor.read();
					skipLineFeed = last == '\r';
					if (last != '\r' && last != '\n') {
						unfinishedStart = start;
					}
					batch.addLast(new LineUpdate(new String(line.getBytes(StandardCharsets.ISO_8859_1),
						StandardCharsets.UTF_8), replaceLast));
					replaceLast = false;
					if (batch.size() > limit) {
						batch.removeFirst();
					}
				}
				return List.copyOf(batch);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}

		@Override
		public synchronized void checkFailure() {
			if (failure != null) {
				throw failure;
			}
		}

		@Override
		public synchronized void stop() {
			if (!stopped) {
				stopped = true;
				unfinishedStart = -1;
				skipLineFeed = false;
				if (executor != null) {
					executor.shutdownNow();
				}
				if (cursor != null) {
					try {
						cursor.close();
					} catch (IOException e) {
						throw new UncheckedIOException(e);
					}
				}
			}
		}
	}

}
