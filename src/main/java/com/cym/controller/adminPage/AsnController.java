package com.cym.controller.adminPage;

import java.util.List;

import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Mapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cym.model.AsnRule;
import com.cym.sqlhelper.utils.ConditionAndWrapper;
import com.cym.utils.BaseController;
import com.cym.utils.JsonResult;
import com.cym.utils.MessageUtils;

import cn.hutool.core.util.StrUtil;

@Controller
@Mapping("/adminPage/asn")
public class AsnController extends BaseController {
	Logger logger = LoggerFactory.getLogger(this.getClass());

	@Mapping("list")
	public JsonResult list() {
		List<AsnRule> list = sqlHelper.findAll(AsnRule.class);
		return renderSuccess(list);
	}

	@Mapping("addOver")
	public JsonResult addOver(AsnRule asnRule) {
		if (StrUtil.isBlank(asnRule.getAsn()) || !asnRule.getAsn().trim().matches("\\d+")) {
			return renderError(m.get("asnStr.invalidAsn"));
		}
		asnRule.setAsn(asnRule.getAsn().trim());

		// 檢查重複
		if (StrUtil.isEmpty(asnRule.getId())) {
			AsnRule existing = sqlHelper.findOneByQuery(
					new ConditionAndWrapper().eq("asn", asnRule.getAsn()), AsnRule.class);
			if (existing != null) {
				return renderError(m.get("asnStr.duplicate"));
			}
		}

		sqlHelper.insertOrUpdate(asnRule);
		return renderSuccess();
	}

	@Mapping("del")
	public JsonResult del(String id) {
		if (StrUtil.isNotEmpty(id)) {
			String[] ids = id.split(",");
			for (String oneId : ids) {
				sqlHelper.deleteById(oneId.trim(), AsnRule.class);
			}
		}
		return renderSuccess();
	}

	@Mapping("setEnable")
	public JsonResult setEnable(String id, Boolean enable) {
		AsnRule rule = sqlHelper.findById(id, AsnRule.class);
		if (rule != null) {
			rule.setEnable(enable);
			sqlHelper.updateById(rule);
		}
		return renderSuccess();
	}
}
