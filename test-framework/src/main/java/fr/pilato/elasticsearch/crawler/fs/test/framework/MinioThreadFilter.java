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
package fr.pilato.elasticsearch.crawler.fs.test.framework;

import com.carrotsearch.randomizedtesting.ThreadFilter;

/** This is temporary until https://github.com/minio/minio-java/issues/1584 is solved */
public class MinioThreadFilter implements ThreadFilter {
    @Override
    public boolean reject(Thread t) {
        return "Okio Watchdog".equals(t.getName())
                || "OkHttp TaskRunner".equals(t.getName())
                || "ForkJoinPool.commonPool-worker-1".equals(t.getName());
    }
}
