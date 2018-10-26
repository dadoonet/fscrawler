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

import fr.pilato.elasticsearch.crawler.fs.beans.Doc;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchHit;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.client.ESTermQuery;
import fr.pilato.elasticsearch.crawler.fs.client.ESTermsAggregation;
import org.hamcrest.Matcher;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.carrotsearch.randomizedtesting.RandomizedTest.randomAsciiLettersOfLengthBetween;
import static com.carrotsearch.randomizedtesting.RandomizedTest.randomLongBetween;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.INDEX_SUFFIX_FOLDER;
import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.extractFromPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isOneOf;
import static org.hamcrest.Matchers.iterableWithSize;

/**
 * Test crawler with subdirs
 */
public class FsCrawlerTestSubDirsIT extends AbstractFsCrawlerITCase {

    @Test
    public void testSubdirs() throws Exception {
        startCrawler();

        // We expect to have two files
        ESSearchResponse searchResponse = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 2L, null);

        // We check that the subdir document has his meta path data correctly set
        for (ESSearchHit hit : searchResponse.getHits()) {
            Object virtual = extractFromPath(hit.getSourceAsMap(), Doc.FIELD_NAMES.PATH)
                    .get(fr.pilato.elasticsearch.crawler.fs.beans.Path.FIELD_NAMES.VIRTUAL);
            assertThat(virtual, isOneOf("/subdir/roottxtfile_multi_feed.txt", "/roottxtfile.txt"));
        }

        // Try to search within part of the full path, ie subdir
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()).withESQuery(new ESTermQuery("path.virtual.fulltext", "subdir")), 1L, null);
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()).withESQuery(new ESTermQuery("path.real.fulltext", "subdir")), 1L, null);
    }

    @Test
    public void testSubdirsDeepTree() throws Exception {
        startCrawler();

        // We expect to have 7 files
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 7L, null);

        // Run aggs
        ESSearchResponse response = esClient.search(new ESSearchRequest()
                        .withIndex(getCrawlerName())
                        .withSize(0)
                        .withAggregation(new ESTermsAggregation("folders", "path.virtual.tree")));
        assertThat(response.getTotalHits(), is(7L));

        // aggregations
        assertThat(response.getAggregations(), hasKey("folders"));
        ESTermsAggregation aggregation = response.getAggregations().get("folders");
        List<ESTermsAggregation.ESTermsBucket> buckets = aggregation.getBuckets();

        assertThat(buckets, iterableWithSize(10));

        // Check files
        response = esClient.search(new ESSearchRequest().withIndex(getCrawlerName()).withSort("path.virtual"));
        assertThat(response.getTotalHits(), is(7L));

        int i = 0;
        pathHitTester(response, i++, hit -> (Map<String, Object>) hit.getSourceAsMap().get("path"), "/test_subdirs_deep_tree/roottxtfile.txt", is("/roottxtfile.txt"));
        pathHitTester(response, i++, hit -> (Map<String, Object>) hit.getSourceAsMap().get("path"), "/test_subdirs_deep_tree/subdir1/roottxtfile_multi_feed.txt", is("/subdir1/roottxtfile_multi_feed.txt"));
        pathHitTester(response, i++, hit -> (Map<String, Object>) hit.getSourceAsMap().get("path"), "/test_subdirs_deep_tree/subdir1/subdir11/roottxtfile.txt", is("/subdir1/subdir11/roottxtfile.txt"));
        pathHitTester(response, i++, hit -> (Map<String, Object>) hit.getSourceAsMap().get("path"), "/test_subdirs_deep_tree/subdir1/subdir12/roottxtfile.txt", is("/subdir1/subdir12/roottxtfile.txt"));
        pathHitTester(response, i++, hit -> (Map<String, Object>) hit.getSourceAsMap().get("path"), "/test_subdirs_deep_tree/subdir2/roottxtfile_multi_feed.txt", is("/subdir2/roottxtfile_multi_feed.txt"));
        pathHitTester(response, i++, hit -> (Map<String, Object>) hit.getSourceAsMap().get("path"), "/test_subdirs_deep_tree/subdir2/subdir21/roottxtfile.txt", is("/subdir2/subdir21/roottxtfile.txt"));
        pathHitTester(response, i, hit -> (Map<String, Object>) hit.getSourceAsMap().get("path"), "/test_subdirs_deep_tree/subdir2/subdir22/roottxtfile.txt", is("/subdir2/subdir22/roottxtfile.txt"));


        // Check folders
        response = esClient.search(new ESSearchRequest().withIndex(getCrawlerName() + INDEX_SUFFIX_FOLDER).withSort("virtual"));
        assertThat(response.getTotalHits(), is(7L));

        i = 0;
        pathHitTester(response, i++, ESSearchHit::getSourceAsMap, "/test_subdirs_deep_tree", is("/"));
        pathHitTester(response, i++, ESSearchHit::getSourceAsMap, "/test_subdirs_deep_tree/subdir1", is("/subdir1"));
        pathHitTester(response, i++, ESSearchHit::getSourceAsMap, "/test_subdirs_deep_tree/subdir1/subdir11", is("/subdir1/subdir11"));
        pathHitTester(response, i++, ESSearchHit::getSourceAsMap, "/test_subdirs_deep_tree/subdir1/subdir12", is("/subdir1/subdir12"));
        pathHitTester(response, i++, ESSearchHit::getSourceAsMap, "/test_subdirs_deep_tree/subdir2", is("/subdir2"));
        pathHitTester(response, i++, ESSearchHit::getSourceAsMap, "/test_subdirs_deep_tree/subdir2/subdir21", is("/subdir2/subdir21"));
        pathHitTester(response, i, ESSearchHit::getSourceAsMap, "/test_subdirs_deep_tree/subdir2/subdir22", is("/subdir2/subdir22"));
    }

    @Test
    public void testSubdirsVeryDeepTree() throws Exception {

        long subdirs = randomLongBetween(30, 100);

        staticLogger.debug("  --> Generating [{}] dirs [{}]", subdirs, currentTestResourceDir);

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

        startCrawler();

        // We expect to have x files (<- whoa that's funny Mulder!)
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), subdirs+1, null);

        // Run aggs
        ESSearchResponse response = esClient.search(new ESSearchRequest()
                .withIndex(getCrawlerName())
                .withSize(0)
                .withAggregation(new ESTermsAggregation("folders", "path.virtual.tree")));
        assertThat(response.getTotalHits(), is(subdirs+1));

        // aggregations
        assertThat(response.getAggregations(), hasKey("folders"));
        ESTermsAggregation aggregation = response.getAggregations().get("folders");
        List<ESTermsAggregation.ESTermsBucket> buckets = aggregation.getBuckets();

        assertThat(buckets, iterableWithSize(10));

        // Check files
        response = esClient.search(new ESSearchRequest()
                .withIndex(getCrawlerName())
                .withSize(1000)
                .withSort("path.virtual"));
        assertThat(response.getTotalHits(), is(subdirs+1));

        for (int i = 0; i < subdirs; i++) {
            pathHitTester(response, i, hit -> (Map<String, Object>) hit.getSourceAsMap().get("path"), "sample.txt", endsWith("/" + "sample.txt"));
        }

        // Check folders
        response = esClient.search(new ESSearchRequest()
                .withIndex(getCrawlerName() + INDEX_SUFFIX_FOLDER)
                .withSize(1000)
                .withSort("virtual"));
        assertThat(response.getTotalHits(), is(subdirs+2));

        // Let's remove the main subdir and wait...
        staticLogger.debug("  --> Removing all dirs from [{}]", mainDir);
        deleteRecursively(mainDir);

        // We expect to have 1 doc now
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, currentTestResourceDir);
    }

    private void pathHitTester(ESSearchResponse response, int position, Function<ESSearchHit, Map<String, Object>> extractPath,
                               String expectedReal, Matcher<String> expectedVirtual) {
        ESSearchHit hit = response.getHits().get(position);
        Map<String, Object> path = extractPath.apply(hit);
        String real = (String) path.get("real");
        String virtual = (String) path.get("virtual");
        logger.debug(" - {}, {}", real, virtual);
        assertThat("path.real[" + position + "]", real, endsWith(expectedReal));
        assertThat("path.virtual[" + position + "]", virtual, expectedVirtual);
    }

}
