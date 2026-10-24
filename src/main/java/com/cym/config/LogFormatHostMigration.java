package com.cym.config;

import java.util.List;

import com.cym.model.Http;
import com.cym.sqlhelper.utils.ConditionAndWrapper;
import com.cym.sqlhelper.utils.SqlHelper;

/**
 * 種子 log_format main 末尾補上 "$host"，讓「最近被擋下的 IP」能依站台篩選。
 * 只改內容仍等於種子值（CRLF 或 LF 皆可）的列；使用者改過的保留。
 */
public final class LogFormatHostMigration {
	public static final String SEED_LEGACY = "main '$remote_addr - $remote_user [$time_local] \"$request\" '\r\n"
			+ "                      '$status $body_bytes_sent \"$http_referer\" '\r\n"
			+ "                      '\"$http_user_agent\" \"$geoip2_data_country_code\" \"$geoip2_data_city_name\" \"$geoip2_data_asn\" \"$geoip2_data_asn_org\"'";
	public static final String SEED_WITH_HOST = SEED_LEGACY.substring(0, SEED_LEGACY.length() - 1) + " \"$host\"'";

	private LogFormatHostMigration() {
	}

	/** @return 改寫的列數 */
	public static int run(SqlHelper sqlHelper) {
		int changed = 0;
		List<Http> rows = sqlHelper.findListByQuery(new ConditionAndWrapper().eq("name", "log_format"), Http.class);
		for (Http http : rows) {
			String value = http.getValue();
			if (value != null && SEED_LEGACY.equals(value.replace("\r\n", "\n").replace("\n", "\r\n"))) {
				http.setValue(SEED_WITH_HOST);
				sqlHelper.updateById(http);
				changed++;
			}
		}
		return changed;
	}
}
