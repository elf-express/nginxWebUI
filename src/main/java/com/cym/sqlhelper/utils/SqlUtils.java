package com.cym.sqlhelper.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cym.config.SQLConstants;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.hutool.core.util.StrUtil;

@Component
public class SqlUtils {
	static Logger logger = LoggerFactory.getLogger(SqlUtils.class);

	@Inject("${project.sqlPrint:false}")
	Boolean print;
	@Inject
	JdbcTemplate jdbcTemplate;

	String separator = System.getProperty("line.separator");

	public String formatSql(String sql) {
		if (StrUtil.isEmpty(sql)) {
			return "";
		}

		sql = sql.replace("FROM", separator + "FROM")//
				.replace("WHERE", separator + "WHERE")//
				.replace("ORDER", separator + "ORDER")//
				.replace("LIMIT", separator + "LIMIT")//
				.replace("VALUES", separator + "VALUES");//

		return sql;
	}

	public void checkOrCreateTable(Class<?> clazz) {
		String sql = "CREATE TABLE IF NOT EXISTS " + SQLConstants.SUFFIX + StrUtil.toUnderlineCase(clazz.getSimpleName()) + SQLConstants.SUFFIX + " (id VARCHAR(32) NOT NULL PRIMARY KEY)";
		logQuery(formatSql(sql));
		jdbcTemplate.executeQuietly(formatSql(sql));

	}

	public void logQuery(String sql) {
		logQuery(sql, null);
	}

	public void logQuery(String sql, Object[] params) {
		if (print) {
			try {
				if (params != null) {
					for (Object object : params) {

						if (object instanceof String) {
							object = object.toString().replace("$", "RDS_CHAR_DOLLAR");
							sql = sql.replaceFirst("\\?", "'" + object + "'").replace("RDS_CHAR_DOLLAR", "$");
						} else {
							sql = sql.replaceFirst("\\?", String.valueOf(object));
						}

					}
				}
				logger.info(sql);
			} catch (Exception e) {
				logger.error(e.getMessage(), e);
			}
		}
	}

	public void checkOrCreateIndex(Class<?> clazz, String name, boolean unique, List<Map<String, Object>> indexs) {
		checkOrCreateIndex(clazz, new String[] { name }, unique, indexs);
	}

	public void checkOrCreateIndex(Class<?> clazz, String[] colums, boolean unique, List<Map<String, Object>> indexs) {
		List<String> columList = new ArrayList<String>();
		for (String colum : colums) {
			columList.add(StrUtil.toUnderlineCase(colum));
		}
		String name = StrUtil.join("&", columList) + "@" + StrUtil.toUnderlineCase(clazz.getSimpleName());

		boolean hasIndex = false;
		for (Map<String, Object> map : indexs) {
			if (StrUtil.toUnderlineCase(name).equalsIgnoreCase((String) map.get("name")) || StrUtil.toUnderlineCase(name).equalsIgnoreCase((String) map.get("Key_name"))) {
				hasIndex = true;
			}
		}

		if (!hasIndex) {
			String type = unique ? "UNIQUE INDEX" : "INDEX";
			String length = "";

			columList = new ArrayList<String>();
			for (String colum : colums) {
				columList.add(StrUtil.toUnderlineCase(SQLConstants.SUFFIX + colum + SQLConstants.SUFFIX + length));
			}

			String sql = "CREATE " + type + "  " + SQLConstants.SUFFIX + StrUtil.toUnderlineCase(name) + SQLConstants.SUFFIX + " ON " + SQLConstants.SUFFIX + StrUtil.toUnderlineCase(clazz.getSimpleName()) + SQLConstants.SUFFIX + "(" + StrUtil.join(",", columList) + ")";
			logQuery(formatSql(sql));
			jdbcTemplate.executeQuietly(formatSql(sql));
		}

	}

	public void checkOrCreateColumn(Class<?> clazz, String name, Set<String> columns) {
		if (!columns.contains(StrUtil.toUnderlineCase(name).toLowerCase())) {
			String sql = "ALTER TABLE " + SQLConstants.SUFFIX + StrUtil.toUnderlineCase(clazz.getSimpleName()) + SQLConstants.SUFFIX + " ADD COLUMN " + SQLConstants.SUFFIX + StrUtil.toUnderlineCase(name) + SQLConstants.SUFFIX + " TEXT";
			logQuery(formatSql(sql));
			logger.debug("checkOrCreateColumn sql:{} ",formatSql(sql));
			//System.out.println("checkOrCreateColumn sql: "+formatSql(sql));
			jdbcTemplate.executeQuietly(formatSql(sql));
		}

	}

	/**
	 * Boolean 欄位表現形正規化:'true'→'1'、'false'→'0'(冪等)。
	 * 歷史資料混雜兩種形式(@InitValue backfill 寫 'true'/'false'、sqlite-jdbc 綁定寫 1/0、
	 * PG 原生 Boolean 綁定寫 'true'),查詢端統一綁 '1'/'0' 後必須把存量資料收斂到同一形式。
	 */
	public void normalizeBooleanColumn(Class<?> clazz, String column) {
		String tableName = SQLConstants.SUFFIX + StrUtil.toUnderlineCase(clazz.getSimpleName()) + SQLConstants.SUFFIX;
		String columnName = SQLConstants.SUFFIX + StrUtil.toUnderlineCase(column) + SQLConstants.SUFFIX;
		jdbcTemplate.execute("UPDATE " + tableName + " SET " + columnName + " = '1' WHERE " + columnName + " = 'true'");
		jdbcTemplate.execute("UPDATE " + tableName + " SET " + columnName + " = '0' WHERE " + columnName + " = 'false'");
	}

	public void updateDefaultValue(Class<?> clazz, String column, String value) {
		String sql = "SELECT COUNT(*) FROM " + SQLConstants.SUFFIX + StrUtil.toUnderlineCase(clazz.getSimpleName()) + SQLConstants.SUFFIX + " WHERE " + SQLConstants.SUFFIX + StrUtil.toUnderlineCase(column) + SQLConstants.SUFFIX + " IS NULL";
		logQuery(formatSql(sql));
		logger.debug("updateDefaultValue sql:{} ",formatSql(sql));
		//System.out.println("updateDefaultValue sql: "+formatSql(sql));
		Long count = jdbcTemplate.queryForCount(formatSql(sql));
		if (count != null && count > 0) {
			sql = "UPDATE " + SQLConstants.SUFFIX + StrUtil.toUnderlineCase(clazz.getSimpleName()) + SQLConstants.SUFFIX + " SET " + SQLConstants.SUFFIX + StrUtil.toUnderlineCase(column) + SQLConstants.SUFFIX + " = ? WHERE " + SQLConstants.SUFFIX + StrUtil.toUnderlineCase(column) + SQLConstants.SUFFIX + " IS NULL";
			logQuery(formatSql(sql));
			jdbcTemplate.execute(formatSql(sql), value);
		}

	}

}
