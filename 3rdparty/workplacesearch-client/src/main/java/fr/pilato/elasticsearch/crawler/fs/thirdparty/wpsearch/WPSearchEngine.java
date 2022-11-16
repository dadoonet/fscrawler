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

package fr.pilato.elasticsearch.crawler.fs.thirdparty.wpsearch;

import fr.pilato.elasticsearch.crawler.fs.framework.bulk.Engine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static fr.pilato.elasticsearch.crawler.fs.thirdparty.wpsearch.WPSearchClient.DEFAULT_WS_ENDPOINT;

public class WPSearchEngine implements Engine<WPSearchOperation, WPSearchBulkRequest, WPSearchBulkResponse> {
    private static final Logger logger = LogManager.getLogger(WPSearchEngine.class);
    private final WPSearchClient wpSearchClient;

    public WPSearchEngine(WPSearchClient wpSearchClient) {
        this.wpSearchClient = wpSearchClient;
    }

    @Override
    public WPSearchBulkResponse bulk(WPSearchBulkRequest request) {

        // We need to split the request by custom id in case we have multiple ones
        // the custom id is actually extracted from the index name
        Map<String, List<Map<String, Object>>> operationsBySource = new HashMap<>();
        for (WPSearchOperation operation : request.getOperations()) {
            List<Map<String, Object>> documents = operationsBySource.getOrDefault(operation.getCustomSourceId(), new ArrayList<>());
            documents.add(operation.getDocument());
            operationsBySource.putIfAbsent(operation.getCustomSourceId(), documents);
        }

        Map<String, String> responses = new HashMap<>();
        for (String sourceId : operationsBySource.keySet()) {
            try {
                String urlForBulkCreate = "sources/" + sourceId + "/documents/bulk_create";

                logger.debug("Sending a bulk request of [{}] documents to the Workplace Search service [{}]",
                        operationsBySource.get(sourceId).size(), wpSearchClient.toString());
                String response = wpSearchClient.post(DEFAULT_WS_ENDPOINT, urlForBulkCreate, operationsBySource.get(sourceId), String.class);
                responses.put(sourceId, response);
            } catch (Exception e) {
                logger.error(e);
                responses.put(sourceId, e.getMessage());
            }
        }
        return new WPSearchBulkResponse(responses);
    }
}
