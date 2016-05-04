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

import fr.pilato.elasticsearch.crawler.fs.client.*;
import fr.pilato.elasticsearch.crawler.fs.fileabstractor.FileAbstractModel;
import fr.pilato.elasticsearch.crawler.fs.fileabstractor.FileAbstractor;
import fr.pilato.elasticsearch.crawler.fs.fileabstractor.FileAbstractorFile;
import fr.pilato.elasticsearch.crawler.fs.fileabstractor.FileAbstractorSSH;
import fr.pilato.elasticsearch.crawler.fs.meta.doc.Doc;
import fr.pilato.elasticsearch.crawler.fs.meta.doc.DocParser;
import fr.pilato.elasticsearch.crawler.fs.meta.doc.PathParser;
import fr.pilato.elasticsearch.crawler.fs.meta.job.FsJob;
import fr.pilato.elasticsearch.crawler.fs.meta.job.FsJobFileHandler;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.Elasticsearch;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.Fs;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.FsSettingsFileHandler;
import fr.pilato.elasticsearch.crawler.fs.util.FsCrawlerUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tika.metadata.Metadata;

import java.io.*;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static fr.pilato.elasticsearch.crawler.fs.TikaInstance.tika;

/**
 * @author dadoonet (David Pilato)
 */
public class FsCrawlerImpl {

    public static final int REQUEST_SIZE = 10000;

    public static final class PROTOCOL {
        public static final String LOCAL = "local";
        public static final String SSH = "ssh";
        public static final int SSH_PORT = 22;
    }

    private static final Logger logger = LogManager.getLogger(FsCrawlerImpl.class);

    private static final String PATH_ENCODED = FsCrawlerUtil.Doc.PATH + "." + FsCrawlerUtil.Doc.Path.ENCODED;
    private static final String FILE_FILENAME = FsCrawlerUtil.Doc.FILE + "." + FsCrawlerUtil.Doc.File.FILENAME;


    private volatile BulkProcessor bulkProcessor;

    private volatile boolean closed = false;

    /**
     * We store config files here...
     * Default to ~/.fscrawler
     */
    private final FsSettings settings;
    private final FsSettingsFileHandler fsSettingsFileHandler;
    private final FsJobFileHandler fsJobFileHandler;

    private ElasticsearchClient client;

    public FsCrawlerImpl(Path config, FsSettings settings) {
        this.fsSettingsFileHandler = new FsSettingsFileHandler(config);
        this.fsJobFileHandler = new FsJobFileHandler(config);

        this.settings = settings;
        if (settings.getFs() == null || settings.getFs().getUrl() == null) {
            logger.warn("`url` is not set. Please define it. Falling back to default: [{}].", Fs.DEFAULT_DIR);
            if (settings.getFs() == null) {
                settings.setFs(Fs.DEFAULT);
            } else {
                settings.getFs().setUrl(Fs.DEFAULT_DIR);
            }
        }

        if (settings.getElasticsearch() == null) {
            settings.setElasticsearch(Elasticsearch.DEFAULT);
        }
        if (settings.getElasticsearch().getIndex() == null) {
            // When index is not set, we fallback to the config name
            settings.getElasticsearch().setIndex(settings.getName());
        }

        // Checking protocol
        if (settings.getServer() != null) {
            if (!PROTOCOL.LOCAL.equals(settings.getServer().getProtocol()) &&
                    !PROTOCOL.SSH.equals(settings.getServer().getProtocol())) {
                // Non supported protocol
                logger.error(settings.getServer().getProtocol() + " is not supported yet. Please use " +
                        PROTOCOL.LOCAL + " or " + PROTOCOL.SSH + ". Disabling crawler");
                closed = true;
                return;
            }

            // Checking username/password
            if (PROTOCOL.SSH.equals(settings.getServer().getProtocol()) &&
                    !hasText(settings.getServer().getUsername())) {
                // Non supported protocol
                logger.error("When using SSH, you need to set a username and probably a password or a pem file. Disabling crawler");
                closed = true;
            }
        }
    }

    public void start() throws Exception {
        if (logger.isInfoEnabled())
            logger.info("Starting FS crawler");

        if (closed) {
            logger.info("Fs crawler is closed. Exiting");
            return;
        }

        try {
            // Create an elasticsearch client
            client = ElasticsearchClient.builder().build();

            if (!client.waitUntilClusterIsRunning(
                    settings.getElasticsearch().getWaitSeconds(),
                    settings.getElasticsearch().getWaitInterval(),
                    settings.getElasticsearch().getNodes())){
                return;
            }

            settings.getElasticsearch().getNodes().forEach(client::addNode);

            client.createIndex(settings.getElasticsearch().getIndex(), true);
        } catch (Exception e) {
            logger.warn("failed to create index [{}], disabling crawler...", settings.getElasticsearch().getIndex());
            throw e;
        }

        try {
            // If needed, we create the new mapping for files
            if (!settings.getFs().isJsonSupport()) {
                ElasticsearchClient.pushMapping(client, settings.getElasticsearch().getIndex(), settings.getElasticsearch().getType(),
                        FsCrawlerUtil.buildFsFileMapping(true, settings.getFs().isStoreSource()));
            }
            // If needed, we create the new mapping for folders
            ElasticsearchClient.pushMapping(client, settings.getElasticsearch().getIndex(), FsCrawlerUtil.INDEX_TYPE_FOLDER,
                    FsCrawlerUtil.buildFsFolderMapping());
        } catch (Exception e) {
            logger.warn("failed to create mapping for [{}/{}], disabling crawler...",
                    settings.getElasticsearch().getIndex(), settings.getElasticsearch().getType());
            throw e;
        }

        // Creating bulk processor
        this.bulkProcessor = BulkProcessor.simpleBulkProcessor(client, settings.getElasticsearch().getBulkSize(),
                settings.getElasticsearch().getFlushInterval());

        // We save crawler settings
        // TODO May be do that in another place?
        fsSettingsFileHandler.write(settings);

        // Start the crawler thread
        Thread thread = new Thread(new FSParser(settings));
        thread.start();
    }

    public void close() {
        logger.info("Closing fs crawler");
        closed = true;

        if (this.bulkProcessor != null) {
            this.bulkProcessor.close();
        }
    }

    public boolean isClosed() {
        return closed;
    }

    private class FSParser implements Runnable {
        private final FsSettings fsSettings;

        private ScanStatistic stats;

        public FSParser(FsSettings fsSettings) {
            this.fsSettings = fsSettings;
            logger.info("creating fs crawler thread [{}] for [{}] every [{}]", fsSettings.getName(),
                    fsSettings.getFs().getUrl(),
                    fsSettings.getFs().getUpdateRate());
        }

        @Override
        public void run() {
            while (true) {
                if (closed) {
                    return;
                }

                try {
                    stats = new ScanStatistic(fsSettings.getFs().getUrl());

                    File directory = new File(fsSettings.getFs().getUrl());
                    if (!directory.exists()) {
                        throw new RuntimeException(fsSettings.getFs().getUrl() + " doesn't exists.");
                    }

                    String rootPathId = SignTool.sign(directory.getAbsolutePath());
                    stats.setRootPathId(rootPathId);

                    Instant scanDatenew = Instant.now();
                    Instant scanDate = getLastDateFromMeta(fsSettings.getName());

                    // We only index the root directory once (first run)
                    // That means that we don't have a scanDate yet
                    if (scanDate == null) {
                        indexRootDirectory(directory);
                    }

                    addFilesRecursively(fsSettings.getFs().getUrl(), scanDate);

                    updateFsJob(fsSettings.getName(), scanDatenew);
                } catch (Exception e) {
                    logger.warn("Error while indexing content from {}", fsSettings.getFs().getUrl(), e);
                }

                try {
                    if(logger.isInfoEnabled()) {
                        logger.info("Fs crawler is going to sleep for {}", fsSettings.getFs().getUpdateRate());
                    }
                    Thread.sleep(fsSettings.getFs().getUpdateRate().millis());
                } catch (InterruptedException e1) {
                }
            }
        }

        @SuppressWarnings("unchecked")
        private Instant getLastDateFromMeta(String jobName) throws IOException {
            try {
                FsJob fsJob = fsJobFileHandler.read(jobName);
                return fsJob.getLastrun();
            } catch (NoSuchFileException e) {
                // The file does not exist yet
            }
            return null;
        }

        /**
         * Update the job metadata
         *
         * @param jobName  job name
         * @param scanDate last date we scan the dirs
         * @throws Exception
         */
        private void updateFsJob(String jobName, Instant scanDate) throws Exception {
            // We need to round that latest date to the lower second and
            // remove 2 seconds.
            // See #82: https://github.com/dadoonet/fscrawler/issues/82
            scanDate = scanDate.minus(2, ChronoUnit.SECONDS);
            FsJob fsJob = FsJob.builder()
                    .setName(jobName)
                    .setLastrun(scanDate)
                    .setIndexed(stats.getNbDocScan())
                    .setDeleted(stats.getNbDocDeleted())
                    .build();
            fsJobFileHandler.write(jobName, fsJob);
        }

        private FileAbstractor buildFileAbstractor() throws Exception {
            // What is the protocol used?
            if (fsSettings.getServer() == null || PROTOCOL.LOCAL.equals(fsSettings.getServer().getProtocol())) {
                // Local FS
                return new FileAbstractorFile(fsSettings);
            } else if (PROTOCOL.SSH.equals(fsSettings.getServer().getProtocol())) {
                // Remote SSH FS
                return new FileAbstractorSSH(fsSettings);
            }

            // Non supported protocol
            throw new RuntimeException(fsSettings.getServer().getProtocol() + " is not supported yet. Please use " +
                    PROTOCOL.LOCAL + " or " + PROTOCOL.SSH);
        }

        private void addFilesRecursively(String filepath, Instant lastScanDate)
                throws Exception {

            logger.debug("indexing [{}] content", filepath);
            FileAbstractor path = buildFileAbstractor();
            path.open();

            try {

                final Collection<FileAbstractModel> children = path.getFiles(filepath);
                Collection<String> fsFiles = new ArrayList<>();
                Collection<String> fsFolders = new ArrayList<>();

                if (children != null) {
                    for (FileAbstractModel child : children) {
                        String filename = child.name;

                        // https://github.com/dadoonet/fscrawler/issues/1 : Filter documents
                        boolean isIndexable = FsCrawlerUtil.isIndexable(filename, fsSettings.getFs().getIncludes(), fsSettings.getFs().getExcludes());
                        logger.debug("[{}] can be indexed: [{}]", filename, isIndexable);
                        if (isIndexable) {
                            if (child.file) {
                                logger.debug("  - file: {}", filename);
                                fsFiles.add(filename);
                                if (lastScanDate == null
                                        || child.lastModifiedDate.isAfter(lastScanDate)
                                        || (child.creationDate != null && child.creationDate.isAfter(lastScanDate))) {
                                    indexFile(stats, child.name, filepath, path.getInputStream(child), child.lastModifiedDate, child.size);
                                    stats.addFile();
                                } else {
                                    logger.debug("    - not modified: creation date {} , file date {}, last scan date {}",
                                            child.creationDate, child.lastModifiedDate, lastScanDate);
                                }
                            } else if (child.directory) {
                                logger.debug("  - folder: {}", filename);
                                fsFolders.add(filename);
                                indexDirectory(stats, filename, child.fullpath.concat(File.separator));
                                addFilesRecursively(child.fullpath.concat(File.separator), lastScanDate);
                            } else {
                                logger.debug("  - other: {}", filename);
                                logger.debug("Not a file nor a dir. Skipping {}", child.fullpath);
                            }
                        } else {
                            logger.debug("  - ignored file/dir: {}", filename);
                        }
                    }
                }

                if (fsSettings.getFs().isRemoveDeleted()) {
                    Collection<String> esFiles = getFileDirectory(filepath);
                    removeDeletedFilesFromElasticsearch(filepath, esFiles, fsFiles);
                    removeDeletedDirectoriesFromElasticsearch(filepath, fsFolders);
                }

            } finally {
                path.close();
            }
        }

        private void removeDeletedDirectoriesFromElasticsearch(String filepath, Collection<String> fsFolders) throws Exception {
            logger.debug("Looking for removed directories in [{}]...", filepath);
            Collection<String> esFolders = getFolderDirectory(filepath);

            // for the delete folder
            for (String esfolder : esFolders) {
                if (FsCrawlerUtil.isIndexable(esfolder, fsSettings.getFs().getIncludes(), fsSettings.getFs().getExcludes())) {
                    logger.trace("Checking directory [{}]", esfolder);
                    if (!fsFolders.contains(esfolder)) {
                        logger.trace("Removing recursively directory [{}] in elasticsearch", esfolder);
                        removeEsDirectoryRecursively(filepath, esfolder);
                    }
                }
            }
        }

        private void removeDeletedFilesFromElasticsearch(String filepath, Collection<String> esFiles, Collection<String> fsFiles) throws NoSuchAlgorithmException {
            logger.debug("Looking for removed files in [{}]...", filepath);


            // for the delete files
            for (String esfile : esFiles) {
                logger.trace("Checking file [{}]", esfile);

                if (FsCrawlerUtil.isIndexable(esfile, fsSettings.getFs().getIncludes(), fsSettings.getFs().getExcludes())
                        && !fsFiles.contains(esfile)) {
                    File file = new File(filepath, esfile);

                    logger.trace("Removing file [{}] in elasticsearch", esfile);
                    esDelete(fsSettings.getElasticsearch().getIndex(), fsSettings.getElasticsearch().getType(),
                            SignTool.sign(file.getAbsolutePath()));
                    stats.removeFile();
                }
            }
        }

        // TODO Optimize it. We can probably use a search for a big array of filenames instead of
        // Searching fo 50000 files (which is somehow limited).
        // --> changed to 10000 which is the max. Higher values results to a default of 10 (pagination)
        private Collection<String> getFileDirectory(String path) throws Exception {
            Collection<String> files = new ArrayList<>();

            // If the crawler is being closed, we return
            if (closed) {
                return files;
            }

            logger.trace("Querying elasticsearch for files in dir [{}:{}]", PATH_ENCODED, SignTool.sign(path));
            SearchResponse response = client.search(
                    fsSettings.getElasticsearch().getIndex(),
                    fsSettings.getElasticsearch().getType(),
                    PATH_ENCODED + ":" + SignTool.sign(path),
                    REQUEST_SIZE,
                    FILE_FILENAME
            );

            if (response.getHits() != null) {
                logger.trace("Response [{} hits]", response.getHits().getTotal());

                if (response.getHits().getHits() != null) {
                    List<SearchResponse.Hit> hits = response.getHits().getHits();

                    for (SearchResponse.Hit hit : hits) {
                        String name;
                        if (hit.getSource() != null && hit.getSource().get(FILE_FILENAME) != null) {
                            name = getName(hit.getSource().get(FILE_FILENAME));

                        } else if (hit.getFields() != null && hit.getFields().get(FILE_FILENAME) != null) {
                            name = getName(hit.getFields().get(FILE_FILENAME));

                        } else {
                            // Houston, we have a problem ! We can't get the old files from ES
                            logger.warn("Can't find in _source nor fields the existing filenames in path [{}]. " +
                                    "Please enable _source or store field [{}]", path, FILE_FILENAME);
                            throw new RuntimeException("Mapping is incorrect: please enable _source or store field [" +
                                    FILE_FILENAME + "].");
                        }

                        files.add(name);
                    }
                }
            }

            return files;
        }

        private String getName(Object nameObject) {

            if(nameObject instanceof List){
                return String.valueOf (((List) nameObject).get(0));
            }

            throw new RuntimeException("search result, file.name not of type List<String>");
        }

        private Collection<String> getFolderDirectory(String path)
                throws Exception {
            Collection<String> files = new ArrayList<>();

            // If the crawler is being closed, we return
            if (closed) {
                return files;
            }

            SearchResponse response = client.search(
                    fsSettings.getElasticsearch().getIndex(),
                    FsCrawlerUtil.INDEX_TYPE_FOLDER,
                    PATH_ENCODED + ":" + SignTool.sign(path),
                    REQUEST_SIZE, // TODO: WHAT? DID I REALLY WROTE THAT? :p
                    null
            );

            if (response.getHits() != null
                    && response.getHits().getHits() != null) {
                for (SearchResponse.Hit hit : response.getHits().getHits()) {
                    String name = hit.getSource()
                            .get(FILE_FILENAME).toString();
                    files.add(name);
                }
            }

            return files;
        }

        /**
         * Index a file
         */
        private void indexFile(ScanStatistic stats, String filename, String filepath, InputStream fileReader,
                               Instant lastmodified, long size) throws Exception {
            logger.debug("fetching content from [{}],[{}]", filepath, filename);

            // Create the Doc object
            Doc doc = new Doc();

            // File
            doc.getFile().setFilename(filename);
            doc.getFile().setLastModified(lastmodified);
            doc.getFile().setIndexingDate(Instant.now());
            doc.getFile().setUrl("file://" + (new File(filepath, filename)).toString());
            doc.getFile().setFilesize(size);

            // Path
            doc.getPath().setEncoded(SignTool.sign(filepath));
            doc.getPath().setRoot(stats.getRootPathId());
            doc.getPath().setVirtual(FsCrawlerUtil.computeVirtualPathName(stats, filepath));
            doc.getPath().setReal((new File(filepath, filename)).toString());
            // Path


            if (fsSettings.getFs().isIndexContent()) {
                byte[] buffer = new byte[1024];
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                int i;
                while (-1 != (i = fileReader.read(buffer))) {
                    bos.write(buffer, 0, i);
                }
                byte[] data = bos.toByteArray();

                fileReader.close();
                bos.close();

                // https://github.com/dadoonet/fscrawler/issues/5 : Support JSon files
                if (fsSettings.getFs().isJsonSupport()) {
                    String id;
                    if (fsSettings.getFs().isFilenameAsId()) {
                        id = filename;
                        int pos = id.lastIndexOf(".");
                        if (pos > 0) {
                            id = id.substring(0, pos);
                        }
                    } else {
                        id = SignTool.sign((new File(filepath, filename)).toString());
                    }
                    esIndex(fsSettings.getElasticsearch().getIndex(),
                            fsSettings.getElasticsearch().getType(),
                            id,
                            new String(data, "UTF-8"));
                    return;
                } else {
                    // Extracting content with Tika
                    // See #38: https://github.com/dadoonet/fscrawler/issues/38
                    int indexedChars = 100000;
                    if (fsSettings.getFs().getIndexedChars() != null) {
                        if (fsSettings.getFs().getIndexedChars().percentage()) {
                            indexedChars = (int) Math.round(data.length * fsSettings.getFs().getIndexedChars().asDouble());
                            logger.trace("using percentage [{}] to define indexed chars: [{}]",
                                    fsSettings.getFs().getIndexedChars(), indexedChars);
                        } else {
                            indexedChars = (int) fsSettings.getFs().getIndexedChars().value();
                            logger.trace("indexed chars [{}]",
                                    indexedChars == -1 ? "has been disabled. All text will be extracted" : indexedChars);
                        }
                    }
                    Metadata metadata = new Metadata();

                    String parsedContent = null;
                    try {
                        // Set the maximum length of strings returned by the parseToString method, -1 sets no limit
                        parsedContent = tika().parseToString(new ByteArrayInputStream(data), metadata, indexedChars);
                    } catch (Throwable e) {
                        logger.debug("Failed to extract [" + indexedChars + "] characters of text for [" + filename + "]", e);
                    }

                    // Adding what we found to the document we want to index

                    // File
                    doc.getFile().setContentType(metadata.get(Metadata.CONTENT_TYPE));

                    // We only add `indexed_chars` if we have other value than default or -1
                    if (fsSettings.getFs().getIndexedChars() != null && fsSettings.getFs().getIndexedChars().value() != -1) {
                        doc.getFile().setIndexedChars(indexedChars);
                    }

                    if (fsSettings.getFs().isAddFilesize()) {
                        if (metadata.get(Metadata.CONTENT_LENGTH) != null) {
                            // We try to get CONTENT_LENGTH from Tika first
                            doc.getFile().setFilesize(Long.parseLong(metadata.get(Metadata.CONTENT_LENGTH)));
                        }
                    }

                    // Meta
                    doc.getMeta().setAuthor(metadata.get(Metadata.AUTHOR));
                    doc.getMeta().setTitle(metadata.get(Metadata.TITLE));
                    // TODO Fix that as the date we get from Tika might be not parseable as a Date
                    // doc.getMeta().setDate(metadata.get(Metadata.DATE));
                    doc.getMeta().setKeywords(commaDelimitedListToStringArray(metadata.get(Metadata.KEYWORDS)));
                    // Meta

                    // Doc content
                    doc.setContent(parsedContent);

                    // Doc as binary attachment
                    if (fsSettings.getFs().isStoreSource()) {
                        doc.setAttachment(new String(Base64.getEncoder().encode(data), "UTF-8"));
                    }
                    // End of our document
                }
            }

            // We index
            esIndex(fsSettings.getElasticsearch().getIndex(),
                    fsSettings.getElasticsearch().getType(),
                    SignTool.sign((new File(filepath, filename)).toString()),
                    doc);
        }

        private void indexDirectory(String id, String name, String root, String virtual, String encoded)
                throws Exception {

            fr.pilato.elasticsearch.crawler.fs.meta.doc.Path path = new fr.pilato.elasticsearch.crawler.fs.meta.doc.Path();
            path.setReal(name);
            path.setRoot(root);
            path.setVirtual(virtual);
            path.setEncoded(encoded);
            esIndex(fsSettings.getElasticsearch().getIndex(),
                    FsCrawlerUtil.INDEX_TYPE_FOLDER,
                    id,
                    path);
        }

        /**
         * Index a directory
         */
        private void indexDirectory(ScanStatistic stats, String filename, String filepath)
                throws Exception {
            indexDirectory(SignTool.sign(filepath),
                    filename,
                    stats.getRootPathId(),
                    FsCrawlerUtil.computeVirtualPathName(stats,
                            filepath.substring(0, filepath.lastIndexOf(File.separator))),
                    SignTool.sign(filepath.substring(0, filepath.lastIndexOf(File.separator))));
        }

        /**
         * Add the root directory as a folder
         */
        private void indexRootDirectory(File file) throws Exception {
            indexDirectory(SignTool.sign(file.getAbsolutePath()),
                    file.getName(),
                    stats.getRootPathId(),
                    null,
                    SignTool.sign(file.getParent()));
        }

        /**
         * Remove a full directory and sub dirs recursively
         */
        private void removeEsDirectoryRecursively(String path, String name)
                throws Exception {

            String fullPath = path.concat(File.separator).concat(name);

            logger.debug("Delete folder " + fullPath);
            Collection<String> listFile = getFileDirectory(fullPath);

            for (String esfile : listFile) {
                esDelete(
                        fsSettings.getElasticsearch().getIndex(),
                        fsSettings.getElasticsearch().getType(),
                        SignTool.sign(fullPath.concat(File.separator).concat(esfile)));
            }

            Collection<String> listFolder = getFolderDirectory(fullPath);

            for (String esfolder : listFolder) {
                removeEsDirectoryRecursively(fullPath, esfolder);
            }

            esDelete(fsSettings.getElasticsearch().getIndex(), FsCrawlerUtil.INDEX_TYPE_FOLDER,
                    SignTool.sign(fullPath));

        }

        /**
         * Add to bulk an IndexRequest
         */
        private void esIndex(String index, String type, String id,
                             Doc doc) throws Exception {
            esIndex(index, type, id, DocParser.toJson(doc));
        }

        private void esIndex(String index, String type, String id, fr.pilato.elasticsearch.crawler.fs.meta.doc.Path path)
                throws Exception {
            esIndex(index, type, id, PathParser.toJson(path));
        }

        /**
         * Add to bulk an IndexRequest in JSon format
         */
        private void esIndex(String index, String type, String id, String json) {
            logger.debug("Indexing in ES " + index + ", " + type + ", " + id);

            if (!closed) {
                bulkProcessor.add(new IndexRequest(index, type, id).source(json));
            } else {
                logger.warn("trying to add new file while closing crawler. Document [{}]/[{}]/[{}] has been ignored", index, type, id);
            }
        }

        /**
         * Add to bulk a DeleteRequest
         */
        private void esDelete(String index, String type, String id) {
            logger.debug("Deleting from ES " + index + ", " + type + ", " + id);
            if (!closed) {
                bulkProcessor.add(new DeleteRequest(index, type, id));
            } else {
                logger.warn("trying to remove a file while closing crawler. Document [{}]/[{}]/[{}] has been ignored", index, type, id);
            }
        }
    }

    /**
     * Check whether the given CharSequence has actual text.
     * More specifically, returns <code>true</code> if the string not <code>null</code>,
     * its length is greater than 0, and it contains at least one non-whitespace character.
     * <p><pre>
     * StringUtils.hasText(null) = false
     * StringUtils.hasText("") = false
     * StringUtils.hasText(" ") = false
     * StringUtils.hasText("12345") = true
     * StringUtils.hasText(" 12345 ") = true
     * </pre>
     *
     * @param str the CharSequence to check (may be <code>null</code>)
     * @return <code>true</code> if the CharSequence is not <code>null</code>,
     * its length is greater than 0, and it does not contain whitespace only
     * @see java.lang.Character#isWhitespace
     */
    public static boolean hasText(CharSequence str) {
        if (!hasLength(str)) {
            return false;
        }
        int strLen = str.length();
        for (int i = 0; i < strLen; i++) {
            if (!Character.isWhitespace(str.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    public static List<String> commaDelimitedListToStringArray(String str) {
        if (str == null) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        int pos = 0;
        int delPos;
        while ((delPos = str.indexOf(",", pos)) != -1) {
            result.add(str.substring(pos, delPos));
            pos = delPos + 1;
        }
        if (str.length() > 0 && pos <= str.length()) {
            // Add rest of String, but not in case of empty input.
            result.add(str.substring(pos));
        }
        return result;
    }

    private static boolean hasLength(CharSequence str) {
        return str != null && str.length() > 0;
    }


}
