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
    private String username = "USERNAME";
    private String password = "PASSWORD";
    private String server = "localhost";

    /**
     * We wait for 5 seconds before each test
     */
    @Override
    public long waitingTime() throws Exception {
        return 5;
    }

    /**
     * We use the default mapping
     */
    @Override
    public String mapping() throws Exception {
        return null;
    }

    /**
     * <ul>
     * <li>TODO Fill the use case
     * </ul>
     */
    @Override
    public XContentBuilder fsRiver() throws Exception {
        // We update every minute
        int updateRate = 10 * 1000;
        String dir = "testsubdir";

        // First we check that filesystem to be analyzed exists...
        File dataDir = new File("./target/test-classes/" + dir);
        if (!dataDir.exists()) {
            throw new RuntimeException("src/test/resources/" + dir + " doesn't seem to exist. Check your JUnit tests.");
        }
        String url = dataDir.getAbsoluteFile().getAbsolutePath();

        XContentBuilder xb = jsonBuilder()
                .startObject()
                .field("type", "fs")
                .startObject("fs")
                .field("url", url + "/")
                .field("update_rate", updateRate)
                .field("username", username)
                .field("password", password)
                .field("protocol", FsRiver.PROTOCOL.SSH)
                .field("server", server)
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
    public void index_is_not_empty() throws Exception {
        countTestHelper(null, 2);
    }
}
