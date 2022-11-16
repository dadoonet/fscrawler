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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.CopyOption;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileOwnerAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Pattern;

public class FsCrawlerUtil {
    public static final String INDEX_SUFFIX_FOLDER = "_folder";
    public static final String INDEX_SETTINGS_FILE = "_settings";
    public static final String INDEX_WORKPLACE_SEARCH_SETTINGS_FILE = "_wpsearch_settings";
    public static final String INDEX_SETTINGS_FOLDER_FILE = "_settings_folder";

    private static final Logger logger = LogManager.getLogger(FsCrawlerUtil.class);

    /**
     * Reads a mapping from config/_default/version/type.json file
     *
     * @param config Root dir where we can find the configuration (default to ~/.fscrawler)
     * @param version Elasticsearch major version number (only major digit is kept so for 2.3.4 it will be 2)
     * @param type The expected type (will be expanded to type.json)
     * @return the mapping
     * @throws IOException If the mapping can not be read
     */
    public static String readDefaultJsonVersionedFile(Path config, int version, String type) throws IOException {
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
    private static String readJsonVersionedFile(Path dir, int version, String type) throws IOException {
        Path file = dir.resolve("" + version).resolve(type + ".json");
        return Files.readString(file);
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
     * @throws IOException If the mapping can not be read
     */
    public static String readJsonFile(Path dir, Path config, int version, String filename) throws IOException {
        try {
            return readJsonVersionedFile(dir, version, filename);
        } catch (NoSuchFileException e) {
            // We fall back to default mappings in config dir
            return readDefaultJsonVersionedFile(config, version, filename);
        }
    }

    /**
     * Merges two json nodes into one. Main node overwrites the update node's values in the case if both nodes have the same key.
     * @param mainNode Json node that rules over update node
     * @param updateNode Json node that is subordinate to main node
     * @return the merged nodes
     */
    public static JsonNode merge(JsonNode mainNode, JsonNode updateNode) {
        Iterator<String> fieldNames = updateNode.fieldNames();

        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            JsonNode jsonNode = mainNode.get(fieldName);

            if (jsonNode != null) {
                if (jsonNode.isObject()) {
                    merge(jsonNode, updateNode.get(fieldName));
                } else if (jsonNode.isArray()) {
                    for (int i = 0; i < jsonNode.size(); i++) {
                        merge(jsonNode.get(i), updateNode.get(fieldName).get(i));
                    }
                }
            } else {
                if (mainNode instanceof ObjectNode) {
                    // Overwrite field
                    JsonNode value = updateNode.get(fieldName);
                    ((ObjectNode) mainNode).set(fieldName, value);
                }
            }
        }

        return mainNode;
    }

    /**
     * We check if we can index the file or if we should ignore it
     *
     * @param filename The filename to scan
     * @param includes include rules, may be empty not null
     * @param excludes exclude rules, may be empty not null
     */
    private static boolean isIndexable(String filename, List<String> includes, List<String> excludes) {
        boolean excluded = isExcluded(filename, excludes);
        if (excluded) return false;

        return isIncluded(filename, includes);
    }

    /**
     * We check if we can index the file or if we should ignore it
     *
     * @param directory true if the current file is a directory, false in other case (actual file)
     * @param filename The filename to scan
     * @param includes include rules, may be empty not null
     * @param excludes exclude rules, may be empty not null
     */
    public static boolean isIndexable(boolean directory, String filename, List<String> includes, List<String> excludes) {
        logger.debug("directory = [{}], filename = [{}], includes = [{}], excludes = [{}]", directory, filename, includes, excludes);

        boolean isIndexable = isIndexable(filename, includes, excludes);

        // It can happen that we have a dir "foo" which does not match the included name like "*.txt"
        // We need to go in it unless it has been explicitly excluded by the user
        if (directory && !isExcluded(filename, excludes)) {
            isIndexable = true;
        }

        return isIndexable;
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
            String regex = exclude.toLowerCase().replace("?", ".?").replace("*", ".*?");
            logger.trace("regex is [{}]", regex);
            if (filename.toLowerCase().matches(regex)) {
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
            String regex = include.toLowerCase().replace("?", ".?").replace("*", ".*?");
            logger.trace("regex is [{}]", regex);
            if (filename.toLowerCase().matches(regex)) {
                logger.trace("does match include regex");
                return true;
            }
        }

        logger.trace("does not match any include pattern");
        return false;
    }

    /**
     * We check if we can index the content or skip it
     *
     * @param content Content to parse
     * @param filters regular expressions that all needs to match if we want to index. If empty
     *                we consider it always matches.
     */
    public static boolean isIndexable(String content, List<String> filters) {
        if (isNullOrEmpty(content)) {
            logger.trace("Null or empty content always matches.");
            return true;
        }

        if (filters == null || filters.isEmpty()) {
            logger.trace("No pattern always matches.");
            return true;
        }

        logger.trace("content = [{}], filters = {}", content, filters);
        for (String filter : filters) {
            Pattern pattern = Pattern.compile(filter, Pattern.MULTILINE | Pattern.UNIX_LINES);
            logger.trace("Testing filter [{}]", filter);
            if (!pattern.matcher(content).find()) {
                logger.trace("Filter [{}] is not matching.", filter);
                return false;
            } else {
                logger.trace("Filter [{}] is matching.", filter);
            }
        }

        return true;
    }

    public static String getPathSeparator(String path) {
        if (path.contains("/") && !path.contains("\\")) {
            return "/";
        }

        if (!path.contains("/") && (path.contains("\\") || path.contains(":"))) {
            return "\\";
        }

        return File.separator;
    }

    public static String computeRealPathName(String _dirname, String filename) {
        // new File(dirname, filename).toString() is not suitable for server
        String separator = getPathSeparator(_dirname);
        String dirname = _dirname.endsWith(separator) ? _dirname : _dirname.concat(separator);
        return dirname + filename;
    }

    public static String computeVirtualPathName(String rootPath, String realPath) {
        String result = getPathSeparator(rootPath);
        if (realPath.startsWith(rootPath) && realPath.length() > rootPath.length()) {
            if (rootPath.equals("/")) {
                // "/" is very common for FTP
                result = realPath;
            } else {
                result = realPath.substring(rootPath.length());
            }
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

    public static LocalDateTime getModificationTime(File file) {
        LocalDateTime time;
        try  {
            Path path = Paths.get(file.getAbsolutePath());
            BasicFileAttributes fileattr = Files
                    .getFileAttributeView(path, BasicFileAttributeView.class)
                    .readAttributes();
            time = LocalDateTime.ofInstant(fileattr.lastModifiedTime().toInstant(), ZoneId.systemDefault());
        } catch (Exception e) {
            time = null;
        }
        return time;
    }

    public static LocalDateTime getLastAccessTime(File file) {
        LocalDateTime time;
        try  {
            Path path = Paths.get(file.getAbsolutePath());
            BasicFileAttributes fileattr = Files
                    .getFileAttributeView(path, BasicFileAttributeView.class)
                    .readAttributes();
            time = LocalDateTime.ofInstant(fileattr.lastAccessTime().toInstant(), ZoneId.systemDefault());
        } catch (Exception e) {
            time = null;
        }
        return time;
    }

    public static Date localDateTimeToDate(LocalDateTime ldt) {
        if (ldt == null) {
            return null;
        }
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
        if (OsValidator.WINDOWS) {
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

    /**
     * Determines file permissions.
     */
    public static int getFilePermissions(final File file) {
        if (OsValidator.WINDOWS) {
            logger.trace("Determining 'group' is skipped for file [{}] on [{}]", file, OsValidator.OS);
            return -1;
        }
        try {
            final Path path = Paths.get(file.getAbsolutePath());
            PosixFileAttributes attrs = Files.getFileAttributeView(path, PosixFileAttributeView.class).readAttributes();
            Set<PosixFilePermission> permissions = attrs.permissions();
            int user = toOctalPermission(
                    permissions.contains(PosixFilePermission.OWNER_READ),
                    permissions.contains(PosixFilePermission.OWNER_WRITE),
                    permissions.contains(PosixFilePermission.OWNER_EXECUTE));
            int group = toOctalPermission(
                    permissions.contains(PosixFilePermission.GROUP_READ),
                    permissions.contains(PosixFilePermission.GROUP_WRITE),
                    permissions.contains(PosixFilePermission.GROUP_EXECUTE));
            int others = toOctalPermission(
                    permissions.contains(PosixFilePermission.OTHERS_READ),
                    permissions.contains(PosixFilePermission.OTHERS_WRITE),
                    permissions.contains(PosixFilePermission.OTHERS_EXECUTE));

            return user * 100 + group * 10 + others;
        }
        catch(Exception e) {
            logger.warn("Failed to determine 'permissions' of {}: {}", file, e.getMessage());
            return -1;
        }
    }

    public static int toOctalPermission(boolean read, boolean write, boolean execute) {
        return (read ? 4 : 0) + (write ? 2 : 0) + (execute ? 1 : 0);
    }

    private static final String CLASSPATH_RESOURCES_ROOT = "/fr/pilato/elasticsearch/crawler/fs/_default/";
    public static final String[] MAPPING_RESOURCES = {
            "6/_settings.json", "6/_settings_folder.json",
            "7/_settings.json", "7/_settings_folder.json", "7/_wpsearch_settings.json",
            "8/_settings.json", "8/_settings_folder.json", "8/_wpsearch_settings.json"
    };

    /**
     * Copy default resources files which are available as project resources under
     * fr.pilato.elasticsearch.crawler.fs._default package to a given configuration path
     * under a _default subdirectory.
     * @param configPath The config path which is by default .fscrawler
     * @throws IOException If copying does not work
     */
    public static void copyDefaultResources(Path configPath) throws IOException {
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
     * Copy a single resource file from the classpath or from a JAR.
     * @param target The target
     * @throws IOException If copying does not work
     */
    public static void copyResourceFile(String source, Path target) throws IOException {
        InputStream resource = FsCrawlerUtil.class.getResourceAsStream(source);
        FileUtils.copyInputStreamToFile(resource, target.toFile());
    }

    /**
     * Read a property file from the class loader
     * @param resource Resource name
     * @return The properties loaded
     */
    public static Properties readPropertiesFromClassLoader(String resource) {
        Properties properties = new Properties();
        try {
            properties.load(FsCrawlerUtil.class.getClassLoader().getResourceAsStream(resource));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return properties;
    }

    /**
     * Copy files from a source to a target
     * under a _default subdirectory.
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

    /**
     * Unzip a jar file
     * @param jarFile Jar file url like file:/path/to/foo.jar
     * @param destination Directory where we want to extract the content to
     * @throws IOException In case of any IO problem
     */
    public static void unzip(String jarFile, Path destination) throws IOException {
        Map<String, String> zipProperties = new HashMap<>();
        /* We want to read an existing ZIP File, so we set this to false */
        zipProperties.put("create", "false");
        zipProperties.put("encoding", "UTF-8");
        URI zipFile = URI.create("jar:" + jarFile);

        try (FileSystem zipfs = FileSystems.newFileSystem(zipFile, zipProperties)) {
            Path rootPath = zipfs.getPath("/");
            Files.walkFileTree(rootPath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    Path targetPath = destination.resolve(rootPath.relativize(dir).toString());
                    if (!Files.exists(targetPath)) {
                        Files.createDirectory(targetPath);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.copy(file, destination.resolve(rootPath.relativize(file).toString()), StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    public static boolean isNullOrEmpty(String string) {
        return string == null || string.isEmpty();
    }

    public static void createDirIfMissing(Path root) {
        try {
            if (Files.notExists(root)) {
                Files.createDirectory(root);
            }
        } catch (IOException ignored) {
            logger.error("Failed to create config dir");
        }
    }

    /**
     * Format the double value with a single decimal points, trimming trailing '.0'.
     */
    public static String format1Decimals(double value, String suffix) {
        String p = String.valueOf(value);
        int ix = p.indexOf('.') + 1;
        int ex = p.indexOf('E');
        char fraction = p.charAt(ix);
        if (fraction == '0') {
            if (ex != -1) {
                return p.substring(0, ix - 1) + p.substring(ex) + suffix;
            } else {
                return p.substring(0, ix - 1) + suffix;
            }
        } else {
            if (ex != -1) {
                return p.substring(0, ix) + fraction + p.substring(ex) + suffix;
            } else {
                return p.substring(0, ix) + fraction + suffix;
            }
        }
    }

    /**
     * Compare if a file size is strictly under a given limit
     * @param limit Limit. If null, we consider that there is no limit, and we return true.
     * @param fileSizeAsBytes File size
     * @return true if under the limit. false otherwise.
     */
    public static boolean isFileSizeUnderLimit(ByteSizeValue limit, long fileSizeAsBytes) {
        boolean result = true;
        if (limit != null) {
            // We check the file size to avoid indexing too big files
            ByteSizeValue fileSize = new ByteSizeValue(fileSizeAsBytes);
            int compare = fileSize.compareTo(limit);
            result = compare <= 0;
            logger.debug("Comparing file size [{}] with current limit [{}] -> {}", fileSize, limit,
                    result ? "under limit" : "above limit");
        }

        return result;
    }

    public static int extractMajorVersion(String version) {
        return Integer.parseInt(version.split("\\.")[0]);
    }

    public static String extractMinorVersion(String version) {
        return version.split("\\.")[1];
    }
}
