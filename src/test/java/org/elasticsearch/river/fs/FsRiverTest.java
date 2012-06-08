package org.elasticsearch.river.fs;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

import java.io.File;

import org.apache.log4j.Logger;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.junit.Test;

public class FsRiverTest extends AbstractFsRiverSimpleTest {

	private Logger logger = Logger.getLogger(FsRiverTest.class);

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

	/**
	 * 
	 * <ul>
	 *   <li>TODO Fill the use case
	 * </ul>
	 */
	@Override
	public XContentBuilder fsRiver() throws Exception {
		// We update every minute
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
						.startArray("fs")
							.startObject()
								.field("name", dir)
								.field("url", url)
								.field("update_rate", updateRate)
							.endObject()
						.endArray()
					.endObject()
				.endObject();
		return xb;
	}
	

	@Test
	public void index_is_not_empty() throws Exception {
		countTestHelper();
	}
}
