package com.cym.controller.adminPage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.core.handle.Context;
import org.noear.solon.core.handle.ModelAndView;
import org.noear.solon.core.handle.UploadedFile;

import com.cym.config.HomeConfig;
import cn.hutool.core.io.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cym.ext.AdminExt;
import com.cym.ext.Tree;
import com.cym.model.Admin;
import com.cym.model.Group;
import com.cym.service.AdminService;
import com.cym.service.GroupService;
import com.cym.service.SettingService;
import com.cym.sqlhelper.bean.Page;
import com.cym.utils.AuthUtils;
import com.cym.utils.BaseController;
import com.cym.utils.JsonResult;
import com.cym.utils.SendMailUtils;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;

import cn.hutool.core.util.StrUtil;

@Controller
@Mapping("/adminPage/admin")
public class AdminController extends BaseController {
	Logger logger = LoggerFactory.getLogger(this.getClass());
	@Inject
	AdminService adminService;
	@Inject
	SettingService settingService;
	@Inject
	SendMailUtils sendCloudUtils;
	@Inject
	AuthUtils authUtils;
	@Inject
	GroupService groupService;
	@Inject
	RemoteController remoteController;
	@Inject
	HomeConfig homeConfig;

	@Mapping("")
	public ModelAndView index(ModelAndView modelAndView, Page page) {
		setPage(page);
		page = adminService.search(page);
		modelAndView.put("page", page);
		modelAndView.view("/adminPage/admin/index.html");
		return modelAndView;
	}

	@Mapping("addOver")
	public JsonResult addOver(String id, String name, Boolean api, Integer type, String[] parentId) {

		if (StrUtil.isEmpty(id)) {
			Long count = adminService.getCountByName(name);
			if (count > 0) {
				return renderError(m.get("adminStr.nameRepetition"));
			}
		} else {
			Long count = adminService.getCountByNameWithOutId(name, id);
			if (count > 0) {
				return renderError(m.get("adminStr.nameRepetition"));
			}
		}

		Admin admin = new Admin();
		admin.setId(id);
		admin.setName(name);
		admin.setApi(api);
		admin.setType(type);

		adminService.addOver(admin, parentId);

		return renderSuccess();
	}

	@Mapping("changePassOver")
	public JsonResult changePassOver(String id, String pass, Boolean auth) {
		Admin admin = new Admin();
		admin.setId(id);
		admin.setPass(pass);
		admin.setAuth(auth);

		adminService.changePassOver(admin);

		return renderSuccess();
	}

	@Mapping("detail")
	public JsonResult detail(String id) {
		AdminExt adminExt = new AdminExt();
		adminExt.setAdmin(sqlHelper.findById(id, Admin.class));
		adminExt.setGroupIds(adminService.getGroupIds(adminExt.getAdmin().getId()));
		adminExt.getAdmin().setPass("");
		return renderSuccess(adminExt);
	}

	@Mapping("del")
	public JsonResult del(String id) {
		String[] ids = id.split(",");
		sqlHelper.deleteByIds(ids, Admin.class);

		return renderSuccess();
	}
	
	
	

	@Mapping("getMailSetting")
	public JsonResult getMailSetting() {
		Map<String, String> map = new HashMap<>();

		map.put("mail_host", settingService.get("mail_host"));
		map.put("mail_port", settingService.get("mail_port"));
		map.put("mail_from", settingService.get("mail_from"));
		map.put("mail_user", settingService.get("mail_user"));
		map.put("mail_pass", settingService.get("mail_pass"));
		map.put("mail_ssl", settingService.get("mail_ssl"));
		map.put("mail_interval", settingService.get("mail_interval"));

		return renderSuccess(map);
	}

	@Mapping("updateMailSetting")
	public JsonResult updateMailSetting(String mailType, String mail_user, String mail_host, String mail_port, String mail_from, String mail_pass, String mail_ssl, String mail_interval) {
		settingService.set("mail_host", mail_host);
		settingService.set("mail_port", mail_port);
		settingService.set("mail_user", mail_user);
		settingService.set("mail_from", mail_from);
		settingService.set("mail_pass", mail_pass);
		settingService.set("mail_ssl", mail_ssl);
		settingService.set("mail_interval", mail_interval);

		return renderSuccess();
	}

	@Mapping("testMail")
	public JsonResult testMail(String mail) {
		if (StrUtil.isEmpty(mail)) {
			return renderError(m.get("adminStr.emailEmpty"));
		}
		try {
			sendCloudUtils.sendMailSmtp(mail, m.get("adminStr.emailTest"), m.get("adminStr.emailTest"));
			return renderSuccess();
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			return renderError(m.get("commonStr.error") + ": " + e.getMessage());
		}
	}

	@Mapping("testAuth")
	public JsonResult testAuth(String key, String code) {

		Boolean rs = authUtils.testKey(key, code);
		return renderSuccess(rs);
	}

	@Mapping(value = "qr")
	public void getqcode(String url, Integer w, Integer h) throws IOException {
		if (StrUtil.isNotBlank(url)) {

			if (w == null) {
				w = 300;
			}
			if (h == null) {
				h = 300;
			}
			try {
				Hashtable<EncodeHintType, Object> hints = new Hashtable<EncodeHintType, Object>();
				hints.put(EncodeHintType.CHARACTER_SET, "utf-8");
				hints.put(EncodeHintType.MARGIN, 0);

				BitMatrix matrix = new MultiFormatWriter().encode(url, BarcodeFormat.QR_CODE, w, h, hints);
				MatrixToImageWriter.writeToStream(matrix, "png", Context.current().outputStream());
			} catch (WriterException e) {
				logger.error(e.getMessage(), e);
			}
		}
	}

	@Mapping("getGroupTree")
	public JsonResult getGroupTree() {

		List<Group> groups = groupService.getListByParent(null);
		List<Tree> treeList = new ArrayList<>();
		remoteController.fillTree(groups, treeList);

		return renderSuccess(treeList);
	}

	// ───────────── 品牌 Logo 上傳 ─────────────
	// 上傳 logo（png/jpg/jpeg/svg、上限 200 KB），存到 homeConfig.home + "brand-logo.<ext>"
	// 副檔名記在 setting key="brandLogoExt"，AppFilter 讀取注入 ${brandLogoUrl} 給 header.html
	@Mapping("uploadLogo")
	public JsonResult uploadLogo(UploadedFile file) {
		if (file == null) {
			return renderError("no file");
		}
		try {
			String name = file.getName();
			String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : "";
			if (!"png".equals(ext) && !"jpg".equals(ext) && !"jpeg".equals(ext) && !"svg".equals(ext)) {
				return renderError("僅支援 png / jpg / svg");
			}
			if (file.getContentSize() > 200 * 1024) {
				return renderError("檔案不可超過 200 KB");
			}

			// 先把所有副檔名舊檔清掉，避免殘留
			deleteAllLogoFiles();

			File dest = new File(homeConfig.home + "brand-logo." + ext);
			file.transferTo(dest);
			settingService.set("brandLogoExt", ext);
			return renderSuccess();
		} catch (Exception e) {
			logger.error("uploadLogo failed", e);
			return renderError(e.getMessage());
		}
	}

	// 服務 logo 圖檔（binary 直接 stream 出去）
	@Mapping("logo")
	public void logo(Context context) throws IOException {
		String ext = settingService.get("brandLogoExt");
		if (StrUtil.isBlank(ext)) {
			context.status(404);
			return;
		}
		File f = new File(homeConfig.home + "brand-logo." + ext);
		if (!f.exists()) {
			context.status(404);
			return;
		}
		String mime = "svg".equals(ext) ? "image/svg+xml" : "image/" + ("jpg".equals(ext) ? "jpeg" : ext);
		context.contentType(mime);
		context.output(Files.readAllBytes(f.toPath()));
	}

	// 恢復預設（刪 logo 檔 + 清 setting）
	@Mapping("resetLogo")
	public JsonResult resetLogo() {
		deleteAllLogoFiles();
		settingService.set("brandLogoExt", "");
		return renderSuccess();
	}

	private void deleteAllLogoFiles() {
		for (String ext : new String[] { "png", "jpg", "jpeg", "svg" }) {
			File f = new File(homeConfig.home + "brand-logo." + ext);
			if (f.exists()) {
				FileUtil.del(f);
			}
		}
	}
}
