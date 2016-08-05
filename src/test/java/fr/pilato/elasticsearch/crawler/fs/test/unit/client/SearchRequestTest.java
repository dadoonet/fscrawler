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

package fr.pilato.elasticsearch.crawler.fs.test.unit.client;

import fr.pilato.elasticsearch.crawler.fs.client.JsonUtil;
import fr.pilato.elasticsearch.crawler.fs.client.SearchRequest;
import fr.pilato.elasticsearch.crawler.fs.test.AbstractFSCrawlerTestCase;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

public class SearchRequestTest extends AbstractFSCrawlerTestCase {

    @Test
    public void testSerializeDeserialize() {
        launchSerializeTest(null, null);
        launchSerializeTest("hello", null);
        launchSerializeTest("hello", 10);
        launchSerializeTest("hello", null, "field1");
        launchSerializeTest("hello", null, "field1", "field2");
        launchSerializeTest("hello", 10, "field1", "field2");
    }

    private void launchSerializeTest(String query, Integer size, String... fields) {
        SearchRequest expected;
        if (fields.length == 0) {
            expected = new SearchRequest(query, null, size);
        } else {
            expected = new SearchRequest(query, fields, size);
        }
        String json = JsonUtil.serialize(expected);
        logger.info("asJson -> {}", json);

        if (query != null) {
            assertThat(json, containsString("\"query\""));
            assertThat(json, containsString(query));
        } else {
            assertThat(json, not(containsString("\"query\"")));
        }
        if (size != null) {
            assertThat(json, containsString("\"size\""));
            assertThat(json, containsString(size.toString()));
        } else {
            assertThat(json, not(containsString("\"size\"")));
        }
        if (fields.length > 0) {
            assertThat(json, containsString("\"fields\""));
            for (String field : fields) {
                assertThat(json, containsString(field));
            }
        } else {
            assertThat(json, not(containsString("\"fields\"")));
        }

        InputStream stream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        SearchRequest request = JsonUtil.deserialize(stream, SearchRequest.class);
        logger.info("asObject -> {}", request);
        assertThat(request, is(expected));

        stream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        Map<String, Object> map = JsonUtil.asMap(stream);
        logger.info("asMap -> {}", map);

        if (query != null) {
            assertThat(map, hasEntry("query", query));
        } else {
            assertThat(map, not(hasKey("query")));
        }
        if (size != null) {
            assertThat(map, hasEntry("size", size));
        } else {
            assertThat(map, not(hasKey("size")));
        }
        if (fields.length > 0) {
            assertThat(map, hasKey("fields"));
            List<String> arrayFields = (List<String>) map.get("fields");
            assertThat(arrayFields, contains(fields));
        } else {
            assertThat(map, not(hasKey("fields")));
        }
    }
}
