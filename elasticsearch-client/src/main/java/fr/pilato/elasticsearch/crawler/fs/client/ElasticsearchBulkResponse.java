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

import com.jayway.jsonpath.DocumentContext;
import fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil;
import fr.pilato.elasticsearch.crawler.fs.framework.bulk.FsCrawlerBulkResponse;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ElasticsearchBulkResponse extends FsCrawlerBulkResponse<ElasticsearchOperation> {

    private static final Logger logger = LogManager.getLogger();

    private final ElasticsearchClientException exception;

    public ElasticsearchBulkResponse(ElasticsearchClientException exception) {
        this.exception = exception;
    }

    public ElasticsearchBulkResponse(String response) {
        exception = null;
        DocumentContext document = JsonUtil.parseJsonAsDocumentContext(response);
        boolean hasRealErrors = false;

        // Walk $.items (not $.._id): duplicate _ids in one bulk are valid with create, and we need the action name.
        List<Map<String, Object>> responseItems = document.read("$.items");
        for (Map<String, Object> responseItem : responseItems) {
            Map.Entry<String, Object> entry = responseItem.entrySet().iterator().next();
            String operationName = entry.getKey();
            @SuppressWarnings("unchecked")
            Map<String, Object> jsonItemResponse = (Map<String, Object>) entry.getValue();

            String index = (String) jsonItemResponse.get("_index");
            String id = (String) jsonItemResponse.get("_id");
            BulkItemResponse<ElasticsearchOperation> itemResponse = new BulkItemResponse<>();
            // Operation type is only needed for retry matching by id / logging; IndexOperation is enough.
            itemResponse.setOperation(new ElasticsearchIndexOperation(index, id, null, null));

            @SuppressWarnings("unchecked")
            Map<String, Object> error = (Map<String, Object>) jsonItemResponse.get("error");
            if (error != null) {
                String errorType = (String) error.get("type");
                String errorMessage = (String) error.get("reason");
                // First writer wins: a later create for an existing _id is expected when deduplicating.
                if ("create".equals(operationName) && "version_conflict_engine_exception".equals(errorType)) {
                    logger.debug("Ignoring expected create conflict for [{}/{}]: {}", index, id, errorMessage);
                } else {
                    itemResponse.setFailureMessage(errorMessage);
                    itemResponse.setFailed(true);
                    hasRealErrors = true;
                }
            }
            items.add(itemResponse);
        }

        errors = hasRealErrors;
    }

    @Override
    public Throwable buildFailureMessage() {
        if (exception != null) {
            return exception;
        }
        return super.buildFailureMessage();
    }
}
