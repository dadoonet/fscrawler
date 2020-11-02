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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import fr.pilato.elasticsearch.crawler.fs.beans.DocParser;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClient;
import fr.pilato.elasticsearch.crawler.fs.framework.SignTool;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Type;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

/**
 * Pulls {@link fr.pilato.elasticsearch.crawler.fs.beans.Doc} from context,
 * merges it with 'extraDoc' if it exists and then indexes it to Elasticsearch
 */
public class EsIndexProcessor implements Processor {
    private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());
    private final FsSettings fsSettings;
    private final ElasticsearchClient esClient;

    public EsIndexProcessor(FsSettings fsSettings, ElasticsearchClient esClient) {
        this.fsSettings = fsSettings;
        this.esClient = esClient;
    }

    @Override
    public void process(FsCrawlerContext ctx) throws ProcessingException {
        try {
            long startTime = System.currentTimeMillis();
            String index = fsSettings.getElasticsearch().getIndex();
            String id = generateId(ctx);
            String pipeline = fsSettings.getElasticsearch().getPipeline();
            String json = DocParser.toJson(ctx.getDoc());
            if (!ctx.getExtraDoc().isEmpty()) {
                json = mergeExtraDoc(json, ctx.getExtraDoc());
            }
            esClient.index(index, id, json, pipeline);
            logger.debug("Indexed {}/{}?pipeline={} in {}ms", index, id, pipeline,
                    System.currentTimeMillis() - startTime);
            logger.trace("JSon indexed : {}", json);
        } catch (JsonProcessingException e) {
            throw new ProcessingException(e);
        }
    }

    protected static String mergeExtraDoc(String json, Map<String, Object> extraDoc) {
        Type mapType = new TypeToken<Map<String, Object>>() {}.getType();
        Map<String,Object> doc = new Gson().fromJson(json, mapType);
        doc.putAll(extraDoc);
        return new Gson().toJson(doc);
    }

    protected String generateId(FsCrawlerContext ctx) {
        try {
            return fsSettings.getFs().isFilenameAsId() ?
                    ctx.getFile().getName() :
                    SignTool.sign((new File(ctx.getFile().getName(), ctx.getFilepath())).toString());
        } catch (NoSuchAlgorithmException e) {
            throw new ProcessingException(e);
        }
    }
}
