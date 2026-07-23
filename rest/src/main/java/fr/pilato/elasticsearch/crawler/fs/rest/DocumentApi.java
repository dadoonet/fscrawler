/*
 * Licensed to David Pilato (the "Author") under one
 * or more contributor license agreements. See the NOTICE file
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
 *
 * Made from 🇫🇷🇪🇺 with ❤️ - 2011-2026
 */
package fr.pilato.elasticsearch.crawler.fs.rest;

import com.jayway.jsonpath.DocumentContext;
import fr.pilato.elasticsearch.crawler.fs.beans.Doc;
import fr.pilato.elasticsearch.crawler.fs.beans.DocUtils;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClientException;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil;
import fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil;
import fr.pilato.elasticsearch.crawler.fs.framework.SignTool;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerDocumentService;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.tika.TikaDocParser;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerExtensionFsProvider;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerExtensionPasswordProvider;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerPluginsManager;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;

@Path("/_document")
public class DocumentApi implements RestApi {
    private static final Logger logger = LogManager.getLogger();
    private static final long MULTIPART_IN_MEMORY_SPOOL_THRESHOLD = 64L * 1024;

    private final FsCrawlerDocumentService documentService;
    private final FsSettings settings;
    private static final TimeBasedUUIDGenerator TIME_UUID_GENERATOR = new TimeBasedUUIDGenerator();
    private final FsCrawlerPluginsManager pluginsManager;
    private final TikaDocParser tikaDocParser;

    DocumentApi(FsSettings settings, FsCrawlerDocumentService documentService, FsCrawlerPluginsManager pluginsManager) {
        this(settings, documentService, pluginsManager, new TikaDocParser(settings));
    }

    DocumentApi(
            FsSettings settings,
            FsCrawlerDocumentService documentService,
            FsCrawlerPluginsManager pluginsManager,
            TikaDocParser tikaDocParser) {
        this.settings = settings;
        this.documentService = documentService;
        this.pluginsManager = pluginsManager;
        this.tikaDocParser = tikaDocParser;
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public UploadResponse addDocument(
            @QueryParam("debug") String debug,
            @QueryParam("simulate") String simulate,
            @FormDataParam("id") String formId,
            @FormDataParam("index") String formIndex,
            @HeaderParam("id") String headerId,
            @HeaderParam("index") String headerIndex,
            @QueryParam("id") String queryParamId,
            @QueryParam("index") String queryParamIndex,
            @FormDataParam("password") String formDocumentPassword,
            @HeaderParam("password") String headerDocumentPassword,
            @QueryParam("password") String queryParamDocumentPassword,
            @FormDataParam("tags") InputStream tags,
            @FormDataParam("file") InputStream filecontent,
            @FormDataParam("file") FormDataContentDisposition d)
            throws IOException, NoSuchAlgorithmException {
        String id = FsCrawlerUtil.getFirstNonNullValue(formId, headerId, queryParamId);
        String index = FsCrawlerUtil.getFirstNonNullValue(formIndex, headerIndex, queryParamIndex);
        String password = FsCrawlerUtil.getFirstNonNullValue(
                formDocumentPassword, headerDocumentPassword, queryParamDocumentPassword);
        return uploadToDocumentService(debug, simulate, id, index, password, tags, filecontent, d);
    }

    @PUT
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public UploadResponse addDocument(
            @QueryParam("debug") String debug,
            @QueryParam("simulate") String simulate,
            @PathParam("id") String id,
            @FormDataParam("index") String formIndex,
            @HeaderParam("index") String headerIndex,
            @QueryParam("index") String queryParamIndex,
            @FormDataParam("password") String formDocumentPassword,
            @HeaderParam("password") String headerDocumentPassword,
            @QueryParam("password") String queryParamDocumentPassword,
            @FormDataParam("tags") InputStream tags,
            @FormDataParam("file") InputStream filecontent,
            @FormDataParam("file") FormDataContentDisposition d)
            throws IOException, NoSuchAlgorithmException {
        String index = FsCrawlerUtil.getFirstNonNullValue(formIndex, headerIndex, queryParamIndex);
        String password = FsCrawlerUtil.getFirstNonNullValue(
                formDocumentPassword, headerDocumentPassword, queryParamDocumentPassword);
        return uploadToDocumentService(debug, simulate, id, index, password, tags, filecontent, d);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public UploadResponse addDocumentFrom3rdParty(
            @QueryParam("debug") String debug,
            @QueryParam("simulate") String simulate,
            @QueryParam("id") String queryParamId,
            @QueryParam("index") String queryParamIndex,
            @HeaderParam("id") String headerId,
            @HeaderParam("index") String headerIndex,
            @HeaderParam("password") String headerDocumentPassword,
            @QueryParam("password") String queryParamDocumentPassword,
            InputStream json) {
        String id = FsCrawlerUtil.getFirstNonNullValue(headerId, queryParamId);
        String index = FsCrawlerUtil.getFirstNonNullValue(headerIndex, queryParamIndex);
        String password = FsCrawlerUtil.getFirstNonNullValue(headerDocumentPassword, queryParamDocumentPassword);

        DocumentContext document = JsonUtil.parseJsonAsDocumentContext(json);
        String type = document.read("$.type");

        logger.debug("Reading document from 3rd-party [{}]", type);

        try (FsCrawlerExtensionFsProvider provider = pluginsManager.findFsProvider(type)) {
            logger.trace("Plugin [{}] found", provider.getType());
            provider.start(settings, document.jsonString());
            Doc doc = provider.createDocument();
            doc = enrichDoc(doc, null, provider::readFile, password, resolvePasswordProvider(password));
            return uploadToDocumentService(debug, simulate, id, index, doc);
        } catch (Exception e) {
            logger.debug(
                    "Failed to add document from [{}] 3rd-party: [{}] - [{}]",
                    type,
                    e.getClass().getSimpleName(),
                    e.getMessage());
            logger.trace("Full stacktrace:", e);
            return new UploadResponse(
                    false,
                    "Failed to add document from [" + type + "] 3rd-party: ["
                            + e.getClass().getSimpleName() + "] - [" + e.getMessage() + "]");
        }
    }

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    public DeleteResponse removeDocument(
            @HeaderParam("filename") String headerFilename,
            @HeaderParam("filename*") String headerFilenameStar,
            @QueryParam("filename") String queryParamFilename,
            @HeaderParam("index") String headerIndex,
            @QueryParam("index") String queryParamIndex)
            throws NoSuchAlgorithmException {
        String index = headerIndex == null ? queryParamIndex : headerIndex;
        String filename = headerFilename == null ? queryParamFilename : headerFilename;

        // Support for rfc6266: https://datatracker.ietf.org/doc/html/rfc6266#section-5
        if (headerFilenameStar != null) {
            String[] splits = headerFilenameStar.split("''");
            filename = URLDecoder.decode(splits[1], Charset.forName(splits[0]));
        }

        if (filename == null) {
            return new DeleteResponse(
                    false,
                    "We can not delete a document without an id or a filename. "
                            + "Either call DELETE /_document/ID or DELETE /_document?filename=foo.txt");
        }

        return removeDocumentInDocumentService(
                SignTool.sign(settings.getFs().getHashAlgorithm(), filename), filename, index);
    }

    @Path("/{id}")
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    public DeleteResponse removeDocument(
            @PathParam("id") String id,
            @HeaderParam("index") String headerIndex,
            @QueryParam("index") String queryParamIndex) {
        return removeDocumentInDocumentService(id, null, headerIndex == null ? queryParamIndex : headerIndex);
    }

    // Multipart upload keeps JAX-RS-resolved fields together; extracting a param object would not reduce call-site
    // noise.
    @SuppressWarnings("java:S107")
    private UploadResponse uploadToDocumentService(
            String debug,
            String simulate,
            String id,
            String index,
            String explicitPassword,
            InputStream tags,
            InputStream filecontent,
            FormDataContentDisposition d)
            throws IOException, NoSuchAlgorithmException {
        if (d == null) {
            UploadResponse response = new UploadResponse();
            response.setOk(false);
            response.setMessage("No file has been sent or you are not using [file] as the field name.");
            return response;
        }

        Doc doc = new Doc();
        String filename = new String(d.getFileName().getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
        doc.getFile().setFilename(filename);
        doc.getPath().setVirtual(filename);
        doc.getPath().setReal(filename);
        doc.getFile().setFilesize(d.getSize());

        try (filecontent;
                ReopenableContent reopenableContent = spoolMultipartContent(filecontent, d.getSize())) {
            doc = enrichDoc(
                    doc, tags, reopenableContent.reopen(), explicitPassword, resolvePasswordProvider(explicitPassword));
            return uploadToDocumentService(debug, simulate, id, index, doc);
        }
    }

    private Doc enrichDoc(
            Doc doc,
            InputStream tags,
            TikaDocParser.InputStreamSupplier reopenableInputStream,
            String explicitPassword,
            FsCrawlerExtensionPasswordProvider passwordProvider)
            throws IOException {
        // File
        doc.getFile()
                .setExtension(
                        FilenameUtils.getExtension(doc.getFile().getFilename()).toLowerCase());
        doc.getFile().setIndexingDate(Instant.now());
        // File

        // Read the file content
        tikaDocParser.generate(
                reopenableInputStream, doc, doc.getFile().getFilesize(), explicitPassword, passwordProvider);

        // We merge tags if any and return the final doc
        return DocUtils.getMergedDoc(doc, tags, JsonUtil.mapper);
    }

    private FsCrawlerExtensionPasswordProvider resolvePasswordProvider(String explicitPassword) {
        if (explicitPassword != null) {
            return null;
        }

        String providerType =
                settings.getPasswords() == null || settings.getPasswords().getProvider() == null
                        ? "noop"
                        : settings.getPasswords().getProvider();
        return pluginsManager.findPasswordProvider(providerType);
    }

    private ReopenableContent spoolMultipartContent(InputStream filecontent, long filesize) throws IOException {
        if (filesize > 0 && filesize <= MULTIPART_IN_MEMORY_SPOOL_THRESHOLD) {
            byte[] content = filecontent.readAllBytes();
            return new ReopenableContent(() -> new ByteArrayInputStream(content), null);
        }

        java.nio.file.Path tempFile = createMultipartTempFile();
        try (OutputStream outputStream = Files.newOutputStream(tempFile)) {
            filecontent.transferTo(outputStream);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException cleanupException) {
                e.addSuppressed(cleanupException);
            }
            throw e;
        }
        return new ReopenableContent(() -> Files.newInputStream(tempFile), tempFile);
    }

    private java.nio.file.Path createMultipartTempFile() throws IOException {
        // Use the job-private fs.temp_dir (set by FsCrawlerImpl). Never fall back to java.io.tmpdir
        // (Sonar java:S5443 — publicly writable directories).
        if (settings.getFs().getTempDir() == null) {
            throw new IOException("tempDir must be configured when REST multipart content is spooled to disk. "
                    + "This is normally set automatically by FsCrawlerImpl.");
        }
        java.nio.file.Path tempDir = Paths.get(settings.getFs().getTempDir());
        Files.createDirectories(tempDir);
        return Files.createTempFile(tempDir, "fscrawler-rest-", ".tmp");
    }

    private UploadResponse uploadToDocumentService(String debug, String simulate, String id, String index, Doc doc)
            throws NoSuchAlgorithmException {
        // Id
        if (id == null) {
            if (settings.getFs().isFilenameAsId()) {
                id = doc.getFile().getFilename();
            } else {
                id = SignTool.sign(
                        settings.getFs().getHashAlgorithm(), doc.getFile().getFilename());
            }
        } else if (id.equals("_auto_")) {
            // We are using a specific id which tells us to generate a unique _id like elasticsearch does
            id = TIME_UUID_GENERATOR.getBase64UUID();
        }

        // Index
        if (index == null) {
            index = settings.getElasticsearch().getIndex();
        }

        // Elasticsearch entity coordinates (we use the first node address)
        String url = settings.getElasticsearch().getUrls().get(0) + "/" + index + "/_doc/" + id;
        if (Boolean.parseBoolean(simulate)) {
            logger.debug(
                    "Simulate mode is on, so we skip sending document [{}] to elasticsearch at [{}].",
                    doc.getFile().getFilename(),
                    url);
        } else {
            logger.debug(
                    "Sending document [{}] to elasticsearch.", doc.getFile().getFilename());
            try {
                // Capture generation before index so a timer-driven bulk failure (and a concurrent crawl clear of the
                // sticky flag) still makes this upload return ok:false.
                long bulkFailureGeneration = documentService.getBulkFailureGeneration();
                documentService.index(
                        index, id, doc, settings.getElasticsearch().getPipeline());
                documentService.flushAndEnsureBulkSucceededSince(bulkFailureGeneration);
            } catch (ElasticsearchClientException e) {
                logger.error(
                        "Failed to index document [{}] via REST after bulk retries: {}",
                        doc.getFile().getFilename(),
                        e.getMessage());
                UploadResponse response = new UploadResponse(
                        false, "Can not index document [" + doc.getFile().getFilename() + "]: " + e.getMessage());
                response.setFilename(doc.getFile().getFilename());
                response.setUrl(url);
                return response;
            }
        }

        UploadResponse response = new UploadResponse(true);
        response.setFilename(doc.getFile().getFilename());
        response.setUrl(url);

        if (logger.isDebugEnabled() || Boolean.parseBoolean(debug)) {
            // We send the content back if debug is on or if we got in the query explicitly a debug command
            response.setDoc(doc);
        }

        return response;
    }

    private DeleteResponse removeDocumentInDocumentService(String id, String filename, String index) {
        if (index == null) {
            index = settings.getElasticsearch().getIndex();
        }

        if (id == null && filename == null) {
            return new DeleteResponse(
                    false,
                    "We can not delete a document without an id or a filename. "
                            + "Either call DELETE /_document/ID or DELETE /_document?filename=foo.txt");
        }

        logger.debug("Delete document [{}/{}] from elasticsearch using index [{}].", id, filename, index);
        DeleteResponse response;
        try {
            documentService.deleteSingle(index, id);
            response = new DeleteResponse(true);
        } catch (Exception e) {
            response = new DeleteResponse(
                    false,
                    "Can not remove document [" + index + "/" + (filename == null ? id : filename) + "]: "
                            + e.getMessage());
        }
        response.setIndex(index);
        response.setId(id);
        response.setFilename(filename);

        return response;
    }

    private record ReopenableContent(TikaDocParser.InputStreamSupplier reopen, java.nio.file.Path tempFile)
            implements AutoCloseable {
        @Override
        public void close() throws IOException {
            if (tempFile != null) {
                Files.deleteIfExists(tempFile);
            }
        }
    }
}
