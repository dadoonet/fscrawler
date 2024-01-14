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

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.isIndexable;
import static fr.pilato.elasticsearch.crawler.fs.settings.Fs.DEFAULT_EXCLUDED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class FsMatchFilesTest extends AbstractFSCrawlerTestCase {
    @Test
    public void default_ignored_file() {
        assertThat(isIndexable(false, "/~mydoc", new ArrayList<>(), DEFAULT_EXCLUDED), is(false));
        assertThat(isIndexable(false, "/~", new ArrayList<>(), DEFAULT_EXCLUDED), is(false));
        assertThat(isIndexable(false, "/adoc.doc", new ArrayList<>(), DEFAULT_EXCLUDED), is(true));
        assertThat(isIndexable(false, "/mydoc~", new ArrayList<>(), DEFAULT_EXCLUDED), is(true));
    }
}
