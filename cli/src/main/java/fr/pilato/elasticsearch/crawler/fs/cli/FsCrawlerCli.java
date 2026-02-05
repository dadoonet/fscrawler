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

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import fr.pilato.elasticsearch.crawler.fs.FsCrawlerImpl;
import fr.pilato.elasticsearch.crawler.fs.beans.FsJobFileHandler;
import fr.pilato.elasticsearch.crawler.fs.framework.*;
import fr.pilato.elasticsearch.crawler.fs.rest.RestServer;
import fr.pilato.elasticsearch.crawler.fs.settings.*;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerPluginsManager;
import org.apache.commons.lang3.StringUtils;
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

import java.io.Console;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.*;
import static org.awaitility.Awaitility.await;

/**
 * Main entry point to launch FsCrawler
 */
public class FsCrawlerCli {

    private static final Duration CLOSE_POLLING_WAIT_TIME = Duration.ofMillis(100);

    private static final Logger logger = LogManager.getLogger();
    private static RestServer restServer;

    @SuppressWarnings("CanBeFinal")
    public static class FsCrawlerCommand {
        @Parameter(description = "The job name to run. If not specified, fscrawler is used as the job name.")
        List<String> jobName;

        @Parameter(names = "--config_dir", description = "Config directory. Default to ~/.fscrawler")
        String configDir = null;

        @Parameter(names = "--api_key", description = "Elasticsearch api key. (Deprecated - use " +
                "FS_JAVA_OPTS=\"-Delasticsearch.api-key\" instead)")
        @Deprecated
        String apiKey = null;

        @Parameter(names = "--username", description = "Elasticsearch username. (Deprecated - use " +
                "FS_JAVA_OPTS=\"-Delasticsearch.api-key\" instead)")
        @Deprecated
        String username = null;

        @Parameter(names = "--loop", description = "Number of scan loop before exiting.")
        Integer loop = -1;

        @Parameter(names = "--restart", description = "Restart fscrawler job like if it never ran before. " +
                "This does not clean elasticsearch indices.")
        boolean restart = false;

        @Parameter(names = "--rest", description = "Start REST Layer")
        boolean rest = false;

        @Parameter(names = "--upgrade", description = "Upgrade elasticsearch indices from one old version to the last version.")
        boolean upgrade = false;

        @Parameter(names = "--setup", description = "Setup FSCrawler and associated services for a given job name.")
        boolean setup = false;

        @Parameter(names = "--list", description = "List FSCrawler jobs if any.")
        boolean list = false;

        @Parameter(names = "--migrate", description = "Migrate a job configuration from v1 to v2 pipeline format.")
        boolean migrate = false;

        @Parameter(names = "--migrate-output", description = "Output file for migrated configuration (use with --migrate). " +
                "If not specified, the new configuration is displayed on console.")
        String migrateOutput = null;

        @Deprecated
        @Parameter(names = "--debug", description = "Debug mode (Deprecated - use FS_JAVA_OPTS=\"-DLOG_LEVEL=debug\" instead)")
        boolean debug = false;

        @Deprecated
        @Parameter(names = "--trace", description = "Trace mode (Deprecated - use FS_JAVA_OPTS=\"-DLOG_LEVEL=trace\" instead)")
        boolean trace = false;

        @Parameter(names = "--silent", description = "Silent mode")
        boolean silent = false;

        @Parameter(names = "--help", description = "display current help", help = true)
        boolean help;
    }


    public static void main(String[] args) throws Exception {
        FsCrawlerCommand command = commandParser(args);

        if (command != null) {
            if (command.debug) {
                // Deprecated command line option
                logger.warn("--debug option has been deprecated. Use FS_JAVA_OPTS=\"-DLOG_LEVEL=debug\" instead.");
            }
            if (command.trace) {
                // Deprecated command line option
                logger.warn("--trace option has been deprecated. Use FS_JAVA_OPTS=\"-DLOG_LEVEL=trace\" instead.");
            }

            // We change the log level if needed
            changeLoggerContext(command);

            // Display the welcome banner
            banner();

            // We can now launch the crawler
            runner(command);
        }
    }

    static FsCrawlerCommand commandParser(String[] args) {
        FsCrawlerCommand commands = new FsCrawlerCommand();
        JCommander jCommander = new JCommander(commands);
        jCommander.parse(args);

        // Check the expected parameters when in silent mode
        if (commands.silent) {
            if (commands.jobName == null) {
                banner();
                logger.warn("--silent is set but no job has been defined. Add a job name or remove --silent option. Exiting.");
                jCommander.usage();
                throw new FsCrawlerIllegalConfigurationException("No job specified while in silent mode.");
            }
        }

        if (commands.help) {
            jCommander.usage();
            return null;
        }

        return commands;
    }

    static void changeLoggerContext(FsCrawlerCommand command) {
        // Change debug level if needed
        if (command.debug || command.trace || command.silent) {
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            Configuration config = ctx.getConfiguration();
            LoggerConfig loggerConfig = config.getLoggerConfig("fr.pilato.elasticsearch.crawler.fs");
            ConsoleAppender console = config.getAppender("Console");

            if (command.silent) {
                // We don't write anything on the console anymore
                if (console != null) {
                    console.addFilter(LevelMatchFilter.newBuilder().setLevel(Level.ALL).setOnMatch(Filter.Result.DENY).build());
                }
            } else {
                if (console != null) {
                    console.addFilter(LevelRangeFilter.createFilter(
                            command.debug ? Level.TRACE : Level.ALL,
                            Level.ALL,
                            Filter.Result.DENY,
                            Filter.Result.ACCEPT));
                }
            }

            loggerConfig.setLevel(command.debug ? Level.DEBUG : Level.TRACE);
            ctx.updateLoggers();
        }
    }

    /**
     * Reinit the logger context to remove all filters
     */
    static void reinitLoggerContext() {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        ConsoleAppender console = config.getAppender("Console");
        if (console != null) {
            Filter filter = console.getFilter();
            console.removeFilter(filter);
        }

        LoggerConfig loggerConfig = config.getLoggerConfig("fr.pilato.elasticsearch.crawler.fs");
        if (loggerConfig != null) {
            loggerConfig.setLevel(Level.INFO);
        }
    }

    /**
     * Create a job if needed
     * @param jobName the job name
     * @param configDir the config dir
     * @throws IOException In case of IO problem
     */
    static void createJob(String jobName, Path configDir) throws IOException {
        Path configJobDir = configDir.resolve(jobName);
        Files.createDirectories(configJobDir);
        Path configFile = configJobDir.resolve(FsSettingsLoader.SETTINGS_YAML);

        if (Files.exists(configFile)) {
            logger.debug("Job [{}] already exists, skipping creation", jobName);
        } else {
            // Write the example config files from the classpath FsSettingsLoader.EXAMPLE_SETTINGS
            logger.debug("Creating [{}] from the classloader [{}] file.", configFile, FsSettingsLoader.EXAMPLE_SETTINGS);
            copyResourceFile(FsSettingsLoader.EXAMPLE_SETTINGS, configFile);
        }
    }

    static void runner(FsCrawlerCommand command) throws IOException {
        // create a scanner so we can read the command-line input
        Console con = System.console();
        Scanner scanner = null;
        if (con != null) {
            scanner = new Scanner(con.reader());
        }

        BootstrapChecks.check();

        Path configDir;

        if (command.configDir == null) {
            configDir = MetaFileHandler.DEFAULT_ROOT;
        } else {
            configDir = Paths.get(command.configDir);
        }

        // Create the config dir if needed
        FsCrawlerUtil.createDirIfMissing(configDir);

        FsSettings fsSettings;

        String jobName;
        if (command.jobName == null || command.jobName.isEmpty()) {
            logger.debug("No job name specified. Using default one [{}]...", Defaults.JOB_NAME_DEFAULT);
            jobName = Defaults.JOB_NAME_DEFAULT;
        } else {
            jobName = command.jobName.get(0);
        }

        if (command.list) {
            // We are in list mode. We just display the list of existing jobs if any.
            listJobs(configDir);
            return;
        }

        if (command.setup) {
            // We are in setup mode. We need to create the job if it does not exist yet.
            setup(configDir, jobName);
            return;
        }

        if (command.migrate) {
            // We are in migrate mode. We read the v1 configuration and output v2 format.
            migrateConfiguration(configDir, jobName, command.migrateOutput);
            return;
        }

        // If we ask to reinit, we need to clean the status for the job
        if (command.restart) {
            logger.debug("Cleaning existing status for job [{}]...", jobName);
            new FsJobFileHandler(configDir).clean(jobName);
        }

        logger.debug("Starting job [{}]...", jobName);
        try {
            fsSettings = new FsSettingsLoader(configDir).read(jobName);
            // Let's make the job name not mandatory in the settings file
            if (fsSettings.getName() == null) {
                fsSettings.setName(jobName);
            }
        } catch (Exception e) {
            logger.fatal("Cannot parse the configuration file: {}", e.getMessage());
            throw e;
        }

        if (command.username != null) {
            logger.fatal("We don't support reading elasticsearch username from the command line anymore. " +
                            "Please use either FS_JAVA_OPTS=\"-Delasticsearch.username={}\" or set the env variable as " +
                            "follows: FSCRAWLER_ELASTICSEARCH_USERNAME={} ",
                    command.username, command.username);
            return;
        }

        if (command.apiKey != null) {
            logger.fatal("We don't support reading elasticsearch API Key from the command line anymore. " +
                            "Please use either FS_JAVA_OPTS=\"-Delasticsearch.api-key={}\" or set the env variable as " +
                            "follows: FSCRAWLER_ELASTICSEARCH_API-KEY={} ",
                    command.apiKey, command.apiKey);
            return;
        }

        if (fsSettings.getElasticsearch().getUsername() != null && fsSettings.getElasticsearch().getPassword() == null && scanner != null) {
            logger.fatal("We don't support reading elasticsearch password from the command line anymore. " +
                    "Please use either FS_JAVA_OPTS=\"-Delasticsearch.password=YOUR_PASS\" or set the env variable as " +
                    "follows: FSCRAWLER_ELASTICSEARCH_PASSWORD=YOUR_PASS.");
            logger.warn("Using username and password is deprecated. Please use API Keys instead. See " +
                            "https://fscrawler.readthedocs.io/en/latest/admin/fs/elasticsearch.html#api-key");
            return;
        }

        if (logger.isTraceEnabled()) {
            logger.trace("settings used for this crawler: [{}]", FsSettingsParser.toYaml(fsSettings));
        }
        if (FsCrawlerValidator.validateSettings(logger, fsSettings)) {
            // We don't go further as we have critical errors
            return;
        }

        // Load all plugins
        FsCrawlerPluginsManager pluginsManager = new FsCrawlerPluginsManager();
        pluginsManager.loadPlugins();
        pluginsManager.startPlugins();

        try (FsCrawlerImpl fsCrawler = new FsCrawlerImpl(configDir, fsSettings, command.loop, command.rest)) {
            // Let see if we want to upgrade an existing cluster to the latest version
            if (command.upgrade) {
                logger.info("Upgrading job [{}]. No rule implemented. Skipping.", jobName);
            } else {
                if (!startFsCrawlerThreadAndServices(fsCrawler)) {
                    return;
                }

                // Start the REST Server if needed
                if (command.rest) {
                    restServer = new RestServer(fsSettings, fsCrawler.getManagementService(), fsCrawler.getDocumentService(), pluginsManager);
                    restServer.start();
                }

                // We add a shutdown hook to stop the crawler and all the related services
                Runtime.getRuntime().addShutdownHook(new FSCrawlerShutdownHook(fsCrawler, pluginsManager, restServer));

                // We just have to wait until the process is stopped
                await()
                        .forever()
                        .pollInterval(CLOSE_POLLING_WAIT_TIME)
                        .until(() -> fsCrawler.getFsParser().isClosed());
            }
        } catch (Exception e) {
            logger.fatal("Fatal error received while running the crawler: [{}]", e.getMessage());
            logger.debug("error caught", e);
        }
    }

    private static void listJobs(Path configDir) {
        FSCrawlerLogger.console("Here is the list of existing jobs:");
        List<String> files = FsCrawlerJobsUtil.listExistingJobs(configDir);
        if (!files.isEmpty()) {
            for (int i = 0; i < files.size(); i++) {
                FSCrawlerLogger.console("[{}] - {}", i+1, files.get(i));
            }
        } else {
            FSCrawlerLogger.console("No job exists in [{}].", configDir);
            FSCrawlerLogger.console("To create your first job, run 'fscrawler --setup'");
        }
    }

    private static void setup(Path configDir, String jobName) throws IOException {
        logger.debug("Entering setup mode for [{}]...", jobName);
        createJob(jobName, configDir);

        FSCrawlerLogger.console("You can edit the settings in [{}]. Then, you can run again fscrawler " +
                        "without the --setup option.",
                configDir.resolve(jobName).resolve(FsSettingsLoader.SETTINGS_YAML));
    }

    private static void migrateConfiguration(Path configDir, String jobName, String outputFile) throws IOException {
        logger.debug("Entering migrate mode for [{}]...", jobName);
        
        FsSettings fsSettings;
        try {
            fsSettings = new FsSettingsLoader(configDir).read(jobName);
            if (fsSettings.getName() == null) {
                fsSettings.setName(jobName);
            }
        } catch (Exception e) {
            logger.fatal("Cannot parse the configuration file: {}", e.getMessage());
            throw e;
        }
        
        // Detect the version
        int version = FsSettingsMigrator.detectVersion(fsSettings);
        
        if (version == FsSettingsMigrator.VERSION_2) {
            FSCrawlerLogger.console("Job [{}] is already using v2 pipeline format. No migration needed.", jobName);
            return;
        }
        
        FSCrawlerLogger.console("Migrating job [{}] from v1 to v2 pipeline format...", jobName);
        
        // Perform the migration
        FsSettings v2Settings = FsSettingsMigrator.migrateV1ToV2(fsSettings);
        
        if (outputFile != null) {
            // Check if output should be a split directory structure
            boolean isSplitOutput = outputFile.endsWith("/") || 
                                   outputFile.equals(FsSettingsLoader.SETTINGS_DIR) ||
                                   outputFile.endsWith("/" + FsSettingsLoader.SETTINGS_DIR);
            
            if (isSplitOutput) {
                // Generate split files
                writeSplitConfiguration(configDir, jobName, outputFile, v2Settings);
            } else {
                // Write single file
                writeSingleConfiguration(configDir, jobName, outputFile, v2Settings);
            }
        } else {
            // Display on console
            String v2Yaml = FsSettingsMigrator.generateV2Yaml(v2Settings);
            FSCrawlerLogger.console("");
            FSCrawlerLogger.console("--- Migrated v2 configuration ---");
            FSCrawlerLogger.console(v2Yaml);
            FSCrawlerLogger.console("---------------------------------");
            FSCrawlerLogger.console("");
            FSCrawlerLogger.console("To save this configuration as a single file, run:");
            FSCrawlerLogger.console("  fscrawler {} --migrate --migrate-output _settings_v2.yaml", jobName);
            FSCrawlerLogger.console("");
            FSCrawlerLogger.console("To save as split files (recommended for complex configurations), run:");
            FSCrawlerLogger.console("  fscrawler {} --migrate --migrate-output _settings/", jobName);
        }
    }

    private static void writeSingleConfiguration(Path configDir, String jobName, String outputFile, FsSettings v2Settings) throws IOException {
        String v2Yaml = FsSettingsMigrator.generateV2Yaml(v2Settings);
        Path outputPath = Paths.get(outputFile);
        if (!outputPath.isAbsolute()) {
            outputPath = configDir.resolve(jobName).resolve(outputFile);
        }
        Files.writeString(outputPath, v2Yaml);
        FSCrawlerLogger.console("Migrated configuration written to [{}]", outputPath);
        FSCrawlerLogger.console("");
        FSCrawlerLogger.console("To use the new configuration:");
        FSCrawlerLogger.console("  1. Review the generated file");
        FSCrawlerLogger.console("  2. Backup your current _settings.yaml");
        FSCrawlerLogger.console("  3. Replace _settings.yaml with the migrated version");
    }

    private static void writeSplitConfiguration(Path configDir, String jobName, String outputDir, FsSettings v2Settings) throws IOException {
        // Determine the output directory path
        String dirName = outputDir.endsWith("/") ? outputDir.substring(0, outputDir.length() - 1) : outputDir;
        Path outputPath = Paths.get(dirName);
        if (!outputPath.isAbsolute()) {
            outputPath = configDir.resolve(jobName).resolve(dirName);
        }
        
        // Create directory if needed
        Files.createDirectories(outputPath);
        
        // Generate and write split files
        Map<String, String> splitFiles = FsSettingsMigrator.generateV2SplitFiles(v2Settings);
        for (Map.Entry<String, String> entry : splitFiles.entrySet()) {
            Path filePath = outputPath.resolve(entry.getKey());
            Files.writeString(filePath, entry.getValue());
            FSCrawlerLogger.console("  Created: {}", filePath.getFileName());
        }
        
        FSCrawlerLogger.console("");
        FSCrawlerLogger.console("Migrated configuration written to [{}]", outputPath);
        FSCrawlerLogger.console("");
        FSCrawlerLogger.console("Files created:");
        FSCrawlerLogger.console("  - 00-common.yaml     : Name and version");
        FSCrawlerLogger.console("  - 10-input-*.yaml    : Input configurations");
        FSCrawlerLogger.console("  - 20-filter-*.yaml   : Filter configurations");
        FSCrawlerLogger.console("  - 30-output-*.yaml   : Output configurations");
        FSCrawlerLogger.console("");
        FSCrawlerLogger.console("To use the new configuration:");
        FSCrawlerLogger.console("  1. Review the generated files");
        FSCrawlerLogger.console("  2. Backup your current _settings.yaml");
        FSCrawlerLogger.console("  3. Remove _settings.yaml (the _settings/ directory takes precedence)");
    }

    private static boolean startFsCrawlerThreadAndServices(FsCrawlerImpl fsCrawler) {
        try {
            fsCrawler.start();
            return true;
        } catch (Exception t) {
            logger.fatal("We can not start FSCrawler Thread and Services. Exiting.", t);
            return false;
        }
    }

    private static final int BANNER_LENGTH = 100;

    /**
     * This is coming from: <a href="https://patorjk.com/software/taag/#p=display&f=3D%20Diagonal&t=FSCrawler">https://patorjk.com/software/taag/#p=display&f=3D%20Diagonal&t=FSCrawler</a>
     */
    private static final String ASCII_ART =
            "    ,---,.  .--.--.     ,----..                                     ,--,                      \n" +
            "  ,'  .' | /  /    '.  /   /   \\                                  ,--.'|                      \n" +
            ",---.'   ||  :  /`. / |   :     :  __  ,-.                   .---.|  | :               __  ,-.\n" +
            "|   |   .';  |  |--`  .   |  ;. /,' ,'/ /|                  /. ./|:  : '             ,' ,'/ /|\n" +
            ":   :  :  |  :  ;_    .   ; /--` '  | |' | ,--.--.       .-'-. ' ||  ' |      ,---.  '  | |' |\n" +
            ":   |  |-, \\  \\    `. ;   | ;    |  |   ,'/       \\     /___/ \\: |'  | |     /     \\ |  |   ,'\n" +
            "|   :  ;/|  `----.   \\|   : |    '  :  / .--.  .-. | .-'.. '   ' .|  | :    /    /  |'  :  /  \n" +
            "|   |   .'  __ \\  \\  |.   | '___ |  | '   \\__\\/: . ./___/ \\:     ''  : |__ .    ' / ||  | '   \n" +
            "'   :  '   /  /`--'  /'   ; : .'|;  : |   ,\" .--.; |.   \\  ' .\\   |  | '.'|'   ;   /|;  : |   \n" +
            "|   |  |  '--'.     / '   | '/  :|  , ;  /  /  ,.  | \\   \\   ' \\ |;  :    ;'   |  / ||  , ;   \n" +
            "|   :  \\    `--'---'  |   :    /  ---'  ;  :   .'   \\ \\   \\  |--\" |  ,   / |   :    | ---'    \n" +
            "|   | ,'               \\   \\ .'         |  ,     .-./  \\   \\ |     ---`-'   \\   \\  /          \n" +
            "`----'                  `---`            `--`---'       '---\"                `----'           \n";

    private static void banner() {
        FSCrawlerLogger.console(
                separatorLine(",", ".") +
                centerAsciiArt() +
                separatorLine("+", "+") +
                bannerLine("You know, for Files!") +
                bannerLine("Made from France with Love") +
                bannerLine("Source: https://github.com/dadoonet/fscrawler/") +
                bannerLine("Documentation: https://fscrawler.readthedocs.io/") +
                separatorLine("`", "'"));
    }

    private static String centerAsciiArt() {
        String[] lines = StringUtils.split(ASCII_ART, '\n');

        // Edit line 0 as we want to add the version
        String version = Version.getVersion();
        String firstLine = StringUtils.stripEnd(StringUtils.center(lines[0], BANNER_LENGTH), null);
        String pad = StringUtils.rightPad(firstLine, BANNER_LENGTH - version.length() - 1) + version;
        lines[0] = pad;

        StringBuilder content = new StringBuilder();
        for (String line : lines) {
            content.append(bannerLine(line));
        }

        return content.toString();
    }

    private static String bannerLine(String text) {
        return "|" + StringUtils.center(text, BANNER_LENGTH) + "|\n";
    }

    private static String separatorLine(String first, String last) {
        return first + StringUtils.center("", BANNER_LENGTH, "-") + last + "\n";
    }
}
