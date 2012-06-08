package org.elasticsearch.river.fs;

import java.io.File;

import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.common.io.FileSystemUtils;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.indices.IndexMissingException;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;
import org.junit.Before;
import org.junit.BeforeClass;

public abstract class AbstractFsRiverTest {

	/**
	 * Define a unique index name
	 * @return The unique index name (could be this.getClass().getSimpleName())
	 */
	protected String indexName() {
		return this.getClass().getSimpleName().toLowerCase();
	}
	
	/**
	 * Define a mapping if needed
	 * @return The mapping to use
	 */
	abstract public XContentBuilder mapping() throws Exception;
	
	/**
	 * Define the Rss River settings
	 * @return Rss River Settings
	 */
	abstract public XContentBuilder fsRiver() throws Exception;
	
	/**
	 * Define the waiting time in seconds before launching a test
	 * @return Waiting time (in seconds)
	 */
	abstract public long waitingTime() throws Exception;
	
	protected static Node node;
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		if (node == null) {
			// First we delete old datas...
			File dataDir = new File("./target/es/data");
			if(dataDir.exists()) {
				FileSystemUtils.deleteRecursively(dataDir, true);
			}
			
			// Then we start our node for tests
			node = NodeBuilder
					.nodeBuilder()
					.settings(
							ImmutableSettings.settingsBuilder()
							.put("gateway.type", "local")
							.put("path.data", "./target/es/data")		
							.put("path.logs", "./target/es/logs")		
							.put("path.work", "./target/es/work")		
							).node();
			
			// We wait now for the yellow (or green) status
			node.client().admin().cluster().prepareHealth().setWaitForYellowStatus().execute().actionGet(); 
			
			// We clean existing rivers
			try {
				node.client().admin().indices()
						.delete(new DeleteIndexRequest("_river")).actionGet();
				// We wait for one second to let ES delete the river
				Thread.sleep(1000);
			} catch (IndexMissingException e) {
				// Index does not exist... Fine
			}
		}
	}

	@Before
	public void setUp() throws Exception {
		XContentBuilder fsRiver = fsRiver();
		String indexName = indexName();
		XContentBuilder mapping = mapping();
		
		// We delete the index before we start any test
		try {
			node.client().admin().indices()
					.delete(new DeleteIndexRequest(indexName)).actionGet();
			// We wait for one second to let ES delete the index
			Thread.sleep(1000);
		} catch (IndexMissingException e) {
			// Index does not exist... Fine
		}
		
		// Creating the index
		node.client().admin().indices().create(new CreateIndexRequest(indexName)).actionGet();
		Thread.sleep(1000);

		// If a mapping is defined, we will use it
		if (mapping != null) {
			node.client().admin().indices()
			.preparePutMapping(indexName)
			.setType("page")
			.setSource(mapping)
			.execute().actionGet();
		}

		if (fsRiver == null) throw new Exception("Subclasses must provide an fs setup...");
		node.client().prepareIndex("_river", indexName, "_meta").setSource(fsRiver)
				.execute().actionGet();
		
		// Let's wait x seconds 
		Thread.sleep(waitingTime() * 1000);
	}

}
