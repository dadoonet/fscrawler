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
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class FsProviderSettingsTest extends AbstractFSCrawlerTestCase {

    @AfterEach
    void cleanupSystemProperties() {
        System.clearProperty("fs.ssh.hostname");
        System.clearProperty("fs.ssh.port");
        System.clearProperty("fs.ftp.username");
    }

    @Test
    void loadsSshProviderConfigFromFsBlock() throws Exception {
        String hostname = randomHostname();
        int port = RandomizedTest.randomIntInRange(randomizedRandomForTests, 1024, 65535);
        String username = randomToken();
        String password = randomToken();
        String pemName = randomToken() + ".pem";

        FsSettings settings = loadJobYaml("""
                name: "%s"
                fs:
                  provider: "ssh"
                  url: "/docs"
                  ssh:
                    hostname: "%s"
                    port: %d
                    username: "%s"
                    password: "%s"
                    pem_path: "/keys/%s"
                """.formatted(jobName, hostname, port, username, password, pemName));

        Assertions.assertThat(settings.getFs().getProvider()).isEqualTo("ssh");
        Map<String, Object> ssh = settings.getFs().getProviderConfig("ssh");
        Assertions.assertThat(ssh)
                .isNotNull()
                .containsEntry("hostname", hostname)
                .containsEntry("username", username)
                .containsEntry("password", password)
                .containsEntry("pem_path", "/keys/" + pemName);
        Assertions.assertThat(((Number) ssh.get("port")).intValue()).isEqualTo(port);
        Assertions.assertThat(settings.getFs().getProviderConfig("ftp")).isNull();
    }

    @Test
    void loadsFtpProviderConfigFromFsBlock() throws Exception {
        String hostname = randomHostname();
        int port = RandomizedTest.randomIntInRange(randomizedRandomForTests, 1024, 65535);
        String username = randomToken();
        String password = randomToken();

        FsSettings settings = loadJobYaml("""
                name: "%s"
                fs:
                  provider: "ftp"
                  url: "/pub"
                  ftp:
                    hostname: "%s"
                    port: %d
                    username: "%s"
                    password: "%s"
                """.formatted(jobName, hostname, port, username, password));

        Assertions.assertThat(settings.getFs().getProvider()).isEqualTo("ftp");
        Map<String, Object> ftp = settings.getFs().getProviderConfig("ftp");
        Assertions.assertThat(ftp)
                .isNotNull()
                .containsEntry("hostname", hostname)
                .containsEntry("username", username)
                .containsEntry("password", password);
        Assertions.assertThat(((Number) ftp.get("port")).intValue()).isEqualTo(port);
        Assertions.assertThat(settings.getFs().getProviderConfig("ssh")).isNull();
    }

    @Test
    void overlaysSshHostnameFromSystemPropertyWhenYamlOmitsIt() throws Exception {
        String hostname = randomHostname();
        System.setProperty("fs.ssh.hostname", hostname);

        FsSettings settings = loadJobYaml("""
                name: "%s"
                fs:
                  provider: "ssh"
                  url: "/docs"
                  ssh:
                    username: "jobuser"
                """.formatted(jobName));

        Map<String, Object> ssh = settings.getFs().getProviderConfig("ssh");
        Assertions.assertThat(ssh)
                .isNotNull()
                .containsEntry("hostname", hostname)
                .containsEntry("username", "jobuser");
    }

    @Test
    void yamlSshHostnameWinsOverSystemProperty() throws Exception {
        String yamlHostname = randomHostname();
        System.setProperty("fs.ssh.hostname", "ignored-" + randomHostname());

        FsSettings settings = loadJobYaml("""
                name: "%s"
                fs:
                  provider: "ssh"
                  ssh:
                    hostname: "%s"
                """.formatted(jobName, yamlHostname));

        Assertions.assertThat(settings.getFs().getProviderConfig("ssh")).containsEntry("hostname", yamlHostname);
    }

    private FsSettings loadJobYaml(String yaml) throws Exception {
        Path configRoot = Files.createDirectories(rootTmpDir.resolve("config-" + randomToken()));
        Path jobDir = Files.createDirectories(configRoot.resolve(jobName));
        Files.writeString(jobDir.resolve(FsSettingsLoader.SETTINGS_YAML), yaml);
        return new FsSettingsLoader(configRoot).read(jobName);
    }

    private String randomHostname() {
        return randomToken() + ".example.com";
    }

    private String randomToken() {
        return RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 6, 12)
                .toLowerCase(Locale.ROOT);
    }
}
