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

package fr.pilato.elasticsearch.crawler.fs.test.integration.elasticsearch;

import com.carrotsearch.randomizedtesting.annotations.Timeout;
import com.carrotsearch.randomizedtesting.annotations.TimeoutSuite;
import com.jayway.jsonpath.DocumentContext;
import fr.pilato.elasticsearch.crawler.fs.beans.Folder;
import fr.pilato.elasticsearch.crawler.fs.client.*;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.carrotsearch.randomizedtesting.RandomizedTest.randomAsciiLettersOfLengthBetween;
import static com.carrotsearch.randomizedtesting.RandomizedTest.randomLongBetween;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.INDEX_SUFFIX_FOLDER;
import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.parseJsonAsDocumentContext;
import static fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase.TIMEOUT_MINUTE_AS_MS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test crawler with subdirs
 */
@TimeoutSuite(millis = 5 * TIMEOUT_MINUTE_AS_MS)
public class FsCrawlerTestSubDirsIT extends AbstractFsCrawlerITCase {
    private static final Logger logger = LogManager.getLogger();

    @Test
    public void subdirs() throws Exception {
        crawler = startCrawler();

        // We expect to have two files
        ESSearchResponse searchResponse = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 2L, null);

        // We check that the subdir document has his meta path data correctly set
        assertThat(searchResponse.getHits())
                .isNotEmpty()
                .allSatisfy(hit -> {
                    DocumentContext document = parseJsonAsDocumentContext(hit.getSource());
                    assertThat((String) document.read("$.path.virtual"))
                            .containsAnyOf("/subdir/roottxtfile_multi_feed.txt", "/roottxtfile.txt");
                });

        // Try to search within part of the full path, ie subdir
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()).withESQuery(new ESTermQuery("path.virtual.fulltext", "subdir")), 1L, null);
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()).withESQuery(new ESTermQuery("path.real.fulltext", "subdir")), 1L, null);
    }

    @Test
    public void subdirs_deep_tree() throws Exception {
        crawler = startCrawler();

        // We expect to have 7 files
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 7L, null);

        // We expect to have 7 folders
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName() + INDEX_SUFFIX_FOLDER), 7L, null);

        // Run aggs
        ESSearchResponse response = client.search(new ESSearchRequest()
                        .withIndex(getCrawlerName())
                        .withSize(0)
                        .withAggregation(new ESTermsAggregation("folders", "path.virtual.tree")));
        assertThat(response.getTotalHits()).isEqualTo(7L);

        // aggregations
        assertThat(response.getAggregations()).containsKey("folders");
        ESTermsAggregation aggregation = response.getAggregations().get("folders");
        List<ESTermsAggregation.ESTermsBucket> buckets = aggregation.getBuckets();

        assertThat(buckets).hasSize(10);

        // Check files
        response = client.search(new ESSearchRequest().withIndex(getCrawlerName()).withSort("path.virtual"));
        assertThat(response.getTotalHits()).isEqualTo(7L);

        DocumentContext document = parseJsonAsDocumentContext(response.getJson());

        int i = 0;
        pathHitTester(document, i++, "/subdirs_deep_tree/roottxtfile.txt", "/roottxtfile.txt");
        pathHitTester(document, i++, "/subdirs_deep_tree/subdir1/roottxtfile_multi_feed.txt", "/subdir1/roottxtfile_multi_feed.txt");
        pathHitTester(document, i++, "/subdirs_deep_tree/subdir1/subdir11/roottxtfile.txt", "/subdir1/subdir11/roottxtfile.txt");
        pathHitTester(document, i++, "/subdirs_deep_tree/subdir1/subdir12/roottxtfile.txt", "/subdir1/subdir12/roottxtfile.txt");
        pathHitTester(document, i++, "/subdirs_deep_tree/subdir2/roottxtfile_multi_feed.txt", "/subdir2/roottxtfile_multi_feed.txt");
        pathHitTester(document, i++, "/subdirs_deep_tree/subdir2/subdir21/roottxtfile.txt", "/subdir2/subdir21/roottxtfile.txt");
        pathHitTester(document, i, "/subdirs_deep_tree/subdir2/subdir22/roottxtfile.txt", "/subdir2/subdir22/roottxtfile.txt");


        // Check folders
        response = client.search(new ESSearchRequest().withIndex(getCrawlerName() + INDEX_SUFFIX_FOLDER).withSort("path.virtual"));
        assertThat(response.getTotalHits()).isEqualTo(7L);

        document = parseJsonAsDocumentContext(response.getJson());

        i = 0;
        folderHitTester(document, i++, "/subdirs_deep_tree", "/", "subdirs_deep_tree");
        folderHitTester(document, i++, "/subdirs_deep_tree/subdir1", "/subdir1", "subdir1");
        folderHitTester(document, i++, "/subdirs_deep_tree/subdir1/subdir11", "/subdir1/subdir11", "subdir11");
        folderHitTester(document, i++, "/subdirs_deep_tree/subdir1/subdir12", "/subdir1/subdir12", "subdir12");
        folderHitTester(document, i++, "/subdirs_deep_tree/subdir2", "/subdir2", "subdir2");
        folderHitTester(document, i++, "/subdirs_deep_tree/subdir2/subdir21", "/subdir2/subdir21", "subdir21");
        folderHitTester(document, i, "/subdirs_deep_tree/subdir2/subdir22", "/subdir2/subdir22", "subdir22");
    }

    @Test
    @Timeout(millis = 5 * TIMEOUT_MINUTE_AS_MS)
    public void subdirs_very_deep_tree() throws Exception {

        long subdirs = randomLongBetween(30, 100);

        logger.debug("  --> Generating [{}] dirs [{}]", subdirs, currentTestResourceDir);

        Path sourceFile = currentTestResourceDir.resolve("roottxtfile.txt");
        Path mainDir = currentTestResourceDir.resolve("main_dir");
        Files.createDirectory(mainDir);
        Path newDir = mainDir;

        for (int i = 0; i < subdirs; i++) {
            newDir = newDir.resolve(i + "_" + randomAsciiLettersOfLengthBetween(2, 5));
            Files.createDirectory(newDir);
            // Copy the original test file in the new dir
            Files.copy(sourceFile, newDir.resolve("sample.txt"));
        }

        crawler = startCrawler(createTestSettings(), MAX_WAIT_FOR_SEARCH_LONG_TESTS);

        // We expect to have x files (<- whoa that's funny Mulder!)
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), subdirs+1, null, MAX_WAIT_FOR_SEARCH_LONG_TESTS);

        // Run aggs
        ESSearchResponse response = client.search(new ESSearchRequest()
                .withIndex(getCrawlerName())
                .withSize(0)
                .withAggregation(new ESTermsAggregation("folders", "path.virtual.tree")));
        assertThat(response.getTotalHits()).isEqualTo(subdirs + 1);

        // aggregations
        assertThat(response.getAggregations()).containsKey("folders");
        ESTermsAggregation aggregation = response.getAggregations().get("folders");
        List<ESTermsAggregation.ESTermsBucket> buckets = aggregation.getBuckets();

        assertThat(buckets).hasSize(10);

        // Check files
        response = client.search(new ESSearchRequest()
                .withIndex(getCrawlerName())
                .withSize(1000)
                .withSort("path.virtual"));
        assertThat(response.getTotalHits()).isEqualTo(subdirs + 1);

        DocumentContext document = parseJsonAsDocumentContext(response.getJson());

        for (int i = 0; i < subdirs; i++) {
            pathHitTesterEndWith(document, i, "sample.txt", "/sample.txt");
        }

        // Check folders
        response = client.search(new ESSearchRequest()
                .withIndex(getCrawlerName() + INDEX_SUFFIX_FOLDER)
                .withSize(1000)
                .withSort("path.virtual"));
        assertThat(response.getTotalHits()).isEqualTo(subdirs + 2);

        // Let's remove the main subdir and wait...
        logger.debug("  --> Removing all dirs from [{}]", mainDir);
        deleteRecursively(mainDir);

        // We expect to have 1 doc now but this could take some time to happen
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, currentTestResourceDir, MAX_WAIT_FOR_SEARCH_LONG_TESTS);
    }

    private void folderHitTester(DocumentContext document, int position, String expectedReal, String expectedVirtual,
                                 String expectedFilename) {
        pathHitTester(document, position, expectedReal, expectedVirtual);
        assertThat((String) document.read("$.hits.hits[" + position + "]._source.file.filename"))
                .isEqualTo(expectedFilename);
        assertThat((String) document.read("$.hits.hits[" + position + "]._source.file.content_type"))
                .isEqualTo(Folder.CONTENT_TYPE);
    }

    private void pathHitTester(DocumentContext document, int position, String expectedReal, String expectedVirtual) {
        String real = document.read("$.hits.hits[" + position + "]._source.path.real");
        String virtual = document.read("$.hits.hits[" + position + "]._source.path.virtual");
        logger.trace(" - {}, {}", real, virtual);
        assertThat(real)
                .as("path.real[" + position + "]")
                .endsWith(expectedReal);
        assertThat(virtual)
                .as("path.virtual[" + position + "]")
                .isEqualTo(expectedVirtual);
    }

    private void pathHitTesterEndWith(DocumentContext document, int position, String expectedReal, String expectedVirtual) {
        String real = document.read("$.hits.hits[" + position + "]._source.path.real");
        String virtual = document.read("$.hits.hits[" + position + "]._source.path.virtual");
        logger.trace(" - {}, {}", real, virtual);
        assertThat(real)
                .as("path.real[" + position + "]")
                .endsWith(expectedReal);
        assertThat(virtual)
                .as("path.virtual[" + position + "]")
                .endsWith(expectedVirtual);
    }
}
