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

import fr.pilato.elasticsearch.crawler.fs.beans.Attributes;
import fr.pilato.elasticsearch.crawler.fs.beans.Doc;
import fr.pilato.elasticsearch.crawler.fs.beans.DocParser;
import fr.pilato.elasticsearch.crawler.fs.beans.PathParser;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClientManager;
import fr.pilato.elasticsearch.crawler.fs.crawler.FileAbstractModel;
import fr.pilato.elasticsearch.crawler.fs.crawler.FileAbstractor;
import fr.pilato.elasticsearch.crawler.fs.crawler.fs.FileAbstractorFile;
import fr.pilato.elasticsearch.crawler.fs.crawler.ssh.FileAbstractorSSH;
import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.meta.job.FsJob;
import fr.pilato.elasticsearch.crawler.fs.meta.job.FsJobFileHandler;
import fr.pilato.elasticsearch.crawler.fs.rest.RestServer;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsFileHandler;
import fr.pilato.elasticsearch.crawler.fs.tika.XmlDocParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.bulk.BulkProcessor;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static fr.pilato.elasticsearch.crawler.fs.FsCrawlerValidator.validateSettings;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.computeVirtualPathName;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.isExcluded;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.isIndexable;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.localDateTimeToDate;
import static fr.pilato.elasticsearch.crawler.fs.tika.TikaDocParser.generate;

/**
 * @author dadoonet (David Pilato)
 */
public class FsCrawlerImpl {

    @Deprecated
    public static final String INDEX_TYPE_FOLDER = "folder";
    @Deprecated
    public static final String INDEX_TYPE_DOC = "doc";

    public static final class PROTOCOL {
        public static final String LOCAL = "local";
        public static final String SSH = "ssh";
        public static final int SSH_PORT = 22;
    }

    private static final Logger logger = LogManager.getLogger(FsCrawlerImpl.class);

    private static final String PATH_ROOT = Doc.FIELD_NAMES.PATH + "." + fr.pilato.elasticsearch.crawler.fs.beans.Path.FIELD_NAMES.ROOT;
    private static final String FILE_FILENAME = Doc.FIELD_NAMES.FILE + "." + fr.pilato.elasticsearch.crawler.fs.beans.File.FIELD_NAMES.FILENAME;

    private final AtomicInteger runNumber = new AtomicInteger(0);

    private final static String FSCRAWLER_PROPERTIES = "fscrawler.properties";
    public static final Properties properties;

    static {
        properties = new Properties();
        try {
            properties.load(FsCrawlerImpl.class.getClassLoader().getResourceAsStream(FSCRAWLER_PROPERTIES));
        } catch (IOException e) {
            logger.error("Can not find [{}] resource in the class loader", FSCRAWLER_PROPERTIES);
            throw new RuntimeException(e);
        }
    }

    private static final int REQUEST_SIZE = 10000;
    public static final int LOOP_INFINITE = -1;
    public static final long MAX_SLEEP_RETRY_TIME = TimeValue.timeValueSeconds(30).millis();

    private volatile boolean closed = false;
    private final Object semaphore = new Object();

    private final FsSettings settings;
    private final FsJobFileHandler fsJobFileHandler;
    private final Integer loop;
    private final boolean rest;
    private MessageDigest messageDigest = null;

    private Thread fsCrawlerThread;

    private final ElasticsearchClientManager esClientManager;

    public FsCrawlerImpl(Path config, FsSettings settings) {
        this(config, settings, LOOP_INFINITE, false);
    }

    public FsCrawlerImpl(Path config, FsSettings settings, Integer loop, boolean rest) {
        /*
         * We store config files here...
         * Default to ~/.fscrawler
         * The dir will be created if needed by calling the following CTOR
         */
        new FsSettingsFileHandler(config);
        this.fsJobFileHandler = new FsJobFileHandler(config);
        this.settings = settings;
        this.loop = loop;
        this.rest = rest;
        this.esClientManager = new ElasticsearchClientManager(config, settings);

        closed = validateSettings(logger, settings, rest);
        if (closed) {
            // We don't go further as we have critical errors
            return;
        }

        // Generate the directory where we write status and other files
        Path jobSettingsFolder = config.resolve(settings.getName());
        try {
            Files.createDirectories(jobSettingsFolder);
        } catch (IOException e) {
            throw new RuntimeException("Can not create the job config directory", e);
        }

        // Create MessageDigest instance
        if (settings.getFs().getChecksum() != null) {
            try {
                messageDigest = MessageDigest.getInstance(settings.getFs().getChecksum());
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("This should never happen as we checked that previously");
            }
        }
    }

    public ElasticsearchClientManager getEsClientManager() {
        return esClientManager;
    }

    /**
     * Upgrade FSCrawler indices
     * @return true if done successfully
     * @throws Exception In case of error
     */
    @SuppressWarnings("deprecation")
    public boolean upgrade() throws Exception {
        // We need to start a client so we can send requests to elasticsearch
        esClientManager.start();

        // The upgrade script is for now a bit dumb. It assumes that you had an old version of FSCrawler (< 2.3) and it will
        // simply move data from index/folder to index_folder
        String index = settings.getElasticsearch().getIndex();

        // Check that the old index actually exists
        if (esClientManager.client().isExistingIndex(index)) {
            // We check that the new indices don't exist yet or are empty
            String indexFolder = settings.getElasticsearch().getIndexFolder();
            boolean indexExists = esClientManager.client().isExistingIndex(indexFolder);
            long numberOfDocs = 0;
            if (indexExists) {
                SearchResponse responseFolder = esClientManager.client().search(new SearchRequest(indexFolder));
                numberOfDocs = responseFolder.getHits().getTotalHits();
            }
            if (numberOfDocs > 0) {
                logger.warn("[{}] already exists and is not empty. No upgrade needed.", indexFolder);
            } else {
                logger.debug("[{}] can be upgraded.", index);

                // Create the new indices with the right mappings (well, we don't read existing user configuration)
                if (!indexExists) {
                    esClientManager.createIndices(settings);
                    logger.info("[{}] has been created.", indexFolder);
                }

                // Run reindex task for folders
                logger.info("Starting reindex folders...");
                int folders = esClientManager.client().reindex(index, INDEX_TYPE_FOLDER, indexFolder);
                logger.info("Done reindexing [{}] folders...", folders);

                // Run delete by query task for folders
                logger.info("Starting removing folders from [{}]...", index);
                esClientManager.client().deleteByQuery(index, INDEX_TYPE_FOLDER);
                logger.info("Done removing folders from [{}]", index);

                logger.info("You can now upgrade your elasticsearch cluster to >=6.0.0!");
                return true;
            }
        } else {
            logger.info("[{}] does not exist. No upgrade needed.", index);
        }

        return false;
    }

    public void start() throws Exception {
        logger.info("Starting FS crawler");
        if (loop < 0) {
            logger.info("FS crawler started in watch mode. It will run unless you stop it with CTRL+C.");
        }

        if (loop == 0 && !rest) {
            closed = true;
        }

        if (closed) {
            logger.info("Fs crawler is closed. Exiting");
            return;
        }

        esClientManager.start();
        esClientManager.createIndices(settings);

        // Start the REST Server if needed
        if (rest) {
            RestServer.start(settings, esClientManager);
            logger.info("FS crawler Rest service started on [{}]", settings.getRest().url());
        }

        // Start the crawler thread - but not if only in rest mode
        if (loop != 0) {
            fsCrawlerThread = new Thread(new FSParser(settings), "fs-crawler");
            fsCrawlerThread.start();
        }
    }

    public void close() throws InterruptedException {
        logger.debug("Closing FS crawler [{}]", settings.getName());

        closed = true;

        synchronized(semaphore) {
            semaphore.notifyAll();
        }

        if (this.fsCrawlerThread != null) {
            while (fsCrawlerThread.isAlive()) {
                // We check that the crawler has been closed effectively
                logger.debug("FS crawler thread is still running");
                Thread.sleep(500);
            }
            logger.debug("FS crawler thread is now stopped");
        }

        // Stop the REST Server if needed
        RestServer.close();
        logger.debug("FS crawler Rest service stopped");

        esClientManager.close();
        logger.debug("ES Client Manager stopped");

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
                    logger.debug("FS crawler thread [{}] is now marked as closed...", fsSettings.getName());
                    return;
                }

                int run = runNumber.incrementAndGet();
                FileAbstractor path = null;

                try {
                    logger.debug("Fs crawler thread [{}] is now running. Run #{}...", fsSettings.getName(), run);
                    stats = new ScanStatistic(fsSettings.getFs().getUrl());

                    path = buildFileAbstractor();
                    path.open();

                    if (!path.exists(fsSettings.getFs().getUrl())) {
                        throw new RuntimeException(fsSettings.getFs().getUrl() + " doesn't exists.");
                    }

                    String rootPathId = SignTool.sign(fsSettings.getFs().getUrl());
                    stats.setRootPathId(rootPathId);

                    LocalDateTime scanDatenew = LocalDateTime.now();
                    LocalDateTime scanDate = getLastDateFromMeta(fsSettings.getName());

                    // We only index the root directory once (first run)
                    // That means that we don't have a scanDate yet
                    if (scanDate == null && fsSettings.getFs().isIndexFolders()) {
                        indexDirectory(fsSettings.getFs().getUrl());
                    }

                    if (scanDate == null) {
                        scanDate = LocalDateTime.MIN;
                    }

                    addFilesRecursively(path, fsSettings.getFs().getUrl(), scanDate);

                    updateFsJob(fsSettings.getName(), scanDatenew);
                } catch (Exception e) {
                    logger.warn("Error while crawling {}: {}", fsSettings.getFs().getUrl(), e.getMessage());
                    if (logger.isDebugEnabled()) {
                        logger.warn("Full stacktrace", e);
                    }
                } finally {
                    if (path != null) {
                        try {
                            path.close();
                        } catch (Exception e) {
                            logger.warn("Error while closing the connection: {}", e, e.getMessage());
                        }
                    }
                }

                if (loop > 0 && run >= loop) {
                    logger.info("FS crawler is stopping after {} run{}", run, run > 1 ? "s" : "");
                    closed = true;
                    return;
                }

                try {
                    logger.debug("Fs crawler is going to sleep for {}", fsSettings.getFs().getUpdateRate());

                    // The problem here is that there is no wait to close the thread while we are sleeping.
                    // Which leads to Zombie threads in our tests

                    synchronized (semaphore) {
                        semaphore.wait(fsSettings.getFs().getUpdateRate().millis());
                        logger.debug("Fs crawler is now waking up again...");
                    }
                } catch (InterruptedException e) {
                    logger.debug("Fs crawler thread has been interrupted: [{}]", e.getMessage());
                    Thread.currentThread().interrupt();
                }
            }
        }

        @SuppressWarnings("unchecked")
        private LocalDateTime getLastDateFromMeta(String jobName) throws IOException {
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
         * @throws Exception In case of error
         */
        private void updateFsJob(String jobName, LocalDateTime scanDate) throws Exception {
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

        private FileAbstractor buildFileAbstractor() {
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

        private void addFilesRecursively(FileAbstractor<?> path, String filepath, LocalDateTime lastScanDate)
                throws Exception {

            logger.debug("indexing [{}] content", filepath);

            final Collection<FileAbstractModel> children = path.getFiles(filepath);
            Collection<String> fsFiles = new ArrayList<>();
            Collection<String> fsFolders = new ArrayList<>();

            if (children != null) {
                for (FileAbstractModel child : children) {
                    String filename = child.name;

                    // https://github.com/dadoonet/fscrawler/issues/1 : Filter documents
                    boolean isIndexable = isIndexable(filename, fsSettings.getFs().getIncludes(), fsSettings.getFs().getExcludes());

                    // It can happen that we a dir "foo" which does not match the include name like "*.txt"
                    // We need to go in it unless it has been explicitly excluded by the user
                    if (child.directory && !isExcluded(filename, fsSettings.getFs().getExcludes())) {
                        isIndexable = true;
                    }

                    logger.debug("[{}] can be indexed: [{}]", filename, isIndexable);
                    if (isIndexable) {
                        if (child.file) {
                            logger.debug("  - file: {}", filename);
                            fsFiles.add(filename);
                            if (child.lastModifiedDate.isAfter(lastScanDate) ||
                                    (child.creationDate != null && child.creationDate.isAfter(lastScanDate))) {
                                try {
                                    indexFile(child, stats, filepath,
                                            fsSettings.getFs().isIndexContent() ? path.getInputStream(child) : null, child.size);
                                    stats.addFile();
                                } catch (java.io.FileNotFoundException e) {
                                    if (fsSettings.getFs().isContinueOnError()) {
                                        logger.warn("Unable to open Input Stream for {}, skipping...", e.getMessage());
                                    } else {
                                        throw e;
                                    }
                                }
                            } else {
                                logger.debug("    - not modified: creation date {} , file date {}, last scan date {}",
                                        child.creationDate, child.lastModifiedDate, lastScanDate);
                            }
                        } else if (child.directory) {
                            logger.debug("  - folder: {}", filename);
                            if (settings.getFs().isIndexFolders()) {
                                fsFolders.add(child.fullpath);
                                indexDirectory(child.fullpath);
                            }
                            addFilesRecursively(path, child.fullpath, lastScanDate);
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

                    if (isIndexable(esfile, fsSettings.getFs().getIncludes(), fsSettings.getFs().getExcludes())
                            && !fsFiles.contains(esfile)) {
                        logger.trace("Removing file [{}] in elasticsearch", esfile);
                        esDelete(fsSettings.getElasticsearch().getIndex(), generateIdFromFilename(esfile, filepath));
                        stats.removeFile();
                    }
                }

                if (settings.getFs().isIndexFolders()) {
                    logger.debug("Looking for removed directories in [{}]...", filepath);
                    Collection<String> esFolders = getFolderDirectory(filepath);

                    // for the delete folder
                    for (String esfolder : esFolders) {
                        if (isIndexable(esfolder, fsSettings.getFs().getIncludes(), fsSettings.getFs().getExcludes())) {
                            logger.trace("Checking directory [{}]", esfolder);
                            if (!fsFolders.contains(esfolder)) {
                                logger.trace("Removing recursively directory [{}] in elasticsearch", esfolder);
                                removeEsDirectoryRecursively(esfolder);
                            }
                        }
                    }
                }
            }
        }

        // TODO Optimize it. We can probably use a search for a big array of filenames instead of
        // Searching fo 10000 files (which is somehow limited).
        private Collection<String> getFileDirectory(String path)
                throws Exception {

            // If the crawler is being closed, we return
            if (closed) {
                return Collections.emptyList();
            }

            logger.trace("Querying elasticsearch for files in dir [{}:{}]", PATH_ROOT, SignTool.sign(path));
            Collection<String> files;
            // Hack because the High Level Client is not compatible with versions < 5.0
            if (esClientManager.client().isIngestSupported()) {
                files = new ArrayList<>();
                SearchResponse response = esClientManager.client().search(
                        new SearchRequest(fsSettings.getElasticsearch().getIndex()).source(
                                new SearchSourceBuilder()
                                        .size(REQUEST_SIZE) // TODO: WHAT? DID I REALLY WROTE THAT? :p
                                        .storedField(FILE_FILENAME)
                                        .query(QueryBuilders.termQuery(PATH_ROOT, SignTool.sign(path)))));

                logger.trace("Response [{}]", response.toString());
                if (response.getHits() != null && response.getHits().getHits() != null) {
                    for (SearchHit hit : response.getHits().getHits()) {
                        String name;
                        if (hit.getFields() != null
                                && hit.getFields().get(FILE_FILENAME) != null) {
                            // In case someone disabled _source which is not recommended
                            name = hit.getFields().get(FILE_FILENAME).getValue();
                        } else {
                            // Houston, we have a problem ! We can't get the old files from ES
                            logger.warn("Can't find stored field name to check existing filenames in path [{}]. " +
                                    "Please set store: true on field [{}]", path, FILE_FILENAME);
                            throw new RuntimeException("Mapping is incorrect: please set stored: true on field [" +
                                    FILE_FILENAME + "].");
                        }
                        files.add(name);
                    }
                }
            } else {
                files = esClientManager.client().getFromStoredFieldsV2(
                        fsSettings.getElasticsearch().getIndex(),
                        REQUEST_SIZE,    // TODO: WHAT? DID I REALLY WROTE THAT? :p
                        FILE_FILENAME,
                        path,
                        QueryBuilders.termQuery(PATH_ROOT, SignTool.sign(path)));
            }

            logger.trace("We found: {}", files);

            return files;
        }

        private Collection<String> getFolderDirectory(String path) throws Exception {
            Collection<String> files = new ArrayList<>();

            // If the crawler is being closed, we return
            if (closed) {
                return files;
            }

            SearchResponse response = esClientManager.client().search(
                    new SearchRequest(fsSettings.getElasticsearch().getIndexFolder()).source(
                            new SearchSourceBuilder()
                                    .size(REQUEST_SIZE) // TODO: WHAT? DID I REALLY WROTE THAT? :p
                                    .query(QueryBuilders.termQuery(fr.pilato.elasticsearch.crawler.fs.beans.Path.FIELD_NAMES.ROOT, SignTool.sign(path)))));

            if (response.getHits() != null && response.getHits().getHits() != null) {
                for (SearchHit hit : response.getHits().getHits()) {
                    String name = hit.getSourceAsMap().get(fr.pilato.elasticsearch.crawler.fs.beans.Path.FIELD_NAMES.REAL).toString();
                    files.add(name);
                }
            }

            return files;
        }

        /**
         * Index a file
         */
        private void indexFile(FileAbstractModel fileAbstractModel, ScanStatistic stats, String dirname, InputStream inputStream,
                               long filesize) throws Exception {
            final String filename = fileAbstractModel.name;
            final LocalDateTime lastmodified = fileAbstractModel.lastModifiedDate;
            final String extension = fileAbstractModel.extension;
            final long size = fileAbstractModel.size;

            logger.debug("fetching content from [{}],[{}]", dirname, filename);

            try {
                // Create the Doc object (only needed when we have add_as_inner_object: true (default) or when we don't index json or xml)
                if (fsSettings.getFs().isAddAsInnerObject() || (!fsSettings.getFs().isJsonSupport() && !fsSettings.getFs().isXmlSupport())) {

                    String fullFilename = new File(dirname, filename).toString();

                    Doc doc = new Doc();

                    // File
                    doc.getFile().setFilename(filename);
                    doc.getFile().setLastModified(localDateTimeToDate(lastmodified));
                    doc.getFile().setIndexingDate(localDateTimeToDate(LocalDateTime.now()));
                    doc.getFile().setUrl("file://" + fullFilename);
                    doc.getFile().setExtension(extension);
                    if (fsSettings.getFs().isAddFilesize()) {
                        doc.getFile().setFilesize(size);
                    }
                    // File

                    // Path
                    // Encoded version of the dir this file belongs to
                    doc.getPath().setRoot(SignTool.sign(dirname));
                    // The virtual URL (not including the initial root dir)
                    doc.getPath().setVirtual(computeVirtualPathName(stats.getRootPath(), fullFilename));
                    // The real and complete filename
                    doc.getPath().setReal(fullFilename);
                    // Path

                    // Attributes
                    if (fsSettings.getFs().isAttributesSupport()) {
                        doc.setAttributes(new Attributes());
                        doc.getAttributes().setOwner(fileAbstractModel.owner);
                        doc.getAttributes().setGroup(fileAbstractModel.group);
                    }
                    // Attributes

                    if (fsSettings.getFs().isIndexContent()) {
                        // If needed, we generate the content in addition to metadata
                        if (fsSettings.getFs().isJsonSupport()) {
                            // https://github.com/dadoonet/fscrawler/issues/5 : Support JSon files
                            doc.setObject(DocParser.asMap(read(inputStream)));
                        } else if (fsSettings.getFs().isXmlSupport()) {
                            // https://github.com/dadoonet/fscrawler/issues/185 : Support Xml files
                            doc.setObject(XmlDocParser.generateMap(inputStream));
                        } else {
                            // Extracting content with Tika
                            generate(fsSettings, inputStream, filename, doc, messageDigest, filesize);
                        }
                    }

                    // We index the data structure
                    esIndex(esClientManager.bulkProcessorDoc(), fsSettings.getElasticsearch().getIndex(),
                            generateIdFromFilename(filename, dirname),
                            DocParser.toJson(doc),
                            fsSettings.getElasticsearch().getPipeline());
                } else if (fsSettings.getFs().isIndexContent()) {
                    if (fsSettings.getFs().isJsonSupport()) {
                        // We index the json content directly
                        esIndex(esClientManager.bulkProcessorDoc(), fsSettings.getElasticsearch().getIndex(),
                                generateIdFromFilename(filename, dirname),
                                read(inputStream),
                                fsSettings.getElasticsearch().getPipeline());
                    } else if (fsSettings.getFs().isXmlSupport()) {
                        // We index the xml content directly
                        esIndex(esClientManager.bulkProcessorDoc(), fsSettings.getElasticsearch().getIndex(),
                                generateIdFromFilename(filename, dirname),
                                XmlDocParser.generate(inputStream),
                                fsSettings.getElasticsearch().getPipeline());
                    }
                }
            } finally {
                // Let's close the stream
                if (inputStream != null) {
                    inputStream.close();
                }
            }
        }

        private String generateIdFromFilename(String filename, String filepath) throws NoSuchAlgorithmException {
            return fsSettings.getFs().isFilenameAsId() ? filename : SignTool.sign((new File(filepath, filename)).toString());
        }

        private String read(InputStream input) throws IOException {
            try (BufferedReader buffer = new BufferedReader(new InputStreamReader(input, "UTF-8"))) {
                return buffer.lines().collect(Collectors.joining("\n"));
            }
        }

        /**
         * Index a Path object (AKA a folder) in elasticsearch
         * @param id    id of the path
         * @param path  path object
         * @throws Exception in case of error
         */
        private void indexDirectory(String id, fr.pilato.elasticsearch.crawler.fs.beans.Path path) throws Exception {
            esIndex(esClientManager.bulkProcessorFolder(), fsSettings.getElasticsearch().getIndexFolder(),
                    id,
                    PathParser.toJson(path),
                    null);
        }

        /**
         * Index a directory
         * @param path complete path like /path/to/subdir
         */
        private void indexDirectory(String path) throws Exception {
            fr.pilato.elasticsearch.crawler.fs.beans.Path pathObject = new fr.pilato.elasticsearch.crawler.fs.beans.Path();
            // The real and complete path
            pathObject.setReal(path);
            String rootdir = path.substring(0, path.lastIndexOf(File.separator));
            // Encoded version of the parent dir
            pathObject.setRoot(SignTool.sign(rootdir));
            // The virtual URL (not including the initial root dir)
            pathObject.setVirtual(computeVirtualPathName(stats.getRootPath(), path));

            indexDirectory(SignTool.sign(path), pathObject);
        }

        /**
         * Remove a full directory and sub dirs recursively
         */
        private void removeEsDirectoryRecursively(final String path) throws Exception {
            logger.debug("Delete folder [{}]", path);
            Collection<String> listFile = getFileDirectory(path);

            for (String esfile : listFile) {
                esDelete(fsSettings.getElasticsearch().getIndex(), SignTool.sign(path.concat(File.separator).concat(esfile)));
            }

            Collection<String> listFolder = getFolderDirectory(path);
            for (String esfolder : listFolder) {
                removeEsDirectoryRecursively(esfolder);
            }

            esDelete(fsSettings.getElasticsearch().getIndexFolder(), SignTool.sign(path));
        }

        /**
         * Add to bulk an IndexRequest in JSon format
         */
        void esIndex(BulkProcessor bulkProcessor, String index, String id, String json, String pipeline) {
            logger.debug("Indexing {}/doc/{}?pipeline={}", index, id, pipeline);
            logger.trace("JSon indexed : {}", json);

            if (!closed) {
                bulkProcessor.add(new IndexRequest(index, "doc", id).source(json, XContentType.JSON).setPipeline(pipeline));
            } else {
                logger.warn("trying to add new file while closing crawler. Document [{}]/[doc]/[{}] has been ignored", index, id);
            }
        }

        /**
         * Add to bulk a DeleteRequest
         */
        void esDelete(String index, String id) {
            logger.debug("Deleting {}/doc/{}", index, id);
            if (!closed) {
                esClientManager.bulkProcessorDoc().add(new DeleteRequest(index, "doc", id));
            } else {
                logger.warn("trying to remove a file while closing crawler. Document [{}]/[doc]/[{}] has been ignored", index, id);
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

    public int getRunNumber() {
        return runNumber.get();
    }
}
