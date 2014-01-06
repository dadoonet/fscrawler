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

import org.elasticsearch.common.io.FileSystemUtils;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

/**
 * Test case for issue #35: https://github.com/dadoonet/fsriver/issues/35
 */
public class FsRiverDisableDeleteTest extends AbstractFsRiverSimpleTest {

	private static final int UPDATE_RATE = 2 * 1000;
	private static final String DIRECTORY = "testfs_delete_disabled";
	private static final String FILENAME = "roottxtfile.txt";

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
				.field("remove_deleted", false)
				.endObject()
				.startObject("index")
				.field("index", indexName())
				.field("type", "doc")
				.field("bulk_size", 1)
				.endObject()
				.endObject();
	}

	@Test
	public void deleted_file_should_not_be_removed() throws Exception {
		createACopyOfTheFile();
		waitUntilNextUpdate(UPDATE_RATE);

		countTestHelper(null, 2);

		removeCopiedFile();
		waitUntilNextUpdate(UPDATE_RATE);

		countTestHelper(null, 2);
	}

	private void removeCopiedFile() {
		File file = new File("./target/test-classes/" + DIRECTORY + "/deleted_" + FILENAME);
		try {
			file.delete();
		} catch (Exception ignored) {
		}
	}

	private void createACopyOfTheFile() throws IOException {
		File file1 = new File("./target/test-classes/" + DIRECTORY + "/" + FILENAME);
		File file2 = new File("./target/test-classes/" + DIRECTORY + "/deleted_" + FILENAME);
		FileSystemUtils.copyFile(file1, file2);
	}
}
