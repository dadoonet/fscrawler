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

package fr.pilato.elasticsearch.crawler.fs.settings;

import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.isIndexable;
import static fr.pilato.elasticsearch.crawler.fs.settings.Fs.DEFAULT_EXCLUDED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class FsMatchFilesTest extends AbstractFSCrawlerTestCase {

    @Test
    public void exclude_only() {
        assertThat(isIndexable(false, "/test.doc", new ArrayList<>(), Collections.singletonList("*/*.doc")), is(false));
        assertThat(isIndexable(false, "/test.xls", new ArrayList<>(), Collections.singletonList("*/*.doc")), is(true));
        assertThat(isIndexable(false, "/my.doc.xls", new ArrayList<>(), Collections.singletonList("*/*.doc")), is(true));
        assertThat(isIndexable(false, "/my.doc.xls", new ArrayList<>(), Arrays.asList("*/*.doc", "*/*.xls")), is(false));
        assertThat(isIndexable(false, "/my.doc.xls", new ArrayList<>(), Collections.singletonList("*/my.d?c*.xls")), is(false));
        assertThat(isIndexable(false, "/my.douc.xls", new ArrayList<>(), Collections.singletonList("*/my.d?c*.xls")), is(true));
        assertThat(isIndexable(false, "/.snapshots", new ArrayList<>(), Collections.singletonList("*/.snapshots")), is(false));
        assertThat(isIndexable(false, "/doc.doc", new ArrayList<>(), Arrays.asList("*/*.pdf", "*/*.xls", "*/*.doc")), is(false));
        assertThat(isIndexable(false, "/doc.ppt", new ArrayList<>(), Arrays.asList("*/*.pdf", "*/*.xls", "*/*.doc")), is(true));
    }

    @Test
    public void include_only() {
        assertThat(isIndexable(false, "/test.doc", Collections.singletonList("*/*.doc"), new ArrayList<>()), is(true));
        assertThat(isIndexable(false, "/test.xls", Collections.singletonList("*/*.doc"), new ArrayList<>()), is(false));
        assertThat(isIndexable(false, "/my.doc.xls", Collections.singletonList("*/*.doc"), new ArrayList<>()), is(false));
        assertThat(isIndexable(false, "/my.doc.xls", Collections.singletonList("*/my.d?c*.xls"), new ArrayList<>()), is(true));
        assertThat(isIndexable(false, "/my.douc.xls", Collections.singletonList("*/my.d?c*.xls"), new ArrayList<>()), is(false));
        assertThat(isIndexable(false, "/doc.doc", Arrays.asList("*/*.pdf", "*/*.xls", "*/*.doc"), new ArrayList<>()), is(true));
        assertThat(isIndexable(false, "/doc.ppt", Arrays.asList("*/*.pdf", "*/*.xls", "*/*.doc"), new ArrayList<>()), is(false));
    }

    @Test
    public void include_exclude() {
        assertThat(isIndexable(false, "/test.doc", Collections.singletonList("*/*.xls"), Collections.singletonList("*/*.doc")), is(false));
        assertThat(isIndexable(false, "/test.xls", Collections.singletonList("*/*.xls"), Collections.singletonList("*/*.doc")), is(true));
        assertThat(isIndexable(false, "/my.doc.xls", Collections.singletonList("*/*.xls"), Collections.singletonList("*/*.doc")), is(true));
        assertThat(isIndexable(false, "/my.doc.xls", Collections.singletonList("*/*.xls"), Collections.singletonList("*/my.d?c*.xls")), is(false));
        assertThat(isIndexable(false, "/my.douc.xls", Collections.singletonList("*/*.xls"), Collections.singletonList("*/my.d?c*.xls")), is(true));
    }

    @Test
    public void default_ignored_file() {
        assertThat(isIndexable(false, "/~mydoc", new ArrayList<>(), DEFAULT_EXCLUDED), is(false));
        assertThat(isIndexable(false, "/~", new ArrayList<>(), DEFAULT_EXCLUDED), is(false));
        assertThat(isIndexable(false, "/adoc.doc", new ArrayList<>(), DEFAULT_EXCLUDED), is(true));
        assertThat(isIndexable(false, "/mydoc~", new ArrayList<>(), DEFAULT_EXCLUDED), is(true));
    }

    @Test
    public void case_sensitive() {
        // Excludes
        assertThat(isIndexable(false, "/test.doc", new ArrayList<>(), Collections.singletonList("*/*.DOC")), is(false));
        assertThat(isIndexable(false, "/test.xls", new ArrayList<>(), Collections.singletonList("*/*.DOC")), is(true));
        assertThat(isIndexable(false, "/my.doc.xls", new ArrayList<>(), Collections.singletonList("*/*.DOC")), is(true));
        assertThat(isIndexable(false, "/my.doc.xls", new ArrayList<>(), Arrays.asList("*/*.DOC", "*/*.XLS")), is(false));
        assertThat(isIndexable(false, "/my.doc.xls", new ArrayList<>(), Collections.singletonList("*/MY.D?C*.XLS")), is(false));
        assertThat(isIndexable(false, "/my.douc.xls", new ArrayList<>(), Collections.singletonList("*/MY.d?C*.XLS")), is(true));
        assertThat(isIndexable(false, "/.snapshots", new ArrayList<>(), Collections.singletonList("*/.SNAPSHOTS")), is(false));

        // Includes
        assertThat(isIndexable(false, "/test.doc", Collections.singletonList("*/*.DOC"), new ArrayList<>()), is(true));
        assertThat(isIndexable(false, "/test.xls", Collections.singletonList("*/*.DOC"), new ArrayList<>()), is(false));
        assertThat(isIndexable(false, "/my.doc.xls", Collections.singletonList("*/*.DOC"), new ArrayList<>()), is(false));
        assertThat(isIndexable(false, "/my.doc.xls", Collections.singletonList("*/MY.D?C*.XLS"), new ArrayList<>()), is(true));
        assertThat(isIndexable(false, "/my.douc.xls", Collections.singletonList("*/MY.D?C*.XLS"), new ArrayList<>()), is(false));
    }
    
    @Test
    public void directories() {
        assertThat(isIndexable(true, "/folderA", new ArrayList<>(), Collections.singletonList("/folderB/subfolder*")), is(true));
        assertThat(isIndexable(true, "/folderA/subfolderA", new ArrayList<>(), Collections.singletonList("/folderB/subfolder*")), is(true));
        assertThat(isIndexable(true, "/folderA/subfolderB", new ArrayList<>(), Collections.singletonList("/folderB/subfolder*")), is(true));
        assertThat(isIndexable(true, "/folderA/subfolderC", new ArrayList<>(), Collections.singletonList("/folderB/subfolder*")), is(true));
        assertThat(isIndexable(true, "/folderB", new ArrayList<>(), Collections.singletonList("/folderB/subfolder*")), is(true));
        assertThat(isIndexable(true, "/folderB/subfolderA", new ArrayList<>(), Collections.singletonList("/folderB/subfolder*")), is(false));
        assertThat(isIndexable(true, "/folderB/subfolderB", new ArrayList<>(), Collections.singletonList("/folderB/subfolder*")), is(false));
        assertThat(isIndexable(true, "/folderB/subfolderC", new ArrayList<>(), Collections.singletonList("/folderB/subfolder*")), is(false));
        assertThat(isIndexable(true, "/folderC", new ArrayList<>(), Collections.singletonList("/folderB/subfolder*")), is(true));
        assertThat(isIndexable(true, "/folderC/subfolderA", new ArrayList<>(), Collections.singletonList("/folderB/subfolder*")), is(true));
        assertThat(isIndexable(true, "/folderC/subfolderB", new ArrayList<>(), Collections.singletonList("/folderB/subfolder*")), is(true));
        assertThat(isIndexable(true, "/folderC/subfolderC", new ArrayList<>(), Collections.singletonList("/folderB/subfolder*")), is(true));
        assertThat(isIndexable(true, "/subfolderA", new ArrayList<>(), Collections.singletonList("/folderB/subfolder*")), is(true));
        assertThat(isIndexable(true, "/subfolderB", new ArrayList<>(), Collections.singletonList("/folderB/subfolder*")), is(true));
        assertThat(isIndexable(true, "/subfolderC", new ArrayList<>(), Collections.singletonList("/folderB/subfolder*")), is(true));
        assertThat(isIndexable(true, "/folderA", new ArrayList<>(), Collections.singletonList("/folderB/subfolderB")), is(true));
        assertThat(isIndexable(true, "/folderA/subfolderA", new ArrayList<>(), Collections.singletonList("/folderB/subfolderB")), is(true));
        assertThat(isIndexable(true, "/folderA/subfolderB", new ArrayList<>(), Collections.singletonList("/folderB/subfolderB")), is(true));
        assertThat(isIndexable(true, "/folderA/subfolderC", new ArrayList<>(), Collections.singletonList("/folderB/subfolderB")), is(true));
        assertThat(isIndexable(true, "/folderB", new ArrayList<>(), Collections.singletonList("/folderB/subfolderB")), is(true));
        assertThat(isIndexable(true, "/folderB/subfolderA", new ArrayList<>(), Collections.singletonList("/folderB/subfolderB")), is(true));
        assertThat(isIndexable(true, "/folderB/subfolderB", new ArrayList<>(), Collections.singletonList("/folderB/subfolderB")), is(false));
        assertThat(isIndexable(true, "/folderB/subfolderC", new ArrayList<>(), Collections.singletonList("/folderB/subfolderB")), is(true));
        assertThat(isIndexable(true, "/folderC", new ArrayList<>(), Collections.singletonList("/folderB/subfolderB")), is(true));
        assertThat(isIndexable(true, "/folderC/subfolderA", new ArrayList<>(), Collections.singletonList("/folderB/subfolderB")), is(true));
        assertThat(isIndexable(true, "/folderC/subfolderB", new ArrayList<>(), Collections.singletonList("/folderB/subfolderB")), is(true));
        assertThat(isIndexable(true, "/folderC/subfolderC", new ArrayList<>(), Collections.singletonList("/folderB/subfolderB")), is(true));
        assertThat(isIndexable(true, "/subfolderA", new ArrayList<>(), Collections.singletonList("/folderB/subfolderB")), is(true));
        assertThat(isIndexable(true, "/subfolderB", new ArrayList<>(), Collections.singletonList("/folderB/subfolderB")), is(true));
        assertThat(isIndexable(true, "/subfolderC", new ArrayList<>(), Collections.singletonList("/folderB/subfolderB")), is(true));
    }
}
