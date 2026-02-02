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

import com.jayway.jsonpath.DocumentContext;
import fr.pilato.elasticsearch.crawler.fs.beans.*;
import fr.pilato.elasticsearch.crawler.fs.beans.FileAbstractModel;
import fr.pilato.elasticsearch.crawler.fs.framework.*;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerDocumentService;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerManagementService;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.Server.PROTOCOL;
import fr.pilato.elasticsearch.crawler.fs.tika.TikaDocParser;
import fr.pilato.elasticsearch.crawler.fs.tika.XmlDocParser;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerExtensionFsProvider;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.io.File;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.*;
import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.asMap;

public class FsParserAbstract extends FsParser {
    private static final Logger logger = LogManager.getLogger();

    private static final String FSCRAWLER_IGNORE_FILENAME = ".fscrawlerignore";
    private static final String FULL_STACKTRACE_LOG_MESSAGE = "Full stacktrace";

    final FsSettings fsSettings;
    private final FsJobFileHandler fsJobFileHandler;
    private final FsAclsFileHandler fsAclsFileHandler;

    private final FsCrawlerManagementService managementService;
    private final FsCrawlerDocumentService documentService;
    private final Integer loop;
    private Map<String, String> aclHashCache;
    private boolean aclHashCacheDirty;
    private final FsCrawlerExtensionFsProvider crawlerPlugin;
    private final String metadataFilename;
    private final byte[] staticMetadata;
    private static final TimeValue CHECK_JOB_INTERVAL = TimeValue.timeValueSeconds(5);

    public FsParserAbstract(FsSettings fsSettings, Path config, FsCrawlerManagementService managementService,
                           FsCrawlerDocumentService documentService, Integer loop,
                           FsCrawlerExtensionFsProvider crawlerPlugin) {
        this.fsSettings = fsSettings;
        this.fsJobFileHandler = new FsJobFileHandler(config);
        this.fsAclsFileHandler = initializeAclsFileHandler(fsSettings, config);
        this.aclHashCache = initializeAclCache(fsSettings);
        this.aclHashCacheDirty = false;
        this.managementService = managementService;
        this.documentService = documentService;
        this.crawlerPlugin = crawlerPlugin;

        this.loop = loop;
        logger.debug("creating fs crawler thread [{}] for [{}] every [{}]", fsSettings.getName(),
                fsSettings.getFs().getUrl(),
                fsSettings.getFs().getUpdateRate());

        metadataFilename = resolveMetadataFilename(fsSettings);
        staticMetadata = loadStaticMetadata(fsSettings);
    }

    @Override
    public void close() {
        super.close();
        logger.trace("Closing the parser {}", this.getClass().getSimpleName());
        try {
            crawlerPlugin.closeConnection();
        } catch (Exception e) {
            logger.error("Error while closing crawler plugin", e);
            throw new RuntimeException(e);
        }
    }

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

            try {
                logger.info("Run #{}: job [{}]: starting...", run, fsSettings.getName());
                ScanStatistic stats = new ScanStatistic(fsSettings.getFs().getUrl());
                LocalDateTime startDate = LocalDateTime.now();
                stats.setStartTime(startDate);

                crawlerPlugin.openConnection();

                if (!crawlerPlugin.exists(fsSettings.getFs().getUrl())) {
                    throw new RuntimeException(fsSettings.getFs().getUrl() + " doesn't exists.");
                }

                String rootPathId = SignTool.sign(fsSettings.getFs().getUrl());
                stats.setRootPathId(rootPathId);

                // We need to round that latest date to the lower second and remove 2 seconds.
                // See #82: https://github.com/dadoonet/fscrawler/issues/82
                LocalDateTime scanDatenew = startDate.minusSeconds(2);
                LocalDateTime scanDate = getLastDateFromMeta(fsSettings.getName());

                // We only index the root directory once (first run)
                // That means that we don't have a scanDate yet
                if (scanDate == null && fsSettings.getFs().isIndexFolders()) {
                    indexDirectory(fsSettings.getFs().getUrl(), fsSettings.getFs().getUrl());
                }

                if (scanDate == null) {
                    scanDate = LocalDateTime.MIN;
                }

                addFilesRecursively(fsSettings.getFs().getUrl(), scanDate, stats);

                stats.setEndTime(LocalDateTime.now());

                // Compute the next check time by adding fsSettings.getFs().getUpdateRate().millis()
                LocalDateTime nextCheck = scanDatenew.plus(fsSettings.getFs().getUpdateRate().millis(), ChronoUnit.MILLIS);
                logger.info("Run #{}: job [{}]: indexed [{}], deleted [{}], documents up to [{}]. " +
                                "Started at [{}], finished at [{}], took [{}]. " +
                                "Will restart at [{}].", run, fsSettings.getName(),
                        stats.getNbDocScan(), stats.getNbDocDeleted(), scanDatenew,
                        stats.getStartTime(), stats.getEndTime(), durationToString(stats.computeDuration()),
                        nextCheck);

                updateFsJob(fsSettings.getName(), scanDatenew, nextCheck, stats);
                // TODO Update stats
                // updateStats(fsSettings.getName(), stats);
            } catch (Exception e) {
                logger.warn("Error while crawling {}: {}", fsSettings.getFs().getUrl(), e.getMessage() == null ? e.getClass().getName() : e.getMessage());
                logger.debug(FULL_STACKTRACE_LOG_MESSAGE, e);
            } finally {
                persistAclHashCacheIfNeeded();
                try {
                    logger.debug("Closing FS crawler plugin [{}].", crawlerPlugin.getType());
                    crawlerPlugin.closeConnection();
                } catch (Exception e) {
                    logger.warn("Error while closing the connection: {}", e.getMessage());
                    logger.debug(FULL_STACKTRACE_LOG_MESSAGE, e);
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
                        long totalWaitTime = 0;
                        long maxWaitTime = fsSettings.getFs().getUpdateRate().millis();
                        while (totalWaitTime < maxWaitTime && !closed) {
                            long waitTime = Math.min(CHECK_JOB_INTERVAL.millis(), maxWaitTime - totalWaitTime);
                            semaphore.wait(waitTime);
                            totalWaitTime += waitTime;
                            logger.trace("Waking up after {} ms to check if the condition changed. We waited for {} in total on {}...", waitTime, totalWaitTime, maxWaitTime);

                            // Read again the FsJob to check if we need to stop the crawler
                            try {
                                FsJob fsJob = fsJobFileHandler.read(fsSettings.getName());
                                if (fsJob.getNextCheck() == null || LocalDateTime.now().isAfter(fsJob.getNextCheck())) {
                                    logger.debug("Fs crawler is waking up because next check time [{}] is in the past.", fsJob.getNextCheck());
                                    break; // Exit the loop to re-run the crawler
                                }
                            } catch (NoSuchFileException e) {
                                // The file does not exist yet, we can continue
                            } catch (IOException e) {
                                logger.warn("Error while reading job metadata: {}", e.getMessage());
                            }
                        }
                        logger.debug("Fs crawler is now waking up again after a total wait time of {}...", totalWaitTime);
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
     * Update the job statistics
     *
     * @param jobName   job name
     * @param scanDate  last date we scan the dirs
     * @param nextCheck next planned date to scan again
     * @param stats     the stats used to update the job statistics
     * @throws IOException In case of error while saving the job stats
     */
    private void updateFsJob(final String jobName, final LocalDateTime scanDate, final LocalDateTime nextCheck, final ScanStatistic stats) throws IOException {
        FsJob fsJob = new FsJob(jobName, scanDate, nextCheck, stats.getNbDocScan(), stats.getNbDocDeleted());
        // TODO replace this with a call to the management service (Elasticsearch)
        fsJobFileHandler.write(jobName, fsJob);
        logger.debug("Updating job metadata after run for [{}]: lastrun [{}], nextcheck [{}], indexed [{}], deleted [{}]",
                jobName, scanDate, nextCheck, stats.getNbDocScan(), stats.getNbDocDeleted());
    }

    private Map<String, String> loadAclHashCache(String jobName) {
        if (fsAclsFileHandler == null) {
            return new HashMap<>();
        }
        try {
            return fsAclsFileHandler.read(jobName);
        } catch (IOException e) {
            logger.warn("Failed to load ACL cache for [{}]: {}", jobName, e.getMessage());
            logger.debug(FULL_STACKTRACE_LOG_MESSAGE, e);
            return new HashMap<>();
        }
    }

    private FsAclsFileHandler initializeAclsFileHandler(FsSettings fsSettings, Path config) {
        if (fsSettings.getFs().isAttributesSupport() && fsSettings.getFs().isAclSupport()) {
            return new FsAclsFileHandler(config);
        }
        return null;
    }

    private Map<String, String> initializeAclCache(FsSettings fsSettings) {
        if (fsAclsFileHandler == null) {
            return new HashMap<>();
        }
        return loadAclHashCache(fsSettings.getName());
    }

    private String resolveMetadataFilename(FsSettings fsSettings) {
        if (fsSettings.getTags() != null && !StringUtils.isEmpty(fsSettings.getTags().getMetaFilename())) {
            String filename = fsSettings.getTags().getMetaFilename();
            logger.debug("We are going to use [{}] as meta file if found while crawling dirs", filename);
            return filename;
        }
        return null;
    }

    private byte[] loadStaticMetadata(FsSettings fsSettings) {
        if (fsSettings.getTags() == null || StringUtils.isEmpty(fsSettings.getTags().getStaticMetaFilename())) {
            return new byte[0];
        }

        File staticMetadataFile = new File(fsSettings.getTags().getStaticMetaFilename());
        if (!staticMetadataFile.exists() || !staticMetadataFile.isFile()) {
            throw new FsCrawlerIllegalConfigurationException("Static meta file [" + staticMetadataFile.getAbsolutePath() + "] does not exist or is not a file.");
        }
        try {
            byte[] data = FileUtils.readFileToByteArray(staticMetadataFile);
            logger.debug("We are going to use [{}] as the static meta file for every document", fsSettings.getTags().getStaticMetaFilename());
            return data;
        } catch (IOException e) {
            throw new FsCrawlerIllegalConfigurationException("Static meta file [" + staticMetadataFile.getAbsolutePath() + "] cannot be read.", e);
        }
    }

    private void persistAclHashCacheIfNeeded() {
        if (fsAclsFileHandler == null || !aclHashCacheDirty) {
            return;
        }
        try {
            fsAclsFileHandler.write(fsSettings.getName(), aclHashCache);
            aclHashCacheDirty = false;
        } catch (IOException e) {
            logger.warn("Failed to store ACL cache for [{}]: {}", fsSettings.getName(), e.getMessage());
            logger.debug(FULL_STACKTRACE_LOG_MESSAGE, e);
        }
    }

    private boolean shouldTrackAclChanges() {
        return fsAclsFileHandler != null;
    }

    private boolean hasAclChanged(String filename, String filepath, FileAbstractModel fileAbstractModel) throws NoSuchAlgorithmException {
        if (!shouldTrackAclChanges()) {
            return false;
        }
        String id = generateIdFromFilename(filename, filepath);
        List<FileAcl> acls = fileAbstractModel.getAcls();
        if (acls == null || acls.isEmpty()) {
            return aclHashCache.containsKey(id);
        }
        String currentHash = fileAbstractModel.getAclHash();
        if (currentHash == null) {
            currentHash = FsCrawlerUtil.computeAclHash(acls);
        }
        if (currentHash == null) {
            return false;
        }
        String previousHash = aclHashCache.get(id);
        if (previousHash == null) {
            return true;
        }
        return !Objects.equals(previousHash, currentHash);
    }

    private void rememberCurrentAclHash(String id, FileAbstractModel fileAbstractModel) {
        if (!shouldTrackAclChanges()) {
            return;
        }
        String currentHash = fileAbstractModel.getAclHash();
        if (currentHash == null) {
            currentHash = FsCrawlerUtil.computeAclHash(fileAbstractModel.getAcls());
        }
        if (currentHash == null) {
            if (aclHashCache.remove(id) != null) {
                aclHashCacheDirty = true;
            }
            return;
        }
        String previous = aclHashCache.put(id, currentHash);
        if (!Objects.equals(previous, currentHash)) {
            aclHashCacheDirty = true;
        }
    }

    private void removeStoredAclHash(String id) {
        if (!shouldTrackAclChanges()) {
            return;
        }
        if (aclHashCache.remove(id) != null) {
            aclHashCacheDirty = true;
        }
    }

    private boolean shouldIndexBecauseOfChanges(FileAbstractModel child, LocalDateTime lastScanDate, String filename, String filepath)
            throws NoSuchAlgorithmException {
        if (child.getLastModifiedDate().isAfter(lastScanDate)) {
            return true;
        }
        if (child.getCreationDate() != null && child.getCreationDate().isAfter(lastScanDate)) {
            return true;
        }
        if (shouldTrackAclChanges() && hasAclChanged(filename, filepath, child)) {
            logger.trace("    - ACL change detected for {}", child.getFullpath());
            return true;
        }
        return false;
    }

    private void addFilesRecursively(final String filepath, final LocalDateTime lastScanDate, final ScanStatistic stats)
            throws Exception {
        logger.debug("indexing [{}] content", filepath);

        if (closed) {
            logger.debug("FS crawler thread [{}] is now marked as closed...", fsSettings.getName());
            return;
        }

        final Collection<FileAbstractModel> children = crawlerPlugin.getFiles(filepath);
        Collection<String> fsFiles = new ArrayList<>();
        Collection<String> fsFolders = new ArrayList<>();

        if (children != null) {
            boolean ignoreFolder = false;
            FileAbstractModel metadataFile = null;
            for (FileAbstractModel child : children) {
                // We check if we have a .fscrawlerignore file within this folder in which case
                // we want to ignore all files and subdirs
                if (child.getName().equalsIgnoreCase(FSCRAWLER_IGNORE_FILENAME)) {
                    logger.debug("We found a [{}] file in folder: [{}]. Let's skip it.", FSCRAWLER_IGNORE_FILENAME, filepath);
                    ignoreFolder = true;
                    break;
                }

                // We check if we have a .meta.yml file (or equivalent) within this folder in which case
                // we want to merge its content with the current file metadata
                if (child.getName().equalsIgnoreCase(metadataFilename)) {
                    logger.debug("We found a [{}] file in folder: [{}]", metadataFilename, filepath);
                    metadataFile = child;
                }
            }

            if (!ignoreFolder) {
                for (FileAbstractModel child : children) {
                    logger.trace("FileAbstractModel = {}", child);
                    String filename = child.getName();

                    // If the filename is the expected metadata file, we skip it
                    if (filename.equalsIgnoreCase(metadataFilename)) {
                        logger.trace("Skipping metadata file [{}]", filename);
                        continue;
                    }

                    String virtualFileName = computeVirtualPathName(stats.getRootPath(), computeRealPathName(filepath, filename));

                    // https://github.com/dadoonet/fscrawler/issues/1 : Filter documents
                    boolean isIndexable = isIndexable(child.isDirectory(), virtualFileName, fsSettings.getFs().getIncludes(), fsSettings.getFs().getExcludes());

                    logger.trace("[{}] can be indexed: [{}]", virtualFileName, isIndexable);
                    if (isIndexable) {
                        if (child.isFile()) {
                            logger.trace("  - file: {}", virtualFileName);
                            fsFiles.add(filename);
                            if (shouldIndexBecauseOfChanges(child, lastScanDate, filename, filepath)) {
                                logger.trace("    - modified: creation date {} , file date {}, last scan date {}",
                                        child.getCreationDate(), child.getLastModifiedDate(), lastScanDate);

                                if (isFileSizeUnderLimit(fsSettings.getFs().getIgnoreAbove(), child.getSize())) {
                                    InputStream inputStream = null;
                                    InputStream metadataStream = null;
                                    try {
                                        if (fsSettings.getFs().isIndexContent() || fsSettings.getFs().isStoreSource()) {
                                            inputStream = crawlerPlugin.getInputStream(child);
                                        }
                                        if (metadataFile != null) {
                                            metadataStream = crawlerPlugin.getInputStream(metadataFile);
                                        }
                                        indexFile(child, stats, filepath, inputStream, child.getSize(), metadataStream);
                                        stats.addFile();
                                    } catch (Exception e) {
                                        if (fsSettings.getFs().isContinueOnError()) {
                                            logger.warn("Unable to index {}, skipping...: {}", filename, e.getMessage());
                                        } else {
                                            throw e;
                                        }
                                    } finally {
                                        if (metadataStream != null) {
                                            crawlerPlugin.closeInputStream(metadataStream);
                                        }
                                        if (inputStream != null) {
                                            crawlerPlugin.closeInputStream(inputStream);
                                        }
                                    }
                                } else {
                                    logger.debug("file [{}] has a size [{}] above the limit [{}]. We skip it.", filename,
                                            new ByteSizeValue(child.getSize()), fsSettings.getFs().getIgnoreAbove());
                                }
                            } else {
                                logger.trace("    - not modified: creation date {} , file date {}, last scan date {}",
                                        child.getCreationDate(), child.getLastModifiedDate(), lastScanDate);
                            }
                        } else if (child.isDirectory()) {
                            logger.debug("  - folder: {}", filename);
                            if (fsSettings.getFs().isIndexFolders()) {
                                fsFolders.add(child.getFullpath());
                                indexDirectory(child.getFullpath(), fsSettings.getFs().getUrl());
                            }
                            addFilesRecursively(child.getFullpath(), lastScanDate, stats);
                        } else {
                            logger.debug("  - other: {}", filename);
                            logger.debug("Not a file nor a dir. Skipping {}", child.getFullpath());
                        }
                    } else {
                        logger.debug("  - ignored file/dir: {}", filename);
                    }
                }

                logger.trace("End of parsing the folder [{}]", filepath);
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
                    logger.trace("Removing file [{}] in elasticsearch", esfile);
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
                            removeEsDirectoryRecursively(esfolder, stats);
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
                           long filesize, InputStream externalTags) throws Exception {
        final String filename = fileAbstractModel.getName();
        final LocalDateTime created = fileAbstractModel.getCreationDate();
        final LocalDateTime lastModified = fileAbstractModel.getLastModifiedDate();
        final LocalDateTime lastAccessed = fileAbstractModel.getAccessDate();
        final String extension = fileAbstractModel.getExtension();
        final long size = fileAbstractModel.getSize();

        logger.trace("fetching content from [{}],[{}]", dirname, filename);
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
            if (fsSettings.getServer() == null || PROTOCOL.LOCAL.equals(fsSettings.getServer().getProtocol())) {
                doc.getFile().setUrl("file://" + fullFilename);
            } else if (PROTOCOL.FTP.equals(fsSettings.getServer().getProtocol())) {
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
                if (fsSettings.getFs().isAclSupport()) {
                    List<FileAcl> fileAcls = fileAbstractModel.getAcls();
                    if (!fileAcls.isEmpty()) {
                        doc.getAttributes().setAcl(fileAcls);
                    }
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
                TikaDocParser.generate(fsSettings, inputStream, doc, filesize);
            }

            // Merge static metadata if available
            Doc mergedDoc = doc;
            if (staticMetadata.length > 0) {
                mergedDoc = DocUtils.getMergedDoc(doc, fsSettings.getTags().getStaticMetaFilename(),
                        new ByteArrayInputStream(staticMetadata));
            }
            // Merge metadata if available in the same folder
            mergedDoc = DocUtils.getMergedDoc(mergedDoc, metadataFilename, externalTags);

            // We index the data structure
            if (isIndexable(mergedDoc.getContent(), fsSettings.getFs().getFilters())) {
                if (!closed) {
                    FSCrawlerLogger.documentDebug(id,
                            computeVirtualPathName(stats.getRootPath(), fullFilename),
                            "Indexing content");
                    documentService.index(
                            fsSettings.getElasticsearch().getIndex(),
                            id,
                            mergedDoc,
                            fsSettings.getElasticsearch().getPipeline());
                    rememberCurrentAclHash(id, fileAbstractModel);
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
                // We need to check that the provided file is actually a JSON file which can be parsed
                try {
                    DocumentContext documentContext = JsonUtil.parseJsonAsDocumentContext(inputStream);
                    String jsonString = documentContext.jsonString();

                    // We index the json content directly
                    if (!closed) {
                        documentService.indexRawJson(
                                fsSettings.getElasticsearch().getIndex(),
                                id,
                                jsonString,
                                fsSettings.getElasticsearch().getPipeline());
                        rememberCurrentAclHash(id, fileAbstractModel);
                    } else {
                        logger.warn("trying to add new file while closing crawler. Document [{}]/[{}] has been ignored",
                                fsSettings.getElasticsearch().getIndex(), id);
                    }
                } catch (Exception e) {
                    logger.warn("Unable to parse JSON file [{}] in [{}]: {}", filename, dirname, e.getMessage());
                    logger.debug(FULL_STACKTRACE_LOG_MESSAGE, e);
                } finally {
                    if (inputStream != null) {
                        crawlerPlugin.closeInputStream(inputStream);
                    }
                }
            } else if (fsSettings.getFs().isXmlSupport()) {
                FSCrawlerLogger.documentDebug(generateIdFromFilename(filename, dirname),
                        computeVirtualPathName(stats.getRootPath(), fullFilename),
                        "Indexing xml content");
                // We need to check that the provided file is actually a JSON file which can be parsed
                try {
                    // We index the xml content directly (after transformation to json)
                    if (!closed) {
                        documentService.indexRawJson(
                                fsSettings.getElasticsearch().getIndex(),
                                id,
                                XmlDocParser.generate(inputStream),
                                fsSettings.getElasticsearch().getPipeline());
                        rememberCurrentAclHash(id, fileAbstractModel);
                    } else {
                        logger.warn("trying to add new file while closing crawler. Document [{}]/[{}] has been ignored",
                                fsSettings.getElasticsearch().getIndex(), id);
                    }
                } catch (Exception e) {
                    logger.warn("Unable to parse XML file [{}] in [{}]: {}", filename, dirname, e.getMessage());
                    logger.debug(FULL_STACKTRACE_LOG_MESSAGE, e);
                } finally {
                    if (inputStream != null) {
                        crawlerPlugin.closeInputStream(inputStream);
                    }
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

    /**
     * Index a folder object in elasticsearch
     * @param id        id of the folder
     * @param folder    path object
     */
    private void indexDirectory(String id, Folder folder) {
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
     * @param rootPath the root path we started from
     */
    private void indexDirectory(String path, String rootPath) throws Exception {
        // Find the last separator regardless of type to handle mixed separator scenarios on Windows
        int lastSepIndex = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        if (lastSepIndex < 0) {
            // Edge case: no separator found (e.g., root path like "C:")
            logger.warn("Cannot index directory [{}]: no path separator found. " +
                    "On Windows, please use backslashes (e.g., C:\\\\path\\\\to\\\\dir) in fs.url setting.", path);
            return;
        }
        String name = path.substring(lastSepIndex + 1);
        String rootdir = path.substring(0, lastSepIndex);

        File folderInfo = new File(path);

        Folder folder = new Folder(name,
                SignTool.sign(rootdir),
                path,
                computeVirtualPathName(rootPath, path),
                getCreationTime(folderInfo),
                getModificationTime(folderInfo),
                getLastAccessTime(folderInfo));

        if (fsSettings.getFs().isAttributesSupport() && (fsSettings.getServer() == null || PROTOCOL.LOCAL.equals(fsSettings.getServer().getProtocol()))) {
            Attributes attributes = new Attributes();
            attributes.setOwner(getOwnerName(folderInfo));
            attributes.setGroup(getGroupName(folderInfo));
            int permissions = getFilePermissions(folderInfo);
            if (permissions >= 0) {
                attributes.setPermissions(permissions);
            }
            if (fsSettings.getFs().isAclSupport()) {
                List<FileAcl> folderAcls = getFileAcls(folderInfo.toPath());
                if (!folderAcls.isEmpty()) {
                    attributes.setAcl(folderAcls);
                }
            }

            if (attributes.getOwner() != null || attributes.getGroup() != null || attributes.getAcl() != null || permissions >= 0) {
                folder.setAttributes(attributes);
            }
        }

        indexDirectory(SignTool.sign(path), folder);
    }

    /**
     * Remove a full directory and sub dirs recursively
     */
    private void removeEsDirectoryRecursively(final String path, ScanStatistic stats) throws Exception {
        logger.debug("Delete folder [{}]", path);
        Collection<String> listFile = getFileDirectory(path);

        for (String esfile : listFile) {
            esDelete(managementService, fsSettings.getElasticsearch().getIndex(), generateIdFromFilename(esfile, path));
            stats.removeFile();
        }

        Collection<String> listFolder = getFolderDirectory(path);
        for (String esfolder : listFolder) {
            removeEsDirectoryRecursively(esfolder, stats);
        }

        esDelete(managementService, fsSettings.getElasticsearch().getIndexFolder(), SignTool.sign(path));
    }

    /**
     * Remove a document with the document service
     */
    private void esDelete(FsCrawlerDocumentService service, String index, String id) {
        logger.debug("Deleting {}/{}", index, id);
        if (!closed) {
            service.delete(index, id);
            removeStoredAclHash(id);
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
            removeStoredAclHash(id);
        } else {
            logger.warn("trying to remove a file while closing crawler. Document [{}]/[{}] has been ignored", index, id);
        }
    }

}
