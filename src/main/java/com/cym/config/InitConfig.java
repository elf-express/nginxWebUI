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
import com.cym.model.GeoRule;
import com.cym.model.Http;
import com.cym.model.Param;
import com.cym.model.Module;
import com.cym.model.Template;
import com.cym.service.BasicService;
import com.cym.service.ConfService;
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
	DataSourceEmbed dataSourceEmbed;
	@Inject("${project.beanPackage}")
	String packageName;
	@Inject("${project.findPass}")
	Boolean findPass;
	@Inject("${spring.database.type}")
	String databaseType;

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
			https.add(new Http("include", "/etc/nginx/geoip/realip.conf", seq++, "realip"));

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

		// 初始化模組管理表
		Long moduleCount = sqlHelper.findAllCount(Module.class);
		if (moduleCount == 0) {
			List<Module> modules = new ArrayList<>();
			long seq = 0;
			modules.add(new Module("ngx_stream_module.so",                    "descrStream",          false, seq++));
			modules.add(new Module("ngx_stream_geoip2_module.so",             "descrStreamGeoip2",    false, seq++));
			modules.add(new Module("ngx_http_geoip2_module.so",               "descrHttpGeoip2",      false, seq++));
			modules.add(new Module("ndk_http_module.so",                      "descrNdk",             false, seq++));
			modules.add(new Module("ngx_http_lua_module.so",                  "descrLua",             false, seq++));
			modules.add(new Module("ngx_http_brotli_filter_module.so",        "descrBrotliFilter",    false, seq++));
			modules.add(new Module("ngx_http_brotli_static_module.so",        "descrBrotliStatic",    false, seq++));
			modules.add(new Module("ngx_http_zstd_filter_module.so",          "descrZstdFilter",      false, seq++));
			modules.add(new Module("ngx_http_zstd_static_module.so",          "descrZstdStatic",      false, seq++));
			modules.add(new Module("ngx_http_headers_more_filter_module.so",  "descrHeadersMore",     false, seq++));
			modules.add(new Module("ngx_http_cache_purge_module.so",          "descrCachePurge",      false, seq++));
			sqlHelper.insertAll(modules);
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

		if (SystemTool.isLinux()) {
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
