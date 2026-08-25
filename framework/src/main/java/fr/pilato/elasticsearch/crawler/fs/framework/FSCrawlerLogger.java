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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FSCrawlerLogger {

    /** Logger name for detailed bulk failure dumps ({@code logs/bulk-failures.log} by default). */
    public static final String BULK_FAILURE_LOGGER_NAME = "fscrawler.bulk.failure";

    /**
     * Default relative path of the bulk-failure log file shipped in {@code config/log4j2.xml}. Mentioned from console /
     * {@code fscrawler.log} WARNs so operators know where to look.
     */
    public static final String BULK_FAILURES_LOG_FILE = "logs/bulk-failures.log";

    /** Max characters of a document body dumped at TRACE into the bulk-failure log. */
    public static final int BULK_FAILURE_PAYLOAD_MAX_CHARS = 8_192;

    private FSCrawlerLogger() {
        // Utility class, do not instantiate
    }

    /** This logger is for the console */
    private static final Logger consoleLogger = LogManager.getLogger("fscrawler.console");

    /** This logger is used to log information related to documents */
    private static final Logger documentLogger = LogManager.getLogger("fscrawler.document");

    /** This logger is used to help writing the test cases */
    private static final Logger metadataLogger = LogManager.getLogger("fscrawler.metadata");

    /** Dedicated logger for bulk failure details (actions / truncated payloads). */
    private static final Logger bulkFailureLogger = LogManager.getLogger(BULK_FAILURE_LOGGER_NAME);

    /**
     * Print something to the console
     *
     * @param message message to print
     * @param params parameters if any
     */
    public static void console(String message, Object... params) {
        consoleLogger.info(message, params);
    }

    /**
     * Log information in Debug Level about documents
     *
     * @param id Document ID
     * @param path Virtual path to the document
     * @param message Message to display
     */
    public static void documentDebug(String id, String path, String message) {
        documentLogger.debug("[{}][{}] {}", id, path, message);
    }

    /**
     * Log information in Error Level about documents
     *
     * @param id Document ID
     * @param path Virtual path to the document
     * @param error Error to display
     */
    public static void documentError(String id, String path, String error) {
        documentLogger.error("[{}][{}] {}", id, path, error);
    }

    /**
     * Print the metadata in a useful format, so they can be easily used in tests
     *
     * @param message message to print
     * @param params parameters if any
     */
    public static void metadata(String message, Object... params) {
        metadataLogger.debug(message, params);
    }

    /**
     * Log a bulk-failure detail line at WARN. {@code reason} is always prefixed so lines are greppable.
     *
     * @param reason short failure reason (HTTP error, item error, …)
     * @param message message template after the reason prefix
     * @param params template parameters
     */
    public static void bulkFailureWarn(String reason, String message, Object... params) {
        bulkFailureLogger.warn("[{}] " + message, prepend(reason, params));
    }

    /**
     * Log a truncated payload / extra context at TRACE under the bulk-failure logger.
     *
     * @param reason short failure reason
     * @param message message template after the reason prefix
     * @param params template parameters
     */
    public static void bulkFailureTrace(String reason, String message, Object... params) {
        if (bulkFailureLogger.isTraceEnabled()) {
            bulkFailureLogger.trace("[{}] " + message, prepend(reason, params));
        }
    }

    /** Truncate a bulk document body for TRACE dumps. */
    public static String truncateForBulkFailureLog(String payload) {
        if (payload == null) {
            return "null";
        }
        if (payload.length() <= BULK_FAILURE_PAYLOAD_MAX_CHARS) {
            return payload;
        }
        return payload.substring(0, BULK_FAILURE_PAYLOAD_MAX_CHARS) + "...(truncated)";
    }

    private static Object[] prepend(String reason, Object... params) {
        Object[] all = new Object[params.length + 1];
        all[0] = reason == null || reason.isBlank() ? "unknown" : reason;
        System.arraycopy(params, 0, all, 1, params.length);
        return all;
    }
}
