package org.elasticsearch.river.fs;


import org.apache.log4j.Logger;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.junit.Test;

public class FsMappingTest {
	
	private Logger logger = Logger.getLogger(FsMappingTest.class);

	@Test
	public void fs_mapping_for_files() throws Exception {
		XContentBuilder xb = FsRiverUtil.buildFsFileMapping();
		logger.debug("Mapping used for files : " + xb.string());
	}
	
	@Test
	public void fs_mapping_for_folders() throws Exception {
		XContentBuilder xb = FsRiverUtil.buildFsFolderMapping();
		logger.debug("Mapping used for folders : " + xb.string());
	}

	@Test
	public void fs_mapping_for_meta() throws Exception {
		XContentBuilder xb = FsRiverUtil.buildFsRiverMapping();
		logger.debug("Mapping used for river metadata : " + xb.string());
	}

}
