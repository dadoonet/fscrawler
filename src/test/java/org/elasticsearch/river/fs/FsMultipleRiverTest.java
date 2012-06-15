package org.elasticsearch.river.fs;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

import java.io.File;

import org.elasticsearch.action.count.CountResponse;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class FsMultipleRiverTest extends AbstractFsRiverTest {

	@Override
	public long waitingTime() throws Exception {
		return 5;
	}
	/**
	 * We use the default mapping
	 */
	@Override
	public XContentBuilder mapping() throws Exception {
		return null;
	}
	
	@Override @Before
	public void setUp() throws Exception {
		super.setUp();
		
		// In this case, we want to add another river
		// We update every five seconds
		int updateRate = 5 * 1000;
		String dir = "testfs2";
		
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
					.startObject("index")
						.field("index", indexName())
						.field("type", "otherdocs")
						.field("bulk_size", 5)
					.endObject()
				.endObject();

		addARiver(dir, xb);
		
		// Let's wait x seconds 
		Thread.sleep(waitingTime() * 1000);
	}

	/**
	 * 
	 * <ul>
	 *   <li>TODO Fill the use case
	 * </ul>
	 */
	@Override
	public XContentBuilder fsRiver() throws Exception {
		// We update every ten seconds
		int updateRate = 10 * 1000;
		String dir = "testfs1";
		
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
	public void index_must_have_two_files() throws Exception {
		// Let's search for entries
		CountResponse response = node.client().prepareCount(indexName()).setTypes("otherdocs","doc")
				.setQuery(QueryBuilders.matchAllQuery()).execute().actionGet();
		Assert.assertEquals("We should have two docs...", 2, response.count());
	}
}
