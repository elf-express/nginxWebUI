package com.cym.controller.adminPage;

import java.util.ArrayList;
import java.util.List;

import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.core.handle.ModelAndView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cym.ext.DenyAllowExt;
import com.cym.ext.ServerExt;
import com.cym.model.Admin;
import com.cym.model.DenyAllow;
import com.cym.model.Log;
import com.cym.model.Upstream;
import com.cym.service.DenyAllowService;
import com.cym.service.SettingService;
import com.cym.sqlhelper.bean.Page;
import com.cym.utils.BaseController;
import com.cym.utils.JsonResult;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;

@Controller
@Mapping("/adminPage/denyAllow")
public class DenyAllowController extends BaseController {
	Logger logger = LoggerFactory.getLogger(this.getClass());
	@Inject
	DenyAllowService denyAllowService;

	@Inject
	SettingService settingService;

	@Mapping("")
	public ModelAndView index(ModelAndView modelAndView, Page page) {
		setPage(page);
		page = denyAllowService.search(page);

		List<DenyAllowExt> exts = new ArrayList<DenyAllowExt>();
		for (DenyAllow denyAllow : (List<DenyAllow>) page.getRecords()) {
			DenyAllowExt denyAllowExt = new DenyAllowExt();
			denyAllowExt.setDenyAllow(denyAllow);

			if (StrUtil.isBlankIfStr(denyAllow.getIp())) {
				denyAllowExt.setIpCount(0);
			} else {
				denyAllowExt.setIpCount(denyAllow.getIp().split("\n").length);
			}

			if (denyAllow.getLastFetchAt() != null) {
				denyAllowExt.setLastFetchAtStr(DateUtil.format(new java.util.Date(denyAllow.getLastFetchAt()), "yyyy-MM-dd HH:mm"));
			}

			exts.add(denyAllowExt);
		}
		page.setRecords(exts);

		modelAndView.put("page", page);
		modelAndView.view("/adminPage/denyAllow/index.html");
		return modelAndView;
	}

	@Mapping("addOver")
	public JsonResult addOver(DenyAllow denyAllow) {
		// 黑白衝突檢查:同一 IP 已存在於另一 type 名單時提示,不靜默建立
		java.util.List<String> conflicts = denyAllowService.findConflictIps(denyAllow, denyAllow.getType());
		if (!conflicts.isEmpty()) {
			String preview = conflicts.size() > 5
					? StrUtil.join(", ", conflicts.subList(0, 5)) + " ..."
					: StrUtil.join(", ", conflicts);
			return renderError(m.get("denyAllowStr.typeConflict").replace("{ips}", preview));
		}

		// 若填了來源 URL，立即抓一次；之後每天 fetchTime 排程繼續抓
		if (StrUtil.isNotBlank(denyAllow.getSourceUrl())) {
			boolean ok = denyAllowService.fetchAndUpdate(denyAllow);
			if (!ok) {
				logger.warn("Immediate fetch failed for {} ({}), saving record anyway", denyAllow.getName(), denyAllow.getSourceUrl());
			}
		} else {
			denyAllowService.removeSame(denyAllow);
		}

		sqlHelper.insertOrUpdate(denyAllow);

		return renderSuccess();
	}


	@Mapping("detail")
	public JsonResult detail(String id) {
		return renderSuccess(sqlHelper.findById(id, DenyAllow.class));
	}

	@Mapping("del")
	public JsonResult del(String id) {
		String[] ids = id.split(",");
		sqlHelper.deleteByIds(ids, DenyAllow.class);

		return renderSuccess();
	}

}
