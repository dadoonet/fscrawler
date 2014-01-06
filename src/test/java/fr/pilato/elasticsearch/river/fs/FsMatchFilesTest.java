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

package fr.pilato.elasticsearch.river.fs;


import fr.pilato.elasticsearch.river.fs.util.FsRiverUtil;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;

public class FsMatchFilesTest {

	@Test
	public void exclude_only() throws Exception {
		Assert.assertFalse(FsRiverUtil.isIndexable("test.doc", new ArrayList<String>(), Arrays.asList("*.doc")));
		Assert.assertTrue(FsRiverUtil.isIndexable("test.xls", new ArrayList<String>(), Arrays.asList("*.doc")));
		Assert.assertTrue(FsRiverUtil.isIndexable("my.doc.xls", new ArrayList<String>(), Arrays.asList("*.doc")));
		Assert.assertFalse(FsRiverUtil.isIndexable("my.doc.xls", new ArrayList<String>(), Arrays.asList("*.doc", "*.xls")));
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
