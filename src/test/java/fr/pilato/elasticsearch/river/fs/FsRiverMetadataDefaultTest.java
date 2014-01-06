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

import static junit.framework.Assert.assertNull;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.junit.Assert.assertNotNull;

public class FsRiverMetadataDefaultTest extends AbstractFsRiverSimpleTest {

	private static final int UPDATE_RATE = 10 * 1000;
	private static final String DIRECTORY = "testfs_metadata";
	private static final String TYPE_NAME = "doc";

	/**
	 * <ul>
	 * <li>We want to check that the FSRiver extract also metadata
	 * with some defaults (content_type) (see https://github.com/dadoonet/fsriver/issues/22)
	 * </ul>
	 */
	@Override
	public XContentBuilder fsRiverSettings() throws Exception {
		File dataDir = verifyDirectoryExists(DIRECTORY);
		String url = dataDir.getAbsoluteFile().getAbsolutePath();

		return jsonBuilder()
				.startObject()
				.field("type", "fs")
				.startObject("fs")
				.field("url", url)
				.field("update_rate", UPDATE_RATE)
				.field("excludes", "*.json")
				.endObject()
				.startObject("index")
				.field("index", indexName())
				.field("type", "doc")
				.field("bulk_size", 1)
				.endObject()
				.endObject();
	}

	@Test
	public void we_have_metadata() throws Exception {
		SearchResponse searchResponse = node.client().prepareSearch(indexName()).setTypes(TYPE_NAME)
				.setQuery(QueryBuilders.matchAllQuery())
				.addField("*")
				.execute().actionGet();

		for (SearchHit hit : searchResponse.getHits()) {
			assertNull(hit.getFields().get(FsRiverUtil.Doc.ATTACHMENT));

			assertNotNull(hit.getFields().get(FsRiverUtil.Doc.FILE + "." + FsRiverUtil.Doc.File.FILENAME));
			assertNotNull(hit.getFields().get(FsRiverUtil.Doc.FILE + "." + FsRiverUtil.Doc.File.CONTENT_TYPE));
			assertNotNull(hit.getFields().get(FsRiverUtil.Doc.FILE + "." + FsRiverUtil.Doc.File.URL));
			assertNotNull(hit.getFields().get(FsRiverUtil.Doc.FILE + "." + FsRiverUtil.Doc.File.FILESIZE));
			assertNotNull(hit.getFields().get(FsRiverUtil.Doc.FILE + "." + FsRiverUtil.Doc.File.INDEXING_DATE));
			assertNull(hit.getFields().get(FsRiverUtil.Doc.FILE + "." + FsRiverUtil.Doc.File.INDEXED_CHARS));
			assertNotNull(hit.getFields().get(FsRiverUtil.Doc.FILE + "." + FsRiverUtil.Doc.File.LAST_MODIFIED));

			assertNotNull(hit.getFields().get(FsRiverUtil.Doc.META + "." + FsRiverUtil.Doc.Meta.TITLE));
			assertNotNull(hit.getFields().get(FsRiverUtil.Doc.META + "." + FsRiverUtil.Doc.Meta.DATE));
		}
	}
}
