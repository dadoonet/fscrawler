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
import fr.pilato.elasticsearch.crawler.fs.client.SearchResponse;
import fr.pilato.elasticsearch.crawler.fs.test.AbstractFSCrawlerTestCase;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

public class SearchResponseTest extends AbstractFSCrawlerTestCase {

    @Test
    public void testSerializeDeserialize() {
        String json = "{\"took\":4,\"timed_out\":false,\"_shards\":{\"total\":5,\"successful\":5,\"failed\":0},\"hits\":{\"total\":1," +
                "\"max_score\":1.0,\"hits\":[{\"_index\":\"index\",\"_type\":\"type\",\"_id\":\"id\",\"_score\":1.0,\"_source\":{" +
                "\"foo\":\"bar\"}}]}}";

        InputStream stream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        SearchResponse response = JsonUtil.deserialize(stream, SearchResponse.class);
        logger.info("asObject -> {}", response);

        assertThat(response.getHits().getTotal(), is(1L));
        assertThat(response.getHits().getHits(), hasSize(1));
        assertThat(response.getHits().getHits().get(0).getSource(), hasEntry("foo", "bar"));
        assertThat(response.getHits().getHits().get(0).getFields(), nullValue());

        stream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        Map<String, Object> map = JsonUtil.asMap(stream);
        logger.info("asMap -> {}", map);

    }
}
