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
package fr.pilato.elasticsearch.crawler.fs.framework.bulk;

import com.carrotsearch.randomizedtesting.jupiter.RandomizedTest;
import fr.pilato.elasticsearch.crawler.fs.framework.FSCrawlerLogger;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import java.io.StringWriter;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.WriterAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FsCrawlerSimpleBulkProcessorListenerTest extends AbstractFSCrawlerTestCase {

    private static final String APPENDER_NAME = "bulk-failure-test-writer";

    private StringWriter bulkFailureWriter;
    private WriterAppender writerAppender;
    private Level previousLevel;

    @BeforeEach
    void attachBulkFailureAppender() {
        bulkFailureWriter = new StringWriter();
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();

        writerAppender = WriterAppender.newBuilder()
                .setName(APPENDER_NAME)
                .setTarget(bulkFailureWriter)
                .setLayout(PatternLayout.newBuilder().withPattern("%p %m%n").build())
                .build();
        writerAppender.start();
        config.addAppender(writerAppender);

        LoggerConfig loggerConfig = config.getLoggerConfig(FSCrawlerLogger.BULK_FAILURE_LOGGER_NAME);
        previousLevel = loggerConfig.getLevel();
        if (!FSCrawlerLogger.BULK_FAILURE_LOGGER_NAME.equals(loggerConfig.getName())) {
            loggerConfig = new LoggerConfig(FSCrawlerLogger.BULK_FAILURE_LOGGER_NAME, Level.TRACE, false);
            config.addLogger(FSCrawlerLogger.BULK_FAILURE_LOGGER_NAME, loggerConfig);
        } else {
            loggerConfig.setLevel(Level.TRACE);
        }
        loggerConfig.addAppender(writerAppender, Level.TRACE, null);
        ctx.updateLoggers();
    }

    @AfterEach
    void detachBulkFailureAppender() {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        LoggerConfig loggerConfig = config.getLoggerConfig(FSCrawlerLogger.BULK_FAILURE_LOGGER_NAME);
        loggerConfig.removeAppender(APPENDER_NAME);
        if (previousLevel != null && FSCrawlerLogger.BULK_FAILURE_LOGGER_NAME.equals(loggerConfig.getName())) {
            loggerConfig.setLevel(previousLevel);
        }
        if (writerAppender != null) {
            writerAppender.stop();
        }
        config.getAppenders().remove(APPENDER_NAME);
        ctx.updateLoggers();
    }

    @Test
    void afterBulkThrowable_logsReasonFirstAndHintsAtBulkFailuresFile() {
        String reason = "Read timed out";
        String id = RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 6, 12);
        TestOperation op = new TestOperation(new TestBean(id));
        TestBulkRequest request = new TestBulkRequest();
        request.add(op);

        FsCrawlerSimpleBulkProcessorListener<TestOperation, TestBulkRequest, TestBulkResponse> listener =
                new FsCrawlerSimpleBulkProcessorListener<>();
        listener.afterBulk(1L, request, new RuntimeException(reason));

        String logged = bulkFailureWriter.toString();
        Assertions.assertThat(logged)
                .contains("[" + reason + "]")
                .contains("executionId=1")
                .contains("failed for 1 actions")
                .contains(op.toString());
        Assertions.assertThat(FSCrawlerLogger.BULK_FAILURES_LOG_FILE).isEqualTo("logs/bulk-failures.log");
    }

    @Test
    void afterBulkItemFailures_logsPerItemReasonFirst() {
        String reason = "mapper_parsing_exception";
        String id = RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 6, 12);
        TestOperation op = new TestOperation(new TestBean(id));
        TestBulkRequest request = new TestBulkRequest();
        request.add(op);

        TestBulkResponse response = new TestBulkResponse();
        response.errors = true;
        FsCrawlerBulkResponse.BulkItemResponse<TestOperation> item = new FsCrawlerBulkResponse.BulkItemResponse<>();
        item.setFailed(true);
        item.setOperation(op);
        item.setFailureMessage(reason);
        response.getItems().add(item);

        FsCrawlerSimpleBulkProcessorListener<TestOperation, TestBulkRequest, TestBulkResponse> listener =
                new FsCrawlerSimpleBulkProcessorListener<>();
        listener.afterBulk(7L, request, response);

        String logged = bulkFailureWriter.toString();
        Assertions.assertThat(logged)
                .contains("[" + reason + "]")
                .contains("executionId=7")
                .contains(op.toString());
    }

    @Test
    void truncateForBulkFailureLog_limitsPayload() {
        String longPayload = "x".repeat(FSCrawlerLogger.BULK_FAILURE_PAYLOAD_MAX_CHARS + 50);
        String truncated = FSCrawlerLogger.truncateForBulkFailureLog(longPayload);
        Assertions.assertThat(truncated)
                .hasSize(FSCrawlerLogger.BULK_FAILURE_PAYLOAD_MAX_CHARS + "...(truncated)".length())
                .endsWith("...(truncated)");
    }
}
