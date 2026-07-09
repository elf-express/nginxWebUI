package com.cym.controller.adminPage;

import java.util.ArrayList;
import java.util.List;

import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.core.handle.ModelAndView;

import com.cym.ext.DenyAllowExt;
import com.cym.model.Cert;
import com.cym.model.DenyAllow;
import com.cym.model.Server;
import com.cym.service.CertService;
import com.cym.service.DenyAllowService;
import com.cym.service.GeoipService;
import com.cym.service.SettingService;
import com.cym.sqlhelper.bean.Page;
import com.cym.utils.BaseController;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;

@Controller
@Mapping("/adminPage/protectionCert")
public class ProtectionCertController extends BaseController {
	@Inject
	DenyAllowService denyAllowService;
	@Inject
	SettingService settingService;
	@Inject
	CertService certService;
	@Inject
	GeoipService geoipService;

	@Mapping("")
	public ModelAndView index(ModelAndView modelAndView, Page page, String certKeywords) {
		// DenyAllow data (paginated) — 黑白名單分頁
		setPage(page);

		List<Server> allServers = sqlHelper.findAll(Server.class);
		String httpDenyId = settingService.get("denyId");
		String httpAllowId = settingService.get("allowId");
		String streamDenyId = settingService.get("denyIdStream");
		String streamAllowId = settingService.get("allowIdStream");

		// 黑名單分頁(type=deny)
		Page blackPage = new Page();
		blackPage.setCurr(page.getCurr());
		blackPage.setLimit(page.getLimit());
		blackPage = denyAllowService.searchByType(blackPage, "deny");
		blackPage.setRecords(buildExts((List<DenyAllow>) blackPage.getRecords(), allServers,
				httpDenyId, httpAllowId, streamDenyId, streamAllowId));
		modelAndView.put("blackPage", blackPage);

		// 白名單分頁(type=allow)
		Page whitePage = new Page();
		whitePage.setCurr(1);
		whitePage.setLimit(page.getLimit());
		whitePage = denyAllowService.searchByType(whitePage, "allow");
		whitePage.setRecords(buildExts((List<DenyAllow>) whitePage.getRecords(), allServers,
				httpDenyId, httpAllowId, streamDenyId, streamAllowId));
		modelAndView.put("whitePage", whitePage);

		// GeoIP 資料庫資訊（Tab 1 黑名單表格前面的版本 / 排程 / 下載表格）
		modelAndView.put("geoipDbInfos", geoipService.getDbInfos());

		// Cert data
		Page certPage = new Page();
		certPage.setCurr(1);
		certPage.setLimit(100);
		certPage = certService.getPage(certKeywords, certPage);
		for (Cert cert : (List<Cert>) certPage.getRecords()) {
			if (cert.getType() == 0 || cert.getType() == 2) {
				cert.setDomain(cert.getDomain() + "(" + cert.getEncryption() + ")");
			}
			if (cert.getMakeTime() != null && cert.getType() != 1) {
				cert.setEndTime(cert.getMakeTime() + 90 * 24 * 60 * 60 * 1000l);
			}
		}
		modelAndView.put("certPage", certPage);
		modelAndView.put("certKeywords", certKeywords);

		modelAndView.view("/adminPage/protectionCert/index.html");
		return modelAndView;
	}

	/** DenyAllow → DenyAllowExt(含 ipCount / usedBy / lastFetchAtStr)。 */
	private List<DenyAllowExt> buildExts(List<DenyAllow> list, List<Server> allServers,
			String httpDenyId, String httpAllowId, String streamDenyId, String streamAllowId) {
		List<DenyAllowExt> exts = new ArrayList<DenyAllowExt>();
		for (DenyAllow denyAllow : list) {
			DenyAllowExt ext = new DenyAllowExt();
			ext.setDenyAllow(denyAllow);
			ext.setIpCount(StrUtil.isBlankIfStr(denyAllow.getIp()) ? 0 : denyAllow.getIp().split("\n").length);

			List<String> usedBy = new ArrayList<String>();
			String daId = denyAllow.getId();
			if (DenyAllowService.csvContainsId(httpDenyId, daId) || DenyAllowService.csvContainsId(httpAllowId, daId)) {
				usedBy.add("HTTP Global");
			}
			if (DenyAllowService.csvContainsId(streamDenyId, daId) || DenyAllowService.csvContainsId(streamAllowId, daId)) {
				usedBy.add("Stream Global");
			}
			for (Server s : allServers) {
				if (DenyAllowService.csvContainsId(s.getDenyId(), daId) || DenyAllowService.csvContainsId(s.getAllowId(), daId)) {
					String label = StrUtil.isNotEmpty(s.getServerName()) ? s.getServerName() : s.getListen();
					usedBy.add("Server: " + label);
				}
			}
			ext.setUsedBy(usedBy);

			if (denyAllow.getLastFetchAt() != null) {
				ext.setLastFetchAtStr(DateUtil.format(new java.util.Date(denyAllow.getLastFetchAt()), "yyyy-MM-dd HH:mm"));
			}
			exts.add(ext);
		}
		return exts;
	}
}
