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

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.INDEX_SUFFIX_DOCS;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Test crawler behavior with different path formats on Windows.
 * These tests verify the fix for <a href="https://github.com/dadoonet/fscrawler/issues/2134">#2134</a>
 * where using Linux-style paths (forward slashes) on Windows caused a StringIndexOutOfBoundsException.
 */
public class FsCrawlerTestWindowsPathIT extends AbstractFsCrawlerITCase {

    /**
     * Test that using forward slashes in fs.url on Windows works correctly.
     * This test converts the path from Windows backslashes to forward slashes
     * to simulate a user configuring fs.url with "c:/Temp/dir" style paths.
     */
    @Test
    public void windows_path_with_forward_slashes() throws Exception {
        // This test is only relevant on Windows
        assumeThat(OsValidator.WINDOWS)
                .as("This test is only relevant on Windows")
                .isTrue();

        FsSettings fsSettings = createTestSettings();
        // Convert the path to use forward slashes (Linux-style) on Windows
        // This simulates the user setting fs.url: "c:/Temp/dir" in their config
        String pathWithForwardSlashes = currentTestResourceDir.toString().replace("\\", "/");
        fsSettings.getFs().setUrl(pathWithForwardSlashes);

        crawler = startCrawler(fsSettings);

        // We should have at least one document indexed
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName() + INDEX_SUFFIX_DOCS), 1L, null);
    }

    /**
     * Test that using backslashes in fs.url on Windows works correctly.
     * This is the recommended way to configure paths on Windows.
     */
    @Test
    public void windows_path_with_backslashes() throws Exception {
        // This test is only relevant on Windows
        assumeThat(OsValidator.WINDOWS)
                .as("This test is only relevant on Windows")
                .isTrue();

        FsSettings fsSettings = createTestSettings();
        // The default path should already use backslashes on Windows
        // but let's make sure by explicitly keeping them
        String pathWithBackslashes = currentTestResourceDir.toString();
        fsSettings.getFs().setUrl(pathWithBackslashes);

        crawler = startCrawler(fsSettings);

        // We should have at least one document indexed
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName() + INDEX_SUFFIX_DOCS), 1L, null);
    }
}
