package com.cym.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * GeoIP 自動下載的時間／開關判斷（project.skipGeoipFetch，E2E 與離線環境用）。
 */
public class ScheduleTaskGeoipFetchModeTest {

	@Test
	void fetchTimeMatches_downloadsAll() {
		assertEquals("all", ScheduleTask.geoipFetchMode(false, "03:00", "03:00"));
	}

	@Test
	void topOfHour_downloadsStaleOnly() {
		assertEquals("stale", ScheduleTask.geoipFetchMode(false, "03:00", "14:00"));
	}

	@Test
	void otherMinutes_doNothing() {
		assertNull(ScheduleTask.geoipFetchMode(false, "03:00", "14:07"));
	}

	@Test
	void skipEnabled_neverDownloads() {
		assertNull(ScheduleTask.geoipFetchMode(true, "03:00", "03:00"));
		assertNull(ScheduleTask.geoipFetchMode(true, "03:00", "14:00"));
	}
}
