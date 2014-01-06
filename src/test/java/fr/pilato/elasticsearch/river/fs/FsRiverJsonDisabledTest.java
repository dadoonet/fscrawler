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

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

/**
 * Test case for issue #5: https://github.com/dadoonet/fsriver/issues/5 : Support JSON documents
 */
public class FsRiverJsonDisabledTest extends AbstractFsRiverSimpleTest {

	private static final int UPDATE_RATE = 10 * 1000;
	private static final String DIRECTORY = "testfsjson1";
	private static final int EXPECTED_NUMBER_OF_DOCUMENTS_AFTER_RIVER_CREATION = 2;

	/**
	 * <ul>
	 * <li>We index JSON files so we don't use the mapper attachment plugin
	 * </ul>
	 */
	@Override
	public XContentBuilder fsRiverSettings() throws Exception {
		File dataDir = verifyDirectoryExists(DIRECTORY);
		String url = dataDir.getAbsoluteFile().getAbsolutePath();

		XContentBuilder xb = jsonBuilder()
				.startObject()
				.field("type", "fs")
				.startObject("fs")
				.field("url", url)
				.field("update_rate", UPDATE_RATE)
				.field("json_support", false)
				.endObject()
				.startObject("index")
				.field("index", indexName())
				.field("type", "doc")
				.field("bulk_size", 1)
				.endObject()
				.endObject();
		return xb;
	}

	@Override
	protected int expectedNumberOfDocumentsInTheRiver() {
		return EXPECTED_NUMBER_OF_DOCUMENTS_AFTER_RIVER_CREATION;
	}

	@Test
	public void tweets_should_be_indexed_as_plain_text() throws Exception {
		SearchResponse searchResponse = node.client().prepareSearch(indexName())
				.setQuery(QueryBuilders.termQuery("text", "tweet")).execute().actionGet();
		Assert.assertEquals("We should have 0 doc for tweet in text field...", 0, searchResponse.getHits().getTotalHits());

		searchResponse = node.client().prepareSearch(indexName())
				.setQuery(QueryBuilders.queryString("tweet")).execute().actionGet();
		Assert.assertEquals("We should have 2 docs for tweet in _all...", 2, searchResponse.getHits().getTotalHits());
	}
}
