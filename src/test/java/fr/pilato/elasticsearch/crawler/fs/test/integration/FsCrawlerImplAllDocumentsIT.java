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

package fr.pilato.elasticsearch.crawler.fs.test.integration;

import fr.pilato.elasticsearch.crawler.fs.FsCrawlerImpl;
import fr.pilato.elasticsearch.crawler.fs.client.SearchResponse;
import fr.pilato.elasticsearch.crawler.fs.meta.job.FsJobFileHandler;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.Elasticsearch;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.Fs;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.util.FsCrawlerUtil;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Test all type of documents we have
 */
public class FsCrawlerImplAllDocumentsIT extends AbstractITCase {

    @BeforeClass
    public static void startCrawling() throws Exception {
        Path testResourceTarget = rootTmpDir.resolve("resources");
        if (Files.notExists(testResourceTarget)) {
            Files.createDirectory(testResourceTarget);
        }

        // We copy files from the src dir to the temp dir
        String url = getUrl("documents");
        Path from = Paths.get(url);

        staticLogger.debug("  --> Copying test resources from [{}]", from);
        if (Files.notExists(from)) {
            staticLogger.error("directory [{}] should be copied to [{}]", from, testResourceTarget);
            throw new RuntimeException(from + " doesn't seem to exist. Check your JUnit tests.");
        }

        FsCrawlerUtil.copyDirs(from, testResourceTarget);

        staticLogger.debug("  --> Test resources ready in [{}]", testResourceTarget);

        Long numFiles = Files.list(testResourceTarget).count();

        staticLogger.info(" -> Removing existing index [fscrawler_test_all_documents]");
        elasticsearchClient.deleteIndex("fscrawler_test_all_documents");

        staticLogger.info("  --> starting crawler in [{}] which contains [{}] files", testResourceTarget, numFiles);

        FsCrawlerImpl crawler = new FsCrawlerImpl(metadataDir,
                FsSettings.builder("fscrawler_test_all_documents")
                        .setElasticsearch(Elasticsearch.builder()
                                .addNode(Elasticsearch.Node.builder().setHost("127.0.0.1").setPort(HTTP_TEST_PORT).build())
                                .setBulkSize(5)
                                .setFlushInterval(TimeValue.timeValueSeconds(1))
                                .build())
                        .setFs(Fs.builder()
                                .setUrl(testResourceTarget.toString())
                                .build())
                        .build());
        crawler.start();

        // We wait up to 10 seconds before considering a failing test
        assertThat("Job meta file should exists in ~/.fscrawler...", awaitBusy(() -> {
            try {
                new FsJobFileHandler(metadataDir).read("fscrawler_test_all_documents");
                return true;
            } catch (IOException e) {
                return false;
            }
        }), equalTo(true));

        countTestHelper("fscrawler_test_all_documents", null, numFiles.intValue(), null);

        // Make sure we refresh indexed docs before launching tests
        refresh();

        staticLogger.info("  --> Stopping crawler");
        crawler.close();
    }

    /**
     * Test case for https://github.com/dadoonet/fscrawler/issues/163
     */
    @Test
    public void testXmlIssue163() throws IOException {
        SearchResponse response = elasticsearchClient.search("fscrawler_test_all_documents", null, "file.filename:\"issue-163.xml\"");
        assertThat(response.getHits().getTotal(), is(1L));
    }

    @Test
    public void testExtractFromDoc() throws IOException {
        SearchResponse response = elasticsearchClient.search("fscrawler_test_all_documents", null, "+content:sample +file.filename:\"test.doc\"");
        assertThat(response.getHits().getTotal(), is(1L));
    }

    @Test
    public void testExtractFromDocx() throws IOException {
        SearchResponse response = elasticsearchClient.search("fscrawler_test_all_documents", null, "+content:sample +file.filename:\"test.docx\"", null, "*");
        assertThat(response.getHits().getTotal(), is(1L));

        for (SearchResponse.Hit hit : response.getHits().getHits()) {
            assertThat(hit.getFields().get(FsCrawlerUtil.Doc.FILE + "." + FsCrawlerUtil.Doc.File.FILENAME), notNullValue());
            assertThat(hit.getFields().get(FsCrawlerUtil.Doc.FILE + "." + FsCrawlerUtil.Doc.File.CONTENT_TYPE), notNullValue());
            assertThat(hit.getFields().get(FsCrawlerUtil.Doc.FILE + "." + FsCrawlerUtil.Doc.File.URL), notNullValue());
            assertThat(hit.getFields().get(FsCrawlerUtil.Doc.FILE + "." + FsCrawlerUtil.Doc.File.FILESIZE), notNullValue());
            assertThat(hit.getFields().get(FsCrawlerUtil.Doc.FILE + "." + FsCrawlerUtil.Doc.File.INDEXING_DATE), notNullValue());
            assertThat(hit.getFields().get(FsCrawlerUtil.Doc.FILE + "." + FsCrawlerUtil.Doc.File.LAST_MODIFIED), notNullValue());

            assertThat(hit.getFields().get(FsCrawlerUtil.Doc.META + "." + FsCrawlerUtil.Doc.Meta.TITLE), notNullValue());
            assertThat(hit.getFields().get(FsCrawlerUtil.Doc.META + "." + FsCrawlerUtil.Doc.Meta.KEYWORDS), notNullValue());
        }

    }

    @Test
    public void testExtractFromHtml() throws IOException {
        SearchResponse response = elasticsearchClient.search("fscrawler_test_all_documents", null, "+content:sample +file.filename:\"test.html\"");
        assertThat(response.getHits().getTotal(), is(1L));
    }

    @Test
    public void testExtractFromMp3() throws IOException {
        SearchResponse response = elasticsearchClient.search("fscrawler_test_all_documents", null, "+content:tika +file.filename:\"test.mp3\"");
        assertThat(response.getHits().getTotal(), is(1L));
    }

    @Test
    public void testExtractFromOdt() throws IOException {
        SearchResponse response = elasticsearchClient.search("fscrawler_test_all_documents", null, "+content:sample +file.filename:\"test.odt\"");
        assertThat(response.getHits().getTotal(), is(1L));
    }

    @Test
    public void testExtractFromPdf() throws IOException {
        SearchResponse response = elasticsearchClient.search("fscrawler_test_all_documents", null, "+content:sample +file.filename:\"test.pdf\"");
        assertThat(response.getHits().getTotal(), is(1L));
    }

    @Test
    public void testExtractFromRtf() throws IOException {
        SearchResponse response = elasticsearchClient.search("fscrawler_test_all_documents", null, "+content:sample +file.filename:\"test.rtf\"");
        assertThat(response.getHits().getTotal(), is(1L));
    }

    @Test
    public void testExtractFromTxt() throws IOException {
        SearchResponse response = elasticsearchClient.search("fscrawler_test_all_documents", null, "+content:contains +file.filename:\"test.txt\"");
        assertThat(response.getHits().getTotal(), is(1L));
    }

    @Test
    public void testExtractFromWav() throws IOException {
        SearchResponse response = elasticsearchClient.search("fscrawler_test_all_documents", null, "file.filename:\"test.wav\"");
        assertThat(response.getHits().getTotal(), is(1L));
    }


}
