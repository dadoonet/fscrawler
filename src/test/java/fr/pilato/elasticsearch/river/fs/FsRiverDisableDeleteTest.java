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
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.junit.Test;

import java.io.File;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

/**
 * Test case for issue #35: https://github.com/dadoonet/fsriver/issues/35
 */
public class FsRiverDisableDeleteTest extends AbstractFsRiverSimpleTest {
    private static int updateRate = 2 * 1000;
    private static String dir = "testfs_delete_disabled";
    private static String filename = "deletedfile.txt";

	/**
	 * We use the default mapping
	 */
	@Override
	public String mapping() throws Exception {
        XContentBuilder xb = jsonBuilder()
                .startObject()
                .startObject("doc")
                    .startObject("_source").field("enabled", false).endObject()
                    .startObject("properties")
                        .startObject(FsRiverUtil.DOC_FIELD_NAME)
                            .field("type", "string")
                            .field("analyzer","keyword")
                            .field("store", true)
                        .endObject()
                    .endObject()
                .endObject()
                .endObject();

		return xb.string();
	}

	/**
	 * 
	 * <ul>
	 *   <li>TODO Fill the use case
	 * </ul>
	 */
	@Override
	public XContentBuilder fsRiver() throws Exception {
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
                        .field("remove_deleted", false)
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
	public void deleted_file_should_not_be_removed() throws Exception {
        // We should have two docs first
        countTestHelper(null, 2);

        // We remove a file
        File file = new File("./target/test-classes/" + dir + "/" + filename);
        try {
            file.delete();
        } catch (Exception e) {
            // Ignoring
        }

        Thread.sleep(updateRate + 1000);

        // We expect to have two files
        countTestHelper(null, 2);
	}
}
