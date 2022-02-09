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
import fr.pilato.elasticsearch.crawler.fs.beans.Folder;
import fr.pilato.elasticsearch.crawler.fs.beans.FsJob;
import fr.pilato.elasticsearch.crawler.fs.beans.FsJobFileHandler;
import fr.pilato.elasticsearch.crawler.fs.beans.ScanStatistic;
import fr.pilato.elasticsearch.crawler.fs.crawler.FileAbstractModel;
import fr.pilato.elasticsearch.crawler.fs.crawler.FileAbstractor;
import fr.pilato.elasticsearch.crawler.fs.crawler.fs.FileAbstractorFile;
import fr.pilato.elasticsearch.crawler.fs.framework.ByteSizeValue;
import fr.pilato.elasticsearch.crawler.fs.framework.FSCrawlerLogger;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil;
import fr.pilato.elasticsearch.crawler.fs.framework.OsValidator;
import fr.pilato.elasticsearch.crawler.fs.framework.SignTool;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerDocumentService;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerManagementService;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.Server.PROTOCOL;
import fr.pilato.elasticsearch.crawler.fs.tika.TikaDocParser;
import fr.pilato.elasticsearch.crawler.fs.tika.XmlDocParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.*;
import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.asMap;

public abstract class FsParserAbstract extends FsParser {
    private static final Logger logger = LogManager.getLogger(FsParserAbstract.class);

    private static final String FSCRAWLER_IGNORE_FILENAME = ".fscrawlerignore";

    final FsSettings fsSettings;
    private final FsJobFileHandler fsJobFileHandler;

    private final FsCrawlerManagementService managementService;
    private final FsCrawlerDocumentService documentService;
    private final Integer loop;
    private final MessageDigest messageDigest;
    private final String pathSeparator;

    private ScanStatistic stats;

    FsParserAbstract(FsSettings fsSettings, Path config, FsCrawlerManagementService managementService, FsCrawlerDocumentService documentService, Integer loop) {
        this.fsSettings = fsSettings;
        this.fsJobFileHandler = new FsJobFileHandler(config);
        this.managementService = managementService;
        this.documentService = documentService;

        this.loop = loop;
        logger.debug("creating fs crawler thread [{}] for [{}] every [{}]", fsSettings.getName(),
                fsSettings.getFs().getUrl(),
                fsSettings.getFs().getUpdateRate());

        // Create MessageDigest instance
        if (fsSettings.getFs().getChecksum() != null) {
            try {
                messageDigest = MessageDigest.getInstance(fsSettings.getFs().getChecksum());
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("This should never happen as we checked that previously");
            }
        } else {
            messageDigest = null;
        }

        pathSeparator = FsCrawlerUtil.getPathSeparator(fsSettings.getFs().getUrl());
        if (OsValidator.WINDOWS && fsSettings.getServer() == null) {
            logger.debug("We are running on Windows without Server settings so we use the separator in accordance with fs.url");
            FileAbstractorFile.separator = pathSeparator;
        }
    }

    protected abstract FileAbstractor<?> buildFileAbstractor();

    @Override
    public void run() {
        logger.info("FS crawler started for [{}] for [{}] every [{}]", fsSettings.getName(),
                fsSettings.getFs().getUrl(),
                fsSettings.getFs().getUpdateRate());
        closed = false;
        while (true) {
            if (closed) {
                logger.debug("FS crawler thread [{}] is now marked as closed...", fsSettings.getName());
                return;
            }

            int run = runNumber.incrementAndGet();
            FileAbstractor<?> path = null;

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
                logger.warn("Error while crawling {}: {}", fsSettings.getFs().getUrl(), e.getMessage() == null ? e.getClass().getName() : e.getMessage());
                if (logger.isDebugEnabled()) {
                    logger.warn("Full stacktrace", e);
                }
            } finally {
                if (path != null) {
                    try {
                        path.close();
                    } catch (Exception e) {
                        logger.warn("Error while closing the connection: {}", e.getMessage());
                        if (logger.isDebugEnabled()) {
                            logger.warn("Full stacktrace", e);
                        }
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

                if (!closed) {
                    synchronized (semaphore) {
                        semaphore.wait(fsSettings.getFs().getUpdateRate().millis());
                        logger.debug("Fs crawler is now waking up again...");
                    }
                }
            } catch (InterruptedException e) {
                logger.debug("Fs crawler thread has been interrupted: [{}]", e.getMessage());
                Thread.currentThread().interrupt();
            }
        }
    }

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

    private void addFilesRecursively(FileAbstractor<?> path, String filepath, LocalDateTime lastScanDate)
            throws Exception {

        logger.debug("indexing [{}] content", filepath);

        if (closed) {
            logger.debug("FS crawler thread [{}] is now marked as closed...", fsSettings.getName());
            return;
        }

        final Collection<FileAbstractModel> children = path.getFiles(filepath);
        Collection<String> fsFiles = new ArrayList<>();
        Collection<String> fsFolders = new ArrayList<>();

        if (children != null) {
            boolean ignoreFolder = false;
            for (FileAbstractModel child : children) {
                // We check if we have a .fscrawlerignore file within this folder in which case
                // we want to ignore all files and subdirs
                if (child.getName().equalsIgnoreCase(FSCRAWLER_IGNORE_FILENAME)) {
                    logger.debug("We found a [{}] file in folder: [{}]. Let's skip it.", FSCRAWLER_IGNORE_FILENAME, filepath);
                    ignoreFolder = true;
                    break;
                }
            }

            if (!ignoreFolder) {
                for (FileAbstractModel child : children) {
                    logger.trace("FileAbstractModel = {}", child);
                    String filename = child.getName();

                    String virtualFileName = computeVirtualPathName(stats.getRootPath(), computeRealPathName(filepath, filename));

                    // https://github.com/dadoonet/fscrawler/issues/1 : Filter documents
                    boolean isIndexable = isIndexable(child.isDirectory(), virtualFileName, fsSettings.getFs().getIncludes(), fsSettings.getFs().getExcludes());

                    logger.debug("[{}] can be indexed: [{}]", virtualFileName, isIndexable);
                    if (isIndexable) {
                        if (child.isFile()) {
                            logger.debug("  - file: {}", virtualFileName);
                            fsFiles.add(filename);
                            if (child.getLastModifiedDate().isAfter(lastScanDate) ||
                                    (child.getCreationDate() != null && child.getCreationDate().isAfter(lastScanDate))) {
                                if (isFileSizeUnderLimit(fsSettings.getFs().getIgnoreAbove(), child.getSize())) {
                                    InputStream inputStream = null;
                                    try {
                                        if (fsSettings.getFs().isIndexContent() || fsSettings.getFs().isStoreSource()) {
                                            inputStream = path.getInputStream(child);
                                        }
                                        indexFile(child, stats, filepath, inputStream, child.getSize());
                                        stats.addFile();
                                    } catch (Exception e) {
                                        if (fsSettings.getFs().isContinueOnError()) {
                                            logger.warn("Unable to index {}, skipping...: {}", filename, e.getMessage());
                                        } else {
                                            throw e;
                                        }
                                    } finally {
                                        if (inputStream != null) {
                                            path.closeInputStream(inputStream);
                                        }
                                    }
                                } else {
                                    logger.debug("file [{}] has a size [{}] above the limit [{}]. We skip it.", filename,
                                            new ByteSizeValue(child.getSize()), fsSettings.getFs().getIgnoreAbove());
                                }
                            } else {
                                logger.debug("    - not modified: creation date {} , file date {}, last scan date {}",
                                        child.getCreationDate(), child.getLastModifiedDate(), lastScanDate);
                            }
                        } else if (child.isDirectory()) {
                            logger.debug("  - folder: {}", filename);
                            if (fsSettings.getFs().isIndexFolders()) {
                                fsFolders.add(child.getFullpath());
                                indexDirectory(child.getFullpath());
                            }
                            addFilesRecursively(path, child.getFullpath(), lastScanDate);
                        } else {
                            logger.debug("  - other: {}", filename);
                            logger.debug("Not a file nor a dir. Skipping {}", child.getFullpath());
                        }
                    } else {
                        logger.debug("  - ignored file/dir: {}", filename);
                    }
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

                String virtualFileName = computeVirtualPathName(stats.getRootPath(), computeRealPathName(filepath, esfile));
                if (isIndexable(false, virtualFileName, fsSettings.getFs().getIncludes(), fsSettings.getFs().getExcludes())
                        && !fsFiles.contains(esfile)) {
                    logger.trace("Removing file [{}] in elasticsearch/workplace", esfile);
                    esDelete(documentService, fsSettings.getElasticsearch().getIndex(), generateIdFromFilename(esfile, filepath));
                    stats.removeFile();
                }
            }

            if (fsSettings.getFs().isIndexFolders()) {
                logger.debug("Looking for removed directories in [{}]...", filepath);
                Collection<String> esFolders = getFolderDirectory(filepath);

                // for the delete folder
                for (String esfolder : esFolders) {
                    String virtualFileName = computeVirtualPathName(stats.getRootPath(), computeRealPathName(filepath, esfolder));
                    if (isIndexable(true, virtualFileName, fsSettings.getFs().getIncludes(), fsSettings.getFs().getExcludes())) {
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

    private Collection<String> getFileDirectory(String path)
            throws Exception {
        // If the crawler is being closed, we return
        if (closed) {
            return new ArrayList<>();
        }
        return managementService.getFileDirectory(path);
    }

    private Collection<String> getFolderDirectory(String path) throws Exception {
        // If the crawler is being closed, we return
        if (closed) {
            return new ArrayList<>();
        }
        return managementService.getFolderDirectory(path);
    }

    /**
     * Index a file
     */
    private void indexFile(FileAbstractModel fileAbstractModel, ScanStatistic stats, String dirname, InputStream inputStream,
                           long filesize) throws Exception {
        final String filename = fileAbstractModel.getName();
        final LocalDateTime created = fileAbstractModel.getCreationDate();
        final LocalDateTime lastModified = fileAbstractModel.getLastModifiedDate();
        final LocalDateTime lastAccessed = fileAbstractModel.getAccessDate();
        final String extension = fileAbstractModel.getExtension();
        final long size = fileAbstractModel.getSize();

        logger.debug("fetching content from [{}],[{}]", dirname, filename);
        String fullFilename = computeRealPathName(dirname, filename);

        // Create the Doc object (only needed when we have add_as_inner_object: true (default) or when we don't index json or xml)
        String id = generateIdFromFilename(filename, dirname);
        if (fsSettings.getFs().isAddAsInnerObject() || (!fsSettings.getFs().isJsonSupport() && !fsSettings.getFs().isXmlSupport())) {
            Doc doc = new Doc();

            // File
            doc.getFile().setFilename(filename);
            doc.getFile().setCreated(localDateTimeToDate(created));
            doc.getFile().setLastModified(localDateTimeToDate(lastModified));
            doc.getFile().setLastAccessed(localDateTimeToDate(lastAccessed));
            doc.getFile().setIndexingDate(localDateTimeToDate(LocalDateTime.now()));
            if (fsSettings.getServer() == null) {
                doc.getFile().setUrl("file://" + fullFilename);
            } else if (fsSettings.getServer().getProtocol().equals(PROTOCOL.FTP)) {
                doc.getFile().setUrl(String.format("ftp://%s:%d%s", fsSettings.getServer().getHostname(), fsSettings.getServer().getPort(), fullFilename));
            }
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
                doc.getAttributes().setOwner(fileAbstractModel.getOwner());
                doc.getAttributes().setGroup(fileAbstractModel.getGroup());
                if (fileAbstractModel.getPermissions() >= 0) {
                    doc.getAttributes().setPermissions(fileAbstractModel.getPermissions());
                }
            }
            // Attributes

            // If needed, we generate the content in addition to metadata
            if (fsSettings.getFs().isJsonSupport()) {
                // https://github.com/dadoonet/fscrawler/issues/5 : Support JSon files
                doc.setObject(asMap(inputStream));
            } else if (fsSettings.getFs().isXmlSupport()) {
                // https://github.com/dadoonet/fscrawler/issues/185 : Support Xml files
                doc.setObject(XmlDocParser.generateMap(inputStream));
            } else {
                // Extracting content with Tika
                TikaDocParser.generate(fsSettings, inputStream, filename, fullFilename, doc, messageDigest, filesize);
            }

            // We index the data structure
            if (isIndexable(doc.getContent(), fsSettings.getFs().getFilters())) {
                if (!closed) {
                    FSCrawlerLogger.documentDebug(id,
                            computeVirtualPathName(stats.getRootPath(), fullFilename),
                            "Indexing content");
                    documentService.index(
                            fsSettings.getElasticsearch().getIndex(),
                            id,
                            doc,
                            fsSettings.getElasticsearch().getPipeline());
                } else {
                    logger.warn("trying to add new file while closing crawler. Document [{}]/[{}] has been ignored",
                            fsSettings.getElasticsearch().getIndex(), id);
                }
            } else {
                logger.debug("We ignore file [{}] because it does not match all the patterns {}", filename,
                        fsSettings.getFs().getFilters());
            }
        } else {
            if (fsSettings.getFs().isJsonSupport()) {
                FSCrawlerLogger.documentDebug(generateIdFromFilename(filename, dirname),
                        computeVirtualPathName(stats.getRootPath(), fullFilename),
                        "Indexing json content");
                // We index the json content directly
                if (!closed) {
                    documentService.indexRawJson(
                            fsSettings.getElasticsearch().getIndex(),
                            id,
                            read(inputStream),
                            fsSettings.getElasticsearch().getPipeline());
                } else {
                    logger.warn("trying to add new file while closing crawler. Document [{}]/[{}] has been ignored",
                            fsSettings.getElasticsearch().getIndex(), id);
                }
            } else if (fsSettings.getFs().isXmlSupport()) {
                FSCrawlerLogger.documentDebug(generateIdFromFilename(filename, dirname),
                        computeVirtualPathName(stats.getRootPath(), fullFilename),
                        "Indexing xml content");
                // We index the xml content directly (after transformation to json)
                if (!closed) {
                    documentService.indexRawJson(
                            fsSettings.getElasticsearch().getIndex(),
                            id,
                            XmlDocParser.generate(inputStream),
                            fsSettings.getElasticsearch().getPipeline());
                } else {
                    logger.warn("trying to add new file while closing crawler. Document [{}]/[{}] has been ignored",
                            fsSettings.getElasticsearch().getIndex(), id);
                }
            }
        }
    }

    private String generateIdFromFilename(String filename, String filepath) throws NoSuchAlgorithmException {
        String filepathForId = filepath.replace("\\", "/");
        String filenameForId = filename.replace("\\", "").replace("/", "");
        String idSource = filepathForId.endsWith("/") ? filepathForId.concat(filenameForId) : filepathForId.concat("/").concat(filenameForId);
        return fsSettings.getFs().isFilenameAsId() ? filename : SignTool.sign(idSource);
    }

    private String read(InputStream input) throws IOException {
        try (BufferedReader buffer = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            return buffer.lines().collect(Collectors.joining("\n"));
        }
    }

    /**
     * Index a folder object in elasticsearch
     * @param id        id of the folder
     * @param folder    path object
     */
    private void indexDirectory(String id, Folder folder) throws IOException {
        if (!closed) {
            managementService.storeVisitedDirectory(fsSettings.getElasticsearch().getIndexFolder(), id, folder);
        } else {
            logger.warn("trying to add new file while closing crawler. Document [{}]/[{}] has been ignored",
                    fsSettings.getElasticsearch().getIndexFolder(), id);
        }
    }

    /**
     * Index a directory
     * @param path complete path like "/", "/path/to/subdir", "C:\\dir", "C:/dir", "/C:/dir", "//SOMEONE/dir"
     */
    private void indexDirectory(String path) throws Exception {
        String name = path.substring(path.lastIndexOf(pathSeparator) + 1);
        String rootdir = path.substring(0, path.lastIndexOf(pathSeparator));
        Folder folder = new Folder(name, SignTool.sign(rootdir), path, computeVirtualPathName(stats.getRootPath(), path));

        indexDirectory(SignTool.sign(path), folder);
    }

    /**
     * Remove a full directory and sub dirs recursively
     */
    private void removeEsDirectoryRecursively(final String path) throws Exception {
        logger.debug("Delete folder [{}]", path);
        Collection<String> listFile = getFileDirectory(path);

        for (String esfile : listFile) {
            esDelete(managementService, fsSettings.getElasticsearch().getIndex(), SignTool.sign(path.concat(pathSeparator).concat(esfile)));
        }

        Collection<String> listFolder = getFolderDirectory(path);
        for (String esfolder : listFolder) {
            removeEsDirectoryRecursively(esfolder);
        }

        esDelete(managementService, fsSettings.getElasticsearch().getIndexFolder(), SignTool.sign(path));
    }

    /**
     * Remove a document with the document service
     */
    private void esDelete(FsCrawlerDocumentService service, String index, String id) throws IOException {
        logger.debug("Deleting {}/{}", index, id);
        if (!closed) {
            service.delete(index, id);
        } else {
            logger.warn("trying to remove a file while closing crawler. Document [{}]/[{}] has been ignored", index, id);
        }
    }

    /**
     * Remove a document with the management service
     */
    private void esDelete(FsCrawlerManagementService service, String index, String id) {
        logger.debug("Deleting {}/{}", index, id);
        if (!closed) {
            service.delete(index, id);
        } else {
            logger.warn("trying to remove a file while closing crawler. Document [{}]/[{}] has been ignored", index, id);
        }
    }

}
