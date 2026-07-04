package com.cym.service;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cym.config.HomeConfig;
import com.cym.ext.AsycPack;
import com.cym.ext.ConfExt;
import com.cym.ext.ConfFile;
import com.cym.model.AsnRule;
import com.cym.model.Bak;
import com.cym.model.BakSub;
import com.cym.model.Basic;
import com.cym.model.Cert;
import com.cym.model.CertCode;
import com.cym.model.DenyAllow;
import com.cym.model.GeoRule;
import com.cym.model.Http;
import com.cym.model.Location;
import com.cym.model.Param;
import com.cym.model.Password;
import com.cym.model.Server;
import com.cym.model.Stream;
import com.cym.model.Template;
import com.cym.model.Upstream;
import com.cym.model.UpstreamServer;
import com.cym.sqlhelper.bean.Sort;
import com.cym.sqlhelper.bean.Sort.Direction;
import com.cym.sqlhelper.utils.ConditionAndWrapper;
import com.cym.sqlhelper.utils.SqlHelper;
import com.cym.utils.SystemTool;
import com.cym.utils.TelnetUtils;
import com.cym.utils.ToolUtils;
import com.github.odiszapc.nginxparser.NgxBlock;
import com.github.odiszapc.nginxparser.NgxConfig;
import com.github.odiszapc.nginxparser.NgxDumper;
import com.github.odiszapc.nginxparser.NgxEntry;
import com.github.odiszapc.nginxparser.NgxParam;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ClassPathResource;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.RuntimeUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.ZipUtil;

@Component
public class ConfService {
	Logger logger = LoggerFactory.getLogger(this.getClass());
	@Inject
	UpstreamService upstreamService;
	@Inject
	SettingService settingService;
	@Inject
	ServerService serverService;
	@Inject
	LocationService locationService;
	@Inject
	ParamService paramService;
	@Inject
	SqlHelper sqlHelper;
	@Inject
	TemplateService templateService;
	@Inject
	OperateLogService operateLogService;
	@Inject
	HomeConfig homeConfig;
	@Inject
	CertService certService;
	@Inject
	DenyAllowService denyAllowService;
	@Inject
	NginxService nginxService;

	public synchronized ConfExt buildConf(Boolean decompose, Boolean check) {
		ConfExt confExt = new ConfExt();
		confExt.setFileList(new ArrayList<>());

//		String nginxPath = settingService.get("nginxPath");
//		if (check) {
//			nginxPath = homeConfig.home + "temp/nginx.conf";
//		}
		try {

			NgxConfig ngxConfig = new NgxConfig();

			// 載入資料庫中已啟用且磁碟上存在的模組（Linux 環境）
			if (SystemTool.isLinux()) {
				List<String> modulePaths = nginxService.getEnabledModulePaths();
				for (String path : modulePaths) {
					NgxParam ngxParam = new NgxParam();
					ngxParam.addValue("load_module " + path);
					ngxConfig.addEntry(ngxParam);
				}
			}

			// 获取基本参数（跳過 load_module，已由自動偵測處理）
			List<Basic> basicList = sqlHelper.findAll(new Sort("seq", Direction.ASC), Basic.class);
			for (Basic basic : basicList) {
				if ("load_module".equals(basic.getName().trim())) {
					continue; // 跳過，由自動偵測處理
				}
				NgxParam ngxParam = new NgxParam();
				ngxParam.addValue(basic.getName().trim() + " " + basic.getValue().trim());
				ngxConfig.addEntry(ngxParam);
			}

			// 获取http
			List<Http> httpList = sqlHelper.findAll(new Sort("seq", Direction.ASC), Http.class);
			boolean hasHttp = false;
			NgxBlock ngxBlockHttp = new NgxBlock();
			ngxBlockHttp.addValue("http");
			for (Http http : httpList) {
				if (http.getEnable() == null || !http.getEnable()) {
					continue;
				}

				// 跳過靜態 ASN map 條目（改由 AsnRule 表動態產生）
				if ("map".equals(http.getName()) && http.getValue() != null && http.getValue().contains("geoip2_data_asn")) {
					continue;
				}

				NgxParam ngxParam = new NgxParam();
				ngxParam.addValue(http.getName().trim() + " " + http.getValue().trim());
				ngxBlockHttp.addEntry(ngxParam);

				hasHttp = true;
			}

			// 自動補齊缺失的 shared memory zone 定義
			// 當 server/location 使用了 limit_conn 或 limit_req，但 http 區塊沒有對應的 zone 定義時自動注入
			autoInjectMissingZones(httpList, ngxBlockHttp);

			// 黑白名单
			buildDenyAllow(ngxBlockHttp, "http", "http", confExt);

			// 國家存取控制 — map 指令放在 http block
			List<GeoRule> geoRules = sqlHelper.findAll(GeoRule.class);
			for (GeoRule rule : geoRules) {
				if (rule.getEnable() == null || !rule.getEnable() || StrUtil.isEmpty(rule.getCountries())) {
					continue;
				}

				// 生成唯一的 map 變數名
				String idPart = StrUtil.isEmpty(rule.getServerId()) ? "global" : rule.getServerId().replace("-", "");
				if (idPart.length() > 12) idPart = idPart.substring(0, 12);
				String mapVarName = "geo_block_" + idPart;

				// 建立 map block: map $geoip2_data_country_code $geo_block_xxx { ... }
				NgxBlock mapBlock = new NgxBlock();
				mapBlock.addValue("map $geoip2_data_country_code $" + mapVarName);

				// default 值
				NgxParam defaultParam = new NgxParam();
				if (rule.getMode() == 0) {
					// 白名單：預設封鎖(1)，列出的允許(0)
					defaultParam.addValue("default 1");
				} else {
					// 黑名單：預設允許(0)，列出的封鎖(1)
					defaultParam.addValue("default 0");
				}
				mapBlock.addEntry(defaultParam);

				// 國家代碼
				String[] codes = rule.getCountries().split(",");
				for (String code : codes) {
					NgxParam codeParam = new NgxParam();
					if (rule.getMode() == 0) {
						codeParam.addValue(code.trim() + " 0");
					} else {
						codeParam.addValue(code.trim() + " 1");
					}
					mapBlock.addEntry(codeParam);
				}

				ngxBlockHttp.addEntry(mapBlock);
				hasHttp = true;
			}

			// ASN 封鎖 — 從 AsnRule 表動態產生 map
			List<AsnRule> asnRules = sqlHelper.findAll(AsnRule.class);
			boolean hasEnabledAsn = false;
			NgxBlock asnMapBlock = new NgxBlock();
			asnMapBlock.addValue("map $geoip2_data_asn $blocked_asn");
			NgxParam asnDefault = new NgxParam();
			asnDefault.addValue("default 0");
			asnMapBlock.addEntry(asnDefault);
			for (AsnRule asnRule : asnRules) {
				if (asnRule.getEnable() != null && asnRule.getEnable() && StrUtil.isNotBlank(asnRule.getAsn())) {
					NgxParam asnParam = new NgxParam();
					asnParam.addValue(asnRule.getAsn().trim() + " 1");
					asnMapBlock.addEntry(asnParam);
					hasEnabledAsn = true;
				}
			}
			if (hasEnabledAsn) {
				ngxBlockHttp.addEntry(asnMapBlock);
				hasHttp = true;
			}

			// 添加upstream
			NgxParam ngxParam;
			List<Upstream> upstreams = upstreamService.getListByProxyType(0);

			for (Upstream upstream : upstreams) {
				NgxBlock ngxBlockServer = new NgxBlock();
				ngxBlockServer.addValue("upstream " + upstream.getName().trim());

				if (StrUtil.isNotEmpty(upstream.getDescr())) {
					String[] descrs = upstream.getDescr().split("\n");
					for (String d : descrs) {
						ngxParam = new NgxParam();
						ngxParam.addValue("# " + d);
						ngxBlockServer.addEntry(ngxParam);
					}

				}

				if (StrUtil.isNotEmpty(upstream.getTactics())) {
					ngxParam = new NgxParam();
					ngxParam.addValue(upstream.getTactics());
					ngxBlockServer.addEntry(ngxParam);
				}

				// 自定义参数 - 前置模式 (position=1)
				List<Param> paramList = paramService.getListByTypeId(upstream.getId(), "upstream");
				for (Param param : paramList) {
					if (param.getPosition() != null && param.getPosition() == 1) {
						setSameParam(param, ngxBlockServer);
					}
				}

				List<UpstreamServer> upstreamServers = upstreamService.getUpstreamServers(upstream.getId());
				for (UpstreamServer upstreamServer : upstreamServers) {
					if (upstreamServer.getEnable()  == 1) {
						ngxParam = new NgxParam();
						ngxParam.addValue("server " + buildNodeStr(upstreamServer));
						ngxBlockServer.addEntry(ngxParam);
					}
				}

				// 自定义参数 - 追加模式 (position=0 或 null，默认)
				for (Param param : paramList) {
					if (param.getPosition() == null || param.getPosition() == 0) {
						setSameParam(param, ngxBlockServer);
					}
				}

				hasHttp = true;

				if (decompose) {
					String filename = addConfFile(confExt, "upstreams." + upstream.getName() + ".conf", ngxBlockServer);

					ngxParam = new NgxParam();
					ngxParam.addValue("include " + filename);
					ngxBlockHttp.addEntry(ngxParam);

				} else {
					ngxBlockHttp.addEntry(ngxBlockServer);
				}

			}

			// 添加server
			List<Server> servers = serverService.getListByProxyType(new String[] { "0" });
			for (Server server : servers) {
				if (server.getEnable() == null || !server.getEnable()) {
					continue;
				}

				NgxBlock ngxBlockServer = bulidBlockServer(server, confExt);
				hasHttp = true;

				// 是否需要分解
				if (decompose) {
					String name = "all";

					if (StrUtil.isNotEmpty(server.getServerName())) {
						name = server.getServerName();
					}

					String filename = addConfFile(confExt, name + ".conf", ngxBlockServer);

					ngxParam = new NgxParam();
					ngxParam.addValue("include " + filename);

					if (noContain(ngxBlockHttp, ngxParam)) {
						ngxBlockHttp.addEntry(ngxParam);
					}

				} else {
					ngxBlockHttp.addEntry(ngxBlockServer);
				}

			}
			if (hasHttp) {
				ngxConfig.addEntry(ngxBlockHttp);
			}

			// TCP/UDP转发
			// 创建stream
			List<Stream> streamList = sqlHelper.findAll(new Sort("seq", Direction.ASC), Stream.class);
			boolean hasStream = false;
			NgxBlock ngxBlockStream = new NgxBlock();
			ngxBlockStream.addValue("stream");
			for (Stream stream : streamList) {
				ngxParam = new NgxParam();
				ngxParam.addValue(stream.getName() + " " + stream.getValue());
				ngxBlockStream.addEntry(ngxParam);

				hasStream = true;
			}

			// 黑白名单
			buildDenyAllow(ngxBlockStream, "stream", "stream", confExt);

			// 添加upstream
			upstreams = upstreamService.getListByProxyType(1);
			for (Upstream upstream : upstreams) {
				NgxBlock ngxBlockServer = buildBlockUpstream(upstream);

				if (decompose) {
					String filename = addConfFile(confExt, "upstreams." + upstream.getName() + ".conf", ngxBlockServer);

					ngxParam = new NgxParam();
					ngxParam.addValue("include " + filename);
					ngxBlockStream.addEntry(ngxParam);
				} else {
					ngxBlockStream.addEntry(ngxBlockServer);
				}

				hasStream = true;
			}

			// 添加server
			servers = serverService.getListByProxyType(new String[] { "1", "2" });
			for (Server server : servers) {
				if (server.getEnable() == null || !server.getEnable()) {
					continue;
				}

				NgxBlock ngxBlockServer = bulidBlockServer(server, confExt);

				if (decompose) {
					String type = "";
					if (server.getProxyType() == 0) {
						type = "http";
					} else if (server.getProxyType() == 1) {
						type = "tcp";
					} else if (server.getProxyType() == 2) {
						type = "udp";
					}

					String filename = addConfFile(confExt, type + "." + server.getListen() + ".conf", ngxBlockServer);

					ngxParam = new NgxParam();
					ngxParam.addValue("include " + filename);
					ngxBlockStream.addEntry(ngxParam);
				} else {
					ngxBlockStream.addEntry(ngxBlockServer);
				}

				hasStream = true;
			}

			if (hasStream) {
				ngxConfig.addEntry(ngxBlockStream);
			}

			String conf = ToolUtils.formatConf(ToolUtils.handleConf(new NgxDumper(ngxConfig).dump()));
			// 将多个;替换成单一;
			while (conf.contains(";;")) {
				conf = conf.replaceAll(";;", ";");
			}

			confExt.setConf(conf);

			// fileList 排序
			Collections.sort(confExt.getFileList(), new Comparator<ConfFile>() {

				@Override
				public int compare(ConfFile o1, ConfFile o2) {
					return o1.getName().compareTo(o2.getName());
				}

			});

			return confExt;
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}

		return null;
	}

	/**
	 * 用「目前 DB 狀態」build 出 nginx.conf，寫臨時檔後跑 nginx -t 語法預檢。
	 * @return null=預檢通過；"SKIPPED"=nginxExe 未設定，略過；其他字串=nginx -t 錯誤訊息。
	 */
	public synchronized String precheckConf() {
		String nginxExe = ToolUtils.handleConf(settingService.get("nginxExe"));
		if (StrUtil.isEmpty(nginxExe)) {
			return "SKIPPED";
		}
		String nginxDir = ToolUtils.handleConf(settingService.get("nginxDir"));

		String decompose = settingService.get("decompose");
		boolean decomposeFlag = StrUtil.isNotEmpty(decompose) && decompose.equals("true");
		ConfExt confExt = buildConf(decomposeFlag, false);
		if (confExt == null) {
			return "buildConf failed";
		}

		// 寫臨時檔（與 ConfController.check 同路徑/同 replace 流程，isReplace=false 不備份）
		FileUtil.del(homeConfig.home + "temp");
		String fileTemp = homeConfig.home + "temp/nginx.conf";
		List<String> subContent = new ArrayList<>();
		List<String> subName = new ArrayList<>();
		for (ConfFile cf : confExt.getFileList()) {
			subContent.add(cf.getConf());
			subName.add(cf.getName());
		}
		replace(fileTemp, confExt.getConf(), subContent, subName, false, null);

		String cmd = nginxExe + " -t -c " + fileTemp;
		if (StrUtil.isNotEmpty(nginxDir)) {
			cmd += " -p " + nginxDir;
		}
		String rs;
		try {
			rs = RuntimeUtil.execForStr(cmd);
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			return e.getMessage();
		}
		return rs.contains("test is successful") ? null : rs;
	}

	/**
	 * 自動補齊缺失的 shared memory zone 定義。
	 * 當 server/location params 使用了 limit_conn 或 limit_req，
	 * 但 http 區塊沒有對應的 limit_conn_zone / limit_req_zone 時，自動注入預設值。
	 */
	private void autoInjectMissingZones(List<Http> httpList, NgxBlock ngxBlockHttp) {
		// 檢查已啟用的 http 參數中是否已有 zone 定義
		boolean hasConnZone = false;
		boolean hasReqZone = false;
		for (Http http : httpList) {
			if (http.getEnable() != null && http.getEnable()) {
				if ("limit_conn_zone".equals(http.getName().trim())) {
					hasConnZone = true;
				}
				if ("limit_req_zone".equals(http.getName().trim())) {
					hasReqZone = true;
				}
			}
		}

		// 檢查 server/location params 是否引用了 limit_conn 或 limit_req
		if (!hasConnZone) {
			ConditionAndWrapper cond = new ConditionAndWrapper();
			cond.eq("name", "limit_conn");
			Long count = sqlHelper.findCountByQuery(cond, Param.class);
			if (count > 0) {
				NgxParam ngxParam = new NgxParam();
				ngxParam.addValue("limit_conn_zone $binary_remote_addr zone=conn_limit:10m");
				ngxBlockHttp.addEntry(ngxParam);
			}
		}

		if (!hasReqZone) {
			ConditionAndWrapper cond = new ConditionAndWrapper();
			cond.eq("name", "limit_req");
			Long count = sqlHelper.findCountByQuery(cond, Param.class);
			if (count > 0) {
				NgxParam ngxParam = new NgxParam();
				ngxParam.addValue("limit_req_zone $binary_remote_addr zone=req_limit:10m rate=10r/s");
				ngxBlockHttp.addEntry(ngxParam);
			}
		}
	}

	/**
	 * 收集 csvIds 對應的所有 DenyAllow 清單 IP（"id1,id2,id3" → 三個清單的 IP 合併）。
	 * single id 也是合法 CSV (split 後 array 只一個 element)、舊資料不需 migration。
	 */
	private List<String> collectIpsFromCsvIds(String csvIds) {
		List<String> ips = new ArrayList<>();
		if (csvIds == null || csvIds.trim().isEmpty()) {
			return ips;
		}
		for (String id : csvIds.split(",")) {
			String trimmedId = id.trim();
			if (trimmedId.isEmpty()) {
				continue;
			}
			DenyAllow da = sqlHelper.findById(trimmedId, DenyAllow.class);
			if (da == null || da.getIp() == null || da.getIp().isEmpty()) {
				continue;
			}
			for (String ipLine : da.getIp().split("\n")) {
				String ip = ipLine.trim();
				if (!ip.isEmpty()) {
					ips.add(ip);
				}
			}
		}
		return ips;
	}

	public void buildDenyAllow(NgxBlock ngxBlock, String type, String id, ConfExt confExt) {
		Integer denyAllowValue = null;
		String denyId = null;
		String allowId = null;

		if (type.equals("http")) {
			denyAllowValue = Integer.parseInt(settingService.get("denyAllow"));
			denyId = settingService.get("denyId");
			allowId = settingService.get("allowId");
		} else if (type.equals("stream")) {
			denyAllowValue = Integer.parseInt(settingService.get("denyAllowStream"));
			denyId = settingService.get("denyIdStream");
			allowId = settingService.get("allowIdStream");
		} else if (type.equals("server")) {
			Server server = sqlHelper.findById(id, Server.class);
			denyAllowValue = server.getDenyAllow();
			denyId = server.getDenyId();
			allowId = server.getAllowId();
		}

		List<String> strs = new ArrayList<>();
		if (denyAllowValue == 1) {
			// 黑名单 — denyId 可能是 CSV "id1,id2,id3"，loop 蒐集所有清單的 IP
			for (String ip : collectIpsFromCsvIds(denyId)) {
				strs.add("deny " + ip + ";");
			}
			strs.add("allow all;");
		}
		if (denyAllowValue == 2) {
			// 白名单 — allowId 同樣 CSV
			for (String ip : collectIpsFromCsvIds(allowId)) {
				strs.add("allow " + ip + ";");
			}
			strs.add("deny all;");
		}

		if (denyAllowValue == 3) {
			// 黑白名单 — 同時處理 allow 與 deny 兩個 CSV
			for (String ip : collectIpsFromCsvIds(allowId)) {
				strs.add("allow " + ip + ";");
			}
			for (String ip : collectIpsFromCsvIds(denyId)) {
				strs.add("deny " + ip + ";");
			}
		}

		if (denyAllowValue != 0) {
			String filename = addConfFile(confExt, "deny_" + id + ".conf", strs);
			NgxParam ngxParam = new NgxParam();
			ngxParam.addValue("include " + filename);
			ngxBlock.addEntry(ngxParam);
		}
	}

	public NgxBlock buildBlockUpstream(Upstream upstream) {
		NgxParam ngxParam = null;

		NgxBlock ngxBlockServer = new NgxBlock();

		ngxBlockServer.addValue("upstream " + upstream.getName());

		if (StrUtil.isNotEmpty(upstream.getDescr())) {
			String[] descrs = upstream.getDescr().split("\n");
			for (String d : descrs) {
				ngxParam = new NgxParam();
				ngxParam.addValue("# " + d);
				ngxBlockServer.addEntry(ngxParam);
			}

		}

		if (StrUtil.isNotEmpty(upstream.getTactics())) {
			ngxParam = new NgxParam();
			ngxParam.addValue(upstream.getTactics());
			ngxBlockServer.addEntry(ngxParam);
		}

		List<UpstreamServer> upstreamServers = upstreamService.getUpstreamServers(upstream.getId());
		for (UpstreamServer upstreamServer : upstreamServers) {
			if (upstreamServer.getEnable() == 1) {
				ngxParam = new NgxParam();
				ngxParam.addValue("server " + buildNodeStr(upstreamServer));
				ngxBlockServer.addEntry(ngxParam);
			}
		}

		// 自定义参数
		List<Param> paramList = paramService.getListByTypeId(upstream.getId(), "upstream");
		for (Param param : paramList) {
			setSameParam(param, ngxBlockServer);
		}

		return ngxBlockServer;
	}

	public List<Integer> processPort(String listenString) {
		List<Integer> numbers = new ArrayList<>();

		// 使用逗号分割字符串
		String[] partsByComma = listenString.split(",");

		for (String part : partsByComma) {
			String[] range = part.split("-");
			if (range.length == 2) {
				// 处理为范围的情况
				int start = Integer.parseInt(range[0]);
				int end = Integer.parseInt(range[1]);

				if (start <= end) {
					for (int i = start; i <= end; i++) {
						numbers.add(i);
					}
				} else {
					for (int i = start; i >= end; i--) {
						numbers.add(i);
					}
				}
			} else {
				// 处理单个数字的情况
				int num = Integer.parseInt(part);
				numbers.add(num);
			}
		}
		return numbers;
	}

	public void httpListenPort(Server server, NgxBlock ngxBlockServer, Boolean isIpv6) {
		String host = null;
		List<Integer> ports = null;
		// 分离host和port
		if (server.getListen().contains(":")) {
			int lastColonIndex = server.getListen().lastIndexOf(":");
			host = server.getListen().substring(0, lastColonIndex);
			ports = processPort(server.getListen().substring(lastColonIndex + 1));
		} else {
			ports = processPort(server.getListen());
		}

		String listenKey = null;
		if (isIpv6) {
			listenKey = "listen [::]:";
		} else if (host != null) {
			listenKey = "listen " + host + ":";
		} else {
			listenKey = "listen ";
		}

		String value = "";
		for (Integer port : ports) {
			NgxParam ngxParam = new NgxParam();
			value = listenKey + port;
			if (server.getDef() == 1) {
				value += " default_server";
			}
			if (server.getProxyProtocol() == 1) {
				value += " proxy_protocol";
			}

			if (server.getSsl() == 1) {
				value += " ssl";
				if (server.getHttp2() == 1) { // http2旧版写法
					value += " http2";
				}
			}
			ngxParam.addValue(value);
			ngxBlockServer.addEntry(ngxParam);
		}

	}

	public void tcpListenPort(Server server, NgxBlock ngxBlockServer, Boolean isIpv6) {
		String host = null;
		List<Integer> ports = null;
		// 分离host和port
		if (server.getListen().contains(":")) {
			int lastColonIndex = server.getListen().lastIndexOf(":");
			host = server.getListen().substring(0, lastColonIndex);
			ports = processPort(server.getListen().substring(lastColonIndex + 1));
		} else {
			ports = processPort(server.getListen());
		}

		String listenKey = null;
		if (isIpv6) {
			listenKey = "listen [::]:";
		} else if (host != null) {
			listenKey = "listen " + host + ":";
		} else {
			listenKey = "listen ";
		}

		String value = "";
		for (Integer port : ports) {
			NgxParam ngxParam = new NgxParam();
			value = listenKey + port;
			if (server.getProxyProtocol() == 1) {
				value += " proxy_protocol";
			}
			if (server.getProxyType() == 2) {
				value += " udp";
			}
			if (server.getSsl() != null && server.getSsl() == 1) {
				value += " ssl";
			}
			ngxParam.addValue(value);
			ngxBlockServer.addEntry(ngxParam);
		}

	}

	public NgxBlock bulidBlockServer(Server server, ConfExt confExt) {
		NgxParam ngxParam = null;

		NgxBlock ngxBlockServer = new NgxBlock();
		if (server.getProxyType() == 0) {
			ngxBlockServer.addValue("server");

			if (StrUtil.isNotEmpty(server.getDescr())) {
				String[] descrs = server.getDescr().split("\n");
				for (String d : descrs) {
					ngxParam = new NgxParam();
					ngxParam.addValue("# " + d);
					ngxBlockServer.addEntry(ngxParam);
				}
			}

			// 监听域名
			if (StrUtil.isNotEmpty(server.getServerName())) {
				ngxParam = new NgxParam();
				ngxParam.addValue("server_name " + server.getServerName());
				ngxBlockServer.addEntry(ngxParam);
			}

			// 监听端口
			httpListenPort(server, ngxBlockServer, false);
			if (server.getIpv6() == 1) {
				httpListenPort(server, ngxBlockServer, true);
			}

			if (server.getSsl() == 1 && server.getHttp2() == 2) { // http2新版写法
				ngxParam = new NgxParam();
				ngxParam.addValue("http2 on");
				ngxBlockServer.addEntry(ngxParam);
			}

			// 密码配置
			if (StrUtil.isNotEmpty(server.getPasswordId())) {
				Password password = sqlHelper.findById(server.getPasswordId(), Password.class);

				if (password != null) {
					ngxParam = new NgxParam();
					ngxParam.addValue("auth_basic \"" + password.getDescr() + "\"");
					ngxBlockServer.addEntry(ngxParam);

					ngxParam = new NgxParam();
					ngxParam.addValue("auth_basic_user_file " + password.getPath());
					ngxBlockServer.addEntry(ngxParam);
				}
			}

			// ssl配置
			setServerSsl(server, ngxBlockServer);

			// IP黑白名单
			buildDenyAllow(ngxBlockServer, "server", server.getId(), confExt);

			// 國家存取控制 — if 指令放在 server block
			{
				// 先查 server 專屬規則
				ConditionAndWrapper geoCondition = new ConditionAndWrapper();
				geoCondition.eq("serverId", server.getId()).eq("enable", true);
				GeoRule geoRule = sqlHelper.findOneByQuery(geoCondition, GeoRule.class);

				if (geoRule == null) {
					// 再查全域規則
					ConditionAndWrapper globalCondition = new ConditionAndWrapper();
					globalCondition.isNull("serverId").eq("enable", true);
					geoRule = sqlHelper.findOneByQuery(globalCondition, GeoRule.class);
				}

				if (geoRule != null && StrUtil.isNotEmpty(geoRule.getCountries())) {
					String idPart = StrUtil.isEmpty(geoRule.getServerId()) ? "global" : geoRule.getServerId().replace("-", "");
					if (idPart.length() > 12) idPart = idPart.substring(0, 12);
					String mapVarName = "geo_block_" + idPart;

					NgxBlock ifBlock = new NgxBlock();
					ifBlock.addValue("if ($" + mapVarName + " = 1)");
					NgxParam returnParam = new NgxParam();
					returnParam.addValue("return 403");
					ifBlock.addEntry(returnParam);
					ngxBlockServer.addEntry(ifBlock);
				}
			}

			// 自定义参数
			String type = "server";
			if (server.getProxyType() != 0) {
				type += server.getProxyType();
			}
			List<Param> paramList = paramService.getListByTypeId(server.getId(), type);
			for (Param param : paramList) {
				setSameParam(param, ngxBlockServer);
			}

			// location参数配置
			List<Location> locationList = serverService.getLocationByServerId(server.getId());
			for (Location location : locationList) {
				if (location.getEnable() == 1) {
					NgxBlock ngxBlockLocation = new NgxBlock();
					ngxBlockLocation.addValue("location");
					ngxBlockLocation.addValue(location.getPath());

					if (StrUtil.isNotEmpty(location.getDescr())) {
						String[] descrs = location.getDescr().split("\n");
						for (String d : descrs) {
							ngxParam = new NgxParam();
							ngxParam.addValue("# " + d);
							ngxBlockLocation.addEntry(ngxParam);
						}
					}

					// 自定义参数 - 前置模式 (position=1)
					paramList = paramService.getListByTypeId(location.getId(), "location");
					for (Param param : paramList) {
						if (param.getPosition() != null && param.getPosition() == 1) {
							setSameParam(param, ngxBlockLocation);
						}
					}

					if (location.getType() == 0 || location.getType() == 2) { // 动态代理或负载均衡

						if (location.getType() == 0) {
							ngxParam = new NgxParam();
							ngxParam.addValue("proxy_pass " + location.getValue());
							ngxBlockLocation.addEntry(ngxParam);
						} else if (location.getType() == 2) {
							Upstream upstream = sqlHelper.findById(location.getUpstreamId(), Upstream.class);
							if (upstream != null) {
								ngxParam = new NgxParam();
								ngxParam.addValue("proxy_pass " + location.getUpstreamType() + "://" + upstream.getName() + (location.getUpstreamPath() != null ? location.getUpstreamPath() : ""));
								ngxBlockLocation.addEntry(ngxParam);
							}
						}

						if (location.getHeader() == 1) { // 设置header参数
							ngxParam = new NgxParam();
							ngxParam.addValue("proxy_set_header Host " + location.getHeaderHost());
							ngxBlockLocation.addEntry(ngxParam);

							ngxParam = new NgxParam();
							ngxParam.addValue("proxy_set_header X-Real-IP $remote_addr");
							ngxBlockLocation.addEntry(ngxParam);

							ngxParam = new NgxParam();
							ngxParam.addValue("proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for");
							ngxBlockLocation.addEntry(ngxParam);

							ngxParam = new NgxParam();
							ngxParam.addValue("proxy_set_header X-Forwarded-Host $http_host");
							ngxBlockLocation.addEntry(ngxParam);

							ngxParam = new NgxParam();
							ngxParam.addValue("proxy_set_header X-Forwarded-Port $server_port");
							ngxBlockLocation.addEntry(ngxParam);

							ngxParam = new NgxParam();
							ngxParam.addValue("proxy_set_header X-Forwarded-Proto $scheme");
							ngxBlockLocation.addEntry(ngxParam);
						}

						if (location.getWebsocket() == 1) { // 设置websocket
							ngxParam = new NgxParam();
							ngxParam.addValue("proxy_http_version 1.1");
							ngxBlockLocation.addEntry(ngxParam);

							ngxParam = new NgxParam();
							ngxParam.addValue("proxy_set_header Upgrade $http_upgrade");
							ngxBlockLocation.addEntry(ngxParam);

							ngxParam = new NgxParam();
							ngxParam.addValue("proxy_set_header Connection \"upgrade\"");
							ngxBlockLocation.addEntry(ngxParam);
						}

						if (location.getCros() == 1) { // 设置跨域
							ngxParam = new NgxParam();
							ngxParam.addValue("add_header Access-Control-Allow-Origin *");
							ngxBlockLocation.addEntry(ngxParam);

							ngxParam = new NgxParam();
							ngxParam.addValue("add_header Access-Control-Allow-Methods *");
							ngxBlockLocation.addEntry(ngxParam);

							ngxParam = new NgxParam();
							ngxParam.addValue("add_header Access-Control-Allow-Headers *");
							ngxBlockLocation.addEntry(ngxParam);

							ngxParam = new NgxParam();
							ngxParam.addValue("add_header Access-Control-Allow-Credentials true");
							ngxBlockLocation.addEntry(ngxParam);

							NgxBlock ngxBlock = new NgxBlock();
							ngxBlock.addValue("if ($request_method = 'OPTIONS')");
							ngxParam = new NgxParam();

							ngxParam.addValue("return 204");
							ngxBlock.addEntry(ngxParam);

							ngxBlockLocation.addEntry(ngxBlock);

						}

						if (server.getSsl() == 1 && server.getRewrite() == 1) { // redirect http转https
							ngxParam = new NgxParam();
							ngxParam.addValue("proxy_redirect http:// https://");
							ngxBlockLocation.addEntry(ngxParam);
						}

					} else if (location.getType() == 1) { // 静态html
						if (location.getRootType() != null && location.getRootType().equals("alias")) {
							ngxParam = new NgxParam();
							ngxParam.addValue("alias " + ToolUtils.handlePath(location.getRootPath()));
							ngxBlockLocation.addEntry(ngxParam);
						} else {
							ngxParam = new NgxParam();
							ngxParam.addValue("root " + ToolUtils.handlePath(location.getRootPath()));
							ngxBlockLocation.addEntry(ngxParam);
						}

						if (StrUtil.isNotEmpty(location.getRootPage())) {
							ngxParam = new NgxParam();
							ngxParam.addValue("index " + location.getRootPage());
							ngxBlockLocation.addEntry(ngxParam);
						}

					} else if (location.getType() == 4) { // 重定向
						ngxParam = new NgxParam();
						ngxParam.addValue("return 301 " + location.getReturnUrl() + (location.getReturnPath() == 1 ? "$request_uri" : ""));
						ngxBlockLocation.addEntry(ngxParam);
					}

					// 自定义参数 - 追加模式 (position=0 或 null，默认)
					for (Param param : paramList) {
						if (param.getPosition() == null || param.getPosition() == 0) {
							setSameParam(param, ngxBlockLocation);
						}
					}

					ngxBlockServer.addEntry(ngxBlockLocation);
				}
			}

		} else {
			ngxBlockServer.addValue("server");

			// 监听端口
			tcpListenPort(server, ngxBlockServer, false);
			if (server.getIpv6() == 1) {
				tcpListenPort(server, ngxBlockServer, true);
			}

			// 指向负载均衡
			Upstream upstream = sqlHelper.findById(server.getProxyUpstreamId(), Upstream.class);
			if (upstream != null) {
				ngxParam = new NgxParam();
				ngxParam.addValue("proxy_pass " + upstream.getName());
				ngxBlockServer.addEntry(ngxParam);
			}

			// ssl配置
			setServerSsl(server, ngxBlockServer);
			// IP黑白名单
			buildDenyAllow(ngxBlockServer, "server", server.getId(), confExt);

			// 自定义参数
			String type = "server";
			if (server.getProxyType() != 0) {
				type += server.getProxyType();
			}
			List<Param> paramList = paramService.getListByTypeId(server.getId(), type);
			for (Param param : paramList) {
				setSameParam(param, ngxBlockServer);
			}
		}

		return ngxBlockServer;
	}

	/**
	 * 配置ssl
	 *
	 * @param server
	 * @param ngxBlockServer
	 */
	private void setServerSsl(Server server, NgxBlock ngxBlockServer) {
		NgxParam ngxParam = null;
		if (server.getSsl() == 1) {
			if (StrUtil.isNotEmpty(server.getPem()) && StrUtil.isNotEmpty(server.getKey())) {
				ngxParam = new NgxParam();
				ngxParam.addValue("ssl_certificate " + ToolUtils.handlePath(server.getPem()));
				ngxBlockServer.addEntry(ngxParam);

				ngxParam = new NgxParam();
				ngxParam.addValue("ssl_certificate_key " + ToolUtils.handlePath(server.getKey()));
				ngxBlockServer.addEntry(ngxParam);

				if (StrUtil.isNotEmpty(server.getProtocols())) {
					ngxParam = new NgxParam();
					ngxParam.addValue("ssl_protocols " + server.getProtocols());
					ngxBlockServer.addEntry(ngxParam);
				}

			}

			// https添加80端口重写
			if (server.getProxyType() == 0 && server.getRewrite() == 1) {
				if (StrUtil.isNotEmpty(server.getRewriteListen())) {
					ngxParam = new NgxParam();
					String reValue = "listen " + server.getRewriteListen();
					ngxParam.addValue(reValue);
					ngxBlockServer.addEntry(ngxParam);

					// ipv6
					if (server.getIpv6() == 1) {
						ngxParam = new NgxParam();
						reValue = "listen [::]:" + replaceIp(server.getRewriteListen());
						ngxParam.addValue(reValue);
						ngxBlockServer.addEntry(ngxParam);
					}
				}

				String port = replaceIp(server.getListen());

				NgxBlock ngxBlock = new NgxBlock();
				ngxBlock.addValue("if ($scheme = http)");
				ngxParam = new NgxParam();

				ngxParam.addValue("return 301 https://$host:" + port + "$request_uri");
				ngxBlock.addEntry(ngxParam);

				ngxBlockServer.addEntry(ngxBlock);

			}
		}
	}

	/**
	 * 替换掉listen中的ip
	 *
	 * @param listen
	 * @return
	 */
	private String replaceIp(String listen) {

		if (listen.contains(":")) {
			return listen.split(":")[1];
		}

		return listen;
	}

	/**
	 * include防止重复
	 *
	 * @param ngxBlockHttp
	 * @param ngxParam
	 * @return
	 */
	private boolean noContain(NgxBlock ngxBlockHttp, NgxParam ngxParam) {
		for (NgxEntry ngxEntry : ngxBlockHttp.getEntries()) {
			if (ngxEntry.toString().equals(ngxParam.toString())) {
				return false;
			}
		}

		return true;
	}

	public String buildNodeStr(UpstreamServer upstreamServer) {

		if (upstreamServer.getServer().contains(":")) {
			upstreamServer.setServer("[" + upstreamServer.getServer() + "]");
		}

		String conf = upstreamServer.getServer() + ":" + upstreamServer.getPort();
		if (upstreamServer.getWeight() != null) {
			conf += " weight=" + upstreamServer.getWeight();
		}
		if (upstreamServer.getFailTimeout() != null) {
			conf += " fail_timeout=" + upstreamServer.getFailTimeout() + "s";
		}
		if (upstreamServer.getMaxFails() != null) {
			conf += " max_fails=" + upstreamServer.getMaxFails();
		}
		if (upstreamServer.getMaxConns() != null) {
			conf += " max_conns=" + upstreamServer.getMaxConns();
		}
		if (!"none".equals(upstreamServer.getStatus())) {
			conf += " " + upstreamServer.getStatus();
		}
		if (upstreamServer.getParam() != null) {
			conf += " " + upstreamServer.getParam();
		}
		return conf;
	}

	private void setSameParam(Param param, NgxBlock ngxBlock) {
		if (StrUtil.isEmpty(param.getTemplateValue())) {
			NgxParam ngxParam = new NgxParam();
			if (StrUtil.isNotEmpty(param.getName().trim())) {
				param.setName(param.getName().trim() + " ");
			}

			ngxParam.addValue(param.getName() + param.getValue().trim());
			ngxBlock.addEntry(ngxParam);
		} else {
			List<Param> params = templateService.getParamList(param.getTemplateValue());
			for (Param paramSub : params) {
				NgxParam ngxParam = new NgxParam();
				if (StrUtil.isNotEmpty(paramSub.getName().trim())) {
					paramSub.setName(paramSub.getName().trim() + " ");
				}

				ngxParam.addValue(paramSub.getName() + paramSub.getValue().trim());
				ngxBlock.addEntry(ngxParam);
			}
		}
	}

	private String addConfFile(ConfExt confExt, String name, List<String> strs) {
		name = name.replace(" ", "_").replaceAll("[!@#$%^&*()_+=\\{\\}\\[\\]\"<>,/;':\\\\|`~]+", "_");

		boolean hasSameName = false;
		for (ConfFile confFile : confExt.getFileList()) {
			if (confFile.getName().equals(name)) {
				confFile.setConf(confFile.getConf() + "\n" + buildStr(strs));
				hasSameName = true;
			}
		}

		if (!hasSameName) {
			ConfFile confFile = new ConfFile();
			confFile.setName(name);
			confFile.setConf(buildStr(strs));
			confExt.getFileList().add(confFile);
		}

//		return new File(nginxPath).getParent().replace("\\", "/") + "/conf.d/" + name;

		return "conf.d/" + name;
	}

	private String addConfFile(ConfExt confExt, String name, NgxBlock ngxBlockServer) {
		name = name.replace(" ", "_").replaceAll("[!@#$%^&*()_+=\\{\\}\\[\\]\"<>,/;':\\\\|`~]+", "_");

		boolean hasSameName = false;
		for (ConfFile confFile : confExt.getFileList()) {
			if (confFile.getName().equals(name)) {
				confFile.setConf(confFile.getConf() + "\n" + buildStr(ngxBlockServer));
				hasSameName = true;
			}
		}

		if (!hasSameName) {
			ConfFile confFile = new ConfFile();
			confFile.setName(name);
			confFile.setConf(buildStr(ngxBlockServer));
			confExt.getFileList().add(confFile);
		}

//		return new File(nginxPath).getParent().replace("\\", "/") + "/conf.d/" + name;
		return "conf.d/" + name;
	}

	private String buildStr(NgxBlock ngxBlockServer) {

		NgxConfig ngxConfig = new NgxConfig();
		ngxConfig.addEntry(ngxBlockServer);

		return ToolUtils.formatConf(ToolUtils.handleConf(new NgxDumper(ngxConfig).dump()));
	}

	private String buildStr(List<String> strs) {

		return StrUtil.join("\n", strs);
	}

	public void replace(String nginxPath, String nginxContent, List<String> subContent, List<String> subName, Boolean isReplace, String adminName) {

		String beforeConf = null;
		if (isReplace) {
			// 先读取已有的配置
			beforeConf = FileUtil.readString(nginxPath, StandardCharsets.UTF_8);
		}

		String confd = new File(nginxPath).getParent().replace("\\", "/") + "/conf.d/";
		// 删除conf.d下全部文件
		FileUtil.del(confd);
		FileUtil.mkdir(confd);

		// 写入主文件
		FileUtil.writeString(nginxContent, nginxPath.replace(" ", "_"), StandardCharsets.UTF_8);

		// 写入conf.d文件
		if (subContent != null && subName != null) {
			for (int i = 0; i < subContent.size(); i++) {
				String tagert = (new File(nginxPath).getParent().replace("\\", "/") + "/conf.d/" + subName.get(i)).replace(" ", "_");
				FileUtil.writeString(subContent.get(i), tagert, StandardCharsets.UTF_8); // 清空
			}
		}

		// 写入周边配置文件
		ClassPathResource resource = new ClassPathResource("conf.zip");
		InputStream inputStream = resource.getStream();
		ZipUtil.unzip(inputStream, new File(new File(nginxPath).getParent().replace("\\", "/")), CharsetUtil.defaultCharset());

		// 备份文件
		if (isReplace) {
			Bak bak = new Bak();
			bak.setTime(DateUtil.format(new Date(), "yyyy-MM-dd HH:mm:ss"));
			bak.setContent(nginxContent);
			sqlHelper.insert(bak);

			// 备份子文件
			for (int i = 0; i < subContent.size(); i++) {
				BakSub bakSub = new BakSub();
				bakSub.setBakId(bak.getId());

				bakSub.setName(subName.get(i));
				bakSub.setContent(subContent.get(i));
				sqlHelper.insert(bakSub);
			}

			// 写入操作日志
			if (StrUtil.isNotEmpty(adminName)) {
				operateLogService.addLog(beforeConf, nginxContent, adminName);
			}
		}
	}

	public AsycPack getAsycPack(String[] asycData) {
		AsycPack asycPack = new AsycPack();
		if (hasStr(asycData, "basic") || hasStr(asycData, "all")) {
			asycPack.setBasicList(sqlHelper.findAll(Basic.class));
		}

		if (hasStr(asycData, "http") || hasStr(asycData, "all")) {
			asycPack.setHttpList(sqlHelper.findAll(Http.class));
		}

		if (hasStr(asycData, "server") || hasStr(asycData, "all")) {
			List<Server> serverList = sqlHelper.findAll(Server.class);
			for (Server server : serverList) {
				if (StrUtil.isNotEmpty(server.getPem()) && FileUtil.exist(server.getPem())) {
					server.setPemStr(FileUtil.readString(server.getPem(), StandardCharsets.UTF_8));
				}

				if (StrUtil.isNotEmpty(server.getKey()) && FileUtil.exist(server.getKey())) {
					server.setKeyStr(FileUtil.readString(server.getKey(), StandardCharsets.UTF_8));
				}
			}
			asycPack.setServerList(serverList);
			asycPack.setLocationList(sqlHelper.findAll(Location.class));
		}

		if (hasStr(asycData, "password") || hasStr(asycData, "all")) {
			List<Password> passwordList = sqlHelper.findAll(Password.class);
			for (Password password : passwordList) {
				if (StrUtil.isNotEmpty(password.getPath()) && FileUtil.exist(password.getPath())) {
					password.setPathStr(FileUtil.readString(password.getPath(), StandardCharsets.UTF_8));
				}

			}
			asycPack.setPasswordList(passwordList);
		}

		if (hasStr(asycData, "upstream") || hasStr(asycData, "all")) {
			asycPack.setUpstreamList(sqlHelper.findAll(Upstream.class));
			asycPack.setUpstreamServerList(sqlHelper.findAll(UpstreamServer.class));
		}

		if (hasStr(asycData, "stream") || hasStr(asycData, "all")) {
			asycPack.setStreamList(sqlHelper.findAll(Stream.class));
		}

		if (hasStr(asycData, "param") || hasStr(asycData, "all")) {
			asycPack.setTemplateList(sqlHelper.findAll(Template.class));
			asycPack.setParamList(sqlHelper.findAll(Param.class));
		}

		if (hasStr(asycData, "cert") || hasStr(asycData, "all")) {
			asycPack.setCertList(sqlHelper.findAll(Cert.class));
			asycPack.setCertCodeList(sqlHelper.findAll(CertCode.class));
			asycPack.setAcmeZip(certService.getAcmeZipBase64());
			asycPack.setCertZip(certService.getCertZipBase64());
		}

		if (hasStr(asycData, "denyAllow") || hasStr(asycData, "all")) {
			asycPack.setDenyAllowList(sqlHelper.findAll(DenyAllow.class));
		}

		return asycPack;
	}

	private boolean hasStr(String[] asycData, String data) {
		for (String str : asycData) {
			if (str.equals(data)) {
				return true;
			}
		}
		return false;
	}

	public void setAsycPack(AsycPack asycPack) {
		try {

			if (asycPack.getBasicList() != null) {
				sqlHelper.deleteByQuery(new ConditionAndWrapper(), Basic.class);
				sqlHelper.insertAll(asycPack.getBasicList());
			}

			if (asycPack.getHttpList() != null) {
				sqlHelper.deleteByQuery(new ConditionAndWrapper(), Http.class);
				sqlHelper.insertAll(asycPack.getHttpList());
			}

			if (asycPack.getServerList() != null) {
				sqlHelper.deleteByQuery(new ConditionAndWrapper(), Server.class);
				sqlHelper.insertAll(asycPack.getServerList());
				sqlHelper.deleteByQuery(new ConditionAndWrapper(), Location.class);
				sqlHelper.insertAll(asycPack.getLocationList());

				for (Server server : asycPack.getServerList()) {
					try {
						if (StrUtil.isNotEmpty(server.getPem()) && StrUtil.isNotEmpty(server.getPemStr())) {
							String pemPath = SystemTool.isWindows() ? server.getPem().replace("*", "_") : server.getPem();
							FileUtil.writeString(server.getPemStr(), pemPath, StandardCharsets.UTF_8);
						}
						if (StrUtil.isNotEmpty(server.getKey()) && StrUtil.isNotEmpty(server.getKeyStr())) {
							String keyPath = SystemTool.isWindows() ? server.getKey().replace("*", "_") : server.getKey();
							FileUtil.writeString(server.getKeyStr(), keyPath, StandardCharsets.UTF_8);
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}

			if (asycPack.getUpstreamList() != null) {
				sqlHelper.deleteByQuery(new ConditionAndWrapper(), Upstream.class);
				sqlHelper.insertAll(asycPack.getUpstreamList());
				sqlHelper.deleteByQuery(new ConditionAndWrapper(), UpstreamServer.class);
				sqlHelper.insertAll(asycPack.getUpstreamServerList());
			}

			if (asycPack.getStreamList() != null) {
				sqlHelper.deleteByQuery(new ConditionAndWrapper(), Stream.class);
				sqlHelper.insertAll(asycPack.getStreamList());
			}

			if (asycPack.getTemplateList() != null) {
				sqlHelper.deleteByQuery(new ConditionAndWrapper(), Template.class);
				sqlHelper.insertAll(asycPack.getTemplateList());
				sqlHelper.deleteByQuery(new ConditionAndWrapper(), Param.class);
				sqlHelper.insertAll(asycPack.getParamList());
			}

			if (asycPack.getDenyAllowList() != null) {
				sqlHelper.deleteByQuery(new ConditionAndWrapper(), DenyAllow.class);
				sqlHelper.insertAll(asycPack.getDenyAllowList());
			}

			if (asycPack.getPasswordList() != null) {
				sqlHelper.deleteByQuery(new ConditionAndWrapper(), Password.class);
				sqlHelper.insertAll(asycPack.getPasswordList());

				for (Password password : asycPack.getPasswordList()) {
					if (StrUtil.isNotEmpty(password.getPath()) && StrUtil.isNotEmpty(password.getPathStr())) {
						FileUtil.writeString(password.getPathStr(), password.getPath(), StandardCharsets.UTF_8);
					}
				}
			}

			// 导入证书
			if (asycPack.getCertList() != null) {
				sqlHelper.deleteByQuery(new ConditionAndWrapper(), Cert.class);
				sqlHelper.insertAll(asycPack.getCertList());
			}
			if (asycPack.getCertCodeList() != null) {
				sqlHelper.deleteByQuery(new ConditionAndWrapper(), CertCode.class);
				sqlHelper.insertAll(asycPack.getCertCodeList());
			}
			if (asycPack.getAcmeZip() != null) {
				certService.writeAcmeZipBase64(asycPack.getAcmeZip());
			}
			if (asycPack.getCertZip() != null) {
				certService.writeCertZipBase64(asycPack.getCertZip());
			}

		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}

	}

	public List<Cert> getApplyCerts() {
		List<Cert> certs = sqlHelper.findListByQuery(new ConditionAndWrapper().ne(Cert::getType, 1), Cert.class);
		return certs;
	}

	/**
	 * 測試所有代理目標的 TCP 連通性
	 */
	public List<Map<String, String>> testAllDestinations() {
		List<Map<String, String>> results = new ArrayList<>();

		// HTTP Server (proxyType=0)
		List<Server> httpServers = serverService.getListByProxyType(new String[] { "0" });
		for (Server server : httpServers) {
			if (server.getEnable() == null || !server.getEnable()) {
				continue;
			}
			String serverLabel = buildServerLabel(server);
			List<Location> locations = serverService.getLocationByServerId(server.getId());
			for (Location location : locations) {
				if (location.getEnable() != null && location.getEnable() == 0) {
					continue;
				}
				if (location.getType() == 0) {
					addProxyTestResult(results, serverLabel, location.getPath(), location.getValue());
				} else if (location.getType() == 2) {
					addUpstreamTestResults(results, serverLabel, location.getPath(), location.getUpstreamId());
				}
			}
		}

		// TCP/UDP Server (proxyType=1,2)
		List<Server> tcpUdpServers = serverService.getListByProxyType(new String[] { "1", "2" });
		for (Server server : tcpUdpServers) {
			if (server.getEnable() == null || !server.getEnable()) {
				continue;
			}
			String serverLabel = buildServerLabel(server);
			if (StrUtil.isNotEmpty(server.getProxyUpstreamId())) {
				addUpstreamTestResults(results, serverLabel, "stream", server.getProxyUpstreamId());
			}
		}

		return results;
	}

	private String buildServerLabel(Server server) {
		String label = "";
		if (StrUtil.isNotEmpty(server.getServerName())) {
			label = server.getServerName();
		}
		if (StrUtil.isNotEmpty(server.getListen())) {
			label += (label.isEmpty() ? "" : " ") + ":" + server.getListen();
		}
		if (label.isEmpty()) {
			label = server.getId();
		}
		return label;
	}

	private void addProxyTestResult(List<Map<String, String>> results, String serverLabel, String locationPath, String proxyPassUrl) {
		if (StrUtil.isEmpty(proxyPassUrl)) {
			return;
		}
		try {
			java.net.URL url = new java.net.URL(proxyPassUrl);
			String host = url.getHost();
			int port = url.getPort();
			if (port == -1) {
				port = "https".equalsIgnoreCase(url.getProtocol()) ? 443 : 80;
			}
			String destination = host + ":" + port;
			boolean ok = TelnetUtils.isRunning(host, port);

			Map<String, String> row = new LinkedHashMap<>();
			row.put("server", serverLabel);
			row.put("location", locationPath);
			row.put("destination", destination);
			row.put("status", ok ? "OK" : "FAIL");
			results.add(row);
		} catch (Exception e) {
			Map<String, String> row = new LinkedHashMap<>();
			row.put("server", serverLabel);
			row.put("location", locationPath);
			row.put("destination", proxyPassUrl);
			row.put("status", "FAIL");
			results.add(row);
		}
	}

	private void addUpstreamTestResults(List<Map<String, String>> results, String serverLabel, String locationPath, String upstreamId) {
		if (StrUtil.isEmpty(upstreamId)) {
			return;
		}
		Upstream upstream = sqlHelper.findById(upstreamId, Upstream.class);
		if (upstream == null) {
			return;
		}
		List<UpstreamServer> servers = upstreamService.getUpstreamServers(upstreamId);
		for (UpstreamServer us : servers) {
			if (us.getEnable() != null && us.getEnable() == 0) {
				continue;
			}
			String destination = us.getServer() + ":" + us.getPort();
			boolean ok = TelnetUtils.isRunning(us.getServer(), us.getPort());

			Map<String, String> row = new LinkedHashMap<>();
			row.put("server", serverLabel);
			row.put("location", locationPath + " -> " + upstream.getName());
			row.put("destination", destination);
			row.put("status", ok ? "OK" : "FAIL");
			results.add(row);
		}
	}

}
