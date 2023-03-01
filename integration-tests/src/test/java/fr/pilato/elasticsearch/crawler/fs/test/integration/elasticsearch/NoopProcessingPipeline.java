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
package fr.pilato.elasticsearch.crawler.fs.test.integration.elasticsearch;

import fr.pilato.elasticsearch.crawler.fs.EsIndexProcessor;
import fr.pilato.elasticsearch.crawler.fs.FsCrawlerContext;
import fr.pilato.elasticsearch.crawler.fs.ProcessingPipeline;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil;
import java.io.IOException;

/**
 * The processing pipeline does nothing.
 *
 */
 public class NoopProcessingPipeline implements ProcessingPipeline {

    protected Config config;
    protected EsIndexProcessor es;

    @Override
    public void processFile(FsCrawlerContext ctx) {
        indexToEs(ctx);
    }

        /**
     * Indexes document using {@link EsIndexProcessor}
     */
    protected void indexToEs(FsCrawlerContext ctx) {
        if (FsCrawlerUtil.isIndexable(ctx.getDoc().getContent(), config.getFsSettings().getFs().getFilters())) {
            es.process(ctx);
        }
    }

    @Override
    public void init(Config config) throws IOException {
        this.config = config;
        es = new EsIndexProcessor(config.getFsSettings(), config.getDocumentService());
    }

}