package com.cym.controller.adminPage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.annotation.Post;
import org.noear.solon.core.handle.ModelAndView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cym.config.AppFilter;
import com.cym.ext.LogTailData;
import com.cym.model.Log;
import com.cym.service.LogService;
import com.cym.service.SettingService;
import com.cym.sqlhelper.bean.Page;
import com.cym.sqlhelper.utils.ConditionAndWrapper;
import com.cym.utils.BLogFileTailer;
import com.cym.utils.BaseController;
import com.cym.utils.JsonResult;
import com.cym.utils.SystemTool;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.core.util.StrUtil;

@Controller
@Mapping("/adminPage/log")
public class LogController extends BaseController {
	Logger logger = LoggerFactory.getLogger(this.getClass());
	@Inject
	SettingService settingService;
	@Inject
	LogService logService;
	@Inject
	AppFilter appFilter;
	@Inject
	BLogFileTailer bLogFileTailer;

	@Mapping("")
	public ModelAndView index(ModelAndView modelAndView, Page page) {
		setPage(page);
		page = logService.search(page);
		modelAndView.put("page", page);

		modelAndView.put("isLinux", SystemTool.isLinux());
		modelAndView.view("/adminPage/log/index.html");
		return modelAndView;
	}

	@Mapping("addOver")
	public JsonResult addOver(Log log) {
		if (logService.hasDir(log.getPath(), log.getId())) {
			return renderError(m.get("logStr.sameDir"));
		}

		if (FileUtil.isDirectory(log.getPath())) {
			return renderError(m.get("logStr.notFile"));
		}

		sqlHelper.insertOrUpdate(log);
		return renderSuccess();
	}

	@Mapping("detail")
	public JsonResult detail(String id) {
		return renderSuccess(sqlHelper.findById(id, Log.class));
	}

	@Mapping("del")
	public JsonResult del(String id) {
		String[] ids = id.split(",");
		sqlHelper.deleteByIds(ids, Log.class);
		return renderSuccess();
	}

	@Mapping("tail")
	public ModelAndView tail(ModelAndView modelAndView, String id, String protocol, String legacy) {
		modelAndView.put("id", id);
		modelAndView.view("1".equals(legacy) ? "/adminPage/log/tail.html" : "/adminPage/log/tailSpa.html");
		return modelAndView;
	}

	@Mapping("down")
	public File down(String id) throws IOException {
		Log log = sqlHelper.findById(id, Log.class);
		//todo: 简化下载处理
		return new File(log.getPath());
	}

	@Mapping("tailCmd")
	public JsonResult tailCmd(String id, String guid) {
		Log log = sqlHelper.findById(id, Log.class);
		if (!FileUtil.exist(log.getPath())) {
			bLogFileTailer.invalidate(guid, log.getPath());
			return renderSuccess("");
		}

		String rs = bLogFileTailer.run(guid, log.getPath());
		return renderSuccess(rs);
	}

	@Post
	@Mapping("tailData")
	public JsonResult tailData(String id, String guid) {
		if (StrUtil.isBlank(id)) {
			return renderError(m.get("logStr.invalidId"));
		}
		if (StrUtil.isBlank(guid)) {
			return renderError(m.get("logStr.invalidGuid"));
		}
		Log log = sqlHelper.findById(id, Log.class);
		if (log == null) {
			return renderError(m.get("logStr.invalidId"));
		}
		String path = log.getPath();
		try {
			if (StrUtil.isBlank(path)) {
				return renderSuccess(new LogTailData("unreadable", path, List.of()));
			}
			Path file = Path.of(path);
			if (Files.notExists(file)) {
				bLogFileTailer.invalidate(guid, path);
				return renderSuccess(new LogTailData("missing", path, List.of()));
			}
			if (!Files.isRegularFile(file) || !Files.isReadable(file)) {
				bLogFileTailer.invalidate(guid, path);
				return renderSuccess(new LogTailData("unreadable", path, List.of()));
			}
		} catch (RuntimeException e) {
			return renderSuccess(new LogTailData("unreadable", path, List.of()));
		}
		try {
			return renderSuccess(new LogTailData("ready", path, bLogFileTailer.readLines(guid, path)));
		} catch (IllegalArgumentException e) {
			return renderError(m.get("logStr.invalidGuid"));
		} catch (RuntimeException e) {
			// The reader closes failed sessions before reporting an error; the next poll can rebuild it.
			return renderSuccess(new LogTailData("unreadable", path, List.of()));
		}
	}

}
