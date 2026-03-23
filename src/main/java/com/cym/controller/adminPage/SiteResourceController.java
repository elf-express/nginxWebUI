package com.cym.controller.adminPage;

import java.util.List;

import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.core.handle.ModelAndView;

import com.cym.model.Www;
import com.cym.model.Password;
import com.cym.service.PasswordService;
import com.cym.sqlhelper.bean.Page;
import com.cym.sqlhelper.bean.Sort;
import com.cym.sqlhelper.bean.Sort.Direction;
import com.cym.utils.BaseController;

@Controller
@Mapping("/adminPage/siteResource")
public class SiteResourceController extends BaseController {
	@Inject
	PasswordService passwordService;

	@Mapping("")
	public ModelAndView index(ModelAndView modelAndView, Page page) {
		// WWW data
		List<Www> wwwList = sqlHelper.findAll(new Sort("dir", Direction.ASC), Www.class);
		modelAndView.put("wwwList", wwwList);

		// Password data (paginated)
		setPage(page);
		page = passwordService.search(page);
		modelAndView.put("pwdPage", page);

		modelAndView.view("/adminPage/siteResource/index.html");
		return modelAndView;
	}
}
