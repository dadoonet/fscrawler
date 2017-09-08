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
import fr.pilato.elasticsearch.crawler.fs.util.FsCrawlerUtil;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.elasticsearch.Version;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Scanner;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.copyDefaultResources;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.moveLegacyResource;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.readJsonFile;

/**
 * Main entry point to launch FsCrawler
 */
public class FsCrawler {

    @Deprecated
    public static final String INDEX_SETTINGS_FILE = "_settings";

    private static final long CLOSE_POLLING_WAIT_MS = 100;

    private static final Logger logger = LogManager.getLogger(FsCrawler.class);

    private static FsCrawlerImpl fsCrawler = null;

    @SuppressWarnings("CanBeFinal")
    public static class FsCrawlerCommand {
        @Parameter(description = "job_name")
        List<String> jobName;

        @Parameter(names = "--config_dir", description = "Config directory. Default to ~/.fscrawler")
        private String configDir = null;

        @Parameter(names = "--username", description = "Elasticsearch username when running with security.")
        private String username = null;

        @Parameter(names = "--loop", description = "Number of scan loop before exiting.")
        private Integer loop = -1;

        @Parameter(names = "--restart", description = "Restart fscrawler job like if it never ran before. " +
                "This does not clean elasticsearch indices.")
        private boolean restart = false;

        @Parameter(names = "--rest", description = "Start REST Layer")
        private boolean rest = false;

        @Parameter(names = "--upgrade", description = "Upgrade elasticsearch indices from one old version to the last version.")
        private boolean upgrade = false;

        @Parameter(names = "--debug", description = "Debug mode")
        private boolean debug = false;

        @Parameter(names = "--trace", description = "Trace mode")
        private boolean trace = false;

        @Parameter(names = "--silent", description = "Silent mode")
        private boolean silent = false;

        @Parameter(names = "--help", description = "display current help", help = true)
        boolean help;
    }


    @SuppressWarnings("deprecation")
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
            return;
        }

        BootstrapChecks.check();

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

            List<String> files = FsCrawlerUtil.listExistingJobs(configDir);

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
                return;
            }

        } else {
            jobName = commands.jobName.get(0);
        }

        // If we ask to reinit, we need to clean the status for the job
        if (commands.restart) {
            logger.debug("Cleaning existing status for job [{}]...", jobName);
            new FsJobFileHandler(configDir).clean(jobName);
        }

        try {
            logger.debug("Starting job [{}]...", jobName);
            fsSettings = fsSettingsFileHandler.read(jobName);

            // Check default settings
            if (fsSettings.getFs() == null) {
                fsSettings.setFs(Fs.DEFAULT);
            }
            if (fsSettings.getElasticsearch() == null) {
                fsSettings.setElasticsearch(Elasticsearch.DEFAULT());
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

        } catch (NoSuchFileException e) {
            logger.warn("job [{}] does not exist", jobName);

            String yesno = null;
            while (!"y".equalsIgnoreCase(yesno) && !"n".equalsIgnoreCase(yesno)) {
                logger.info("Do you want to create it (Y/N)?");
                yesno = scanner.next();
            }

            if ("y".equalsIgnoreCase(yesno)) {
                fsSettings = FsSettings.builder(commands.jobName.get(0))
                        .setFs(Fs.DEFAULT)
                        .setElasticsearch(Elasticsearch.DEFAULT())
                        .build();
                fsSettingsFileHandler.write(fsSettings);

                Path config = configDir.resolve(jobName).resolve(FsSettingsFileHandler.FILENAME);
                logger.info("Settings have been created in [{}]. Please review and edit before relaunch", config);
            }

            return;
        }

        logger.trace("settings used for this crawler: [{}]", FsSettingsParser.toJson(fsSettings));
        fsCrawler = new FsCrawlerImpl(configDir, fsSettings, commands.loop, commands.rest);
        Runtime.getRuntime().addShutdownHook(new FSCrawlerShutdownHook(fsCrawler));

        try {
            // Let see if we want to upgrade an existing cluster to latest version
            if (commands.upgrade) {
                logger.info("Upgrading job [{}]", jobName);
                boolean success = fsCrawler.upgrade();
                if (success) {
                    // We can rewrite the fscrawler setting file (we now have a elasticsearch.index_folder property)
                    logger.info("Updating fscrawler setting file");
                    fsSettingsFileHandler.write(fsSettings);
                }
            } else {
                Path jobMappingDir = configDir.resolve(fsSettings.getName()).resolve("_mappings");
                fsCrawler.getEsClientManager().start();
                Version elasticsearchVersion = fsCrawler.getEsClientManager().client().info().getVersion();
                try {
                    // If we are able to read an old configuration file, we should tell the user to check the documentation
                    readJsonFile(jobMappingDir, configDir, Byte.toString(elasticsearchVersion.major), INDEX_SETTINGS_FILE);
                    logger.warn("We found old configuration index settings in [{}] or [{}]. You should look at the documentation" +
                            " about upgrades: https://github.com/dadoonet/fscrawler#upgrade-to-23", configDir, jobMappingDir);
                } catch (IllegalArgumentException ignored) { }

                fsCrawler.start();
                // We just have to wait until the process is stopped
                while (!fsCrawler.isClosed()) {
                    sleep();
                }
            }
        } catch (Exception e) {
            logger.fatal("Fatal error received while running the crawler: [{}]", e.getMessage());
            logger.debug("error caught", e);
        } finally {
            if (fsCrawler != null) {
                fsCrawler.close();
            }
        }
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

    private static void sleep() {
        try {
            Thread.sleep(CLOSE_POLLING_WAIT_MS);
        }
        catch(InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
