/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

package fr.pilato.elasticsearch.crawler.fs.test.unit.client;

import fr.pilato.elasticsearch.crawler.fs.client.BulkResponse;
import fr.pilato.elasticsearch.crawler.fs.client.JsonUtil;
import fr.pilato.elasticsearch.crawler.fs.test.AbstractFSCrawlerTestCase;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;


public class BulkResponseTest  extends AbstractFSCrawlerTestCase {

    @Test
    public void testSerializeDeserializeMultipleLines() {
        String json = "{\"took\":4,\"errors\":false,\"items\":[{\"index\":{\"_index\":\"fscrawler_test_bulk_with_time\"," +
                "\"_type\":\"doc\",\"_id\":\"id1\",\"_version\":2,\"forced_refresh\":false,\"_shards\":{\"total\":2,\"successful\":1," +
                "\"failed\":0},\"created\":false,\"status\":200}},{\"index\":{\"_index\":\"fscrawler_test_bulk_with_time\"," +
                "\"_type\":\"doc\",\"_id\":\"id2\",\"_version\":2,\"forced_refresh\":false,\"_shards\":{\"total\":2,\"successful\":1," +
                "\"failed\":0},\"created\":false,\"status\":200}}]}";

        InputStream stream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        BulkResponse response = JsonUtil.deserialize(stream, BulkResponse.class);
        logger.info("asObject -> {}", response);
    }

    @Test
    public void testSerializeDeserializeSingleLine() {
        String json = "{\n" +
                "   \"took\":4,\n" +
                "   \"errors\":false,\n" +
                "   \"items\":[\n" +
                "      {\n" +
                "         \"index\":{\n" +
                "            \"_index\":\"fscrawler_test_bulk_with_time\",\n" +
                "            \"_type\":\"doc\",\n" +
                "            \"_id\":\"id1\",\n" +
                "            \"_version\":2,\n" +
                "            \"forced_refresh\":false,\n" +
                "            \"_shards\":{\n" +
                "               \"total\":2,\n" +
                "               \"successful\":1,\n" +
                "               \"failed\":0\n" +
                "            },\n" +
                "            \"created\":false,\n" +
                "            \"status\":200\n" +
                "         }\n" +
                "      }\n" +
                "   ]\n" +
                "}";

        InputStream stream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        BulkResponse response = JsonUtil.deserialize(stream, BulkResponse.class);
        logger.info("asObject -> {}", response);
    }

    /*
    {
  "took" : 141,
  "errors" : true,
  "items" : [
    {
      "index" : {
        "_index" : "fscrawler_test_bulk_with_time",
        "_type" : "doc",
        "_id" : "id1",
        "status" : 400,
        "error" : {
          "type" : "illegal_argument_exception",
          "reason" : "mapper [int] of different type, current_type [text], merged_type [long]"
        }
      }
    },
    {
      "index" : {
        "_index" : "fscrawler_test_bulk_with_time",
        "_type" : "doc",
        "_id" : "id2",
        "_version" : 3,
        "forced_refresh" : false,
        "_shards" : {
          "total" : 2,
          "successful" : 1,
          "failed" : 0
        },
        "created" : false,
        "status" : 200
      }
    }
  ]
}

     */
}
