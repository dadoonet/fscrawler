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

package org.elasticsearch.river.fs;

import org.elasticsearch.ExceptionsHelper;
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingResponse;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.block.ClusterBlockException;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.cluster.metadata.MappingMetaData;
import org.elasticsearch.common.Base64;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.joda.time.format.ISODateTimeFormat;
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
import java.io.FileInputStream;
import java.net.MalformedURLException;
import java.util.*;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

/**
 * @author dadoonet (David Pilato)
 */
public class FsRiver extends AbstractRiverComponent implements River {

	private final Client client;

	private final String indexName;

	private final String typeName;

	private final long bulkSize;

	private volatile Thread feedThread;

	private volatile boolean closed = false;

	private final FsRiverFeedDefinition fsDefinition;

	@SuppressWarnings({ "unchecked" })
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
			String url = XContentMapValues.nodeStringValue(feed.get("url"),
					null);

			int updateRate = XContentMapValues.nodeIntegerValue(
					feed.get("update_rate"), 15 * 60 * 1000);
			
			String[] includes = FsRiverUtil.buildArrayFromSettings(settings.settings(), "fs.includes");
			String[] excludes = FsRiverUtil.buildArrayFromSettings(settings.settings(), "fs.excludes");

            // https://github.com/dadoonet/fsriver/issues/5 : Support JSon documents
			boolean jsonSupport = XContentMapValues.nodeBooleanValue(feed.get("json_support"), false);

            // https://github.com/dadoonet/fsriver/issues/7 : JSON support: use filename as ID
            boolean filenameAsId = XContentMapValues.nodeBooleanValue(feed.get("filename_as_id"), false);

            // https://github.com/dadoonet/fsriver/issues/18 : Add filesize to indexed document
            boolean addFilesize = XContentMapValues.nodeBooleanValue(feed.get("add_filesize"), true);

            fsDefinition = new FsRiverFeedDefinition(feedname, url,
                    updateRate, Arrays.asList(includes), Arrays.asList(excludes),
                    jsonSupport, filenameAsId, addFilesize);
		} else {
			String url = "/esdir";
			logger.warn(
					"You didn't define the fs url. Switching to defaults : [{}]",
					url);
			int updateRate = 60 * 60 * 1000;
			fsDefinition = new FsRiverFeedDefinition("defaultlocaldir", url,
					updateRate, Arrays.asList("*.txt","*.pdf"), Arrays.asList("*.exe"), false, false, true);
		}

		if (settings.settings().containsKey("index")) {
			Map<String, Object> indexSettings = (Map<String, Object>) settings
					.settings().get("index");
			indexName = XContentMapValues.nodeStringValue(
					indexSettings.get("index"), riverName.name());
			typeName = XContentMapValues.nodeStringValue(
					indexSettings.get("type"), FsRiverUtil.INDEX_TYPE_DOC);
			bulkSize = XContentMapValues.nodeLongValue(
					indexSettings.get("bulk_size"), 100);
		} else {
			indexName = riverName.name();
			typeName = FsRiverUtil.INDEX_TYPE_DOC;
			bulkSize = 100;
		}
	}

	@Override
	public void start() {
		if (logger.isInfoEnabled())
			logger.info("Starting fs river scanning");
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
                pushMapping(indexName, typeName, FsRiverUtil.buildFsFileMapping(typeName));
		} catch (Exception e) {
			logger.warn("failed to create mapping for [{}/{}], disabling river...",
					e, indexName, typeName);
			return;
		}

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
	}

    /**
	 * Check if a mapping already exists in an index
	 * @param index Index name
	 * @param type Mapping name
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
		if (logger.isTraceEnabled()) logger.trace("pushMapping("+index+","+type+")");
		
		// If type does not exist, we create it
		boolean mappingExist = isMappingExist(index, type);
		if (!mappingExist) {
			logger.debug("Mapping ["+index+"]/["+type+"] doesn't exist. Creating it.");

			// Read the mapping json file if exists and use it
			if (xcontent != null) {
				if (logger.isTraceEnabled()) logger.trace("Mapping for ["+index+"]/["+type+"]="+xcontent.string());
				// Create type and mapping
				PutMappingResponse response = client.admin().indices()
					.preparePutMapping(index)
					.setType(type)
					.setSource(xcontent)
					.execute().actionGet();			
				if (!response.isAcknowledged()) {
					throw new Exception("Could not define mapping for type ["+index+"]/["+type+"].");
				} else {
					if (logger.isDebugEnabled()) {
						if (mappingExist) {
							logger.debug("Mapping definition for ["+index+"]/["+type+"] succesfully merged.");
						} else {
							logger.debug("Mapping definition for ["+index+"]/["+type+"] succesfully created.");
						}
					}
				}
			} else {
				if (logger.isDebugEnabled()) logger.debug("No mapping definition for ["+index+"]/["+type+"]. Ignoring.");
			}
		} else {
			if (logger.isDebugEnabled()) logger.debug("Mapping ["+index+"]/["+type+"] already exists and mergeMapping is not set.");
		}
		if (logger.isTraceEnabled()) logger.trace("/pushMapping("+index+","+type+")");
	}

	
	
	
	private class FSParser implements Runnable {
		private FsRiverFeedDefinition fsdef;
		
		private BulkRequestBuilder bulk;
		private ScanStatistic stats;

		public FSParser(FsRiverFeedDefinition fsDefinition) {
			this.fsdef = fsDefinition;
			if (logger.isInfoEnabled())
				logger.info("creating fs river [{}] for [{}] every [{}] ms",
						fsdef.getFeedname(), fsdef.getUrl(), fsdef.getUpdateRate());
		}

		@Override
		public void run() {
			while (true) {
				if (closed) {
					return;
				}

				try {
					stats = new ScanStatistic(fsdef.getUrl());

					File directory = new File(fsdef.getUrl());

					if (!directory.exists())
						throw new RuntimeException(fsdef.getUrl() + " doesn't exists.");

					String rootPathId = SignTool.sign(directory
							.getAbsolutePath());
					stats.setRootPathId(rootPathId);

					bulk = client.prepareBulk();

					String lastupdateField = "_lastupdated";
					Date scanDatenew = new Date();
					Date scanDate = getLastDateFromRiver(lastupdateField);

					// We only index the root directory once (first run)
					// That means that we don't have a scanDate yet
					if (scanDate == null) indexRootDirectory(directory);

					addFilesRecursively(directory, scanDate);

					updateFsRiver(lastupdateField, scanDatenew);

					// If some bulkActions remains, we should commit them
					commitBulk();

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
						.field("feedname", fsdef.getFeedname())
						.field("lastdate", scanDate)
						.field("docadded", stats.getNbDocScan())
						.field("docdeleted", stats.getNbDocDeleted())
					.endObject()
				.endObject();
			esIndex("_river", riverName.name(), lastupdateField, xb);
		}

		/**
		 * Commit to ES if something is in queue
		 * 
		 * @throws Exception
		 */
		private void commitBulk() throws Exception {
			if (bulk != null && bulk.numberOfActions() > 0) {
				if (logger.isDebugEnabled()) logger.debug("ES Bulk Commit is needed");
				BulkResponse response = bulk.execute().actionGet();
				if (response.hasFailures()) {
					logger.warn("Failed to execute "
							+ response.buildFailureMessage());
				}
			}
		}

		/**
		 * Commit to ES if we have too much in bulk
		 * 
		 * @throws Exception
		 */
		private void commitBulkIfNeeded() throws Exception {
			if (bulk != null && bulk.numberOfActions() > 0 && bulk.numberOfActions() >= bulkSize) {
				if (logger.isDebugEnabled()) logger.debug("ES Bulk Commit is needed");
				
				BulkResponse response = bulk.execute().actionGet();
				if (response.hasFailures()) {
					logger.warn("Failed to execute "
							+ response.buildFailureMessage());
				}
				
				// Reinit a new bulk
				bulk = client.prepareBulk();
			}
		}

		private void addFilesRecursively(File path, Date lastScanDate)
				throws Exception {

			final File[] children = path.listFiles();
			Collection<String> fsFiles = new ArrayList<String>();
			Collection<String> fsFolders = new ArrayList<String>();

			if (children != null) {

				for (File child : children) {

					if (child.isFile()) {
						String filename = child.getName();
						
						// https://github.com/dadoonet/fsriver/issues/1 : Filter documents
						if (FsRiverUtil.isIndexable(filename, fsdef.getIncludes(), fsdef.getExcludes())) {
							fsFiles.add(filename);
							if ((lastScanDate == null || child.lastModified() > lastScanDate
									.getTime())) {
								indexFile(stats, child);
								stats.addFile();
							}
						}
					} else if (child.isDirectory()) {
						fsFolders.add(child.getName());
						indexDirectory(stats, child);
						addFilesRecursively(child, lastScanDate);
					} else {
						if (logger.isDebugEnabled()) logger.debug("Not a file nor a dir. Skipping {}", child.getAbsolutePath());
					}
				}
			}

			// TODO Optimize
			// if (path.isDirectory() && path.lastModified() > lastScanDate
			// && lastScanDate != 0) {

			if (path.isDirectory()) {
				Collection<String> esFiles = getFileDirectory(path
						.getAbsolutePath());

				// for the delete files
				for (String esfile : esFiles) {
					if (FsRiverUtil.isIndexable(esfile, fsdef.getIncludes(), fsdef.getExcludes()) && !fsFiles.contains(esfile)) {
						File file = new File(path.getAbsolutePath()
								.concat(File.separator).concat(esfile));

						esDelete(indexName, typeName,
								SignTool.sign(file.getAbsolutePath()));
						stats.removeFile();
					}
				}

				Collection<String> esFolders = getFolderDirectory(path
						.getAbsolutePath());

				// for the delete folder
				for (String esfolder : esFolders) {

					if (!fsFolders.contains(esfolder)) {

						removeEsDirectoryRecursively(path.getAbsolutePath(),
								esfolder);
					}
				}

				// for the older files
				for (String fsFile : fsFiles) {

					File file = new File(path.getAbsolutePath()
							.concat(File.separator).concat(fsFile));
					
					if (FsRiverUtil.isIndexable(fsFile, fsdef.getIncludes(), fsdef.getExcludes())) {
						if (!esFiles.contains(fsFile)
								&& (lastScanDate == null || file.lastModified() < lastScanDate.getTime())) {
							indexFile(stats, file);
							stats.addFile();
						}
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
									FsRiverUtil.DOC_FIELD_PATH_ENCODED,
									SignTool.sign(path)))
                    .setFrom(0)
					.setSize(50000)
                    .addField(FsRiverUtil.DOC_FIELD_NAME)
                    .execute().actionGet();

			if (response.getHits() != null
					&& response.getHits().getHits() != null) {
				for (SearchHit hit : response.getHits().getHits()) {
                    String name = null;
                    if (hit.getSource() != null && hit.getSource().get(FsRiverUtil.DOC_FIELD_NAME) != null) {
                        name = hit.getSource().get(FsRiverUtil.DOC_FIELD_NAME).toString();
                    } else if (hit.getFields() != null && hit.getFields().get(FsRiverUtil.DOC_FIELD_NAME) != null) {
                        name = hit.getFields().get(FsRiverUtil.DOC_FIELD_NAME).getValue().toString();
                    } else {
                        // Houston, we have a problem ! We can't get the old files from ES
                        logger.warn("Can't find in _source nor fields the existing filenames in path [{}]. " +
                                "Please enable _source or store field [{}]", path, FsRiverUtil.DOC_FIELD_NAME);
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
									FsRiverUtil.DIR_FIELD_PATH_ENCODED,
									SignTool.sign(path))).setFrom(0)
					.setSize(50000).execute().actionGet();

			if (response.getHits() != null
					&& response.getHits().getHits() != null) {
				for (SearchHit hit : response.getHits().getHits()) {
					String name = hit.getSource()
							.get(FsRiverUtil.DIR_FIELD_NAME).toString();
					files.add(name);
				}
			}

			return files;

		}

		/**
		 * Index a file
		 * 
		 * @param stats
		 * @param file
		 * @throws Exception
		 */
		private void indexFile(ScanStatistic stats, File file) throws Exception {

			FileInputStream fileReader = new FileInputStream(file);

			// write it to a byte[] using a buffer since we don't know the exact
			// image size
			byte[] buffer = new byte[1024];
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			int i = 0;
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
                    id = file.getName();
                    int pos = id.lastIndexOf(".");
                    if (pos > 0) {
                        id = id.substring(0, pos);
                    }
                } else {
                    id = SignTool.sign(file.getAbsolutePath());
                }
                esIndex(indexName,
                        typeName,
                        id,
                        data);
            } else {
                XContentBuilder source = jsonBuilder().startObject();

                // We add metadata
                source
                    .field(FsRiverUtil.DOC_FIELD_NAME, file.getName())
                    .field(FsRiverUtil.DOC_FIELD_DATE, file.lastModified())
                    .field(FsRiverUtil.DOC_FIELD_PATH_ENCODED, SignTool.sign(file.getParent()))
                    .field(FsRiverUtil.DOC_FIELD_ROOT_PATH, stats.getRootPathId())
                    .field(FsRiverUtil.DOC_FIELD_VIRTUAL_PATH,
                            FsRiverUtil.computeVirtualPathName(stats,file.getParent()));
                if (fsDefinition.isAddFilesize()) {
                    source.field(FsRiverUtil.DOC_FIELD_FILESIZE, file.length());
                }

                // We add the content itself
                source.startObject("file").field("_name", file.getName())
                    .field("content", Base64.encodeBytes(data))
                    .endObject().endObject();

                // We index
                esIndex(indexName,
                        typeName,
                        SignTool.sign(file.getAbsolutePath()),
                        source);
            }

 		}

		/**
		 * Index a directory
		 * 
		 * @param stats
		 * @param file
		 * @throws Exception
		 */
		private void indexDirectory(ScanStatistic stats, File file)
				throws Exception {
			esIndex(indexName,
					FsRiverUtil.INDEX_TYPE_FOLDER,
					SignTool.sign(file.getAbsolutePath()),
					jsonBuilder()
							.startObject()
							.field(FsRiverUtil.DIR_FIELD_NAME, file.getName())
							.field(FsRiverUtil.DIR_FIELD_ROOT_PATH,
									stats.getRootPathId())
							.field(FsRiverUtil.DIR_FIELD_VIRTUAL_PATH,
                                    FsRiverUtil.computeVirtualPathName(stats,
											file.getParent()))
							.field(FsRiverUtil.DIR_FIELD_PATH_ENCODED,
									SignTool.sign(file.getParent()))
							.endObject());
		}

		/**
		 * Add the root directory as a folder
		 *
		 * @param file
		 * @throws Exception
		 */
		private void indexRootDirectory(File file) throws Exception {
			esIndex(indexName,
					FsRiverUtil.INDEX_TYPE_FOLDER,
					SignTool.sign(file.getAbsolutePath()),
					jsonBuilder()
							.startObject()
							.field(FsRiverUtil.DIR_FIELD_NAME, file.getName())
							.field(FsRiverUtil.DIR_FIELD_ROOT_PATH,
									stats.getRootPathId())
							.field(FsRiverUtil.DIR_FIELD_VIRTUAL_PATH,
									(String) null)
							.field(FsRiverUtil.DIR_FIELD_PATH_ENCODED,
									SignTool.sign(file.getParent()))
							.endObject());
		}

		/**
		 * Remove a full directory and sub dirs recursively
		 * 
		 * @param path
		 * @param name
		 * @throws Exception
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
		 * 
		 * @param index
		 * @param type
		 * @param id
		 * @param xb
		 * @throws Exception 
		 */
		private void esIndex(String index, String type, String id,
				XContentBuilder xb) throws Exception {
			if (logger.isDebugEnabled()) logger.debug("Indexing in ES " + index + ", " + type + ", " + id);
			if (logger.isTraceEnabled()) logger.trace("JSon indexed : {}", xb.string());
			
			bulk.add(client.prepareIndex(index, type, id).setSource(xb));
			commitBulkIfNeeded();
		}

        /**
         * Add to bulk an IndexRequest in JSon format
         *
         * @param index
         * @param type
         * @param id
         * @param json
         * @throws Exception
         */
        private void esIndex(String index, String type, String id,
                             byte[] json) throws Exception {
            if (logger.isDebugEnabled()) logger.debug("Indexing in ES " + index + ", " + type + ", " + id);
            if (logger.isTraceEnabled()) logger.trace("JSon indexed : {}", json);

            bulk.add(client.prepareIndex(index, type, id).setSource(json));
            commitBulkIfNeeded();
        }

        /**
		 * Add to bulk a DeleteRequest
		 * 
		 * @param index
		 * @param type
		 * @param id
		 * @throws Exception 
		 */
		private void esDelete(String index, String type, String id) throws Exception {
			if (logger.isDebugEnabled()) logger.debug("Deleting from ES " + index + ", " + type + ", " + id);
			bulk.add(client.prepareDelete(index, type, id));
			commitBulkIfNeeded();
		}
	}
	
	
	
}
