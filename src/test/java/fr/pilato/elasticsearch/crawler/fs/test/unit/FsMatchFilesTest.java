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

package fr.pilato.elasticsearch.crawler.fs.test.unit;


import fr.pilato.elasticsearch.crawler.fs.test.AbstractFSCrawlerTestCase;
import fr.pilato.elasticsearch.crawler.fs.util.FsCrawlerUtil;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class FsMatchFilesTest extends AbstractFSCrawlerTestCase {

    @Test
    public void exclude_only() {
        assertThat(FsCrawlerUtil.isIndexable("test.doc", new ArrayList<>(), Collections.singletonList("*.doc")), is(false));
        assertThat(FsCrawlerUtil.isIndexable("test.xls", new ArrayList<>(), Collections.singletonList("*.doc")), is(true));
        assertThat(FsCrawlerUtil.isIndexable("my.doc.xls", new ArrayList<>(), Collections.singletonList("*.doc")), is(true));
        assertThat(FsCrawlerUtil.isIndexable("my.doc.xls", new ArrayList<>(), Arrays.asList("*.doc", "*.xls")), is(false));
        assertThat(FsCrawlerUtil.isIndexable("my.doc.xls", new ArrayList<>(), Collections.singletonList("my.d?c*.xls")), is(false));
        assertThat(FsCrawlerUtil.isIndexable("my.douc.xls", new ArrayList<>(), Collections.singletonList("my.d?c*.xls")), is(true));
        assertThat(FsCrawlerUtil.isIndexable(".snapshots", new ArrayList<>(), Collections.singletonList(".snapshots")), is(false));
    }

    @Test
    public void include_only() {
        assertThat(FsCrawlerUtil.isIndexable("test.doc", Collections.singletonList("*.doc"), new ArrayList<>()), is(true));
        assertThat(FsCrawlerUtil.isIndexable("test.xls", Collections.singletonList("*.doc"), new ArrayList<>()), is(false));
        assertThat(FsCrawlerUtil.isIndexable("my.doc.xls", Collections.singletonList("*.doc"), new ArrayList<>()), is(false));
        assertThat(FsCrawlerUtil.isIndexable("my.doc.xls", Collections.singletonList("my.d?c*.xls"), new ArrayList<>()), is(true));
        assertThat(FsCrawlerUtil.isIndexable("my.douc.xls", Collections.singletonList("my.d?c*.xls"), new ArrayList<>()), is(false));
    }

    @Test
    public void include_exclude() {
        assertThat(FsCrawlerUtil.isIndexable("test.doc", Collections.singletonList("*.xls"), Collections.singletonList("*.doc")), is(false));
        assertThat(FsCrawlerUtil.isIndexable("test.xls", Collections.singletonList("*.xls"), Collections.singletonList("*.doc")), is(true));
        assertThat(FsCrawlerUtil.isIndexable("my.doc.xls", Collections.singletonList("*.xls"), Collections.singletonList("*.doc")), is(true));
        assertThat(FsCrawlerUtil.isIndexable("my.doc.xls", Collections.singletonList("*.xls"), Collections.singletonList("my.d?c*.xls")), is(false));
        assertThat(FsCrawlerUtil.isIndexable("my.douc.xls", Collections.singletonList("*.xls"), Collections.singletonList("my.d?c*.xls")), is(true));
    }

    @Test
    public void default_ignored_file() {
        assertThat(FsCrawlerUtil.isIndexable("~mydoc", new ArrayList<>(), new ArrayList<>()), is(false));
        assertThat(FsCrawlerUtil.isIndexable("~", new ArrayList<>(), new ArrayList<>()), is(false));
        assertThat(FsCrawlerUtil.isIndexable("adoc.doc", new ArrayList<>(), new ArrayList<>()), is(true));
    }
}
