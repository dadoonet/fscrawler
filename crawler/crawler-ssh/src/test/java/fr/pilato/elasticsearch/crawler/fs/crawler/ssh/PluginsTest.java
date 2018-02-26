/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

package fr.pilato.elasticsearch.crawler.fs.crawler.ssh;

import fr.pilato.elasticsearch.crawler.fs.crawler.FsParserAbstract;
import fr.pilato.elasticsearch.crawler.fs.crawler.Plugins;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.Server;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import org.junit.Test;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.instanceOf;

public class PluginsTest extends AbstractFSCrawlerTestCase {
    @Test
    public void testClassIsLoaded() {
        Plugins.registerPlugins();
        Map<String, Class<? extends FsParserAbstract>> plugins = Plugins.getPlugins();
        assertThat(plugins, hasKey(FsParserSsh.NAME));

        // We try with explicit settings
        FsParserAbstract parser = Plugins.createParser(
                FsSettings.builder("foo").setServer(Server.builder().setProtocol(FsParserSsh.NAME).build()).build(),
                rootTmpDir, null, 0);
        assertThat(parser, instanceOf(FsParserSsh.class));
    }
}
