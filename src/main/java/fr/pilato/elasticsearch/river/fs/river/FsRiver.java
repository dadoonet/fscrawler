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

package fr.pilato.elasticsearch.river.fs.river;

import fr.pilato.elasticsearch.river.fs.util.FsRiverUtil;
import org.apache.tika.metadata.Metadata;
import org.elasticsearch.ExceptionsHelper;
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingResponse;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkProcessor;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.block.ClusterBlockException;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.cluster.metadata.MappingMetaData;
import org.elasticsearch.common.Base64;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.io.stream.BytesStreamInput;
import org.elasticsearch.common.joda.time.format.ISODateTimeFormat;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.util.concurrent.EsExecutors;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.support.XContentMapValues;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.indices.IndexAlreadyExistsException;
import org.elasticsearch.river.AbstractRiverComponent;
import org.elasticsearch.river.River;
import org.elasticsearch.river.RiverName;
import org.elasticsearch.river.RiverSettings;
import org.elasticsearch.search.SearchHit;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.*;

import static fr.pilato.elasticsearch.river.fs.river.TikaInstance.tika;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

/**
 * @author dadoonet (David Pilato)
 */
public class FsRiver extends AbstractRiverComponent implements River {
    public static final class PROTOCOL {
        public static final String LOCAL = "local";
        public static final String SSH = "ssh";
        public static final int SSH_PORT = 22;
    }

    private final Client client;

    private final String indexName;

    private final String typeName;

    private final int bulkSize;
    private final int maxConcurrentBulk;
    private final TimeValue bulkFlushInterval;

    private volatile BulkProcessor bulkProcessor;

    private volatile Thread feedThread;

    private volatile boolean closed = false;

    private final FsRiverFeedDefinition fsDefinition;

    @SuppressWarnings({"unchecked"})
    @Inject
    public FsRiver(RiverName riverName, RiverSettings settings, Client client)
            throws MalformedURLException {
        super(riverName, settings);
        this.client = client;

        if (settings.settings().containsKey("fs")) {
            Map<String, Object> feed = (Map<String, Object>) settings
                    .settings().get("fs");

            String feedname = XContentMapValues.nodeStringValue(
                    feed.get("name"), null);
            if (feedname != null) {
                logger.warn("`fs.name` attribute is deprecated. Don't use it anymore.");
            }

            String url = XContentMapValues.nodeStringValue(feed.get("url"), null);
            if (url == null) {
                logger.warn("`url` is not set. Please define it. Falling back to default: /esdir.");
                url = "/esdir";
            }

            int updateRate = XContentMapValues.nodeIntegerValue(feed.get("update_rate"), 15 * 60 * 1000);

            String[] includes = FsRiverUtil.buildArrayFromSettings(settings.settings(), "fs.includes");
            String[] excludes = FsRiverUtil.buildArrayFromSettings(settings.settings(), "fs.excludes");

            // https://github.com/dadoonet/fsriver/issues/5 : Support JSon documents
            boolean jsonSupport = XContentMapValues.nodeBooleanValue(feed.get("json_support"), false);

            // https://github.com/dadoonet/fsriver/issues/7 : JSON support: use filename as ID
            boolean filenameAsId = XContentMapValues.nodeBooleanValue(feed.get("filename_as_id"), false);

            // https://github.com/dadoonet/fsriver/issues/18 : Add filesize to indexed document
            boolean addFilesize = XContentMapValues.nodeBooleanValue(feed.get("add_filesize"), true);

            // https://github.com/dadoonet/fsriver/issues/17 : Modify Indexed Characters limit
            double indexedChars = XContentMapValues.nodeDoubleValue(feed.get("indexed_chars"), 0.0);

            String username = XContentMapValues.nodeStringValue(feed.get("username"), null);
            String password = XContentMapValues.nodeStringValue(feed.get("password"), null);
            String server = XContentMapValues.nodeStringValue(feed.get("server"), null);
            int port = XContentMapValues.nodeIntegerValue(feed.get("port"), PROTOCOL.SSH_PORT);
            String protocol = XContentMapValues.nodeStringValue(feed.get("protocol"), PROTOCOL.LOCAL);

            // https://github.com/dadoonet/fsriver/issues/35 : Option to not delete documents when files are removed
            boolean removeDeleted = XContentMapValues.nodeBooleanValue(feed.get("remove_deleted"), true);
            boolean storeSource = XContentMapValues.nodeBooleanValue(feed.get("store_source"), false);

            fsDefinition = new FsRiverFeedDefinition(riverName.getName(), url,
                    updateRate, Arrays.asList(includes), Arrays.asList(excludes),
                    jsonSupport, filenameAsId, addFilesize, indexedChars,
                    username, password, server, port, protocol, removeDeleted, storeSource);
        } else {
            String url = "/esdir";
            logger.warn(
                    "You didn't define the fs url. Switching to defaults : [{}]",
                    url);
            int updateRate = 60 * 60 * 1000;
            fsDefinition = new FsRiverFeedDefinition(riverName.getName(), url,
                    updateRate, Arrays.asList("*.txt", "*.pdf"), Arrays.asList("*.exe"), false, false, true, 0.0,
                    null, null, null, PROTOCOL.SSH_PORT, null, true, false);
        }

        if (settings.settings().containsKey("index")) {
            Map<String, Object> indexSettings = (Map<String, Object>) settings
                    .settings().get("index");
            indexName = XContentMapValues.nodeStringValue(
                    indexSettings.get("index"), riverName.name());
            typeName = XContentMapValues.nodeStringValue(
                    indexSettings.get("type"), FsRiverUtil.INDEX_TYPE_DOC);
            bulkSize = XContentMapValues.nodeIntegerValue(
                    indexSettings.get("bulk_size"), 100);
            bulkFlushInterval = TimeValue.parseTimeValue(XContentMapValues.nodeStringValue(
                    indexSettings.get("flush_interval"), "5s"), TimeValue.timeValueSeconds(5));
            maxConcurrentBulk = XContentMapValues.nodeIntegerValue(indexSettings.get("max_concurrent_bulk"), 1);
        } else {
            indexName = riverName.name();
            typeName = FsRiverUtil.INDEX_TYPE_DOC;
            bulkSize = 100;
            maxConcurrentBulk = 1;
            bulkFlushInterval = TimeValue.timeValueSeconds(5);
        }


        // Checking protocol
        if (!PROTOCOL.LOCAL.equals(fsDefinition.getProtocol()) &&
                !PROTOCOL.SSH.equals(fsDefinition.getProtocol())) {
            // Non supported protocol
            logger.error(fsDefinition.getProtocol() + " is not supported yet. Please use " +
                    PROTOCOL.LOCAL + " or " + PROTOCOL.SSH + ". Disabling river");
            closed = true;
            return;
        }

        // Checking username/password
        if (PROTOCOL.SSH.equals(fsDefinition.getProtocol()) &&
                !Strings.hasLength(fsDefinition.getUsername())) {
            // Non supported protocol
            logger.error("When using SSH, you need to set a username and probably a password. Disabling river");
            closed = true;
        }
    }

    @Override
    public void start() {
        if (logger.isInfoEnabled())
            logger.info("Starting fs river scanning");

        if (closed) {
            logger.info("Fs river is closed. Exiting");
            return;
        }

        try {
            client.admin().indices().prepareCreate(indexName).execute()
                    .actionGet();
        } catch (Exception e) {
            if (ExceptionsHelper.unwrapCause(e) instanceof IndexAlreadyExistsException) {
                // that's fine
            } else if (ExceptionsHelper.unwrapCause(e) instanceof ClusterBlockException) {
                // ok, not recovered yet..., lets start indexing and hope we
                // recover by the first bulk
                // TODO: a smarter logic can be to register for cluster event
                // listener here, and only start sampling when the block is
                // removed...
            } else {
                logger.warn("failed to create index [{}], disabling river...",
                        e, indexName);
                return;
            }
        }

        try {
            // If needed, we create the new mapping for files
            if (!fsDefinition.isJsonSupport())
                pushMapping(indexName, typeName, FsRiverUtil.buildFsFileMapping(typeName, true, fsDefinition.isStoreSource()));
        } catch (Exception e) {
            logger.warn("failed to create mapping for [{}/{}], disabling river...",
                    e, indexName, typeName);
            return;
        }

        // Creating bulk processor
        this.bulkProcessor = BulkProcessor.builder(client, new BulkProcessor.Listener() {
            @Override
            public void beforeBulk(long executionId, BulkRequest request) {
                logger.debug("Going to execute new bulk composed of {} actions", request.numberOfActions());
            }

            @Override
            public void afterBulk(long executionId, BulkRequest request, BulkResponse response) {
                logger.debug("Executed bulk composed of {} actions", request.numberOfActions());
                if (response.hasFailures()) {
                    logger.warn("There was failures while executing bulk", response.buildFailureMessage());
                    if (logger.isDebugEnabled()) {
                        for (BulkItemResponse item : response.getItems()) {
                            if (item.isFailed()) {
                                logger.debug("Error for {}/{}/{} for {} operation: {}", item.getIndex(),
                                        item.getType(), item.getId(), item.getOpType(), item.getFailureMessage());
                            }
                        }
                    }
                }
            }

            @Override
            public void afterBulk(long executionId, BulkRequest request, Throwable failure) {
                logger.warn("Error executing bulk", failure);
            }
        })
                .setBulkActions(bulkSize)
                .setConcurrentRequests(maxConcurrentBulk)
                .setFlushInterval(bulkFlushInterval)
                .build();

        // We create as many Threads as there are feeds
        feedThread = EsExecutors.daemonThreadFactory(
                settings.globalSettings(), "fs_slurper")
                .newThread(
                        new FSParser(fsDefinition));
        feedThread.start();
    }

    @Override
    public void close() {
        if (logger.isInfoEnabled())
            logger.info("Closing fs river");
        closed = true;

        // We have to close the Thread
        if (feedThread != null) {
            feedThread.interrupt();
        }

        if (this.bulkProcessor != null) {
            this.bulkProcessor.close();
        }
    }

    /**
     * Check if a mapping already exists in an index
     *
     * @param index Index name
     * @param type  Mapping name
     * @return true if mapping exists
     */
    private boolean isMappingExist(String index, String type) {
        ClusterState cs = client.admin().cluster().prepareState().setFilterIndices(index).execute().actionGet().getState();
        IndexMetaData imd = cs.getMetaData().index(index);

        if (imd == null) return false;

        MappingMetaData mdd = imd.mapping(type);

        if (mdd != null) return true;
        return false;
    }

    private void pushMapping(String index, String type, XContentBuilder xcontent) throws Exception {
        if (logger.isTraceEnabled()) logger.trace("pushMapping(" + index + "," + type + ")");

        // If type does not exist, we create it
        boolean mappingExist = isMappingExist(index, type);
        if (!mappingExist) {
            logger.debug("Mapping [" + index + "]/[" + type + "] doesn't exist. Creating it.");

            // Read the mapping json file if exists and use it
            if (xcontent != null) {
                if (logger.isTraceEnabled())
                    logger.trace("Mapping for [" + index + "]/[" + type + "]=" + xcontent.string());
                // Create type and mapping
                PutMappingResponse response = client.admin().indices()
                        .preparePutMapping(index)
                        .setType(type)
                        .setSource(xcontent)
                        .execute().actionGet();
                if (!response.isAcknowledged()) {
                    throw new Exception("Could not define mapping for type [" + index + "]/[" + type + "].");
                } else {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Mapping definition for [" + index + "]/[" + type + "] succesfully created.");
                    }
                }
            } else {
                if (logger.isDebugEnabled())
                    logger.debug("No mapping definition for [" + index + "]/[" + type + "]. Ignoring.");
            }
        } else {
            if (logger.isDebugEnabled()) logger.debug("Mapping [" + index + "]/[" + type + "] already exists.");
        }
        if (logger.isTraceEnabled()) logger.trace("/pushMapping(" + index + "," + type + ")");
    }


    private class FSParser implements Runnable {
        private static final String field_filename = FsRiverUtil.Doc.FILE + "." + FsRiverUtil.Doc.File.FILENAME;
        private FsRiverFeedDefinition fsdef;

        private ScanStatistic stats;

        public FSParser(FsRiverFeedDefinition fsDefinition) {
            this.fsdef = fsDefinition;
            if (logger.isInfoEnabled())
                logger.info("creating fs river [{}] for [{}] every [{}] ms",
                        fsdef.getRivername(), fsdef.getUrl(), fsdef.getUpdateRate());
        }

        @Override
        public void run() {
            while (true) {
                if (closed) {
                    return;
                }

                try {
                    // Let's see if river is suspended
                    GetResponse getResponse = client.prepareGet("_river", fsdef.getRivername(), "_fsstatus").execute().actionGet();
                    boolean isStarted = true;
                    if (!getResponse.isExists()) {
                        XContentBuilder xb = jsonBuilder()
                                .startObject()
                                .startObject("fs")
                                .field("status", "STARTED")
                                .endObject()
                                .endObject();

                        client.prepareIndex("_river", fsdef.getRivername(), "_fsstatus").setSource(xb).execute().actionGet();
                    } else {
                        String status = (String) XContentMapValues.extractValue("fs.status", getResponse.getSourceAsMap());
                        if (status.equals("STOPPED")) {
                            isStarted = false;
                        }
                    }

                    if (isStarted) {
                        stats = new ScanStatistic(fsdef.getUrl());

                        File directory = new File(fsdef.getUrl());

                        if (!directory.exists())
                            throw new RuntimeException(fsdef.getUrl() + " doesn't exists.");

                        String rootPathId = SignTool.sign(directory
                                .getAbsolutePath());
                        stats.setRootPathId(rootPathId);

                        String lastupdateField = "_lastupdated";
                        Date scanDatenew = new Date();
                        Date scanDate = getLastDateFromRiver(lastupdateField);

                        // We only index the root directory once (first run)
                        // That means that we don't have a scanDate yet
                        if (scanDate == null) indexRootDirectory(directory);

                        addFilesRecursively(fsdef.getUrl(), scanDate);

                        updateFsRiver(lastupdateField, scanDatenew);
                    } else {
                        if (logger.isDebugEnabled())
                            logger.debug("FSRiver is disabled for {}", fsdef.getRivername());
                    }


                } catch (Exception e) {
                    logger.warn("Error while indexing content from {}", fsdef.getUrl());
                    if (logger.isDebugEnabled())
                        logger.debug("Exception for {} is {}", fsdef.getUrl(), e);
                }

                try {
                    if (logger.isDebugEnabled())
                        logger.debug("Fs river is going to sleep for {} ms",
                                fsdef.getUpdateRate());
                    Thread.sleep(fsdef.getUpdateRate());
                } catch (InterruptedException e1) {
                }
            }
        }

        @SuppressWarnings("unchecked")
        private Date getLastDateFromRiver(String lastupdateField) {
            Date lastDate = null;
            try {
                // Do something
                client.admin().indices().prepareRefresh("_river").execute()
                        .actionGet();
                GetResponse lastSeqGetResponse = client
                        .prepareGet("_river", riverName().name(),
                                lastupdateField).execute().actionGet();
                if (lastSeqGetResponse.isExists()) {
                    Map<String, Object> fsState = (Map<String, Object>) lastSeqGetResponse
                            .getSourceAsMap().get("fs");

                    if (fsState != null) {
                        Object lastupdate = fsState.get("lastdate");
                        if (lastupdate != null) {
                            String strLastDate = lastupdate.toString();
                            lastDate = ISODateTimeFormat
                                    .dateOptionalTimeParser()
                                    .parseDateTime(strLastDate).toDate();
                        }
                    }
                } else {
                    // First call
                    if (logger.isDebugEnabled())
                        logger.debug("{} doesn't exist", lastupdateField);
                }
            } catch (Exception e) {
                logger.warn("failed to get _lastupdate, throttling....", e);
            }
            return lastDate;
        }

        private void updateFsRiver(String lastupdateField, Date scanDate)
                throws Exception {
            // We store the lastupdate date and some stats
            XContentBuilder xb = jsonBuilder()
                    .startObject()
                    .startObject("fs")
                    .field("feedname", fsdef.getRivername())
                    .field("lastdate", scanDate)
                    .field("docadded", stats.getNbDocScan())
                    .field("docdeleted", stats.getNbDocDeleted())
                    .endObject()
                    .endObject();
            esIndex("_river", riverName.name(), lastupdateField, xb);
        }

        private FileAbstractor buildFileAbstractor() throws Exception {
            // What is the protocol used?
            if (PROTOCOL.LOCAL.equals(fsdef.getProtocol())) {
                // Local FS
                return new FileAbstractorFile(fsdef);
            } else if (PROTOCOL.SSH.equals(fsdef.getProtocol())) {
                // Remote SSH FS
                return new FileAbstractorSSH(fsdef);
            }

            // Non supported protocol
            throw new RuntimeException(fsdef.getProtocol() + " is not supported yet. Please use " +
                    PROTOCOL.LOCAL + " or " + PROTOCOL.SSH);
        }

        private void addFilesRecursively(String filepath, Date lastScanDate)
                throws Exception {

            if (logger.isDebugEnabled()) logger.debug("Indexing [{}] content", filepath);
            FileAbstractor path = buildFileAbstractor();

            final Collection<FileAbstractModel> children = path.getFiles(filepath);
            Collection<String> fsFiles = new ArrayList<String>();
            Collection<String> fsFolders = new ArrayList<String>();

            if (children != null) {
                for (FileAbstractModel child : children) {
                    String filename = child.name;

                    // Ignore temporary files
                    if (filename.contains("~")) {
                        continue;
                    }

                    if (child.file) {
                        logger.debug("  - file: {}", filename);

                        // https://github.com/dadoonet/fsriver/issues/1 : Filter documents
                        if (FsRiverUtil.isIndexable(filename, fsdef.getIncludes(), fsdef.getExcludes())) {
                            fsFiles.add(filename);
                            if ((lastScanDate == null || child.lastModifiedDate > lastScanDate
                                    .getTime())) {
                                indexFile(stats, child.name, filepath, path.getInputStream(child), child.lastModifiedDate);
                                stats.addFile();
                            }
                        }
                    } else if (child.directory) {
                        logger.debug("  - folder: {}", filename);
                        fsFolders.add(filename);
                        indexDirectory(stats, filename, child.fullpath.concat(File.separator));
                        addFilesRecursively(child.fullpath.concat(File.separator), lastScanDate);
                    } else {
                        logger.debug("  - other: {}", filename);
                        if (logger.isDebugEnabled())
                            logger.debug("Not a file nor a dir. Skipping {}", child.fullpath);
                    }
                }
            }

            // TODO Optimize
            // if (path.isDirectory() && path.lastModified() > lastScanDate
            // && lastScanDate != 0) {

            if (fsdef.isRemoveDeleted()) {
                Collection<String> esFiles = getFileDirectory(filepath);

                // for the delete files
                for (String esfile : esFiles) {
                    if (FsRiverUtil.isIndexable(esfile, fsdef.getIncludes(), fsdef.getExcludes()) && !fsFiles.contains(esfile)) {
                        File file = new File(FileAbstractor.computeFullPath(filepath, esfile));

                        esDelete(indexName, typeName,
                                SignTool.sign(file.getAbsolutePath()));
                        stats.removeFile();
                    }
                }

                Collection<String> esFolders = getFolderDirectory(filepath);

                // for the delete folder
                for (String esfolder : esFolders) {

                    if (!fsFolders.contains(esfolder)) {

                        removeEsDirectoryRecursively(filepath,
                                esfolder);
                    }
                }
            }
        }

        // TODO Optimize it. We can probably use a search for a big array of filenames instead of
        // Searching fo 50000 files (which is somehow limited).
        private Collection<String> getFileDirectory(String path)
                throws Exception {
            Collection<String> files = new ArrayList<String>();

            SearchResponse response = client
                    .prepareSearch(indexName)
                    .setSearchType(SearchType.QUERY_AND_FETCH)
                    .setTypes(typeName)
                    .setQuery(
                            QueryBuilders.termQuery(
                                    FsRiverUtil.Doc.Path.ENCODED,
                                    SignTool.sign(path)))
                    .setFrom(0)
                    .setSize(50000)
                    .addField(field_filename)
                    .execute().actionGet();

            if (response.getHits() != null
                    && response.getHits().getHits() != null) {
                for (SearchHit hit : response.getHits().getHits()) {
                    String name = null;
                    if (hit.getSource() != null && hit.getSource().get(FsRiverUtil.Doc.File.FILENAME) != null) {
                        name = hit.getSource().get(FsRiverUtil.Doc.File.FILENAME).toString();
                    } else if (hit.getFields() != null && hit.getFields().get(field_filename) != null) {
                        name = hit.getFields().get(field_filename).getValue().toString();
                    } else {
                        // Houston, we have a problem ! We can't get the old files from ES
                        logger.warn("Can't find in _source nor fields the existing filenames in path [{}]. " +
                                "Please enable _source or store field [{}]", path, field_filename);
                    }
                    files.add(name);
                }
            }

            return files;

        }

        private Collection<String> getFolderDirectory(String path)
                throws Exception {
            Collection<String> files = new ArrayList<String>();

            SearchResponse response = client
                    .prepareSearch(indexName)
                    .setSearchType(SearchType.QUERY_AND_FETCH)
                    .setTypes(FsRiverUtil.INDEX_TYPE_FOLDER)
                    .setQuery(
                            QueryBuilders.termQuery(
                                    FsRiverUtil.Doc.Path.ENCODED,
                                    SignTool.sign(path))).setFrom(0)
                    .setSize(50000).execute().actionGet();

            if (response.getHits() != null
                    && response.getHits().getHits() != null) {
                for (SearchHit hit : response.getHits().getHits()) {
                    String name = hit.getSource()
                            .get(FsRiverUtil.Doc.File.FILENAME).toString();
                    files.add(name);
                }
            }

            return files;

        }

        /**
         * Index a file
         */
        private void indexFile(ScanStatistic stats, String filename, String filepath, InputStream fileReader, long lastmodified) throws Exception {
            if (logger.isDebugEnabled()) logger.debug("fetching content from [{}],[{}]", filepath, filename);

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

            // https://github.com/dadoonet/fsriver/issues/5 : Support JSon files
            if (fsDefinition.isJsonSupport()) {
                String id;
                if (fsDefinition.isFilenameAsId()) {
                    id = filename;
                    int pos = id.lastIndexOf(".");
                    if (pos > 0) {
                        id = id.substring(0, pos);
                    }
                } else {
                    id = SignTool.sign(filepath + File.separator + filename);
                }
                esIndex(indexName,
                        typeName,
                        id,
                        data);
            } else {
                // Extracting content with Tika
                // See #38: https://github.com/dadoonet/fsriver/issues/38
                int indexedChars = 100000;
                if (fsDefinition.getIndexedChars() > 0) {
                    indexedChars = (int) Math.round(data.length * fsDefinition.getIndexedChars());
                }
                Metadata metadata = new Metadata();

                String parsedContent;
                try {
                    // Set the maximum length of strings returned by the parseToString method, -1 sets no limit
                    parsedContent = tika().parseToString(new BytesStreamInput(data, false), metadata, indexedChars);
                } catch (Throwable e) {
                    logger.debug("Failed to extract [" + indexedChars + "] characters of text for [" + filename + "]", e);
                    parsedContent = "";
                }

                XContentBuilder source = jsonBuilder().startObject();

                // File
                source
                        .startObject(FsRiverUtil.Doc.FILE)
                        .field(FsRiverUtil.Doc.File.FILENAME, filename)
                        .field(FsRiverUtil.Doc.File.LAST_MODIFIED, lastmodified)
                        .field(FsRiverUtil.Doc.File.INDEXING_DATE, new Date())
                        .field(FsRiverUtil.Doc.File.CONTENT_TYPE, metadata.get(Metadata.CONTENT_TYPE))
                        .field(FsRiverUtil.Doc.File.URL, "file://" + filepath + File.separator + filename);

                // We only add `indexed_chars` if we have other value than default
                if (fsDefinition.getIndexedChars() > 0) {
                    source.field(FsRiverUtil.Doc.File.INDEXED_CHARS, indexedChars);
                }

                if (fsDefinition.isAddFilesize()) {
                    if (metadata.get(Metadata.CONTENT_LENGTH) != null) {
                        // We try to get CONTENT_LENGTH from Tika first
                        source.field(FsRiverUtil.Doc.File.FILESIZE, metadata.get(Metadata.CONTENT_LENGTH));
                    } else {
                        // Otherwise, we use our byte[] length
                        source.field(FsRiverUtil.Doc.File.FILESIZE, data.length);
                    }
                }
                source.endObject(); // File

                // Path
                source
                        .startObject(FsRiverUtil.Doc.PATH)
                        .field(FsRiverUtil.Doc.Path.ENCODED, SignTool.sign(filepath))
                        .field(FsRiverUtil.Doc.Path.ROOT, stats.getRootPathId())
                        .field(FsRiverUtil.Doc.Path.VIRTUAL,
                                FsRiverUtil.computeVirtualPathName(stats, filepath))
                        .field(FsRiverUtil.Doc.Path.REAL, filepath + File.separator + filename)
                        .endObject(); // Path

                // Meta
                source
                        .startObject(FsRiverUtil.Doc.META)
                        .field(FsRiverUtil.Doc.Meta.AUTHOR, metadata.get(Metadata.AUTHOR))
                        .field(FsRiverUtil.Doc.Meta.TITLE, metadata.get(Metadata.TITLE))
                        .field(FsRiverUtil.Doc.Meta.DATE, metadata.get(Metadata.DATE))
                        .array(FsRiverUtil.Doc.Meta.KEYWORDS, Strings.commaDelimitedListToStringArray(metadata.get(Metadata.KEYWORDS)))
                        .endObject(); // Meta

                // Doc content
                source.field(FsRiverUtil.Doc.CONTENT, parsedContent);

                // Doc as binary attachment
                if (fsDefinition.isStoreSource()) {
                    source.field(FsRiverUtil.Doc.ATTACHMENT, Base64.encodeBytes(data));
                }

                // End of our document
                source.endObject();

                // We index
                esIndex(indexName,
                        typeName,
                        SignTool.sign(filepath + File.separator + filename),
                        source);
            }

        }

        private void indexDirectory(String id, String name, String root, String virtual, String encoded)
                throws Exception {
            esIndex(indexName,
                    FsRiverUtil.INDEX_TYPE_FOLDER,
                    id,
                    jsonBuilder().startObject()
                            .field(FsRiverUtil.Dir.NAME, name)
                            .field(FsRiverUtil.Dir.ROOT, root)
                            .field(FsRiverUtil.Dir.VIRTUAL, virtual)
                            .field(FsRiverUtil.Dir.ENCODED, encoded)
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
                    FsRiverUtil.computeVirtualPathName(stats,
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
                        indexName,
                        typeName,
                        SignTool.sign(fullPath.concat(File.separator).concat(
                                esfile)));
            }

            Collection<String> listFolder = getFolderDirectory(fullPath);

            for (String esfolder : listFolder) {
                removeEsDirectoryRecursively(fullPath, esfolder);
            }

            esDelete(indexName, FsRiverUtil.INDEX_TYPE_FOLDER,
                    SignTool.sign(fullPath));

        }

        /**
         * Add to bulk an IndexRequest
         */
        private void esIndex(String index, String type, String id,
                             XContentBuilder xb) throws Exception {
            if (logger.isDebugEnabled()) logger.debug("Indexing in ES " + index + ", " + type + ", " + id);
            if (logger.isTraceEnabled()) logger.trace("JSon indexed : {}", xb.string());

            bulkProcessor.add(new IndexRequest(index, type, id).source(xb));
        }

        /**
         * Add to bulk an IndexRequest in JSon format
         */
        private void esIndex(String index, String type, String id,
                             byte[] json) throws Exception {
            if (logger.isDebugEnabled()) logger.debug("Indexing in ES " + index + ", " + type + ", " + id);
            if (logger.isTraceEnabled()) logger.trace("JSon indexed : {}", json);

            bulkProcessor.add(new IndexRequest(index, type, id).source(json));
        }

        /**
         * Add to bulk a DeleteRequest
         */
        private void esDelete(String index, String type, String id) throws Exception {
            if (logger.isDebugEnabled()) logger.debug("Deleting from ES " + index + ", " + type + ", " + id);
            bulkProcessor.add(new DeleteRequest(index, type, id));
        }
    }
}
