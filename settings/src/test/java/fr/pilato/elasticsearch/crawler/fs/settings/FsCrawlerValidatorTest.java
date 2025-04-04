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

package fr.pilato.elasticsearch.crawler.fs.settings;

import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.INDEX_SUFFIX_FOLDER;
import static org.assertj.core.api.Assertions.assertThat;

public class FsCrawlerValidatorTest extends AbstractFSCrawlerTestCase {
    private static final Logger logger = LogManager.getLogger();

    @Test
    public void settingsValidation() {
        // Checking default values
        FsSettings settings = FsSettingsLoader.load();
        settings.setName(getCurrentTestName());
        settings.getElasticsearch().setIndex(getCurrentTestName());
        settings.getElasticsearch().setIndexFolder(getCurrentTestName() + INDEX_SUFFIX_FOLDER);

        assertThat(settings.getFs().getUrl()).isEqualTo(Defaults.DEFAULT_DIR);
        assertThat(settings.getElasticsearch().getNodes()).contains(Defaults.NODE_DEFAULT);
        assertThat(settings.getElasticsearch().getIndex()).isEqualTo(getCurrentTestName());
        assertThat(settings.getElasticsearch().getIndexFolder()).isEqualTo(getCurrentTestName() + INDEX_SUFFIX_FOLDER);
        assertThat(settings.getServer().getProtocol()).isEqualTo("local");
        assertThat(settings.getRest().getUrl()).isEqualTo("http://127.0.0.1:8080/fscrawler");

        // Checking Checksum Algorithm
        settings = FsSettingsLoader.load();
        settings.getFs().setChecksum("FSCRAWLER");
        assertThat(FsCrawlerValidator.validateSettings(logger, settings)).isTrue();

        // Checking protocol
        settings = FsSettingsLoader.load();
        settings.getServer().setProtocol("FSCRAWLER");
        assertThat(FsCrawlerValidator.validateSettings(logger, settings)).isTrue();

        // Checking username / password when SSH
        settings = FsSettingsLoader.load();
        settings.getServer().setProtocol(Server.PROTOCOL.SSH);
        assertThat(FsCrawlerValidator.validateSettings(logger, settings)).isTrue();

        // Checking username when FTP
        settings = FsSettingsLoader.load();
        settings.getServer().setProtocol(Server.PROTOCOL.FTP);
        assertThat(FsCrawlerValidator.validateSettings(logger, settings)).isFalse();

        // Checking username when FTP
        settings = FsSettingsLoader.load();
        settings.getServer().setProtocol(Server.PROTOCOL.FTP);
        settings.getServer().setUsername("");
        assertThat(FsCrawlerValidator.validateSettings(logger, settings)).isTrue();

        // Checking That we don't try to do both xml and json
        settings = FsSettingsLoader.load();
        settings.getFs().setJsonSupport(true);
        settings.getFs().setXmlSupport(true);
        assertThat(FsCrawlerValidator.validateSettings(logger, settings)).isTrue();

        // Checking That we don't try to do index xml with not indexing the content
        settings = FsSettingsLoader.load();
        settings.getFs().setIndexContent(false);
        settings.getFs().setXmlSupport(true);
        assertThat(FsCrawlerValidator.validateSettings(logger, settings)).isTrue();

        // Checking That we don't try to do index json with not indexing the content
        settings = FsSettingsLoader.load();
        settings.getFs().setIndexContent(false);
        settings.getFs().setJsonSupport(true);
        assertThat(FsCrawlerValidator.validateSettings(logger, settings)).isTrue();

        // Checking with Rest but no Rest settings
        settings = FsSettingsLoader.load();
        assertThat(FsCrawlerValidator.validateSettings(logger, settings)).isFalse();
        assertThat(settings.getRest()).isNotNull();
    }
}
