/*
 * Licensed to David Pilato under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

import static com.carrotsearch.randomizedtesting.RandomizedTest.randomIntBetween;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.extractMajorVersion;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.extractMinorVersion;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.getFilePermissions;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.getGroupName;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.getOwnerName;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.isFileSizeUnderLimit;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assume.assumeFalse;

public class FsCrawlerUtilTest extends AbstractFSCrawlerTestCase {

    private static File file;

    @BeforeClass
    public static void createTmpFile() throws IOException {
        Path path = rootTmpDir.resolve("test-group.txt");
        Set<PosixFilePermission> permissions = PosixFilePermissions.fromString("rwx------");
        FileAttribute<Set<PosixFilePermission>> fileAttributes = PosixFilePermissions.asFileAttribute(permissions);
        Files.createFile(path, fileAttributes);
        file = path.toFile();
    }

    @Test
    public void testOwnerName() {
        String ownerName = getOwnerName(file);
        assertThat(ownerName, not(isEmptyOrNullString()));
    }

    @Test
    public void testGroups() {
        assumeFalse("This test can not run on Windows.", OsValidator.WINDOWS);
        String groupName = getGroupName(file);
        assertThat(groupName, not(isEmptyOrNullString()));
    }

    @Test
    public void testPermissions() {
        assumeFalse("This test can not run on Windows.", OsValidator.WINDOWS);
        int permissions = getFilePermissions(file);
        assertThat(permissions, is(700));
    }

    @Test
    public void testIsFileSizeUnderLimit() {
        assertThat(isFileSizeUnderLimit(ByteSizeValue.parseBytesSizeValue("1mb"), 1), is(true));
        assertThat(isFileSizeUnderLimit(ByteSizeValue.parseBytesSizeValue("1mb"), 1048576), is(true));
        assertThat(isFileSizeUnderLimit(ByteSizeValue.parseBytesSizeValue("1mb"),
                new ByteSizeValue(randomIntBetween(2, 100), ByteSizeUnit.MB).getBytes()), is(false));
    }

    @Test
    public void testExtractMajorVersion() {
        assertThat(extractMajorVersion("7.2.0"), is("7"));
        assertThat(extractMajorVersion("10.1.0"), is("10"));
    }

    @Test
    public void testExtractMinorVersion() {
        assertThat(extractMinorVersion("7.2.0"), is("2"));
        assertThat(extractMinorVersion("10.1.0"), is("1"));
    }
}
