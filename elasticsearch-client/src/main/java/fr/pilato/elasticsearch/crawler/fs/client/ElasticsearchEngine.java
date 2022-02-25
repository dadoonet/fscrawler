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

package fr.pilato.elasticsearch.crawler.fs.client;

import fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil;
import fr.pilato.elasticsearch.crawler.fs.framework.bulk.Engine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Locale;

import static fr.pilato.elasticsearch.crawler.fs.client.IElasticsearchClient.INDEX_TYPE_DOC;

public class ElasticsearchEngine implements Engine<ElasticsearchOperation, ElasticsearchBulkRequest, ElasticsearchBulkResponse> {
    private static final Logger logger = LogManager.getLogger(ElasticsearchEngine.class);
    private final IElasticsearchClient elasticsearchClient;

    public ElasticsearchEngine(IElasticsearchClient elasticsearchClient) {
        this.elasticsearchClient = elasticsearchClient;
    }

    @Override
    public ElasticsearchBulkResponse bulk(ElasticsearchBulkRequest request) {
        StringBuilder ndjson = new StringBuilder();

        request.getOperations().forEach(r -> {
            StringBuilder bulkRequest = new StringBuilder();
            // Header
            bulkRequest.append("{\"")
                    .append(r.getOperation().toString().toLowerCase(Locale.ROOT))
                    .append("\":{\"_index\":\"")
                    .append(r.getIndex())
                    .append("\"");

            if (elasticsearchClient.getMajorVersion() < 7) {
                // Before version 7, the _type was needed
                bulkRequest.append(",\"_type\":\"")
                        .append(INDEX_TYPE_DOC)
                        .append("\"");
            }

            bulkRequest.append(",\"_id\":\"")
                    .append(r.getId())
                    .append("\"");

            if (r instanceof ElasticsearchIndexOperation && ((ElasticsearchIndexOperation) r).getPipeline() != null) {
                bulkRequest
                        .append(",\"pipeline\":\"")
                        .append(((ElasticsearchIndexOperation) r).getPipeline())
                        .append("\"");
            }
            bulkRequest.append("}}\n");
            if (r instanceof ElasticsearchIndexOperation) {
                ElasticsearchIndexOperation indexOp = (ElasticsearchIndexOperation) r;
                bulkRequest.append(JsonUtil.serialize(JsonUtil.deserialize(indexOp.getJson(), Object.class))).append("\n");
            }
            logger.trace("Adding to bulk request: {}", bulkRequest);
            ndjson.append(bulkRequest);
        });

        logger.trace("Full bulk request {}", ndjson);
        logger.debug("Sending a bulk request of [{}] documents to the Elasticsearch service", request.numberOfActions());
        String response;
        try {
            response = elasticsearchClient.bulk(ndjson.toString());
        } catch (ElasticsearchClientException e) {
            return new ElasticsearchBulkResponse(e);
        }
        return new ElasticsearchBulkResponse(response);
    }
}
