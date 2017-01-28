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
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClientManager;
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
import fr.pilato.elasticsearch.crawler.fs.meta.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.FsSettingsFileHandler;
import fr.pilato.elasticsearch.crawler.fs.rest.RestServer;
import fr.pilato.elasticsearch.crawler.fs.tika.XmlDocParser;
import fr.pilato.elasticsearch.crawler.fs.util.FsCrawlerUtil;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static fr.pilato.elasticsearch.crawler.fs.FsCrawlerValidator.validateSettings;
import static fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClient.extractFromPath;
import static fr.pilato.elasticsearch.crawler.fs.tika.TikaDocParser.generate;

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

    private static final String PATH_ENCODED = Doc.FIELD_NAMES.PATH + "." + fr.pilato.elasticsearch.crawler.fs.meta.doc.Path.FIELD_NAMES.ENCODED;
    private static final String FILE_FILENAME = Doc.FIELD_NAMES.FILE + "." + fr.pilato.elasticsearch.crawler.fs.meta.doc.File.FIELD_NAMES.FILENAME;

    private final AtomicInteger runNumber = new AtomicInteger(0);

    private final static String FSCRAWLER_PROPERTIES = "fscrawler.properties";
    public static final Properties properties;

    static {
        properties = new Properties();
        try {
            properties.load(FsCrawler.class.getClassLoader().getResourceAsStream(FSCRAWLER_PROPERTIES));
        } catch (IOException e) {
            logger.error("Can not find [{}] resource in the class loader", FSCRAWLER_PROPERTIES);
            throw new RuntimeException(e);
        }
    }

    private static final int REQUEST_SIZE = 10000;
    public static final int LOOP_INFINITE = -1;

    private volatile boolean closed = false;
    private final Object semaphore = new Object();

    private final FsSettings settings;
    private final FsJobFileHandler fsJobFileHandler;
    private final Integer loop;
    private final boolean updateMapping;
    private final boolean rest;
    private MessageDigest messageDigest = null;

    private Thread fsCrawlerThread;

    private final ElasticsearchClientManager esClientManager;

    public FsCrawlerImpl(Path config, FsSettings settings) {
        this(config, settings, LOOP_INFINITE, false, false);
    }

    public FsCrawlerImpl(Path config, FsSettings settings, Integer loop, boolean updateMapping, boolean rest) {
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
        this.updateMapping = updateMapping;
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

    public void start() throws Exception {
        logger.info("Starting FS crawler");
        if (loop < 0) {
            logger.info("FS crawler started in watch mode. It will run unless you stop it with CTRL+C.");
        }

        esClientManager.start();
        esClientManager.createIndexAndMappings(settings, updateMapping);

        if (loop == 0 && !rest) {
            closed = true;
        }

        if (closed) {
            logger.info("Fs crawler is closed. Exiting");
            return;
        }

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

        synchronized (semaphore) {
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
                        indexRootDirectory(fsSettings.getFs().getUrl());
                    }

                    if (scanDate == null) {
                        scanDate = LocalDateTime.MIN;
                    }

                    addFilesRecursively(path, fsSettings.getFs().getUrl(), scanDate);

                    updateFsJob(fsSettings.getName(), scanDatenew);
                } catch (Exception e) {
                    logger.warn("Error while indexing content from {}: {}", fsSettings.getFs().getUrl(), e.getMessage());
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
         *
         * @param jobName  job name
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
                    boolean isIndexable = FsCrawlerUtil.isIndexable(filename, fsSettings.getFs().getIncludes(), fsSettings.getFs().getExcludes());

                    // It can happen that we a dir "foo" which does not match the include name like "*.txt"
                    // We need to go in it unless it has been explicitly excluded by the user
                    if (child.directory && !FsCrawlerUtil.isExcluded(filename, fsSettings.getFs().getExcludes())) {
                        isIndexable = true;
                    }

                    logger.debug("[{}] can be indexed: [{}]", filename, isIndexable);
                    if (isIndexable) {
                        if (child.file) {
                            logger.debug("  - file: {}", filename);
                            fsFiles.add(filename);
                            if (child.lastModifiedDate.isAfter(lastScanDate) ||
                                    (child.creationDate != null && child.creationDate.isAfter(lastScanDate))) {
                                indexFile(child, stats, filepath, path.getInputStream(child), child.size);
                                stats.addFile();
                            } else {
                                logger.debug("    - not modified: creation date {} , file date {}, last scan date {}",
                                        child.creationDate, child.lastModifiedDate, lastScanDate);
                            }
                        } else if (child.directory) {
                            logger.debug("  - folder: {}", filename);
                            if (settings.getFs().isIndexFolders()) {
                                fsFolders.add(filename);
                                indexDirectory(stats, filename, child.fullpath);
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

                    if (FsCrawlerUtil.isIndexable(esfile, fsSettings.getFs().getIncludes(), fsSettings.getFs().getExcludes())
                            && !fsFiles.contains(esfile)) {
                        File file = new File(filepath, esfile);

                        logger.trace("Removing file [{}] in elasticsearch", esfile);
                        esDelete(fsSettings.getElasticsearch().getIndex(), fsSettings.getElasticsearch().getType(),
                                SignTool.sign(file.getAbsolutePath()));
                        stats.removeFile();
                    }
                }

                if (settings.getFs().isIndexFolders()) {
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
            SearchResponse response = esClientManager.client().search(
                    fsSettings.getElasticsearch().getIndex(),
                    fsSettings.getElasticsearch().getType(),
                    PATH_ENCODED + ":" + SignTool.sign(path),
                    REQUEST_SIZE, // TODO: WHAT? DID I REALLY WROTE THAT? :p
                    "_source", FILE_FILENAME
            );

            logger.trace("Response [{}]", response.toString());
            if (response.getHits() != null && response.getHits().getHits() != null) {
                for (SearchResponse.Hit hit : response.getHits().getHits()) {
                    String name;
                    if (hit.getSource() != null
                            && extractFromPath(hit.getSource(), Doc.FIELD_NAMES.FILE).get(fr.pilato.elasticsearch.crawler.fs.meta.doc
                            .File.FIELD_NAMES.FILENAME) != null) {
                        name = (String) extractFromPath(hit.getSource(), Doc.FIELD_NAMES.FILE).get(fr.pilato.elasticsearch.crawler.fs.meta.doc.File.FIELD_NAMES.FILENAME);
                    } else if (hit.getFields() != null
                            && hit.getFields().get(FILE_FILENAME) != null) {
                        // In case someone disabled _source which is not recommended
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
                return String.valueOf(((List) nameObject).get(0));
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

            SearchResponse response = esClientManager.client().search(
                    fsSettings.getElasticsearch().getIndex(),
                    FsCrawlerUtil.INDEX_TYPE_FOLDER,
                    PATH_ENCODED + ":" + SignTool.sign(path),
                    REQUEST_SIZE // TODO: WHAT? DID I REALLY WROTE THAT? :p
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
        private void indexFile(FileAbstractModel fileAbstractModel, ScanStatistic stats, String dirname, InputStream inputStream,
                               long filesize) throws Exception {
            final String filename = fileAbstractModel.name;
            final LocalDateTime lastmodified = fileAbstractModel.lastModifiedDate;
            final long size = fileAbstractModel.size;

            logger.debug("fetching content from [{}],[{}]", dirname, filename);

            try {
                // Create the Doc object
                Doc doc = new Doc();
                String docId;
                if (fsSettings.getFs().isFilenameAsId()) {
                    docId = generateIdFromFilename(filename, dirname);
                } else {
                    docId = SignTool.sign((new File(dirname, filename)).toString());
                }
                // File
                doc.getFile().setFilename(filename);
                doc.getFile().setLastModified(lastmodified);
                doc.getFile().setIndexingDate(LocalDateTime.now());
                doc.getFile().setUrl("file://" + (new File(dirname, filename)).toString());
                if (fsSettings.getFs().isAddFilesize()) {
                    doc.getFile().setFilesize(size);
                }
                // File

                // Path
                doc.getPath().setEncoded(SignTool.sign(dirname));
                doc.getPath().setRoot(stats.getRootPathId());
                doc.getPath().setVirtual(FsCrawlerUtil.computeVirtualPathName(stats, dirname));
                doc.getPath().setReal((new File(dirname, filename)).toString());
                // Path

                // Attributes
                if (fsSettings.getFs().isAttributesSupport()) {
                    doc.setAttributes(new Attributes());
                    doc.getAttributes().setOwner(fileAbstractModel.owner);
                    doc.getAttributes().setGroup(fileAbstractModel.group);
                }
                // We index
                if (fsSettings.getFs().isIndexContent()) {
                    String fileExtension = FilenameUtils.getExtension(filename);
                    if (fsSettings.getFs().isJsonSupport() && (fileExtension.equals("json") || fileExtension.equals("js"))) {
                        // https://github.com/dadoonet/fscrawler/issues/5 : Support JSon files
                        if (fsSettings.getFs().isUseDeprecatedJsonSetup()) {
                            esIndex(esClientManager.bulkProcessor(), fsSettings.getElasticsearch().getIndex(),
                                    fsSettings.getElasticsearch().getType(),
                                    generateIdFromFilename(filename, dirname),
                                    read(inputStream));
                            return;
                        } else {
                                // https://github.com/dadoonet/fscrawler/issues/5 : Support JSon files
                                doc.setJsonContent(DocParser.fromJsonToMap(read(inputStream)));
                        }
                    } else if (fsSettings.getFs().isXmlSupport() && fileExtension.equals("xml")) {
                        // https://github.com/dadoonet/fscrawler/issues/185 : Support Xml files
                        if (fsSettings.getFs().isUseDeprecatedJsonSetup()) {
                            esIndex(esClientManager.bulkProcessor(), fsSettings.getElasticsearch().getIndex(),
                                    fsSettings.getElasticsearch().getType(),
                                    generateIdFromFilename(filename, dirname),
                                    XmlDocParser.generate(inputStream));
                            return;
                        } else {
                                // https://github.com/dadoonet/fscrawler/issues/185 : Support Xml files
                                doc.setJsonContent(XmlDocParser.generateMap(inputStream));
                        }
                    } else {
                        generate(fsSettings, inputStream, filename, doc, messageDigest, filesize);
                    }
                }
                esIndex(esClientManager.bulkProcessor(), fsSettings.getElasticsearch().getIndex(),
                        fsSettings.getElasticsearch().getType(),
                        docId,
                        doc);
            } finally {
                // Let's close the stream
                inputStream.close();
            }
        }

        private String generateIdFromFilename(String filename, String filepath) throws NoSuchAlgorithmException {
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
            return id;
        }

        private String read(InputStream input) throws IOException {
            try (BufferedReader buffer = new BufferedReader(new InputStreamReader(input, "UTF-8"))) {
                return buffer.lines().collect(Collectors.joining("\n"));
            }
        }

        private void indexDirectory(String id, String name, String root, String virtual, String encoded)
                throws Exception {

            fr.pilato.elasticsearch.crawler.fs.meta.doc.Path path = new fr.pilato.elasticsearch.crawler.fs.meta.doc.Path();
            path.setReal(name);
            path.setRoot(root);
            path.setVirtual(virtual);
            path.setEncoded(encoded);
            esIndex(esClientManager.bulkProcessor(), fsSettings.getElasticsearch().getIndex(),
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
        public void esIndex(BulkProcessor bulkProcessor, String index, String type, String id,
                             Doc doc) throws Exception {
            esIndex(bulkProcessor, index, type, id, DocParser.toJson(doc));
        }

        public void esIndex(BulkProcessor bulkProcessor, String index, String type, String id, fr.pilato.elasticsearch.crawler.fs.meta.doc.Path path)
                throws Exception {
            esIndex(bulkProcessor, index, type, id, PathParser.toJson(path));
        }

        /**
         * Add to bulk an IndexRequest in JSon format
         */
        public void esIndex(BulkProcessor bulkProcessor, String index, String type, String id, String json) {
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
        public void esDelete(String index, String type, String id) {
            logger.debug("Deleting from ES " + index + ", " + type + ", " + id);
            if (!closed) {
                esClientManager.bulkProcessor().add(new DeleteRequest(index, type, id));
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

    private static boolean hasLength(CharSequence str) {
        return str != null && str.length() > 0;
    }

    public int getRunNumber() {
        return runNumber.get();
    }
}
