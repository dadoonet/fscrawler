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

import org.elasticsearch.common.io.FileSystemUtils;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.junit.Test;

import java.io.File;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

/**
 * Test case for issue #10: https://github.com/dadoonet/fsriver/issues/10
 */
public class FsRiverDisableSourceTest extends AbstractFsRiverSimpleTest {

	@Override
	public long waitingTime() throws Exception {
		return 5;
	}
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

		return xb.toString();
	}

	/**
	 * 
	 * <ul>
	 *   <li>TODO Fill the use case
	 * </ul>
	 */
	@Override
	public XContentBuilder fsRiver() throws Exception {
		// We update 2 seconds
		int updateRate = 2 * 1000;
		String dir = "testfs_source_disabled";
		
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
					.endObject()
				.endObject();
		return xb;
	}
	

	@Test
	public void index_is_not_empty() throws Exception {
        // We should have one doc first
        countTestHelper(null, 1);

        // We need to remove the old file and wait for the river
        int updateRate = 2 * 1000;
        String dir = "testfs_source_disabled";
        String filename = "roottxtfile.txt";

        // First we check that filesystem to be analyzed exists...
        File file1 = new File("./target/test-classes/" + dir + "/" + filename);
        File file2 = new File("./target/test-classes/" + dir + "/new_" + filename);
        FileSystemUtils.copyFile(file1, file2);

        Thread.sleep(updateRate + 1000);

        countTestHelper(null, 2);

        file2.delete();

        Thread.sleep(updateRate + 1000);

        countTestHelper(null, 1);
	}
}
