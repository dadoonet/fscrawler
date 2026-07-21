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

import fr.pilato.elasticsearch.crawler.fs.framework.bulk.Engine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ElasticsearchEngine
        implements Engine<ElasticsearchOperation, ElasticsearchBulkRequest, ElasticsearchBulkResponse> {
    private static final Logger logger = LogManager.getLogger();
    private final IElasticsearchClient elasticsearchClient;

    public ElasticsearchEngine(IElasticsearchClient elasticsearchClient) {
        this.elasticsearchClient = elasticsearchClient;
    }

    @Override
    public ElasticsearchBulkResponse bulk(ElasticsearchBulkRequest request) {
        String commonIndex = resolveCommonIndex(request);
        StringBuilder ndjson = new StringBuilder();

        request.getOperations().forEach(r -> {
            StringBuilder bulkRequest = new StringBuilder();
            // Header
            bulkRequest
                    .append("{\"")
                    .append(r.getOperation().asLowerCaseString())
                    .append("\":{");

            if (commonIndex == null) {
                bulkRequest.append("\"_index\":\"").append(r.getIndex()).append("\",");
            }

            bulkRequest.append("\"_id\":\"").append(r.getId()).append("\"");

            if (r instanceof ElasticsearchInsertOperation insertOp && insertOp.getPipeline() != null) {
                bulkRequest
                        .append(",\"pipeline\":\"")
                        .append(insertOp.getPipeline())
                        .append("\"");
            }
            bulkRequest.append("}}\n");
            if (r instanceof ElasticsearchInsertOperation insertOp) {
                // NDJSON needs one JSON object per line. Pretty-printed documents may contain
                // structural CR/LF; strip them without a Jackson round-trip (which rejects large
                // string values via StreamReadConstraints). Valid JSON never has raw CR/LF inside
                // strings — those must be escaped as \n / \r.
                bulkRequest.append(toSingleLineJson(insertOp.getJson())).append("\n");
            }
            logger.trace("Adding to bulk request: {}", bulkRequest);
            ndjson.append(bulkRequest);
        });

        logger.trace("Full bulk request {}", ndjson);
        logger.debug(
                "Sending a bulk request of [{}] documents to the Elasticsearch service", request.numberOfActions());
        String response;
        try {
            response = elasticsearchClient.bulk(commonIndex, ndjson.toString());
        } catch (ElasticsearchClientException e) {
            return new ElasticsearchBulkResponse(e);
        }
        return new ElasticsearchBulkResponse(response);
    }

    /**
     * If every operation targets the same index, return that index so the client can call {@code POST index/_bulk}.
     * Otherwise return {@code null} and keep {@code _index} on each action line.
     */
    static String resolveCommonIndex(ElasticsearchBulkRequest request) {
        String common = null;
        for (ElasticsearchOperation operation : request.getOperations()) {
            String index = operation.getIndex();
            if (common == null) {
                common = index;
            } else if (!common.equals(index)) {
                return null;
            }
        }
        return common;
    }

    /**
     * Collapse a JSON document onto a single line for NDJSON bulk requests.
     *
     * @param json JSON document, possibly pretty-printed
     * @return the same JSON without raw CR/LF characters
     */
    static String toSingleLineJson(String json) {
        if (json.indexOf('\n') < 0 && json.indexOf('\r') < 0) {
            return json;
        }
        return json.replace("\r", "").replace("\n", "");
    }
}
