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

package fr.pilato.elasticsearch.crawler.fs.crawler;

import fr.pilato.elasticsearch.crawler.fs.beans.Attributes;
import fr.pilato.elasticsearch.crawler.fs.beans.Doc;
import fr.pilato.elasticsearch.crawler.fs.beans.DocParser;
import fr.pilato.elasticsearch.crawler.fs.beans.FileModel;
import fr.pilato.elasticsearch.crawler.fs.beans.FsJobFileHandler;
import fr.pilato.elasticsearch.crawler.fs.beans.PathParser;
import fr.pilato.elasticsearch.crawler.fs.beans.ScanStatistic;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClientManager;
import fr.pilato.elasticsearch.crawler.fs.framework.SignTool;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.tika.TikaDocParser;
import fr.pilato.elasticsearch.crawler.fs.tika.XmlDocParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.computeVirtualPathName;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.isExcluded;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.isIndexable;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.localDateTimeToDate;

public abstract class FsParserAbstract implements Runnable, FsParser {
    private static final Logger logger = LogManager.getLogger(FsParserAbstract.class);

    protected final FsSettings fsSettings;
    private final FsJobFileHandler fsJobFileHandler;
    private final ElasticsearchWrapper elasticsearchWrapper;
    private final Integer loop;
    private final MessageDigest messageDigest;

    private final AtomicInteger runNumber = new AtomicInteger(0);
    private static final Object semaphore = new Object();
    private ScanStatistic stats;
    private boolean closed;

    protected FsParserAbstract(FsSettings fsSettings, Path config, ElasticsearchClientManager esClientManager, Integer loop) {
        this.fsSettings = fsSettings;
        this.fsJobFileHandler = new FsJobFileHandler(config);
        this.elasticsearchWrapper = new ElasticsearchWrapper(esClientManager, fsSettings);
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
    }

    public Object getSemaphore() {
        return semaphore;
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

            crawl(run);

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

    @Override
    public void crawl(int run) {
        try {
            logger.debug("Fs crawler thread [{}] is now running. Run #{}...", fsSettings.getName(), run);
            stats = new ScanStatistic(fsSettings.getFs().getUrl());

            openConnection();
            validate();

            String rootPathId = SignTool.sign(fsSettings.getFs().getUrl());
            stats.setRootPathId(rootPathId);

            LocalDateTime scanDatenew = LocalDateTime.now();
            LocalDateTime scanDate = fsJobFileHandler.getLastDateFromMeta(fsSettings.getName());

            // We only index the root directory once (first run)
            // That means that we don't have a scanDate yet
            if (scanDate == null && fsSettings.getFs().isIndexFolders()) {
                indexDirectory(stats.getRootPath(), fsSettings.getFs().getUrl());
            }

            if (scanDate == null) {
                scanDate = LocalDateTime.MIN;
            }

            addFilesRecursively(fsSettings.getFs().getUrl(), scanDate);

            fsJobFileHandler.updateFsJob(fsSettings.getName(), scanDatenew, stats);
        } catch (Exception e) {
            logger.warn("Error while crawling {}: {}", fsSettings.getFs().getUrl(), e.getMessage());
            if (logger.isDebugEnabled()) {
                logger.warn("Full stacktrace", e);
            }
        } finally {
            try {
                close();
            } catch (Exception e) {
                logger.warn("Error while closing the connection: {}", e, e.getMessage());
            }
        }
    }

    private void addFilesRecursively(String filepath, LocalDateTime lastScanDate) throws Exception {

        logger.debug("indexing [{}] content", filepath);

        final Collection<FileModel> children = getFiles(filepath);
        Collection<String> fsFiles = new ArrayList<>();
        Collection<String> fsFolders = new ArrayList<>();

        if (children != null) {
            for (FileModel child : children) {
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
                                        fsSettings.getFs().isIndexContent() ? getInputStream(child) : null, child.size);
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
                        if (fsSettings.getFs().isIndexFolders()) {
                            fsFolders.add(child.fullpath);
                            indexDirectory(stats.getRootPath(), child.fullpath);
                        }
                        addFilesRecursively(child.fullpath, lastScanDate);
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
            Collection<String> esFiles = elasticsearchWrapper.getFileDirectory(closed, filepath);

            // for the delete files
            for (String esfile : esFiles) {
                logger.trace("Checking file [{}]", esfile);

                if (isIndexable(esfile, fsSettings.getFs().getIncludes(), fsSettings.getFs().getExcludes())
                        && !fsFiles.contains(esfile)) {
                    logger.trace("Removing file [{}] in elasticsearch", esfile);
                    elasticsearchWrapper.bulk(closed, ElasticsearchWrapper.deleteRequest(fsSettings.getElasticsearch().getIndex(), generateIdFromFilename(esfile, filepath)));
                    stats.removeFile();
                }
            }

            if (fsSettings.getFs().isIndexFolders()) {
                logger.debug("Looking for removed directories in [{}]...", filepath);
                Collection<String> esFolders = elasticsearchWrapper.getFolderDirectory(closed, filepath);

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

    /**
     * Index a file
     */
    private void indexFile(FileModel fileAbstractModel, ScanStatistic stats, String dirname, InputStream inputStream,
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
                        TikaDocParser.generate(fsSettings, inputStream, filename, doc, messageDigest, filesize);
                    }
                }

                // We index the data structure
                elasticsearchWrapper.bulk(closed, ElasticsearchWrapper.indexRequest(fsSettings.getElasticsearch().getIndex(),
                        generateIdFromFilename(filename, dirname),
                        DocParser.toJson(doc),
                        fsSettings.getElasticsearch().getPipeline()));
            } else if (fsSettings.getFs().isIndexContent()) {
                if (fsSettings.getFs().isJsonSupport()) {
                    // We index the json content directly
                    elasticsearchWrapper.bulk(closed, ElasticsearchWrapper.indexRequest(fsSettings.getElasticsearch().getIndex(),
                            generateIdFromFilename(filename, dirname),
                            read(inputStream),
                            fsSettings.getElasticsearch().getPipeline()));
                } else if (fsSettings.getFs().isXmlSupport()) {
                    // We index the xml content directly
                    elasticsearchWrapper.bulk(closed, ElasticsearchWrapper.indexRequest(fsSettings.getElasticsearch().getIndex(),
                            generateIdFromFilename(filename, dirname),
                            XmlDocParser.generate(inputStream),
                            fsSettings.getElasticsearch().getPipeline()));
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

    // TODO Check this as it will consume more likely a lot of memory
    // May be add an option which ask the user if JSON are one line only?
    private String read(InputStream input) throws IOException {
        try (BufferedReader buffer = new BufferedReader(new InputStreamReader(input, "UTF-8"))) {
            return buffer.lines().collect(Collectors.joining("\n"));
        }
    }

    /**
     * Index a directory
     * @param rootPath the root path like /path/to
     * @param path complete path like /path/to/subdir
     */
    private void indexDirectory(String rootPath, String path) throws Exception {
        fr.pilato.elasticsearch.crawler.fs.beans.Path pathObject = new fr.pilato.elasticsearch.crawler.fs.beans.Path();
        // The real and complete path
        pathObject.setReal(path);
        String rootdir = path.substring(0, path.lastIndexOf(File.separator));
        // Encoded version of the parent dir
        pathObject.setRoot(SignTool.sign(rootdir));
        // The virtual URL (not including the initial root dir)
        pathObject.setVirtual(computeVirtualPathName(rootPath, path));

        elasticsearchWrapper.bulk(closed, ElasticsearchWrapper.indexRequest(fsSettings.getElasticsearch().getIndexFolder(),
                SignTool.sign(path),
                PathParser.toJson(pathObject),
                null));
    }

    /**
     * Remove a full directory and sub dirs recursively
     */
    private void removeEsDirectoryRecursively(final String path) throws Exception {
        logger.debug("Delete folder [{}]", path);
        Collection<String> listFile = elasticsearchWrapper.getFileDirectory(closed, path);

        for (String esfile : listFile) {
            elasticsearchWrapper.bulk(closed,
                    ElasticsearchWrapper.deleteRequest(fsSettings.getElasticsearch().getIndex(), SignTool.sign(path.concat(File.separator).concat(esfile))));
        }

        Collection<String> listFolder = elasticsearchWrapper.getFolderDirectory(closed, path);
        for (String esfolder : listFolder) {
            removeEsDirectoryRecursively(esfolder);
        }

        elasticsearchWrapper.bulk(closed, ElasticsearchWrapper.deleteRequest(fsSettings.getElasticsearch().getIndexFolder(), SignTool.sign(path)));
    }

    public int getRunNumber() {
        return runNumber.get();
    }

    public void setClosed(boolean closed) {
        this.closed = closed;
    }

    public boolean isClosed() {
        return closed;
    }

    public Integer getLoop() {
        return loop;
    }
}
