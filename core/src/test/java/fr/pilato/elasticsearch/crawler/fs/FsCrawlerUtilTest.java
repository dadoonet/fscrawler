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

package fr.pilato.elasticsearch.crawler.fs;

import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerMetadataTestCase;
import org.junit.Test;

import java.io.File;
import java.time.LocalDateTime;
import java.util.TimeZone;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.computeVirtualPathName;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.getFileExtension;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.localDateTimeToDate;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * We want to test some utilities
 */
public class FsCrawlerUtilTest extends AbstractFSCrawlerMetadataTestCase {

    @Test
    public void testComputePathLinux() {
        testHelper("/tmp", "/tmp", "/");
        testHelper("/tmp", "/tmp/dir", "/dir");
        testHelper("/tmp", "/tmp/dir/subdir", "/dir/subdir");
        testHelper("/tmp", "/tmp/file.txt", "/file.txt");
        testHelper("/tmp", "/tmp/dir/file.txt", "/dir/file.txt");
        testHelper("/tmp", "/tmp/dir/subdir/file.txt", "/dir/subdir/file.txt");
    }

    @Test
    public void testComputePathWindows() {
        testHelper("C:\\tmp", "C:\\tmp", "/");
        testHelper("C:\\tmp", "C:\\tmp\\dir", "/dir");
        testHelper("C:\\tmp", "C:\\tmp\\dir\\subdir", "/dir/subdir");
        testHelper("C:\\tmp", "C:\\tmp\\file.txt", "/file.txt");
        testHelper("C:\\tmp", "C:\\tmp\\dir\\file.txt", "/dir/file.txt");
        testHelper("C:\\tmp", "C:\\tmp\\dir\\subdir\\file.txt", "/dir/subdir/file.txt");
    }

    private void testHelper(String rootPath, String realPath, String expectedPath) {
        assertThat(computeVirtualPathName(rootPath, realPath), is(expectedPath));
    }

    @Test
    public void testGetFileExtension() {
        assertThat(getFileExtension(new File("foo.bar")), is("bar"));
        assertThat(getFileExtension(new File("foo")), is(""));
        assertThat(getFileExtension(new File("foo.bar.baz")), is("baz"));
    }

    @Test
    public void testLocalDateToDate() {
        LocalDateTime now = LocalDateTime.now();
        logger.info("Current Time [{}] in [{}] is actually [{}]", now, TimeZone.getDefault().getDisplayName(), localDateTimeToDate(now));
    }
}
