package com.cym.controller.adminPage;

import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Mapping;

import com.cym.service.GeoipService;
import com.cym.utils.BaseController;
import com.cym.utils.JsonResult;

import cn.hutool.core.util.StrUtil;

/**
 * GeoIP 資料庫版本查詢 + 手動下載。
 * （與 GeoController 區隔：GeoController 管國家封鎖規則 GeoRule，本控制器只管 MMDB 檔本身。）
 */
@Controller
@Mapping("/adminPage/geoip")
public class GeoipController extends BaseController {
	@Inject
	GeoipService geoipService;

	/** 三個資料庫的版本 / 上次更新 / 排程（給 header 動態刷新與測試斷言）。 */
	@Mapping("versions")
	public JsonResult versions() {
		return renderSuccess(geoipService.getDbInfos());
	}

	/** 手動下載：db = country / city / asn / all（空值視為 all）。 */
	@Mapping("download")
	public JsonResult download(String db) {
		if (StrUtil.isBlank(db)) {
			db = "all";
		}
		boolean ok = geoipService.download(db);
		if (ok) {
			return renderSuccess();
		}
		return renderError(m.get("geoipStr.downloadFail"));
	}
}
