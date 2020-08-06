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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class WPSearchEngine implements Engine<WPSearchOperation, WPSearchBulkRequest, WPSearchBulkResponse> {
    private static final Logger logger = LogManager.getLogger(WPSearchEngine.class);
    private final WPSearchClient wpSearchClient;

    public WPSearchEngine(WPSearchClient wpSearchClient) {
        this.wpSearchClient = wpSearchClient;
    }

    @Override
    public WPSearchBulkResponse bulk(WPSearchBulkRequest request) {
        List<Map<String, Object>> documents = new ArrayList<>();
        for (WPSearchOperation operation : request.getOperations()) {
            documents.add(operation.getDocument());
        }

        try {
            String response = wpSearchClient.post(wpSearchClient.urlForBulkCreate, documents, String.class);
            return new WPSearchBulkResponse(response);
        } catch (Exception e) {
            logger.error(e);
            return new WPSearchBulkResponse(e.getMessage());
        }
    }
}
