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
package fr.pilato.elasticsearch.crawler.fs.framework;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.CopyOption;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileOwnerAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.TimeZone;

public class FsCrawlerUtil extends MetaParser {
    public static final String INDEX_SUFFIX_FOLDER = "_folder";
    public static final String INDEX_SETTINGS_FILE = "_settings";
    public static final String INDEX_SETTINGS_FOLDER_FILE = "_settings_folder";

    private static final Logger logger = LogManager.getLogger(FsCrawlerUtil.class);

    /**
     * Reads a mapping from config/_default/version/type.json file
     *
     * @param config Root dir where we can find the configuration (default to ~/.fscrawler)
     * @param version Elasticsearch major version number (only major digit is kept so for 2.3.4 it will be 2)
     * @param type The expected type (will be expanded to type.json)
     * @return the mapping
     * @throws URISyntaxException If URI is malformed
     * @throws IOException If the mapping can not be read
     */
    public static String readDefaultJsonVersionedFile(Path config, String version, String type) throws URISyntaxException, IOException {
        Path defaultConfigDir = config.resolve("_default");
        try {
            return readJsonVersionedFile(defaultConfigDir, version, type);
        } catch (NoSuchFileException e) {
            throw new IllegalArgumentException("Mapping file " + type + ".json does not exist for elasticsearch version " + version +
                    " in [" + defaultConfigDir + "] dir");
        }
    }

    /**
     * Reads a mapping from dir/version/type.json file
     *
     * @param dir Directory containing mapping files per major version
     * @param version Elasticsearch major version number (only major digit is kept so for 2.3.4 it will be 2)
     * @param type The expected type (will be expanded to type.json)
     * @return the mapping
     * @throws IOException If the mapping can not be read
     */
    private static String readJsonVersionedFile(Path dir, String version, String type) throws IOException {
        Path file = dir.resolve(version).resolve(type + ".json");
        return new String(Files.readAllBytes(file), "UTF-8");
    }

    /**
     * Reads a Json file from dir/version/filename.json file.
     * If not found, read from ~/.fscrawler/_default/version/filename.json
     *
     * @param dir Directory which might contain filename files per major version (job dir)
     * @param config Root dir where we can find the configuration (default to ~/.fscrawler)
     * @param version Elasticsearch major version number (only major digit is kept so for 2.3.4 it will be 2)
     * @param filename The expected filename (will be expanded to filename.json)
     * @return the mapping
     * @throws URISyntaxException If URI is malformed
     * @throws IOException If the mapping can not be read
     */
    public static String readJsonFile(Path dir, Path config, String version, String filename) throws URISyntaxException, IOException {
        try {
            return readJsonVersionedFile(dir, version, filename);
        } catch (NoSuchFileException e) {
            // We fall back to default mappings in config dir
            return readDefaultJsonVersionedFile(config, version, filename);
        }
    }

    /**
     * We check if we can index the file or if we should ignore it
     *
     * @param filename The filename to scan
     * @param includes include rules, may be empty not null
     * @param excludes exclude rules, may be empty not null
     */
    public static boolean isIndexable(String filename, List<String> includes, List<String> excludes) {
        logger.debug("filename = [{}], includes = [{}], excludes = [{}]", filename, includes, excludes);

        boolean excluded = isExcluded(filename, excludes);
        if (excluded) return false;

        return isIncluded(filename, includes);
    }

    /**
     * We check if we can index the file or if we should ignore it
     *
     * @param filename The filename to scan
     * @param excludes exclude rules, may be empty not null
     */
    public static boolean isExcluded(String filename, List<String> excludes) {
        logger.debug("filename = [{}], excludes = [{}]", filename, excludes);

        // No rules ? Fine, we index everything
        if (excludes == null || excludes.isEmpty()) {
            logger.trace("no rules");
            return false;
        }

        // Exclude rules : we know that whatever includes rules are, we should exclude matching files
        for (String exclude : excludes) {
            String regex = exclude.replace("?", ".?").replace("*", ".*?");
            logger.trace("regex is [{}]", regex);
            if (filename.matches(regex)) {
                logger.trace("does match exclude regex");
                return true;
            }
        }

        logger.trace("does not match any exclude pattern");
        return false;
    }

    /**
     * We check if we can index the file or if we should ignore it
     *
     * @param filename The filename to scan
     * @param includes include rules, may be empty not null
     */
    public static boolean isIncluded(String filename, List<String> includes) {
        logger.debug("filename = [{}], includes = [{}]", filename, includes);

        // No rules ? Fine, we index everything
        if (includes == null || includes.isEmpty()) {
            logger.trace("no include rules");
            return true;
        }

        for (String include : includes) {
            String regex = include.replace("?", ".?").replace("*", ".*?");
            logger.trace("regex is [{}]", regex);
            if (filename.matches(regex)) {
                logger.trace("does match include regex");
                return true;
            }
        }

        logger.trace("does not match any include pattern");
        return false;
    }

    public static String computeVirtualPathName(String rootPath, String realPath) {
        String result = "/";
        if (realPath != null && realPath.length() > rootPath.length()) {
            result = realPath.substring(rootPath.length())
                    .replace("\\", "/");
        }

        logger.debug("computeVirtualPathName({}, {}) = {}", rootPath, realPath, result);
        return result;
    }

    public static LocalDateTime getCreationTime(File file) {
        LocalDateTime time;
        try  {
            Path path = Paths.get(file.getAbsolutePath());
            BasicFileAttributes fileattr = Files
                    .getFileAttributeView(path, BasicFileAttributeView.class)
                    .readAttributes();
            time = LocalDateTime.ofInstant(fileattr.creationTime().toInstant(), ZoneId.systemDefault());
        } catch (Exception e) {
            time = null;
        }
        return time;
    }

    public static Date localDateTimeToDate(LocalDateTime ldt) {
        return Date.from(ldt.atZone(TimeZone.getDefault().toZoneId()).toInstant());
    }

    public static Date localDateTimeToDate(String sDate) {
        if (sDate == null) {
            return null;
        }
        return localDateTimeToDate(LocalDateTime.parse(sDate, DateTimeFormatter.ISO_DATE_TIME));
    }

    public static String getFileExtension(File file) {
        return FilenameUtils.getExtension(file.getAbsolutePath()).toLowerCase();
    }

    /**
     * Determines the 'owner' of the file.
     */
    public static String getOwnerName(final File file) {
        try {
            final Path path = Paths.get(file.getAbsolutePath());
            final FileOwnerAttributeView ownerAttributeView = Files.getFileAttributeView(path, FileOwnerAttributeView.class);
            return ownerAttributeView != null ? ownerAttributeView.getOwner().getName() : null;
        }
        catch(Exception e) {
            logger.warn("Failed to determine 'owner' of {}: {}", file, e.getMessage());
            return null;
        }
    }

    /**
     * Determines the 'group' of the file. Please note that 'group' is not
     * available of Windows OS.
     */
    public static String getGroupName(final File file) {
        if (OsValidator.windows) {
            logger.trace("Determining 'group' is skipped for file [{}] on [{}]", file, OsValidator.OS);
            return null;
        }
        try {
            final Path path = Paths.get(file.getAbsolutePath());
            final PosixFileAttributes posixFileAttributes = Files.getFileAttributeView(path, PosixFileAttributeView.class).readAttributes();
            return posixFileAttributes != null ? posixFileAttributes.group().getName() : null;
        } catch (Exception e) {
            logger.warn("Failed to determine 'group' of {}: {}", file, e.getMessage());
            return null;
        }
    }

    private static final String CLASSPATH_RESOURCES_ROOT = "/fr/pilato/elasticsearch/crawler/fs/_default/";
    public static final String[] MAPPING_RESOURCES = {
            "2/_settings.json", "2/_settings_folder.json",
            "5/_settings.json", "5/_settings_folder.json",
            "6/_settings.json", "6/_settings_folder.json"
    };

    /**
     * Copy default resources files which are available as project resources under
     * fr.pilato.elasticsearch.crawler.fs._default package to a given configuration path
     * under a _default sub directory.
     * @param configPath The config path which is by default .fscrawler
     * @throws IOException If copying does not work
     */
    public static void copyDefaultResources(Path configPath) throws IOException, URISyntaxException {
        Path targetResourceDir = configPath.resolve("_default");

        for (String filename : MAPPING_RESOURCES) {
            Path target = targetResourceDir.resolve(filename);
            if (Files.exists(target)) {
                logger.debug("Mapping [{}] already exists", filename);
            } else {
                logger.debug("Copying [{}]...", filename);
                copyResourceFile(CLASSPATH_RESOURCES_ROOT + filename, target);
            }
        }
    }

    /**
     * Move a Legacy resource to another destination if needed
     * @param legacy Legacy file
     * @param destinationFile New file name (directory must exist)
     * @throws IOException In case moving file did not work
     */
    public static void moveLegacyResource(Path legacy, Path destinationFile) throws IOException {
        if (Files.exists(legacy)) {
            logger.debug("Found a legacy file at [{}]", legacy);
            Files.move(legacy, destinationFile);
            logger.info("Moved Legacy file from [{}] to [{}]", legacy, destinationFile);
        }
    }

    /**
     * Copy a single resource file from the classpath or from a JAR.
     * @param target The target
     * @throws IOException If copying does not work
     */
    public static void copyResourceFile(String source, Path target) throws IOException {
        InputStream resource = FsCrawlerUtil.class.getResourceAsStream(source);
        FileUtils.copyInputStreamToFile(resource, target.toFile());
    }

    /**
     * Copy files from a source to a target
     * under a _default sub directory.
     * @param source The source dir
     * @param target The target dir
     * @param options Potential options
     * @throws IOException If copying does not work
     */
    public static void copyDirs(Path source, Path target, CopyOption... options) throws IOException {
        if (Files.notExists(target)) {
            Files.createDirectory(target);
        }

        logger.debug("  --> Copying resources from [{}]", source);
        if (Files.notExists(source)) {
            throw new RuntimeException(source + " doesn't seem to exist.");
        }

        Files.walkFileTree(source, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
                new InternalFileVisitor(source, target, options));

        logger.debug("  --> Resources ready in [{}]", target);
    }

    private static class InternalFileVisitor extends SimpleFileVisitor<Path> {

        private final Path fromPath;
        private final Path toPath;
        private final CopyOption[] copyOption;

        public InternalFileVisitor(Path fromPath, Path toPath, CopyOption... copyOption) {
            this.fromPath = fromPath;
            this.toPath = toPath;
            this.copyOption = copyOption;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {

            Path targetPath = toPath.resolve(fromPath.relativize(dir));
            if(!Files.exists(targetPath)){
                Files.createDirectory(targetPath);
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            try {
                Files.copy(file, toPath.resolve(fromPath.relativize(file)), copyOption);
            } catch (FileAlreadyExistsException ignored) {
                // The file already exists we just ignore it
            }
            return FileVisitResult.CONTINUE;
        }
    }

    public static String buildUrl(String scheme, String host, int port) {
        return scheme + "://" + host + ":" + port;
    }
}
