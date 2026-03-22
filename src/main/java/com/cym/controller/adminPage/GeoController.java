package com.cym.controller.adminPage;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Init;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Mapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cym.model.GeoRule;
import com.cym.service.NginxService;
import com.cym.sqlhelper.utils.ConditionAndWrapper;
import com.cym.utils.BaseController;
import com.cym.utils.JsonResult;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.io.resource.ClassPathResource;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;

@Controller
@Mapping("/adminPage/geo")
public class GeoController extends BaseController {
	Logger logger = LoggerFactory.getLogger(this.getClass());

	@Inject
	NginxService nginxService;

	// 啟動時載入一次，快取到記憶體
	@SuppressWarnings("rawtypes")
	private List<Map> countryList;

	@Init
	public void init() {
		try {
			ClassPathResource resource = new ClassPathResource("countries.json");
			InputStream in = resource.getStream();
			String json = IoUtil.readUtf8(in);
			JSONArray array = JSONUtil.parseArray(json);
			countryList = array.toList(Map.class);
		} catch (Exception e) {
			logger.error("Failed to load countries.json", e);
		}
	}

	@Mapping("list")
	public JsonResult list() {
		List<GeoRule> list = sqlHelper.findAll(GeoRule.class);
		return renderSuccess(list);
	}

	@Mapping("detail")
	public JsonResult detail(String serverId) {
		ConditionAndWrapper condition = new ConditionAndWrapper();
		if (StrUtil.isEmpty(serverId)) {
			condition.isNull("serverId");
		} else {
			condition.eq("serverId", serverId);
		}
		GeoRule geoRule = sqlHelper.findOneByQuery(condition, GeoRule.class);
		return renderSuccess(geoRule);
	}

	@Mapping("addOver")
	public JsonResult addOver(GeoRule geoRule) {
		sqlHelper.insertOrUpdate(geoRule);
		return renderSuccess();
	}

	@Mapping("del")
	public JsonResult del(String id) {
		sqlHelper.deleteById(id, GeoRule.class);
		return renderSuccess();
	}

	@Mapping("hasGeoIp2")
	public JsonResult hasGeoIp2() {
		return renderSuccess(nginxService.hasGeoIp2Module());
	}

	@Mapping("countries")
	public JsonResult countries() {
		return renderSuccess(countryList);
	}
}
