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
package fr.pilato.elasticsearch.crawler.fs.settings;

import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerIllegalConfigurationException;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class FsMatchFilesYamlTest extends AbstractFSCrawlerTestCase {

    @ParameterizedTest
    @MethodSource("quotedPatterns")
    void yaml_quotes_preserve_escaped_patterns(String yaml, @TempDir Path tempDir) throws IOException {
        Fs settings = load(yaml, tempDir).getFs();
        Assertions.assertThat(settings.getIncludes()).containsExactly("*\\.doc", "*/report\\+final\\.pdf");
        Assertions.assertThat(settings.getExcludes()).containsExactly("*/report\\[draft\\]\\.doc", "*/.ds_store");
        Assertions.assertThat(
                        FsCrawlerUtil.isIndexable(false, "/report.doc", settings.getIncludes(), settings.getExcludes()))
                .isTrue();
        Assertions.assertThat(
                        FsCrawlerUtil.isIndexable(false, "/reportXdoc", settings.getIncludes(), settings.getExcludes()))
                .isFalse();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        false, "/report+final.pdf", settings.getIncludes(), settings.getExcludes()))
                .isTrue();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        false, "/reportfinal.pdf", settings.getIncludes(), settings.getExcludes()))
                .isFalse();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        false, "/report[draft].doc", settings.getIncludes(), settings.getExcludes()))
                .isFalse();
    }

    static Stream<String> quotedPatterns() {
        return Stream.of("""
                fs:
                  includes:
                    - '*\\.doc'
                    - '*/report\\+final\\.pdf'
                  excludes:
                    - '*/report\\[draft\\]\\.doc'
                """, """
                fs:
                  includes:
                    - "*\\\\.doc"
                    - "*/report\\\\+final\\\\.pdf"
                  excludes:
                    - "*/report\\\\[draft\\\\]\\\\.doc"
                """);
    }

    @Test
    void double_quoted_yaml_rejects_a_single_backslash_before_dot(@TempDir Path tempDir) {
        Assertions.assertThatThrownBy(() -> load("""
                        fs:
                          includes:
                            - "*\\.doc"
                        """, tempDir))
                .isInstanceOf(FsCrawlerIllegalConfigurationException.class)
                .hasMessageContaining("Syntax error");
    }

    @Test
    void leading_wildcards_must_be_quoted_in_yaml(@TempDir Path tempDir) {
        Assertions.assertThatThrownBy(() -> load("""
                        fs:
                          includes:
                            - *.pdf
                        """, tempDir))
                .isInstanceOf(FsCrawlerIllegalConfigurationException.class)
                .hasMessageContaining("Syntax error");
    }

    @Test
    void a_single_custom_exclude_replaces_only_the_first_default(@TempDir Path tempDir) throws IOException {
        Fs settings = load("""
                        fs:
                          excludes:
                            - '*/private/*'
                        """, tempDir).getFs();
        Assertions.assertThat(settings.getExcludes()).containsExactly("*/private/*", "*/.ds_store");
        Assertions.assertThat(FsCrawlerUtil.isIndexable(false, "/~draft.pdf", List.of(), settings.getExcludes()))
                .isTrue();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(false, "/.DS_Store", List.of(), settings.getExcludes()))
                .isFalse();
    }

    @Test
    void explicit_empty_excludes_retain_the_defaults(@TempDir Path tempDir) throws IOException {
        Fs settings = load("""
                        fs:
                          includes: []
                          excludes: []
                        """, tempDir).getFs();
        Assertions.assertThat(settings.getExcludes()).containsExactlyElementsOf(Defaults.DEFAULT_EXCLUDED);
        Assertions.assertThat(
                        FsCrawlerUtil.isIndexable(false, "/~draft.pdf", settings.getIncludes(), settings.getExcludes()))
                .isFalse();
    }

    @Test
    void two_custom_excludes_replace_both_default_positions(@TempDir Path tempDir) throws IOException {
        Fs settings = load("""
                        fs:
                          excludes:
                            - '*/private/*'
                            - '*/archive/*'
                        """, tempDir).getFs();
        Assertions.assertThat(settings.getExcludes()).containsExactly("*/private/*", "*/archive/*");
        Assertions.assertThat(FsCrawlerUtil.isIndexable(false, "/~draft.pdf", List.of(), settings.getExcludes()))
                .isTrue();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(false, "/.DS_Store", List.of(), settings.getExcludes()))
                .isTrue();
        Assertions.assertThat(
                        FsCrawlerUtil.isIndexable(false, "/private/report.pdf", List.of(), settings.getExcludes()))
                .isFalse();
        Assertions.assertThat(
                        FsCrawlerUtil.isIndexable(false, "/archive/report.pdf", List.of(), settings.getExcludes()))
                .isFalse();
    }

    @Test
    void single_quoted_yaml_preserves_a_doubled_apostrophe(@TempDir Path tempDir) throws IOException {
        Fs settings = load("""
                        fs:
                          includes:
                            - '*/author''s report\\.pdf'
                        """, tempDir).getFs();
        Assertions.assertThat(settings.getIncludes()).containsExactly("*/author's report\\.pdf");
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        false, "/author's report.pdf", settings.getIncludes(), settings.getExcludes()))
                .isTrue();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        false, "/authors report.pdf", settings.getIncludes(), settings.getExcludes()))
                .isFalse();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        false, "/author's reportXpdf", settings.getIncludes(), settings.getExcludes()))
                .isFalse();
    }

    private FsSettings load(String yaml, Path tempDir) throws IOException {
        Path file = tempDir.resolve("_settings.yaml");
        Files.writeString(file, yaml);
        return FsSettingsLoader.load(file);
    }
}
