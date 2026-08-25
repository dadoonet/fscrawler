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
package fr.pilato.elasticsearch.crawler.fs.settings;

import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import java.util.ArrayList;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class FsMatchFilesTest extends AbstractFSCrawlerTestCase {
    @Test
    void default_ignored_file() {
        Assertions.assertThat(FsCrawlerUtil.isIndexable(false, "/~mydoc", new ArrayList<>(), Defaults.DEFAULT_EXCLUDED))
                .isFalse();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(false, "/~", new ArrayList<>(), Defaults.DEFAULT_EXCLUDED))
                .isFalse();
        Assertions.assertThat(
                        FsCrawlerUtil.isIndexable(false, "/adoc.doc", new ArrayList<>(), Defaults.DEFAULT_EXCLUDED))
                .isTrue();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(false, "/mydoc~", new ArrayList<>(), Defaults.DEFAULT_EXCLUDED))
                .isTrue();

        // macOS Finder metadata — matching is case-insensitive, pattern is lowercase
        Assertions.assertThat(Defaults.DEFAULT_EXCLUDED).contains("*/.ds_store");
        Assertions.assertThat(
                        FsCrawlerUtil.isIndexable(false, "/.DS_Store", new ArrayList<>(), Defaults.DEFAULT_EXCLUDED))
                .isFalse();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        false, "/subdir/.DS_Store", new ArrayList<>(), Defaults.DEFAULT_EXCLUDED))
                .isFalse();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        false, "/subdir/.ds_store", new ArrayList<>(), Defaults.DEFAULT_EXCLUDED))
                .isFalse();
        Assertions.assertThat(
                        FsCrawlerUtil.isIndexable(false, "/DS_Store", new ArrayList<>(), Defaults.DEFAULT_EXCLUDED))
                .isTrue();
    }
}
