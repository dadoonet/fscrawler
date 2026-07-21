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
import fr.pilato.elasticsearch.crawler.fs.settings.BulkOperation;
import java.util.List;
import java.util.Locale;
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

        List<Map<String, Object>> responseItems = document.read("$.items");
        for (Map<String, Object> responseItem : responseItems) {
            Map.Entry<String, Object> entry = responseItem.entrySet().iterator().next();
            String operationName = entry.getKey();
            @SuppressWarnings("unchecked")
            Map<String, Object> jsonItemResponse = (Map<String, Object>) entry.getValue();

            String index = (String) jsonItemResponse.get("_index");
            String id = (String) jsonItemResponse.get("_id");
            BulkOperation operation = parseOperation(operationName);

            BulkItemResponse<ElasticsearchOperation> itemResponse = new BulkItemResponse<>();
            itemResponse.setOperation(toOperation(operation, index, id));

            @SuppressWarnings("unchecked")
            Map<String, Object> error = (Map<String, Object>) jsonItemResponse.get("error");
            if (error != null) {
                String errorType = (String) error.get("type");
                String errorMessage = (String) error.get("reason");
                if (isExpectedCreateConflict(operation, errorType, errorMessage)) {
                    // First writer wins: a later create for an existing _id is expected when deduplicating.
                    logger.debug("Ignoring expected create conflict for [{}/{}]: {}", index, id, errorMessage);
                    itemResponse.setFailed(false);
                    itemResponse.setFailureMessage(errorMessage);
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

    private static BulkOperation parseOperation(String operationName) {
        try {
            return BulkOperation.valueOf(operationName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            // Fallback for unexpected action names; treat as INDEX for response modelling.
            return BulkOperation.INDEX;
        }
    }

    private static ElasticsearchOperation toOperation(BulkOperation operation, String index, String id) {
        return switch (operation) {
            case CREATE -> new ElasticsearchCreateOperation(index, id, null, null);
            case DELETE -> new ElasticsearchDeleteOperation(index, id);
            case INDEX -> new ElasticsearchIndexOperation(index, id, null, null);
        };
    }

    /**
     * Elasticsearch returns a version conflict when bulk {@code create} targets an existing {@code _id}. That is the
     * intended “keep the first copy” behaviour for content-based ids.
     */
    static boolean isExpectedCreateConflict(BulkOperation operation, String errorType, String errorMessage) {
        if (operation != BulkOperation.CREATE) {
            return false;
        }
        if ("version_conflict_engine_exception".equals(errorType)) {
            return true;
        }
        return errorMessage != null && errorMessage.toLowerCase(Locale.ROOT).contains("document already exists");
    }

    @Override
    public Throwable buildFailureMessage() {
        if (exception != null) {
            return exception;
        }
        return super.buildFailureMessage();
    }
}
