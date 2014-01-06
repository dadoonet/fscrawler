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

import org.elasticsearch.action.count.CountResponse;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

public class FsMultipleRiverTest extends AbstractFsRiverTest {

	private static final int UPDATE_RATE_SECOND_RIVER = 5 * 1000;
	private static final int UPDATE_RATE_FIRST_RIVER = 10 * 1000;
	private static final String DIRECTORY_SECOND_RIVER = "testfs2";
	private static final int EXPECTED_NUMBER_OF_DOC_SECOND_RIVER_CREATION = 2;
	private static final String DIRECTORY_FIRST_RIVER = "testfs1";
	private static final String TYPE_SECOND_RIVER = "otherdocs";

	@Before
	public void setUp() throws Exception {
		createSecondRiver();
	}

	@Override
	public XContentBuilder fsRiverSettings() throws Exception {
		File dataDir = verifyDirectoryExists(DIRECTORY_FIRST_RIVER);
		String url = dataDir.getAbsoluteFile().getAbsolutePath();

		XContentBuilder xb = jsonBuilder()
				.startObject()
				.field("type", "fs")
				.startObject("fs")
				.field("url", url)
				.field("update_rate", UPDATE_RATE_FIRST_RIVER)
				.endObject()
				.startObject("index")
				.field("index", indexName())
				.field("type", "doc")
				.field("bulk_size", 1)
				.endObject()
				.endObject();
		return xb;
	}

	@Test
	public void index_must_have_two_files() throws Exception {
		CountResponse response = node.client().prepareCount(indexName()).setTypes(TYPE_SECOND_RIVER, "doc")
				.setQuery(QueryBuilders.matchAllQuery()).execute().actionGet();

		Assert.assertEquals("We should have two docs...", EXPECTED_NUMBER_OF_DOC_SECOND_RIVER_CREATION,
				response.getCount());
	}

	private void createSecondRiver() throws Exception {
		File dataDir = verifyDirectoryExists(DIRECTORY_SECOND_RIVER);
		String url = dataDir.getAbsoluteFile().getAbsolutePath();

		XContentBuilder xb = jsonBuilder()
				.startObject()
				.field("type", "fs")
				.startObject("fs")
				.field("url", url)
				.field("update_rate", UPDATE_RATE_SECOND_RIVER)
				.endObject()
				.startObject("index")
				.field("index", indexName())
				.field("type", TYPE_SECOND_RIVER)
				.field("bulk_size", 1)
				.endObject()
				.endObject();

		addARiver(DIRECTORY_SECOND_RIVER, xb);
		waitForIndexToHaveAtLeast(EXPECTED_NUMBER_OF_DOC_SECOND_RIVER_CREATION);
	}
}
