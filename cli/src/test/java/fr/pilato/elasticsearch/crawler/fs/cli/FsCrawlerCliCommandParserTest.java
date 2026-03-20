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
package fr.pilato.elasticsearch.crawler.fs.cli;

import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerIllegalConfigurationException;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import java.nio.file.Path;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.BeforeClass;
import org.junit.Test;

/** We want to test FSCrawler main app */
public class FsCrawlerCliCommandParserTest extends AbstractFSCrawlerTestCase {

    private static Path metadataDir;

    @BeforeClass
    public static void createFsCrawlerJobDir() {
        metadataDir = rootTmpDir.resolve(".fscrawler");
    }

    @Test
    public void commandParserWithFullOptions() {
        String[] args = {
            "--config_dir", metadataDir.toString(), "--loop", "0", "--rest", "--upgrade", "--restart", "jobName"
        };
        FsCrawlerCli.FsCrawlerCommand command = FsCrawlerCli.commandParser(args);
        Assertions.assertThat(command).isNotNull();
        Assertions.assertThat(command.configDir).isEqualTo(metadataDir.toString());
        Assertions.assertThat(command.loop).isZero();
        Assertions.assertThat(command.rest).isTrue();
        Assertions.assertThat(command.upgrade).isTrue();
        Assertions.assertThat(command.restart).isTrue();
        Assertions.assertThat(command.silent).isFalse();
        Assertions.assertThat(command.jobName.get(0)).isEqualTo("jobName");
    }

    @Test
    public void commandParserForHelp() {
        String[] args = {"--help"};
        FsCrawlerCli.FsCrawlerCommand command = FsCrawlerCli.commandParser(args);
        Assertions.assertThat(command).isNull();
    }

    @Test
    public void commandParserSilentModeNoJob() {
        String[] args = {"--silent"};
        AssertionsForClassTypes.assertThatExceptionOfType(FsCrawlerIllegalConfigurationException.class)
                .isThrownBy(() -> FsCrawlerCli.commandParser(args));
    }

    @Test
    public void commandParserSilentModeWithJob() {
        String[] args = {"--silent", "jobName"};
        FsCrawlerCli.FsCrawlerCommand command = FsCrawlerCli.commandParser(args);
        Assertions.assertThat(command).isNotNull();
        Assertions.assertThat(command.silent).isTrue();
        Assertions.assertThat(command.jobName.get(0)).isEqualTo("jobName");
    }
}
