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

package fr.pilato.elasticsearch.river.fs.util;

import fr.pilato.elasticsearch.river.fs.river.ScanStatistic;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.support.XContentMapValues;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.*;
import java.nio.file.*;
import java.util.List;
import java.util.Map;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

public class FsRiverUtil {
    public static final String INDEX_TYPE_DOC = "doc";
    public static final String INDEX_TYPE_FOLDER = "folder";
    public static final String INDEX_TYPE_FS = "fsRiver";

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
    }

    static public final class Dir {
        public static final String NAME = "name";
        public static final String ENCODED = "encoded";
        public static final String ROOT = "root";
        public static final String VIRTUAL = "virtual";
        public static final String REAL = "real";
    }

    public static XContentBuilder buildFsFileMapping(String type) throws Exception {
        return buildFsFileMapping(type, true, false);
    }

    /**
     * Build the mapping for documents
     *
     * @param type         elasticsearch type you will use
     * @param enableSource Do you want to enable _source?
     * @param storeSource  Do you want to store file source as binary BASE64 encoded?
     * @return a mapping
     * @throws Exception
     */
    public static XContentBuilder buildFsFileMapping(String type, boolean enableSource, boolean storeSource) throws Exception {
        XContentBuilder xbMapping = jsonBuilder().prettyPrint().startObject();

        // Type
        xbMapping.startObject(type);

        // Manage _source
        if (!enableSource) {
            // Disable source
            xbMapping.startObject("_source").field("enabled", false).endObject();
        } else {
            if (storeSource) {
                // We store binary source as a stored field so we don't need it in _source
                xbMapping.startObject("_source").array("excludes", Doc.ATTACHMENT).endObject();
            }
        }

        xbMapping.startObject("properties");

        // Doc content
        addAnalyzedString(xbMapping, Doc.CONTENT);

        // Doc source
        if (storeSource) {
            addBinary(xbMapping, Doc.ATTACHMENT);
        }

        // Meta
        xbMapping.startObject(Doc.META).startObject("properties");
        addAnalyzedString(xbMapping, Doc.Meta.AUTHOR);
        addAnalyzedString(xbMapping, Doc.Meta.TITLE);
        addDate(xbMapping, Doc.Meta.DATE);
        addAnalyzedString(xbMapping, Doc.Meta.KEYWORDS);
        xbMapping.endObject().endObject(); // End Meta

        // File
        xbMapping.startObject(Doc.FILE).startObject("properties");
        addNotAnalyzedString(xbMapping, Doc.File.CONTENT_TYPE);
        addDate(xbMapping, Doc.File.LAST_MODIFIED);
        addDate(xbMapping, Doc.File.INDEXING_DATE);
        addLong(xbMapping, Doc.File.FILESIZE);
        addLong(xbMapping, Doc.File.INDEXED_CHARS);
        addNotAnalyzedString(xbMapping, Doc.File.FILENAME);
        addNotIndexedString(xbMapping, Doc.File.URL);
        xbMapping.endObject().endObject(); // End File

        // Path
        xbMapping.startObject(Doc.PATH).startObject("properties");
        addNotAnalyzedString(xbMapping, Doc.Path.ENCODED);
        addNotAnalyzedString(xbMapping, Doc.Path.VIRTUAL);
        addNotAnalyzedString(xbMapping, Doc.Path.ROOT);
        addNotAnalyzedString(xbMapping, Doc.Path.REAL);
        xbMapping.endObject().endObject(); // End Path

        xbMapping.endObject().endObject().endObject(); // End Type
        return xbMapping;
    }


    public static XContentBuilder buildFsFolderMapping(String type) throws Exception {
        XContentBuilder xbMapping = jsonBuilder().prettyPrint().startObject()
                // Type
                .startObject(type).startObject("properties");

        addNotAnalyzedString(xbMapping, Dir.NAME);
        addNotAnalyzedString(xbMapping, Dir.REAL);
        addNotAnalyzedString(xbMapping, Dir.ENCODED);
        addNotAnalyzedString(xbMapping, Dir.ROOT);
        addNotAnalyzedString(xbMapping, Dir.VIRTUAL);

        xbMapping.endObject().endObject().endObject(); // End Type

        return xbMapping;
    }

    public static XContentBuilder buildFsRiverMapping(String type) throws Exception {
        XContentBuilder xbMapping = jsonBuilder().prettyPrint().startObject()
                // Type
                .startObject(type).startObject("properties");

        addLong(xbMapping, "scanDate");

        // Folders
        xbMapping.startObject("folders").startObject("properties");
        addNotIndexedString(xbMapping, Doc.File.URL);
        xbMapping.endObject().endObject(); // End Folders

        xbMapping.endObject().endObject().endObject(); // End Type

        return xbMapping;
    }

    public static XContentBuilder buildFsFileMapping() throws Exception {
        return buildFsFileMapping(INDEX_TYPE_DOC);
    }

    public static XContentBuilder buildFsFolderMapping() throws Exception {
        return buildFsFolderMapping(INDEX_TYPE_FOLDER);
    }

    public static XContentBuilder buildFsRiverMapping() throws Exception {
        return buildFsRiverMapping(INDEX_TYPE_FS);
    }

    private static void addAnalyzedString(XContentBuilder xcb, String fieldName) throws IOException {
        xcb.startObject(fieldName)
                .field("type", "string")
                .field("store", "yes")
                .endObject();
    }

    private static void addNotAnalyzedString(XContentBuilder xcb, String fieldName) throws IOException {
        xcb.startObject(fieldName)
                .field("type", "string")
                .field("store", "yes")
                .field("index", "not_analyzed")
                .endObject();
    }

    private static void addNotIndexedString(XContentBuilder xcb, String fieldName) throws IOException {
        xcb.startObject(fieldName)
                .field("type", "string")
                .field("store", "yes")
                .field("index", "no")
                .endObject();
    }

    private static void addDate(XContentBuilder xcb, String fieldName) throws IOException {
        xcb.startObject(fieldName)
                .field("type", "date")
                .field("format", "dateOptionalTime")
                .field("store", "yes")
                .endObject();
    }

    private static void addLong(XContentBuilder xcb, String fieldName) throws IOException {
        xcb.startObject(fieldName)
                .field("type", "long")
                .field("store", "yes")
                .endObject();
    }

    private static void addBinary(XContentBuilder xcb, String fieldName) throws IOException {
        xcb.startObject(fieldName)
                .field("type", "binary")
                .endObject();
    }


    /**
     * Extract array from settings (array or ; delimited String)
     *
     * @param settings Settings
     * @param path     Path to definition : "fs.includes"
     */
    @SuppressWarnings("unchecked")
    public static String[] buildArrayFromSettings(Map<String, Object> settings, String path) {
        String[] includes;

        // We manage comma separated format and arrays
        if (XContentMapValues.isArray(XContentMapValues.extractValue(path, settings))) {
            List<String> includesarray = (List<String>) XContentMapValues.extractValue(path, settings);
            int i = 0;
            includes = new String[includesarray.size()];
            for (String include : includesarray) {
                includes[i++] = Strings.trimAllWhitespace(include);
            }
        } else {
            String includedef = (String) XContentMapValues.extractValue(path, settings);
            includes = Strings.commaDelimitedListToStringArray(Strings.trimAllWhitespace(includedef));
        }

        return Strings.removeDuplicateStrings(includes);
    }

    /**
     * We check if we can index the file or if we should ignore it
     *
     * @param filename The filename to scan
     * @param includes include rules, may be empty not null
     * @param excludes exclude rules, may be empty not null
     */
    public static boolean isIndexable(String filename, List<String> includes, List<String> excludes) {
        // No rules ? Fine, we index everything
        if (includes.isEmpty() && excludes.isEmpty()) return true;

        // Exclude rules : we know that whatever includes rules are, we should exclude matching files
        for (String exclude : excludes) {
            String regex = exclude.replace("?", ".?").replace("*", ".*?");
            if (filename.matches(regex)) return false;
        }

        // Include rules : we should add document if it match include rules
        if (includes.isEmpty()) return true;

        for (String include : includes) {
            String regex = include.replace("?", ".?").replace("*", ".*?");
            if (filename.matches(regex)) return true;
        }

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

    public static long getCreationTime(File file) {
        long time;
        try  {
            Path path = Paths.get(file.getAbsolutePath());
            BasicFileAttributes fileattr = Files
                    .getFileAttributeView(path, BasicFileAttributeView.class)
                    .readAttributes();
            time = fileattr.creationTime().toMillis();
        } catch (Exception e) {
            time = 0L;
        }
        return time;
    }
}
