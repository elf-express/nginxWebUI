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
import com.cym.model.Http;
import com.cym.model.Param;
import com.cym.model.Template;
import com.cym.service.BasicService;
import com.cym.service.ConfService;
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
			// 載入動態模組（stream_module 必須在 stream_geoip2_module 之前）
			basics.add(new Basic("load_module", "/usr/lib/nginx/modules/ngx_stream_module.so", -10l));
			basics.add(new Basic("load_module", "/usr/lib/nginx/modules/ngx_stream_geoip2_module.so", -9l));
			basics.add(new Basic("load_module", "/usr/lib/nginx/modules/ngx_http_geoip2_module.so", 0l));
			basics.add(new Basic("load_module", "/usr/lib/nginx/modules/ngx_http_brotli_filter_module.so", 0l));
			basics.add(new Basic("load_module", "/usr/lib/nginx/modules/ngx_http_brotli_static_module.so", 0l));
			basics.add(new Basic("load_module", "/usr/lib/nginx/modules/ngx_http_headers_more_filter_module.so", 0l));
			basics.add(new Basic("load_module", "/usr/lib/nginx/modules/ngx_http_cache_purge_module.so", 0l));
			sqlHelper.insertAll(basics);
		}

		// 初始化http值
		count = sqlHelper.findAllCount(Http.class);
		if (count == 0) {
			List<Http> https = new ArrayList<Http>();
			long seq = 0;
			https.add(new Http("include", "mime.types", seq++));
			https.add(new Http("default_type", "application/octet-stream", seq++));

			// Real IP（Cloudflare）
			https.add(new Http("include", "/etc/nginx/geoip/realip.conf", seq++));

			// GeoIP2（國家、城市、ASN）
			https.add(new Http("geoip2", "/etc/nginx/geoip/GeoLite2-Country.mmdb {\r\n    auto_reload 60m;\r\n    $geoip2_data_country_code country iso_code;\r\n    $geoip2_data_country_name country names en;\r\n}", seq++));
			https.add(new Http("geoip2", "/etc/nginx/geoip/GeoLite2-City.mmdb {\r\n    auto_reload 60m;\r\n    $geoip2_data_city_name city names en;\r\n}", seq++));
			https.add(new Http("geoip2", "/etc/nginx/geoip/GeoLite2-ASN.mmdb {\r\n    auto_reload 60m;\r\n    $geoip2_data_asn autonomous_system_number;\r\n    $geoip2_data_asn_org autonomous_system_organization;\r\n}", seq++));

			// Gzip 壓縮
			https.add(new Http("gzip", "on", seq++));
			https.add(new Http("gzip_min_length", "1k", seq++));
			https.add(new Http("gzip_comp_level", "5", seq++));
			https.add(new Http("gzip_types", "text/plain application/json application/javascript text/css application/xml text/javascript application/x-httpd-php image/svg+xml", seq++));

			// Brotli 壓縮（比 gzip 更高效）
			https.add(new Http("brotli", "on", seq++));
			https.add(new Http("brotli_comp_level", "6", seq++));
			https.add(new Http("brotli_types", "text/plain application/json application/javascript text/css application/xml text/javascript image/svg+xml", seq++));

			// 安全 Headers
			https.add(new Http("add_header", "X-Frame-Options SAMEORIGIN", seq++));
			https.add(new Http("add_header", "X-Content-Type-Options nosniff", seq++));
			https.add(new Http("add_header", "X-XSS-Protection \"1; mode=block\"", seq++));
			https.add(new Http("add_header", "Referrer-Policy \"strict-origin-when-cross-origin\"", seq++));

			// 日誌格式（含真實 IP + GeoIP）
			https.add(new Http("log_format", "main '$remote_addr - $remote_user [$time_local] \"$request\" '\r\n                      '$status $body_bytes_sent \"$http_referer\" '\r\n                      '\"$http_user_agent\" \"$geoip2_data_country_code\" \"$geoip2_data_city_name\"'", seq++));

			// 預設開啟日誌（供 Promtail / CrowdSec 收集）
			https.add(new Http("access_log", homeConfig.home + "log/access.log main", seq++));
			https.add(new Http("error_log", homeConfig.home + "log/error.log", seq++));

			sqlHelper.insertAll(https);
		}

		// 初始化预设模板
		Long templateCount = sqlHelper.findAllCount(Template.class);
		if (templateCount == 0) {
			initDefaultTemplates();
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
		// ── 代理類 ──
		addTemplate("WebSocket Proxy", "location", new String[][] {
			{ "proxy_http_version", "1.1" },
			{ "proxy_set_header", "Upgrade $http_upgrade" },
			{ "proxy_set_header", "Connection \"upgrade\"" },
		});

		addTemplate("Proxy Headers", "location", new String[][] {
			{ "proxy_set_header", "Host $host" },
			{ "proxy_set_header", "X-Real-IP $remote_addr" },
			{ "proxy_set_header", "X-Forwarded-For $proxy_add_x_forwarded_for" },
			{ "proxy_set_header", "X-Forwarded-Proto $scheme" },
			{ "proxy_set_header", "X-Forwarded-Host $http_host" },
			{ "proxy_set_header", "X-Forwarded-Port $server_port" },
		});

		addTemplate("Large File Upload", "server", new String[][] {
			{ "client_max_body_size", "500m" },
			{ "proxy_read_timeout", "600s" },
			{ "proxy_send_timeout", "600s" },
			{ "proxy_connect_timeout", "600s" },
			{ "proxy_request_buffering", "off" },
		});

		// ── 緩存類 ──
		addTemplate("Static File Cache", "location", new String[][] {
			{ "expires", "30d" },
			{ "add_header", "Cache-Control \"public, no-transform\"" },
			{ "access_log", "off" },
		});

		addTemplate("Proxy Cache", "location", new String[][] {
			{ "proxy_cache_valid", "200 302 1h" },
			{ "proxy_cache_valid", "404 1m" },
			{ "proxy_cache_use_stale", "error timeout updating http_500 http_502 http_503 http_504" },
			{ "add_header", "X-Cache-Status $upstream_cache_status" },
		});

		// ── 跨域 CORS ──
		addTemplate("CORS Allow All", "location", new String[][] {
			{ "add_header", "Access-Control-Allow-Origin *" },
			{ "add_header", "Access-Control-Allow-Methods \"GET, POST, PUT, DELETE, OPTIONS\"" },
			{ "add_header", "Access-Control-Allow-Headers \"DNT,User-Agent,X-Requested-With,If-Modified-Since,Cache-Control,Content-Type,Range,Authorization\"" },
			{ "add_header", "Access-Control-Max-Age 1728000" },
		});

		addTemplate("CORS Specific Origin", "server", new String[][] {
			{ "add_header", "Access-Control-Allow-Origin $http_origin" },
			{ "add_header", "Access-Control-Allow-Methods \"GET, POST, PUT, DELETE, OPTIONS\"" },
			{ "add_header", "Access-Control-Allow-Headers \"DNT,User-Agent,X-Requested-With,If-Modified-Since,Cache-Control,Content-Type,Range,Authorization\"" },
			{ "add_header", "Access-Control-Allow-Credentials true" },
		});

		// ── 限流 Rate Limiting ──
		// limit_req_zone / limit_conn_zone 只能放在 http block，不能放在 server block
		addTemplate("Rate Limit (http)", "http", new String[][] {
			{ "limit_req_zone", "$binary_remote_addr zone=req_limit:10m rate=10r/s" },
		});
		addTemplate("Rate Limit (server)", "server", new String[][] {
			{ "limit_req", "zone=req_limit burst=20 nodelay" },
			{ "limit_req_status", "429" },
		});

		addTemplate("Connection Limit (http)", "http", new String[][] {
			{ "limit_conn_zone", "$binary_remote_addr zone=conn_limit:10m" },
		});
		addTemplate("Connection Limit (server)", "server", new String[][] {
			{ "limit_conn", "conn_limit 50" },
			{ "limit_conn_status", "429" },
		});

		// ── 安全類 ──
		addTemplate("Security Headers (HSTS)", "server", new String[][] {
			{ "add_header", "Strict-Transport-Security \"max-age=31536000; includeSubDomains; preload\" always" },
			{ "add_header", "Content-Security-Policy \"default-src 'self'\"" },
			{ "add_header", "Permissions-Policy \"camera=(), microphone=(), geolocation=()\"" },
		});

		addTemplate("Hide Server Info", "server", new String[][] {
			{ "server_tokens", "off" },
			{ "more_clear_headers", "Server" },
			{ "more_clear_headers", "X-Powered-By" },
		});

		addTemplate("Block Sensitive Paths", "location", new String[][] {
			{ "deny", "all" },
			{ "return", "404" },
		});

		// ── GeoIP 存取控制 ──
		addTemplate("GeoIP Allow TW Only", "server", new String[][] {
			{ "if", "($geoip2_data_country_code != \"TW\") {\r\n        return 403;\r\n    }" },
		});

		addTemplate("GeoIP Log Country", "server", new String[][] {
			{ "add_header", "X-Country $geoip2_data_country_code" },
			{ "add_header", "X-City $geoip2_data_city_name" },
		});

		// ── CrowdSec Bouncer ──
		addTemplate("CrowdSec Auth Request", "server", new String[][] {
			{ "auth_request", "/crowdsec-check" },
			{ "auth_request_set", "$auth_status $upstream_status" },
		});
	}

	private void addTemplate(String name, String def, String[][] params) {
		Template template = new Template();
		template.setName(name);
		template.setDef(def);

		List<Param> paramList = new ArrayList<>();
		for (String[] pair : params) {
			Param param = new Param();
			param.setName(pair[0]);
			param.setValue(pair[1]);
			paramList.add(param);
		}

		templateService.addOver(template, paramList);
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
