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
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

/**
 * Test case for issue #53: https://github.com/dadoonet/fsriver/issues/53
 */
public class FsRiverFilenameSearchTest extends AbstractFsRiverSimpleTest {
    private static int updateRate = 2 * 1000;
    private static String dir = "testfs1";
    private static String filename = "roottxtfile.txt";

    /**
     * We use the default mapping
     */
    @Override
    public String mapping() throws Exception {
        return null;
    }

    @Override
    public XContentBuilder fsRiver() throws Exception {
        // First we check that filesystem to be analyzed exists...
        File dataDir = new File("./target/test-classes/" + dir);
        if (!dataDir.exists()) {
            throw new RuntimeException("src/test/resources/" + dir + " doesn't seem to exist. Check your JUnit tests.");
        }

        String url = dataDir.getAbsoluteFile().getAbsolutePath();

        return jsonBuilder()
                .startObject()
                .field("type", "fs")
                .startObject("fs")
                .field("url", url)
                .field("update_rate", updateRate)
                .endObject()
                .startObject("index")
                .field("index", indexName())
                .field("type", "doc")
                .field("bulk_size", 1)
                .endObject()
                .endObject();
    }


    @Test
    public void index_is_not_empty() throws Exception {
        // We should have one doc first
        countTestHelper(null, 1);

        // We search on filename. Should still intact
        QueryBuilder query = QueryBuilders.termQuery("file.filename", filename);

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

        Assert.assertTrue("We should have at least one doc...", totalHits >= 1);
    }
}
