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

import com.jayway.jsonpath.DocumentContext;
import fr.pilato.elasticsearch.crawler.fs.framework.bulk.FsCrawlerBulkResponse;

import java.util.List;
import java.util.Map;

import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.parseJsonAsDocumentContext;

public class ElasticsearchBulkResponse extends FsCrawlerBulkResponse<ElasticsearchOperation> {

    private final ElasticsearchClientException exception;

    public ElasticsearchBulkResponse(ElasticsearchClientException exception) {
        this.exception = exception;
    }

    public ElasticsearchBulkResponse(String response) {
        exception = null;
        // We need to parse the response object
        DocumentContext document = parseJsonAsDocumentContext(response);
        errors = document.read("$.errors");
        List<String> ids = document.read("$.._id");
        ids.forEach(id -> {
            Map<String, Object> jsonItemResponse = ((List<Map<String, Object>>) document.read("$..[?(@._id == '" + id + "')]")).get(0);
            String index = (String) jsonItemResponse.get("_index");
            BulkItemResponse<ElasticsearchOperation> itemResponse = new BulkItemResponse<>();
            itemResponse.setOperation(new ElasticsearchIndexOperation(index, id, null, null));
            Map<String, Object> error = (Map<String, Object>) jsonItemResponse.get("error");
            if (error != null) {
                String errorMessage = (String) error.get("reason");
                itemResponse.setFailureMessage(errorMessage);
                itemResponse.setFailed(true);
            }
            items.add(itemResponse);
        });
    }

    @Override
    public Throwable buildFailureMessage() {
        if (exception != null) {
            return exception;
        }
        return super.buildFailureMessage();
    }
}
