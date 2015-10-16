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

package fr.pilato.elasticsearch.crawler.unit;


import fr.pilato.elasticsearch.crawler.fs.util.FsCrawlerUtil;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class FsMatchFilesTest {

    @Test
    public void exclude_only() throws Exception {
        assertThat(FsCrawlerUtil.isIndexable("test.doc", new ArrayList<>(), Arrays.asList("*.doc")), is(false));
        assertThat(FsCrawlerUtil.isIndexable("test.xls", new ArrayList<>(), Arrays.asList("*.doc")), is(true));
        assertThat(FsCrawlerUtil.isIndexable("my.doc.xls", new ArrayList<>(), Arrays.asList("*.doc")), is(true));
        assertThat(FsCrawlerUtil.isIndexable("my.doc.xls", new ArrayList<>(), Arrays.asList("*.doc", "*.xls")), is(false));
        assertThat(FsCrawlerUtil.isIndexable("my.doc.xls", new ArrayList<>(), Arrays.asList("my.d?c*.xls")), is(false));
        assertThat(FsCrawlerUtil.isIndexable("my.douc.xls", new ArrayList<>(), Arrays.asList("my.d?c*.xls")), is(true));
    }

    @Test
    public void include_only() throws Exception {
        assertThat(FsCrawlerUtil.isIndexable("test.doc", Arrays.asList("*.doc"), new ArrayList<>()), is(true));
        assertThat(FsCrawlerUtil.isIndexable("test.xls", Arrays.asList("*.doc"), new ArrayList<>()), is(false));
        assertThat(FsCrawlerUtil.isIndexable("my.doc.xls", Arrays.asList("*.doc"), new ArrayList<>()), is(false));
        assertThat(FsCrawlerUtil.isIndexable("my.doc.xls", Arrays.asList("my.d?c*.xls"), new ArrayList<>()), is(true));
        assertThat(FsCrawlerUtil.isIndexable("my.douc.xls", Arrays.asList("my.d?c*.xls"), new ArrayList<>()), is(false));
    }

    @Test
    public void include_exclude() throws Exception {
        assertThat(FsCrawlerUtil.isIndexable("test.doc", Arrays.asList("*.xls"), Arrays.asList("*.doc")), is(false));
        assertThat(FsCrawlerUtil.isIndexable("test.xls", Arrays.asList("*.xls"), Arrays.asList("*.doc")), is(true));
        assertThat(FsCrawlerUtil.isIndexable("my.doc.xls", Arrays.asList("*.xls"), Arrays.asList("*.doc")), is(true));
        assertThat(FsCrawlerUtil.isIndexable("my.doc.xls", Arrays.asList("*.xls"), Arrays.asList("my.d?c*.xls")), is(false));
        assertThat(FsCrawlerUtil.isIndexable("my.douc.xls", Arrays.asList("*.xls"), Arrays.asList("my.d?c*.xls")), is(true));
    }

}
