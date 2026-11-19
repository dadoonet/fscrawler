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
package fr.pilato.elasticsearch.crawler.fs.test.integration.elasticsearch;

import com.carrotsearch.randomizedtesting.jupiter.RandomizedTest;
import com.jayway.jsonpath.JsonPath;
import fr.pilato.elasticsearch.crawler.fs.beans.CrawlerState;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsLoader;
import fr.pilato.elasticsearch.crawler.fs.test.framework.VerySlow;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Real filesystem coverage, shared by the Linux and Windows integration-test jobs. */
@VerySlow
class FsCrawlerTestIncludesExcludesFilesIT extends AbstractFsCrawlerITCase {
    private final Map<String, String> contents = new LinkedHashMap<>();
    private String report;
    private String doubleExtension;
    private String markdown;
    private String upperCaseExtension;
    private String draft;
    private String nested;
    private String archived;

    @BeforeEach
    void createFiles() throws Exception {
        // A separate native root avoids the default samples copied by the base class.
        currentTestResourceDir = Files.createDirectory(rootTmpDir.resolve(randomLetters()));
        String stem = randomLetters();
        report = "/" + stem + ".txt";
        doubleExtension = "/" + stem + ".pdf.txt";
        markdown = "/" + stem + ".md";
        upperCaseExtension = "/" + stem + "-upper.TXT";
        draft = "/drafts/" + stem + ".txt";
        nested = "/project café/nested/" + stem + ".txt";
        archived = "/archive/" + stem + ".txt";
        for (String virtualPath :
                List.of(report, doubleExtension, markdown, upperCaseExtension, draft, nested, archived)) {
            Path file = currentTestResourceDir;
            for (String component : virtualPath.substring(1).split("/")) {
                file = file.resolve(component);
            }
            Files.createDirectories(file.getParent());
            String content = randomLetters();
            Files.writeString(file, content + "\n");
            contents.put(virtualPath, content);
        }
    }

    @Test
    void includes_text_files_across_native_directories() throws Exception {
        FsSettings settings = createTestSettings();
        settings.getFs().setIncludes(List.of("*\\.txt"));
        assertIndexedFiles(settings, report, doubleExtension, upperCaseExtension, draft, nested, archived);
    }

    @Test
    void excludes_a_directory_and_its_files() throws Exception {
        FsSettings settings = createTestSettings();
        settings.getFs().setExcludes(List.of("/archive/*"));
        assertIndexedFiles(settings, report, doubleExtension, markdown, upperCaseExtension, draft, nested);
    }

    @Test
    void yaml_excludes_take_precedence_over_includes() throws Exception {
        Path yaml = rootTmpDir.resolve("_settings.yaml");
        Files.writeString(yaml, """
                fs:
                  url: '%s'
                  includes:
                    - '*\\.txt'
                  excludes:
                    - '/drafts/*'
                    - '/archive/*'
                """.formatted(currentTestResourceDir.toString().replace("'", "''")));
        FsSettings settings = createTestSettings();
        // Keep the test cluster configuration, but load the filesystem configuration from disk.
        settings.setFs(FsSettingsLoader.load(yaml).getFs());
        assertIndexedFiles(settings, report, doubleExtension, upperCaseExtension, nested);
    }

    @Test
    void includes_nested_files_through_nonmatching_parent_directories() throws Exception {
        FsSettings settings = createTestSettings();
        settings.getFs().setIncludes(List.of("/project café/nested/*\\.txt"));
        assertIndexedFiles(settings, nested);
    }

    @Test
    void includes_match_mixed_case_names_on_both_operating_systems() throws Exception {
        FsSettings settings = createTestSettings();
        // Distinct basenames also work on case-insensitive Windows filesystems.
        settings.getFs().setIncludes(List.of("/*-UPPER\\.txt"));
        assertIndexedFiles(settings, upperCaseExtension);
    }

    private void assertIndexedFiles(FsSettings settings, String... expectedPaths) throws Exception {
        settings.getFs().setIndexContent(true);
        settings.getFs().setUpdateRate(TimeValue.timeValueMinutes(15));
        crawler = startCrawler(settings);
        // Wait for the whole scan, not a transient document count that could hide extra files.
        Awaitility.await()
                .atMost(MAX_WAIT_FOR_SEARCH)
                .until(() -> crawler.getFsParser().getCheckpoint().getState() == CrawlerState.COMPLETED);
        crawler.close();
        crawler = null;
        // close() drains the bulk processor before we inspect the complete result.
        refresh(settings.getElasticsearch().getIndex());
        ESSearchResponse response = client.search(new ESSearchRequest()
                .withIndex(settings.getElasticsearch().getIndex())
                .withSize(contents.size() + 1));
        Assertions.assertThat(response.getTotalHits()).isEqualTo(expectedPaths.length);
        Assertions.assertThat(response.getHits())
                .extracting(hit -> ((String) JsonPath.read(hit.getSource(), "$.path.virtual")).replace('\\', '/'))
                .containsExactlyInAnyOrder(expectedPaths);
        for (var hit : response.getHits()) {
            String path = ((String) JsonPath.read(hit.getSource(), "$.path.virtual")).replace('\\', '/');
            Assertions.assertThat((String) JsonPath.read(hit.getSource(), "$.content"))
                    .as("Extracted content of %s", path)
                    .contains(contents.get(path));
        }
    }

    private String randomLetters() {
        return RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 8, 16);
    }
}
