package org.elasticsearch.river.fs;

import java.util.ArrayList;
import java.util.List;

/**
 * Define a FS Feed with source (aka short name), url and updateRate attributes
 * @author dadoonet (David Pilato)
 */
public class FsRiverFeedDefinition {
	private String feedname;
	private String url;
	private int updateRate;
	private List<String> includes;
	private List<String> excludes;
	
	
	public FsRiverFeedDefinition() {
		this(null, null, 0, new ArrayList<String>(), new ArrayList<String>());
	}
	
	public FsRiverFeedDefinition(String feedname, String url, int updateRate) {
		this(feedname, url, updateRate, new ArrayList<String>(), new ArrayList<String>());
	}
	
	public FsRiverFeedDefinition(String feedname, String url, int updateRate, List<String> includes, List<String> excludes) {
		assert( excludes != null);
		assert( includes != null);
		this.includes = includes;
		this.excludes = excludes;
		this.feedname = feedname;
		this.url = url;
		this.updateRate = updateRate;
	}
	
	public String getFeedname() {
		return feedname;
	}
	
	public void setFeedname(String feedname) {
		this.feedname = feedname;
	}
	
	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public int getUpdateRate() {
		return updateRate;
	}

	public void setUpdateRate(int updateRate) {
		this.updateRate = updateRate;
	}
	
	public List<String> getExcludes() {
		return excludes;
	}
	
	public void setExcludes(List<String> excludes) {
		this.excludes = excludes;
	}
	
	public List<String> getIncludes() {
		return includes;
	}
	
	public void setIncludes(List<String> includes) {
		this.includes = includes;
	}
	
	public void addInclude(String include) {
		this.includes.add(include);
	}

	public void addExclude(String exclude) {
		this.excludes.add(exclude);
	}
}
