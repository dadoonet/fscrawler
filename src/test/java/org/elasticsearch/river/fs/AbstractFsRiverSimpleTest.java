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

import org.elasticsearch.action.count.CountResponse;
import org.elasticsearch.index.query.QueryBuilders;
import org.junit.Assert;


public abstract class AbstractFsRiverSimpleTest extends AbstractFsRiverTest {

	/**
	 * We wait for 5 seconds before each test
	 */
	@Override
	public long waitingTime() throws Exception {
		return 5;
	}

	protected void searchTestHelper(String feedname) {
		// Let's search for entries for darkreading
//		SearchResponse searchResponse = node.client().prepareSearch(indexName())
//				.setQuery(QueryBuilders.fieldQuery("feedname", feedname)).execute().actionGet();
//		Assert.assertTrue("We should have at least one doc for " + feedname + "...", searchResponse.hits().getTotalHits() > 1);
	}
	
	public void countTestHelper() throws Exception {
		// Let's search for entries
		CountResponse response = node.client().prepareCount(indexName())
				.setQuery(QueryBuilders.matchAllQuery()).execute().actionGet();
		Assert.assertTrue("We should have at least one doc...", response.count() > 1);
	}

}
