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
import com.cym.model.Server;
import com.cym.model.Upstream;
import com.cym.service.DenyAllowService;
import com.cym.service.SettingService;
import com.cym.sqlhelper.bean.Page;
import com.cym.utils.BaseController;
import com.cym.utils.JsonResult;

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

		// 預載所有 Server 用於 usedBy 查詢
		List<Server> allServers = sqlHelper.findAll(Server.class);
		String httpDenyId = settingService.get("denyId");
		String httpAllowId = settingService.get("allowId");
		String streamDenyId = settingService.get("denyIdStream");
		String streamAllowId = settingService.get("allowIdStream");

		List<DenyAllowExt> exts = new ArrayList<DenyAllowExt>();
		for (DenyAllow denyAllow : (List<DenyAllow>) page.getRecords()) {
			DenyAllowExt denyAllowExt = new DenyAllowExt();
			denyAllowExt.setDenyAllow(denyAllow);

			if (StrUtil.isBlankIfStr(denyAllow.getIp())) {
				denyAllowExt.setIpCount(0);
			} else {
				denyAllowExt.setIpCount(denyAllow.getIp().split("\n").length);
			}

			// 計算 usedBy
			List<String> usedBy = new ArrayList<String>();
			String daId = denyAllow.getId();
			if (daId.equals(httpDenyId) || daId.equals(httpAllowId)) {
				usedBy.add("HTTP Global");
			}
			if (daId.equals(streamDenyId) || daId.equals(streamAllowId)) {
				usedBy.add("Stream Global");
			}
			for (Server s : allServers) {
				if (daId.equals(s.getDenyId()) || daId.equals(s.getAllowId())) {
					String label = StrUtil.isNotEmpty(s.getServerName()) ? s.getServerName() : s.getListen();
					usedBy.add("Server: " + label);
				}
			}
			denyAllowExt.setUsedBy(usedBy);

			exts.add(denyAllowExt);
		}
		page.setRecords(exts);

		modelAndView.put("page", page);
		modelAndView.view("/adminPage/denyAllow/index.html");
		return modelAndView;
	}

	@Mapping("addOver")
	public JsonResult addOver(DenyAllow denyAllow) {
		// 若填了來源 URL，立即抓一次（覆寫 ip + 設 lastFetchAt）；之後每天 fetchTime 排程繼續抓
		if (StrUtil.isNotBlank(denyAllow.getSourceUrl())) {
			boolean ok = denyAllowService.fetchAndUpdate(denyAllow);
			if (!ok) {
				// 抓失敗仍允許儲存（保留使用者填的 URL 與 fetchTime），讓排程之後再試
				logger.warn("Immediate fetch failed for {} ({}), saving record anyway", denyAllow.getName(), denyAllow.getSourceUrl());
			}
		} else {
			// 沒填 URL，走原本的手動 IP 去重邏輯
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
