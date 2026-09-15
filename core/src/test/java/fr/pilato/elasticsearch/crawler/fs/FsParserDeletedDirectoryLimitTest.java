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
package fr.pilato.elasticsearch.crawler.fs;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carrotsearch.randomizedtesting.jupiter.RandomizedTest;
import fr.pilato.elasticsearch.crawler.fs.beans.ScanStatistic;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerDocumentService;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerManagementService;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsLoader;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import fr.pilato.elasticsearch.crawler.fs.test.framework.Slow;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

@Slow
class FsParserDeletedDirectoryLimitTest extends AbstractFSCrawlerTestCase {

    private static final int DIRECTORY_QUERY_LIMIT = 10_000;

    @Test
    void retains_folder_record_when_file_query_reaches_limit() throws Exception {
        TestContext context = createContext();
        String deletedPath = randomPath();
        String filename = randomName();
        when(context.managementService().getFileDirectory(deletedPath))
                .thenReturn(Collections.nCopies(DIRECTORY_QUERY_LIMIT, filename));
        when(context.managementService().getFolderDirectory(deletedPath)).thenReturn(List.of());

        removeDirectory(context.parser(), deletedPath);

        verify(context.managementService(), never())
                .delete(eq(context.settings().getElasticsearch().getIndexFolder()), anyString());
    }

    @Test
    void retains_parent_folder_record_when_descendant_query_reaches_limit() throws Exception {
        TestContext context = createContext();
        String deletedPath = randomPath();
        String childPath = deletedPath + "/" + randomName();
        String filename = randomName();
        when(context.managementService().getFileDirectory(deletedPath)).thenReturn(List.of());
        when(context.managementService().getFolderDirectory(deletedPath)).thenReturn(List.of(childPath));
        when(context.managementService().getFileDirectory(childPath))
                .thenReturn(Collections.nCopies(DIRECTORY_QUERY_LIMIT, filename));
        when(context.managementService().getFolderDirectory(childPath)).thenReturn(List.of());

        removeDirectory(context.parser(), deletedPath);

        verify(context.managementService(), never())
                .delete(eq(context.settings().getElasticsearch().getIndexFolder()), anyString());
    }

    @Test
    void deletes_folder_record_when_all_queries_are_below_limit() throws Exception {
        TestContext context = createContext();
        String deletedPath = randomPath();
        when(context.managementService().getFileDirectory(deletedPath)).thenReturn(List.of(randomName()));
        when(context.managementService().getFolderDirectory(deletedPath)).thenReturn(List.of());

        removeDirectory(context.parser(), deletedPath);

        verify(context.managementService())
                .delete(eq(context.settings().getElasticsearch().getIndexFolder()), anyString());
    }

    private TestContext createContext() throws Exception {
        FsSettings settings = FsSettingsLoader.load();
        String indexPrefix = RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 6, 12)
                .toLowerCase(Locale.ROOT);
        settings.setName(indexPrefix);
        settings.getElasticsearch().setIndex(indexPrefix + "_docs");
        settings.getElasticsearch().setIndexFolder(indexPrefix + "_folder");
        FsCrawlerManagementService managementService = mock(FsCrawlerManagementService.class);
        FsParser parser = new FsParser(
                settings, testTmpDir, managementService, mock(FsCrawlerDocumentService.class), 1, false, null);
        parser.closed.set(false);
        return new TestContext(parser, settings, managementService);
    }

    private void removeDirectory(FsParser parser, String path) throws Exception {
        Method method =
                FsParser.class.getDeclaredMethod("removeEsDirectoryRecursively", String.class, ScanStatistic.class);
        method.setAccessible(true);
        method.invoke(parser, path, new ScanStatistic(path));
    }

    private String randomPath() {
        return "/" + randomName();
    }

    private String randomName() {
        return RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 6, 12)
                .toLowerCase(Locale.ROOT);
    }

    private record TestContext(FsParser parser, FsSettings settings, FsCrawlerManagementService managementService) {}
}
