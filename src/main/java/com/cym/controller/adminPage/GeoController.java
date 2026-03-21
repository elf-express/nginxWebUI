package com.cym.controller.adminPage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Mapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cym.model.GeoRule;
import com.cym.service.NginxService;
import com.cym.sqlhelper.utils.ConditionAndWrapper;
import com.cym.utils.BaseController;
import com.cym.utils.JsonResult;

import cn.hutool.core.util.StrUtil;

@Controller
@Mapping("/adminPage/geo")
public class GeoController extends BaseController {
	Logger logger = LoggerFactory.getLogger(this.getClass());

	@Inject
	NginxService nginxService;

	@Mapping("list")
	public JsonResult list() {
		List<GeoRule> list = sqlHelper.findAll(GeoRule.class);
		return renderSuccess(list);
	}

	@Mapping("detail")
	public JsonResult detail(String serverId) {
		ConditionAndWrapper condition = new ConditionAndWrapper();
		if (StrUtil.isEmpty(serverId)) {
			condition.isNull("serverId");
		} else {
			condition.eq("serverId", serverId);
		}
		GeoRule geoRule = sqlHelper.findOneByQuery(condition, GeoRule.class);
		return renderSuccess(geoRule);
	}

	@Mapping("addOver")
	public JsonResult addOver(GeoRule geoRule) {
		sqlHelper.insertOrUpdate(geoRule);
		return renderSuccess();
	}

	@Mapping("del")
	public JsonResult del(String id) {
		sqlHelper.deleteById(id, GeoRule.class);
		return renderSuccess();
	}

	@Mapping("hasGeoIp2")
	public JsonResult hasGeoIp2() {
		return renderSuccess(nginxService.hasGeoIp2Module());
	}

	@Mapping("countries")
	public JsonResult countries() {
		List<Map<String, Object>> continents = new ArrayList<>();

		continents.add(buildContinent("asia", new String[][] {
				{ "TW", "台灣", "Taiwan" },
				{ "JP", "日本", "Japan" },
				{ "KR", "韓國", "South Korea" },
				{ "CN", "中國", "China" },
				{ "HK", "香港", "Hong Kong" },
				{ "MO", "澳門", "Macau" },
				{ "SG", "新加坡", "Singapore" },
				{ "MY", "馬來西亞", "Malaysia" },
				{ "TH", "泰國", "Thailand" },
				{ "VN", "越南", "Vietnam" },
				{ "PH", "菲律賓", "Philippines" },
				{ "ID", "印尼", "Indonesia" },
				{ "IN", "印度", "India" },
				{ "PK", "巴基斯坦", "Pakistan" },
				{ "BD", "孟加拉", "Bangladesh" },
				{ "MM", "緬甸", "Myanmar" },
				{ "KH", "柬埔寨", "Cambodia" },
				{ "LA", "寮國", "Laos" },
				{ "NP", "尼泊爾", "Nepal" },
				{ "LK", "斯里蘭卡", "Sri Lanka" },
				{ "MN", "蒙古", "Mongolia" },
				{ "KZ", "哈薩克", "Kazakhstan" },
				{ "UZ", "烏茲別克", "Uzbekistan" },
				{ "IL", "以色列", "Israel" },
				{ "AE", "阿聯酋", "UAE" },
				{ "SA", "沙烏地阿拉伯", "Saudi Arabia" },
				{ "TR", "土耳其", "Turkey" },
				{ "IQ", "伊拉克", "Iraq" },
				{ "IR", "伊朗", "Iran" }
		}));

		continents.add(buildContinent("europe", new String[][] {
				{ "GB", "英國", "United Kingdom" },
				{ "DE", "德國", "Germany" },
				{ "FR", "法國", "France" },
				{ "IT", "義大利", "Italy" },
				{ "ES", "西班牙", "Spain" },
				{ "PT", "葡萄牙", "Portugal" },
				{ "NL", "荷蘭", "Netherlands" },
				{ "BE", "比利時", "Belgium" },
				{ "CH", "瑞士", "Switzerland" },
				{ "AT", "奧地利", "Austria" },
				{ "SE", "瑞典", "Sweden" },
				{ "NO", "挪威", "Norway" },
				{ "DK", "丹麥", "Denmark" },
				{ "FI", "芬蘭", "Finland" },
				{ "IE", "愛爾蘭", "Ireland" },
				{ "PL", "波蘭", "Poland" },
				{ "CZ", "捷克", "Czech Republic" },
				{ "RO", "羅馬尼亞", "Romania" },
				{ "HU", "匈牙利", "Hungary" },
				{ "GR", "希臘", "Greece" },
				{ "UA", "烏克蘭", "Ukraine" },
				{ "RU", "俄羅斯", "Russia" },
				{ "BY", "白俄羅斯", "Belarus" }
		}));

		continents.add(buildContinent("northAmerica", new String[][] {
				{ "US", "美國", "United States" },
				{ "CA", "加拿大", "Canada" },
				{ "MX", "墨西哥", "Mexico" }
		}));

		continents.add(buildContinent("southAmerica", new String[][] {
				{ "BR", "巴西", "Brazil" },
				{ "AR", "阿根廷", "Argentina" },
				{ "CL", "智利", "Chile" },
				{ "CO", "哥倫比亞", "Colombia" },
				{ "PE", "秘魯", "Peru" },
				{ "VE", "委內瑞拉", "Venezuela" }
		}));

		continents.add(buildContinent("oceania", new String[][] {
				{ "AU", "澳洲", "Australia" },
				{ "NZ", "紐西蘭", "New Zealand" }
		}));

		continents.add(buildContinent("africa", new String[][] {
				{ "ZA", "南非", "South Africa" },
				{ "EG", "埃及", "Egypt" },
				{ "NG", "奈及利亞", "Nigeria" },
				{ "KE", "肯亞", "Kenya" },
				{ "GH", "迦納", "Ghana" },
				{ "ET", "衣索比亞", "Ethiopia" }
		}));

		return renderSuccess(continents);
	}

	private Map<String, Object> buildContinent(String key, String[][] countryData) {
		Map<String, Object> continent = new HashMap<>();
		continent.put("key", key);

		List<Map<String, String>> countries = new ArrayList<>();
		for (String[] c : countryData) {
			Map<String, String> country = new HashMap<>();
			country.put("code", c[0]);
			country.put("nameZh", c[1]);
			country.put("nameEn", c[2]);
			countries.add(country);
		}
		continent.put("countries", countries);

		return continent;
	}

}
