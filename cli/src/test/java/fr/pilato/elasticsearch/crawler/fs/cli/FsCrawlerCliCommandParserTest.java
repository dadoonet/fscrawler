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

import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerIllegalConfigurationException;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * We want to test FSCrawler main app
 */
public class FsCrawlerCliCommandParserTest extends AbstractFSCrawlerTestCase {

    private static Path metadataDir;

    @BeforeClass
    public static void createFsCrawlerJobDir() {
        metadataDir = rootTmpDir.resolve(".fscrawler");
    }

    @Test
    public void testCommandParserWithFullOptions() {
        String[] args = {
                "--config_dir", metadataDir.toString(),
                "--loop", "0",
                "--username", "dadoonet",
                "--rest",
                "--upgrade",
                "--restart",
                "--debug",
                "jobName"
        };
        FsCrawlerCli.FsCrawlerCommand command = FsCrawlerCli.commandParser(args);
        assertThat(command, notNullValue());
        assertThat(command.configDir, is(metadataDir.toString()));
        assertThat(command.loop, is(0));
        assertThat(command.username, is("dadoonet"));
        assertThat(command.rest, is(true));
        assertThat(command.upgrade, is(true));
        assertThat(command.restart, is(true));
        assertThat(command.debug, is(true));
        assertThat(command.trace, is(false));
        assertThat(command.silent, is(false));
        assertThat(command.jobName.get(0), is("jobName"));
    }

    @Test
    public void testCommandParserForHelp() {
        String[] args = {
                "--help"
        };
        FsCrawlerCli.FsCrawlerCommand command = FsCrawlerCli.commandParser(args);
        assertThat(command, nullValue());
    }

    @Test(expected = FsCrawlerIllegalConfigurationException.class)
    public void testCommandParserSilentModeNoJob() {
        String[] args = {
                "--silent"
        };
        FsCrawlerCli.commandParser(args);
    }

    @Test
    public void testCommandParserSilentModeWithJob() {
        String[] args = {
                "--silent",
                "jobName"
        };
        FsCrawlerCli.FsCrawlerCommand command = FsCrawlerCli.commandParser(args);
        assertThat(command, notNullValue());
        assertThat(command.silent, is(true));
        assertThat(command.jobName.get(0), is("jobName"));
    }
}
