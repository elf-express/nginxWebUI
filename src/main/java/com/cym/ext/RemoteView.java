package com.cym.ext;

import com.cym.model.Remote;

/** 回傳給前端的遠端節點資料，不含連線密碼與 creditKey。 */
public class RemoteView {
	private final String id;
	private final String protocol;
	private final String ip;
	private final Integer port;
	private final Integer status;
	private final String name;
	private final String version;
	private final String system;
	private final String descr;
	private final Integer monitor;
	private final String parentId;
	private final Integer type;
	private final Integer nginx;
	private final Boolean select;

	private RemoteView(Remote remote) {
		this.id = remote.getId();
		this.protocol = remote.getProtocol();
		this.ip = remote.getIp();
		this.port = remote.getPort();
		this.status = remote.getStatus();
		this.name = remote.getName();
		this.version = remote.getVersion();
		this.system = remote.getSystem();
		this.descr = remote.getDescr();
		this.monitor = remote.getMonitor();
		this.parentId = remote.getParentId();
		this.type = remote.getType();
		this.nginx = remote.getNginx();
		this.select = remote.getSelect();
	}

	public static RemoteView of(Remote remote) {
		return remote == null ? null : new RemoteView(remote);
	}

	public String getId() {
		return id;
	}

	public String getProtocol() {
		return protocol;
	}

	public String getIp() {
		return ip;
	}

	public Integer getPort() {
		return port;
	}

	public Integer getStatus() {
		return status;
	}

	public String getName() {
		return name;
	}

	public String getVersion() {
		return version;
	}

	public String getSystem() {
		return system;
	}

	public String getDescr() {
		return descr;
	}

	public Integer getMonitor() {
		return monitor;
	}

	public String getParentId() {
		return parentId;
	}

	public Integer getType() {
		return type;
	}

	public Integer getNginx() {
		return nginx;
	}

	public Boolean getSelect() {
		return select;
	}
}
