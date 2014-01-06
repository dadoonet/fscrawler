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
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.count.CountResponse;
import org.elasticsearch.common.io.FileSystemUtils;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.indices.IndexMissingException;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;
import org.junit.Before;
import org.junit.BeforeClass;

import java.io.File;

public abstract class AbstractFsRiverTest {

	private static final String GATEWAY_TYPE = "local";
	private static final String PATH_ES_DATA = "./target/es/data";
	private static final String PATH_ES_LOGS = "./target/es/logs";
	private static final String PATH_ES_WORK = "./target/es/work";
	private static final int DEFAULT_WAITING_TIME = 5;
	private static final int DEFAULT_EXPECTED_NUMBER_OF_DOCUMENTS = 1;
	private static final String DEFAULT_MAPPING = null;
	protected static Node node;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		if (node == null) {
			deleteOldDataIfExists();

			node = buildTestNode();
			waitUntilNodeIsReady();
			deleteIndexIfExists("_river");
		}
	}

	protected static File verifyDirectoryExists(String dir) {
		File dataDir = new File("./target/test-classes/" + dir);
		if (!dataDir.exists()) {
			throw new RuntimeException("src/test/resources/" + dir + " doesn't seem to exist. Check your JUnit tests.");
		}
		return dataDir;
	}

	private static Node buildTestNode() {
		return NodeBuilder
				.nodeBuilder()
				.local(true)
				.settings(
						ImmutableSettings.settingsBuilder()
								.put("gateway.type", GATEWAY_TYPE)
								.put("path.data", PATH_ES_DATA)
								.put("path.logs", PATH_ES_LOGS)
								.put("path.work", PATH_ES_WORK)
				).node();
	}

	private static void waitUntilNodeIsReady() {
		node.client().admin().cluster().prepareHealth().setWaitForYellowStatus().execute().actionGet();
	}

	private static void deleteOldDataIfExists() {
		File dataDir = new File(PATH_ES_DATA);
		if (dataDir.exists()) {
			FileSystemUtils.deleteRecursively(dataDir, true);
		}
	}

	private static void deleteIndexIfExists(String indexName) throws InterruptedException {
		try {
			node.client().admin().indices()
					.delete(new DeleteIndexRequest(indexName)).actionGet();
			waitUntilNodeIsReady();
		} catch (IndexMissingException ignored) {
		}
	}

	@Before
	public void baseSetUp() throws Exception {
		XContentBuilder fsRiver = fsRiverSettings();
		String indexName = indexName();
		String mapping = mapping();

		deleteIndexIfExists(indexName);
		createIndex(indexName);

		if (mapping != null) {
			putMapping(indexName, mapping);
		}

		verifyFsRiverIsProvidedBySubClasses(fsRiver);

		addARiver(indexName, fsRiver);
		waitForIndexToHaveAtLeast(expectedNumberOfDocumentsInTheRiver());
	}

	/**
	 * Define a unique index name
	 *
	 * @return The unique index name (could be this.getClass().getSimpleName())
	 */
	protected String indexName() {
		return this.getClass().getSimpleName().toLowerCase();
	}

	/**
	 * Define the FS River settings
	 *
	 * @return FS River Settings
	 */
	protected abstract XContentBuilder fsRiverSettings() throws Exception;

	/**
	 * Define a mapping if needed
	 *
	 * @return The mapping to use
	 */
	protected String mapping() throws Exception {
		return DEFAULT_MAPPING;
	}

	protected int expectedNumberOfDocumentsInTheRiver() {
		return DEFAULT_EXPECTED_NUMBER_OF_DOCUMENTS;
	}

	/**
	 * Define the waiting time in seconds before launching a test
	 *
	 * @return Waiting time (in seconds)
	 */
	protected long timeLimitWaitingForTheRiver() throws Exception {
		return DEFAULT_WAITING_TIME;
	}

	protected void addARiver(String riverName, XContentBuilder fsRiver) throws Exception {
		node.client().prepareIndex("_river", riverName, "_meta").setSource(fsRiver).execute().actionGet();
	}

	protected void waitForIndexToHaveAtLeast(long numberOfDocuments) throws Exception {
		long timeLimit = timeLimitWaitingForTheRiver() * 1000;
		long currentTime = 0;
		long deltaTime = 100;
		while (currentTime < timeLimit) {
			final long count = getTotalHitsFromCountRequest(QueryBuilders.matchAllQuery());
			if (count >= numberOfDocuments) {
				return; //We know the river is done indexing stuff
			} else {
				currentTime += deltaTime;
				Thread.sleep(deltaTime);
				refreshTestNode(indexName());
			}
		}
	}

	protected long getTotalHitsFromCountRequest(QueryBuilder query) {
		long totalHits;
		CountResponse response = node.client().prepareCount(indexName())
				.setTypes(FsRiverUtil.INDEX_TYPE_DOC)
				.setQuery(query).execute().actionGet();
		totalHits = response.getCount();
		return totalHits;
	}

	protected void waitUntilNextUpdate(int updateRate) throws InterruptedException {
		Thread.sleep(updateRate + 1000);
	}

	private void verifyFsRiverIsProvidedBySubClasses(XContentBuilder fsRiver) throws Exception {
		if (fsRiver == null) {
			throw new Exception("Subclasses must provide an fs setup...");
		}
	}

	private void putMapping(String indexName, String mapping) {
		node.client().admin().indices()
				.preparePutMapping(indexName)
				.setType(FsRiverUtil.INDEX_TYPE_DOC)
				.setSource(mapping)
				.execute().actionGet();
	}

	private void refreshTestNode(String indexName) {
		node.client().admin().indices().prepareRefresh(indexName).execute().actionGet();
	}

	private void createIndex(String indexName) throws InterruptedException {
		node.client().admin().indices().create(new CreateIndexRequest(indexName)).actionGet();
		waitUntilNodeIsReady();
	}

}
