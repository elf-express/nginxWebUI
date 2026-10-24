package com.cym.testsupport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.cym.sqlhelper.bean.Order;
import com.cym.sqlhelper.bean.Page;
import com.cym.sqlhelper.bean.Sort;
import com.cym.sqlhelper.utils.Condition;
import com.cym.sqlhelper.utils.ConditionWrapper;
import com.cym.sqlhelper.utils.SqlHelper;

import cn.hutool.core.util.ReflectUtil;

/**
 * 測試用的記憶體 SqlHelper：讓 ConfService 等服務不經資料庫就能跑，
 * 只支援 =、&lt;&gt;、&lt;、&lt;=、&gt;、&gt;=、LIKE（包含）、IN、IS NULL、IS NOT NULL 條件（同一層 AND 串接，巢狀的 ConditionWrapper 依它自己的 AND／OR）。
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
	public void updateById(Object object) {
		insertOrUpdate(object);
	}

	@Override
	public void updateAllColumnById(Object object) {
		insertOrUpdate(object);
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

	/** 只排序 findPage 的結果；其他查詢維持插入順序 */
	@Override
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public Page findPage(ConditionWrapper conditions, Sort sort, Page page, Class<?> type) {
		List<Object> all = new ArrayList<>(findListByQuery(conditions, null, (Class<Object>) type));
		if (sort != null) {
			all.sort(comparator(sort));
		}
		int from = Math.min((page.getCurr() - 1) * page.getLimit(), all.size());
		page.setCount((long) all.size());
		page.setRecords(new ArrayList<>(all.subList(from, Math.min(from + page.getLimit(), all.size()))));
		return page;
	}

	@SuppressWarnings("unchecked")
	private static java.util.Comparator<Object> comparator(Sort sort) {
		java.util.Comparator<Object> c = (a, b) -> 0;
		for (Order o : (List<Order>) ReflectUtil.getFieldValue(sort, "orderList")) {
			java.util.Comparator<Object> one = (a, b) -> compareValues(ReflectUtil.getFieldValue(a, o.getColumn()),
					ReflectUtil.getFieldValue(b, o.getColumn()));
			c = c.thenComparing(o.getDirection() == Sort.Direction.DESC ? one.reversed() : one);
		}
		return c;
	}

	/** 兩邊都是整數時依數值比較，否則依字串比較；null 最小 */
	private static int compareValues(Object a, Object b) {
		if (a == null || b == null) {
			return a == null ? (b == null ? 0 : -1) : 1;
		}
		try {
			return Long.compare(Long.parseLong(a.toString()), Long.parseLong(b.toString()));
		} catch (NumberFormatException e) {
			return a.toString().compareTo(b.toString());
		}
	}

	private static boolean compares(Object actual, Object expected, String op) {
		if (actual == null || expected == null) {
			return false;
		}
		int c = compareValues(actual, expected);
		return switch (op) {
		case "<" -> c < 0;
		case "<=" -> c <= 0;
		case ">" -> c > 0;
		default -> c >= 0;
		};
	}

	/** 巢狀的 ConditionWrapper 依它自己的 andLink 決定 AND／OR；空的 wrapper 視為成立 */
	private boolean matches(Object row, ConditionWrapper wrapper) {
		if (wrapper == null) {
			return true;
		}
		@SuppressWarnings("unchecked")
		List<Object> items = (List<Object>) ReflectUtil.getFieldValue(wrapper, "list");
		boolean andLink = Boolean.TRUE.equals(ReflectUtil.getFieldValue(wrapper, "andLink"));
		boolean any = false;
		boolean all = true;
		boolean counted = false;
		for (Object item : items) {
			boolean ok;
			if (item instanceof Condition condition) {
				ok = matchesCondition(row, condition);
			} else if (item instanceof ConditionWrapper nested) {
				ok = matches(row, nested);
			} else {
				continue;
			}
			counted = true;
			any |= ok;
			all &= ok;
		}
		return !counted || (andLink ? all : any);
	}

	private boolean matchesCondition(Object row, Condition condition) {
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
		if (("<".equals(op) || "<=".equals(op) || ">".equals(op) || ">=".equals(op)) && !compares(actual, expected, op)) {
			return false;
		}
		if ("LIKE".equals(op) && (actual == null || !String.valueOf(actual).contains(String.valueOf(expected)))) {
			return false;
		}
		return true;
	}
}
