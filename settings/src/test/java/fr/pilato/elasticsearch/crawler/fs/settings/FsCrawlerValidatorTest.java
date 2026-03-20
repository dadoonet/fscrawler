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

import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public class FsCrawlerValidatorTest extends AbstractFSCrawlerTestCase {
    private static final Logger logger = LogManager.getLogger();

    @Test
    public void settingsValidation() {
        // Checking default values
        FsSettings settings = FsSettingsLoader.load();
        settings.setName(getCurrentTestName());
        settings.getElasticsearch().setIndex(getCurrentTestName() + FsCrawlerUtil.INDEX_SUFFIX_DOCS);
        settings.getElasticsearch().setIndexFolder(getCurrentTestName() + FsCrawlerUtil.INDEX_SUFFIX_FOLDER);

        Assertions.assertThat(settings.getFs().getUrl()).isEqualTo(Defaults.DEFAULT_DIR);
        Assertions.assertThat(settings.getElasticsearch().getUrls()).contains(Defaults.ELASTICSEARCH_URL_DEFAULT);
        Assertions.assertThat(settings.getElasticsearch().getIndex())
                .isEqualTo(getCurrentTestName() + FsCrawlerUtil.INDEX_SUFFIX_DOCS);
        Assertions.assertThat(settings.getElasticsearch().getIndexFolder())
                .isEqualTo(getCurrentTestName() + FsCrawlerUtil.INDEX_SUFFIX_FOLDER);
        Assertions.assertThat(settings.getServer().getProtocol()).isEqualTo("local");
        Assertions.assertThat(settings.getRest().getUrl()).isEqualTo("http://127.0.0.1:8080");

        // Checking Checksum Algorithm
        settings = FsSettingsLoader.load();
        settings.getFs().setChecksum("FSCRAWLER");
        Assertions.assertThat(FsCrawlerValidator.validateSettings(logger, settings))
                .isTrue();

        // Checking protocol
        settings = FsSettingsLoader.load();
        settings.getServer().setProtocol("FSCRAWLER");
        Assertions.assertThat(FsCrawlerValidator.validateSettings(logger, settings))
                .isTrue();

        // Checking username / password when SSH
        settings = FsSettingsLoader.load();
        settings.getServer().setProtocol(Server.PROTOCOL.SSH);
        Assertions.assertThat(FsCrawlerValidator.validateSettings(logger, settings))
                .isTrue();

        // Checking username when FTP
        settings = FsSettingsLoader.load();
        settings.getServer().setProtocol(Server.PROTOCOL.FTP);
        Assertions.assertThat(FsCrawlerValidator.validateSettings(logger, settings))
                .isFalse();

        // Checking username when FTP
        settings = FsSettingsLoader.load();
        settings.getServer().setProtocol(Server.PROTOCOL.FTP);
        settings.getServer().setUsername("");
        Assertions.assertThat(FsCrawlerValidator.validateSettings(logger, settings))
                .isTrue();

        // Checking That we don't try to do both xml and json
        settings = FsSettingsLoader.load();
        settings.getFs().setJsonSupport(true);
        settings.getFs().setXmlSupport(true);
        Assertions.assertThat(FsCrawlerValidator.validateSettings(logger, settings))
                .isTrue();

        // Checking That we don't try to do index xml with not indexing the content
        settings = FsSettingsLoader.load();
        settings.getFs().setIndexContent(false);
        settings.getFs().setXmlSupport(true);
        Assertions.assertThat(FsCrawlerValidator.validateSettings(logger, settings))
                .isTrue();

        // Checking That we don't try to do index json with not indexing the content
        settings = FsSettingsLoader.load();
        settings.getFs().setIndexContent(false);
        settings.getFs().setJsonSupport(true);
        Assertions.assertThat(FsCrawlerValidator.validateSettings(logger, settings))
                .isTrue();

        // Checking with Rest but no Rest settings
        settings = FsSettingsLoader.load();
        Assertions.assertThat(FsCrawlerValidator.validateSettings(logger, settings))
                .isFalse();
        Assertions.assertThat(settings.getRest()).isNotNull();
    }
}
