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

import fr.pilato.elasticsearch.crawler.fs.framework.FSCrawlerLogger;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerDocumentService;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import io.opentelemetry.context.Scope;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.invoke.MethodHandles;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.*;
import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.serialize;
import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.transform;

/**
 * Pulls {@link fr.pilato.elasticsearch.crawler.fs.beans.Doc} from context,
 * merges it with 'extraDoc' if it exists and then indexes it to Elasticsearch
 */
public class EsIndexProcessor extends ProcessorAbstract {
    private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());
    private final FsSettings fsSettings;
    private final FsCrawlerDocumentService documentService;

    public EsIndexProcessor(FsSettings fsSettings, FsCrawlerDocumentService esClient) {
        this.fsSettings = fsSettings;
        this.documentService = esClient;
    }

    @Override
    public void process(FsCrawlerContext ctx) throws ProcessingException {
        var span = tracer.spanBuilder("es-index").startSpan();
        Scope scope = span.makeCurrent();
        
        var doc = ctx.getDoc();
        var filename = ctx.getFile().getName();
        var fullFilename = ctx.getFullFilename();
        var id = ctx.getId();
        var stats = ctx.getScanStatistic();

        var postTransform = fsSettings.getFs().getPipeline().getPostTransform();

        // We index the data structure
        if(isIndexable(doc.getContent(), fsSettings.getFs().getFilters())) {
                FSCrawlerLogger.documentDebug(id,
                        computeVirtualPathName(stats.getRootPath(), fullFilename),
                        "Indexing content");

                var serializedData = serialize(doc);

                if (postTransform != null) {
                    serializedData = transform(serializedData, postTransform);
                }

                documentService.indexRawJson(
                        fsSettings.getElasticsearch().getIndex(),
                        id,
                        serializedData,
                        fsSettings.getElasticsearch().getPipeline());
        } else {
            logger.debug("We ignore file [{}] because it does not match all the patterns {}", filename,
                    fsSettings.getFs().getFilters());
        }
        
        span.end();

    }
}