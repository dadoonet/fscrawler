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

import fr.pilato.elasticsearch.crawler.fs.settings.Fs;
import org.elasticsearch.action.search.SearchRequest;
import org.junit.Test;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;

import static org.junit.Assume.assumeTrue;

/**
 * Test crawler with unparsable files
 */
public class FsCrawlerTestUnparsableIT extends AbstractFsCrawlerITCase {

    /**
     * Test for #105: https://github.com/dadoonet/fscrawler/issues/105
     */
    @Test
    public void test_unparsable() throws Exception {
        startCrawler();

        // We expect to have two files
        countTestHelper(new SearchRequest(getCrawlerName()), 2L, null);
    }

    /**
     * Test case for https://github.com/dadoonet/fscrawler/issues/362
     * @throws Exception In case something is wrong
     */
    @Test
    public void test_non_readable_file() throws Exception {
        // We change the attributes of the file
        logger.info(" ---> Changing attributes for file roottxtfile.txt");

        boolean isPosix =
                FileSystems.getDefault().supportedFileAttributeViews().contains("posix");

        assumeTrue("This test can only run on Posix systems", isPosix);

        Files.getFileAttributeView(currentTestResourceDir.resolve("roottxtfile.txt"), PosixFileAttributeView.class)
                .setPermissions(EnumSet.noneOf(PosixFilePermission.class));

        Fs fs = fsBuilder()
                .setIndexContent(false)
                .build();
        startCrawler(getCrawlerName(), fs, elasticsearchBuilder());

        // We should have one doc first
        countTestHelper(new SearchRequest(getCrawlerName()), 1L, currentTestResourceDir);
    }
}
