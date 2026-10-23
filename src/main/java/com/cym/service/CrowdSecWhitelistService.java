package com.cym.service;

import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.IntSupplier;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Init;
import org.noear.solon.annotation.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cym.model.DenyAllow;
import com.cym.sqlhelper.utils.SqlHelper;
import com.cym.utils.IpMatch;

/**
 * 「加入白名單」：刪除該 IP 的 CrowdSec decisions，並一律寫入 nginxWebUI 白名單（DenyAllow type=allow）。
 * 不再新增 type=whitelist 的 decision；舊版留下的由 cleanupLegacyWhitelistDecisions 清掉一次。
 */
@Component
public class CrowdSecWhitelistService {
	private static final Logger logger = LoggerFactory.getLogger(CrowdSecWhitelistService.class);
	public static final String LEGACY_FLAG = "crowdsecWhitelistDecisionsCleared20261023";

	@Inject
	SqlHelper sqlHelper;
	@Inject
	DenyAllowService denyAllowService;
	@Inject
	OperateLogService operateLogService;
	@Inject
	SettingService settingService;
	@Inject
	CrowdSecClient crowdSecClient;

	BooleanSupplier crowdsecReady;
	Function<String, CrowdSecClient.DeleteResult> deleteIp;
	IntSupplier deleteLegacy;

	@Init
	public void init() {
		crowdsecReady = () -> crowdSecClient.isConfigured() && crowdSecClient.canRead();
		deleteIp = ip -> crowdSecClient.deleteIpDecisions(ip);
		deleteLegacy = () -> crowdSecClient.deleteLegacyWhitelistDecisions();
	}

	/** denyAllow：added／exists；crowdsec：ok／notConfigured／failed（error 為錯誤碼） */
	public record Outcome(String denyAllow, String crowdsec, int deleted, int rangesKept, String error) {
	}

	public Outcome whitelist(String ip, String adminName) {
		if (!IpMatch.isSingleIp(ip)) {
			throw new IllegalArgumentException("invalid_ip");
		}
		String target = ip.trim();
		String crowdsec = "notConfigured";
		int deleted = 0;
		int ranges = 0;
		String error = null;
		if (crowdsecReady.getAsBoolean()) {
			try {
				CrowdSecClient.DeleteResult r = deleteIp.apply(target);
				deleted = r.deleted();
				ranges = r.rangesKept();
				crowdsec = "ok";
			} catch (RuntimeException e) {
				crowdsec = "failed";
				error = e instanceof CrowdSecClient.MachineAuthException ? CrowdSecClient.MachineAuthException.CODE
						: e instanceof CrowdSecClient.LapiUnavailableException ? CrowdSecClient.LapiUnavailableException.CODE
						: "crowdsec_error";
				logger.warn("whitelist {}: crowdsec delete failed: {}", target, e.getMessage());
			}
		}
		String denyAllow = alreadyAllowed(target) ? "exists" : addAllow(target);
		operateLogService.addLog("", "[CrowdSec] whitelist " + target + " (decisions deleted: " + deleted
				+ ", ranges kept: " + ranges + ", allowlist: " + denyAllow + ", crowdsec: " + crowdsec + ")", adminName);
		return new Outcome(denyAllow, crowdsec, deleted, ranges, error);
	}

	private boolean alreadyAllowed(String ip) {
		for (DenyAllow a : denyAllowService.listByType("allow")) {
			if (a.getIp() == null) {
				continue;
			}
			for (String line : a.getIp().split("\n")) {
				if (IpMatch.contains(line.trim(), ip)) {
					return true;
				}
			}
		}
		return false;
	}

	private String addAllow(String ip) {
		DenyAllow da = new DenyAllow();
		da.setName("CrowdSec FP " + ip);
		da.setIp(ip);
		da.setType("allow");
		sqlHelper.insertOrUpdate(da);
		return "added";
	}

	/** 清除舊 whitelist decision 一次；成功後設 flag，之後不再呼叫 LAPI。回 true 表示已完成 */
	public boolean cleanupLegacyWhitelistDecisions() {
		if ("1".equals(settingService.get(LEGACY_FLAG))) {
			return true;
		}
		try {
			int n = deleteLegacy.getAsInt();
			settingService.set(LEGACY_FLAG, "1");
			logger.info("Migration: deleted {} legacy nginxwebui whitelist decision(s)", n);
			return true;
		} catch (RuntimeException e) {
			logger.debug("legacy whitelist cleanup deferred: {}", e.getMessage());
			return false;
		}
	}
}
