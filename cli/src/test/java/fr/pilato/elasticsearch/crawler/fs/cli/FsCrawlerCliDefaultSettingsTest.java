/*
 * Licensed to David Pilato (the "Author") under one
 * or more contributor license agreements.  See the NOTICE file
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
 */

package fr.pilato.elasticsearch.crawler.fs.cli;

import fr.pilato.elasticsearch.crawler.fs.settings.*;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.copyDefaultResources;
import static fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsLoader.SETTINGS_YAML;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.nullValue;

/**
 * We want to test FSCrawler main app
 */
public class FsCrawlerCliDefaultSettingsTest extends AbstractFSCrawlerTestCase {
    private static final Logger logger = LogManager.getLogger();
    private static Path metadataDir;

    @BeforeClass
    public static void createFsCrawlerJobDir() throws IOException {
        // We also need to create default mapping files
        metadataDir = rootTmpDir.resolve(".fscrawler");
        if (Files.notExists(metadataDir)) {
            Files.createDirectory(metadataDir);
        }
        copyDefaultResources(metadataDir);
        logger.debug("  --> Test metadata dir ready in [{}]", metadataDir);
    }

    @Test
    public void testSettingsModifiedWithFtp() throws IOException {
        FsSettingsLoader fsSettingsLoader = new FsSettingsLoader(metadataDir);
        Path jobDir = metadataDir.resolve("modify_settings_server_ftp");
        Files.createDirectories(jobDir);

        Files.writeString(jobDir.resolve(SETTINGS_YAML), "name: \"modify_settings_server_ftp\"\n" +
                        "server:\n" +
                        "  hostname: \"mynode.mydomain.com\"\n" +
                        "  protocol: \"ftp\"\n"
                );
        FsSettings settings = fsSettingsLoader.read("modify_settings_server_ftp");
        assertThat(settings.getServer().getPort(), is(Server.PROTOCOL.FTP_PORT));
        assertThat(settings.getServer().getUsername(), is("anonymous"));
    }

    @Test
    public void testSettingsModifiedWithSsh() throws IOException {
        FsSettingsLoader fsSettingsLoader = new FsSettingsLoader(metadataDir);
        Path jobDir = metadataDir.resolve("modify_settings_server_ssh");
        Files.createDirectories(jobDir);

        Files.writeString(jobDir.resolve(SETTINGS_YAML), "name: \"modify_settings_server_ssh\"\n" +
                        "server:\n" +
                        "  hostname: \"mynode.mydomain.com\"\n" +
                        "  protocol: \"ssh\""
                );

        logger.info("{}", Files.readString(jobDir.resolve(SETTINGS_YAML)));

        FsSettings settings = fsSettingsLoader.read("modify_settings_server_ssh");
        assertThat(settings.getServer().getPort(), is(Server.PROTOCOL.SSH_PORT));
        assertThat(settings.getServer().getUsername(), nullValue());
    }
}
