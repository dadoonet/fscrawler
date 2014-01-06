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

import fr.pilato.elasticsearch.river.fs.river.FsRiver;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

/**
 * You have to adapt this test to your own system (login / password and SSH connexion)
 * So this test is disabled by default
 */
@Ignore
public class FsSshRiverTest extends AbstractFsRiverSimpleTest {
	private static final String USERNAME = "USERNAME";
	private static final String PASSWORD = "PASSWORD";
	private static final String HOSTNAME = "localhost";
	private static final int UPDATE_RATE = 10 * 1000;
	private static final String DIRECTORY = "testsubdir";
	private static final int EXPECTED_NUMBER_OF_DOCUMENTS = 2;

	@Override
	public XContentBuilder fsRiverSettings() throws Exception {
		File dataDir = verifyDirectoryExists(DIRECTORY);
		String url = dataDir.getAbsoluteFile().getAbsolutePath();

		XContentBuilder xb = jsonBuilder()
				.startObject()
				.field("type", "fs")
				.startObject("fs")
				.field("url", url + "/")
				.field("update_rate", UPDATE_RATE)
				.field("username", USERNAME)
				.field("password", PASSWORD)
				.field("protocol", FsRiver.PROTOCOL.SSH)
				.field("server", HOSTNAME)
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
	public void index_contains_all_documents() throws Exception {
		countTestHelper(null, EXPECTED_NUMBER_OF_DOCUMENTS);
	}
}
