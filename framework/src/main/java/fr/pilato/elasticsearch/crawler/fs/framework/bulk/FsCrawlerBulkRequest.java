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

package fr.pilato.elasticsearch.crawler.fs.framework.bulk;

import java.util.ArrayList;
import java.util.List;

public abstract class FsCrawlerBulkRequest<T extends FsCrawlerOperation<T>> {

    private final List<T> operations = new ArrayList<>();

    public int numberOfActions() {
        return operations.size();
    }

    public void add(T request) {
        operations.add(request);
    }

    public List<T> getOperations() {
        return operations;
    }
}
