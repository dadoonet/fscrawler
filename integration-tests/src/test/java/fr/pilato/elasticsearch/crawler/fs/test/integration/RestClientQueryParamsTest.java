/*
 * Licensed to David Pilato (the "Author") under one
 * or more contributor license agreements. See the NOTICE file
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
 *
 * Made from 🇫🇷🇪🇺 with ❤️ - 2011-2026
 */
package fr.pilato.elasticsearch.crawler.fs.test.integration;

import com.carrotsearch.randomizedtesting.jupiter.RandomizedTest;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;
import java.util.LinkedHashMap;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage for {@link AbstractRestITCase#withQueryParams(WebTarget, Map)} — Jersey {@code queryParam} is immutable
 * and must be reassigned.
 */
class RestClientQueryParamsTest extends AbstractFSCrawlerTestCase {

    @Test
    void withQueryParamsReassignsImmutableWebTarget() {
        String index = RandomizedTest.randomAsciiLettersOfLength(randomizedRandomForTests, 8);
        String id = RandomizedTest.randomAsciiLettersOfLength(randomizedRandomForTests, 6);

        Client client = ClientBuilder.newClient();
        try {
            WebTarget target = client.target("http://localhost/_document");
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("index", index);
            params.put("id", id);

            WebTarget withParams = AbstractRestITCase.withQueryParams(target, params);

            Assertions.assertThat(withParams.getUri().getQuery())
                    .contains("index=" + index)
                    .contains("id=" + id);
            Assertions.assertThat(target.getUri().getQuery()).isNull();
        } finally {
            client.close();
        }
    }
}
