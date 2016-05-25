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

package fr.pilato.elasticsearch.crawler.fs.util;

import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.pilato.elasticsearch.crawler.fs.ScanStatistic;
import fr.pilato.elasticsearch.crawler.fs.meta.MetaParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileOwnerAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.time.Instant;
import java.util.List;

public class FsCrawlerUtil extends MetaParser {
    public static final String INDEX_TYPE_DOC = "doc";
    public static final String INDEX_TYPE_FOLDER = "folder";

    static public final class Doc {
        public static final String CONTENT = "content";
        public static final String ATTACHMENT = "attachment";

        public static final String META = "meta";

        static public final class Meta {
            public static final String AUTHOR = "author";
            public static final String TITLE = "title";
            public static final String DATE = "date";
            public static final String KEYWORDS = "keywords";
        }

        public static final String FILE = "file";

        static public final class File {
            public static final String CONTENT_TYPE = "content_type";
            public static final String LAST_MODIFIED = "last_modified";
            public static final String INDEXING_DATE = "indexing_date";
            public static final String FILESIZE = "filesize";
            public static final String FILENAME = "filename";
            public static final String URL = "url";
            public static final String INDEXED_CHARS = "indexed_chars";
        }

        public static final String PATH = "path";

        static public final class Path {
            public static final String ENCODED = "encoded";
            public static final String ROOT = "root";
            public static final String VIRTUAL = "virtual";
            public static final String REAL = "real";
        }

        public static final String ATTRIBUTES = "attributes";

        static public final class Attributes {
            public static final String OWNER = "owner";
            public static final String GROUP = "group";
        }

    }

    static public final class Dir {
        public static final String NAME = "name";
        public static final String ENCODED = "encoded";
        public static final String ROOT = "root";
        public static final String VIRTUAL = "virtual";
        public static final String REAL = "real";
    }

    private static final Logger logger = LogManager.getLogger(FsCrawlerUtil.class);

    /**
     * Build the mapping for documents
     *
     * @param enableSource Do you want to enable _source?
     * @param storeSource  Do you want to store file source as binary BASE64 encoded?
     * @return a mapping
     * @throws Exception
     */
    public static ObjectNode buildFsFileMapping(boolean enableSource, boolean storeSource) throws Exception {
        ObjectNode root = prettyMapper.createObjectNode();

        // Manage _source
        if (!enableSource) {
            // Disable source
            root.putObject("_source").put("enabled", false);
        } else {
            if (storeSource) {
                // We store binary source as a stored field so we don't need it in _source
                root.putObject("_source").putArray("excludes").add(Doc.ATTACHMENT);
            }
        }

        ObjectNode properties = root.putObject("properties");

        // Doc content
        addAnalyzedString(properties, Doc.CONTENT);

        // Doc source
        if (storeSource) {
            addBinary(properties, Doc.ATTACHMENT);
        }

        // Meta
        ObjectNode meta = properties.putObject(Doc.META).putObject("properties");
        addAnalyzedString(meta, Doc.Meta.AUTHOR);
        addAnalyzedString(meta, Doc.Meta.TITLE);
        addDate(meta, Doc.Meta.DATE);
        addAnalyzedString(meta, Doc.Meta.KEYWORDS);
        // End Meta

        // File
        ObjectNode file = properties.putObject(Doc.FILE).putObject("properties");
        addNotAnalyzedString(file, Doc.File.CONTENT_TYPE);
        addDate(file, Doc.File.LAST_MODIFIED);
        addDate(file, Doc.File.INDEXING_DATE);
        addLong(file, Doc.File.FILESIZE);
        addLong(file, Doc.File.INDEXED_CHARS);
        addNotAnalyzedString(file, Doc.File.FILENAME);
        addNotIndexedString(file, Doc.File.URL);
        // End File

        // Path
        ObjectNode path = properties.putObject(Doc.PATH).putObject("properties");
        addNotAnalyzedString(path, Doc.Path.ENCODED);
        addNotAnalyzedString(path, Doc.Path.VIRTUAL);
        addNotAnalyzedString(path, Doc.Path.ROOT);
        addNotAnalyzedString(path, Doc.Path.REAL);
        // End Path

        // Attributes
        ObjectNode attributes = properties.putObject(Doc.ATTRIBUTES).putObject("properties");
        addNotAnalyzedString(attributes, Doc.Attributes.OWNER);
        addNotAnalyzedString(attributes, Doc.Attributes.GROUP);
        // End Attributes


        // End Type
        return root;
    }


    public static ObjectNode buildFsFolderMapping() throws Exception {
        ObjectNode root = prettyMapper.createObjectNode();

        ObjectNode properties = root.putObject("properties");

        addNotAnalyzedString(properties, Dir.NAME);
        addNotAnalyzedString(properties, Dir.REAL);
        addNotAnalyzedString(properties, Dir.ENCODED);
        addNotAnalyzedString(properties, Dir.ROOT);
        addNotAnalyzedString(properties, Dir.VIRTUAL);

        // End Type

        return root;
    }

    public static ObjectNode buildFsFileMapping() throws Exception {
        return buildFsFileMapping(true, false);
    }

    private static void addAnalyzedString(ObjectNode node, String fieldName) {
        node.putObject(fieldName)
                .put("type", "string")
                .put("store", "yes");
    }

    private static void addNotAnalyzedString(ObjectNode node, String fieldName) {
        node.putObject(fieldName)
                .put("type", "string")
                .put("store", "yes")
                .put("index", "not_analyzed");
    }

    private static void addNotIndexedString(ObjectNode node, String fieldName) {
        node.putObject(fieldName)
                .put("type", "string")
                .put("store", "yes")
                .put("index", "no");
    }

    private static void addDate(ObjectNode node, String fieldName) {
        node.putObject(fieldName)
                .put("type", "date")
                .put("format", "dateOptionalTime")
                .put("store", "yes");
    }

    private static void addLong(ObjectNode node, String fieldName) {
        node.putObject(fieldName)
                .put("type", "long")
                .put("store", "yes");
    }

    private static void addBinary(ObjectNode node, String fieldName) {
        node.putObject(fieldName)
                .put("type", "binary")
                .put("store", "yes");
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

        // Ignore temporary files
        if (filename.contains("~")) {
            logger.trace("filename contains ~");
            return false;
        }

        // No rules ? Fine, we index everything
        if ((includes == null || includes.isEmpty()) && (excludes == null || excludes.isEmpty())) {
            logger.trace("no rules");
            return true;
        }

        // Exclude rules : we know that whatever includes rules are, we should exclude matching files
        if (excludes != null) {
            for (String exclude : excludes) {
                String regex = exclude.replace("?", ".?").replace("*", ".*?");
                logger.trace("regex is [{}]", regex);
                if (filename.matches(regex)) {
                    logger.trace("does match exclude regex");
                    return false;
                }
            }
        }

        // Include rules : we should add document if it match include rules
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

    public static String computeVirtualPathName(ScanStatistic stats,
                                                String realPath) {
        if (realPath == null)
            return null;

        if (realPath.length() < stats.getRootPath().length())
            return "/";

        return realPath.substring(stats.getRootPath().length())
                .replace("\\", "/");
    }

    public static Instant getCreationTime(File file) {
        Instant time;
        try  {
            Path path = Paths.get(file.getAbsolutePath());
            BasicFileAttributes fileattr = Files
                    .getFileAttributeView(path, BasicFileAttributeView.class)
                    .readAttributes();
            time = Instant.ofEpochMilli(fileattr.creationTime().toMillis());
        } catch (Exception e) {
            time = null;
        }
        return time;
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
        try {
            final Path path = Paths.get(file.getAbsolutePath());
            final PosixFileAttributes posixFileAttributes = Files.getFileAttributeView(path, PosixFileAttributeView.class).readAttributes();
            return posixFileAttributes != null ? posixFileAttributes.group().getName() : null;
        } catch (Exception e) {
            logger.warn("Failed to determine 'group' of {}: {}", file, e.getMessage());
            return null;
        }
    }
}
