package com.cym.controller.adminPage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.cym.model.Basic;

/**
 * basic 頁資料組裝（純函式）。
 */
public class BasicControllerTest {

	@Test
	void withoutLoadModule_dropsLoadModuleOnly() {
		List<Basic> input = new ArrayList<>(Arrays.asList(
				new Basic("worker_processes", "auto", 1L),
				new Basic("load_module", "modules/ngx_stream_module.so", 2L),
				new Basic("events", "{", 3L)));

		List<Basic> result = BasicController.withoutLoadModule(input);

		assertEquals(2, result.size());
		assertEquals("worker_processes", result.get(0).getName());
		assertEquals("events", result.get(1).getName());
		assertEquals(3, input.size(), "不可修改傳入的清單");
	}
}
