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

import fr.pilato.elasticsearch.crawler.fs.beans.Doc;
import fr.pilato.elasticsearch.crawler.fs.framework.FSCrawlerLogger;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerIllegalConfigurationException;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil;
import fr.pilato.elasticsearch.crawler.fs.framework.SignTool;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tika.language.detect.LanguageResult;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static fr.pilato.elasticsearch.crawler.fs.framework.FSCrawlerLogger.*;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.computeVirtualPathName;
import static fr.pilato.elasticsearch.crawler.fs.tika.TikaInstance.extractText;
import static fr.pilato.elasticsearch.crawler.fs.tika.TikaInstance.langDetector;

/**
 * Parse a binary document and generate a FSCrawler Doc
 */
public class TikaDocParser {

    private static final Logger logger = LogManager.getLogger();

    /**
     * Threshold in bytes below which we keep the file content in memory instead of using a temp file.
     * This avoids disk I/O overhead for small files while still protecting against OOM for large files.
     */
    private static final long IN_MEMORY_THRESHOLD = 64L * 1024; // 64KB

    private static MessageDigest findMessageDigest(FsSettings fsSettings) {
        if (fsSettings.getFs().getChecksum() != null) {
            try {
                return MessageDigest.getInstance(fsSettings.getFs().getChecksum());
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("This should never happen as we checked that previously");
            }
        } else {
            return null;
        }
    }

    public static void generate(FsSettings fsSettings, InputStream inputStream, Doc doc, long filesize) throws IOException {
        logger.trace("Generating document [{}]", doc.getPath().getReal());
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
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, doc.getFile().getFilename());

        String parsedContent = null;

        // If checksum is needed, we need to read the entire stream to compute it.
        // Tika's internal mark/reset during content type detection would otherwise cause
        // the DigestInputStream to only hash the bytes read during detection (typically 64KB),
        // not the entire file.
        // To avoid loading the entire file in memory (which could cause OOM for large files),
        // we use a temporary file to store the content while computing the checksum.
        // We also use a temp file when storeSource is enabled, so we can read the file twice
        // (once for Tika, once for storing as attachment).
        // For small files (below IN_MEMORY_THRESHOLD), we keep everything in memory to avoid disk I/O.
        MessageDigest messageDigest = findMessageDigest(fsSettings);
        boolean needsBuffering = messageDigest != null || fsSettings.getFs().isStoreSource();
        // Use in-memory only when we KNOW the file is small (filesize > 0 and <= threshold)
        // When filesize is unknown (-1 or 0), use temp file to be safe and avoid OOM
        boolean useInMemory = needsBuffering && filesize > 0 && filesize <= IN_MEMORY_THRESHOLD;
        boolean useTempFile = needsBuffering && (filesize <= 0 || filesize > IN_MEMORY_THRESHOLD);
        Path tempFile = null;
        InputStream tempFileStream = null;
        byte[] contentBuffer = null;
        try {
        if (useInMemory) {
            logger.trace("Using in-memory buffer for small file (size: {}, checksum: {}, storeSource: {})",
                    filesize, messageDigest != null, fsSettings.getFs().isStoreSource());
            // Read entire stream into memory
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            if (messageDigest != null) {
                try (DigestInputStream dis = new DigestInputStream(inputStream, messageDigest)) {
                    dis.transferTo(bos);
                }
            } else {
                inputStream.transferTo(bos);
            }
            contentBuffer = bos.toByteArray();
            inputStream = new ByteArrayInputStream(contentBuffer);
        } else if (useTempFile) {
            logger.trace("Using temp file for large file (size: {}, checksum: {}, storeSource: {})",
                    filesize, messageDigest != null, fsSettings.getFs().isStoreSource());
            // Use configured temp directory - it should always be set by FsCrawlerImpl
            if (fsSettings.getFs().getTempDir() == null) {
                throw new FsCrawlerIllegalConfigurationException("tempDir must be configured when checksum or storeSource is enabled. " +
                        "This is normally set automatically by FsCrawlerImpl.");
            }
            Path tempDir = Paths.get(fsSettings.getFs().getTempDir());
            Files.createDirectories(tempDir);
            tempFile = Files.createTempFile(tempDir, "fscrawler-", ".tmp");
            // Copy stream to temp file, optionally computing the digest
            if (messageDigest != null) {
                try (DigestInputStream dis = new DigestInputStream(inputStream, messageDigest);
                     OutputStream fos = Files.newOutputStream(tempFile)) {
                    dis.transferTo(fos);
                }
            } else {
                try (OutputStream fos = Files.newOutputStream(tempFile)) {
                    inputStream.transferTo(fos);
                }
            }
            // Now use the temp file as input for Tika
            tempFileStream = Files.newInputStream(tempFile);
            inputStream = tempFileStream;
        }

        if (fsSettings.getFs().isIndexContent()) {
            try {
                // Set the maximum length of strings returned by the parseToString method, -1 sets no limit
                logger.trace("Beginning Tika extraction");
                parsedContent = extractText(fsSettings, indexedChars, inputStream, metadata);
                logger.trace("End of Tika extraction");
            } catch (Throwable e) {
                // Build a message from embedded errors
                Throwable current = e;
                StringBuilder sb = new StringBuilder();
                while (current != null) {
                    sb.append(current.getMessage());
                    current = current.getCause();
                    if (current != null) {
                        sb.append(" -> ");
                    }
                }

                try {
                    FSCrawlerLogger.documentError(
                            fsSettings.getFs().isFilenameAsId() ? doc.getFile().getFilename() : SignTool.sign(doc.getPath().getReal()),
                            computeVirtualPathName(fsSettings.getFs().getUrl(), doc.getPath().getReal()),
                            sb.toString());
                } catch (NoSuchAlgorithmException ignored) { }
                logger.warn("Failed to extract [{}] characters of text for [{}]: {}", indexedChars, doc.getPath().getReal(), sb.toString());
                logger.debug("Failed to extract [" + indexedChars + "] characters of text for [" + doc.getPath().getReal() + "]", e);
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
                StringBuilder result = new StringBuilder();
                // Convert to Hexa
                for (byte aDigest : digest) {
                    result.append(Integer.toString((aDigest & 0xff) + 0x100, 16).substring(1));
                }
                doc.getFile().setChecksum(result.toString());
            }
            // File

            // Standard Meta
            setMeta(doc.getPath().getReal(), metadata, TikaCoreProperties.CREATOR, doc.getMeta()::setAuthor, Function.identity());
            setMeta(doc.getPath().getReal(), metadata, TikaCoreProperties.TITLE, doc.getMeta()::setTitle, Function.identity());
            setMeta(doc.getPath().getReal(), metadata, TikaCoreProperties.MODIFIED, doc.getMeta()::setDate, FsCrawlerUtil::localDateTimeToDate);

            setMeta(doc.getPath().getReal(), metadata, Office.KEYWORDS, doc.getMeta()::setKeywords, TikaDocParser::commaDelimitedListToStringArray);
            // TODO Fix this with Tika 2.2.1+
            // See https://issues.apache.org/jira/browse/TIKA-3629
            if (doc.getMeta().getKeywords() == null) {
                setMeta(doc.getPath().getReal(), metadata, Property.internalText("pdf:docinfo:keywords"), doc.getMeta()::setKeywords, TikaDocParser::commaDelimitedListToStringArray);
            }

            setMeta(doc.getPath().getReal(), metadata, TikaCoreProperties.FORMAT, doc.getMeta()::setFormat, Function.identity());
            setMeta(doc.getPath().getReal(), metadata, TikaCoreProperties.IDENTIFIER, doc.getMeta()::setIdentifier, Function.identity());
            setMeta(doc.getPath().getReal(), metadata, TikaCoreProperties.CONTRIBUTOR, doc.getMeta()::setContributor, Function.identity());
            setMeta(doc.getPath().getReal(), metadata, TikaCoreProperties.COVERAGE, doc.getMeta()::setCoverage, Function.identity());
            setMeta(doc.getPath().getReal(), metadata, TikaCoreProperties.MODIFIER, doc.getMeta()::setModifier, Function.identity());
            setMeta(doc.getPath().getReal(), metadata, TikaCoreProperties.CREATOR_TOOL, doc.getMeta()::setCreatorTool, Function.identity());
            String finalParsedContent = parsedContent;
            setMeta(doc.getPath().getReal(), metadata, TikaCoreProperties.LANGUAGE, doc.getMeta()::setLanguage, (lang) -> {
                if (lang != null) {
                    return lang;
                } else if (fsSettings.getFs().isLangDetect() && finalParsedContent != null) {
                    List<LanguageResult> languages = langDetector().detectAll(finalParsedContent);
                    if (!languages.isEmpty()) {
                        LanguageResult language = languages.get(0);
                        logger.trace("Main detected language: [{}]", language);
                        return language.getLanguage();
                    }
                }
                return null;
            });
            setMeta(doc.getPath().getReal(), metadata, TikaCoreProperties.PUBLISHER, doc.getMeta()::setPublisher, Function.identity());
            setMeta(doc.getPath().getReal(), metadata, TikaCoreProperties.RELATION, doc.getMeta()::setRelation, Function.identity());
            setMeta(doc.getPath().getReal(), metadata, TikaCoreProperties.RIGHTS, doc.getMeta()::setRights, Function.identity());
            setMeta(doc.getPath().getReal(), metadata, TikaCoreProperties.SOURCE, doc.getMeta()::setSource, Function.identity());
            setMeta(doc.getPath().getReal(), metadata, TikaCoreProperties.TYPE, doc.getMeta()::setType, Function.identity());
            setMeta(doc.getPath().getReal(), metadata, TikaCoreProperties.DESCRIPTION, doc.getMeta()::setDescription, Function.identity());
            setMeta(doc.getPath().getReal(), metadata, TikaCoreProperties.CREATED, doc.getMeta()::setCreated, FsCrawlerUtil::localDateTimeToDate);
            setMeta(doc.getPath().getReal(), metadata, TikaCoreProperties.PRINT_DATE, doc.getMeta()::setPrintDate, FsCrawlerUtil::localDateTimeToDate);
            setMeta(doc.getPath().getReal(), metadata, TikaCoreProperties.METADATA_DATE, doc.getMeta()::setMetadataDate, FsCrawlerUtil::localDateTimeToDate);
            setMeta(doc.getPath().getReal(), metadata, TikaCoreProperties.LATITUDE, doc.getMeta()::setLatitude, Function.identity());
            setMeta(doc.getPath().getReal(), metadata, TikaCoreProperties.LONGITUDE, doc.getMeta()::setLongitude, Function.identity());
            setMeta(doc.getPath().getReal(), metadata, TikaCoreProperties.ALTITUDE, doc.getMeta()::setAltitude, Function.identity());
            setMeta(doc.getPath().getReal(), metadata, TikaCoreProperties.RATING, doc.getMeta()::setRating, (value) -> value == null ? null : Integer.parseInt(value));
            setMeta(doc.getPath().getReal(), metadata, TikaCoreProperties.COMMENTS, doc.getMeta()::setComments, Function.identity());

            // Add support for more OOTB standard metadata

            if (fsSettings.getFs().isRawMetadata()) {
                metadata("Listing all available metadata:");
                metadata("  assertThat(raw)");
                metadata("    .hasSize({})", metadata.size());
                for (String metadataName : metadata.names()) {
                    String value = metadata.get(metadataName);
                    // This is a logger trick which helps to generate our unit tests
                    // You need to change test/resources/log4j2.xml fr.pilato.elasticsearch.crawler.fs.tika level to trace
                    metadata("    .containsEntry(\"{}\", \"{}\")", metadataName, value);

                    // We need to remove dots in field names if any. See https://github.com/dadoonet/fscrawler/issues/256
                    doc.getMeta().addRaw(metadataName.replaceAll("\\.", ":"), value);
                }
                metadata(";");
            }
            // Meta

            // Doc content
            doc.setContent(parsedContent);
        }

        // Doc as binary attachment
        if (fsSettings.getFs().isStoreSource()) {
            logger.trace("Storing source as attachment");
            if (contentBuffer != null) {
                // Use in-memory buffer for small files
                doc.setAttachment(Base64.getEncoder().encodeToString(contentBuffer));
            } else {
                // Stream from temp file for large files to avoid loading raw bytes into memory
                // We use Base64.getEncoder().wrap() to encode while streaming
                ByteArrayOutputStream base64Out = new ByteArrayOutputStream();
                try (OutputStream encoder = Base64.getEncoder().wrap(base64Out);
                     InputStream fileIn = Files.newInputStream(tempFile)) {
                    fileIn.transferTo(encoder);
                }
                // Use explicit charset to avoid platform dependency
                doc.setAttachment(base64Out.toString(StandardCharsets.UTF_8));
            }
        }
        logger.trace("End document generation");
        // End of our document
        } finally {
            // Close the temp file stream before deleting the file
            if (tempFileStream != null) {
                try {
                    tempFileStream.close();
                } catch (IOException e) {
                    logger.debug("Failed to close temp file stream: {}", e.getMessage());
                }
            }
            // Clean up temp file if it was created
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                    logger.trace("Deleted temp file [{}]", tempFile);
                } catch (IOException e) {
                    logger.warn("Failed to delete temp file [{}]: {}", tempFile, e.getMessage());
                }
            }
        }
    }

    private static <T> void setMeta(String filename, Metadata metadata, Property property, Consumer<T> setter, Function<String,T> transformer) {
        String sMeta = metadata.get(property);
        try {
            setter.accept(transformer.apply(sMeta));
        } catch (Exception e) {
            logger.warn("Can not parse meta [{}] for [{}]. Skipping [{}] field...", sMeta, filename, property.getName());
        }
    }

    private static List<String> commaDelimitedListToStringArray(String str) {
        if (str == null) {
            return null;
        }
        List<String> result = new ArrayList<>();
        int pos = 0;
        int delPos;
        while ((delPos = str.indexOf(",", pos)) != -1) {
            result.add(str.substring(pos, delPos));
            pos = delPos + 1;
        }
        if (!str.isEmpty() && pos <= str.length()) {
            // Add rest of String, but not in case of empty input.
            result.add(str.substring(pos));
        }
        return result;
    }
}
