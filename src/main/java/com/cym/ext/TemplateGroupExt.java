package com.cym.ext;

import java.util.List;

public class TemplateGroupExt {
	String groupName;
	String displayName;
	String description;
	List<TemplateExt> templateExtList;

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

	public List<TemplateExt> getTemplateExtList() {
		return templateExtList;
	}

	public void setTemplateExtList(List<TemplateExt> templateExtList) {
		this.templateExtList = templateExtList;
	}
}
