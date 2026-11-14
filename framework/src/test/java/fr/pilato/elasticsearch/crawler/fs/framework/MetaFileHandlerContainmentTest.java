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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MetaFileHandlerContainmentTest extends AbstractFSCrawlerTestCase {
    @ParameterizedTest
    @ValueSource(strings = {"../outside", "..\\outside", ".", "..", "", " ", "invalid:job", "nested/job"})
    void rejectsUnsafeJobNamesForEveryOperation(String name) throws Exception {
        Handler handler = new Handler(Files.createDirectory(rootTmpDir.resolve("metadata")));
        Assertions.assertThatThrownBy(() -> handler.writeFile(name, "_checkpoint.json", "{}"))
                .isInstanceOf(IOException.class);
        Assertions.assertThatThrownBy(() -> handler.readFile(name, "_checkpoint.json"))
                .isInstanceOf(IOException.class);
        Assertions.assertThatThrownBy(() -> handler.removeFile(name, "_checkpoint.json"))
                .isInstanceOf(IOException.class);
        Assertions.assertThat(Files.exists(rootTmpDir.resolve("outside"))).isFalse();
    }

    @Test
    void validJobAndRootLevelFilesRemainUsable() throws Exception {
        Handler handler = new Handler(rootTmpDir);
        String name = RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 5, 15);
        String content = RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 5, 30);
        handler.writeFile(name, "_checkpoint.json", content);
        Assertions.assertThat(handler.readFile(name, "_checkpoint.json")).isEqualTo(content);
        handler.removeFile(name, "_checkpoint.json");
        Assertions.assertThat(Files.exists(rootTmpDir.resolve(name).resolve("_checkpoint.json")))
                .isFalse();
        handler.writeFile(null, "root.json", content);
        Assertions.assertThat(handler.readFile(null, "root.json")).isEqualTo(content);
    }

    @Test
    void rejectsAbsoluteJobPathInsideControlledTemporaryDirectory() throws Exception {
        Handler handler = new Handler(Files.createDirectory(rootTmpDir.resolve("metadata")));
        Path outside = rootTmpDir.resolve("absolute-job");
        Assertions.assertThatThrownBy(() -> handler.writeFile(outside.toString(), "_checkpoint.json", "{}"))
                .isInstanceOf(IOException.class);
        Assertions.assertThat(Files.exists(outside)).isFalse();
    }

    @Test
    void refusesSymlinkedJobDirectory() throws Exception {
        Path outside = Files.createDirectory(rootTmpDir.resolve("outside"));
        Path root = Files.createDirectory(rootTmpDir.resolve("metadata"));
        try {
            Files.createSymbolicLink(root.resolve("linked"), outside);
        } catch (IOException | UnsupportedOperationException e) {
            Assumptions.abort("Symbolic links unavailable: " + e.getMessage());
        }
        Files.writeString(outside.resolve("_checkpoint.json"), "authoritative");
        Handler handler = new Handler(root);
        Assertions.assertThatThrownBy(() -> handler.writeFile("linked", "_checkpoint.json", "replacement"))
                .isInstanceOf(IOException.class);
        Assertions.assertThatThrownBy(() -> handler.readFile("linked", "_checkpoint.json"))
                .isInstanceOf(IOException.class);
        Assertions.assertThatThrownBy(() -> handler.removeFile("linked", "_checkpoint.json"))
                .isInstanceOf(IOException.class);
        Assertions.assertThat(Files.readString(outside.resolve("_checkpoint.json")))
                .isEqualTo("authoritative");
    }

    private static class Handler extends MetaFileHandler {
        Handler(Path root) {
            super(root);
        }
    }
}
