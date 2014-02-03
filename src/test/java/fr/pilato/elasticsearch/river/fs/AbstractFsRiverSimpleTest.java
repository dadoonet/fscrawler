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
import org.elasticsearch.action.count.CountResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.junit.Assert;


public abstract class AbstractFsRiverSimpleTest extends AbstractFsRiverTest {
    protected ESLogger logger = Loggers.getLogger(this.getClass());

    public long waitingTime() throws Exception {
        return 5;
    }

    /**
     * Check that we have the expected number of docs or at least one if expected is null
     *
     * @param term     Term you search for. MatchAll if null.
     * @param expected expected number of docs. Null if at least 1.
     * @throws Exception
     */
    public void countTestHelper(String term, Integer expected) throws Exception {
        // Let's search for entries
        QueryBuilder query;
        if (term == null) {
            query = QueryBuilders.matchAllQuery();
        } else {
            query = QueryBuilders.queryString(term);
        }

        long totalHits = 0;

        if (logger.isDebugEnabled()) {
            // We want traces, so let's run a search query and trace results
            // Let's search for entries
            SearchResponse response = node.client().prepareSearch(indexName())
                    .setTypes(FsRiverUtil.INDEX_TYPE_DOC)
                    .setQuery(query).execute().actionGet();

            logger.debug("result {}", response.toString());
            totalHits = response.getHits().getTotalHits();
        } else {
            CountResponse response = node.client().prepareCount(indexName())
                    .setTypes(FsRiverUtil.INDEX_TYPE_DOC)
                    .setQuery(query).execute().actionGet();
            totalHits = response.getCount();
        }

        if (expected == null) {
            Assert.assertTrue("We should have at least one doc...", totalHits >= 1);
        } else {
            Assert.assertEquals(expected.intValue(), totalHits);
        }
    }

    /**
     * Check that we have at least one doc
     *
     * @throws Exception
     */
    public void countTestHelper() throws Exception {
        // Let's search for entries
        countTestHelper(null, null);
    }

}
