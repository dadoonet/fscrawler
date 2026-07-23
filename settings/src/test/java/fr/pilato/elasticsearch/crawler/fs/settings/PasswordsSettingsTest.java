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

import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

@Execution(SAME_THREAD)
class PasswordsSettingsTest extends AbstractFSCrawlerTestCase {

    @Test
    void passwordsStaticValuesLoadedAsOpaqueProviderMap() throws Exception {
        Path configRoot = Files.createDirectories(rootTmpDir.resolve("config"));
        Path jobDir = Files.createDirectories(configRoot.resolve("passwords-static"));
        try (InputStream fixture =
                PasswordsSettingsTest.class.getResourceAsStream("/config/passwords-static/_settings.yaml")) {
            Assertions.assertThat(fixture).isNotNull();
            Files.copy(fixture, jobDir.resolve("_settings.yaml"), StandardCopyOption.REPLACE_EXISTING);
        }

        FsSettings settings = new FsSettingsLoader(configRoot).read("passwords-static");

        Assertions.assertThat(settings.getPasswords()).isNotNull();
        Assertions.assertThat(settings.getPasswords().getProvider()).isEqualTo("static");
        Assertions.assertThat(settings.getPasswords().getProviders())
                .as("opaque passwords.providers map")
                .isNotNull()
                .containsKey("static");
        Map<String, Object> staticConfig = settings.getPasswords().getProviderConfig("static");
        Assertions.assertThat(staticConfig).isNotNull();
        @SuppressWarnings("unchecked")
        List<String> values = (List<String>) staticConfig.get("values");
        Assertions.assertThat(values).containsExactly("alpha", "beta");
    }
}
