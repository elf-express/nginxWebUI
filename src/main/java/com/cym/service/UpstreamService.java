package com.cym.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;

import com.cym.model.Param;
import com.cym.model.Upstream;
import com.cym.model.UpstreamServer;
import com.cym.sqlhelper.bean.Page;
import com.cym.sqlhelper.bean.Sort;
import com.cym.sqlhelper.bean.Sort.Direction;
import com.cym.sqlhelper.bean.Update;
import com.cym.sqlhelper.utils.ConditionAndWrapper;
import com.cym.sqlhelper.utils.ConditionOrWrapper;
import com.cym.sqlhelper.utils.SqlHelper;
import com.cym.utils.NginxBlockCodec;
import com.cym.utils.SnowFlakeUtils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;

@Component
public class UpstreamService {
	@Inject
	SqlHelper sqlHelper;

	public Page search(Page page, String word) {
		ConditionAndWrapper conditionAndWrapper = new ConditionAndWrapper();

		if (StrUtil.isNotEmpty(word)) {
			conditionAndWrapper.and(new ConditionOrWrapper().like("name", word));
		}

		page = sqlHelper.findPage(conditionAndWrapper, new Sort("seq", Direction.DESC), page, Upstream.class);

		return page;
	}

	
	public void deleteById(String id) {
		String[] ids = id.split(",");
		
		sqlHelper.deleteByIds(ids, Upstream.class);
		sqlHelper.deleteByQuery(new ConditionAndWrapper().in("upstreamId", ids), UpstreamServer.class);
	}

	
	public void addOver(Upstream upstream, List<UpstreamServer> upstreamServers, String upstreamParamJson) {
		if (upstream.getName() != null) upstream.setName(upstream.getName().trim());
		upstream.setConfigMode("legacy");
		upstream.setRawContent(null);
		if (upstream.getProxyType() == 1 || upstream.getTactics() == null) {
			upstream.setTactics("");
		}

		sqlHelper.insertOrUpdate(upstream);

		List<Param> paramList = new ArrayList<Param>();
		if (StrUtil.isNotEmpty(upstreamParamJson) && JSONUtil.isTypeJSON(upstreamParamJson)) {
			paramList = JSONUtil.toList(JSONUtil.parseArray(upstreamParamJson), Param.class);
		}
		sqlHelper.deleteByQuery(new ConditionAndWrapper().eq("upstreamId", upstream.getId()), Param.class);
		// 反向插入,保证列表与输入框对应
		Collections.reverse(paramList);
		for (Param param : paramList) {
			param.setUpstreamId(upstream.getId());
			sqlHelper.insert(param);
		}

		sqlHelper.deleteByQuery(new ConditionAndWrapper().eq("upstreamId", upstream.getId()), UpstreamServer.class);
		if (upstreamServers != null) {
			// 反向插入,保证列表与输入框对应
			Collections.reverse(upstreamServers);

			for (UpstreamServer upstreamServer : upstreamServers) {
				upstreamServer.setUpstreamId(upstream.getId());
				sqlHelper.insert(upstreamServer);
			}
		}

	}

	/** Save one verbatim upstream block after structural validation. */
	public Upstream saveBlock(String id, String name, Integer proxyType, String rawContent, boolean convertLegacy) {
		Upstream upstream = StrUtil.isNotEmpty(id) ? sqlHelper.findById(id, Upstream.class) : null;
		if (StrUtil.isNotEmpty(id) && upstream == null) {
			throw new IllegalArgumentException("upstream_not_found");
		}
		if (upstream != null && !upstream.isBlockMode() && !convertLegacy) {
			throw new IllegalStateException("legacy_conversion_required");
		}
		name = name == null ? null : name.trim();
		if (StrUtil.isEmpty(name)) {
			throw new IllegalArgumentException("invalid_upstream_name");
		}
		long duplicateCount = StrUtil.isEmpty(id) ? getCountByName(name) : getCountByNameWithOutId(name, id);
		if (duplicateCount > 0) {
			throw new IllegalArgumentException("duplicate_upstream_name");
		}
		int effectiveProxyType = proxyType == null ? 0 : proxyType;
		if (effectiveProxyType != 0 && effectiveProxyType != 1) {
			throw new IllegalArgumentException("invalid_proxy_type");
		}
		String context = effectiveProxyType == 0 ? "http-upstream" : "stream-upstream";
		NginxBlockCodec.validate("upstream " + name, rawContent, context);

		if (upstream == null) {
			upstream = new Upstream();
			upstream.setSeq(SnowFlakeUtils.getId());
		}
		upstream.setName(name);
		upstream.setProxyType(effectiveProxyType);
		upstream.setConfigMode("block");
		upstream.setRawContent(rawContent == null ? "" : rawContent);
		upstream.setMonitor(0);
		sqlHelper.insertOrUpdate(upstream);
		return upstream;
	}

	public Upstream findById(String id) {
		return sqlHelper.findById(id, Upstream.class);
	}

	public List<UpstreamServer> getUpstreamServers(String id) {
		return sqlHelper.findListByQuery(new ConditionAndWrapper().eq("upstreamId", id), UpstreamServer.class);
	}

	
//	public void del(String id) {
//		sqlHelper.deleteById(id, Upstream.class);
//		sqlHelper.deleteByQuery(new ConditionAndWrapper().eq("upstreamId", id), UpstreamServer.class);
//
//	}

	public List<Upstream> getListByProxyType(Integer proxyType) {
		Sort sort = new Sort().add("seq", Direction.DESC);
		return sqlHelper.findListByQuery(new ConditionAndWrapper().eq("proxyType", proxyType), sort, Upstream.class);
	}

	public Long getCountByName(String name) {
		return sqlHelper.findCountByQuery(new ConditionAndWrapper().eq("name", name), Upstream.class);
	}

	public Long getCountByNameWithOutId(String name, String id) {
		return sqlHelper.findCountByQuery(new ConditionAndWrapper().eq("name", name).ne("id", id), Upstream.class);
	}

	public List<UpstreamServer> getServerListByMonitor(int monitor) {
		List<String> upstreamIds = new ArrayList<>();
		for (Upstream upstream : sqlHelper.findListByQuery(new ConditionAndWrapper().eq("monitor", monitor), Upstream.class)) {
			if (!upstream.isBlockMode()) upstreamIds.add(upstream.getId());
		}
		return upstreamIds.isEmpty() ? new ArrayList<>()
				: sqlHelper.findListByQuery(new ConditionAndWrapper().in("upstreamId", upstreamIds), UpstreamServer.class);
	}

	public List<UpstreamServer> getAllServer() {
		List<String> blockIds = new ArrayList<>();
		for (Upstream upstream : sqlHelper.findAll(Upstream.class)) {
			if (upstream.isBlockMode()) blockIds.add(upstream.getId());
		}
		List<UpstreamServer> servers = sqlHelper.findAll(UpstreamServer.class);
		if (blockIds.isEmpty()) return servers;
		servers.removeIf(server -> blockIds.contains(server.getUpstreamId()));
		return servers;
	}

	public void resetMonitorStatus() {

		sqlHelper.updateMulti(new ConditionAndWrapper(), new Update().set("monitorStatus", -1), UpstreamServer.class);
	}


	public void setSeq(String upstreamId, Integer seqAdd) {
		Upstream upstream = sqlHelper.findById(upstreamId, Upstream.class);

		List<Upstream> upstreamList = sqlHelper.findAll(new Sort("seq", Direction.DESC), Upstream.class);
		if (upstreamList.size() > 0) {
			Upstream tagert = null;
			if (seqAdd < 0) {
				// 下移
				for (int i = 0; i < upstreamList.size(); i++) {
					if (upstreamList.get(i).getSeq() < upstream.getSeq()) {
						tagert = upstreamList.get(i);
						break;
					}
				}
			} else {
				// 上移
				for (int i = upstreamList.size() - 1; i >= 0; i--) {
					if (upstreamList.get(i).getSeq() > upstream.getSeq()) {
						tagert = upstreamList.get(i);
						break;
					}
				}
			}

			if (tagert != null) {
				// 交换seq
				Long seq = tagert.getSeq();
				tagert.setSeq(upstream.getSeq());
				upstream.setSeq(seq);

				sqlHelper.updateById(tagert);
				sqlHelper.updateById(upstream);
			}

		}

	}

}
