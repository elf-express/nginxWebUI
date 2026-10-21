package com.cym.testsupport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.cym.sqlhelper.bean.Sort;
import com.cym.sqlhelper.utils.Condition;
import com.cym.sqlhelper.utils.ConditionWrapper;
import com.cym.sqlhelper.utils.SqlHelper;

import cn.hutool.core.util.ReflectUtil;

/**
 * 測試用的記憶體 SqlHelper：讓 ConfService 等服務不經資料庫就能跑，
 * 只支援 =、&lt;&gt;、IN、IS NULL、IS NOT NULL 條件（AND 串接）。
 */
public class InMemorySqlHelper extends SqlHelper {
	private final Map<Class<?>, LinkedHashMap<String, Object>> rows = new LinkedHashMap<>();
	private long nextId = 1;

	@Override
	public String insertOrUpdate(Object object) {
		String id = (String) ReflectUtil.getFieldValue(object, "id");
		if (id == null) {
			id = Long.toString(nextId++);
			ReflectUtil.setFieldValue(object, "id", id);
		}
		rows.computeIfAbsent(object.getClass(), ignored -> new LinkedHashMap<>()).put(id, object);
		return id;
	}

	@Override
	public String insert(Object object) {
		return insertOrUpdate(object);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T findById(String id, Class<T> type) {
		return (T) rows.getOrDefault(type, new LinkedHashMap<>()).get(id);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> List<T> findAll(Sort sort, Class<T> type) {
		return new ArrayList<>((List<T>) rows.getOrDefault(type, new LinkedHashMap<>()).values().stream().toList());
	}

	@Override
	public <T> List<T> findAll(Class<T> type) {
		return findAll((Sort) null, type);
	}

	@Override
	public Long findAllCount(Class<?> type) {
		return (long) rows.getOrDefault(type, new LinkedHashMap<>()).size();
	}

	@Override
	public <T> List<T> findListByQuery(ConditionWrapper conditions, Sort sort, Class<T> type) {
		return findAll(sort, type).stream().filter(row -> matches(row, conditions)).toList();
	}

	@Override
	public <T> T findOneByQuery(ConditionWrapper conditions, Class<T> type) {
		return findListByQuery(conditions, null, type).stream().findFirst().orElse(null);
	}

	@Override
	public Long findCountByQuery(ConditionWrapper conditions, Class<?> type) {
		return findListByQuery(conditions, null, type).size() * 1L;
	}

	@Override
	public void deleteById(String id, Class<?> type) {
		LinkedHashMap<String, Object> table = rows.get(type);
		if (table != null) {
			table.remove(id);
		}
	}

	@Override
	public void deleteByQuery(ConditionWrapper conditions, Class<?> type) {
		LinkedHashMap<String, Object> table = rows.get(type);
		if (table != null) {
			table.values().removeIf(row -> matches(row, conditions));
		}
	}

	private boolean matches(Object row, ConditionWrapper wrapper) {
		if (wrapper == null) {
			return true;
		}
		@SuppressWarnings("unchecked")
		List<Object> conditions = (List<Object>) ReflectUtil.getFieldValue(wrapper, "list");
		for (Object item : conditions) {
			if (!(item instanceof Condition condition)) {
				continue;
			}
			Object actual = ReflectUtil.getFieldValue(row, condition.getColumn());
			Object expected = condition.getValue();
			String op = condition.getOperation();
			if ("=".equals(op) && !Objects.equals(actual, expected)) {
				return false;
			}
			if ("<>".equals(op) && Objects.equals(actual, expected)) {
				return false;
			}
			if ("IN".equals(op) && expected instanceof java.util.Collection<?> values && !values.contains(actual)) {
				return false;
			}
			if ("IS NULL".equals(op) && actual != null) {
				return false;
			}
			if ("IS NOT NULL".equals(op) && actual == null) {
				return false;
			}
		}
		return true;
	}
}
