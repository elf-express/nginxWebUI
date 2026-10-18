package com.cym.model;

import com.cym.sqlhelper.bean.BaseModel;
import com.cym.sqlhelper.config.InitValue;
import com.cym.sqlhelper.config.Table;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 
 * Stream参数
 *
 */
@Table
public class Stream extends BaseModel {
	/**
	 * 参数名
	 */
	String name;
	/**
	 * 参数值
	 */
	String value;
	/** Configuration storage mode; null rows from older databases are legacy. */
	String configMode;
	/** Verbatim contents inside this stream child block's braces. */
	String rawContent;
	@JsonIgnore
	Long seq;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	public Long getSeq() {
		return seq;
	}

	public void setSeq(Long seq) {
		this.seq = seq;
	}

	public String getConfigMode() {
		return configMode == null ? "legacy" : configMode;
	}

	public void setConfigMode(String configMode) {
		this.configMode = configMode;
	}

	public boolean isBlockMode() {
		return "block".equals(configMode);
	}

	public String getRawContent() {
		return rawContent;
	}

	public void setRawContent(String rawContent) {
		this.rawContent = rawContent;
	}

}
