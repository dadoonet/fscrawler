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

import com.carrotsearch.randomizedtesting.jupiter.RandomizedTest;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Reads marked examples from the documentation so configuration and expected outcomes cannot drift independently. */
class FsMatchFilesDocumentationTest extends AbstractFSCrawlerTestCase {

    @Test
    void opening_yaml_example_matches_every_row_in_its_results_table(@TempDir Path tempDir) throws IOException {
        String example = documentationExample("opening");
        String yaml = fencedBlock(example, "yaml");
        Path file = tempDir.resolve("_settings.yaml");
        Files.writeString(file, yaml);
        Fs settings = FsSettingsLoader.load(file).getFs();
        Pattern rowPattern = Pattern.compile("^\\| `([^`]+)` \\| `([^`]+)` \\| ([^|]+) \\|\\s*$");
        List<String> table =
                example.lines().filter(line -> line.startsWith("|")).toList();
        Assertions.assertThat(table)
                .as("Results table has a header, separator, and data rows")
                .hasSizeGreaterThan(2);
        int checked = 0;
        for (String line : table.subList(2, table.size())) {
            Matcher rows = rowPattern.matcher(line);
            Assertions.assertThat(rows.matches())
                    .as("Recognized results table row: %s", line)
                    .isTrue();
            String actualPath = rows.group(1);
            String documentedVirtualPath = rows.group(2);
            String outcome = rows.group(3).trim();
            Assertions.assertThat(actualPath).startsWith(settings.getUrl() + "/");
            String virtualPath = FsCrawlerUtil.computeVirtualPathName(settings.getUrl(), actualPath);
            Assertions.assertThat(virtualPath)
                    .as("Virtual path for %s", actualPath)
                    .isEqualTo(documentedVirtualPath);
            boolean expected;
            if (outcome.equals("Included")) {
                expected = true;
            } else {
                Assertions.assertThat(outcome)
                        .as("Recognized documentation outcome for %s", actualPath)
                        .isIn("Does not match an include", "Excluded", "Excluded, despite matching an include");
                expected = false;
            }
            Assertions.assertThat(FsCrawlerUtil.isIndexable(
                            false, virtualPath, settings.getIncludes(), settings.getExcludes()))
                    .as("%s: %s", actualPath, outcome)
                    .isEqualTo(expected);
            checked++;
        }
        Assertions.assertThat(checked).as("Documented file examples checked").isPositive();
    }

    @Test
    void directory_tree_labels_agree_with_the_documented_exclude_pattern() throws IOException {
        String example = documentationExample("directory-tree");
        Matcher configuration = Pattern.compile("`fs\\.url: '([^']+)'` and the exclude pattern `([^`]+)`")
                .matcher(example);
        Assertions.assertThat(configuration.find())
                .as("Directory example configuration")
                .isTrue();
        String root = configuration.group(1);
        List<String> excludes = List.of(configuration.group(2));
        List<String> parents = new ArrayList<>();
        Pattern linePattern = Pattern.compile("^([ │]*)([├└]── )?([^\\s]+/)\\s+\\[(scanned|skipped)[^]]*]$");
        int checked = 0;
        int skipped = 0;
        for (String line : fencedBlock(example, "text").lines().toList()) {
            Matcher row = linePattern.matcher(line);
            Assertions.assertThat(row.matches())
                    .as("Recognized directory tree row: %s", line)
                    .isTrue();
            String directory = row.group(3);
            String actualPath;
            if (row.group(2) == null) {
                Assertions.assertThat(checked)
                        .as("Only the first row is the root")
                        .isZero();
                Assertions.assertThat(directory).isEqualTo(root + "/");
                actualPath = root;
            } else {
                Assertions.assertThat(row.group(1).length() % 4)
                        .as("Tree indentation uses four columns")
                        .isZero();
                int depth = row.group(1).length() / 4 + 1;
                while (parents.size() > depth) {
                    parents.remove(parents.size() - 1);
                }
                Assertions.assertThat(parents)
                        .as("Parent directory for %s", line)
                        .hasSize(depth);
                actualPath = parents.get(depth - 1) + "/" + directory.substring(0, directory.length() - 1);
            }
            parents.add(actualPath);
            String virtualPath = FsCrawlerUtil.computeVirtualPathName(root, actualPath);
            boolean expected = row.group(4).equals("scanned");
            Assertions.assertThat(FsCrawlerUtil.isIndexable(true, virtualPath, List.of(), excludes))
                    .as("%s is %s", actualPath, row.group(4))
                    .isEqualTo(expected);
            if (!expected) {
                String filename = RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 6, 12);
                Assertions.assertThat(
                                FsCrawlerUtil.isIndexable(false, virtualPath + "/" + filename, List.of(), excludes))
                        .as("Files below skipped directory %s are excluded", actualPath)
                        .isFalse();
                skipped++;
            }
            checked++;
        }
        Assertions.assertThat(checked)
                .as("Documented directory examples checked")
                .isGreaterThan(1);
        Assertions.assertThat(skipped)
                .as("The tree demonstrates an excluded directory")
                .isPositive();
    }

    private static String documentationExample(String name) throws IOException {
        // Maven sets basedir to the module directory. Walking upward also supports IDE runs from the checkout root.
        Path directory =
                Path.of(System.getProperty("basedir", ".")).toAbsolutePath().normalize();
        while (directory != null) {
            Path source = directory.resolve("docs/source/admin/fs/local-fs.md");
            if (Files.isRegularFile(source)) {
                String markdown = Files.readString(source);
                String startMarker = "<!-- test:includes-excludes-" + name + ":start -->";
                String endMarker = "<!-- test:includes-excludes-" + name + ":end -->";
                int start = markdown.indexOf(startMarker);
                Assertions.assertThat(start)
                        .as("Documentation marker %s", startMarker)
                        .isNotNegative();
                int end = markdown.indexOf(endMarker, start);
                Assertions.assertThat(end)
                        .as("Documentation marker %s", endMarker)
                        .isGreaterThan(start);
                return markdown.substring(start + startMarker.length(), end);
            }
            directory = directory.getParent();
        }
        throw new IOException("Cannot find docs/source/admin/fs/local-fs.md above the test working directory");
    }

    private static String fencedBlock(String example, String language) {
        Matcher block = Pattern.compile("(?s)```" + Pattern.quote(language) + "\\R(.*?)\\R```")
                .matcher(example);
        Assertions.assertThat(block.find())
                .as("A %s example exists in the marked documentation", language)
                .isTrue();
        return block.group(1);
    }
}
