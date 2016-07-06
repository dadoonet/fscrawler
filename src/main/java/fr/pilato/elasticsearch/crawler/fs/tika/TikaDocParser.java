/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

package fr.pilato.elasticsearch.crawler.fs.tika;

import fr.pilato.elasticsearch.crawler.fs.meta.doc.Doc;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.FsSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tika.metadata.Metadata;

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static fr.pilato.elasticsearch.crawler.fs.tika.TikaInstance.tika;

/**
 * Parse a binary document and generate a FSCrawler Doc
 */
public class TikaDocParser {

    private final static Logger logger = LogManager.getLogger(TikaDocParser.class);

    public static void generate(FsSettings fsSettings, byte[] data, String filename, Doc doc) throws UnsupportedEncodingException {
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
                logger.trace("indexed chars [{}]",
                        indexedChars == -1 ? "has been disabled. All text will be extracted" : indexedChars);
            }
        }
        Metadata metadata = new Metadata();

        String parsedContent = null;
        try {
            // Set the maximum length of strings returned by the parseToString method, -1 sets no limit
            parsedContent = tika().parseToString(new ByteArrayInputStream(data), metadata, indexedChars);
        } catch (Throwable e) {
            logger.debug("Failed to extract [" + indexedChars + "] characters of text for [" + filename + "]", e);
        }

        // Adding what we found to the document we want to index

        // File
        doc.getFile().setContentType(metadata.get(Metadata.CONTENT_TYPE));

        // We only add `indexed_chars` if we have other value than default or -1
        if (fsSettings.getFs().getIndexedChars() != null && fsSettings.getFs().getIndexedChars().value() != -1) {
            doc.getFile().setIndexedChars(indexedChars);
        }

        if (fsSettings.getFs().isAddFilesize()) {
            if (metadata.get(Metadata.CONTENT_LENGTH) != null) {
                // We try to get CONTENT_LENGTH from Tika first
                doc.getFile().setFilesize(Long.parseLong(metadata.get(Metadata.CONTENT_LENGTH)));
            }
        }

        // Meta
        doc.getMeta().setAuthor(metadata.get(Metadata.AUTHOR));
        doc.getMeta().setTitle(metadata.get(Metadata.TITLE));
        // TODO Fix that as the date we get from Tika might be not parseable as a Date
        // doc.getMeta().setDate(metadata.get(Metadata.DATE));
        doc.getMeta().setKeywords(commaDelimitedListToStringArray(metadata.get(Metadata.KEYWORDS)));
        // Meta

        logger.debug("Listing all available metadata:");
        for (String metadataName : metadata.names()) {
            logger.debug(" - [{}] = [{}]", metadataName, metadata.getValues(metadataName));
        }

        // Doc content
        doc.setContent(parsedContent);

        // Doc as binary attachment
        if (fsSettings.getFs().isStoreSource()) {
            doc.setAttachment(new String(Base64.getEncoder().encode(data), "UTF-8"));
        }
        // End of our document
    }

    public static List<String> commaDelimitedListToStringArray(String str) {
        if (str == null) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        int pos = 0;
        int delPos;
        while ((delPos = str.indexOf(",", pos)) != -1) {
            result.add(str.substring(pos, delPos));
            pos = delPos + 1;
        }
        if (str.length() > 0 && pos <= str.length()) {
            // Add rest of String, but not in case of empty input.
            result.add(str.substring(pos));
        }
        return result;
    }


}
