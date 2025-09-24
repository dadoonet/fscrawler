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

import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.framework.OsValidator;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.carrotsearch.randomizedtesting.RandomizedTest.assumeFalse;

/**
 * Test with symlinks
 */
public class FsCrawlerTestFollowSymlinksIT extends AbstractFsCrawlerITCase {

    @Test
    public void follow_symlinks_disabled() throws Exception {
        // We create a symlink (but not on Windows)
       assumeFalse("We cannot create symlink on Windows OS", OsValidator.WINDOWS);
        Path source = currentTestResourceDir.resolve("roottxtfile.txt");
        Path link = currentTestResourceDir.resolve("link_roottxtfile.txt");
        Files.createSymbolicLink(link, source);

        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setFollowSymlinks(false);
        crawler = startCrawler(fsSettings);

        // We should have two docs first
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, currentTestResourceDir);
    }

    @Test
    public void follow_symlinks_enabled() throws Exception {
        // We create a symlink (but not on Windows)
        assumeFalse("We cannot create symlink on Windows OS", OsValidator.WINDOWS);
        Path source = currentTestResourceDir.resolve("roottxtfile.txt");
        Path link = currentTestResourceDir.resolve("link_roottxtfile.txt");
        Files.createSymbolicLink(link, source);

        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setFollowSymlinks(true);
        crawler = startCrawler(fsSettings);

        // We should have two docs first
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 2L, currentTestResourceDir);
    }
}
