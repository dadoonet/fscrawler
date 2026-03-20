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

import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.filter.LevelMatchFilter;
import org.apache.logging.log4j.core.filter.LevelRangeFilter;
import org.assertj.core.api.Assertions;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/** We want to test FSCrawler main app */
public class FsCrawlerCliLoggerTest extends AbstractFSCrawlerTestCase {
    private static final Logger logger = LogManager.getLogger();
    private static Path metadataDir;

    @BeforeClass
    public static void createFsCrawlerJobDir() throws IOException {
        metadataDir = rootTmpDir.resolve(".fscrawler");
        if (Files.notExists(metadataDir)) {
            Files.createDirectory(metadataDir);
        }
        logger.debug("  --> Test metadata dir ready in [{}]", metadataDir);
    }

    @AfterClass
    public static void printMetadataDirContent() throws IOException {
        printLs(metadataDir);
    }

    private static void printLs(Path dir) throws IOException {
        logger.debug("ls -l {}", dir);
        Files.list(dir).forEach(path -> {
            if (Files.isDirectory(path)) {
                try {
                    printLs(path);
                } catch (IOException ignored) {
                }
            } else {
                logger.debug("{}", path);
            }
        });
    }

    /**
     * We want to make sure that we can run several times the same test without having any error due to the fact that
     * the logger context has already been initialized.
     */
    @Before
    public void resetLogger() {
        FsCrawlerCli.reinitLoggerContext();
    }

    @Test
    public void changeLoggerContextForDebug() {
        String[] args = {"--debug", "jobName"};
        FsCrawlerCli.FsCrawlerCommand command = FsCrawlerCli.commandParser(args);
        Assertions.assertThat(command).isNotNull();
        FsCrawlerCli.changeLoggerContext(command);

        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        LoggerConfig loggerConfig = config.getLoggerConfig("fr.pilato.elasticsearch.crawler.fs");
        Assertions.assertThat(loggerConfig).isNotNull();
        Assertions.assertThat(loggerConfig.getLevel()).isEqualTo(Level.DEBUG);

        ConsoleAppender console = config.getAppender("Console");
        Assertions.assertThat(console).isNotNull();
        Assertions.assertThat(console.getFilter()).isInstanceOf(LevelRangeFilter.class);
        LevelRangeFilter filter = (LevelRangeFilter) console.getFilter();
        Assertions.assertThat(filter.getMinLevel()).isEqualTo(Level.TRACE);
        Assertions.assertThat(filter.getMaxLevel()).isEqualTo(Level.ALL);
    }

    @Test
    public void changeLoggerContextForTrace() {
        String[] args = {"--trace", "jobName"};
        FsCrawlerCli.FsCrawlerCommand command = FsCrawlerCli.commandParser(args);
        Assertions.assertThat(command).isNotNull();
        FsCrawlerCli.changeLoggerContext(command);

        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        LoggerConfig loggerConfig = config.getLoggerConfig("fr.pilato.elasticsearch.crawler.fs");
        Assertions.assertThat(loggerConfig).isNotNull();
        Assertions.assertThat(loggerConfig.getLevel()).isEqualTo(Level.TRACE);

        ConsoleAppender console = config.getAppender("Console");
        Assertions.assertThat(console).isNotNull();
        Assertions.assertThat(console.getFilter()).isInstanceOf(LevelRangeFilter.class);
        LevelRangeFilter filter = (LevelRangeFilter) console.getFilter();
        Assertions.assertThat(filter.getMinLevel()).isEqualTo(Level.ALL);
        Assertions.assertThat(filter.getMaxLevel()).isEqualTo(Level.ALL);
    }

    @Test
    public void changeLoggerContextForSilent() {
        String[] args = {"--silent", "jobName"};
        FsCrawlerCli.FsCrawlerCommand command = FsCrawlerCli.commandParser(args);
        Assertions.assertThat(command).isNotNull();
        FsCrawlerCli.changeLoggerContext(command);

        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        LoggerConfig loggerConfig = config.getLoggerConfig("fr.pilato.elasticsearch.crawler.fs");
        Assertions.assertThat(loggerConfig).isNotNull();
        Assertions.assertThat(loggerConfig.getLevel()).isEqualTo(Level.TRACE);

        ConsoleAppender console = config.getAppender("Console");
        Assertions.assertThat(console).isNotNull();
        Assertions.assertThat(console.getFilter()).isInstanceOf(LevelMatchFilter.class);
        LevelMatchFilter filter = (LevelMatchFilter) console.getFilter();
        Assertions.assertThat(filter.getOnMatch()).isEqualTo(Filter.Result.DENY);
    }

    @Test
    public void changeLoggerContextByDefault() {
        String[] args = {"jobName"};
        FsCrawlerCli.FsCrawlerCommand command = FsCrawlerCli.commandParser(args);
        Assertions.assertThat(command).isNotNull();
        FsCrawlerCli.changeLoggerContext(command);

        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        LoggerConfig loggerConfig = config.getLoggerConfig("fr.pilato.elasticsearch.crawler.fs");
        Assertions.assertThat(loggerConfig).isNotNull();
        Assertions.assertThat(loggerConfig.getLevel()).isEqualTo(Level.INFO);

        ConsoleAppender console = config.getAppender("Console");
        Assertions.assertThat(console).isNotNull();
        Assertions.assertThat(console.getFilter()).isNull();
    }
}
