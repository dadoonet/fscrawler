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
import java.time.LocalDateTime;
import java.util.Date;
import java.util.TimeZone;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeFalse;

public class FsCrawlerUtilTest extends AbstractFSCrawlerTestCase {
    private static final Logger logger = LogManager.getLogger();
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
    public void ownerName() {
        String ownerName = FsCrawlerUtil.getOwnerName(file);
        assertThat(ownerName).isNotEmpty();
    }

    @Test
    public void groups() {
        assumeFalse("This test can not run on Windows.", OsValidator.WINDOWS);
        String groupName = FsCrawlerUtil.getGroupName(file);
        assertThat(groupName).isNotEmpty();
    }

    @Test
    public void permissions() {
        assumeFalse("This test can not run on Windows.", OsValidator.WINDOWS);
        int permissions = FsCrawlerUtil.getFilePermissions(file);
        assertThat(permissions).isEqualTo(700);
    }

    @Test
    public void isFileSizeUnderLimit() {
        assertThat(FsCrawlerUtil.isFileSizeUnderLimit(ByteSizeValue.parseBytesSizeValue("1mb"), 1)).isTrue();
        assertThat(FsCrawlerUtil.isFileSizeUnderLimit(ByteSizeValue.parseBytesSizeValue("1mb"), 1048576)).isTrue();
        assertThat(FsCrawlerUtil.isFileSizeUnderLimit(ByteSizeValue.parseBytesSizeValue("1mb"),
                new ByteSizeValue(randomIntBetween(2, 100), ByteSizeUnit.MB).getBytes())).isFalse();
    }

    @Test
    public void extractMajorVersion() {
        assertThat(FsCrawlerUtil.extractMajorVersion("7.2.0")).isEqualTo(7);
        assertThat(FsCrawlerUtil.extractMajorVersion("8.17.1")).isEqualTo(8);
        assertThat(FsCrawlerUtil.extractMajorVersion("10.1.0")).isEqualTo(10);
    }

    @Test
    public void extractMinorVersion() {
        assertThat(FsCrawlerUtil.extractMinorVersion("7.2.0")).isEqualTo(2);
        assertThat(FsCrawlerUtil.extractMinorVersion("8.17.1")).isEqualTo(17);
        assertThat(FsCrawlerUtil.extractMinorVersion("10.1.0")).isEqualTo(1);
    }

    @Test
    public void getRealPathNameWindows() {
        testRealPath("/C:", "test-windows.txt", "/C:/test-windows.txt");
        testRealPath("/C:/", "test-windows.txt", "/C:/test-windows.txt");
        testRealPath("/C:/dir", "test-windows.txt", "/C:/dir/test-windows.txt");
        testRealPath("/C:/dir/", "test-windows.txt", "/C:/dir/test-windows.txt");

        testRealPath("C:/", "test-windows.txt", "C:/test-windows.txt");
        testRealPath("C:/dir", "test-windows.txt", "C:/dir/test-windows.txt");
        testRealPath("C:/dir/", "test-windows.txt", "C:/dir/test-windows.txt");

        testRealPath("C:", "test-windows.txt", "C:\\test-windows.txt");
        testRealPath("C:\\", "test-windows.txt", "C:\\test-windows.txt");
        testRealPath("C:\\dir", "test-windows.txt", "C:\\dir\\test-windows.txt");
        testRealPath("C:\\dir\\", "test-windows.txt", "C:\\dir\\test-windows.txt");

        testRealPath("\\\\SOMEONE", "test-smb.txt", "\\\\SOMEONE\\test-smb.txt");
        testRealPath("\\\\SOMEONE\\", "test-smb.txt", "\\\\SOMEONE\\test-smb.txt");
        testRealPath("\\\\SOMEONE\\share", "test-smb.txt", "\\\\SOMEONE\\share\\test-smb.txt");
        testRealPath("\\\\SOMEONE\\share\\", "test-smb.txt", "\\\\SOMEONE\\share\\test-smb.txt");
    }

    @Test
    public void getRealPathNameLinux() {
        // Local Linux / FTP
        testRealPath("/", "test-linux.txt", "/test-linux.txt");
        testRealPath("/dir", "test-linux.txt", "/dir/test-linux.txt");
        testRealPath("/dir/", "test-linux.txt", "/dir/test-linux.txt");

        // SMB
        testRealPath("//SOMEONE", "test-smb.txt", "//SOMEONE/test-smb.txt");
        testRealPath("//SOMEONE/", "test-smb.txt", "//SOMEONE/test-smb.txt");
        testRealPath("//SOMEONE/share", "test-smb.txt", "//SOMEONE/share/test-smb.txt");
        testRealPath("//SOMEONE/share/", "test-smb.txt", "//SOMEONE/share/test-smb.txt");
    }

    private void testRealPath(String dirname, String filename, String expectedPath) {
        assertThat(FsCrawlerUtil.computeRealPathName(dirname, filename)).isEqualTo(expectedPath);
    }

    @Test
    public void computePathLinux() {
        // Local Linux / FTP
        testVirtualPath("/", "/", "/");
        testVirtualPath("/", "/dir", "/dir");
        testVirtualPath("/", "/dir/subdir", "/dir/subdir");
        testVirtualPath("/", "/file.txt", "/file.txt");
        testVirtualPath("/", "/dir/file.txt", "/dir/file.txt");
        testVirtualPath("/", "/dir/subdir/file.txt", "/dir/subdir/file.txt");

        testVirtualPath("/tmp", "/tmp", "/");
        testVirtualPath("/tmp", "/tmp/dir", "/dir");
        testVirtualPath("/tmp", "/tmp/dir/subdir", "/dir/subdir");
        testVirtualPath("/tmp", "/tmp/file.txt", "/file.txt");
        testVirtualPath("/tmp", "/tmp/dir/file.txt", "/dir/file.txt");
        testVirtualPath("/tmp", "/tmp/dir/subdir/file.txt", "/dir/subdir/file.txt");

        // SMB
        testVirtualPath("//SOMEONE/share", "//SOMEONE/share", "/");
        testVirtualPath("//SOMEONE/share", "//SOMEONE/share/dir", "/dir");
        testVirtualPath("//SOMEONE/share", "//SOMEONE/share/dir/subdir", "/dir/subdir");
        testVirtualPath("//SOMEONE/share", "//SOMEONE/share/file.txt", "/file.txt");
        testVirtualPath("//SOMEONE/share", "//SOMEONE/share/dir/file.txt", "/dir/file.txt");
        testVirtualPath("//SOMEONE/share", "//SOMEONE/share/dir/subdir/file.txt", "/dir/subdir/file.txt");
    }

    @Test
    public void computePathWindows() {
        testVirtualPath("C:", "C:", "\\");
        testVirtualPath("C:", "C:\\dir", "\\dir");
        testVirtualPath("C:", "C:\\dir\\subdir", "\\dir\\subdir");
        testVirtualPath("C:", "C:\\file.txt", "\\file.txt");
        testVirtualPath("C:", "C:\\dir\\file.txt", "\\dir\\file.txt");
        testVirtualPath("C:", "C:\\dir\\subdir\\file.txt", "\\dir\\subdir\\file.txt");

        testVirtualPath("C:\\tmp", "C:\\tmp", "\\");
        testVirtualPath("C:\\tmp", "C:\\tmp\\dir", "\\dir");
        testVirtualPath("C:\\tmp", "C:\\tmp\\dir\\subdir", "\\dir\\subdir");
        testVirtualPath("C:\\tmp", "C:\\tmp\\file.txt", "\\file.txt");
        testVirtualPath("C:\\tmp", "C:\\tmp\\dir\\file.txt", "\\dir\\file.txt");
        testVirtualPath("C:\\tmp", "C:\\tmp\\dir\\subdir\\file.txt", "\\dir\\subdir\\file.txt");

        testVirtualPath("C:/tmp", "C:/tmp", "/");
        testVirtualPath("C:/tmp", "C:/tmp/dir", "/dir");
        testVirtualPath("C:/tmp", "C:/tmp/dir/subdir", "/dir/subdir");
        testVirtualPath("C:/tmp", "C:/tmp/file.txt", "/file.txt");
        testVirtualPath("C:/tmp", "C:/tmp/dir/file.txt", "/dir/file.txt");
        testVirtualPath("C:/tmp", "C:/tmp/dir/subdir/file.txt", "/dir/subdir/file.txt");

        testVirtualPath("/C:/tmp", "/C:/tmp", "/");
        testVirtualPath("/C:/tmp", "/C:/tmp/dir", "/dir");
        testVirtualPath("/C:/tmp", "/C:/tmp/dir/subdir", "/dir/subdir");
        testVirtualPath("/C:/tmp", "/C:/tmp/file.txt", "/file.txt");
        testVirtualPath("/C:/tmp", "/C:/tmp/dir/file.txt", "/dir/file.txt");
        testVirtualPath("/C:/tmp", "/C:/tmp/dir/subdir/file.txt", "/dir/subdir/file.txt");

        testVirtualPath("\\\\SOMEONE\\share", "\\\\SOMEONE\\share", "\\");
        testVirtualPath("\\\\SOMEONE\\share", "\\\\SOMEONE\\share\\dir", "\\dir");
        testVirtualPath("\\\\SOMEONE\\share", "\\\\SOMEONE\\share\\dir\\subdir", "\\dir\\subdir");
        testVirtualPath("\\\\SOMEONE\\share", "\\\\SOMEONE\\share\\file.txt", "\\file.txt");
        testVirtualPath("\\\\SOMEONE\\share", "\\\\SOMEONE\\share\\dir\\file.txt", "\\dir\\file.txt");
        testVirtualPath("\\\\SOMEONE\\share", "\\\\SOMEONE\\share\\dir\\subdir\\file.txt", "\\dir\\subdir\\file.txt");
    }

    private void testVirtualPath(String rootPath, String realPath, String expectedPath) {
        assertThat(FsCrawlerUtil.computeVirtualPathName(rootPath, realPath)).isEqualTo(expectedPath);
    }

    @Test
    public void getFileExtension() {
        assertThat(FsCrawlerUtil.getFileExtension(new File("foo.bar"))).isEqualTo("bar");
        assertThat(FsCrawlerUtil.getFileExtension(new File("foo"))).isEmpty();
        assertThat(FsCrawlerUtil.getFileExtension(new File("foo.bar.baz"))).isEqualTo("baz");
    }

    @Test
    public void localDateToDate() {
        LocalDateTime now = LocalDateTime.now();
        Date date = FsCrawlerUtil.localDateTimeToDate(now);
        logger.info("Current Time [{}] in [{}] is actually [{}]", 
                now, 
                TimeZone.getDefault().getDisplayName(),
                date);
        assertThat(date).isNotNull();
    }
}
