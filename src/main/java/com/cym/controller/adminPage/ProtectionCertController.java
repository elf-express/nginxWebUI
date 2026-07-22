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
	public ModelAndView index(ModelAndView modelAndView, String certKeywords) {
		// DenyAllow data (full list) — 黑白名單全列（與 ASN tab 一致，無需分頁）
		// 黑名單全列(type=deny)
		List<DenyAllowExt> blackList = buildExts(denyAllowService.listByType("deny"));
		modelAndView.put("blackList", blackList);

		// 白名單全列(type=allow)
		List<DenyAllowExt> whiteList = buildExts(denyAllowService.listByType("allow"));
		modelAndView.put("whiteList", whiteList);

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

	/** DenyAllow → DenyAllowExt(含 ipCount / lastFetchAtStr;規則一律全站生效,無逐項綁定)。 */
	private List<DenyAllowExt> buildExts(List<DenyAllow> list) {
		List<DenyAllowExt> exts = new ArrayList<DenyAllowExt>();
		for (DenyAllow denyAllow : list) {
			DenyAllowExt ext = new DenyAllowExt();
			ext.setDenyAllow(denyAllow);
			ext.setIpCount(StrUtil.isBlankIfStr(denyAllow.getIp()) ? 0 : denyAllow.getIp().split("\n").length);

			if (denyAllow.getLastFetchAt() != null) {
				ext.setLastFetchAtStr(DateUtil.format(new java.util.Date(denyAllow.getLastFetchAt()), "yyyy-MM-dd HH:mm"));
			}
			exts.add(ext);
		}
		return exts;
	}
}
