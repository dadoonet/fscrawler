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
import org.elasticsearch.common.io.FileSystemUtils;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.junit.Test;

import java.io.File;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

/**
 * Test case for issue #10: https://github.com/dadoonet/fsriver/issues/10
 */
public class FsRiverDisableSourceTest extends AbstractFsRiverSimpleTest {
    private static int updateRate = 2 * 1000;
    private static String dir = "testfs_source_disabled";
    private static String filename = "roottxtfile.txt";

    /**
     * We use the default mapping
     */
    @Override
    public String mapping() throws Exception {
        return FsRiverUtil.buildFsFileMapping("doc", false, false).string();
    }

    /**
     * <ul>
     * <li>TODO Fill the use case
     * </ul>
     */
    @Override
    public XContentBuilder fsRiver() throws Exception {
        // First we check that filesystem to be analyzed exists...
        File dataDir = new File("./target/test-classes/" + dir);
        if (!dataDir.exists()) {
            throw new RuntimeException("src/test/resources/" + dir + " doesn't seem to exist. Check your JUnit tests.");
        }

        // We need to remove previous file from prior tests
        File file2 = new File("./target/test-classes/" + dir + "/new_" + filename);
        try {
            file2.delete();
        } catch (Exception e) {
            // Ignoring
        }

        String url = dataDir.getAbsoluteFile().getAbsolutePath();

        return jsonBuilder()
                .startObject()
                .field("type", "fs")
                .startObject("fs")
                .field("url", url)
                .field("update_rate", updateRate)
                .endObject()
                .startObject("index")
                .field("index", indexName())
                .field("type", "doc")
                .field("bulk_size", 1)
                .endObject()
                .endObject();
    }


    @Test
    public void index_is_not_empty() throws Exception {
        // We should have one doc first
        countTestHelper(null, 1);

        // We add a new file
        File file1 = new File("./target/test-classes/" + dir + "/" + filename);
        File file2 = new File("./target/test-classes/" + dir + "/new_" + filename);
        FileSystemUtils.copyFile(file1, file2);

        Thread.sleep(updateRate + 1000);

        // We expect to have one more file indexed
        countTestHelper(null, 2);

        // We remove the old file and wait for the river
        file2.delete();

        Thread.sleep(updateRate + 1000);

        // We expect to have one file indexed only
        countTestHelper(null, 1);
    }
}
