package com.cym.model;

import com.cym.sqlhelper.bean.BaseModel;
import com.cym.sqlhelper.config.Table;

@Table
public class Template extends BaseModel{
	String name;

	/**
	 * 自動套用層級（多選、小寫、逗號分隔）。
	 * 合法：http,server,server1,server2,stream,location,upstream；空 = 僅手動選用。
	 * server/server1/server2/location/upstream → ParamService 注入實體；
	 * http / stream → ConfService 注入對應頂層區塊。
	 */
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
