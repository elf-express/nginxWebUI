package com.cym.service;

import java.util.ArrayList;
import java.util.List;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;

import com.cym.model.Param;
import com.cym.model.Template;
import com.cym.sqlhelper.utils.ConditionAndWrapper;
import com.cym.sqlhelper.utils.SqlHelper;
import com.cym.utils.TemplateDefUtils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;

@Component
public class ParamService {

	@Inject
	SqlHelper sqlHelper;

	public String getJsonByTypeId(String id, String type) {
		List<Param> list = sqlHelper.findListByQuery(new ConditionAndWrapper().eq(type + "Id", id), Param.class);
		for (Param param : list) {
			if (StrUtil.isNotEmpty(param.getTemplateValue())) {
				Template template = sqlHelper.findById(param.getTemplateValue(), Template.class);
				param.setTemplateName(template.getName());
			}

		}
		return JSONUtil.toJsonStr(list);
	}

	public List<Param> getListByTypeId(String id, String type) {
		List<Param> list = new ArrayList<>();
		// 自動套用：Template.def 可多選（逗號分隔），例 "server,location"
		String matchType = type;
		boolean streamServer = "server1".equals(matchType) || "server2".equals(matchType);
		List<Template> allTemplates = sqlHelper.findAll(Template.class);
		for (Template template : allTemplates) {
			if (!TemplateDefUtils.contains(template.getDef(), matchType)) {
				continue;
			}
			List<Param> addList = sqlHelper.findListByQuery(
					new ConditionAndWrapper().eq(Param::getTemplateId, template.getId()), Param.class);
			// stream TCP/UDP server：再擋一層 HTTP-only 指令（與 ConfService stream 頂層白名單互補）
			if (streamServer) {
				for (Param p : addList) {
					if (p == null || StrUtil.isEmpty(p.getName())) {
						continue;
					}
					if (TemplateDefUtils.isSafeForStreamServer(p.getName())) {
						list.add(p);
					}
				}
			} else {
				list.addAll(addList);
			}
		}

		if (type.contains("server")) {
			type = "server";
		}

		list.addAll(sqlHelper.findListByQuery(new ConditionAndWrapper().eq(type + "Id", id), Param.class));

		return list;
	}

	public List<Param> getList(String serverId, String locationId, String upstreamId) {
		ConditionAndWrapper conditionAndWrapper = new ConditionAndWrapper();
		if (StrUtil.isNotEmpty(serverId)) {
			conditionAndWrapper.eq("serverId", serverId);
		}
		if (StrUtil.isNotEmpty(locationId)) {
			conditionAndWrapper.eq("locationId", locationId);
		}
		if (StrUtil.isNotEmpty(upstreamId)) {
			conditionAndWrapper.eq("upstreamId", upstreamId);
		}

		return sqlHelper.findListByQuery(conditionAndWrapper, Param.class);
	}

}
