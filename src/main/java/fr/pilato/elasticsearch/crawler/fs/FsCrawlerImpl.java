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

import fr.pilato.elasticsearch.crawler.fs.client.BulkProcessor;
import fr.pilato.elasticsearch.crawler.fs.client.DeleteRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClient;
import fr.pilato.elasticsearch.crawler.fs.client.IndexRequest;
import fr.pilato.elasticsearch.crawler.fs.client.SearchResponse;
import fr.pilato.elasticsearch.crawler.fs.fileabstractor.FileAbstractModel;
import fr.pilato.elasticsearch.crawler.fs.fileabstractor.FileAbstractor;
import fr.pilato.elasticsearch.crawler.fs.fileabstractor.FileAbstractorFile;
import fr.pilato.elasticsearch.crawler.fs.fileabstractor.FileAbstractorSSH;
import fr.pilato.elasticsearch.crawler.fs.meta.doc.Attributes;
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static fr.pilato.elasticsearch.crawler.fs.tika.TikaDocParser.generate;
import static fr.pilato.elasticsearch.crawler.fs.util.FsCrawlerUtil.extractMajorVersionNumber;

/**
 * @author dadoonet (David Pilato)
 */
public class FsCrawlerImpl {

    public static final class PROTOCOL {
        public static final String LOCAL = "local";
        public static final String SSH = "ssh";
        public static final int SSH_PORT = 22;
    }

    private static final Logger logger = LogManager.getLogger(FsCrawlerImpl.class);

    private static final String PATH_ENCODED = FsCrawlerUtil.Doc.PATH + "." + FsCrawlerUtil.Doc.Path.ENCODED;
    private static final String FILE_FILENAME = FsCrawlerUtil.Doc.FILE + "." + FsCrawlerUtil.Doc.File.FILENAME;

    public static final int REQUEST_SIZE = 10000;

    private volatile BulkProcessor bulkProcessor;

    private volatile boolean closed = false;
    private final Object semaphore = new Object();

    /**
     * We store config files here...
     * Default to ~/.fscrawler
     */
    private final Path config;
    private final FsSettings settings;
    private final FsSettingsFileHandler fsSettingsFileHandler;
    private final FsJobFileHandler fsJobFileHandler;

    private ElasticsearchClient client;
    private Thread fsCrawlerThread;

    public FsCrawlerImpl(Path config, FsSettings settings) {
        this.config = config;
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

        String elasticsearchVersion;

        try {
            // Create an elasticsearch client
            client = ElasticsearchClient.builder().build();

            settings.getElasticsearch().getNodes().forEach(client::addNode);

            client.createIndex(settings.getElasticsearch().getIndex(), true);

            // Let's read the current version of elasticsearch cluster
            String version = client.findVersion();
            logger.debug("FS crawler connected to an elasticsearch [{}] node.", version);

            elasticsearchVersion = extractMajorVersionNumber(version);
        } catch (Exception e) {
            logger.warn("failed to create index [{}], disabling crawler...", settings.getElasticsearch().getIndex());
            throw e;
        }

        try {
            // If needed, we create the new mapping for files
            Path jobSettingsDir = config.resolve(settings.getName());
            if (!settings.getFs().isJsonSupport()) {
                // Read file mapping from resources
                String mapping = FsCrawlerUtil.readMapping(jobSettingsDir, config, elasticsearchVersion, FsCrawlerUtil.INDEX_TYPE_DOC);
                ElasticsearchClient.pushMapping(client, settings.getElasticsearch().getIndex(), settings.getElasticsearch().getType(),
                        mapping);
            }
            // If needed, we create the new mapping for folders
            String mapping = FsCrawlerUtil.readMapping(jobSettingsDir, config, elasticsearchVersion, FsCrawlerUtil.INDEX_TYPE_FOLDER);
            ElasticsearchClient.pushMapping(client, settings.getElasticsearch().getIndex(), FsCrawlerUtil.INDEX_TYPE_FOLDER,
                    mapping);
        } catch (Exception e) {
            logger.warn("failed to create mapping for [{}/{}], disabling crawler...",
                    settings.getElasticsearch().getIndex(), settings.getElasticsearch().getType());
            throw e;
        }

        // Creating bulk processor
        this.bulkProcessor = BulkProcessor.simpleBulkProcessor(client, settings.getElasticsearch().getBulkSize(),
                settings.getElasticsearch().getFlushInterval());

        // Start the crawler thread
        fsCrawlerThread = new Thread(new FSParser(settings), "fs-crawler");
        fsCrawlerThread.start();
    }

    public void close() throws InterruptedException, IOException {
        logger.debug("Closing FS crawler [{}]", settings.getName());
        closed = true;

        synchronized(semaphore) {
            semaphore.notify();
        }

        if (this.fsCrawlerThread != null) {
            while (fsCrawlerThread.isAlive()) {
                // We check that the crawler has been closed effectively
                logger.debug("FS crawler thread is still running");
                Thread.sleep(500);
            }
            logger.debug("FS crawler thread is now stopped");
        }

        if (this.bulkProcessor != null) {
            this.bulkProcessor.close();
        }

        if (client != null) {
            client.shutdown();
        }

        logger.info("FS crawler [{}] stopped", settings.getName());
    }

    public boolean isClosed() {
        return closed;
    }

    private class FSParser implements Runnable {
        private final FsSettings fsSettings;

        private ScanStatistic stats;

        public FSParser(FsSettings fsSettings) {
            this.fsSettings = fsSettings;
            logger.debug("creating fs crawler thread [{}] for [{}] every [{}]", fsSettings.getName(),
                    fsSettings.getFs().getUrl(),
                    fsSettings.getFs().getUpdateRate());
        }

        @Override
        public void run() {
            logger.info("FS crawler started for [{}] for [{}] every [{}]", fsSettings.getName(),
                    fsSettings.getFs().getUrl(),
                    fsSettings.getFs().getUpdateRate());
            while (true) {
                if (closed) {
                    return;
                }

                FileAbstractor path = null;

                try {
                    stats = new ScanStatistic(fsSettings.getFs().getUrl());

                    path = buildFileAbstractor();
                    path.open();

                    if (!path.exists(fsSettings.getFs().getUrl())) {
                        throw new RuntimeException(fsSettings.getFs().getUrl() + " doesn't exists.");
                    }

                    String rootPathId = SignTool.sign(fsSettings.getFs().getUrl());
                    stats.setRootPathId(rootPathId);

                    Instant scanDatenew = Instant.now();
                    Instant scanDate = getLastDateFromMeta(fsSettings.getName());

                    // We only index the root directory once (first run)
                    // That means that we don't have a scanDate yet
                    if (scanDate == null) {
                        indexRootDirectory(fsSettings.getFs().getUrl());
                    }

                    addFilesRecursively(path, fsSettings.getFs().getUrl(), scanDate);

                    updateFsJob(fsSettings.getName(), scanDatenew);
                } catch (Exception e) {
                    logger.warn("Error while indexing content from {}", fsSettings.getFs().getUrl());
                    logger.debug("", e);
                } finally {
                    if (path != null) {
                        try {
                            path.close();
                        } catch (Exception e) {
                            logger.warn("Error while closing the connection: {}", e.getMessage());
                            logger.debug("", e);
                        }
                    }
                }

                try {
                    logger.debug("Fs crawler is going to sleep for {}", fsSettings.getFs().getUpdateRate());

                    // The problem here is that there is no wait to close the thread while we are sleeping.
                    // Which leads to Zombie threads in our tests

                    synchronized (semaphore) {
                        semaphore.wait(fsSettings.getFs().getUpdateRate().millis());
                    }
                } catch (InterruptedException ignored) {
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
         * @param jobName job name
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

        private void addFilesRecursively(FileAbstractor path, String filepath, Instant lastScanDate)
                throws Exception {

            logger.debug("indexing [{}] content", filepath);

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
                                indexFile(child, stats, filepath, path.getInputStream(child));
                                stats.addFile();
                            } else {
                                logger.debug("    - not modified: creation date {} , file date {}, last scan date {}",
                                        child.creationDate, child.lastModifiedDate, lastScanDate);
                            }
                        } else if (child.directory) {
                            logger.debug("  - folder: {}", filename);
                            fsFolders.add(filename);
                            indexDirectory(stats, filename, child.fullpath.concat(File.separator));
                            addFilesRecursively(path, child.fullpath.concat(File.separator), lastScanDate);
                        } else {
                            logger.debug("  - other: {}", filename);
                            logger.debug("Not a file nor a dir. Skipping {}", child.fullpath);
                        }
                    } else {
                        logger.debug("  - ignored file/dir: {}", filename);
                    }
                }
            }

            // TODO Optimize
            // if (path.isDirectory() && path.lastModified() > lastScanDate
            // && lastScanDate != 0) {

            if (fsSettings.getFs().isRemoveDeleted()) {
                logger.debug("Looking for removed files in [{}]...", filepath);
                Collection<String> esFiles = getFileDirectory(filepath);

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
        }

        // TODO Optimize it. We can probably use a search for a big array of filenames instead of
        // Searching fo 10000 files (which is somehow limited).
        private Collection<String> getFileDirectory(String path)
                throws Exception {
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
                    REQUEST_SIZE, // TODO: WHAT? DID I REALLY WROTE THAT? :p
                    FILE_FILENAME
            );

            logger.trace("Response [{}]", response.toString());
            if (response.getHits() != null && response.getHits().getHits() != null) {
                for (SearchResponse.Hit hit : response.getHits().getHits()) {
                    String name;
                    if (hit.getSource() != null
                            && hit.getSource().get(FILE_FILENAME) != null) {
                        name = getName(hit.getSource().get(FILE_FILENAME));
                    } else if (hit.getFields() != null
                            && hit.getFields().get(FILE_FILENAME) != null) {
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

            return files;
        }

        private String getName(Object nameObject) {
            if (nameObject instanceof List) {
                return String.valueOf (((List) nameObject).get(0));
            }

            throw new RuntimeException("search result, " + nameObject +
                    " not of type List<String> but " +
                    nameObject.getClass().getName() + " with value " + nameObject);
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
        private void indexFile(FileAbstractModel fileAbstractModel, ScanStatistic stats, String filepath, InputStream fileReader) throws Exception {
            final String filename = fileAbstractModel.name;
            final Instant lastmodified = fileAbstractModel.lastModifiedDate;
            final long size = fileAbstractModel.size;

            logger.debug("fetching content from [{}],[{}]", filepath, filename);

            // Create the Doc object
            Doc doc = new Doc();

            // File
            doc.getFile().setFilename(filename);
            doc.getFile().setLastModified(lastmodified);
            doc.getFile().setIndexingDate(Instant.now());
            doc.getFile().setUrl("file://" + (new File(filepath, filename)).toString());
            if (fsSettings.getFs().isAddFilesize()) {
                doc.getFile().setFilesize(size);
            }

            // Path
            doc.getPath().setEncoded(SignTool.sign(filepath));
            doc.getPath().setRoot(stats.getRootPathId());
            doc.getPath().setVirtual(FsCrawlerUtil.computeVirtualPathName(stats, filepath));
            doc.getPath().setReal((new File(filepath, filename)).toString());
            // Path

            // Attributes
            if (fsSettings.getFs().isAttributesSupport()) {
                doc.setAttributes(new Attributes());
                doc.getAttributes().setOwner(fileAbstractModel.owner);
                doc.getAttributes().setGroup(fileAbstractModel.group);
            }
            // Attributes

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
                    generate(fsSettings, data, filename, doc);
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
        private void indexRootDirectory(String path) throws Exception {
            indexDirectory(SignTool.sign(path),
                    path,
                    stats.getRootPathId(),
                    null,
                    SignTool.sign(path));
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
            logger.trace("JSon indexed : {}", json);

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
     *         its length is greater than 0, and it does not contain whitespace only
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

    private static boolean hasLength(CharSequence str) {
        return str != null && str.length() > 0;
    }


}
