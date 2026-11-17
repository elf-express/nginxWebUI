package com.cym.ext;

import java.util.List;

/** Raw log snapshot for the log viewer. Lines are never rendered as HTML here. */
public class LogTailData {
	private final String state;
	private final String path;
	private final List<String> lines;
	private final int limit = 500;

	public LogTailData(String state, String path, List<String> lines) {
		this.state = state;
		this.path = path;
		this.lines = List.copyOf(lines);
	}

	public String getState() {
		return state;
	}

	public String getPath() {
		return path;
	}

	public List<String> getLines() {
		return lines;
	}

	public int getLimit() {
		return limit;
	}
}
