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

package fr.pilato.elasticsearch.crawler.fs.test.unit;

import fr.pilato.elasticsearch.crawler.fs.FsCrawlerImpl;
import fr.pilato.elasticsearch.crawler.fs.FsCrawlerValidator;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.Elasticsearch;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.Fs;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.Rest;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.Server;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import org.junit.Test;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.INDEX_SUFFIX_FOLDER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

public class FsCrawlerValidatorTest extends AbstractFSCrawlerTestCase {

    @Test
    public void testSettingsValidation() {
        // Checking default values
        FsSettings settings = buildSettings(Fs.builder().build(), null, null, null);
        assertThat(settings.getFs().getUrl(), nullValue());
        assertThat(FsCrawlerValidator.validateSettings(logger, settings, false), is(false));
        assertThat(settings.getFs().getUrl(), is(Fs.DEFAULT_DIR));
        assertThat(settings.getElasticsearch().getNodes(), hasItem(Elasticsearch.Node.DEFAULT));
        assertThat(settings.getElasticsearch().getIndex(), is(getCurrentTestName()));
        assertThat(settings.getElasticsearch().getIndexFolder(), is(getCurrentTestName() + INDEX_SUFFIX_FOLDER));
        assertThat(settings.getServer(), nullValue());

        // Checking default values
        settings = buildSettings(null, null, null, null);
        assertThat(settings.getFs().getUrl(), is(Fs.DEFAULT_DIR));
        assertThat(FsCrawlerValidator.validateSettings(logger, settings, false), is(false));
        assertThat(settings.getElasticsearch().getNodes(), hasItem(Elasticsearch.Node.DEFAULT));
        assertThat(settings.getElasticsearch().getIndex(), is(getCurrentTestName()));
        assertThat(settings.getElasticsearch().getIndexFolder(), is(getCurrentTestName() + INDEX_SUFFIX_FOLDER));
        assertThat(settings.getServer(), nullValue());

        // Checking Checksum Algorithm
        settings = buildSettings(Fs.builder().setChecksum("FSCRAWLER").build(), null, null, null);
        assertThat(FsCrawlerValidator.validateSettings(logger, settings, false), is(true));

        // Checking protocol
        settings = buildSettings(null, null, Server.builder().setProtocol("FSCRAWLER").build(), null);
        assertThat(FsCrawlerValidator.validateSettings(logger, settings, false), is(true));

        // Checking username / password when SSH
        settings = buildSettings(null, null, Server.builder().setProtocol(FsCrawlerImpl.PROTOCOL.SSH).build(), null);
        assertThat(FsCrawlerValidator.validateSettings(logger, settings, false), is(true));

        // Checking That we don't try to do both xml and json
        settings = buildSettings(Fs.builder().setJsonSupport(true).setXmlSupport(true).build(), null, null, null);
        assertThat(FsCrawlerValidator.validateSettings(logger, settings, false), is(true));

        // Checking That we don't try to do index xml with not indexing the content
        settings = buildSettings(Fs.builder().setIndexContent(false).setXmlSupport(true).build(), null, null, null);
        assertThat(FsCrawlerValidator.validateSettings(logger, settings, false), is(true));

        // Checking That we don't try to do index json with not indexing the content
        settings = buildSettings(Fs.builder().setIndexContent(false).setJsonSupport(true).build(), null, null, null);
        assertThat(FsCrawlerValidator.validateSettings(logger, settings, false), is(true));

        // Checking with Rest but no Rest settings
        settings = buildSettings(null, null, null, null);
        assertThat(FsCrawlerValidator.validateSettings(logger, settings, true), is(false));
        assertThat(settings.getRest(), notNullValue());
    }

    private FsSettings buildSettings(Fs fs, Elasticsearch elasticsearch, Server server, Rest rest) {
        FsSettings.Builder settingsBuilder = FsSettings.builder(getCurrentTestName());
        settingsBuilder.setFs(fs == null ? Fs.DEFAULT : fs);
        settingsBuilder.setElasticsearch(elasticsearch == null ? Elasticsearch.DEFAULT() : elasticsearch);
        settingsBuilder.setServer(server);
        settingsBuilder.setRest(rest);

        return settingsBuilder.build();
    }
}
