package org.elasticsearch.river.fs;


import java.util.ArrayList;
import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

public class FsMatchFilesTest {
	
	@Test
	public void exclude_only() throws Exception {
		Assert.assertFalse(FsRiverUtil.isIndexable("test.doc", new ArrayList<String>(), Arrays.asList("*.doc")));
		Assert.assertTrue(FsRiverUtil.isIndexable("test.xls", new ArrayList<String>(), Arrays.asList("*.doc")));
		Assert.assertTrue(FsRiverUtil.isIndexable("my.doc.xls", new ArrayList<String>(), Arrays.asList("*.doc")));
		Assert.assertFalse(FsRiverUtil.isIndexable("my.doc.xls", new ArrayList<String>(), Arrays.asList("*.doc","*.xls")));
		Assert.assertFalse(FsRiverUtil.isIndexable("my.doc.xls", new ArrayList<String>(), Arrays.asList("my.d?c*.xls")));
		Assert.assertTrue(FsRiverUtil.isIndexable("my.douc.xls", new ArrayList<String>(), Arrays.asList("my.d?c*.xls")));
	}

	@Test
	public void include_only() throws Exception {
		Assert.assertTrue(FsRiverUtil.isIndexable("test.doc", Arrays.asList("*.doc"), new ArrayList<String>()));
		Assert.assertFalse(FsRiverUtil.isIndexable("test.xls", Arrays.asList("*.doc"), new ArrayList<String>()));
		Assert.assertFalse(FsRiverUtil.isIndexable("my.doc.xls", Arrays.asList("*.doc"), new ArrayList<String>()));
		Assert.assertTrue(FsRiverUtil.isIndexable("my.doc.xls", Arrays.asList("my.d?c*.xls"), new ArrayList<String>()));
		Assert.assertFalse(FsRiverUtil.isIndexable("my.douc.xls", Arrays.asList("my.d?c*.xls"), new ArrayList<String>()));
	}

	@Test
	public void include_exclude() throws Exception {
		Assert.assertFalse(FsRiverUtil.isIndexable("test.doc", Arrays.asList("*.xls"), Arrays.asList("*.doc")));
		Assert.assertTrue(FsRiverUtil.isIndexable("test.xls", Arrays.asList("*.xls"), Arrays.asList("*.doc")));
		Assert.assertTrue(FsRiverUtil.isIndexable("my.doc.xls", Arrays.asList("*.xls"), Arrays.asList("*.doc")));
		Assert.assertFalse(FsRiverUtil.isIndexable("my.doc.xls", Arrays.asList("*.xls"), Arrays.asList("my.d?c*.xls")));
		Assert.assertTrue(FsRiverUtil.isIndexable("my.douc.xls", Arrays.asList("*.xls"), Arrays.asList("my.d?c*.xls")));
	}

}
