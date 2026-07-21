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
package fr.pilato.elasticsearch.crawler.fs.client;

import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class ElasticsearchBulkResponseTest extends AbstractFSCrawlerTestCase {

    @Test
    void createConflictIsNotTreatedAsFailure() {
        String response = """
                {
                  "errors": true,
                  "items": [
                    {
                      "create": {
                        "_index": "docs",
                        "_id": "checksum-1",
                        "status": 201
                      }
                    },
                    {
                      "create": {
                        "_index": "docs",
                        "_id": "checksum-1",
                        "status": 409,
                        "error": {
                          "type": "version_conflict_engine_exception",
                          "reason": "[checksum-1]: version conflict, document already exists (current version [1])"
                        }
                      }
                    }
                  ]
                }
                """;

        ElasticsearchBulkResponse bulkResponse = new ElasticsearchBulkResponse(response);

        Assertions.assertThat(bulkResponse.isErrors()).isFalse();
        Assertions.assertThat(bulkResponse.hasFailures()).isFalse();
        Assertions.assertThat(bulkResponse.getItems()).hasSize(2);
        Assertions.assertThat(bulkResponse.getItems().get(0).isFailed()).isFalse();
        Assertions.assertThat(bulkResponse.getItems().get(1).isFailed()).isFalse();
        Assertions.assertThat(bulkResponse.getItems().get(0).getOperation().getId())
                .isEqualTo("checksum-1");
        Assertions.assertThat(bulkResponse.getItems().get(1).getOperation().getId())
                .isEqualTo("checksum-1");
    }

    @Test
    void realCreateErrorIsStillAFailure() {
        String response = """
                {
                  "errors": true,
                  "items": [
                    {
                      "create": {
                        "_index": "docs",
                        "_id": "1",
                        "status": 400,
                        "error": {
                          "type": "mapper_parsing_exception",
                          "reason": "failed to parse field [foo] of type [long]"
                        }
                      }
                    }
                  ]
                }
                """;

        ElasticsearchBulkResponse bulkResponse = new ElasticsearchBulkResponse(response);

        Assertions.assertThat(bulkResponse.isErrors()).isTrue();
        Assertions.assertThat(bulkResponse.hasFailures()).isTrue();
        Assertions.assertThat(bulkResponse.getItems()).hasSize(1);
        Assertions.assertThat(bulkResponse.getItems().get(0).isFailed()).isTrue();
        Assertions.assertThat(bulkResponse.getItems().get(0).getFailureMessage())
                .contains("failed to parse field");
    }

    @Test
    void itemsWithDuplicateIdsAreParsedInOrder() {
        String response = """
                {
                  "errors": false,
                  "items": [
                    {
                      "index": {
                        "_index": "docs",
                        "_id": "1",
                        "status": 201
                      }
                    },
                    {
                      "delete": {
                        "_index": "docs",
                        "_id": "1",
                        "status": 200,
                        "result": "deleted"
                      }
                    }
                  ]
                }
                """;

        ElasticsearchBulkResponse bulkResponse = new ElasticsearchBulkResponse(response);

        Assertions.assertThat(bulkResponse.hasFailures()).isFalse();
        Assertions.assertThat(bulkResponse.getItems()).hasSize(2);
        Assertions.assertThat(bulkResponse.getItems().get(0).getOperation().getId())
                .isEqualTo("1");
        Assertions.assertThat(bulkResponse.getItems().get(1).getOperation().getId())
                .isEqualTo("1");
    }
}
