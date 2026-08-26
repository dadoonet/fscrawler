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
package fr.pilato.elasticsearch.crawler.plugins;

import com.carrotsearch.randomizedtesting.jupiter.RandomizedTest;
import com.jayway.jsonpath.DocumentContext;
import fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsLoader;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

@SuppressWarnings("removal")
class RemoteConnectionSettingsTest extends AbstractFSCrawlerTestCase {

    @Test
    void prefersJobFsSshOverDeprecatedServer() {
        String newHost = randomHostname();
        String oldHost = "old-" + randomHostname();
        int newPort = RandomizedTest.randomIntInRange(randomizedRandomForTests, 2000, 3000);
        int oldPort = RandomizedTest.randomIntInRange(randomizedRandomForTests, 4000, 5000);
        String username = randomToken();
        String password = randomToken();
        String pemPath = "/keys/" + randomToken() + ".pem";

        FsSettings settings = FsSettingsLoader.load();
        settings.getFs().setSsh(sshConfig(newHost, newPort, username, password, pemPath));
        settings.getServer().setHostname(oldHost);
        settings.getServer().setPort(oldPort);
        settings.getServer().setUsername("legacy-user");
        settings.getServer().setPassword("legacy-pass");
        settings.getServer().setPemPath("/legacy.pem");

        RemoteConnectionSettings resolved = RemoteConnectionSettings.resolve("ssh", null, settings, 22, null);

        Assertions.assertThat(resolved.hostname()).isEqualTo(newHost);
        Assertions.assertThat(resolved.port()).isEqualTo(newPort);
        Assertions.assertThat(resolved.username()).isEqualTo(username);
        Assertions.assertThat(resolved.password()).isEqualTo(password);
        Assertions.assertThat(resolved.pemPath()).isEqualTo(pemPath);
        Assertions.assertThat(resolved.deprecationWarnings())
                .anyMatch(msg ->
                        msg.contains("server.hostname") && msg.contains("fs.ssh.hostname") && msg.contains("ignored"))
                .anyMatch(msg -> msg.contains("server.port") && msg.contains("fs.ssh.port") && msg.contains("ignored"))
                .anyMatch(msg -> msg.contains("server.username") && msg.contains("ignored"))
                .anyMatch(msg ->
                        msg.contains("server.password") && msg.contains("ignored") && !msg.contains("legacy-pass"))
                .anyMatch(msg -> msg.contains("server.pem_path") && msg.contains("ignored"));
    }

    @Test
    void fallsBackToServerWithMigrationHint() {
        String hostname = randomHostname();
        int port = RandomizedTest.randomIntInRange(randomizedRandomForTests, 1024, 65535);
        String username = randomToken();
        String password = randomToken();
        String pemPath = "/keys/" + randomToken() + ".pem";

        FsSettings settings = FsSettingsLoader.load();
        settings.getServer().setHostname(hostname);
        settings.getServer().setPort(port);
        settings.getServer().setUsername(username);
        settings.getServer().setPassword(password);
        settings.getServer().setPemPath(pemPath);

        RemoteConnectionSettings resolved = RemoteConnectionSettings.resolve("ssh", null, settings, 22, null);

        Assertions.assertThat(resolved.hostname()).isEqualTo(hostname);
        Assertions.assertThat(resolved.port()).isEqualTo(port);
        Assertions.assertThat(resolved.username()).isEqualTo(username);
        Assertions.assertThat(resolved.password()).isEqualTo(password);
        Assertions.assertThat(resolved.pemPath()).isEqualTo(pemPath);
        Assertions.assertThat(resolved.deprecationWarnings())
                .anyMatch(msg -> msg.equals(
                        "Setting server.hostname is deprecated and will be removed in a future version. Please use fs.ssh.hostname: \""
                                + hostname + "\" instead."))
                .anyMatch(msg -> msg.equals(
                        "Setting server.port is deprecated and will be removed in a future version. Please use fs.ssh.port: "
                                + port + " instead."))
                .anyMatch(msg -> msg.equals(
                        "Setting server.username is deprecated and will be removed in a future version. Please use fs.ssh.username: \""
                                + username + "\" instead."))
                .anyMatch(
                        msg -> msg.equals(
                                "Setting server.password is deprecated and will be removed in a future version. Please use fs.ssh.password instead."))
                .anyMatch(msg -> msg.equals(
                        "Setting server.pem_path is deprecated and will be removed in a future version. Please use fs.ssh.pem_path: \""
                                + pemPath + "\" instead."));
    }

    @Test
    void restJsonOverridesJobFsSsh() {
        String restHost = randomHostname();
        String jobHost = "job-" + randomHostname();
        String path = "/" + randomToken() + ".pdf";

        FsSettings settings = FsSettingsLoader.load();
        settings.getFs().setSsh(sshConfig(jobHost, 22, "job-user", "job-pass", null));

        DocumentContext restJson = JsonUtil.parseJsonAsDocumentContext("""
                {"type":"ssh","ssh":{"hostname":"%s","path":"%s"}}
                """.formatted(restHost, path));

        RemoteConnectionSettings resolved = RemoteConnectionSettings.resolve("ssh", restJson, settings, 22, null);

        Assertions.assertThat(resolved.hostname()).isEqualTo(restHost);
        Assertions.assertThat(resolved.username()).isEqualTo("job-user");
        Assertions.assertThat(resolved.remotePath()).isEqualTo(path);
        Assertions.assertThat(resolved.deprecationWarnings()).isEmpty();
    }

    @Test
    void appliesDefaultPortAndFtpAnonymousUsername() {
        String hostname = randomHostname();
        FsSettings settings = FsSettingsLoader.load();
        settings.getFs().setFtp(Map.of("hostname", hostname));

        RemoteConnectionSettings resolved = RemoteConnectionSettings.resolve("ftp", null, settings, 21, "anonymous");

        Assertions.assertThat(resolved.hostname()).isEqualTo(hostname);
        Assertions.assertThat(resolved.port()).isEqualTo(21);
        Assertions.assertThat(resolved.username()).isEqualTo("anonymous");
        Assertions.assertThat(resolved.deprecationWarnings()).isEmpty();
    }

    @Test
    void doesNotWarnOnDefaultLocalServer() {
        String hostname = randomHostname();
        FsSettings settings = FsSettingsLoader.load();
        settings.getFs().setSsh(Map.of("hostname", hostname, "username", "user"));

        RemoteConnectionSettings resolved = RemoteConnectionSettings.resolve("ssh", null, settings, 22, null);

        Assertions.assertThat(resolved.hostname()).isEqualTo(hostname);
        Assertions.assertThat(resolved.deprecationWarnings()).isEmpty();
    }

    private static Map<String, Object> sshConfig(
            String hostname, int port, String username, String password, String pemPath) {
        Map<String, Object> ssh = new LinkedHashMap<>();
        ssh.put("hostname", hostname);
        ssh.put("port", port);
        ssh.put("username", username);
        ssh.put("password", password);
        if (pemPath != null) {
            ssh.put("pem_path", pemPath);
        }
        return ssh;
    }

    private String randomHostname() {
        return randomToken() + ".example.com";
    }

    private String randomToken() {
        return RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 6, 12)
                .toLowerCase(Locale.ROOT);
    }
}
