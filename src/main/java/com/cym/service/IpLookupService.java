package com.cym.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Init;
import org.noear.solon.annotation.Inject;

import com.cym.model.DenyAllow;
import com.cym.model.GeoRule;
import com.cym.model.Server;
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
	@Inject
	ServerService serverService;
	@Inject
	CrowdSecNginxConf crowdSecNginxConf;

	Function<String, GeoLookup.Country> countryOf;
	Function<String, Optional<GeoLookup.Asn>> asnOf;
	BooleanSupplier canReadCrowdSec;
	Function<String, String> decisionsOf;
	/** nginx 是否會載入 CrowdSec bouncer（與 ConfService 產生 bouncer 設定的判斷相同） */
	BooleanSupplier bouncerActive;
	/** HTTP 站台，順序與 ConfService 產生設定時相同 */
	Supplier<List<Server>> httpServers;

	@Init
	public void init() {
		countryOf = ip -> geoLookup.country(ip);
		asnOf = ip -> geoLookup.asn(ip);
		canReadCrowdSec = () -> crowdSecClient.canRead();
		decisionsOf = ip -> crowdSecClient.decisionsForIpRaw(ip);
		bouncerActive = () -> crowdSecNginxConf.isActive();
		httpServers = () -> serverService.getListByProxyType(new String[] { "0" });
	}

	/** state：match／none（名單）、blocked／allowed／unknown（國家）、blocked／notEnforced／none／unavailable（CrowdSec）、skipped */
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

	/** 只算 nginx 實際會寫進設定的行：與 ConfService.collectIps 相同，all、非法 token 與 /0 都略過 */
	private List<String> matchingLists(String type, String ip) {
		List<String> names = new ArrayList<>();
		for (DenyAllow da : denyAllowService.listByType(type)) {
			if (StrUtil.isBlank(da.getIp())) {
				continue;
			}
			for (String line : da.getIp().split("\n")) {
				String entry = line.trim();
				if (ConfService.isValidIpOrCidr(entry) && IpMatch.contains(entry, ip)) {
					names.add(da.getName());
					break;
				}
			}
		}
		return names;
	}

	/**
	 * 逐一計算每個啟用的 HTTP 站台，規則選法與 ConfService 產生 server block 時相同（{@link ConfService#effectiveGeoRule}）：
	 * 有站台專屬規則用它，否則用全域規則；選到的規則沒有國家時該站台不做國家封鎖。
	 * details 每筆一個站台：scope（站台 id）、source（server／global／none）、mode、hasCountries、blocked（判斷不了時不帶）。
	 * 資料庫有、但查不到這個 IP 的國家時，國家代碼視為空字串（nginx 的 $geoip2_data_country_code 為空），
	 * 不在任何清單內：白名單模式（mode 0）封鎖、黑名單模式放行。
	 * 整層：任一站台封鎖為 blocked；資料庫不可用且有站台套用了有國家的規則為 unknown；其他（含沒有任何站台）為 allowed。
	 */
	private Layer geoLayer(GeoLookup.Country geoCountry) {
		List<Map<String, Object>> details = new ArrayList<>();
		boolean blocked = false;
		boolean undecided = false;
		String country = geoCountry.code() == null ? "" : geoCountry.code();
		for (Server server : httpServers.get()) {
			if (server.getEnable() == null || !server.getEnable()) {
				continue;
			}
			GeoRule rule = ConfService.effectiveGeoRule(sqlHelper, server.getId());
			boolean hasCountries = rule != null && StrUtil.isNotEmpty(rule.getCountries());
			Map<String, Object> d = new LinkedHashMap<>();
			d.put("scope", server.getId());
			d.put("source", rule == null ? "none" : rule.getServerId() == null ? "global" : "server");
			if (rule != null) {
				d.put("mode", rule.getMode());
			}
			d.put("hasCountries", hasCountries);
			if (!hasCountries) {
				d.put("blocked", false);
			} else if (!geoCountry.available()) {
				undecided = true;
			} else {
				boolean siteBlocks = ruleBlocks(rule, country);
				blocked |= siteBlocks;
				d.put("blocked", siteBlocks);
			}
			details.add(d);
		}
		String state = blocked ? "blocked" : undecided ? "unknown" : "allowed";
		return new Layer("geo", state, List.of(), details, List.of(), null);
	}

	/** 與 ConfService.buildGeoMaps 的 map 相同：白名單模式（mode 0）列出的放行、其餘封鎖；黑名單模式列出的封鎖 */
	private static boolean ruleBlocks(GeoRule rule, String country) {
		boolean listed = false;
		for (String c : rule.getCountries().split(",")) {
			String code = c.trim();
			listed |= !code.isEmpty() && code.equalsIgnoreCase(country);
		}
		return rule.getMode() != null && rule.getMode() == 0 ? !listed : listed;
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
			// 有 ban 但 nginx 沒有載入 bouncer（缺 Lua 模組或包裝檔）：照列 decision，標為 notEnforced，不算擋下
			String state = !banned ? "none" : bouncerActive.getAsBoolean() ? "blocked" : "notEnforced";
			return new Layer("crowdsec", state, List.of(), List.of(), decisions, null);
		} catch (CrowdSecClient.LapiUnavailableException e) {
			return new Layer("crowdsec", "unavailable", List.of(), List.of(), List.of(), CrowdSecClient.LapiUnavailableException.CODE);
		} catch (RuntimeException e) {
			return new Layer("crowdsec", "unavailable", List.of(), List.of(), List.of(), "crowdsec_error");
		}
	}
}
