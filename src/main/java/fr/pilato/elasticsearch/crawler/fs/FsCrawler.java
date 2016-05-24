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

package fr.pilato.elasticsearch.crawler.fs;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import fr.pilato.elasticsearch.crawler.fs.meta.MetaFileHandler;
import fr.pilato.elasticsearch.crawler.fs.meta.job.FsJobFileHandler;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.*;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Main entry point to launch FsCrawler
 */
public class FsCrawler {

    private static final long CLOSE_POLLING_WAIT_MS = 100;

    private static final Logger logger = LogManager.getLogger(FsCrawler.class);

    public static class FsCrawlerCommand {
        @Parameter(description = "job_name")
        protected List<String> jobName;

        @Parameter(names = "--config_dir", description = "Config directory. Default to ~/.fscrawler")
        private String configDir = null;

        @Parameter(names = "--debug", description = "Debug mode")
        private boolean debug = false;

        @Parameter(names = "--trace", description = "Trace mode")
        private boolean trace = false;

        @Parameter(names = "--silent", description = "Silent mode")
        private boolean silent = false;

        @Parameter(names = "--help", description = "display current help", help = true)
        protected boolean help;
    }


    public static void main(String[] args) throws Exception {
        // create a scanner so we can read the command-line input
        Scanner scanner = new Scanner(System.in);

        FsCrawlerCommand commands = new FsCrawlerCommand();
        JCommander jCommander = new JCommander(commands, args);

        // Change debug level if needed
        if (commands.debug || commands.trace || commands.silent) {
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            Configuration config = ctx.getConfiguration();
            LoggerConfig loggerConfig = config.getLoggerConfig(FsCrawler.class.getPackage().getName());

            if (commands.silent) {
                // We change the full rootLogger level
                LoggerConfig rootLogger = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
                loggerConfig.setLevel(Level.OFF);
                rootLogger.setLevel(Level.OFF);
            } else {
                loggerConfig.setLevel(commands.debug ? Level.DEBUG : Level.TRACE);
            }
            ctx.updateLoggers();
        }

        if (commands.help) {
            jCommander.usage();
            System.exit(0);
        }

        Path configDir;

        if (commands.configDir == null) {
            configDir = MetaFileHandler.DEFAULT_ROOT;
        } else {
            configDir = Paths.get(commands.configDir);
        }

        FsSettings fsSettings = null;
        FsSettingsFileHandler fsSettingsFileHandler = new FsSettingsFileHandler(configDir);

        String jobName;

        if (commands.jobName == null) {
            // The user did not enter a job name.
            // We can list available jobs for him
            logger.info("No job specified. Here is the list of existing jobs:");

            List<String> files = new ArrayList<>();
            try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(configDir)) {
                for (Path path : directoryStream) {
                    String fileName = path.getFileName().toString();
                    if (fileName.endsWith(FsSettingsFileHandler.EXTENSION) && !fileName.endsWith(FsJobFileHandler.EXTENSION)) {
                        files.add(fileName.substring(0, fileName.lastIndexOf(FsSettingsFileHandler.EXTENSION)));
                        logger.info("[{}] - {}", files.size(), files.get(files.size()-1));
                    }
                }
            } catch (IOException ignored) {}

            if (files.size() > 0) {
                int chosenFile = 0;
                while (chosenFile <= 0 || chosenFile > files.size()) {
                    logger.info("Choose your job [1-{}]...", files.size());
                    chosenFile = scanner.nextInt();
                }
                jobName = files.get(chosenFile - 1);
            } else {
                logger.info("No job exists in [{}].", configDir);
                logger.info("To create your first job, run 'fscrawler job_name' with 'job_name' you want");
                jobName = null;
                System.exit(1);
            }

        } else {
            jobName = commands.jobName.get(0);
        }

        try {
            logger.debug("Starting job [{}]...", jobName);
            fsSettings = fsSettingsFileHandler.read(jobName);

            // Check default settings
            if (fsSettings.getFs() == null) {
                fsSettings.setFs(Fs.DEFAULT);
            }
            if (fsSettings.getElasticsearch() == null) {
                fsSettings.setElasticsearch(Elasticsearch.DEFAULT);
            }
        } catch (IOException e) {
            logger.warn("job [{}] does not exist", jobName);

            String yesno = null;
            while (!"y".equalsIgnoreCase(yesno) && !"n".equalsIgnoreCase(yesno)) {
                logger.info("Do you want to create it (Y/N)?");
                yesno = scanner.next();
            }

            if ("y".equalsIgnoreCase(yesno)) {
                fsSettings = FsSettings.builder(commands.jobName.get(0))
                        .setFs(Fs.DEFAULT)
                        .setElasticsearch(Elasticsearch.DEFAULT)
                        .build();
                fsSettingsFileHandler.write(fsSettings);

                logger.info("Settings have been created in [{}]. Please review and edit before relaunch", configDir);
            }

            System.exit(1);
        }

        logger.trace("settings used for this crawler: [{}]", FsSettingsParser.toJson(fsSettings));
        FsCrawlerImpl fsCrawler = new FsCrawlerImpl(configDir, fsSettings);
        Runtime.getRuntime().addShutdownHook(new FSCrawlerShutdownHook(fsCrawler));
        try {
            fsCrawler.start();
            // We just have to wait until the process is stopped
            while (!fsCrawler.isClosed()) {
                sleep(CLOSE_POLLING_WAIT_MS);
            }
        } catch (Exception e) {
            logger.fatal("Fatal error received while running the crawler: [{}]", e.getMessage());
            logger.debug("error caught", e);
            System.exit(-1);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        }
        catch(InterruptedException e) {
        }
    }
}
