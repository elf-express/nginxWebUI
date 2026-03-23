package com.cym.controller.adminPage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.core.handle.ModelAndView;

import com.cym.ext.TemplateExt;
import com.cym.ext.TemplateGroupExt;
import com.cym.model.Param;
import com.cym.model.Template;
import com.cym.service.TemplateService;
import com.cym.utils.BaseController;
import com.cym.utils.JsonResult;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;

@Controller
@Mapping("/adminPage/template")
public class TemplateController extends BaseController {
	@Inject
	TemplateService templateService;

	private static final String[][] GROUP_DEFS = {
		{ "proxy",     "templateGroup.proxy",     "templateGroup.proxyDesc" },
		{ "cache",     "templateGroup.cache",     "templateGroup.cacheDesc" },
		{ "cors",      "templateGroup.cors",      "templateGroup.corsDesc" },
		{ "rateLimit", "templateGroup.rateLimit", "templateGroup.rateLimitDesc" },
		{ "security",  "templateGroup.security",  "templateGroup.securityDesc" },
		{ "geoip",     "templateGroup.geoip",     "templateGroup.geoipDesc" },
		{ "crowdsec",  "templateGroup.crowdsec",  "templateGroup.crowdsecDesc" },
	};

	@Mapping("")
	public ModelAndView index(ModelAndView modelAndView) {
		List<Template> templateList = sqlHelper.findAll(Template.class);

		// Build TemplateExt list
		List<TemplateExt> allExts = new ArrayList<>();
		for (Template template : templateList) {
			TemplateExt ext = new TemplateExt();
			ext.setTemplate(template);
			ext.setParamList(templateService.getParamList(template.getId()));
			ext.setCount(ext.getParamList().size());
			allExts.add(ext);
		}

		// Group by groupName
		LinkedHashMap<String, List<TemplateExt>> groupMap = new LinkedHashMap<>();
		for (String[] def : GROUP_DEFS) {
			groupMap.put(def[0], new ArrayList<>());
		}
		String customKey = "_custom";
		groupMap.put(customKey, new ArrayList<>());

		for (TemplateExt ext : allExts) {
			String gn = ext.getTemplate().getGroupName();
			if (StrUtil.isBlank(gn)) {
				groupMap.get(customKey).add(ext);
			} else if (groupMap.containsKey(gn)) {
				groupMap.get(gn).add(ext);
			} else {
				groupMap.get(customKey).add(ext);
			}
		}

		// Build TemplateGroupExt list
		List<TemplateGroupExt> groupList = new ArrayList<>();
		for (Map.Entry<String, List<TemplateExt>> entry : groupMap.entrySet()) {
			if (entry.getValue().isEmpty()) continue;

			TemplateGroupExt gExt = new TemplateGroupExt();
			gExt.setGroupName(entry.getKey());
			gExt.setTemplateExtList(entry.getValue());

			boolean found = false;
			for (String[] def : GROUP_DEFS) {
				if (def[0].equals(entry.getKey())) {
					gExt.setDisplayName(m.get(def[1]));
					gExt.setDescription(m.get(def[2]));
					found = true;
					break;
				}
			}
			if (!found) {
				gExt.setDisplayName(m.get("templateGroup.custom"));
				gExt.setDescription(m.get("templateGroup.customDesc"));
			}

			groupList.add(gExt);
		}

		modelAndView.put("templateList", allExts);
		modelAndView.put("groupList", groupList);
		modelAndView.view("/adminPage/template/index.html");
		return modelAndView;
	}

	@Mapping("addOver")
	public JsonResult addOver(Template template, String paramJson) {

		if (StrUtil.isEmpty(template.getId())) {
			Long count = templateService.getCountByName(template.getName());
			if (count > 0) {
				return renderError(m.get("templateStr.sameName"));
			}
		} else {
			Long count = templateService.getCountByNameWithOutId(template.getName(), template.getId());
			if (count > 0) {
				return renderError(m.get("templateStr.sameName"));
			}
			// Preserve existing groupName when editing
			Template existing = sqlHelper.findById(template.getId(), Template.class);
			if (existing != null && StrUtil.isBlank(template.getGroupName())) {
				template.setGroupName(existing.getGroupName());
			}
		}

		List<Param> params = JSONUtil.toList(JSONUtil.parseArray(paramJson), Param.class);

		templateService.addOver(template, params);

		return renderSuccess();
	}

	@Mapping("detail")
	public JsonResult detail(String id) {
		Template template = sqlHelper.findById(id, Template.class);
		TemplateExt templateExt = new TemplateExt();
		templateExt.setTemplate(template);

		templateExt.setParamList(templateService.getParamList(template.getId()));
		templateExt.setCount(templateExt.getParamList().size());

		return renderSuccess(templateExt);
	}

	@Mapping("del")
	public JsonResult del(String id) {

		templateService.del(id);
		return renderSuccess();
	}

	@Mapping("getTemplate")
	public JsonResult getTemplate() {

		return renderSuccess(sqlHelper.findAll(Template.class));
	}

}
