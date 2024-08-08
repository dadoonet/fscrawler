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

package fr.pilato.elasticsearch.crawler.fs.rest;

import com.fasterxml.jackson.databind.JsonNode;
import fr.pilato.elasticsearch.crawler.fs.beans.Doc;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchHit;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClientException;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil;
import fr.pilato.elasticsearch.crawler.fs.framework.SignTool;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerDocumentService;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.ServerUrl;
import fr.pilato.elasticsearch.crawler.fs.tika.TikaDocParser;
import jakarta.ws.rs.BadRequestException;
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
import org.apache.commons.io.FilenameUtils;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.localDateTimeToDate;
import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.mapper;

@Path("/_document")
public class DocumentApi extends RestApi {

    private final FsCrawlerDocumentService documentService;
    private final FsSettings settings;
    private final MessageDigest messageDigest;
    private static final TimeBasedUUIDGenerator TIME_UUID_GENERATOR = new TimeBasedUUIDGenerator();

    DocumentApi(FsSettings settings, FsCrawlerDocumentService documentService) {
        this.settings = settings;
        this.documentService = documentService;
        // Create MessageDigest instance
        try {
            messageDigest = settings.getFs().getChecksum() == null ?
                    null : MessageDigest.getInstance(settings.getFs().getChecksum());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("This should never happen as we checked that previously");
        }
    }


    @Path("/url")
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public UploadResponse addUrlDocument(
            @QueryParam("debug") String debug,
            @QueryParam("simulate") String simulate,
            @FormDataParam("id") String formId,
            @FormDataParam("index") String formIndex,
            @HeaderParam("id") String headerId,
            @HeaderParam("index") String headerIndex,
            @QueryParam("id") String queryParamId,
            @QueryParam("index") String queryParamIndex,
            @QueryParam("url") String queryUrl,
            @FormDataParam("fileName") String fileName,
            @FormDataParam("url") String url) throws IOException, NoSuchAlgorithmException {
        String requestUrl = queryUrl != null ? queryUrl : url;

        if (requestUrl == null || requestUrl == "") {
            UploadResponse response = new UploadResponse();
            response.setOk(false);
            response.setMessage("Please set the url address in the queryParam or formData .");
            return response;
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(requestUrl).openConnection();
        connection.setRequestMethod("GET");
        int responseCode = connection.getResponseCode();

        if (responseCode == HttpURLConnection.HTTP_OK) {
            long fileSize = connection.getContentLengthLong();

            String dispositionHeader = connection.getHeaderField("Content-Disposition");
            if (fileName == null || fileName.length() == 0) {
                fileName = getFileNameFromDisposition(dispositionHeader);
            }
            InputStream in = connection.getInputStream();

            logger.warn(fileName);
            logger.warn("fileName:{} ,size: {}", fileName, fileSize);
            String id = formId != null ? formId : headerId != null ? headerId : queryParamId;
            String index = formIndex != null ? formIndex : headerIndex != null ? headerIndex : queryParamIndex;

            String filename = new String(fileName.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
            UploadResponse uploadResponse = uploadToDocumentService(debug, simulate, id, index, null, in, filename
                    , fileSize);
            uploadResponse.getDoc().getFile().setFilesize(fileSize);
            uploadResponse.getDoc().getFile().setFilename(fileName);
            return uploadResponse;
        } else {
            logger.debug("Failed to fetch file. Server returned HTTP code: {}", responseCode);
            UploadResponse response = new UploadResponse();
            response.setOk(false);
            response.setMessage("Failed to fetch file. ");
            return response;
        }

    }

    /**
     * 获取到文件
     *
     * @param debug
     * @param simulate
     * @param id
     * @param index
     * @param tags
     * @param filecontent
     * @param filename
     * @param fileSize
     * @return
     * @throws IOException
     * @throws NoSuchAlgorithmException
     */
    private UploadResponse uploadToDocumentService(
            String debug,
            String simulate,
            String id,
            String index,
            InputStream tags,
            InputStream filecontent, String filename, long fileSize) throws IOException, NoSuchAlgorithmException {

        logger.debug("uploadToDocumentService({}, {}, {}, {}, ...)", debug, simulate, id, index);

        // Create the Doc object
        Doc doc = new Doc();


        // File
        doc.getFile().setFilename(filename);
        doc.getFile().setExtension(FilenameUtils.getExtension(filename).toLowerCase());
        doc.getFile().setIndexingDate(localDateTimeToDate(LocalDateTime.now()));
        doc.getFile().setFilesize(fileSize);
        // File

        //index
        if (index == null) {
            index = settings.getElasticsearch().getIndex();
        }

        // Path
        if (id == null) {
            if (settings.getFs().isFilenameAsId()) {
                id = filename;
            } else {
                id = SignTool.sign(filename);
            }
        } else if (id.equals("_auto_")) {
            // We are using a specific id which tells us to generate a unique _id like elasticsearch does
            id = TIME_UUID_GENERATOR.getBase64UUID();
        } else {
            logger.debug("get elasticsearch({}, {},  ...)", index, id);
            //有了id值的
            // Elasticsearch entity coordinates (we use the first node address)
            ServerUrl node = settings.getElasticsearch().getNodes().get(0);
            String url = node.getUrl() + "/" + index + "/_doc/" + id;
            try {
                boolean exist = documentService.exists(index, id);
                if (exist) {
                    ESSearchHit esSearchHit = documentService.get(index, id);
                    String source = esSearchHit.getSource();
                    JsonNode tagsNode = mapper.readTree(source);
                    JsonNode docNode = mapper.convertValue(doc, JsonNode.class);
                    JsonNode mergedNode = FsCrawlerUtil.merge(tagsNode, docNode);
                    doc = mapper.treeToValue(mergedNode, Doc.class);
                    UploadResponse response = new UploadResponse();
                    response.setOk(true);
                    response.setFilename(filename);
                    response.setUrl(url);
                    response.setDoc(doc);
                    return response;
                }
            } catch (ElasticsearchClientException e) {
                e.printStackTrace();
            }

        }


        doc.getPath().setVirtual(filename);
        doc.getPath().setReal(filename);
        // Path

        // Read the file content
        TikaDocParser.generate(settings, filecontent, filename, filename, doc, messageDigest, fileSize);

        // Elasticsearch entity coordinates (we use the first node address)
        ServerUrl node = settings.getElasticsearch().getNodes().get(0);
        String url = node.getUrl() + "/" + index + "/_doc/" + id;
        final Doc mergedDoc = this.getMergedJsonDoc(doc, tags);
        if (Boolean.parseBoolean(simulate)) {
            logger.debug("Simulate mode is on, so we skip sending document [{}] to elasticsearch at [{}].", filename,
                    url);
        } else {
            logger.debug("Sending document [{}] to elasticsearch.", filename);
            documentService.index(
                    index,
                    id,
                    mergedDoc,
                    settings.getElasticsearch().getPipeline());
        }

        UploadResponse response = new UploadResponse();
        response.setOk(true);
        response.setFilename(filename);
        response.setUrl(url);

        if (logger.isDebugEnabled() || Boolean.parseBoolean(debug)) {
            // We send the content back if debug is on or if we got in the query explicitly a debug command
            response.setDoc(mergedDoc);
        }

        return response;
    }

    private static String getFileNameFromDisposition(String dispositionHeader) {
        if (dispositionHeader != null && dispositionHeader.contains("filename=")) {
            String contentDisposition = dispositionHeader.substring(dispositionHeader.indexOf("filename=") + 9);
            contentDisposition = contentDisposition.replace("\"", "");
            return contentDisposition;
        }
        return "default_filename.ext";
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
            @FormDataParam("tags") InputStream tags,
            @FormDataParam("file") InputStream filecontent,
            @FormDataParam("file") FormDataContentDisposition d) throws IOException, NoSuchAlgorithmException {
        String id = formId != null ? formId : headerId != null ? headerId : queryParamId;
        String index = formIndex != null ? formIndex : headerIndex != null ? headerIndex : queryParamIndex;
        return uploadToDocumentService(debug, simulate, id, index, tags, filecontent, d);
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
            @FormDataParam("tags") InputStream tags,
            @FormDataParam("file") InputStream filecontent,
            @FormDataParam("file") FormDataContentDisposition d) throws IOException, NoSuchAlgorithmException {
        String index = formIndex != null ? formIndex : headerIndex != null ? headerIndex : queryParamIndex;
        return uploadToDocumentService(debug, simulate, id, index, tags, filecontent, d);
    }

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    public DeleteResponse removeDocument(
            @HeaderParam("filename") String headerFilename,
            @HeaderParam("filename*") String headerFilenameStar,
            @QueryParam("filename") String queryParamFilename,
            @HeaderParam("index") String headerIndex,
            @QueryParam("index") String queryParamIndex) throws NoSuchAlgorithmException {
        String index = headerIndex == null ? queryParamIndex : headerIndex;
        String filename = headerFilename == null ? queryParamFilename : headerFilename;

        // Support for rfc6266: https://datatracker.ietf.org/doc/html/rfc6266#section-5
        if (headerFilenameStar != null) {
            String[] splits = headerFilenameStar.split("''");
            filename = URLDecoder.decode(splits[1], Charset.forName(splits[0]));
        }

        if (filename == null) {
            DeleteResponse response = new DeleteResponse();
            response.setOk(false);
            response.setMessage("We can not delete a document without an id or a filename. " +
                    "Either call DELETE /_document/ID or DELETE /_document?filename=foo.txt");
            return response;
        }

        return removeDocumentInDocumentService(SignTool.sign(filename), filename, index);
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

    private UploadResponse uploadToDocumentService(
            String debug,
            String simulate,
            String id,
            String index,
            InputStream tags,
            InputStream filecontent,
            FormDataContentDisposition d) throws IOException, NoSuchAlgorithmException {

        logger.debug("uploadToDocumentService({}, {}, {}, {}, ...)", debug, simulate, id, index);

        // Create the Doc object
        Doc doc = new Doc();

        if (d == null) {
            UploadResponse response = new UploadResponse();
            response.setOk(false);
            response.setMessage("No file has been sent or you are not using [file] as the field name.");
            return response;
        }

        String filename = new String(d.getFileName().getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
        long filesize = d.getSize();

        // File
        doc.getFile().setFilename(filename);
        doc.getFile().setExtension(FilenameUtils.getExtension(filename).toLowerCase());
        doc.getFile().setIndexingDate(localDateTimeToDate(LocalDateTime.now()));
        doc.getFile().setFilesize(filesize);
        // File

        // Path
        if (id == null) {
            if (settings.getFs().isFilenameAsId()) {
                id = filename;
            } else {
                id = SignTool.sign(filename);
            }
        } else if (id.equals("_auto_")) {
            // We are using a specific id which tells us to generate a unique _id like elasticsearch does
            id = TIME_UUID_GENERATOR.getBase64UUID();
        }

        //index
        if (index == null) {
            index = settings.getElasticsearch().getIndex();
        }

        doc.getPath().setVirtual(filename);
        doc.getPath().setReal(filename);
        // Path

        // Read the file content
        TikaDocParser.generate(settings, filecontent, filename, filename, doc, messageDigest, filesize);

        // Elasticsearch entity coordinates (we use the first node address)
        ServerUrl node = settings.getElasticsearch().getNodes().get(0);
        String url = node.getUrl() + "/" + index + "/_doc/" + id;
        final Doc mergedDoc = this.getMergedJsonDoc(doc, tags);
        if (Boolean.parseBoolean(simulate)) {
            logger.debug("Simulate mode is on, so we skip sending document [{}] to elasticsearch at [{}].", filename,
                    url);
        } else {
            logger.debug("Sending document [{}] to elasticsearch.", filename);
            documentService.index(
                    index,
                    id,
                    mergedDoc,
                    settings.getElasticsearch().getPipeline());
        }

        UploadResponse response = new UploadResponse();
        response.setOk(true);
        response.setFilename(filename);
        response.setUrl(url);

        if (logger.isDebugEnabled() || Boolean.parseBoolean(debug)) {
            // We send the content back if debug is on or if we got in the query explicitly a debug command
            response.setDoc(mergedDoc);
        }

        return response;
    }

    private DeleteResponse removeDocumentInDocumentService(
            String id,
            String filename,
            String index) {
        if (index == null) {
            index = settings.getElasticsearch().getIndex();
        }

        if (id == null && filename == null) {
            DeleteResponse response = new DeleteResponse();
            response.setOk(false);
            response.setMessage("We can not delete a document without an id or a filename. " +
                    "Either call DELETE /_document/ID or DELETE /_document?filename=foo.txt");
            return response;
        }

        logger.debug("Delete document [{}/{}] from elasticsearch using index [{}].", id, filename, index);
        DeleteResponse response = new DeleteResponse();
        try {
            documentService.deleteSingle(index, id);
            response.setOk(true);
            response.setIndex(index);
            response.setId(id);
            response.setFilename(filename);
        } catch (Exception e) {
            response.setOk(false);
            response.setMessage("Can not remove document [" + index + "/" + (filename == null ? id : filename) + "]: "
                    + e.getMessage());
            response.setIndex(index);
            response.setId(id);
            response.setFilename(filename);
        }

        return response;
    }

    private Doc getMergedJsonDoc(Doc doc, InputStream tags) throws BadRequestException {
        if (tags == null) {
            return doc;
        }

        try {
            JsonNode tagsNode = mapper.readTree(tags);
            JsonNode docNode = mapper.convertValue(doc, JsonNode.class);

            JsonNode mergedNode = FsCrawlerUtil.merge(tagsNode, docNode);

            return mapper.treeToValue(mergedNode, Doc.class);
        } catch (Exception e) {
            logger.error("Error parsing tags", e);
            throw new BadRequestException("Error parsing tags", e);
        }
    }
}
