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
package fr.pilato.elasticsearch.crawler.fs.settings;

import java.util.Locale;

/**
 * Elasticsearch bulk actions used by FSCrawler.
 *
 * <p>For {@code elasticsearch.bulk_operation}, only {@link #INDEX} and {@link #CREATE} are allowed. {@link #DELETE} is
 * used internally for delete bulk items and must not be set in job settings.
 */
public enum BulkOperation {
    /** Create or replace the document with the given {@code _id} (default for document writes). */
    INDEX,
    /** Create the document only if the {@code _id} does not already exist. */
    CREATE,
    /** Delete the document with the given {@code _id} (internal use only). */
    DELETE;

    public String asLowerCaseString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
