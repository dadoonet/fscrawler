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

import fr.pilato.elasticsearch.crawler.fs.cli.FsCrawler;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsFileHandler;
import org.elasticsearch.action.search.SearchRequest;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.carrotsearch.randomizedtesting.RandomizedTest.randomAsciiLettersOfLengthBetween;
import static com.carrotsearch.randomizedtesting.RandomizedTest.randomLongBetween;

/**
 * We want to test FSCrawler main app
 */
public class FsCrawlerCliIT extends AbstractFsCrawlerITCase {

    @Test
    public void testOneLoop() throws Exception {
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

        String json = "{\n" +
                "  \"name\" : \"fscrawler_fs_crawler_cli_i_t_test_one_loop\",\n" +
                "  \"fs\" : {\n" +
                "    \"url\" : \"[URL]\"\n" +
                "  },\n" +
                "  \"elasticsearch\" : {\n" +
                "    \"nodes\" : [ {\n" +
                "      \"host\" : \"[HOST]\",\n" +
                "      \"port\" : [PORT],\n" +
                "      \"scheme\" : \"[SCHEME]\"\n" +
                "    } ],\n" +
                "    \"username\" : \"[USERNAME]\",\n" +
                "    \"password\" : \"[PASSWORD]\"\n" +
                "  }\n" +
                "}";

        json = json
                .replace("[URL]", currentTestResourceDir.toString())
                .replace("[HOST]", testClusterHost)
                .replace("[PORT]", "" + testClusterPort)
                .replace("[SCHEME]", testClusterScheme.name())
                .replace("[USERNAME]", testClusterUser)
                .replace("[PASSWORD]", testClusterPass);

        new FsSettingsFileHandler(metadataDir).write("fscrawler_fs_crawler_cli_i_t_test_one_loop", json);

        String[] args = { "--config_dir", metadataDir.toString(), "--loop", "1", getCrawlerName() };
        FsCrawler.main(args);
        FsCrawler.getFsCrawler().close();

        // We expect to have x files (<- whoa that's funny Mulder!)
        countTestHelper(new SearchRequest(getCrawlerName()), subdirs+1, null);
    }
}
