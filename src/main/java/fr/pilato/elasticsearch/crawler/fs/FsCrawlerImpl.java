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

import fr.pilato.elasticsearch.crawler.fs.fileabstractor.FileAbstractModel;
import fr.pilato.elasticsearch.crawler.fs.fileabstractor.FileAbstractor;
import fr.pilato.elasticsearch.crawler.fs.fileabstractor.FileAbstractorFile;
import fr.pilato.elasticsearch.crawler.fs.fileabstractor.FileAbstractorSSH;
import fr.pilato.elasticsearch.crawler.fs.meta.job.FsJob;
import fr.pilato.elasticsearch.crawler.fs.meta.job.FsJobFileHandler;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.Elasticsearch;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.FsSettingsFileHandler;
import fr.pilato.elasticsearch.crawler.fs.util.ElasticsearchUtils;
import fr.pilato.elasticsearch.crawler.fs.util.FsCrawlerUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tika.metadata.Metadata;
import org.elasticsearch.ExceptionsHelper;
import org.elasticsearch.action.bulk.BulkProcessor;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.Base64;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.util.concurrent.EsExecutors;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.indices.IndexAlreadyExistsException;
import org.elasticsearch.search.SearchHit;
import org.joda.time.DateTime;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import static fr.pilato.elasticsearch.crawler.fs.TikaInstance.tika;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

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


    private volatile BulkProcessor bulkProcessor;

    private volatile boolean closed = false;

    /**
     * We store config files here...
     * Default to ~/.fscrawler
     */
    private final FsSettings settings;
    private final FsSettingsFileHandler fsSettingsFileHandler;
    private final FsJobFileHandler fsJobFileHandler;

    private TransportClient client;

    public FsCrawlerImpl(Path config, FsSettings settings) {
        this.fsSettingsFileHandler = new FsSettingsFileHandler(config);
        this.fsJobFileHandler = new FsJobFileHandler(config);

        this.settings = settings;
        if (settings.getFs().getUrl() == null) {
            logger.warn("`url` is not set. Please define it. Falling back to default: /esdir.");
            settings.getFs().setUrl("/esdir");
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
                    !Strings.hasLength(settings.getServer().getUsername())) {
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
            client = TransportClient.builder()
                    .settings(Settings.settingsBuilder()
                            .put("client.transport.ignore_cluster_name", true))
                    .build();
            for (Elasticsearch.Node node : settings.getElasticsearch().getNodes()) {
                client.addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(node.getHost()), node.getPort()));
            }

            client.admin().indices().prepareCreate(settings.getElasticsearch().getIndex()).get();
        } catch (Exception e) {
            if (e instanceof IndexAlreadyExistsException || ExceptionsHelper.unwrapCause(e) instanceof IndexAlreadyExistsException) {
                // that's fine
            } else {
                logger.warn("failed to create index [{}], disabling crawler...", settings.getElasticsearch().getIndex());
                throw e;
            }
        }

        try {
            // If needed, we create the new mapping for files
            if (!settings.getFs().isJsonSupport())
                ElasticsearchUtils.pushMapping(client, settings.getElasticsearch().getIndex(), settings.getElasticsearch().getType(),
                        FsCrawlerUtil.buildFsFileMapping(settings.getElasticsearch().getType(), true,
                                settings.getFs().isStoreSource()));
        } catch (Exception e) {
            logger.warn("failed to create mapping for [{}/{}], disabling crawler...",
                    settings.getElasticsearch().getIndex(), settings.getElasticsearch().getType());
            throw e;
        }

        // Creating bulk processor
        this.bulkProcessor = ElasticsearchUtils.buildBulkProcessor(client, settings.getElasticsearch().getBulkSize(),
                settings.getElasticsearch().getFlushInterval().millis());

        // We save crawler settings
        // TODO May be do that in another place?
        fsSettingsFileHandler.write(settings);

        // Start the crawler thread
        EsExecutors.daemonThreadFactory(Settings.EMPTY, "fs_crawler").newThread(new FSParser(settings)).start();
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
        private FsSettings fsSettings;

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

                    Date scanDatenew = new Date();
                    Date scanDate = getLastDateFromMeta(fsSettings.getName());

                    // We only index the root directory once (first run)
                    // That means that we don't have a scanDate yet
                    if (scanDate == null) {
                        indexRootDirectory(directory);
                    }

                    addFilesRecursively(fsSettings.getFs().getUrl(), scanDate);

                    updateFsJob(fsSettings.getName(), scanDatenew);
                } catch (Exception e) {
                    logger.warn("Error while indexing content from {}", fsSettings.getFs().getUrl());
                    logger.debug("", e);
                }

                try {
                    logger.debug("Fs crawler is going to sleep for {}", fsSettings.getFs().getUpdateRate());
                    Thread.sleep(fsSettings.getFs().getUpdateRate().millis());
                } catch (InterruptedException e1) {
                }
            }
        }

        @SuppressWarnings("unchecked")
        private Date getLastDateFromMeta(String jobName) throws IOException {
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
        private void updateFsJob(String jobName, Date scanDate) throws Exception {
            // We need to round that latest date to the lower second and
            // remove 2 seconds.
            // See #82: https://github.com/dadoonet/fscrawler/issues/82
            scanDate = new DateTime(scanDate).secondOfDay().roundFloorCopy().minusSeconds(2).toDate();
            FsJob fsJob = FsJob.builder()
                    .setName(jobName)
                    .setLastrun(new DateTime(scanDate).secondOfDay().roundFloorCopy().minusSeconds(2).toDate())
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

        private void addFilesRecursively(String filepath, Date lastScanDate)
                throws Exception {

            logger.debug("Indexing [{}] content", filepath);
            FileAbstractor path = buildFileAbstractor();
            path.open();

            try {
                final Collection<FileAbstractModel> children = path.getFiles(filepath);
                Collection<String> fsFiles = new ArrayList<>();
                Collection<String> fsFolders = new ArrayList<>();

                if (children != null) {
                    for (FileAbstractModel child : children) {
                        String filename = child.name;

                        // Ignore temporary files
                        if (filename.contains("~")) {
                            continue;
                        }

                        if (child.file) {
                            logger.debug("  - file: {}", filename);

                            // https://github.com/dadoonet/fscrawler/issues/1 : Filter documents
                            if (FsCrawlerUtil.isIndexable(filename, fsSettings.getFs().getIncludes(), fsSettings.getFs().getExcludes())) {
                                fsFiles.add(filename);
                                if ((lastScanDate == null || child.lastModifiedDate > lastScanDate
                                        .getTime()) || (child.creationDate > 0 && child.creationDate > lastScanDate.getTime())) {
                                    indexFile(stats, child.name, filepath, path.getInputStream(child), child.lastModifiedDate);
                                    stats.addFile();
                                } else {
                                    logger.debug("    - not modified: creation date {} , file date {}, last scan date {}",
                                            child.creationDate, child.lastModifiedDate, lastScanDate.getTime());
                                }
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
                        logger.trace("Checking directory [{}]", esfolder);
                        if (!fsFolders.contains(esfolder)) {
                            logger.trace("Removing recursively directory [{}] in elasticsearch", esfolder);
                            removeEsDirectoryRecursively(filepath, esfolder);
                        }
                    }
                }
            } finally {
                path.close();
            }
        }

        // TODO Optimize it. We can probably use a search for a big array of filenames instead of
        // Searching fo 50000 files (which is somehow limited).
        private Collection<String> getFileDirectory(String path)
                throws Exception {
            Collection<String> files = new ArrayList<>();

            // If the crawler is being closed, we return
            if (closed) {
                return files;
            }

            SearchRequestBuilder srb = client
                    .prepareSearch(fsSettings.getElasticsearch().getIndex())
                    .setTypes(fsSettings.getElasticsearch().getType())
                    .setQuery(
                            QueryBuilders.termQuery(
                                    PATH_ENCODED,
                                    SignTool.sign(path)))
                    // TODO: WHAT? DID I REALLY WROTE THAT? :p
                    .setSize(50000)
                    .addField(FILE_FILENAME);

            logger.trace("Querying elasticsearch for files in dir [{}:{}]", PATH_ENCODED, SignTool.sign(path));

            logger.trace("Request [{}]", srb.toString());
            SearchResponse response = srb.get();

            logger.trace("Response [{}]", response.toString());
            if (response.getHits() != null && response.getHits().getHits() != null) {
                for (SearchHit hit : response.getHits().getHits()) {
                    String name = null;
                    if (hit.getSource() != null && hit.getSource().get(FILE_FILENAME) != null) {
                        name = hit.getSource().get(FILE_FILENAME).toString();
                    } else if (hit.getFields() != null && hit.getFields().get(FILE_FILENAME) != null) {
                        name = hit.getFields().get(FILE_FILENAME).getValue().toString();
                    } else {
                        // Houston, we have a problem ! We can't get the old files from ES
                        logger.warn("Can't find in _source nor fields the existing filenames in path [{}]. " +
                                "Please enable _source or store field [{}]", path, FILE_FILENAME);
                    }
                    files.add(name);
                }
            }

            return files;

        }

        private Collection<String> getFolderDirectory(String path)
                throws Exception {
            Collection<String> files = new ArrayList<>();

            // If the crawler is being closed, we return
            if (closed) {
                return files;
            }

            SearchResponse response = client
                    .prepareSearch(fsSettings.getElasticsearch().getIndex())
                    .setTypes(FsCrawlerUtil.INDEX_TYPE_FOLDER)
                    .setQuery(
                            QueryBuilders.termQuery(
                                    PATH_ENCODED,
                                    SignTool.sign(path)))
                    .setSize(50000)
                    .get();

            if (response.getHits() != null
                    && response.getHits().getHits() != null) {
                for (SearchHit hit : response.getHits().getHits()) {
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
        private void indexFile(ScanStatistic stats, String filename, String filepath, InputStream fileReader, long lastmodified) throws Exception {
            logger.debug("fetching content from [{}],[{}]", filepath, filename);

            // write it to a byte[] using a buffer since we don't know the exact
            // image size
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
                        data);
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
                        logger.trace("using [{}] indexed chars", fsSettings.getFs().getIndexedChars());
                        if (indexedChars == -1) {
                            logger.trace("indexed chars has been disabled. All text will be extracted");
                        } else {
                            logger.trace("using [{}] indexed chars", fsSettings.getFs().getIndexedChars());
                        }
                    }
                }
                Metadata metadata = new Metadata();

                String parsedContent;
                try {
                    // Set the maximum length of strings returned by the parseToString method, -1 sets no limit
                    parsedContent = tika().parseToString(StreamInput.wrap(data), metadata, indexedChars);
                } catch (Throwable e) {
                    logger.debug("Failed to extract [" + indexedChars + "] characters of text for [" + filename + "]", e);
                    parsedContent = "";
                }

                XContentBuilder source = jsonBuilder().startObject();

                if (logger.isTraceEnabled()) {
                    source.prettyPrint();
                }

                // File
                source
                        .startObject(FsCrawlerUtil.Doc.FILE)
                        .field(FsCrawlerUtil.Doc.File.FILENAME, filename)
                        .field(FsCrawlerUtil.Doc.File.LAST_MODIFIED, new Date(lastmodified))
                        .field(FsCrawlerUtil.Doc.File.INDEXING_DATE, new Date())
                        .field(FsCrawlerUtil.Doc.File.CONTENT_TYPE, metadata.get(Metadata.CONTENT_TYPE))
                        .field(FsCrawlerUtil.Doc.File.URL, "file://" + (new File(filepath, filename)).toString());

                // We only add `indexed_chars` if we have other value than default or -1
                if (fsSettings.getFs().getIndexedChars() != null && fsSettings.getFs().getIndexedChars().value() != -1) {
                    source.field(FsCrawlerUtil.Doc.File.INDEXED_CHARS, indexedChars);
                }

                if (fsSettings.getFs().isAddFilesize()) {
                    if (metadata.get(Metadata.CONTENT_LENGTH) != null) {
                        // We try to get CONTENT_LENGTH from Tika first
                        source.field(FsCrawlerUtil.Doc.File.FILESIZE, metadata.get(Metadata.CONTENT_LENGTH));
                    } else {
                        // Otherwise, we use our byte[] length
                        source.field(FsCrawlerUtil.Doc.File.FILESIZE, data.length);
                    }
                }
                source.endObject(); // File

                // Path
                source
                        .startObject(FsCrawlerUtil.Doc.PATH)
                        .field(FsCrawlerUtil.Doc.Path.ENCODED, SignTool.sign(filepath))
                        .field(FsCrawlerUtil.Doc.Path.ROOT, stats.getRootPathId())
                        .field(FsCrawlerUtil.Doc.Path.VIRTUAL,
                                FsCrawlerUtil.computeVirtualPathName(stats, filepath))
                        .field(FsCrawlerUtil.Doc.Path.REAL, (new File(filepath, filename)).toString())
                        .endObject(); // Path

                // Meta
                source
                        .startObject(FsCrawlerUtil.Doc.META)
                        .field(FsCrawlerUtil.Doc.Meta.AUTHOR, metadata.get(Metadata.AUTHOR))
                        .field(FsCrawlerUtil.Doc.Meta.TITLE, metadata.get(Metadata.TITLE))
                        .field(FsCrawlerUtil.Doc.Meta.DATE, metadata.get(Metadata.DATE))
                        .array(FsCrawlerUtil.Doc.Meta.KEYWORDS, Strings.commaDelimitedListToStringArray(metadata.get(Metadata.KEYWORDS)))
                        .endObject(); // Meta

                // Doc content
                source.field(FsCrawlerUtil.Doc.CONTENT, parsedContent);

                // Doc as binary attachment
                if (fsSettings.getFs().isStoreSource()) {
                    source.field(FsCrawlerUtil.Doc.ATTACHMENT, Base64.encodeBytes(data));
                }

                // End of our document
                source.endObject();

                // We index
                esIndex(fsSettings.getElasticsearch().getIndex(),
                        fsSettings.getElasticsearch().getType(),
                        SignTool.sign((new File(filepath, filename)).toString()),
                        source);
            }

        }

        private void indexDirectory(String id, String name, String root, String virtual, String encoded)
                throws Exception {
            esIndex(fsSettings.getElasticsearch().getIndex(),
                    FsCrawlerUtil.INDEX_TYPE_FOLDER,
                    id,
                    jsonBuilder().startObject()
                            .field(FsCrawlerUtil.Dir.NAME, name)
                            .field(FsCrawlerUtil.Dir.ROOT, root)
                            .field(FsCrawlerUtil.Dir.VIRTUAL, virtual)
                            .field(FsCrawlerUtil.Dir.ENCODED, encoded)
                            .endObject());
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
                             XContentBuilder xb) throws Exception {
            esIndex(index, type, id, xb.bytes().toBytes());
        }

        /**
         * Add to bulk an IndexRequest in JSon format
         */
        private void esIndex(String index, String type, String id,
                             byte[] json) throws Exception {
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
        private void esDelete(String index, String type, String id) throws Exception {
            logger.debug("Deleting from ES " + index + ", " + type + ", " + id);
            if (!closed) {
                bulkProcessor.add(new DeleteRequest(index, type, id));
            } else {
                logger.warn("trying to remove a file while closing crawler. Document [{}]/[{}]/[{}] has been ignored", index, type, id);
            }
        }
    }
}
