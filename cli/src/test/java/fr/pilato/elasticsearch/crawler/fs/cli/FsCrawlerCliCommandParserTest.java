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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

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
    public void commandParserWithFullOptions() {
        String[] args = {
                "--config_dir", metadataDir.toString(),
                "--loop", "0",
                "--rest",
                "--upgrade",
                "--restart",
                "jobName"
        };
        FsCrawlerCli.FsCrawlerCommand command = FsCrawlerCli.commandParser(args);
        assertThat(command).isNotNull();
        assertThat(command.configDir).isEqualTo(metadataDir.toString());
        assertThat(command.loop).isZero();
        assertThat(command.rest).isTrue();
        assertThat(command.upgrade).isTrue();
        assertThat(command.restart).isTrue();
        assertThat(command.silent).isFalse();
        assertThat(command.jobName.get(0)).isEqualTo("jobName");
    }

    @Test
    public void commandParserForHelp() {
        String[] args = {
                "--help"
        };
        FsCrawlerCli.FsCrawlerCommand command = FsCrawlerCli.commandParser(args);
        assertThat(command).isNull();
    }

    @Test
    public void commandParserSilentModeNoJob() {
        String[] args = {
                    "--silent"
            };
        assertThatExceptionOfType(FsCrawlerIllegalConfigurationException.class).isThrownBy(() ->
                FsCrawlerCli.commandParser(args));
    }

    @Test
    public void commandParserSilentModeWithJob() {
        String[] args = {
                "--silent",
                "jobName"
        };
        FsCrawlerCli.FsCrawlerCommand command = FsCrawlerCli.commandParser(args);
        assertThat(command).isNotNull();
        assertThat(command.silent).isTrue();
        assertThat(command.jobName.get(0)).isEqualTo("jobName");
    }
}
