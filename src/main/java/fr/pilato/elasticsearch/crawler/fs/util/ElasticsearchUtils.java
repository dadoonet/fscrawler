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

package fr.pilato.elasticsearch.crawler.fs.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingResponse;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkProcessor;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentBuilder;

/**
 * Some elasticsearch util methods
 * TODO: Replace elasticsearch with an HTTP Client ?
 */
public class ElasticsearchUtils {

    private static final Logger logger = LogManager.getLogger(ElasticsearchUtils.class);

    /**
     * Check if a mapping already exists in an index
     *
     * @param client elasticsearch client
     * @param index Index name
     * @param type  Mapping name
     * @return true if mapping exists
     */
    public static boolean isMappingExist(Client client, String index, String type) {
        ClusterState cs = client.admin().cluster().prepareState().setIndices(index).get().getState();
        IndexMetaData imd = cs.getMetaData().index(index);
        return imd != null && imd.mapping(type) != null;
    }

    /**
     * Create a mapping if it does not exist already
     * @param client elasticsearch client
     * @param index index name
     * @param type type name
     * @param xcontent Elasticsearch mapping
     * @throws Exception in case of error
     */
    public static void pushMapping(Client client, String index, String type, XContentBuilder xcontent) throws Exception {
        logger.trace("pushMapping(" + index + "," + type + ")");

        // If type does not exist, we create it
        boolean mappingExist = isMappingExist(client, index, type);
        if (!mappingExist) {
            logger.debug("Mapping [" + index + "]/[" + type + "] doesn't exist. Creating it.");

            // Read the mapping json file if exists and use it
            if (xcontent != null) {
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
                    logger.debug("Mapping definition for [" + index + "]/[" + type + "] succesfully created.");
                }
            } else {
                logger.debug("No mapping definition for [" + index + "]/[" + type + "]. Ignoring.");
            }
        } else {
            logger.debug("Mapping [" + index + "]/[" + type + "] already exists.");
        }
        logger.trace("/pushMapping(" + index + "," + type + ")");
    }

    /**
     * Build an elasticsearch bulk processor
     * @param client elasticsearch client
     * @param bulkSize bulk size
     * @param flushIntervalInMs flush interval in milliseconds
     * @return a bulk processor
     */
    public static BulkProcessor buildBulkProcessor(Client client, int bulkSize, long flushIntervalInMs) {
        return BulkProcessor.builder(client, new BulkProcessor.Listener() {
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
                .setFlushInterval(TimeValue.timeValueMillis(flushIntervalInMs))
                .build();
    }
}
