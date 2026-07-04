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
package fr.pilato.elasticsearch.crawler.fs.rest;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.pilato.elasticsearch.crawler.fs.FsParser;
import fr.pilato.elasticsearch.crawler.fs.beans.FsCrawlerCheckpointFileHandler;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CrawlerApi#clearCheckpoint()}.
 *
 * <p>These replace the flaky {@code test_delete_checkpoint_while_running} integration test. The guard that forbids
 * clearing the checkpoint while the crawler is actively scanning is a trivial state check ({@code !isClosed() &&
 * !isPaused()}), so we exercise it directly with a mocked {@link FsParser} rather than racing a real crawl whose tiny
 * scan finishes before the DELETE request lands.
 *
 * <p>Mockito is used programmatically (no {@code MockitoExtension}) to stay independent from the JUnit Jupiter 6
 * extension API.
 */
class CrawlerApiTest extends AbstractFSCrawlerTestCase {

    private static final String JOB_NAME = "test-job";

    private FsParser fsParser;
    private FsCrawlerCheckpointFileHandler checkpointHandler;
    private CrawlerApi crawlerApi;

    @BeforeEach
    void setUp() {
        fsParser = mock(FsParser.class);
        checkpointHandler = mock(FsCrawlerCheckpointFileHandler.class);
        crawlerApi = new CrawlerApi(fsParser, JOB_NAME);
    }

    /** Guard: while the crawler is actively running (neither closed nor paused), clearing must be refused. */
    @Test
    void clearCheckpointIsRefusedWhileRunning() throws IOException {
        when(fsParser.isClosed()).thenReturn(false);
        when(fsParser.isPaused()).thenReturn(false);

        try (Response response = crawlerApi.clearCheckpoint()) {
            SimpleResponse entity = (SimpleResponse) response.getEntity();

            Assertions.assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
            Assertions.assertThat(entity.isOk()).isFalse();
            Assertions.assertThat(entity.getMessage())
                    .contains("Cannot clear checkpoint while crawler is running. Pause or stop it first.");
        }

        // The checkpoint file must NOT have been touched while the crawler is running.
        verify(checkpointHandler, never()).clean(JOB_NAME);
    }

    /** Happy path: when the crawler is paused, clearing succeeds and the checkpoint file is cleaned. */
    @Test
    void clearCheckpointSucceedsWhenPaused() throws IOException {
        when(fsParser.isClosed()).thenReturn(false);
        when(fsParser.isPaused()).thenReturn(true);
        when(fsParser.getCheckpointHandler()).thenReturn(checkpointHandler);

        try (Response response = crawlerApi.clearCheckpoint()) {
            SimpleResponse entity = (SimpleResponse) response.getEntity();

            Assertions.assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
            Assertions.assertThat(entity.isOk()).isTrue();
            Assertions.assertThat(entity.getMessage()).contains("Checkpoint cleared");
        }

        verify(checkpointHandler).clean(JOB_NAME);
    }

    /** Happy path: when the crawler is stopped (closed), clearing succeeds too. */
    @Test
    void clearCheckpointSucceedsWhenClosed() throws IOException {
        when(fsParser.isClosed()).thenReturn(true);
        when(fsParser.getCheckpointHandler()).thenReturn(checkpointHandler);

        try (Response response = crawlerApi.clearCheckpoint()) {
            SimpleResponse entity = (SimpleResponse) response.getEntity();

            Assertions.assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
            Assertions.assertThat(entity.isOk()).isTrue();
        }

        verify(checkpointHandler).clean(JOB_NAME);
    }

    /** Failure path: an IOException while cleaning is surfaced as a 500 with an explanatory message. */
    @Test
    void clearCheckpointReturnsServerErrorOnIOException() throws IOException {
        when(fsParser.isClosed()).thenReturn(true);
        when(fsParser.getCheckpointHandler()).thenReturn(checkpointHandler);
        doThrow(new IOException("disk full")).when(checkpointHandler).clean(JOB_NAME);

        try (Response response = crawlerApi.clearCheckpoint()) {
            SimpleResponse entity = (SimpleResponse) response.getEntity();

            Assertions.assertThat(response.getStatus())
                    .isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
            Assertions.assertThat(entity.isOk()).isFalse();
            Assertions.assertThat(entity.getMessage())
                    .contains("Failed to clear checkpoint")
                    .contains("disk full");
        }
    }
}
