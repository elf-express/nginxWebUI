package com.cym.sqlhelper.utils;

import java.lang.reflect.Field;
import java.sql.SQLException;
import java.util.Set;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cym.sqlhelper.config.InitValue;
import com.cym.sqlhelper.config.Table;

import cn.hutool.core.util.ReflectUtil;

@Component
public class TableUtils {
	static Logger logger = LoggerFactory.getLogger(TableUtils.class);

	@Inject
	JdbcTemplate jdbcTemplate;
	
	@Inject
	SqlUtils sqlUtils;

	public void initTable(Class<?> clazz) throws SQLException {
		Table table = clazz.getAnnotation(Table.class);
		if (table != null) {
			// 创建表
			sqlUtils.checkOrCreateTable(clazz);

			// 获取表所有字段
			Set<String> columns = jdbcTemplate.queryForColumn(clazz);

			// 建立字段
			Field[] fields = ReflectUtil.getFields(clazz);
			for (Field field : fields) {
				// 创建字段
				if (!field.getName().equals("id")) {
					sqlUtils.checkOrCreateColumn(clazz, field.getName(), columns);
				}

				boolean isBooleanField = field.getType() == Boolean.class || field.getType() == boolean.class;

				// Boolean 欄位正規化 migration:歷史寫入混雜 'true'/'false'(@InitValue backfill、
				// PG 原生綁定)與 '1'/'0'(sqlite-jdbc),統一為 '1'/'0'(冪等,每次啟動掃一次)
				if (isBooleanField) {
					sqlUtils.normalizeBooleanColumn(clazz, field.getName());
				}

				// 更新表默认值(Boolean 的 @InitValue "true"/"false" 先轉 '1'/'0' 再回填,避免再混入)
				if (field.isAnnotationPresent(InitValue.class)) {
					InitValue defaultValue = field.getAnnotation(InitValue.class);
					if (defaultValue.value() != null) {
						String value = defaultValue.value();
						if (isBooleanField) {
							value = Boolean.parseBoolean(value) ? "1" : "0";
						}
						sqlUtils.updateDefaultValue(clazz, field.getName(), value);
					}
				}
			}

		}
	}

}
