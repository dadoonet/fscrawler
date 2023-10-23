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

import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.filter.LevelMatchFilter;
import org.apache.logging.log4j.core.filter.LevelRangeFilter;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static fr.pilato.elasticsearch.crawler.fs.cli.FsCrawlerCli.*;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.copyDefaultResources;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * We want to test FSCrawler main app
 */
public class FsCrawlerCliLoggerTest extends AbstractFSCrawlerTestCase {

    private static Path metadataDir;

    @BeforeClass
    public static void createFsCrawlerJobDir() throws IOException {
        // We also need to create default mapping files
        metadataDir = rootTmpDir.resolve(".fscrawler");
        if (Files.notExists(metadataDir)) {
            Files.createDirectory(metadataDir);
        }
        copyDefaultResources(metadataDir);
        staticLogger.debug("  --> Test metadata dir ready in [{}]", metadataDir);
    }

    @AfterClass
    public static void printMetadataDirContent() throws IOException {
        printLs(metadataDir);
    }

    private static void printLs(Path dir) throws IOException {
        staticLogger.debug("ls -l {}", dir);
        Files.list(dir).forEach(path -> {
            if (Files.isDirectory(path)) {
                try {
                    printLs(path);
                } catch (IOException ignored) { }
            } else {
                staticLogger.debug("{}", path);
            }
        });
    }

    /**
     * We want to make sure that we can run several times the same test
     * without having any error due to the fact that the logger context
     * has already been initialized.
     */
    @Before
    public void resetLogger() {
        reinitLoggerContext();
    }

    @Test
    public void testChangeLoggerContextForDebug() {
        String[] args = {
                "--debug",
                "jobName"
        };
        FsCrawlerCommand command = FsCrawlerCli.commandParser(args);
        assertThat(command, notNullValue());
        changeLoggerContext(command);

        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        LoggerConfig loggerConfig = config.getLoggerConfig("fr.pilato.elasticsearch.crawler.fs");
        assertThat(loggerConfig, notNullValue());
        assertThat(loggerConfig.getLevel(), is(Level.DEBUG));

        ConsoleAppender console = config.getAppender("Console");
        assertThat(console, notNullValue());
        assertThat(console.getFilter(), instanceOf(LevelRangeFilter.class));
        LevelRangeFilter filter = (LevelRangeFilter) console.getFilter();
        assertThat(filter.getMinLevel(), is(Level.TRACE));
        assertThat(filter.getMaxLevel(), is(Level.ALL));
    }

    @Test
    public void testChangeLoggerContextForTrace() {
        String[] args = {
                "--trace",
                "jobName"
        };
        FsCrawlerCommand command = FsCrawlerCli.commandParser(args);
        assertThat(command, notNullValue());
        changeLoggerContext(command);

        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        LoggerConfig loggerConfig = config.getLoggerConfig("fr.pilato.elasticsearch.crawler.fs");
        assertThat(loggerConfig, notNullValue());
        assertThat(loggerConfig.getLevel(), is(Level.TRACE));

        ConsoleAppender console = config.getAppender("Console");
        assertThat(console, notNullValue());
        assertThat(console.getFilter(), instanceOf(LevelRangeFilter.class));
        LevelRangeFilter filter = (LevelRangeFilter) console.getFilter();
        assertThat(filter.getMinLevel(), is(Level.ALL));
        assertThat(filter.getMaxLevel(), is(Level.ALL));
    }

    @Test
    public void testChangeLoggerContextForSilent() {
        String[] args = {
                "--silent",
                "jobName"
        };
        FsCrawlerCommand command = FsCrawlerCli.commandParser(args);
        assertThat(command, notNullValue());
        changeLoggerContext(command);

        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        LoggerConfig loggerConfig = config.getLoggerConfig("fr.pilato.elasticsearch.crawler.fs");
        assertThat(loggerConfig, notNullValue());
        assertThat(loggerConfig.getLevel(), is(Level.TRACE));

        ConsoleAppender console = config.getAppender("Console");
        assertThat(console, notNullValue());
        assertThat(console.getFilter(), instanceOf(LevelMatchFilter.class));
        LevelMatchFilter filter = (LevelMatchFilter) console.getFilter();
        assertThat(filter.getOnMatch(), is(Filter.Result.DENY));
    }

    @Test
    public void testChangeLoggerContextByDefault() {
        String[] args = {
                "jobName"
        };
        FsCrawlerCommand command = FsCrawlerCli.commandParser(args);
        assertThat(command, notNullValue());
        changeLoggerContext(command);

        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        LoggerConfig loggerConfig = config.getLoggerConfig("fr.pilato.elasticsearch.crawler.fs");
        assertThat(loggerConfig, notNullValue());
        assertThat(loggerConfig.getLevel(), is(Level.INFO));

        ConsoleAppender console = config.getAppender("Console");
        assertThat(console, notNullValue());
        assertThat(console.getFilter(), nullValue());
    }
}
