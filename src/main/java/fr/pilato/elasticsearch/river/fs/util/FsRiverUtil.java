/*
 * Licensed to David Pilato (the "Author") under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Author licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package fr.pilato.elasticsearch.river.fs.util;

import fr.pilato.elasticsearch.river.fs.river.ScanStatistic;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.support.XContentMapValues;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

public class FsRiverUtil {
	public static final String INDEX_TYPE_DOC = "doc";
	public static final String INDEX_TYPE_FOLDER = "folder";
	public static final String INDEX_TYPE_FS = "fsRiver";

	public static final String DOC_FIELD_NAME = "name";
	public static final String DOC_FIELD_DATE = "postDate";
	public static final String DOC_FIELD_PATH_ENCODED = "pathEncoded";
	public static final String DOC_FIELD_VIRTUAL_PATH = "virtualpath";
	public static final String DOC_FIELD_ROOT_PATH = "rootpath";
    public static final String DOC_FIELD_FILESIZE = "filesize";
    public static final String DOC_FIELD_INDEXED_CHARS = "_indexed_chars";

	public static final String DIR_FIELD_NAME = "name";
	public static final String DIR_FIELD_PATH_ENCODED = "pathEncoded";
	public static final String DIR_FIELD_VIRTUAL_PATH = "virtualpath";
	public static final String DIR_FIELD_ROOT_PATH = "rootpath";
	
	public static XContentBuilder buildFsFileMapping(String type) throws Exception {
		XContentBuilder xbMapping = jsonBuilder().prettyPrint().startObject()
			.startObject(type).startObject("properties")
			.startObject(DOC_FIELD_NAME).field("type", "string").field("analyzer","keyword").endObject()
			.startObject(DOC_FIELD_PATH_ENCODED).field("type", "string").field("analyzer","keyword").endObject()
			.startObject(DOC_FIELD_ROOT_PATH).field("type", "string").field("analyzer","keyword").endObject()
			.startObject(DOC_FIELD_VIRTUAL_PATH).field("type", "string").field("analyzer","keyword").endObject()
            .startObject(DOC_FIELD_FILESIZE).field("type", "long").endObject()
			.startObject(DOC_FIELD_DATE).field("type", "date").endObject()
			.startObject("file").field("type", "attachment")
			.startObject("fields")
                .startObject("content_type").field("store", "yes").endObject()
                .startObject("title").field("store", "yes").endObject()
                .startObject("file").field("term_vector", "with_positions_offsets").field("store", "yes").endObject()
                .startObject(DOC_FIELD_INDEXED_CHARS).field("type", "long").field("index","no").field("include_in_all",false).endObject()
            .endObject().endObject()
			.endObject().endObject().endObject();
		return xbMapping;
	}

	public static XContentBuilder buildFsFolderMapping(String type) throws Exception {
		XContentBuilder xbMapping = jsonBuilder().prettyPrint().startObject()
				.startObject(type).startObject("properties")
				.startObject(DIR_FIELD_NAME).field("type", "string").field("analyzer","keyword").endObject()
				.startObject(DIR_FIELD_PATH_ENCODED).field("type", "string").field("analyzer","keyword").endObject()
				.startObject(DIR_FIELD_ROOT_PATH).field("type", "string").field("analyzer","keyword").endObject()
				.startObject(DIR_FIELD_VIRTUAL_PATH).field("type", "string").field("analyzer","keyword").endObject()
				.endObject().endObject().endObject();

		return xbMapping;
	}

	public static XContentBuilder buildFsRiverMapping(String type) throws Exception {
		XContentBuilder	xbMapping = jsonBuilder().prettyPrint().startObject()
				.startObject(type).startObject("properties")
				.startObject("scanDate").field("type", "long").endObject()
				.startObject("folders").startObject("properties")
					.startObject("url").field("type", "string").endObject()
				.endObject().endObject()
				.endObject().endObject().endObject();

		return xbMapping;
	}

	public static XContentBuilder buildFsFileMapping() throws Exception {
		return buildFsFileMapping(INDEX_TYPE_DOC);
	}

	public static XContentBuilder buildFsFolderMapping() throws Exception {
		return buildFsFolderMapping(INDEX_TYPE_FOLDER);
	}

	public static XContentBuilder buildFsRiverMapping() throws Exception {
		return buildFsRiverMapping(INDEX_TYPE_FS);
	}

	/**
	 * Extract array from settings (array or ; delimited String)
	 * @param settings Settings
	 * @param path Path to definition : "fs.includes"
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static String[] buildArrayFromSettings(Map<String, Object> settings, String path) {
		String[] includes;

		// We manage comma separated format and arrays
		if(XContentMapValues.isArray(XContentMapValues.extractValue(path, settings))) {
			List<String> includesarray = (List<String>) XContentMapValues.extractValue(path, settings);
			int i = 0;
			includes = new String[includesarray.size()];
			for (String include : includesarray) {
				includes[i++] = Strings.trimAllWhitespace(include);
			}
		} else {
			String includedef = (String) XContentMapValues.extractValue(path, settings);
			includes = Strings.commaDelimitedListToStringArray(Strings.trimAllWhitespace(includedef));
		}
		
		String[] uniquelist = Strings.removeDuplicateStrings(includes);
		
		return uniquelist;
	}

	/**
	 * We check if we can index the file or if we should ignore it
	 * @param filename The filename to scan
	 * @param includes include rules, may be empty not null
	 * @param excludes exclude rules, may be empty not null
	 * @return
	 */
	public static boolean isIndexable(String filename, List<String> includes, List<String> excludes) {
		// No rules ? Fine, we index everything
		if (includes.isEmpty() && excludes.isEmpty()) return true;

		// Exclude rules : we know that whatever includes rules are, we should exclude matching files
		for (String exclude : excludes) {
			String regex = exclude.replace("?", ".?").replace("*", ".*?");
			if(filename.matches(regex)) return false;
		}
		
		// Include rules : we should add document if it match include rules
		if (includes.isEmpty()) return true;
		
		for (String include : includes) {
			String regex = include.replace("?", ".?").replace("*", ".*?");
			if(filename.matches(regex)) return true;
		}
		
		return false;
	}

    public static String computeVirtualPathName(ScanStatistic stats,
                                          String realPath) {
        if (realPath == null)
            return null;

        if (realPath.length() < stats.getRootPath().length())
            return "/";

        // Offset is 1 on Windows platforms
        //int offset = 0;
        //if (!"/".equals(File.separator)) offset = 1;
        
        return realPath.substring(stats.getRootPath().length())
                .replace(File.separator, "/");
    }
}
