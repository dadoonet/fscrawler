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


import fr.pilato.elasticsearch.crawler.fs.meta.settings.Fs;
import fr.pilato.elasticsearch.crawler.fs.test.AbstractFSCrawlerTestCase;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;

public class FsIncludesExcludesTest extends AbstractFSCrawlerTestCase {

    @Test
    public void include_with_duplicates() {
        Fs fs = Fs.builder()
                .addInclude("*.txt")
                .addInclude("*.txt")
                .addInclude("*.txt")
                .build();
        assertThat(fs.getIncludes(), hasSize(1));
    }

    @Test
    public void include_3_values() {
        Fs fs = Fs.builder()
                .addInclude("*.txt")
                .addInclude("*.doc")
                .addInclude("*.xls")
                .build();
        assertThat(fs.getIncludes(), hasSize(3));
    }

    @Test
    public void include_single_value() {
        Fs fs = Fs.builder()
                .addInclude("*.txt")
                .build();
        assertThat(fs.getIncludes(), hasSize(1));
    }

    @Test
    public void include_no_value() {
        Fs fs = Fs.builder().build();
        assertThat(fs.getIncludes(), nullValue());
    }

    @Test
    public void exclude_with_duplicates() {
        Fs fs = Fs.builder()
                .addExclude("*.txt")
                .addExclude("*.txt")
                .addExclude("*.txt")
                .build();
        assertThat(fs.getExcludes(), hasSize(1));
    }

    @Test
    public void exclude_3_values() {
        Fs fs = Fs.builder()
                .addExclude("*.txt")
                .addExclude("*.doc")
                .addExclude("*.xls")
                .build();
        assertThat(fs.getExcludes(), hasSize(3));
    }

    @Test
    public void exclude_single_value() {
        Fs fs = Fs.builder()
                .addExclude("*.txt")
                .build();
        assertThat(fs.getExcludes(), hasSize(1));
    }

    @Test
    public void exclude_no_value() {
        Fs fs = Fs.builder().build();
        assertThat(fs.getIncludes(), nullValue());
    }
}
