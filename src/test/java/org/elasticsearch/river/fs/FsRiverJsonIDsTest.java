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

package org.elasticsearch.river.fs;

import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

/**
 * Test case for issue #7: https://github.com/dadoonet/fsriver/issues/7 : JSON support: use filename as ID
 */
public class FsRiverJsonIDsTest extends AbstractFsRiverSimpleTest {

	@Override
	public long waitingTime() throws Exception {
		return 5;
	}
	/**
	 * We use the default mapping
	 */
	@Override
	public XContentBuilder mapping() throws Exception {
		return null;
	}

	/**
	 * 
	 * <ul>
	 *   <li>We index JSon files so we don't use the mapper attachment plugin
	 * </ul>
	 */
	@Override
	public XContentBuilder fsRiver() throws Exception {
		// We update 2 seconds
		int updateRate = 5 * 1000;
		String dir = "testfsjson1";
		
		// First we check that filesystem to be analyzed exists...
		File dataDir = new File("./target/test-classes/" + dir);
		if(!dataDir.exists()) {
			throw new RuntimeException("src/test/resources/" + dir + " doesn't seem to exist. Check your JUnit tests."); 
		}
		String url = dataDir.getAbsoluteFile().getAbsolutePath();
		
		XContentBuilder xb = jsonBuilder()
				.startObject()
					.field("type", "fs")
					.startObject("fs")
						.field("name", dir)
						.field("url", url)
						.field("update_rate", updateRate)
                        .field("json_support", true)
                        .field("filename_as_id", true)
					.endObject()
				.endObject();
		return xb;
	}
	

	@Test
	public void tweet_term_is_indexed_twice() throws Exception {
        // We check that document name is used as an id
        GetResponse getResponse = node.client().prepareGet(indexName(), FsRiverUtil.INDEX_TYPE_DOC, "tweet1").execute().actionGet();
		Assert.assertTrue("Document should exists with [tweet1] id...", getResponse.isExists());
        // We check that document name is used as an id
        getResponse = node.client().prepareGet(indexName(), FsRiverUtil.INDEX_TYPE_DOC, "tweet2").execute().actionGet();
        Assert.assertTrue("Document should exists with [tweet2] id...", getResponse.isExists());
	}
}
