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

import fr.pilato.elasticsearch.crawler.fs.beans.DocParser;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.tika.TikaDocParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.security.MessageDigest;

/**
 * Tikaprocessor will parse full text and update the Doc instance on context.
 * <ul>
 *     <li>Input: raw file input stream</li>
 *     <li>Output: Doc with the parsed text and metadata</li>
 * </ul>
 */
public class TikaProcessor implements Processor {
    private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());
    private FsSettings fsSettings;
    private MessageDigest messageDigest;

    public TikaProcessor(FsSettings fsSettings, MessageDigest messageDigest) {
        this.fsSettings = fsSettings;
        this.messageDigest = messageDigest;
    }

    @Override
    public void process(FsCrawlerContext ctx) throws ProcessingException {
        try(InputStream stream = ctx.getInputStream()) {
            long startTime = System.currentTimeMillis();
            TikaDocParser.generate(
                    fsSettings,
                    stream,
                    ctx.getFile().getName(),
                    ctx.getFullFilename(),
                    ctx.getDoc(),
                    messageDigest,
                    ctx.getFile().getSize());
            logger.debug("Parsing document {} with Tika in {}ms", ctx.getFile().getName(),
                    System.currentTimeMillis() - startTime);
            logger.trace("Parsed doc={}", DocParser.toJson(ctx.getDoc()));
        } catch (IOException e) {
            throw new ProcessingException(e);
        }
    }
}
