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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carrotsearch.randomizedtesting.jupiter.RandomizedTest;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerIllegalConfigurationException;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import java.nio.file.Files;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.yaml.snakeyaml.Yaml;

/** Settings must survive the real file loader and writer; malformed input must never silently use defaults. */
@org.junit.jupiter.api.parallel.Isolated("Changes system properties and temporarily captures warning logs")
class CheckpointSettingsTest extends AbstractFSCrawlerTestCase {
    @Test
    void absentAndEmptySectionsUseAllDefaults() throws Exception {
        defaults(FsSettingsLoader.load());
        defaults(load("fs:\n  checkpoint: {}\n"));
    }

    @ParameterizedTest
    @CsvSource({"max_directories,7", "max_operations,17", "max_bytes,8mb", "max_interval,45s"})
    void partialSectionOverridesOneFieldAndDefaultsTheRest(String key, String value) throws Exception {
        var values = checkpoint(load("fs:\n  checkpoint:\n    " + key + ": " + value));
        assertThat(values).hasSize(4);
        assertThat(number(values, "max_directories")).isEqualTo(key.equals("max_directories") ? 7 : 100);
        assertThat(number(values, "max_operations")).isEqualTo(key.equals("max_operations") ? 17 : 5000);
        assertThat(values.get("max_bytes").toString()).isEqualTo(key.equals("max_bytes") ? "8mb" : "32mb");
        assertThat(values.get("max_interval").toString()).isEqualTo(key.equals("max_interval") ? "45s" : "30s");
    }

    @Test
    void customYamlAndJsonRoundTripAndIgnoreBulkScaling() throws Exception {
        int directories = RandomizedTest.randomIntInRange(randomizedRandomForTests, 20, 80);
        int operations = RandomizedTest.randomIntInRange(randomizedRandomForTests, 500, 900);
        String yaml = "fs:\n  checkpoint:\n    max_directories: " + directories
                + "\n    max_operations: " + operations + "\n    max_bytes: 48mb\n    max_interval: 60s\n"
                + "elasticsearch:\n  bulk_size: 10000\n  byte_size: 100mb\n";
        var settings = load(yaml);
        var values = checkpoint(settings);
        assertThat(number(values, "max_directories")).isEqualTo(directories);
        assertThat(number(values, "max_operations")).isEqualTo(operations);
        assertThat(checkpoint(load(FsSettingsParser.toYaml(settings)))).isEqualTo(values);
        assertThat(load(FsSettingsParser.toYaml(settings))).isEqualTo(settings);
        var json = testTmpDir.resolve("checkpoint.json");
        Files.writeString(json, "{\"fs\":{\"checkpoint\":{\"max_operations\":" + operations + "}}}");
        assertThat(number(checkpoint(FsSettingsLoader.load(json)), "max_operations"))
                .isEqualTo(operations);
    }

    @ParameterizedTest
    @CsvSource({
        "max_directories,0",
        "max_directories,-1",
        "max_directories,1.5",
        "max_operations,0",
        "max_operations,-1",
        "max_operations,words",
        "max_operations,1.5",
        "max_bytes,0mb",
        "max_bytes,-1mb",
        "max_bytes,words",
        "max_bytes,NaNmb",
        "max_bytes,Infinitymb",
        "max_bytes,1e100mb",
        "max_bytes,9223372036854775807mb",
        "max_interval,0s",
        "max_interval,-1s",
        "max_interval,words",
        "max_interval,9223372036854775807d",
        "max_opertions,100",
        "max_operations,null"
    })
    void invalidValuesIdentifyTheSettingRatherThanFallingBack(String key, String value) {
        assertThatThrownBy(() -> load("fs:\n  checkpoint:\n    " + key + ": " + value))
                .isInstanceOf(FsCrawlerIllegalConfigurationException.class)
                .hasMessageContaining("fs.checkpoint." + key);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"null", "disabled", "[]"})
    void malformedSectionIsRejected(String section) {
        assertThatThrownBy(() -> load("fs:\n  checkpoint: " + section))
                .isInstanceOf(FsCrawlerIllegalConfigurationException.class)
                .hasMessageContaining("fs.checkpoint");
    }

    @Test
    void bulkChangesDoNotChangeOmittedCheckpointDefaults() throws Exception {
        defaults(load("elasticsearch:\n  bulk_size: 10000\n  byte_size: 100mb\n  flush_interval: 60s"));
    }

    @Test
    void splitFilesAndSystemPropertiesUseNormalPrecedence() throws Exception {
        String key = "fs.checkpoint.max_operations";
        String previous = System.getProperty(key);
        try {
            System.setProperty(key, "47");
            assertThat(number(checkpoint(FsSettingsLoader.load()), "max_operations"))
                    .isEqualTo(47);
            var first = testTmpDir.resolve("first.yaml");
            var second = testTmpDir.resolve("second.yaml");
            Files.writeString(first, "fs:\n  checkpoint:\n    max_directories: 7");
            Files.writeString(second, "fs:\n  checkpoint:\n    max_operations: 19");
            var values = checkpoint(FsSettingsLoader.load(first, second));
            assertThat(number(values, "max_directories")).isEqualTo(7);
            assertThat(number(values, "max_operations")).isEqualTo(19);
        } finally {
            if (previous == null) System.clearProperty(key);
            else System.setProperty(key, previous);
        }
    }

    @Test
    void loaderWarnsAboutPartialBulksWithoutChangingValidExplicitLimits() throws Exception {
        var output = new java.io.StringWriter();
        var context =
                (org.apache.logging.log4j.core.LoggerContext) org.apache.logging.log4j.LogManager.getContext(false);
        var configuration = context.getConfiguration();
        String name = "checkpoint-warnings-" + RandomizedTest.randomAsciiLettersOfLength(randomizedRandomForTests, 12);
        var appender = org.apache.logging.log4j.core.appender.WriterAppender.newBuilder()
                .setName(name)
                .setTarget(output)
                .setLayout(org.apache.logging.log4j.core.layout.PatternLayout.newBuilder()
                        .withPattern("%m%n")
                        .build())
                .build();
        appender.start();
        var logger = configuration.getLoggerConfig(FsSettingsLoader.class.getName());
        logger.addAppender(appender, org.apache.logging.log4j.Level.WARN, null);
        context.updateLoggers();
        try {
            defaults(FsSettingsLoader.load());
            assertThat(output.toString()).doesNotContain("fs.checkpoint");
            var values = checkpoint(
                    load("fs:\n  checkpoint:\n    max_operations: 1\n    max_bytes: 1b\n    max_interval: 1s"));
            assertThat(number(values, "max_operations")).isEqualTo(1);
            assertThat(output.toString())
                    .contains(
                            "fs.checkpoint.max_operations",
                            "fs.checkpoint.max_bytes",
                            "fs.checkpoint.max_interval",
                            "partial bulk");
        } finally {
            logger.removeAppender(name);
            appender.stop();
            context.updateLoggers();
        }
    }

    @Test
    void fractionalByteUnitsRemainSupported() throws Exception {
        var settings = load("fs:\n  checkpoint:\n    max_bytes: 0.5mb");
        assertThat(settings.getFs().getCheckpoint().getMaxBytes().getBytes()).isEqualTo(512 * 1024);
    }

    private FsSettings load(String content) throws Exception {
        var file = Files.createTempFile(testTmpDir, "checkpoint-", ".yaml");
        Files.writeString(file, content);
        return FsSettingsLoader.load(file);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> checkpoint(FsSettings settings) {
        Map<String, Object> root = new Yaml().load(FsSettingsParser.toYaml(settings));
        Map<String, Object> fs = (Map<String, Object>) root.get("fs");
        assertThat(fs).containsKey("checkpoint");
        return (Map<String, Object>) fs.get("checkpoint");
    }

    private long number(Map<String, Object> values, String key) {
        return ((Number) values.get(key)).longValue();
    }

    private void defaults(FsSettings settings) {
        var values = checkpoint(settings);
        assertThat(number(values, "max_directories")).isEqualTo(100);
        assertThat(number(values, "max_operations")).isEqualTo(5000);
        assertThat(values.get("max_bytes").toString()).isEqualTo("32mb");
        assertThat(values.get("max_interval").toString()).isEqualTo("30s");
    }
}
