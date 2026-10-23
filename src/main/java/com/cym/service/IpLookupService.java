package com.cym.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Init;
import org.noear.solon.annotation.Inject;

import com.cym.model.DenyAllow;
import com.cym.model.GeoRule;
import com.cym.sqlhelper.utils.SqlHelper;
import com.cym.utils.IpMatch;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;

/**
 * 一個 IP 依 nginx 實際判斷順序的四層結果：白名單 → 國家封鎖 → 黑名單 → CrowdSec 封鎖。
 * 白名單命中時其餘三層標為 skipped（nginx 端各層都先看 $nwu_allowlist）。
 */
@Component
public class IpLookupService {
	@Inject
	SqlHelper sqlHelper;
	@Inject
	DenyAllowService denyAllowService;
	@Inject
	GeoLookup geoLookup;
	@Inject
	CrowdSecClient crowdSecClient;

	Function<String, GeoLookup.Country> countryOf;
	Function<String, Optional<GeoLookup.Asn>> asnOf;
	BooleanSupplier canReadCrowdSec;
	Function<String, String> decisionsOf;

	@Init
	public void init() {
		countryOf = ip -> geoLookup.country(ip);
		asnOf = ip -> geoLookup.asn(ip);
		canReadCrowdSec = () -> crowdSecClient.canRead();
		decisionsOf = ip -> crowdSecClient.decisionsForIpRaw(ip);
	}

	/** state：match／none（名單）、blocked／allowed／unknown（國家）、blocked／none／unavailable（CrowdSec）、skipped */
	public record Layer(String layer, String state, List<String> lists, List<Map<String, Object>> details,
			List<Map<String, Object>> decisions, String reason) {
	}

	/** blockedBy：第一個擋下的層（allowlist 表示白名單放行）、none，或 undetermined（沒有層擋下，但有層查不到結果） */
	public record Result(String ip, String country, GeoLookup.Asn asn, List<Layer> layers, String blockedBy) {
	}

	public Result lookup(String ip) {
		if (!IpMatch.isSingleIp(ip)) {
			throw new IllegalArgumentException("invalid_ip");
		}
		String target = ip.trim();
		GeoLookup.Country geoCountry = countryOf.apply(target);
		String country = geoCountry.code();
		GeoLookup.Asn asn = asnOf.apply(target).orElse(null);

		List<String> allowLists = matchingLists("allow", target);
		boolean allowlisted = !allowLists.isEmpty();
		List<Layer> layers = new ArrayList<>();
		layers.add(new Layer("allowlist", allowlisted ? "match" : "none", allowLists, List.of(), List.of(), null));
		if (allowlisted) {
			layers.add(skipped("geo"));
			layers.add(skipped("denylist"));
			layers.add(skipped("crowdsec"));
			return new Result(target, country, asn, layers, "allowlist");
		}
		layers.add(geoLayer(geoCountry));
		List<String> denyLists = matchingLists("deny", target);
		layers.add(new Layer("denylist", denyLists.isEmpty() ? "none" : "match", denyLists, List.of(), List.of(), null));
		layers.add(crowdsecLayer(target));

		String blockedBy = "none";
		boolean undetermined = false;
		for (Layer l : layers) {
			if ("blocked".equals(l.state()) || ("denylist".equals(l.layer()) && "match".equals(l.state()))) {
				blockedBy = l.layer();
				break;
			}
			// 沒設 bouncer 金鑰時 nginx 不載入 bouncer，CrowdSec 不可能擋人，不算查不到
			boolean bouncerNotConfigured = "crowdsec".equals(l.layer())
					&& CrowdSecClient.NotConfiguredException.BOUNCER_KEY.equals(l.reason());
			undetermined |= "unknown".equals(l.state()) || ("unavailable".equals(l.state()) && !bouncerNotConfigured);
		}
		if ("none".equals(blockedBy) && undetermined) {
			blockedBy = "undetermined";
		}
		return new Result(target, country, asn, layers, blockedBy);
	}

	private static Layer skipped(String name) {
		return new Layer(name, "skipped", List.of(), List.of(), List.of(), null);
	}

	private List<String> matchingLists(String type, String ip) {
		List<String> names = new ArrayList<>();
		for (DenyAllow da : denyAllowService.listByType(type)) {
			if (StrUtil.isBlank(da.getIp())) {
				continue;
			}
			for (String line : da.getIp().split("\n")) {
				if (IpMatch.contains(line.trim(), ip)) {
					names.add(da.getName());
					break;
				}
			}
		}
		return names;
	}

	/**
	 * 與 ConfService 相同：有 server 專屬規則用它，否則用全域規則；每條啟用的規則各列一筆。
	 * 資料庫有、但查不到這個 IP 的國家時，國家代碼視為空字串（nginx 的 $geoip2_data_country_code 為空），
	 * 不在任何清單內：白名單模式（mode 0）封鎖、黑名單模式放行。只有資料庫不可用才是 unknown。
	 */
	private Layer geoLayer(GeoLookup.Country geoCountry) {
		List<Map<String, Object>> details = new ArrayList<>();
		boolean anyRule = false;
		boolean blocked = false;
		String country = geoCountry.code() == null ? "" : geoCountry.code();
		for (GeoRule rule : sqlHelper.findAll(GeoRule.class)) {
			if (rule.getEnable() == null || !rule.getEnable() || StrUtil.isEmpty(rule.getCountries())) {
				continue;
			}
			anyRule = true;
			if (!geoCountry.available()) {
				continue;
			}
			boolean listed = false;
			for (String c : rule.getCountries().split(",")) {
				String code = c.trim();
				listed |= !code.isEmpty() && code.equalsIgnoreCase(country);
			}
			boolean ruleBlocks = rule.getMode() != null && rule.getMode() == 0 ? !listed : listed;
			blocked |= ruleBlocks;
			Map<String, Object> d = new LinkedHashMap<>();
			d.put("scope", StrUtil.isEmpty(rule.getServerId()) ? "global" : rule.getServerId());
			d.put("mode", rule.getMode());
			d.put("blocked", ruleBlocks);
			details.add(d);
		}
		String state = !anyRule ? "allowed" : !geoCountry.available() ? "unknown" : blocked ? "blocked" : "allowed";
		return new Layer("geo", state, List.of(), details, List.of(), null);
	}

	private Layer crowdsecLayer(String ip) {
		if (!canReadCrowdSec.getAsBoolean()) {
			return new Layer("crowdsec", "unavailable", List.of(), List.of(), List.of(),
					CrowdSecClient.NotConfiguredException.BOUNCER_KEY);
		}
		try {
			JSONArray arr = CrowdSecClient.parseArray(decisionsOf.apply(ip));
			List<Map<String, Object>> decisions = new ArrayList<>();
			for (int i = 0; i < arr.size(); i++) {
				JSONObject o = arr.getJSONObject(i);
				if (o != null) {
					decisions.add(new LinkedHashMap<>(o));
				}
			}
			// bouncer 只對 ban 類型的 decision 斷線；其他類型（captcha、舊版 whitelist）照列但不判定擋下
			boolean banned = decisions.stream().anyMatch(d -> "ban".equalsIgnoreCase(String.valueOf(d.get("type"))));
			return new Layer("crowdsec", banned ? "blocked" : "none", List.of(), List.of(), decisions, null);
		} catch (CrowdSecClient.LapiUnavailableException e) {
			return new Layer("crowdsec", "unavailable", List.of(), List.of(), List.of(), CrowdSecClient.LapiUnavailableException.CODE);
		} catch (RuntimeException e) {
			return new Layer("crowdsec", "unavailable", List.of(), List.of(), List.of(), "crowdsec_error");
		}
	}
}
