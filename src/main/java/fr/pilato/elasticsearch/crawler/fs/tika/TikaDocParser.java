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

package fr.pilato.elasticsearch.crawler.fs.tika;

import fr.pilato.elasticsearch.crawler.fs.meta.doc.Doc;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.FsSettings;
import org.apache.commons.io.input.TeeInputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tika.language.detect.LanguageResult;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static fr.pilato.elasticsearch.crawler.fs.tika.TikaInstance.langDetector;
import static fr.pilato.elasticsearch.crawler.fs.tika.TikaInstance.tika;

/**
 * Parse a binary document and generate a FSCrawler Doc
 */
public class TikaDocParser {

    private final static Logger logger = LogManager.getLogger(TikaDocParser.class);

    public static void generate(FsSettings fsSettings, InputStream inputStream, String filename, Doc doc, MessageDigest messageDigest,
                                long filesize) throws IOException {
        logger.trace("Generating document [{}]", filename);
        // Extracting content with Tika
        // See #38: https://github.com/dadoonet/fscrawler/issues/38
        int indexedChars = 100000;
        if (fsSettings.getFs().getIndexedChars() != null) {
            if (fsSettings.getFs().getIndexedChars().percentage()) {
                indexedChars = (int) Math.round(filesize * fsSettings.getFs().getIndexedChars().asDouble());
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

        if (messageDigest != null) {
            logger.trace("Generating hash with [{}]", messageDigest.getAlgorithm());
            inputStream = new DigestInputStream(inputStream, messageDigest);
        }

        ByteArrayOutputStream bos = null;
        if (fsSettings.getFs().isStoreSource()) {
            logger.debug("Using a TeeInputStream as we need to store the source");
            bos = new ByteArrayOutputStream();
            inputStream = new TeeInputStream(inputStream, bos);
        }

        try {
            // Set the maximum length of strings returned by the parseToString method, -1 sets no limit
            logger.trace("Beginning Tika extraction");
            parsedContent = tika().parseToString(inputStream, metadata, indexedChars);
            logger.trace("End of Tika extraction");
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
        if (messageDigest != null) {
            byte[] digest = messageDigest.digest();
            String result = "";
            // Convert to Hexa
            for (byte aDigest : digest) {
                result += Integer.toString((aDigest & 0xff) + 0x100, 16).substring(1);
            }
            doc.getFile().setChecksum(result);
        }
        // File

        // Meta
        doc.getMeta().setAuthor(metadata.get(TikaCoreProperties.CREATOR));
        doc.getMeta().setTitle(metadata.get(TikaCoreProperties.TITLE));
        String sDate = metadata.get(TikaCoreProperties.MODIFIED);
        if (sDate != null) {
            try {
                LocalDateTime date = LocalDateTime.parse(sDate, DateTimeFormatter.ISO_DATE_TIME);
                doc.getMeta().setDate(date);
            } catch (DateTimeParseException e) {
                logger.warn("Can not parse date [{}] for [{}]. Skipping date field...", sDate, filename);
            }
        }
        doc.getMeta().setKeywords(commaDelimitedListToStringArray(metadata.get(TikaCoreProperties.KEYWORDS)));

        if (fsSettings.getFs().isRawMetadata()) {
            logger.trace("Listing all available metadata:");
            for (String metadataName : metadata.names()) {
                String value = metadata.get(metadataName);
                // This is a logger trick which helps to generate our unit tests
                // You need to change test/resources/log4j2.xml fr.pilato.elasticsearch.crawler.fs.tika level to trace
                logger.trace("  assertThat(raw, hasEntry(\"{}\", \"{}\"));", metadataName, value);

                // We need to remove dots in field names if any. See https://github.com/dadoonet/fscrawler/issues/256
                doc.getMeta().addRaw(metadataName.replaceAll("\\.", ":"), value);
            }
        }

        if (fsSettings.getFs().isLangDetect() && parsedContent != null) {
            List<LanguageResult> languages = langDetector().detectAll(parsedContent);
            if (!languages.isEmpty()) {
                LanguageResult language = languages.get(0);
                logger.trace("Main detected language: [{}]", language);
                doc.getMeta().setLanguage(language.getLanguage());
            }
        }
        // Meta

        // Doc content
        doc.setContent(parsedContent);

        // Doc as binary attachment
        if (fsSettings.getFs().isStoreSource()) {
            //noinspection ConstantConditions
            doc.setAttachment(Base64.getEncoder().encodeToString(bos.toByteArray()));
        }
        logger.trace("End document generation");
        // End of our document
    }

    private static List<String> commaDelimitedListToStringArray(String str) {
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
