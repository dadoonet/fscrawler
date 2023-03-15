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

import fr.pilato.elasticsearch.crawler.fs.beans.Doc;
import fr.pilato.elasticsearch.crawler.fs.framework.FSCrawlerLogger;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil;
import fr.pilato.elasticsearch.crawler.fs.framework.SignTool;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerDocumentService;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import io.opentelemetry.context.Scope;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.security.NoSuchAlgorithmException;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.*;
import com.fasterxml.jackson.databind.JsonNode;
import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.mapper;
/**
 * Pulls {@link fr.pilato.elasticsearch.crawler.fs.beans.Doc} from context,
 * merges it with 'extraDoc' if it exists and then indexes it to Elasticsearch
 */

public class UpdateIdProcessor extends ProcessorAbstract {
    private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

    @Override
    public void process(FsCrawlerContext ctx) throws ProcessingException {
        var span = tracer.spanBuilder("update-id").startSpan();

        try(Scope scope = span.makeCurrent()) {
            var url = ctx.getDoc().getFile().getUrl();
            ctx.setId(SignTool.sign(url));
        } catch (NoSuchAlgorithmException e) {
            throw new ProcessingException("Couldn't get algorithm for _id hash");
        } finally {
            span.end();
        }

    }

    private String generateIdFromFilename(String filename, String filepath) throws NoSuchAlgorithmException {
        String filepathForId = filepath.replace("\\", "/");
        String filenameForId = filename.replace("\\", "").replace("/", "");
        String idSource = filepathForId.endsWith("/") ? filepathForId.concat(filenameForId) : filepathForId.concat("/").concat(filenameForId);
        return SignTool.sign(idSource);
    }
}
