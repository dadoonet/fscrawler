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

package fr.pilato.elasticsearch.crawler.fs.framework;

import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class FsMatchFilesTest extends AbstractFSCrawlerTestCase {

    @Test
    public void exclude_only() {
        assertThat(FsCrawlerUtil.isIndexable(false, "/test.doc", new ArrayList<>(), Collections.singletonList("*/*.doc"))).isFalse();
        assertThat(FsCrawlerUtil.isIndexable(false, "/test.xls", new ArrayList<>(), Collections.singletonList("*/*.doc"))).isTrue();
        assertThat(FsCrawlerUtil.isIndexable(false, "/my.doc.xls", new ArrayList<>(), Collections.singletonList("*/*.doc"))).isTrue();
        assertThat(FsCrawlerUtil.isIndexable(false, "/my.doc.xls", new ArrayList<>(), Arrays.asList("*/*.doc", "*/*.xls"))).isFalse();
        assertThat(FsCrawlerUtil.isIndexable(false, "/my.doc.xls", new ArrayList<>(), Collections.singletonList("*/my.d?c*.xls"))).isFalse();
        assertThat(FsCrawlerUtil.isIndexable(false, "/my.douc.xls", new ArrayList<>(), Collections.singletonList("*/my.d?c*.xls"))).isTrue();
        assertThat(FsCrawlerUtil.isIndexable(false, "/.snapshots", new ArrayList<>(), Collections.singletonList("*/.snapshots"))).isFalse();
        assertThat(FsCrawlerUtil.isIndexable(false, "/doc.doc", new ArrayList<>(), Arrays.asList("*/*.pdf", "*/*.xls", "*/*.doc"))).isFalse();
        assertThat(FsCrawlerUtil.isIndexable(false, "/doc.ppt", new ArrayList<>(), Arrays.asList("*/*.pdf", "*/*.xls", "*/*.doc"))).isTrue();
    }

    @Test
    public void include_only() {
        assertThat(FsCrawlerUtil.isIndexable(false, "/test.doc", Collections.singletonList("*/*.doc"), new ArrayList<>())).isTrue();
        assertThat(FsCrawlerUtil.isIndexable(false, "/test.xls", Collections.singletonList("*/*.doc"), new ArrayList<>())).isFalse();
        assertThat(FsCrawlerUtil.isIndexable(false, "/my.doc.xls", Collections.singletonList("*/*.doc"), new ArrayList<>())).isFalse();
        assertThat(FsCrawlerUtil.isIndexable(false, "/my.doc.xls", Collections.singletonList("*/my.d?c*.xls"), new ArrayList<>())).isTrue();
        assertThat(FsCrawlerUtil.isIndexable(false, "/my.douc.xls", Collections.singletonList("*/my.d?c*.xls"), new ArrayList<>())).isFalse();
        assertThat(FsCrawlerUtil.isIndexable(false, "/doc.doc", Arrays.asList("*/*.pdf", "*/*.xls", "*/*.doc"), new ArrayList<>())).isTrue();
        assertThat(FsCrawlerUtil.isIndexable(false, "/doc.ppt", Arrays.asList("*/*.pdf", "*/*.xls", "*/*.doc"), new ArrayList<>())).isFalse();
    }

    @Test
    public void include_exclude() {
        assertThat(FsCrawlerUtil.isIndexable(false, "/test.doc", Collections.singletonList("*/*.xls"), Collections.singletonList("*/*.doc"))).isFalse();
        assertThat(FsCrawlerUtil.isIndexable(false, "/test.xls", Collections.singletonList("*/*.xls"), Collections.singletonList("*/*.doc"))).isTrue();
        assertThat(FsCrawlerUtil.isIndexable(false, "/my.doc.xls", Collections.singletonList("*/*.xls"), Collections.singletonList("*/*.doc"))).isTrue();
        assertThat(FsCrawlerUtil.isIndexable(false, "/my.doc.xls", Collections.singletonList("*/*.xls"), Collections.singletonList("*/my.d?c*.xls"))).isFalse();
        assertThat(FsCrawlerUtil.isIndexable(false, "/my.douc.xls", Collections.singletonList("*/*.xls"), Collections.singletonList("*/my.d?c*.xls"))).isTrue();
    }

    @Test
    public void case_sensitive() {
        // Excludes
        assertThat(FsCrawlerUtil.isIndexable(false, "/test.doc", new ArrayList<>(), Collections.singletonList("*/*.DOC"))).isFalse();
        assertThat(FsCrawlerUtil.isIndexable(false, "/test.xls", new ArrayList<>(), Collections.singletonList("*/*.DOC"))).isTrue();
        assertThat(FsCrawlerUtil.isIndexable(false, "/my.doc.xls", new ArrayList<>(), Collections.singletonList("*/*.DOC"))).isTrue();
        assertThat(FsCrawlerUtil.isIndexable(false, "/my.doc.xls", new ArrayList<>(), Arrays.asList("*/*.DOC", "*/*.XLS"))).isFalse();
        assertThat(FsCrawlerUtil.isIndexable(false, "/my.doc.xls", new ArrayList<>(), Collections.singletonList("*/MY.D?C*.XLS"))).isFalse();
        assertThat(FsCrawlerUtil.isIndexable(false, "/my.douc.xls", new ArrayList<>(), Collections.singletonList("*/MY.d?C*.XLS"))).isTrue();
        assertThat(FsCrawlerUtil.isIndexable(false, "/.snapshots", new ArrayList<>(), Collections.singletonList("*/.SNAPSHOTS"))).isFalse();

        // Includes
        assertThat(FsCrawlerUtil.isIndexable(false, "/test.doc", Collections.singletonList("*/*.DOC"), new ArrayList<>())).isTrue();
        assertThat(FsCrawlerUtil.isIndexable(false, "/test.xls", Collections.singletonList("*/*.DOC"), new ArrayList<>())).isFalse();
        assertThat(FsCrawlerUtil.isIndexable(false, "/my.doc.xls", Collections.singletonList("*/*.DOC"), new ArrayList<>())).isFalse();
        assertThat(FsCrawlerUtil.isIndexable(false, "/my.doc.xls", Collections.singletonList("*/MY.D?C*.XLS"), new ArrayList<>())).isTrue();
        assertThat(FsCrawlerUtil.isIndexable(false, "/my.douc.xls", Collections.singletonList("*/MY.D?C*.XLS"), new ArrayList<>())).isFalse();
    }

    @Test
    public void directories() {
        assertThat(FsCrawlerUtil.isIndexable(true, "/folderA", new ArrayList<>(), Collections.singletonList("/folderB/subfolder*"))).isTrue();
        assertThat(FsCrawlerUtil.isIndexable(true, "/folderA/subfolderA", new ArrayList<>(), Collections.singletonList("/folderB/subfolder*"))).isTrue();
        assertThat(FsCrawlerUtil.isIndexable(true, "/folderA/subfolderB", new ArrayList<>(), Collections.singletonList("/folderB/subfolder*"))).isTrue();
        assertThat(FsCrawlerUtil.isIndexable(true, "/folderA/subfolderC", new ArrayList<>(), Collections.singletonList("/folderB/subfolder*"))).isTrue();
        assertThat(FsCrawlerUtil.isIndexable(true, "/folderB", new ArrayList<>(), Collections.singletonList("/folderB/subfolder*"))).isTrue();
        assertThat(FsCrawlerUtil.isIndexable(true, "/folderB/subfolderA", new ArrayList<>(), Collections.singletonList("/folderB/subfolder*"))).isFalse();
        assertThat(FsCrawlerUtil.isIndexable(true, "/folderB/subfolderB", new ArrayList<>(), Collections.singletonList("/folderB/subfolder*"))).isFalse();
        assertThat(FsCrawlerUtil.isIndexable(true, "/folderB/subfolderC", new ArrayList<>(), Collections.singletonList("/folderB/subfolder*"))).isFalse();
        assertThat(FsCrawlerUtil.isIndexable(true, "/folderC", new ArrayList<>(), Collections.singletonList("/folderB/subfolder*"))).isTrue();
        assertThat(FsCrawlerUtil.isIndexable(true, "/folderC/subfolderA", new ArrayList<>(), Collections.singletonList("/folderB/subfolder*"))).isTrue();
        assertThat(FsCrawlerUtil.isIndexable(true, "/folderC/subfolderB", new ArrayList<>(), Collections.singletonList("/folderB/subfolder*"))).isTrue();
        assertThat(FsCrawlerUtil.isIndexable(true, "/folderC/subfolderC", new ArrayList<>(), Collections.singletonList("/folderB/subfolder*"))).isTrue();
        assertThat(FsCrawlerUtil.isIndexable(true, "/subfolderA", new ArrayList<>(), Collections.singletonList("/folderB/subfolder*"))).isTrue();
        assertThat(FsCrawlerUtil.isIndexable(true, "/subfolderB", new ArrayList<>(), Collections.singletonList("/folderB/subfolder*"))).isTrue();
        assertThat(FsCrawlerUtil.isIndexable(true, "/subfolderC", new ArrayList<>(), Collections.singletonList("/folderB/subfolder*"))).isTrue();
        assertThat(FsCrawlerUtil.isIndexable(true, "/folderA", new ArrayList<>(), Collections.singletonList("/folderB/subfolderB/*"))).isTrue();
        assertThat(FsCrawlerUtil.isIndexable(true, "/folderA/subfolderA", new ArrayList<>(), Collections.singletonList("/folderB/subfolderB/*"))).isTrue();
        assertThat(FsCrawlerUtil.isIndexable(true, "/folderA/subfolderB", new ArrayList<>(), Collections.singletonList("/folderB/subfolderB/*"))).isTrue();
        assertThat(FsCrawlerUtil.isIndexable(true, "/folderA/subfolderC", new ArrayList<>(), Collections.singletonList("/folderB/subfolderB/*"))).isTrue();
        assertThat(FsCrawlerUtil.isIndexable(true, "/folderB", new ArrayList<>(), Collections.singletonList("/folderB/subfolderB/*"))).isTrue();
        assertThat(FsCrawlerUtil.isIndexable(true, "/folderB/subfolderA", new ArrayList<>(), Collections.singletonList("/folderB/subfolderB/*"))).isTrue();
        assertThat(FsCrawlerUtil.isIndexable(true, "/folderB/subfolderB", new ArrayList<>(), Collections.singletonList("/folderB/subfolderB/*"))).isFalse();
        assertThat(FsCrawlerUtil.isIndexable(true, "/folderB/subfolderC", new ArrayList<>(), Collections.singletonList("/folderB/subfolderB/*"))).isTrue();
        assertThat(FsCrawlerUtil.isIndexable(true, "/folderC", new ArrayList<>(), Collections.singletonList("/folderB/subfolderB/*"))).isTrue();
        assertThat(FsCrawlerUtil.isIndexable(true, "/folderC/subfolderA", new ArrayList<>(), Collections.singletonList("/folderB/subfolderB/*"))).isTrue();
        assertThat(FsCrawlerUtil.isIndexable(true, "/folderC/subfolderB", new ArrayList<>(), Collections.singletonList("/folderB/subfolderB/*"))).isTrue();
        assertThat(FsCrawlerUtil.isIndexable(true, "/folderC/subfolderC", new ArrayList<>(), Collections.singletonList("/folderB/subfolderB/*"))).isTrue();
        assertThat(FsCrawlerUtil.isIndexable(true, "/subfolderA", new ArrayList<>(), Collections.singletonList("/folderB/subfolderB/*"))).isTrue();
        assertThat(FsCrawlerUtil.isIndexable(true, "/subfolderB", new ArrayList<>(), Collections.singletonList("/folderB/subfolderB/*"))).isTrue();
        assertThat(FsCrawlerUtil.isIndexable(true, "/subfolderC", new ArrayList<>(), Collections.singletonList("/folderB/subfolderB/*"))).isTrue();
    }

    /**
     * Testing with short wildcards (word documents being edited)
     * See <a href="https://github.com/dadoonet/fscrawler/issues/1794>#1794</a>
     */
    @Test
    public void specialCharacters1794() {
        assertThat(FsCrawlerUtil.isIndexable(false, "/filter test/should-exclude.docx",
                Arrays.asList("*.pdf", "*.doc", "*.docx", "*.xsl", "*.xslx", "*.msg", "*.txt", "*.md"),
                Arrays.asList("*.exclude", "*~*"))).isTrue();
        assertThat(FsCrawlerUtil.isIndexable(false, "/filter test/~should-exclude.docx",
                Arrays.asList("*.pdf", "*.doc", "*.docx", "*.xsl", "*.xslx", "*.msg", "*.txt", "*.md"),
                Arrays.asList("*.exclude", "*~*"))).isFalse();
    }

    /**
     * Testing with short wildcards (word documents being edited)
     * See <a href="https://github.com/dadoonet/fscrawler/issues/1794>#1794</a>
     */
    @Test
    public void isMatching() {
        assertThat(FsCrawlerUtil.isMatching("/filter test/~should-exclude.docx", Collections.singletonList("*~*"), "exclusion")).isTrue();
        assertThat(FsCrawlerUtil.isMatching("/filter test/~should-exclude.docx", Collections.singletonList("*/~*"), "exclusion")).isTrue();
        assertThat(FsCrawlerUtil.isMatching("/filter test/should-not-exclude.docx", Collections.singletonList("*~*"), "exclusion")).isFalse();
        assertThat(FsCrawlerUtil.isMatching("/filter test/should-not-exclude.docx.exclude", Collections.singletonList("*.exclude"), "exclusion")).isTrue();
    }

    /**
     * Testing with windows separator
     * See <a href="https://github.com/dadoonet/fscrawler/issues/1974>#1974</a>
     */
    @Test
    public void windowsSeparator() {
        // We test with the Linux separator
        assertThat(FsCrawlerUtil.isIndexable(true, "/arbets", new ArrayList<>(), Collections.singletonList("*/arbets/*"))).isFalse();
        assertThat(FsCrawlerUtil.isIndexable(true, "/foo/arbets", new ArrayList<>(), Collections.singletonList("*/arbets/*"))).isFalse();
        assertThat(FsCrawlerUtil.isIndexable(true, "/foo", new ArrayList<>(), Collections.singletonList("*/arbets/*"))).isTrue();
        // We test with the Windows separator
        assertThat(FsCrawlerUtil.isIndexable(true, "\\arbets", new ArrayList<>(), Collections.singletonList("*/arbets/*"))).isFalse();
        assertThat(FsCrawlerUtil.isIndexable(true, "\\foo\\arbets", new ArrayList<>(), Collections.singletonList("*/arbets/*"))).isFalse();
        assertThat(FsCrawlerUtil.isIndexable(true, "\\foo", new ArrayList<>(), Collections.singletonList("*/arbets/*"))).isTrue();
    }
}
