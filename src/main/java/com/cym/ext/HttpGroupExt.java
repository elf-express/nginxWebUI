package com.cym.ext;

import java.util.List;

import com.cym.model.Http;

public class HttpGroupExt {
	String groupName;
	String displayName;
	String description;
	String moduleNote;
	List<Http> httpList;

	public String getGroupName() {
		return groupName;
	}

	public void setGroupName(String groupName) {
		this.groupName = groupName;
	}

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getModuleNote() {
		return moduleNote;
	}

	public void setModuleNote(String moduleNote) {
		this.moduleNote = moduleNote;
	}

	public List<Http> getHttpList() {
		return httpList;
	}

	public void setHttpList(List<Http> httpList) {
		this.httpList = httpList;
	}
}
