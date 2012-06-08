package org.elasticsearch.river.fs;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

import org.elasticsearch.common.xcontent.XContentBuilder;

public class FsRiverUtil {
	public static final String INDEX_TYPE_DOC = "doc";
	public static final String INDEX_TYPE_FOLDER = "folder";
	public static final String INDEX_TYPE_FS = "fsRiver";

	public static final String DOC_FIELD_NAME = "name";
	public static final String DOC_FIELD_DATE = "postDate";
	public static final String DOC_FIELD_PATH_ENCODED = "pathEncoded";
	public static final String DOC_FIELD_VIRTUAL_PATH = "virtualpath";
	public static final String DOC_FIELD_ROOT_PATH = "rootpath";
	
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
			.startObject(DOC_FIELD_DATE).field("type", "date").endObject()
			.startObject("file").field("type", "attachment")
			.startObject("fields").startObject("title")
			.field("store", "yes").endObject().startObject("file")
			.field("term_vector", "with_positions_offsets")
			.field("store", "yes").endObject().endObject().endObject()
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



}
