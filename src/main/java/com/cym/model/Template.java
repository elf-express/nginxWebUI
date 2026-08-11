package com.cym.model;

import com.cym.sqlhelper.bean.BaseModel;
import com.cym.sqlhelper.config.Table;

@Table
public class Template extends BaseModel{
	String name;

	// 自动套用目标: "" 仅手动; server/server1/server2/location/upstream 由 ParamService 注入实体;
	// stream = 注入全域 stream{} (ConfService.buildConf)
	String def;

	String groupName;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDef() {
		return def;
	}

	public void setDef(String def) {
		this.def = def;
	}

	public String getGroupName() {
		return groupName;
	}

	public void setGroupName(String groupName) {
		this.groupName = groupName;
	}
}
