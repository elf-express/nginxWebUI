package com.cym.utils;

import java.io.File;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.RuntimeUtil;
import cn.hutool.system.SystemUtil;

@Component
public class UpdateUtils {
	@Inject("${server.port}")
	String port;
	@Inject("${project.home}")
	String home;

	@Inject("${spring.database.type:}")
	String type;
	@Inject("${spring.datasource.url:}")
	String url;
	@Inject("${spring.datasource.username:}")
	String username;
	@Inject("${spring.datasource.password:}")
	String password;

	private static final Logger LOG = LoggerFactory.getLogger(UpdateUtils.class);

	public void run(String path) {
		ThreadUtil.safeSleep(2000);
		
		// linux更新,去掉版本号并覆盖源文件
		if(!SystemUtil.getOsInfo().isWindows()) {
			String jarPath = JarUtil.getCurrentFile().getParent() + File.separator + "nginxWebUI.jar";
			FileUtil.rename(new File(path), jarPath, true);
			path = jarPath;
		}

		String param = " --server.port=" + port + " --project.home=" + home;
		String logParam = param;

		if ("mysql".equalsIgnoreCase(type)) {
			// 執行用與記 log 用的參數分開組:前者帶真密碼,後者從頭就是 ***。
			// 先組再 replace 也能遮住,但密碼仍流進了 log 字串,靜態分析追不出它被遮掉。
			String dbParam = " --spring.database.type=" + type //
					+ " --spring.datasource.url=" + url //
					+ " --spring.datasource.username=" + username;
			param += dbParam + " --spring.datasource.password=" + password;
			logParam += dbParam + " --spring.datasource.password=***";
		}

		LOG.info(buildCmd(path, logParam));
		RuntimeUtil.exec(buildCmd(path, param));
	}

	private static String buildCmd(String path, String param) {
		if (SystemTool.isWindows()) {
			return "java -jar -Dfile.encoding=UTF-8 " + path + param;
		}
		return "nohup java -jar -Dfile.encoding=UTF-8 " + path + param + " > /dev/null &";
	}
}
