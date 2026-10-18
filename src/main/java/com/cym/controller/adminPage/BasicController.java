package com.cym.controller.adminPage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.core.handle.ModelAndView;

import com.cym.model.Basic;
import com.cym.model.Module;
import com.cym.service.BasicService;
import com.cym.service.NginxService;
import com.cym.sqlhelper.bean.Sort;
import com.cym.sqlhelper.bean.Sort.Direction;
import com.cym.utils.BaseController;
import com.cym.utils.JsonResult;
import com.cym.utils.SnowFlakeUtils;
import com.cym.utils.SystemTool;

import cn.hutool.core.util.StrUtil;

@Controller
@Mapping("/adminPage/basic")
public class BasicController extends BaseController {
	@Inject
	BasicService basicService;
	@Inject
	NginxService nginxService;

	@Mapping("")
	public ModelAndView index(ModelAndView modelAndView) {
		buildPageData().forEach(modelAndView::put);
		modelAndView.view("/adminPage/basic/index.html");
		return modelAndView;
	}

	@Mapping("pageData")
	public JsonResult pageData() {
		return renderSuccess(buildPageData());
	}

	private Map<String, Object> buildPageData() {
		Map<String, Object> data = new HashMap<>();
		data.put("basicList", withoutLoadModule(basicService.findAll()));
		data.put("moduleList", sqlHelper.findAll(new Sort("seq", Direction.ASC), Module.class));
		data.put("modulesOnDisk", nginxService.getAllModules());
		data.put("isLinux", SystemTool.isLinux());
		return data;
	}

	// load_module 由模組管理 UI 控制，不列在基礎參數清單
	static List<Basic> withoutLoadModule(List<Basic> basicList) {
		List<Basic> result = new ArrayList<>(basicList);
		result.removeIf(b -> "load_module".equals(b.getName()));
		return result;
	}

	@Mapping("addOver")
	public JsonResult addOver(Basic basic) {
		if (StrUtil.isEmpty(basic.getId())) {
			basic.setSeq( SnowFlakeUtils.getId());
		}
		sqlHelper.insertOrUpdate(basic);

		return renderSuccess();
	}

	@Mapping("setOrder")
	public JsonResult setOrder(String id, Integer count) {
		basicService.setSeq(id, count);

		return renderSuccess();
	}
	
	@Mapping("detail")
	public JsonResult detail(String id) {
		return renderSuccess(sqlHelper.findById(id, Basic.class));
	}

	@Mapping("del")
	public JsonResult del(String id) {
		String[] ids = id.split(",");
		sqlHelper.deleteByIds(ids, Basic.class);

		return renderSuccess();
	}

	@Mapping("setModuleEnable")
	public JsonResult setModuleEnable(Module module) {
		sqlHelper.updateById(module);
		return renderSuccess();
	}

}
