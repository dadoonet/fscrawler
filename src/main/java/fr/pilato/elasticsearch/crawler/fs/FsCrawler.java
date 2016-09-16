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
import fr.pilato.elasticsearch.crawler.fs.meta.settings.Elasticsearch;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.Fs;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.FsSettingsFileHandler;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.FsSettingsParser;
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

import static fr.pilato.elasticsearch.crawler.fs.util.FsCrawlerUtil.copyDefaultResources;
import static fr.pilato.elasticsearch.crawler.fs.util.FsCrawlerUtil.moveLegacyResource;

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

        @Parameter(names = "--username", description = "Elasticsearch username when running with security.")
        private String username = null;

        @Parameter(names = "--loop", description = "Number of scan loop before exiting.")
        private Integer loop = -1;

        @Parameter(names = "--update_mapping", description = "Update elasticsearch mapping")
        private boolean updateMapping = false;

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

        // We copy default mapping and settings to the default settings dir .fscrawler/_default/
        copyDefaultResources(configDir);

        // We move the legacy stuff which might come from version 2.0
        moveLegacyResources(configDir);

        FsSettings fsSettings = null;
        FsSettingsFileHandler fsSettingsFileHandler = new FsSettingsFileHandler(configDir);

        String jobName;

        if (commands.jobName == null) {
            // The user did not enter a job name.
            // We can list available jobs for him
            logger.info("No job specified. Here is the list of existing jobs:");

            List<String> files = listExistingJobs(configDir);

            if (files.size() > 0) {
                for (int i = 0; i < files.size(); i++) {
                    logger.info("[{}] - {}", i+1, files.get(i));
                }
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

            String username = commands.username;
            if (fsSettings.getElasticsearch().getUsername() != null) {
                username = fsSettings.getElasticsearch().getUsername();
            }

            if (username != null && fsSettings.getElasticsearch().getPassword() == null) {
                logger.info("Password for " +  username + ":");
                String password = scanner.next();
                fsSettings.getElasticsearch().setUsername(username);
                fsSettings.getElasticsearch().setPassword(password);
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

                Path config = configDir.resolve(jobName).resolve(FsSettingsFileHandler.FILENAME);
                logger.info("Settings have been created in [{}]. Please review and edit before relaunch", config);
            }

            System.exit(1);
        }

        logger.trace("settings used for this crawler: [{}]", FsSettingsParser.toJson(fsSettings));
        FsCrawlerImpl fsCrawler = new FsCrawlerImpl(configDir, fsSettings, commands.loop, commands.updateMapping);
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

    public static List<String> listExistingJobs(Path configDir) {
        List<String> files = new ArrayList<>();
        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(configDir)) {
            for (Path path : directoryStream) {
                // This is a directory. Let's see if we have the _settings.json file in it
                if (Files.isDirectory(path)) {
                    String jobName = path.getFileName().toString();
                    Path jobSettingsFile = path.resolve(FsSettingsFileHandler.FILENAME);
                    if (Files.exists(jobSettingsFile)) {
                        files.add(jobName);
                        logger.debug("Adding job [{}]", jobName, FsSettingsFileHandler.FILENAME);
                    } else {
                        logger.debug("Ignoring [{}] dir as no [{}] has been found", jobName, FsSettingsFileHandler.FILENAME);
                    }
                }
            }
        } catch (IOException ignored) {}

        return files;
    }

    public static void moveLegacyResources(Path root) {
        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(root)) {
            for (Path path : directoryStream) {
                String fileName = path.getFileName().toString();
                if (fileName.endsWith(FsJobFileHandler.LEGACY_EXTENSION)) {
                    // We have a Legacy Job Settings file {job_name.json} which needs to move to job_name/_settings.json
                    String jobName = fileName.substring(0, fileName.length() - FsJobFileHandler.LEGACY_EXTENSION.length());
                    Path jobDir = root.resolve(jobName);
                    Files.createDirectories(jobDir);
                    Path destination = jobDir.resolve(FsJobFileHandler.FILENAME);
                    moveLegacyResource(path, destination);
                } else if (fileName.endsWith(FsSettingsFileHandler.LEGACY_EXTENSION)) {
                    // We have a Legacy Job Settings file {job_name.json} which needs to move to job_name/_settings.json
                    String jobName = fileName.substring(0, fileName.length() - FsSettingsFileHandler.LEGACY_EXTENSION.length());
                    Path jobDir = root.resolve(jobName);
                    Files.createDirectories(jobDir);
                    Path destination = jobDir.resolve(FsSettingsFileHandler.FILENAME);
                    moveLegacyResource(path, destination);
                }
            }
        } catch (IOException e) {
            logger.warn("Got error while moving legacy content", e);
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
