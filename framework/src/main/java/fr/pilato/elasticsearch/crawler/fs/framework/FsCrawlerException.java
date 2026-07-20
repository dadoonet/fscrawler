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
package fr.pilato.elasticsearch.crawler.fs.framework;

/**
 * Base unchecked exception for FSCrawler domain failures.
 *
 * <p>Callers that want to handle any FSCrawler-specific runtime error can catch this type. Elasticsearch client
 * failures remain modeled by the checked {@code ElasticsearchClientException}.
 */
public class FsCrawlerException extends RuntimeException {
    public FsCrawlerException(String message) {
        super(message);
    }

    public FsCrawlerException(String message, Throwable cause) {
        super(message, cause);
    }

    public FsCrawlerException(Throwable cause) {
        super(cause);
    }
}
