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
package fr.pilato.elasticsearch.crawler.fs.framework;

import com.carrotsearch.randomizedtesting.jupiter.RandomizedTest;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@Isolated("Characterizes matching with different JVM default locales")
class FsMatchFilesTest extends AbstractFSCrawlerTestCase {

    @Test
    void exclude_only() {
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        false, "/test.doc", new ArrayList<>(), Collections.singletonList("*/*.doc")))
                .isFalse();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        false, "/test.xls", new ArrayList<>(), Collections.singletonList("*/*.doc")))
                .isTrue();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        false, "/my.doc.xls", new ArrayList<>(), Collections.singletonList("*/*.doc")))
                .isTrue();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        false, "/my.doc.xls", new ArrayList<>(), Arrays.asList("*/*.doc", "*/*.xls")))
                .isFalse();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        false, "/my.doc.xls", new ArrayList<>(), Collections.singletonList("*/my.d?c*.xls")))
                .isFalse();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        false, "/my.douc.xls", new ArrayList<>(), Collections.singletonList("*/my.d?c*.xls")))
                .isTrue();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        false, "/.snapshots", new ArrayList<>(), Collections.singletonList("*/.snapshots")))
                .isFalse();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        false, "/doc.doc", new ArrayList<>(), Arrays.asList("*/*.pdf", "*/*.xls", "*/*.doc")))
                .isFalse();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        false, "/doc.ppt", new ArrayList<>(), Arrays.asList("*/*.pdf", "*/*.xls", "*/*.doc")))
                .isTrue();
    }

    @Test
    void include_only() {
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        false, "/test.doc", Collections.singletonList("*/*.doc"), new ArrayList<>()))
                .isTrue();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        false, "/test.xls", Collections.singletonList("*/*.doc"), new ArrayList<>()))
                .isFalse();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        false, "/my.doc.xls", Collections.singletonList("*/*.doc"), new ArrayList<>()))
                .isFalse();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        false, "/my.doc.xls", Collections.singletonList("*/my.d?c*.xls"), new ArrayList<>()))
                .isTrue();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        false, "/my.douc.xls", Collections.singletonList("*/my.d?c*.xls"), new ArrayList<>()))
                .isFalse();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        false, "/doc.doc", Arrays.asList("*/*.pdf", "*/*.xls", "*/*.doc"), new ArrayList<>()))
                .isTrue();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        false, "/doc.ppt", Arrays.asList("*/*.pdf", "*/*.xls", "*/*.doc"), new ArrayList<>()))
                .isFalse();
    }

    @Test
    void include_exclude() {
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        false, "/test.doc", Collections.singletonList("*/*.xls"), Collections.singletonList("*/*.doc")))
                .isFalse();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        false, "/test.xls", Collections.singletonList("*/*.xls"), Collections.singletonList("*/*.doc")))
                .isTrue();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        false,
                        "/my.doc.xls",
                        Collections.singletonList("*/*.xls"),
                        Collections.singletonList("*/*.doc")))
                .isTrue();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        false,
                        "/my.doc.xls",
                        Collections.singletonList("*/*.xls"),
                        Collections.singletonList("*/my.d?c*.xls")))
                .isFalse();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        false,
                        "/my.douc.xls",
                        Collections.singletonList("*/*.xls"),
                        Collections.singletonList("*/my.d?c*.xls")))
                .isTrue();
    }

    @Test
    void case_insensitive() {
        // Excludes
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        false, "/test.doc", new ArrayList<>(), Collections.singletonList("*/*.DOC")))
                .isFalse();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        false, "/test.xls", new ArrayList<>(), Collections.singletonList("*/*.DOC")))
                .isTrue();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        false, "/my.doc.xls", new ArrayList<>(), Collections.singletonList("*/*.DOC")))
                .isTrue();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        false, "/my.doc.xls", new ArrayList<>(), Arrays.asList("*/*.DOC", "*/*.XLS")))
                .isFalse();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        false, "/my.doc.xls", new ArrayList<>(), Collections.singletonList("*/MY.D?C*.XLS")))
                .isFalse();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        false, "/my.douc.xls", new ArrayList<>(), Collections.singletonList("*/MY.d?C*.XLS")))
                .isTrue();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        false, "/.snapshots", new ArrayList<>(), Collections.singletonList("*/.SNAPSHOTS")))
                .isFalse();

        // Includes
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        false, "/test.doc", Collections.singletonList("*/*.DOC"), new ArrayList<>()))
                .isTrue();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        false, "/test.xls", Collections.singletonList("*/*.DOC"), new ArrayList<>()))
                .isFalse();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        false, "/my.doc.xls", Collections.singletonList("*/*.DOC"), new ArrayList<>()))
                .isFalse();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        false, "/my.doc.xls", Collections.singletonList("*/MY.D?C*.XLS"), new ArrayList<>()))
                .isTrue();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        false, "/my.douc.xls", Collections.singletonList("*/MY.D?C*.XLS"), new ArrayList<>()))
                .isFalse();
    }

    @Test
    void directories() {
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        true, "/folderA", new ArrayList<>(), Collections.singletonList("/folderB/subfolder*")))
                .isTrue();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        true,
                        "/folderA/subfolderA",
                        new ArrayList<>(),
                        Collections.singletonList("/folderB/subfolder*")))
                .isTrue();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        true,
                        "/folderA/subfolderB",
                        new ArrayList<>(),
                        Collections.singletonList("/folderB/subfolder*")))
                .isTrue();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        true,
                        "/folderA/subfolderC",
                        new ArrayList<>(),
                        Collections.singletonList("/folderB/subfolder*")))
                .isTrue();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        true, "/folderB", new ArrayList<>(), Collections.singletonList("/folderB/subfolder*")))
                .isTrue();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        true,
                        "/folderB/subfolderA",
                        new ArrayList<>(),
                        Collections.singletonList("/folderB/subfolder*")))
                .isFalse();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        true,
                        "/folderB/subfolderB",
                        new ArrayList<>(),
                        Collections.singletonList("/folderB/subfolder*")))
                .isFalse();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        true,
                        "/folderB/subfolderC",
                        new ArrayList<>(),
                        Collections.singletonList("/folderB/subfolder*")))
                .isFalse();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        true, "/folderC", new ArrayList<>(), Collections.singletonList("/folderB/subfolder*")))
                .isTrue();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        true,
                        "/folderC/subfolderA",
                        new ArrayList<>(),
                        Collections.singletonList("/folderB/subfolder*")))
                .isTrue();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        true,
                        "/folderC/subfolderB",
                        new ArrayList<>(),
                        Collections.singletonList("/folderB/subfolder*")))
                .isTrue();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        true,
                        "/folderC/subfolderC",
                        new ArrayList<>(),
                        Collections.singletonList("/folderB/subfolder*")))
                .isTrue();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        true, "/subfolderA", new ArrayList<>(), Collections.singletonList("/folderB/subfolder*")))
                .isTrue();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        true, "/subfolderB", new ArrayList<>(), Collections.singletonList("/folderB/subfolder*")))
                .isTrue();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        true, "/subfolderC", new ArrayList<>(), Collections.singletonList("/folderB/subfolder*")))
                .isTrue();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        true, "/folderA", new ArrayList<>(), Collections.singletonList("/folderB/subfolderB/*")))
                .isTrue();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        true,
                        "/folderA/subfolderA",
                        new ArrayList<>(),
                        Collections.singletonList("/folderB/subfolderB/*")))
                .isTrue();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        true,
                        "/folderA/subfolderB",
                        new ArrayList<>(),
                        Collections.singletonList("/folderB/subfolderB/*")))
                .isTrue();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        true,
                        "/folderA/subfolderC",
                        new ArrayList<>(),
                        Collections.singletonList("/folderB/subfolderB/*")))
                .isTrue();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        true, "/folderB", new ArrayList<>(), Collections.singletonList("/folderB/subfolderB/*")))
                .isTrue();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        true,
                        "/folderB/subfolderA",
                        new ArrayList<>(),
                        Collections.singletonList("/folderB/subfolderB/*")))
                .isTrue();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        true,
                        "/folderB/subfolderB",
                        new ArrayList<>(),
                        Collections.singletonList("/folderB/subfolderB/*")))
                .isFalse();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        true,
                        "/folderB/subfolderC",
                        new ArrayList<>(),
                        Collections.singletonList("/folderB/subfolderB/*")))
                .isTrue();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        true, "/folderC", new ArrayList<>(), Collections.singletonList("/folderB/subfolderB/*")))
                .isTrue();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        true,
                        "/folderC/subfolderA",
                        new ArrayList<>(),
                        Collections.singletonList("/folderB/subfolderB/*")))
                .isTrue();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        true,
                        "/folderC/subfolderB",
                        new ArrayList<>(),
                        Collections.singletonList("/folderB/subfolderB/*")))
                .isTrue();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        true,
                        "/folderC/subfolderC",
                        new ArrayList<>(),
                        Collections.singletonList("/folderB/subfolderB/*")))
                .isTrue();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        true, "/subfolderA", new ArrayList<>(), Collections.singletonList("/folderB/subfolderB/*")))
                .isTrue();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        true, "/subfolderB", new ArrayList<>(), Collections.singletonList("/folderB/subfolderB/*")))
                .isTrue();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        true, "/subfolderC", new ArrayList<>(), Collections.singletonList("/folderB/subfolderB/*")))
                .isTrue();
    }

    /**
     * Testing with short wildcards (word documents being edited) See <a
     * href="https://github.com/dadoonet/fscrawler/issues/1794>#1794</a>
     */
    @Test
    void specialCharacters1794() {
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        false,
                        "/filter test/should-exclude.docx",
                        Arrays.asList("*.pdf", "*.doc", "*.docx", "*.xsl", "*.xslx", "*.msg", "*.txt", "*.md"),
                        Arrays.asList("*.exclude", "*~*")))
                .isTrue();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        false,
                        "/filter test/~should-exclude.docx",
                        Arrays.asList("*.pdf", "*.doc", "*.docx", "*.xsl", "*.xslx", "*.msg", "*.txt", "*.md"),
                        Arrays.asList("*.exclude", "*~*")))
                .isFalse();
    }

    /**
     * Testing with short wildcards (word documents being edited) See <a
     * href="https://github.com/dadoonet/fscrawler/issues/1794>#1794</a>
     */
    @Test
    void isMatching() {
        Assertions.assertThat(FsCrawlerUtil.isMatching(
                        "/filter test/~should-exclude.docx", Collections.singletonList("*~*"), "exclusion"))
                .isTrue();
        Assertions.assertThat(FsCrawlerUtil.isMatching(
                        "/filter test/~should-exclude.docx", Collections.singletonList("*/~*"), "exclusion"))
                .isTrue();
        Assertions.assertThat(FsCrawlerUtil.isMatching(
                        "/filter test/should-not-exclude.docx", Collections.singletonList("*~*"), "exclusion"))
                .isFalse();
        Assertions.assertThat(FsCrawlerUtil.isMatching(
                        "/filter test/should-not-exclude.docx.exclude",
                        Collections.singletonList("*.exclude"),
                        "exclusion"))
                .isTrue();
    }

    /** Testing with windows separator See <a href="https://github.com/dadoonet/fscrawler/issues/1974>#1974</a> */
    @Test
    void windowsSeparator() {
        // We test with the Linux separator
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        true, "/arbets", new ArrayList<>(), Collections.singletonList("*/arbets/*")))
                .isFalse();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        true, "/foo/arbets", new ArrayList<>(), Collections.singletonList("*/arbets/*")))
                .isFalse();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        true, "/foo", new ArrayList<>(), Collections.singletonList("*/arbets/*")))
                .isTrue();
        // We test with the Windows separator
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        true, "\\arbets", new ArrayList<>(), Collections.singletonList("*/arbets/*")))
                .isFalse();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        true, "\\foo\\arbets", new ArrayList<>(), Collections.singletonList("*/arbets/*")))
                .isFalse();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(
                        true, "\\foo", new ArrayList<>(), Collections.singletonList("*/arbets/*")))
                .isTrue();
    }

    @Test
    void empty_rules_accept_files_and_directories() {
        String path = "/" + RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 6, 12);
        Assertions.assertThat(FsCrawlerUtil.isIndexable(false, path, List.of(), List.of()))
                .isTrue();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(true, path, List.of(), List.of()))
                .isTrue();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(false, path, null, null))
                .isTrue();
    }

    @ParameterizedTest(name = "included={0}, excluded={1} -> indexable={2}")
    @MethodSource("includeExcludeDecisions")
    void include_exclude_decision_matrix(boolean included, boolean excluded, boolean expected) {
        String name = RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 6, 12);
        String path = "/" + name + ".pdf";
        List<String> includes = List.of(included ? "*.pdf" : "*.txt");
        List<String> excludes = List.of(excluded ? "*.pdf" : "*.doc");
        Assertions.assertThat(FsCrawlerUtil.isIndexable(false, path, includes, excludes))
                .isEqualTo(expected);
    }

    static Stream<Arguments> includeExcludeDecisions() {
        return Stream.of(
                Arguments.of(true, false, true),
                Arguments.of(true, true, false),
                Arguments.of(false, false, false),
                Arguments.of(false, true, false));
    }

    @Test
    void directories_remain_traversable_unless_excluded() {
        List<String> includes = List.of("*.pdf");
        List<String> excludes = List.of("*/private/*");
        Assertions.assertThat(FsCrawlerUtil.isIndexable(true, "/reports", includes, excludes))
                .isTrue();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(true, "/reports/", includes, excludes))
                .isTrue();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(true, "/private", includes, excludes))
                .isFalse();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(true, "/private/", includes, excludes))
                .isFalse();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(true, "/private", List.of("*"), excludes))
                .isFalse();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(false, "/reports/readme.txt", includes, excludes))
                .isFalse();
    }

    @Test
    void directory_patterns_must_match_the_trailing_separator() {
        Assertions.assertThat(FsCrawlerUtil.isIndexable(true, "/.ignore", List.of(), List.of(".ignore")))
                .isTrue();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(true, "/.ignore", List.of(), List.of("/.ignore")))
                .isTrue();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(true, "/.ignore", List.of(), List.of("*/\\.ignore/*")))
                .isFalse();
        Assertions.assertThat(FsCrawlerUtil.isIndexable(true, "/nested/.ignore", List.of(), List.of("*/\\.ignore/*")))
                .isFalse();
    }

    // These expectations preserve the existing regex-based matcher, including its surprising cases.
    // Changing them requires an explicit compatibility decision, rather than only changing these tests.
    @ParameterizedTest(name = "{0} against {1} -> {2}")
    @MethodSource("compatibilityPatterns")
    void compatible_patterns_work_for_both_includes_and_excludes(String pattern, String path, boolean matches) {
        Assertions.assertThat(FsCrawlerUtil.isIndexable(false, path, List.of(pattern), List.of()))
                .isEqualTo(matches);
        Assertions.assertThat(FsCrawlerUtil.isIndexable(false, path, List.of(), List.of(pattern)))
                .isEqualTo(!matches);
    }

    static Stream<Arguments> compatibilityPatterns() {
        return Stream.of(
                Arguments.of("*.doc", "/reportXdoc", true),
                Arguments.of("*\\.doc", "/report.doc", true),
                Arguments.of("*\\.doc", "/reportXdoc", false),
                Arguments.of("*/report+final.doc", "/report+final.doc", false),
                Arguments.of("*/report+final.doc", "/reportfinal.doc", true),
                Arguments.of("*/report\\+final\\.doc", "/report+final.doc", true),
                Arguments.of("*/report\\+final\\.doc", "/reportfinal.doc", false),
                Arguments.of("*/report\\[draft\\]\\.doc", "/report[draft].doc", true),
                Arguments.of("*/report\\[draft\\]\\.doc", "/reportd.doc", false),
                Arguments.of("*/report\\[\\.doc", "/report[.doc", true),
                Arguments.of("*/report\\(final\\)\\.doc", "/report(final).doc", true),
                Arguments.of("*/report\\(final\\)\\.doc", "/reportfinal.doc", false),
                Arguments.of("*/price\\$list\\.pdf", "/price$list.pdf", true),
                Arguments.of("*/report?.doc", "/report.doc", true),
                Arguments.of("*/report?.doc", "/report1.doc", true),
                Arguments.of("*/report?.doc", "/report12.doc", false),
                Arguments.of("*/report\\?.doc", "/report?.doc", false),
                Arguments.of("/images/*\\.jpg", "/images/archive/photo.jpg", true),
                Arguments.of("/images/*\\.jpg", "/other/images/photo.jpg", false),
                Arguments.of("*\\.pdf", "\\reports\\REPORT.PDF", true),
                Arguments.of("*/private/*", "\\reports\\private\\report.pdf", true),
                Arguments.of("*/~*", "/~draft.doc", true),
                Arguments.of("*/~*", "/draft~.doc", false),
                Arguments.of("*.pdf", "/reportXpdf", true),
                Arguments.of("*/report(final)\\.pdf", "/reportfinal.pdf", true),
                Arguments.of("*/report(final)\\.pdf", "/report(final).pdf", false),
                Arguments.of("*/report[12]\\.pdf", "/report1.pdf", true),
                Arguments.of("*/report[12]\\.pdf", "/report2.pdf", true),
                Arguments.of("*/report[12]\\.pdf", "/report[12].pdf", false),
                Arguments.of("*/report{2}\\.pdf", "/reportt.pdf", true),
                Arguments.of("*/report{2}\\.pdf", "/report{2}.pdf", false),
                Arguments.of("*/report\\{2\\}\\.pdf", "/report{2}.pdf", true),
                Arguments.of("*/report\\{2\\}\\.pdf", "/reportt.pdf", false),
                Arguments.of("*/^report\\.pdf", "/^report.pdf", false),
                Arguments.of("*/\\^report\\.pdf", "/^report.pdf", true),
                Arguments.of("*/\\^report\\.pdf", "/report.pdf", false),
                Arguments.of("*/budget$\\.pdf", "/budget$.pdf", false),
                Arguments.of("*/budget\\$\\.pdf", "/budget$.pdf", true),
                Arguments.of("*/budget\\$\\.pdf", "/budget.pdf", false),
                Arguments.of("*/report(a|b)\\.pdf", "/reporta.pdf", true),
                Arguments.of("*/report(a|b)\\.pdf", "/reportb.pdf", true),
                Arguments.of("*/report(a|b)\\.pdf", "/report(a|b).pdf", false),
                Arguments.of("*/report\\(a\\|b\\)\\.pdf", "/report(a|b).pdf", true),
                Arguments.of("*/report\\(a\\|b\\)\\.pdf", "/reporta.pdf", false),
                Arguments.of("*/report]\\.pdf", "/report].pdf", true),
                Arguments.of("*/report}\\.pdf", "/report}.pdf", true),
                Arguments.of("*/report\\*\\.pdf", "/report*.pdf", false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"*/report[.doc", "*/report(.pdf"})
    void unescaped_unbalanced_delimiters_throw_for_includes_and_excludes(String pattern) {
        Assertions.assertThatThrownBy(
                        () -> FsCrawlerUtil.isIndexable(false, "/report[.doc", List.of(pattern), List.of()))
                .isInstanceOf(PatternSyntaxException.class);
        Assertions.assertThatThrownBy(
                        () -> FsCrawlerUtil.isIndexable(false, "/report[.doc", List.of(), List.of(pattern)))
                .isInstanceOf(PatternSyntaxException.class);
    }

    @Test
    void case_matching_currently_depends_on_the_default_locale() {
        Locale previous = Locale.getDefault();
        Locale previousDisplay = Locale.getDefault(Locale.Category.DISPLAY);
        Locale previousFormat = Locale.getDefault(Locale.Category.FORMAT);
        try {
            Locale.setDefault(Locale.ENGLISH);
            Assertions.assertThat(FsCrawlerUtil.isIndexable(false, "/FILE.TXT", List.of("*/file.txt"), List.of()))
                    .isTrue();
            Assertions.assertThat(FsCrawlerUtil.isIndexable(false, "/FILE.TXT", List.of(), List.of("*/file.txt")))
                    .isFalse();
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            Assertions.assertThat(FsCrawlerUtil.isIndexable(false, "/FILE.TXT", List.of("*/file.txt"), List.of()))
                    .isFalse();
            Assertions.assertThat(FsCrawlerUtil.isIndexable(false, "/FILE.TXT", List.of(), List.of("*/file.txt")))
                    .isTrue();
        } finally {
            Locale.setDefault(previous);
            Locale.setDefault(Locale.Category.DISPLAY, previousDisplay);
            Locale.setDefault(Locale.Category.FORMAT, previousFormat);
        }
    }
}
