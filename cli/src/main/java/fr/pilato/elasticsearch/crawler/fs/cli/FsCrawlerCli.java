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

import fr.pilato.elasticsearch.crawler.plugins.pipeline.PipelinePluginsManager;

import java.io.Console;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
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

        @Parameter(names = "--setup", description = "Setup FSCrawler with V2 per-plugin configuration. " +
                "Creates _settings/ directory with global settings and one config file per plugin.")
        boolean setup = false;

        @Parameter(names = "--list", description = "List FSCrawler jobs if any.")
        boolean list = false;

        @Parameter(names = "--list-plugins", description = "List discovered pipeline and service plugins (inputs, filters, outputs, services).")
        boolean listPlugins = false;

        @Parameter(names = "--migrate", description = "Migrate a job configuration from v1 to v2 per-plugin format. " +
                "Creates separate config files for each plugin in _settings/inputs/, _settings/filters/, _settings/outputs/.")
        boolean migrate = false;

        @Parameter(names = "--migrate-keep-old-files", description = "Keep old configuration files after migration (use with --migrate).")
        boolean migrateKeepOldFiles = false;

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
     * Create a job if needed (V2 format with per-plugin settings).
     * Creates the following structure:
     * <pre>
     *   config/{jobName}/_settings/
     *     01-global.yaml
     *     inputs/
     *       01-local.yaml
     *     filters/
     *       01-tika.yaml
     *     outputs/
     *       01-elasticsearch.yaml
     * </pre>
     * @param jobName the job name
     * @param configDir the config dir
     * @param pluginsManager the plugins manager for discovering available plugins
     * @return list of created plugin descriptions for display
     * @throws IOException In case of IO problem
     */
    static List<String> createJob(String jobName, Path configDir, PipelinePluginsManager pluginsManager) throws IOException {
        List<String> createdFiles = new ArrayList<>();
        Path configJobDir = configDir.resolve(jobName);
        Path settingsDir = configJobDir.resolve(GlobalSettings.SETTINGS_DIR);
        
        // Check if job already exists (either V1 or V2 format)
        if (Files.exists(settingsDir)) {
            logger.debug("Job [{}] already exists (V2 format), skipping creation", jobName);
            return createdFiles;
        }
        Path oldConfigFile = configJobDir.resolve(FsSettingsLoader.SETTINGS_YAML);
        if (Files.exists(oldConfigFile)) {
            logger.debug("Job [{}] already exists (V1 format), skipping creation", jobName);
            return createdFiles;
        }
        
        // Create directory structure
        Files.createDirectories(settingsDir.resolve(GlobalSettings.INPUTS_DIR));
        Files.createDirectories(settingsDir.resolve(GlobalSettings.FILTERS_DIR));
        Files.createDirectories(settingsDir.resolve(GlobalSettings.OUTPUTS_DIR));
        
        // Create global settings file (replace ${JOB_NAME} placeholder with actual job name)
        Path globalSettingsFile = settingsDir.resolve(GlobalSettings.GLOBAL_SETTINGS_YAML);
        logger.debug("Creating global settings from the classloader [{}] file.", GlobalSettings.EXAMPLE_SETTINGS);
        String globalContent = readResourceFileAsString(GlobalSettings.EXAMPLE_SETTINGS);
        globalContent = globalContent.replace("${JOB_NAME}", jobName);
        Files.writeString(globalSettingsFile, globalContent);
        createdFiles.add("01-global.yaml : Global settings (job name, REST API)");
        
        // Create input plugin settings from discovered plugins
        for (var plugin : pluginsManager.getDefaultInputPlugins()) {
            String filename = plugin.getDefaultSettingsFilename();
            String resource = plugin.getDefaultYamlResource();
            if (filename != null && resource != null) {
                Path targetFile = settingsDir.resolve(GlobalSettings.INPUTS_DIR).resolve(filename);
                logger.debug("Creating input settings from plugin [{}] using resource [{}]", plugin.getType(), resource);
                copyResourceFile(resource, targetFile);
                createdFiles.add("inputs/" + filename + " : " + plugin.getDescription());
            }
        }
        
        // Create filter plugin settings from discovered plugins
        for (var plugin : pluginsManager.getDefaultFilterPlugins()) {
            String filename = plugin.getDefaultSettingsFilename();
            String resource = plugin.getDefaultYamlResource();
            if (filename != null && resource != null) {
                Path targetFile = settingsDir.resolve(GlobalSettings.FILTERS_DIR).resolve(filename);
                logger.debug("Creating filter settings from plugin [{}] using resource [{}]", plugin.getType(), resource);
                copyResourceFile(resource, targetFile);
                createdFiles.add("filters/" + filename + " : " + plugin.getDescription());
            }
        }
        
        // Create output plugin settings from discovered plugins
        for (var plugin : pluginsManager.getDefaultOutputPlugins()) {
            String filename = plugin.getDefaultSettingsFilename();
            String resource = plugin.getDefaultYamlResource();
            if (filename != null && resource != null) {
                Path targetFile = settingsDir.resolve(GlobalSettings.OUTPUTS_DIR).resolve(filename);
                logger.debug("Creating output settings from plugin [{}] using resource [{}]", plugin.getType(), resource);
                copyResourceFile(resource, targetFile);
                createdFiles.add("outputs/" + filename + " : " + plugin.getDescription());
            }
        }
        
        return createdFiles;
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

        if (command.listPlugins) {
            listPlugins();
            return;
        }

        if (command.setup) {
            // We are in setup mode. We need to create the job if it does not exist yet.
            setup(configDir, jobName);
            return;
        }

        if (command.migrate) {
            // We are in migrate mode. We read the v1 configuration and output v2 per-plugin format.
            migrateConfiguration(configDir, jobName, command.silent, command.migrateKeepOldFiles);
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

        // For V1 format, check if user needs to provide password
        if (fsSettings.getElasticsearch() != null &&
                fsSettings.getElasticsearch().getUsername() != null && 
                fsSettings.getElasticsearch().getPassword() == null && scanner != null) {
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

    private static void listPlugins() {
        PipelinePluginsManager manager = new PipelinePluginsManager();
        manager.loadPlugins();
        manager.startPlugins();
        try {
            FSCrawlerLogger.console("Discovered plugins:");
            FSCrawlerLogger.console("  Inputs:   {}", String.join(", ", manager.getAvailableInputTypes()));
            FSCrawlerLogger.console("  Filters: {}", String.join(", ", manager.getAvailableFilterTypes()));
            FSCrawlerLogger.console("  Outputs: {}", String.join(", ", manager.getAvailableOutputTypes()));
            FSCrawlerLogger.console("  Services: {} (enable in _settings/services/*.yaml)", String.join(", ", manager.getAvailableServiceTypes()));
        } finally {
            manager.close();
        }
    }

    private static void setup(Path configDir, String jobName) throws IOException {
        logger.debug("Entering setup mode for [{}]...", jobName);
        
        // Load plugins to discover available plugin types
        PipelinePluginsManager pluginsManager = new PipelinePluginsManager();
        pluginsManager.loadPlugins();
        pluginsManager.startPlugins();
        
        try {
            List<String> createdFiles = createJob(jobName, configDir, pluginsManager);
            
            if (createdFiles.isEmpty()) {
                FSCrawlerLogger.console("Job [{}] already exists. No files created.", jobName);
                return;
            }
            
            Path settingsDir = configDir.resolve(jobName).resolve(GlobalSettings.SETTINGS_DIR);
            FSCrawlerLogger.console("Created job settings in [{}]:", settingsDir);
            for (String fileDesc : createdFiles) {
                FSCrawlerLogger.console("  - {}", fileDesc);
            }
            FSCrawlerLogger.console("");
            FSCrawlerLogger.console("Edit these files to configure your crawler, then run 'fscrawler %s'.", jobName);
        } finally {
            pluginsManager.close();
        }
    }

    private static void migrateConfiguration(Path configDir, String jobName,
                                              boolean silent, boolean keepOldFiles) throws IOException {
        logger.debug("Entering migrate mode for [{}]...", jobName);
        
        // Step 1: Read and parse current configuration (without auto-migration)
        FsSettings fsSettings;
        Path jobDir = configDir.resolve(jobName);
        try {
            fsSettings = new FsSettingsLoader(configDir).read(jobName, false);
            if (fsSettings.getName() == null) {
                fsSettings.setName(jobName);
            }
        } catch (Exception e) {
            logger.fatal("Cannot parse the configuration file: {}", e.getMessage());
            throw e;
        }
        
        // Check if already v2 (per-plugin format)
        Path settingsDir = jobDir.resolve(FsSettingsMigrator.SETTINGS_DIR);
        if (Files.exists(settingsDir) && Files.isDirectory(settingsDir)) {
            FSCrawlerLogger.console("Job [{}] already has a _settings/ directory. No migration needed.", jobName);
            return;
        }
        
        // Detect existing configuration files (v1 format)
        Path existingYaml = jobDir.resolve(FsSettingsLoader.SETTINGS_YAML);
        Path existingJson = jobDir.resolve(FsSettingsLoader.SETTINGS_JSON);
        
        List<Path> oldFiles = new java.util.ArrayList<>();
        if (Files.exists(existingYaml)) oldFiles.add(existingYaml);
        if (Files.exists(existingJson)) oldFiles.add(existingJson);
        
        if (oldFiles.isEmpty()) {
            FSCrawlerLogger.console("No configuration files found for job [{}].", jobName);
            return;
        }
        
        // Generate per-plugin configuration files (v2 format)
        Path outputPath = jobDir.resolve(FsSettingsMigrator.SETTINGS_DIR);
        Map<String, String> newFiles = FsSettingsMigrator.generatePerPluginFiles(fsSettings);
        
        // Step 2: Display what will be done (unless silent)
        if (!silent) {
            FSCrawlerLogger.console("");
            FSCrawlerLogger.console("=== Migration Preview for job [{}] ===", jobName);
            FSCrawlerLogger.console("");
            
            // Show files to be created
            FSCrawlerLogger.console("Files to be CREATED in [{}]:", outputPath);
            for (Map.Entry<String, String> entry : newFiles.entrySet()) {
                FSCrawlerLogger.console("");
                FSCrawlerLogger.console("--- {} ---", entry.getKey());
                FSCrawlerLogger.console(entry.getValue().trim());
            }
            FSCrawlerLogger.console("");
            
            // Show files to be deleted
            if (!oldFiles.isEmpty() && !keepOldFiles) {
                FSCrawlerLogger.console("Files to be DELETED:");
                for (Path oldFile : oldFiles) {
                    FSCrawlerLogger.console("  - {}", oldFile.getFileName());
                }
                FSCrawlerLogger.console("");
            } else if (keepOldFiles) {
                FSCrawlerLogger.console("Old files will be KEPT (--migrate-keep-old-files).");
                FSCrawlerLogger.console("");
            }
            
            // Step 3: Ask for confirmation
            FSCrawlerLogger.console("=================================");
            if (!askConfirmation("Do you want to proceed with the migration?")) {
                FSCrawlerLogger.console("Migration cancelled.");
                return;
            }
        }
        
        // Step 4: Create new files
        for (Map.Entry<String, String> entry : newFiles.entrySet()) {
            Path filePath = outputPath.resolve(entry.getKey());
            // Create parent directories if needed (for per-plugin format with subdirs)
            if (filePath.getParent() != null) {
                Files.createDirectories(filePath.getParent());
            }
            Files.writeString(filePath, entry.getValue());
            if (!silent) {
                FSCrawlerLogger.console("  Created: {}", filePath);
            }
        }
        
        // Step 5: Delete old files (unless --migrate-keep-old-files)
        if (!keepOldFiles && !oldFiles.isEmpty()) {
            for (Path oldFile : oldFiles) {
                // Don't delete if it's the same as output
                if (oldFile.equals(outputPath)) continue;
                
                if (Files.isDirectory(oldFile)) {
                    // Delete directory contents
                    try (var stream = Files.walk(oldFile)) {
                        stream.sorted(java.util.Comparator.reverseOrder())
                              .forEach(p -> {
                                  try {
                                      Files.delete(p);
                                  } catch (IOException e) {
                                      logger.warn("Could not delete {}: {}", p, e.getMessage());
                                  }
                              });
                    }
                } else {
                    Files.deleteIfExists(oldFile);
                }
                if (!silent) {
                    FSCrawlerLogger.console("  Deleted: {}", oldFile);
                }
            }
        }
        
        if (!silent) {
            FSCrawlerLogger.console("");
            FSCrawlerLogger.console("Migration completed successfully!");
        }
    }

    /**
     * Asks for user confirmation via console.
     * @param message The question to ask
     * @return true if user confirms, false otherwise
     */
    private static boolean askConfirmation(String message) {
        Console console = System.console();
        if (console != null) {
            String response = console.readLine("%s (y/N): ", message);
            return response != null && (response.equalsIgnoreCase("y") || response.equalsIgnoreCase("yes"));
        } else {
            // Fallback to Scanner if no console (e.g., IDE)
            FSCrawlerLogger.console("{} (y/N): ", message);
            Scanner scanner = new Scanner(System.in);
            String response = scanner.nextLine();
            return response != null && (response.equalsIgnoreCase("y") || response.equalsIgnoreCase("yes"));
        }
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
