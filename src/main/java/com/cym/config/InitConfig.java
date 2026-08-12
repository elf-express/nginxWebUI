package com.cym.config;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Init;
import org.noear.solon.annotation.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cym.model.Admin;
import com.cym.model.Basic;
import com.cym.model.DenyAllow;
import com.cym.model.GeoRule;
import com.cym.model.Http;
import com.cym.model.Param;
import com.cym.model.Module;
import com.cym.model.Server;
import com.cym.model.Template;
import com.cym.service.BasicService;
import com.cym.service.ConfService;
import com.cym.service.DenyAllowService;
import com.cym.service.GeoipService;
import com.cym.service.NginxDocService;
import com.cym.service.NginxService;
import com.cym.service.SettingService;
import com.cym.service.TemplateService;
import com.cym.sqlhelper.config.DataSourceEmbed;
import com.cym.sqlhelper.config.Table;
import com.cym.sqlhelper.utils.ConditionAndWrapper;
import com.cym.sqlhelper.utils.JdbcTemplate;
import com.cym.sqlhelper.utils.SqlHelper;
import com.cym.utils.EncodePassUtils;
import com.cym.utils.MessageUtils;
import com.cym.utils.SystemTool;
import com.cym.utils.TemplateDefUtils;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ClassPathResource;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.ClassUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.RuntimeUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.ZipUtil;

@Component
public class InitConfig {
	Logger logger = LoggerFactory.getLogger(this.getClass());
	@Inject
	MessageUtils m;

	@Inject
	HomeConfig homeConfig;

	@Inject
	VersionConfig versionConfig;

	@Inject
	SettingService settingService;
	@Inject
	BasicService basicService;
	@Inject
	SqlHelper sqlHelper;
	@Inject
	JdbcTemplate jdbcTemplate;
	@Inject
	ConfService confService;
	@Inject
	TemplateService templateService;
	@Inject
	NginxService nginxService;
	@Inject
	DenyAllowService denyAllowService;
	@Inject
	GeoipService geoipService;
	@Inject
	NginxDocService nginxDocService;
	@Inject
	DataSourceEmbed dataSourceEmbed;
	@Inject("${project.beanPackage}")
	String packageName;
	@Inject("${project.findPass}")
	Boolean findPass;
	@Inject("${spring.database.type}")
	String databaseType;

	@Inject("${project.skipSeedFetch:false}")
	Boolean skipSeedFetch;

	@Inject("${init.admin}")
	String initAdmin;
	@Inject("${init.pass}")
	String initPass;
	@Inject("${init.api}")
	Boolean initApi;

	@Init
	public void start() throws Throwable {

		// 找回密码
		if (findPass) {
			List<Admin> admins = sqlHelper.findAll(Admin.class);
			for (Admin admin : admins) {
				String randomPass = RandomUtil.randomString(8);
				
				admin.setAuth(false); // 关闭二次验证
				admin.setPass(EncodePassUtils.encode(randomPass));
				sqlHelper.updateById(admin);
				
				System.out.println(m.get("adminStr.name") + ":" + admin.getName() + " " + m.get("adminStr.pass") + ":" + randomPass);
			}
			System.exit(1);
		}

		// 初始化管理员账号
		if (StrUtil.isNotBlank(initAdmin) && StrUtil.isNotBlank(initPass)) {
			addAdmin();
		}

		// 初始化base值
		Long count = sqlHelper.findAllCount(Basic.class);
		if (count == 0) {
			List<Basic> basics = new ArrayList<Basic>();
			basics.add(new Basic("worker_processes", "auto", 1l));
			basics.add(new Basic("events", "{\r\n    worker_connections  1024;\r\n    accept_mutex on;\r\n}", 2l));
			// load_module 由 ConfService 自動偵測容器內模組並按依賴排序載入，不再手動預設
			sqlHelper.insertAll(basics);
		}

		// 初始化http值
		count = sqlHelper.findAllCount(Http.class);
		if (count == 0) {
			List<Http> https = new ArrayList<Http>();
			long seq = 0;
			https.add(new Http("include", "mime.types", seq++, "base"));
			https.add(new Http("default_type", "application/octet-stream", seq++, "base"));

			// Real IP（Cloudflare）
			https.add(new Http("include", GeoipService.GEOIP_DIR + GeoipService.REALIP_CONF_NAME, seq++, "realip"));

			// GeoIP2（國家、城市、ASN）
			https.add(new Http("geoip2", "/etc/nginx/geoip/GeoLite2-Country.mmdb {\r\n    auto_reload 60m;\r\n    $geoip2_data_country_code country iso_code;\r\n    $geoip2_data_country_name country names en;\r\n}", seq++, "geoip"));
			https.add(new Http("geoip2", "/etc/nginx/geoip/GeoLite2-City.mmdb {\r\n    auto_reload 60m;\r\n    $geoip2_data_city_name city names en;\r\n}", seq++, "geoip"));
			https.add(new Http("geoip2", "/etc/nginx/geoip/GeoLite2-ASN.mmdb {\r\n    auto_reload 60m;\r\n    $geoip2_data_asn autonomous_system_number;\r\n    $geoip2_data_asn_org autonomous_system_organization;\r\n}", seq++, "geoip"));

			// Gzip 壓縮
			https.add(new Http("gzip", "on", seq++, "gzip"));
			https.add(new Http("gzip_min_length", "1k", seq++, "gzip"));
			https.add(new Http("gzip_comp_level", "5", seq++, "gzip"));
			https.add(new Http("gzip_types", "text/plain application/json application/javascript text/css application/xml text/javascript application/x-httpd-php image/svg+xml", seq++, "gzip"));

			// Brotli 壓縮（比 gzip 更高效）
			https.add(new Http("brotli", "on", seq++, "brotli"));
			https.add(new Http("brotli_comp_level", "6", seq++, "brotli"));
			https.add(new Http("brotli_types", "text/plain application/json application/javascript text/css application/xml text/javascript image/svg+xml", seq++, "brotli"));

			// 安全 Headers
			https.add(new Http("add_header", "X-Frame-Options SAMEORIGIN", seq++, "headers"));
			https.add(new Http("add_header", "X-Content-Type-Options nosniff", seq++, "headers"));
			https.add(new Http("add_header", "X-XSS-Protection \"1; mode=block\"", seq++, "headers"));
			https.add(new Http("add_header", "Referrer-Policy \"strict-origin-when-cross-origin\"", seq++, "headers"));

			// Proxy Headers Hash（避免 warn）
			https.add(new Http("proxy_headers_hash_max_size", "4096", seq++, "proxy"));

			// GeoIP2 / map 變數多時避免 variables_hash 警告
			https.add(new Http("variables_hash_max_size", "2048", seq++, "base"));
			https.add(new Http("variables_hash_bucket_size", "128", seq++, "base"));

			// ASN 封鎖清單改由 AsnRule 表 + ConfService 動態產生 map，不再寫入 Http 表

			// 日誌格式（含真實 IP + GeoIP + ASN）
			https.add(new Http("log_format", "main '$remote_addr - $remote_user [$time_local] \"$request\" '\r\n                      '$status $body_bytes_sent \"$http_referer\" '\r\n                      '\"$http_user_agent\" \"$geoip2_data_country_code\" \"$geoip2_data_city_name\" \"$geoip2_data_asn\" \"$geoip2_data_asn_org\"'", seq++, "logging"));

			// 預設開啟日誌（供 CrowdSec 收集）
			https.add(new Http("access_log", homeConfig.home + "log/access.log main", seq++, "logging"));
			https.add(new Http("error_log", homeConfig.home + "log/error.log", seq++, "logging"));

			sqlHelper.insertAll(https);
		}

		// 初始化预设模板
		Long templateCount = sqlHelper.findAllCount(Template.class);
		if (templateCount == 0) {
			initDefaultTemplates();
		}

		// 初始化預設國家白名單（全域 GeoRule）— 第一次啟動才 seed，已有則不動
		// 17 國：CN/JP/HK/KR/SG/TH/MY/TW/VN/GB/FR/DE/GR/CA/US/MO/LA
		Long geoCount = sqlHelper.findAllCount(GeoRule.class);
		if (geoCount == 0) {
			GeoRule defaultGeo = new GeoRule();
			defaultGeo.setMode(0); // 0 = allow（白名單）
			defaultGeo.setCountries("CN,JP,HK,KR,SG,TH,MY,TW,VN,GB,FR,DE,GR,CA,US,MO,LA");
			defaultGeo.setServerId(null); // null = 全域 http 層級
			defaultGeo.setEnable(true);
			sqlHelper.insert(defaultGeo);
			logger.info("Initialized default GeoRule: allow {} countries", defaultGeo.getCountries().split(",").length);
		}

		// 遷移：清除模板 def 值，停止自動套用到所有 server/location
		// 舊版模板 def="server"/"location"/"http" 會被 ParamService 自動注入，導致所有模板參數無差別套用
		if (!"1".equals(settingService.get("templateDefMigrated"))) {
			List<Template> templates = sqlHelper.findAll(Template.class);
			for (Template tpl : templates) {
				if (StrUtil.isNotEmpty(tpl.getDef())) {
					tpl.setDef("");
					sqlHelper.updateById(tpl);
				}
			}
			settingService.set("templateDefMigrated", "1");
			logger.info("Migration: cleared template def values to prevent auto-apply");
		}

		// 遷移：為已有模板賦 groupName
		if (!"1".equals(settingService.get("templateGroupMigrated"))) {
			migrateTemplateGroups();
			settingService.set("templateGroupMigrated", "1");
			logger.info("Migration: assigned groupName to existing templates");
		}

		// 遷移：將既有英文 template name 改成「English (中文)」格式
		if (!"1".equals(settingService.get("templateNameCnMigrated"))) {
			migrateTemplateNameCn();
			settingService.set("templateNameCnMigrated", "1");
			logger.info("Migration: appended Chinese annotation to template names");
		}

		// 遷移：既有庫補 stream 連線限制模板（參數不動舊四個 rateLimit；zone 名 s_conn_perip 與 http 隔離）
		if (!"1".equals(settingService.get("streamConnLimitTemplatesSeeded"))) {
			seedStreamConnLimitTemplatesIfMissing();
			settingService.set("streamConnLimitTemplatesSeeded", "1");
		}

		// 遷移：社群慣用參數模板庫（僅手動套用 def=""；名稱含建議；不覆蓋已存在同名）
		if (!"1".equals(settingService.get("moduleCommunityTemplatesSeeded"))) {
			int n = seedModuleCommunityTemplatesIfMissing();
			settingService.set("moduleCommunityTemplatesSeeded", "1");
			if (n > 0) {
				logger.info("Migration: seeded {} community module param template(s)", n);
			}
		}

		// 初始化模組管理表（全量 MODULE_CATALOG；新裝預設 enable=false，由 moduleInitMigrated 對磁碟模組自動開啟）
		Long moduleCount = sqlHelper.findAllCount(Module.class);
		if (moduleCount == 0) {
			sqlHelper.insertAll(buildModuleCatalogSeed());
		}

		// 遷移：既有庫補上 Alpine 全量模組列（已存在 name 不覆蓋 enable）
		if (!"1".equals(settingService.get("moduleCatalogFullSeeded"))) {
			int added = seedMissingModulesFromCatalog();
			settingService.set("moduleCatalogFullSeeded", "1");
			if (added > 0) {
				logger.info("Migration: added {} module catalog row(s)", added);
			}
		}

		// 遷移：精簡模組集 — 刪除已下架模組列，並依 MODULE_CATALOG 重寫 seq（load_module 順序）
		if (!"1".equals(settingService.get("moduleCatalogHardened20260812"))) {
			int pruned = pruneModulesNotInCatalog();
			resequenceModulesToCatalogOrder();
			settingService.set("moduleCatalogHardened20260812", "1");
			logger.info("Migration: pruned {} obsolete module row(s), resequenced catalog", pruned);
		}

		// 遷移：修正 def=stream 誤標（GeoIP 的 if / HTTP log_format 被自動注入 stream{} → nginx -t 失敗）
		// 僅允許「Connection Limit (stream) 連線數限制 — stream 層」保留 def=stream（zone 宣告）
		// stream server 的 limit_conn 用 def=server1；其餘模板 def 清空（手動套用）
		if (!"1".equals(settingService.get("streamDefTemplatesSanitized20260812"))) {
			int n = sanitizeStreamDefTemplates();
			ensureVariablesHashHttpParams();
			settingService.set("streamDefTemplatesSanitized20260812", "1");
			logger.info("Migration: sanitized stream template def/params ({} row touch(es)), ensured variables_hash", n);
		}

		// 遷移：為已有 Http 記錄填充 groupName
		if (!"1".equals(settingService.get("httpGroupMigrated"))) {
			List<Http> allHttp = sqlHelper.findAll(Http.class);
			for (Http h : allHttp) {
				if (StrUtil.isNotEmpty(h.getGroupName())) continue;
				String n = h.getName();
				String v = h.getValue();
				String g = null;
				if ("include".equals(n) && v != null && v.contains("mime.types")) g = "base";
				else if ("default_type".equals(n)) g = "base";
				else if ("include".equals(n) && v != null && v.contains("realip")) g = "realip";
				else if ("geoip2".equals(n)) g = "geoip";
				else if ("map".equals(n) && v != null && v.contains("geoip2_data_asn")) g = "geoip";
				else if (n != null && n.startsWith("gzip")) g = "gzip";
				else if (n != null && n.startsWith("brotli")) g = "brotli";
				else if ("add_header".equals(n)) g = "headers";
				else if (n != null && n.contains("proxy_headers_hash")) g = "proxy";
				else if ("log_format".equals(n) || "access_log".equals(n) || "error_log".equals(n)) g = "logging";
				if (g != null) {
					h.setGroupName(g);
					sqlHelper.updateById(h);
				}
			}
			settingService.set("httpGroupMigrated", "1");
			logger.info("Migration: assigned groupName to existing Http records");
		}

		// 升級遷移：自動啟用磁碟上已存在的模組（保持舊版全載入行為）
		if (!"1".equals(settingService.get("moduleInitMigrated"))) {
			if (SystemTool.isLinux()) {
				List<String> availableOnDisk = nginxService.getAvailableModules();
				if (!availableOnDisk.isEmpty()) {
					List<Module> allModules = sqlHelper.findAll(Module.class);
					for (Module mod : allModules) {
						if (availableOnDisk.contains(mod.getName())) {
							mod.setEnable(true);
							sqlHelper.updateById(mod);
						}
					}
					logger.info("Migration: auto-enabled " + availableOnDisk.size() + " on-disk modules");
				}
			}
			settingService.set("moduleInitMigrated", "1");
		}

		// 遷移：為既有 DenyAllow 反查引用歸類 type（被 allowId 引用→allow、否則→deny）
		if (!"1".equals(settingService.get("denyAllowTypeMigrated"))) {
			List<Server> daServers = sqlHelper.findAll(Server.class);
			String httpDenyId = settingService.get("denyId");
			String httpAllowId = settingService.get("allowId");
			String streamDenyId = settingService.get("denyIdStream");
			String streamAllowId = settingService.get("allowIdStream");
			List<DenyAllow> daList = sqlHelper.findAll(DenyAllow.class);
			int migrated = 0;
			for (DenyAllow da : daList) {
				String resolved = DenyAllowService.resolveTypeByReference(
						da.getId(), daServers, httpDenyId, httpAllowId, streamDenyId, streamAllowId);
				da.setType(resolved);
				sqlHelper.updateById(da);
				migrated++;
			}
			settingService.set("denyAllowTypeMigrated", "1");
			logger.info("Migration: assigned type to {} existing DenyAllow records (reference-based)", migrated);
		}

		// 释放基础nginx配置文件
		if (!FileUtil.exist(homeConfig.home + "fastcgi.conf")) {
			ClassPathResource resource = new ClassPathResource("conf.zip");
			InputStream inputStream = resource.getStream();
			ZipUtil.unzip(inputStream, new File(homeConfig.home), CharsetUtil.defaultCharset());
		}
		if (!FileUtil.exist(homeConfig.home + "nginx.conf")) {
			ClassPathResource resource = new ClassPathResource("nginx.conf");
			InputStream inputStream = resource.getStream();
			FileUtil.writeFromStream(inputStream, homeConfig.home + "nginx.conf");

		}

		// 设置nginx配置文件
		String nginxPath = settingService.get("nginxPath");
		if (StrUtil.isEmpty(nginxPath)) {
			nginxPath = homeConfig.home + "nginx.conf";
			// 设置nginx.conf路径
			settingService.set("nginxPath", nginxPath);
		}

		// 释放acme全新包
		String acmeShDir = homeConfig.home + ".acme.sh" + File.separator;
		ClassPathResource resource = new ClassPathResource("acme.zip");
		InputStream inputStream = resource.getStream();
		ZipUtil.unzip(inputStream, new File(acmeShDir), CharsetUtil.defaultCharset());

		// 全局黑白名单
		if (settingService.get("denyAllow") == null) {
			settingService.set("denyAllow", "0");
		}
		if (settingService.get("denyAllowStream") == null) {
			settingService.set("denyAllowStream", "0");
		}

		// 種子:預設惡意 IP 黑名單(seed-on-empty;flag 保證只播一次,使用者刪光不重播)
		if (!"1".equals(settingService.get("denyAllowSeeded"))) {
			if (sqlHelper.findAllCount(DenyAllow.class) == 0) {
				List<DenyAllow> seedRules = DenyAllowService.defaultBlocklistRules();
				for (DenyAllow da : seedRules) {
					sqlHelper.insertOrUpdate(da);
				}
				logger.info("Seeded {} default DenyAllow blocklist rules", seedRules.size());
				// 非同步首抓(失敗不影響啟動;規則留空,交由每日排程重試)。
				// E2E/離線環境用 --project.skipSeedFetch=true 關閉,避免每次測試都打外部 feed。
				if (!skipSeedFetch) {
					ThreadUtil.execute(() -> {
						for (DenyAllow da : seedRules) {
							if (denyAllowService.fetchAndUpdate(da)) {
								// 使用者可能已在首抓完成前刪除該規則 → 確認仍存在才寫回,避免以新 id 復活
								if (sqlHelper.findById(da.getId(), DenyAllow.class) != null) {
									sqlHelper.updateById(da);
								}
							}
						}
					});
				}
			}
			// 升級型安裝(已有規則)也補設 flag:日後刪光全部規則時不重播種子
			settingService.set("denyAllowSeeded", "1");
		}

		if (SystemTool.isLinux()) {
			// realip.conf 保底:locked include 缺檔會讓 nginx -t 永遠失敗 → 存檔死鎖
			geoipService.ensureRealipPlaceholder();
			// 查找ngx_stream_module模块
			if (!basicService.contain("ngx_stream_module.so") && FileUtil.exist("/usr/lib/nginx/modules/ngx_stream_module.so")) {
				Basic basic = new Basic("load_module", "/usr/lib/nginx/modules/ngx_stream_module.so", -10l);
				sqlHelper.insert(basic);
			}

			// 判断是否存在nginx命令
			if (hasNginx() && StrUtil.isEmpty(settingService.get("nginxExe"))) {
				// 设置nginx执行文件
				settingService.set("nginxExe", "nginx");
			}

			// 异步重启nginx, 重建pid
			ThreadUtil.execute(new Runnable() {

				@Override
				public void run() {

					String nginxExe = settingService.get("nginxExe");
					String nginxDir = settingService.get("nginxDir");
					String nginxPath = settingService.get("nginxPath");
					if (StrUtil.isNotEmpty(nginxExe) && StrUtil.isNotEmpty(nginxPath)) {
						runCmd("pkill -9 nginx");
						String cmd = nginxExe + " -c " + nginxPath;
						if (StrUtil.isNotEmpty(nginxDir)) {
							cmd += " -p " + nginxDir;
						}
						runCmd(cmd);
					}
				}

			});
		}

		// nginx 文件索引:只在啟用 MCP 時才載入,避免沒用到卻付出解析 150 頁 markdown 的成本
		if (StrUtil.isNotEmpty(org.noear.solon.Solon.cfg().get("mcp.token"))) {
			nginxDocService.loadFromClasspath();
		}

		// 展示logo
		showLogo();
	}

	private void runCmd(String cmd) {
		logger.info("run: " + cmd);
		RuntimeUtil.execForStr("/bin/sh", "-c", cmd);
	}

	private boolean hasNginx() {
		String rs = RuntimeUtil.execForStr("which nginx");
		if (StrUtil.isNotEmpty(rs)) {
			return true;
		}

		return false;
	}

	private void showLogo() throws IOException {
		ClassPathResource resource = new ClassPathResource("banner.txt");
		BufferedReader reader = resource.getReader(StandardCharsets.UTF_8);
		String str = null;
		StringBuilder stringBuilder = new StringBuilder();
		// 使用readLine() 比较方便的读取一行
		while (null != (str = reader.readLine())) {
			stringBuilder.append(str).append("\n");
		}
		reader.close();// 关闭流

		stringBuilder.append("nginxWebUI ").append(versionConfig.currentVersion).append("\n");

		logger.info(stringBuilder.toString());

	}

	@Deprecated
	private void transferSql() {
		// 关闭sqlite连接
		dataSourceEmbed.getDataSource().close();
		// 建立h2连接
		HikariConfig dbConfig = new HikariConfig();
		dbConfig.setJdbcUrl("jdbc:h2:" + homeConfig.home + "h2");
		dbConfig.setUsername("sa");
		dbConfig.setPassword("");
		dbConfig.setMaximumPoolSize(1);
		HikariDataSource dataSourceH2 = new HikariDataSource(dbConfig);
		dataSourceEmbed.setDataSource(dataSourceH2);
		// 读取全部数据
		Map<String, List<?>> map = readAll();

		// 关闭h2连接
		dataSourceH2.close();

		// 重新建立sqlite连接
		dataSourceEmbed.init();

		// 导入数据
		insertAll(map);

		// 重命名h2文件
		FileUtil.rename(new File(homeConfig.home + "h2.mv.db"), homeConfig.home + "h2.mv.db.bak", true);
	}

	private Map<String, List<?>> readAll() {
		Map<String, List<?>> map = new HashMap<>();

		Set<Class<?>> set = ClassUtil.scanPackage(packageName);
		for (Class<?> clazz : set) {
			Table table = clazz.getAnnotation(Table.class);
			if (table != null) {
				try {
					List<Map<String, Object>> list = jdbcTemplate.queryForList("SELECT * FROM " + SQLConstants.SUFFIX + StrUtil.toUnderlineCase(clazz.getSimpleName()) + SQLConstants.SUFFIX);

					map.put(clazz.getName(), sqlHelper.buildObjects(list, clazz));
				} catch (Exception e) {
					logger.info(e.getMessage(), e);
				}
			}
		}

		return map;
	}

	private void insertAll(Map<String, List<?>> map) {
		try {
			for (String key : map.keySet()) {
				sqlHelper.deleteByQuery(new ConditionAndWrapper(), Class.forName(key));

				sqlHelper.insertAll(map.get(key));
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
	}

	private void initDefaultTemplates() {
		// 所有模板 def="" → 純模板庫，不會自動套用到任何 server/location
		// 用戶需手動在 server/location 編輯頁面選擇要套用的模板

		// ── 代理類 ──
		addTemplate("WebSocket Proxy (WebSocket 代理)", "", "proxy", new String[][] {
			{ "proxy_http_version", "1.1" },
			{ "proxy_set_header", "Upgrade $http_upgrade" },
			{ "proxy_set_header", "Connection \"upgrade\"" },
		});

		addTemplate("Proxy Headers (代理請求頭)", "", "proxy", new String[][] {
			{ "proxy_set_header", "Host $host" },
			{ "proxy_set_header", "X-Real-IP $remote_addr" },
			{ "proxy_set_header", "X-Forwarded-For $proxy_add_x_forwarded_for" },
			{ "proxy_set_header", "X-Forwarded-Proto $scheme" },
			{ "proxy_set_header", "X-Forwarded-Host $http_host" },
			{ "proxy_set_header", "X-Forwarded-Port $server_port" },
		});

		addTemplate("Large File Upload (大檔案上傳)", "", "proxy", new String[][] {
			{ "client_max_body_size", "500m" },
			{ "proxy_read_timeout", "600s" },
			{ "proxy_send_timeout", "600s" },
			{ "proxy_connect_timeout", "600s" },
			{ "proxy_request_buffering", "off" },
		});

		// ── 緩存類 ──
		addTemplate("Static File Cache (靜態檔案快取)", "", "cache", new String[][] {
			{ "expires", "30d" },
			{ "add_header", "Cache-Control \"public, no-transform\"" },
			{ "access_log", "off" },
		});

		addTemplate("Proxy Cache (代理快取)", "", "cache", new String[][] {
			{ "proxy_cache_valid", "200 302 1h" },
			{ "proxy_cache_valid", "404 1m" },
			{ "proxy_cache_use_stale", "error timeout updating http_500 http_502 http_503 http_504" },
			{ "add_header", "X-Cache-Status $upstream_cache_status" },
		});

		// ── 跨域 CORS ──
		addTemplate("CORS Allow All (允許全部跨域)", "", "cors", new String[][] {
			{ "add_header", "Access-Control-Allow-Origin *" },
			{ "add_header", "Access-Control-Allow-Methods \"GET, POST, PUT, DELETE, OPTIONS\"" },
			{ "add_header", "Access-Control-Allow-Headers \"DNT,User-Agent,X-Requested-With,If-Modified-Since,Cache-Control,Content-Type,Range,Authorization\"" },
			{ "add_header", "Access-Control-Max-Age 1728000" },
		});

		addTemplate("CORS Specific Origin (指定來源跨域)", "", "cors", new String[][] {
			{ "add_header", "Access-Control-Allow-Origin $http_origin" },
			{ "add_header", "Access-Control-Allow-Methods \"GET, POST, PUT, DELETE, OPTIONS\"" },
			{ "add_header", "Access-Control-Allow-Headers \"DNT,User-Agent,X-Requested-With,If-Modified-Since,Cache-Control,Content-Type,Range,Authorization\"" },
			{ "add_header", "Access-Control-Allow-Credentials true" },
		});

		// ── 限流 Rate Limiting ──
		addTemplate("Rate Limit (http) (請求速率限制 — http 層)", "", "rateLimit", new String[][] {
			{ "limit_req_zone", "$binary_remote_addr zone=req_limit:10m rate=10r/s" },
		});
		addTemplate("Rate Limit (server) (請求速率限制 — server 層)", "", "rateLimit", new String[][] {
			{ "limit_req", "zone=req_limit burst=20 nodelay" },
			{ "limit_req_status", "429" },
		});

		addTemplate("Connection Limit (http) (連線數限制 — http 層)", "", "rateLimit", new String[][] {
			{ "limit_conn_zone", "$binary_remote_addr zone=conn_limit:10m" },
		});
		addTemplate("Connection Limit (server) (連線數限制 — server 層)", "", "rateLimit", new String[][] {
			{ "limit_conn", "conn_limit 50" },
			{ "limit_conn_status", "429" },
		});
		// stream 專用 zone 名 s_conn_perip — 不可與 http 的 conn_limit 同名（跨模組 shared zone 衝突）
		// 先 stream 層宣告，再 stream server 層使用；勿抄 limit_conn_status（stream 無狀態碼）
		addTemplate("Connection Limit (stream) (連線數限制 — stream 層)", "stream", "rateLimit", new String[][] {
			{ "limit_conn_zone", "$binary_remote_addr zone=s_conn_perip:10m" },
			{ "limit_conn_log_level", "warn" },
		});
		addTemplate("Connection Limit (stream server) (連線數限制 — stream server 層)", "server1", "rateLimit", new String[][] {
			{ "limit_conn", "s_conn_perip 50" },
		});

		// ── 安全類 ──
		addTemplate("Security Headers (HSTS) (安全標頭 HSTS)", "", "security", new String[][] {
			{ "add_header", "Strict-Transport-Security \"max-age=31536000; includeSubDomains; preload\" always" },
			{ "add_header", "Content-Security-Policy \"default-src 'self'\"" },
			{ "add_header", "Permissions-Policy \"camera=(), microphone=(), geolocation=()\"" },
		});

		addTemplate("Hide Server Info (隱藏伺服器資訊)", "", "security", new String[][] {
			{ "server_tokens", "off" },
			{ "more_clear_headers", "Server" },
			{ "more_clear_headers", "X-Powered-By" },
		});

		addTemplate("Block Sensitive Paths (阻擋敏感路徑)", "", "security", new String[][] {
			{ "deny", "all" },
			{ "return", "404" },
		});

		// ── GeoIP 存取控制 ──
		addTemplate("GeoIP Allow TW Only (GeoIP 僅允許台灣)", "", "geoip", new String[][] {
			{ "if", "($geoip2_data_country_code != \"TW\") {\r\n        return 403;\r\n    }" },
		});

		addTemplate("GeoIP Log Country (GeoIP 記錄國家)", "", "geoip", new String[][] {
			{ "add_header", "X-Country $geoip2_data_country_code" },
			{ "add_header", "X-City $geoip2_data_city_name" },
		});

		addTemplate("ASN Block List (ASN 封鎖清單)", "", "geoip", new String[][] {
			{ "if", "($blocked_asn) {\r\n        return 403;\r\n    }" },
		});

		addTemplate("ASN Log Info (ASN 記錄資訊)", "", "geoip", new String[][] {
			{ "add_header", "X-ASN $geoip2_data_asn" },
			{ "add_header", "X-ASN-Org $geoip2_data_asn_org" },
		});

		// ── CrowdSec Bouncer ──
		addTemplate("CrowdSec Auth Request (CrowdSec 認證請求)", "", "crowdsec", new String[][] {
			{ "auth_request", "/crowdsec-check" },
			{ "auth_request_set", "$auth_status $upstream_status" },
		});

		// 其餘開源模組社群範本（與 seedModuleCommunityTemplatesIfMissing 同源）
		seedModuleCommunityTemplatesCore();
	}

	private void addTemplate(String name, String def, String groupName, String[][] params) {
		Template template = new Template();
		template.setName(name);
		template.setDef(def);
		template.setGroupName(groupName);

		List<Param> paramList = new ArrayList<>();
		for (String[] pair : params) {
			Param param = new Param();
			param.setName(pair[0]);
			param.setValue(pair[1]);
			paramList.add(param);
		}

		templateService.addOver(template, paramList);
	}

	/** 空庫：依 NginxService.MODULE_CATALOG 建立模組管理列（預設不啟用）。 */
	private List<Module> buildModuleCatalogSeed() {
		List<Module> modules = new ArrayList<>();
		long seq = 0;
		for (String[] row : NginxService.MODULE_CATALOG) {
			modules.add(new Module(row[0], row[1], false, seq++));
		}
		return modules;
	}

	/** 既有庫：補 catalog 中缺少的 name，不改既有 enable。 */
	private int seedMissingModulesFromCatalog() {
		List<Module> existing = sqlHelper.findAll(Module.class);
		java.util.HashSet<String> names = new java.util.HashSet<>();
		long maxSeq = -1;
		for (Module m : existing) {
			if (m.getName() != null) {
				names.add(m.getName());
			}
			if (m.getSeq() != null && m.getSeq() > maxSeq) {
				maxSeq = m.getSeq();
			}
		}
		long seq = maxSeq + 1;
		int added = 0;
		for (String[] row : NginxService.MODULE_CATALOG) {
			if (names.contains(row[0])) {
				continue;
			}
			sqlHelper.insert(new Module(row[0], row[1], false, seq++));
			added++;
		}
		return added;
	}

	/** 刪除不在 MODULE_CATALOG 的 module 列（下架未維護／高風險模組）。 */
	private int pruneModulesNotInCatalog() {
		java.util.HashSet<String> keep = new java.util.HashSet<>();
		for (String[] row : NginxService.MODULE_CATALOG) {
			keep.add(row[0]);
		}
		int pruned = 0;
		for (Module mod : sqlHelper.findAll(Module.class)) {
			if (mod.getName() == null || !keep.contains(mod.getName())) {
				sqlHelper.deleteById(mod.getId(), Module.class);
				pruned++;
			}
		}
		return pruned;
	}

	/** 依 MODULE_CATALOG 重寫 seq（0..n），保證 UI 與 load 序一致。 */
	private void resequenceModulesToCatalogOrder() {
		java.util.HashMap<String, Module> byName = new java.util.HashMap<>();
		for (Module mod : sqlHelper.findAll(Module.class)) {
			if (mod.getName() != null) {
				byName.put(mod.getName(), mod);
			}
		}
		long seq = 0;
		for (String[] row : NginxService.MODULE_CATALOG) {
			Module mod = byName.get(row[0]);
			if (mod == null) {
				continue;
			}
			mod.setSeq(seq++);
			if (StrUtil.isNotEmpty(row[1])) {
				mod.setDescrKey(row[1]);
			}
			sqlHelper.updateById(mod);
		}
	}

	/**
	 * 既有 DB 補 stream 連線限制模板。名稱已存在則跳過（不改參數、不覆蓋使用者改過的列）。
	 * zone 必須用 s_conn_perip，不可重用 http 的 conn_limit。
	 */
	private void seedStreamConnLimitTemplatesIfMissing() {
		String streamZoneName = "Connection Limit (stream) (連線數限制 — stream 層)";
		String streamServerName = "Connection Limit (stream server) (連線數限制 — stream server 層)";
		int added = 0;
		if (templateService.getCountByName(streamZoneName) == 0) {
			addTemplate(streamZoneName, "stream", "rateLimit", new String[][] {
				{ "limit_conn_zone", "$binary_remote_addr zone=s_conn_perip:10m" },
				{ "limit_conn_log_level", "warn" },
			});
			added++;
		}
		if (templateService.getCountByName(streamServerName) == 0) {
			// server1 = TCP stream server（與 ConfService proxyType=1 的 type 字串一致）
			addTemplate(streamServerName, "server1", "rateLimit", new String[][] {
				{ "limit_conn", "s_conn_perip 50" },
			});
			added++;
		}
		if (added > 0) {
			logger.info("Migration: seeded {} stream connection-limit template(s)", added);
		}
	}

	/**
	 * 修正被誤標 def=stream / def=server 的模板，避免 ConfService / ParamService 自動注入非法指令。
	 * stream{} 禁止 HTTP 的 if / return 403 / add_header 等。
	 * @return 有改動的 template 列數（約）
	 */
	private int sanitizeStreamDefTemplates() {
		int touched = 0;
		String streamZoneHint = "Connection Limit (stream) (連線數限制 — stream 層)";
		String streamServerHint = "Connection Limit (stream server) (連線數限制 — stream server 層)";

		List<Template> all = sqlHelper.findAll(Template.class);
		for (Template tpl : all) {
			if (tpl.getName() == null) {
				continue;
			}
			String name = tpl.getName();
			boolean changed = false;

			if (name.equals(streamZoneHint) || (name.startsWith("Connection Limit (stream)") && !name.contains("server"))) {
				if (!"stream".equals(tpl.getDef())) {
					tpl.setDef("stream");
					changed = true;
				}
			} else if (name.equals(streamServerHint) || name.startsWith("Connection Limit (stream server)")) {
				// TCP + UDP stream server 皆可套用 limit_conn
				String want = "server1,server2";
				if (!want.equals(TemplateDefUtils.normalize(tpl.getDef()))) {
					tpl.setDef(want);
					changed = true;
				}
			} else if (StrUtil.isNotEmpty(tpl.getDef())) {
				// 僅剔除「參數不允許」的層級，保留合法的 http/server/location 多選
				List<Param> params = sqlHelper.findListByQuery(
						new ConditionAndWrapper().eq(Param::getTemplateId, tpl.getId()), Param.class);
				String filtered = TemplateDefUtils.normalizeAndFilter(tpl.getDef(), params);
				if (!filtered.equals(TemplateDefUtils.normalize(tpl.getDef()))) {
					tpl.setDef(filtered);
					changed = true;
					logger.info("Migration: filtered template def for '{}': -> '{}'", name, filtered);
				}
			}

			if (changed) {
				sqlHelper.updateById(tpl);
				touched++;
			}

			// GeoIP Allow：確保 if 本體為 HTTP 語法（僅手動套用到 server/location）
			if (name.startsWith("GeoIP Allow TW Only")) {
				List<Param> params = sqlHelper.findListByQuery(
						new ConditionAndWrapper().eq(Param::getTemplateId, tpl.getId()), Param.class);
				for (Param p : params) {
					if ("if".equals(p.getName()) && p.getValue() != null
							&& (p.getValue().contains("!~") || p.getValue().contains("stream"))) {
						p.setValue("($geoip2_data_country_code != \"TW\") {\r\n        return 403;\r\n    }");
						sqlHelper.updateById(p);
						touched++;
					}
				}
			}

			// GeoIP Log：禁止用 HTTP 的 $request log_format 當 stream 參數；改回 add_header
			if (name.startsWith("GeoIP Log Country")) {
				List<Param> params = sqlHelper.findListByQuery(
						new ConditionAndWrapper().eq(Param::getTemplateId, tpl.getId()), Param.class);
				boolean hasLogFormat = false;
				for (Param p : params) {
					if ("log_format".equals(p.getName())) {
						sqlHelper.deleteById(p.getId(), Param.class);
						hasLogFormat = true;
						touched++;
					}
				}
				if (hasLogFormat || params.isEmpty()) {
					boolean hasCountry = false;
					boolean hasCity = false;
					params = sqlHelper.findListByQuery(
							new ConditionAndWrapper().eq(Param::getTemplateId, tpl.getId()), Param.class);
					for (Param p : params) {
						if ("add_header".equals(p.getName()) && p.getValue() != null) {
							if (p.getValue().contains("X-Country")) {
								hasCountry = true;
							}
							if (p.getValue().contains("X-City")) {
								hasCity = true;
							}
						}
					}
					if (!hasCountry) {
						Param p = new Param();
						p.setTemplateId(tpl.getId());
						p.setName("add_header");
						p.setValue("X-Country $geoip2_data_country_code");
						sqlHelper.insert(p);
						touched++;
					}
					if (!hasCity) {
						Param p = new Param();
						p.setTemplateId(tpl.getId());
						p.setName("add_header");
						p.setValue("X-City $geoip2_data_city_name");
						sqlHelper.insert(p);
						touched++;
					}
				}
			}
		}
		return touched;
	}

	/** geoip2 / map 變數多時避免 variables_hash 警告；已存在則不改。 */
	private void ensureVariablesHashHttpParams() {
		Http max = sqlHelper.findOneByQuery(
				new ConditionAndWrapper().eq(Http::getName, "variables_hash_max_size"), Http.class);
		if (max == null) {
			sqlHelper.insert(new Http("variables_hash_max_size", "2048", -2L, "base"));
		}
		Http bucket = sqlHelper.findOneByQuery(
				new ConditionAndWrapper().eq(Http::getName, "variables_hash_bucket_size"), Http.class);
		if (bucket == null) {
			sqlHelper.insert(new Http("variables_hash_bucket_size", "128", -1L, "base"));
		}
	}

	/** 空庫 initDefaultTemplates 直接插入社群範本。 */
	private void seedModuleCommunityTemplatesCore() {
		for (Object[] row : communityTemplateDefs()) {
			@SuppressWarnings("unchecked")
			String[][] params = (String[][]) row[3];
			addTemplate((String) row[0], (String) row[1], (String) row[2], params);
		}
	}

	/** 既有庫：同名略過。回傳新增筆數。 */
	private int seedModuleCommunityTemplatesIfMissing() {
		int added = 0;
		for (Object[] row : communityTemplateDefs()) {
			String name = (String) row[0];
			if (templateService.getCountByName(name) > 0) {
				continue;
			}
			@SuppressWarnings("unchecked")
			String[][] params = (String[][]) row[3];
			addTemplate(name, (String) row[1], (String) row[2], params);
			added++;
		}
		return added;
	}

	/**
	 * 社群慣用參數模板（參考 nginx.org / 模組 README）。
	 * row = { name, def, groupName, String[][] params }
	 * 原則：def 多為 ""（僅手動）；名稱含建議；不含舊版 geoip v1。
	 */
	private List<Object[]> communityTemplateDefs() {
		List<Object[]> list = new ArrayList<>();

		// ── compress（模組：brotli / zstd；http 參數頁可能已有全域值，此為可選包）──
		list.add(new Object[] { "Brotli Full (Brotli 完整 — 建議 http 層 / 需 brotli 模組)", "", "compress",
				new String[][] {
					{ "brotli", "on" },
					{ "brotli_comp_level", "6" },
					{ "brotli_static", "on" },
					{ "brotli_types", "text/plain text/css application/javascript application/json application/xml image/svg+xml" },
				} });
		list.add(new Object[] { "Zstd Full (Zstd 完整 — 建議 http 層 / 需 zstd 模組)", "", "compress",
				new String[][] {
					{ "zstd", "on" },
					{ "zstd_comp_level", "3" },
					{ "zstd_static", "on" },
					{ "zstd_types", "text/plain text/css application/javascript application/json application/xml image/svg+xml" },
				} });

		// ── observe：VTS（社群常用 vhost_traffic_status）──
		list.add(new Object[] { "VTS Zone (流量狀態 zone — 建議 http 層 / 需 vts 模組)", "", "observe",
				new String[][] {
					{ "vhost_traffic_status_zone", "shared:vhost_traffic_status:32m" },
					{ "vhost_traffic_status_filter_by_host", "on" },
				} });
		list.add(new Object[] { "VTS Status Location (狀態頁 — 建議 location=/status 且限制 IP)", "", "observe",
				new String[][] {
					{ "vhost_traffic_status_display", "" },
					{ "vhost_traffic_status_display_format", "html" },
					{ "access_log", "off" },
					{ "allow", "127.0.0.1" },
					{ "deny", "all" },
				} });

		// ── auth：JWT ──
		list.add(new Object[] { "Auth JWT (JWT 驗證 — 建議 location / 需 auth_jwt 模組)", "", "auth",
				new String[][] {
					{ "auth_jwt", "\"closed site\"" },
					{ "auth_jwt_key_file", "/etc/nginx/jwt/secret.jwk" },
					{ "auth_jwt_header", "Authorization" },
					{ "error_page", "401 = @error401" },
				} });

		// ── njs ──
		list.add(new Object[] { "njs Import (njs 載入 — 建議 http 層 / 需 http_js 模組)", "", "njs",
				new String[][] {
					{ "js_import", "main from conf.d/njs/main.js" },
					{ "js_path", "/etc/nginx/njs/;" },
				} });
		list.add(new Object[] { "njs Content (njs 回應 — 建議 location)", "", "njs",
				new String[][] {
					{ "js_content", "main.handler" },
				} });

		// ── keyval ──
		list.add(new Object[] { "Keyval Zone HTTP (keyval zone — 建議 http 層 / 需 keyval 模組)", "", "keyval",
				new String[][] {
					{ "keyval_zone", "zone=kv:1m" },
					{ "keyval", "$arg_key $kv_value zone=kv" },
				} });
		list.add(new Object[] { "Keyval Zone Stream (stream keyval — 建議 stream 層 / 需 stream_keyval)", "", "keyval",
				new String[][] {
					{ "keyval_zone", "zone=s_kv:1m" },
					{ "keyval", "$remote_addr $s_kv_val zone=s_kv" },
				} });

		// ── util（已移除 echo / upload* 等未維護模組對應範本）──
		list.add(new Object[] { "Set Misc Basics (set_misc 常用 — 建議 server/location / 需 NDK+set_misc)", "", "util",
				new String[][] {
					{ "set_secure_random_alphanum", "$sid 32" },
					{ "set_escape_uri", "$escaped $arg_q" },
				} });
		list.add(new Object[] { "Cookie Flag (Cookie 旗標 — 建議 location / 需 cookie_flag)", "", "util",
				new String[][] {
					{ "proxy_cookie_flags", "~ nosecure samesite=lax" },
				} });
		list.add(new Object[] { "Headers More Clear (清除敏感頭 — 需 headers_more)", "", "util",
				new String[][] {
					{ "more_clear_headers", "Server" },
					{ "more_clear_headers", "X-Powered-By" },
					{ "more_set_headers", "\"X-Content-Type-Options: nosniff\"" },
				} });

		// ── media ──
		list.add(new Object[] { "Image Filter Thumb (縮圖 — 建議 location / 需 image_filter)", "", "media",
				new String[][] {
					{ "image_filter", "resize 200 200" },
					{ "image_filter_jpeg_quality", "85" },
					{ "image_filter_buffer", "10M" },
				} });
		list.add(new Object[] { "VOD HLS Skeleton (VOD 骨架 — 需 vod 模組，路徑請改)", "", "media",
				new String[][] {
					{ "vod", "hls" },
					{ "vod_mode", "local" },
					{ "alias", "/var/media/;" },
					{ "add_header", "Access-Control-Allow-Origin *" },
				} });

		// ── realtime：Nchan ──
		list.add(new Object[] { "Nchan PubSub (Nchan 發訂 — 建議 location / 需 nchan)", "", "realtime",
				new String[][] {
					{ "nchan_pubsub", "" },
					{ "nchan_channel_id", "$arg_id" },
					{ "nchan_message_timeout", "5m" },
					{ "nchan_store_messages", "on" },
				} });

		// ── waf：NAXSI ──
		list.add(new Object[] { "NAXSI Basic (WAF 骨架 — 需 naxsi 與規則 include)", "", "waf",
				new String[][] {
					{ "SecRulesEnabled", "" },
					{ "DeniedUrl", "/RequestDenied" },
					{ "CheckRule", "\"$SQL >= 8\" BLOCK" },
					{ "CheckRule", "\"$RFI >= 8\" BLOCK" },
					{ "CheckRule", "\"$TRAVERSAL >= 4\" BLOCK" },
					{ "CheckRule", "\"$XSS >= 8\" BLOCK" },
				} });

		// ── mail（非 HTTP；僅範本庫）──
		list.add(new Object[] { "Mail Auth Basic (mail 認證骨架 — 需 mail 模組)", "", "mail",
				new String[][] {
					{ "auth_http", "127.0.0.1:9000/auth" },
					{ "auth_http_timeout", "5s" },
					{ "proxy_pass_error_message", "on" },
				} });

		// ── cache purge 使用端 ──
		list.add(new Object[] { "Cache Purge Location (快取清除 — 建議 location 且限制方法/IP)", "", "cache",
				new String[][] {
					{ "proxy_cache_purge", "PURGE from $host$request_uri" },
					{ "allow", "127.0.0.1" },
					{ "deny", "all" },
				} });

		// ── dynamic healthcheck 提示 ──
		list.add(new Object[] { "Dynamic Healthcheck Hint (動態健康檢查 — 需 dynamic_healthcheck 模組)", "", "upstream_ext",
				new String[][] {
					{ "healthcheck_uri", "/health" },
					{ "healthcheck_interval", "5s" },
					{ "healthcheck_timeout", "2s" },
					{ "healthcheck_fall", "3" },
					{ "healthcheck_rise", "2" },
				} });

		return list;
	}

	// 既有 DB 內舊英文 template name → 加上「English (中文)」註解
	// 只 rename 字面完全匹配舊英文名的 record，使用者改過名的不動
	private void migrateTemplateNameCn() {
		String[][] renameMap = {
			{ "WebSocket Proxy",          "WebSocket Proxy (WebSocket 代理)" },
			{ "Proxy Headers",            "Proxy Headers (代理請求頭)" },
			{ "Large File Upload",        "Large File Upload (大檔案上傳)" },
			{ "Static File Cache",        "Static File Cache (靜態檔案快取)" },
			{ "Proxy Cache",              "Proxy Cache (代理快取)" },
			{ "CORS Allow All",           "CORS Allow All (允許全部跨域)" },
			{ "CORS Specific Origin",     "CORS Specific Origin (指定來源跨域)" },
			{ "Rate Limit (http)",        "Rate Limit (http) (請求速率限制 — http 層)" },
			{ "Rate Limit (server)",      "Rate Limit (server) (請求速率限制 — server 層)" },
			{ "Connection Limit (http)",  "Connection Limit (http) (連線數限制 — http 層)" },
			{ "Connection Limit (server)","Connection Limit (server) (連線數限制 — server 層)" },
			{ "Security Headers (HSTS)",  "Security Headers (HSTS) (安全標頭 HSTS)" },
			{ "Hide Server Info",         "Hide Server Info (隱藏伺服器資訊)" },
			{ "Block Sensitive Paths",    "Block Sensitive Paths (阻擋敏感路徑)" },
			{ "GeoIP Allow TW Only",      "GeoIP Allow TW Only (GeoIP 僅允許台灣)" },
			{ "GeoIP Log Country",        "GeoIP Log Country (GeoIP 記錄國家)" },
			{ "ASN Block List",           "ASN Block List (ASN 封鎖清單)" },
			{ "ASN Log Info",             "ASN Log Info (ASN 記錄資訊)" },
			{ "CrowdSec Auth Request",    "CrowdSec Auth Request (CrowdSec 認證請求)" },
		};

		List<Template> templates = sqlHelper.findAll(Template.class);
		int renamed = 0;
		for (Template tpl : templates) {
			for (String[] mapping : renameMap) {
				if (mapping[0].equals(tpl.getName())) {
					tpl.setName(mapping[1]);
					sqlHelper.updateById(tpl);
					renamed++;
					break;
				}
			}
		}
		logger.info("Migration: renamed {} templates with Chinese annotation", renamed);
	}

	private void migrateTemplateGroups() {
		// Map template name patterns to group names
		String[][] nameToGroup = {
			{ "WebSocket Proxy",        "proxy" },
			{ "Proxy Headers",          "proxy" },
			{ "Large File Upload",      "proxy" },
			{ "Static File Cache",      "cache" },
			{ "Proxy Cache",            "cache" },
			{ "CORS Allow All",         "cors" },
			{ "CORS Specific Origin",   "cors" },
			{ "Rate Limit",             "rateLimit" },
			{ "Connection Limit",       "rateLimit" },
			{ "Security Headers",       "security" },
			{ "Hide Server Info",       "security" },
			{ "Block Sensitive Paths",  "security" },
			{ "GeoIP",                  "geoip" },
			{ "CrowdSec",              "crowdsec" },
		};

		List<Template> templates = sqlHelper.findAll(Template.class);
		for (Template tpl : templates) {
			if (StrUtil.isNotBlank(tpl.getGroupName())) continue;

			for (String[] mapping : nameToGroup) {
				if (tpl.getName().startsWith(mapping[0])) {
					tpl.setGroupName(mapping[1]);
					sqlHelper.updateById(tpl);
					break;
				}
			}
		}
	}

	private void addAdmin() {
		Long adminCount = sqlHelper.findAllCount(Admin.class);
		if (adminCount > 0) {
			return;
		}

		Admin admin = new Admin();
		admin.setName(initAdmin);
		admin.setPass(EncodePassUtils.encode(initPass));
		admin.setApi(initApi);
		admin.setType(0);

		sqlHelper.insert(admin);

	}
}
