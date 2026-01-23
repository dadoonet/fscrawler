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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class FsCrawlerUtil {
    public static final String INDEX_SUFFIX_DOCS = "_docs";
    public static final String INDEX_SUFFIX_FOLDER = "_folder";

    private static final Logger logger = LogManager.getLogger();

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
    public static boolean isIndexable(final boolean directory, final String filename, final List<String> includes, final List<String> excludes) {
        logger.trace("directory = [{}], filename = [{}], includes = [{}], excludes = [{}]", directory, filename, includes, excludes);

        String originalFilename = filename;

        // When the current file is a directory, we need to append a / to the filename
        if (directory && !filename.endsWith("/")) {
            originalFilename += "/";
        }

        boolean isIndexable = isIndexable(originalFilename, includes, excludes);

        // It can happen that we have a dir "foo" which does not match the included name like "*.txt"
        // We need to go in it unless it has been explicitly excluded by the user
        if (directory && !isExcluded(originalFilename, excludes)) {
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
        logger.trace("filename = [{}], excludes = [{}]", filename, excludes);

        // No rules ? Fine, we index everything
        if (excludes == null || excludes.isEmpty()) {
            logger.trace("no rules = no exclusion");
            return false;
        }

        return isMatching(filename, excludes, "exclusion");
    }

    /**
     * We check if we can index the file or if we should ignore it
     *
     * @param filename The filename to scan
     * @param includes include rules, may be empty not null
     */
    public static boolean isIncluded(String filename, List<String> includes) {
        logger.trace("filename = [{}], includes = [{}]", filename, includes);

        // No rules ? Fine, we index everything
        if (includes == null || includes.isEmpty()) {
            logger.trace("no rules = include all");
            return true;
        }

        return isMatching(filename, includes, "inclusion");
    }

    public static boolean isMatching(final String filename, final List<String> matches, final String type) {
        logger.trace("checking {} for filename = [{}], matches = [{}]", type, filename, matches);

        // We are using a linux style virtual path, meaning that if we have a windows path, we need to convert it
        // to a linux path
        String virtualPath = filename.replace("\\", "/");

        for (String match : matches) {
            String regex = match.toLowerCase().replace("?", ".?").replace("*", ".*");
            String filenameLowerCase = virtualPath.toLowerCase();
            if (filenameLowerCase.matches(regex)) {
                logger.trace("✅ [{}] does match {} regex [{}] (was [{}])", filenameLowerCase, type, regex, match);
                return true;
            } else {
                logger.trace("❌ [{}] does not match {} regex [{}] (was [{}])", filenameLowerCase, type, regex, match);
            }
        }

        logger.trace("does not match any pattern for {}", type);
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
                logger.trace("Filter [{}] is not matching.", filter);
                return false;
            } else {
                logger.trace("Filter [{}] is matching.", filter);
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
        if (rootPath == null || realPath == null) {
            return getPathSeparator(rootPath);
        }

        String defaultSeparator = getPathSeparator(rootPath);
        String normalizedRoot = normalizeForComparison(rootPath);
        String normalizedReal = normalizeForComparison(realPath);

        String result = defaultSeparator;
        if (shouldResolveRelativePath(normalizedRoot, normalizedReal)) {
            result = resolveRelativePath(normalizedRoot, normalizedReal, defaultSeparator);
        }

        if (needsSeparatorConversion(defaultSeparator)) {
            result = convertSeparator(result, defaultSeparator);
        }

        logger.trace("computeVirtualPathName({}, {}) = {}", rootPath, realPath, result);
        return result;
    }

    private static boolean shouldResolveRelativePath(String normalizedRoot, String normalizedReal) {
        if (normalizedRoot.isEmpty()) {
            return false;
        }
        boolean matches;
        if (OsValidator.WINDOWS) {
            matches = normalizedReal.regionMatches(true, 0, normalizedRoot, 0, normalizedRoot.length());
        } else {
            matches = normalizedReal.startsWith(normalizedRoot);
        }
        return matches && hasBoundary(normalizedRoot, normalizedReal);
    }

    private static String resolveRelativePath(String normalizedRoot, String normalizedReal, String defaultSeparator) {
        if ("/".equals(normalizedRoot)) {
            // "/" is very common for FTP
            return normalizedReal;
        }
        String suffix = normalizedReal.substring(normalizedRoot.length());
        return suffix.isEmpty() ? defaultSeparator : suffix;
    }

    private static boolean needsSeparatorConversion(String defaultSeparator) {
        return !"/".equals(defaultSeparator);
    }

    private static String convertSeparator(String value, String defaultSeparator) {
        return value.replace('/', defaultSeparator.charAt(0));
    }

    private static String normalizeForComparison(String path) {
        if (path == null) {
            return "";
        }
        return path.replace('\\', '/');
    }

    private static boolean hasBoundary(String normalizedRoot, String normalizedReal) {
        if (normalizedRoot == null) {
            return false;
        }
        if ("/".equals(normalizedRoot)) {
            return true;
        }
        if (normalizedReal.length() == normalizedRoot.length()) {
            return true;
        }
        if (normalizedReal.length() > normalizedRoot.length()) {
            char c = normalizedReal.charAt(normalizedRoot.length());
            return c == '/';
        }
        return false;
    }

    private static LocalDateTime getFileTime(File file, Function<BasicFileAttributes, FileTime> timeFunction) {
        try  {
            Path path = Paths.get(file.getAbsolutePath());
            BasicFileAttributes fileattr = Files.getFileAttributeView(path, BasicFileAttributeView.class).readAttributes();
            return LocalDateTime.ofInstant(timeFunction.apply(fileattr).toInstant(), ZoneId.systemDefault());
        } catch (Exception e) {
            return null;
        }
    }

    public static LocalDateTime getCreationTime(File file) {
        return getFileTime(file, BasicFileAttributes::creationTime);
    }

    public static LocalDateTime getModificationTime(File file) {
        return getFileTime(file, BasicFileAttributes::lastModifiedTime);
    }

    public static LocalDateTime getLastAccessTime(File file) {
        return getFileTime(file, BasicFileAttributes::lastAccessTime);
    }

    public static LocalDateTime getModificationOrCreationTime(File file) {
        LocalDateTime lastAccessTime = getFileTime(file, BasicFileAttributes::lastAccessTime);
        if (lastAccessTime != null) {
            return lastAccessTime;
        } else {
            return getFileTime(file, BasicFileAttributes::creationTime);
        }
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
            logger.debug("Determining 'group' is skipped for file [{}] on [{}]", file, OsValidator.OS);
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
            logger.trace("Determining 'group' is skipped for file [{}] on [{}]", file, OsValidator.OS);
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

    /**
     * Determines Access Control List entries for the given file.
     */
    public static List<FileAcl> getFileAcls(final Path path) {
        logger.trace("Resolving ACLs for [{}]", path);
        try {
            final AclFileAttributeView aclView = Files.getFileAttributeView(path, AclFileAttributeView.class);
            if (aclView == null) {
                logger.trace("Determining 'acl' is skipped for file [{}] as ACL view is not supported", path);
                return Collections.emptyList();
            }

            final List<AclEntry> aclEntries = aclView.getAcl();
            if (aclEntries == null || aclEntries.isEmpty()) {
                logger.trace("No ACL entries found for [{}]", path);
                return Collections.emptyList();
            }

            final List<FileAcl> result = new ArrayList<>(aclEntries.size());
            for (AclEntry entry : aclEntries) {
                final String principal = entry.principal() != null ? entry.principal().getName() : null;
                final String type = entry.type() != null ? entry.type().name() : null;
                final List<String> permissions = entry.permissions().stream()
                        .map(AclEntryPermission::name)
                        .sorted()
                        .collect(Collectors.toList());
                final List<String> flags = entry.flags().stream()
                        .map(AclEntryFlag::name)
                        .sorted()
                        .collect(Collectors.toList());
                result.add(new FileAcl(principal, type, permissions, flags));
            }
            logger.debug("ACL entries found for [{}]: {}", path, result);
            return result;
        } catch (Exception e) {
            logger.debug("Failed to determine 'acl' of {}: {}", path, e);
            return Collections.emptyList();
        }
    }

    public static String computeAclHash(List<FileAcl> acls) {
        if (acls == null || acls.isEmpty()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            acls.stream()
                    .sorted(Comparator
                            .comparing(FileAcl::getPrincipal, Comparator.nullsFirst(String::compareTo))
                            .thenComparing(FileAcl::getType, Comparator.nullsFirst(String::compareTo))
                            .thenComparing(a -> joinAndSort(a.getPermissions()))
                            .thenComparing(a -> joinAndSort(a.getFlags())))
                    .forEach(entry -> {
                        updateDigest(digest, entry.getPrincipal());
                        updateDigest(digest, entry.getType());
                        updateDigest(digest, joinAndSort(entry.getPermissions()));
                        updateDigest(digest, joinAndSort(entry.getFlags()));
                    });
            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Unable to compute ACL hash", e);
        }
    }

    private static void updateDigest(MessageDigest digest, String value) {
        if (value == null) {
            digest.update((byte) 0);
        } else {
            digest.update(value.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String joinAndSort(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        List<String> copy = new ArrayList<>(values);
        copy.sort(String::compareTo);
        return String.join(",", copy);
    }

    private static String toHex(byte[] data) {
        StringBuilder builder = new StringBuilder(data.length * 2);
        for (byte b : data) {
            builder.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        }
        return builder.toString();
    }

    public static int toOctalPermission(boolean read, boolean write, boolean execute) {
        return (read ? 4 : 0) + (write ? 2 : 0) + (execute ? 1 : 0);
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

    public static int extractMinorVersion(String version) {
        return Integer.parseInt(version.split("\\.")[1]);
    }
}
