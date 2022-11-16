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

import fr.pilato.elasticsearch.crawler.fs.framework.bulk.FsCrawlerOperation;

public abstract class ElasticsearchOperation implements FsCrawlerOperation<ElasticsearchOperation> {
    private final Operation operation;
    private final String index;
    private final String id;

    enum Operation {
        INDEX,
        DELETE
    }

    public ElasticsearchOperation(Operation operation, String index, String id) {
        this.operation = operation;
        this.index = index;
        this.id = id;
    }

    public Operation getOperation() {
        return operation;
    }

    public String getIndex() {
        return index;
    }

    public String getId() {
        return id;
    }

    @Override
    public int compareTo(ElasticsearchOperation request) {
        // We check on the id field
        return id.compareTo(request.id);
    }

    @Override
    public String toString() {
        return "ElasticsearchOperation{" + "operation=" + operation +
                ", index='" + index + '\'' +
                ", id='" + id + '\'' +
                '}';
    }
}
