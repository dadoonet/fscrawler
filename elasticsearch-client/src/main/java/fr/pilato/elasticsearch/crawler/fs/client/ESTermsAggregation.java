/*
 * Licensed to David Pilato under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ESTermsAggregation {
    private final String name;
    private final String field;
    private final List<ESTermsBucket> buckets = new ArrayList<>();

    public ESTermsAggregation(String name, String field) {
        this.name = name;
        this.field = field;
    }

    public String getField() {
        return field;
    }

    public String getName() {
        return name;
    }

    public List<ESTermsBucket> getBuckets() {
        return buckets;
    }

    public void addBucket(ESTermsBucket bucket) {
        this.buckets.add(bucket);
    }

    public static class ESTermsBucket {
        private final String key;
        private final long docCount;

        public ESTermsBucket(String key, long docCount) {
            this.key = key;
            this.docCount = docCount;
        }

        public String getKey() {
            return key;
        }

        public long getDocCount() {
            return docCount;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ESTermsBucket that = (ESTermsBucket) o;
            return docCount == that.docCount && key.equals(that.key);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key, docCount);
        }
    }
}
