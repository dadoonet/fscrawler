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
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.junit.Test;

import java.io.File;

import static org.elasticsearch.common.io.Streams.copyToStringFromClasspath;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.junit.Assert.assertNotNull;

public class FsRiverMetadataTest extends AbstractFsRiverSimpleTest {

	/**
	 * We use the default mapping
	 */
	@Override
	public String mapping() throws Exception {
        String mapping = copyToStringFromClasspath("/testfs_metadata/odt-mapping.json");
		return mapping;
	}

	/**
	 * 
	 * <ul>
	 *   <li>We want to check that the FSRiver extract also metadata (see https://github.com/dadoonet/fsriver/issues/14)
	 * </ul>
	 */
	@Override
	public XContentBuilder fsRiver() throws Exception {
        // We update every minute
		int updateRate = 10 * 1000;
		String dir = "testfs_metadata";
		
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
						.field("url", url)
						.field("update_rate", updateRate)
                        .field("excludes", "*.json")
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
	public void we_have_metadata() throws Exception {
        SearchResponse searchResponse = node.client().prepareSearch(indexName()).setTypes("doc")
                .setQuery(QueryBuilders.matchAllQuery())
                .addField("*")
                .execute().actionGet();

        for (SearchHit hit : searchResponse.getHits()) {
            assertNotNull(hit.getFields().get("file.content_type"));
            assertNotNull(hit.getFields().get("file.keywords"));
            assertNotNull(hit.getFields().get("file.date"));
            assertNotNull(hit.getFields().get("file.title"));
            assertNotNull(hit.getFields().get(FsRiverUtil.DOC_FIELD_URL));
        }

	}
}
