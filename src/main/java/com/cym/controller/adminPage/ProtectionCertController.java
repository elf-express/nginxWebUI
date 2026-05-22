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

	@Mapping("")
	public ModelAndView index(ModelAndView modelAndView, Page page, String certKeywords) {
		// DenyAllow data (paginated)
		setPage(page);
		page = denyAllowService.search(page);

		List<Server> allServers = sqlHelper.findAll(Server.class);
		String httpDenyId = settingService.get("denyId");
		String httpAllowId = settingService.get("allowId");
		String streamDenyId = settingService.get("denyIdStream");
		String streamAllowId = settingService.get("allowIdStream");

		List<DenyAllowExt> exts = new ArrayList<DenyAllowExt>();
		for (DenyAllow denyAllow : (List<DenyAllow>) page.getRecords()) {
			DenyAllowExt ext = new DenyAllowExt();
			ext.setDenyAllow(denyAllow);

			if (StrUtil.isBlankIfStr(denyAllow.getIp())) {
				ext.setIpCount(0);
			} else {
				ext.setIpCount(denyAllow.getIp().split("\n").length);
			}

			// usedBy — denyId / allowId 可能是 CSV「id1,id2,id3」, 用 csvContainsId 比對
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
		page.setRecords(exts);
		modelAndView.put("daPage", page);

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
}
