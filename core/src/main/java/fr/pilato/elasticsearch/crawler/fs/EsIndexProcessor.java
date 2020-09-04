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
import fr.pilato.elasticsearch.crawler.fs.beans.DocParser;
import fr.pilato.elasticsearch.crawler.fs.framework.SignTool;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.security.NoSuchAlgorithmException;

/**
 * Pulls {@link fr.pilato.elasticsearch.crawler.fs.beans.Doc} from context and indexes it to Elasticsearch
 */
public class EsIndexProcessor implements Processor {
    private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

    @Override
    public void process(FsCrawlerContext ctx) throws ProcessingException {
        try {
            long startTime = System.currentTimeMillis();
            String index = ctx.getFsSettings().getElasticsearch().getIndex();
            String id = generateId(ctx);
            String pipeline = ctx.getFsSettings().getElasticsearch().getPipeline();
            String json = DocParser.toJson(ctx.getDoc());
            ctx.getEsClient().index(index, id, json, pipeline);
            logger.debug("Indexed {}/{}?pipeline={} in {}ms", index, id, pipeline,
                    System.currentTimeMillis() - startTime);
            logger.trace("JSon indexed : {}", json);
        } catch (JsonProcessingException e) {
            throw new ProcessingException(e);
        }
    }

    String generateId(FsCrawlerContext ctx) {
        try {
            return ctx.getFsSettings().getFs().isFilenameAsId() ?
                    ctx.getFile().getName() :
                    SignTool.sign((new File(ctx.getFile().getName(), ctx.getFilepath())).toString());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
