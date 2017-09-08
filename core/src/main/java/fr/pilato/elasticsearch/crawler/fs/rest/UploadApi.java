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


import fr.pilato.elasticsearch.crawler.fs.SignTool;
import fr.pilato.elasticsearch.crawler.fs.meta.doc.Doc;
import fr.pilato.elasticsearch.crawler.fs.meta.doc.DocParser;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.Elasticsearch;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.tika.TikaDocParser;
import fr.pilato.elasticsearch.crawler.fs.util.TimeBasedUUIDGenerator;
import org.elasticsearch.action.bulk.BulkProcessor;
import org.elasticsearch.common.xcontent.XContentType;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.buildUrl;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.localDateTimeToDate;

@Path("/_upload")
public class UploadApi extends RestApi {

    private final BulkProcessor bulkProcessor;
    private final FsSettings settings;
    private final MessageDigest messageDigest;
    private static final TimeBasedUUIDGenerator TIME_UUID_GENERATOR = new TimeBasedUUIDGenerator();

    public UploadApi(FsSettings settings, BulkProcessor bulkProcessor) {
        this.settings = settings;
        this.bulkProcessor = bulkProcessor;
        // Create MessageDigest instance
        try {
            messageDigest = settings.getFs().getChecksum() == null ?
                    null : MessageDigest.getInstance(settings.getFs().getChecksum());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("This should never happen as we checked that previously");
        }
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public UploadResponse post(
            @QueryParam("debug") String debug,
            @QueryParam("simulate") String simulate,
            @FormDataParam("id") String id,
            @FormDataParam("file") InputStream filecontent,
            @FormDataParam("file") FormDataContentDisposition d) throws IOException, NoSuchAlgorithmException {

        // Create the Doc object
        Doc doc = new Doc();

        String filename = new String(d.getFileName().getBytes("ISO-8859-1"), "UTF-8");
        long filesize = d.getSize();

        // File
        doc.getFile().setFilename(filename);
        doc.getFile().setIndexingDate(localDateTimeToDate(LocalDateTime.now()));
        // File

        // Path
        if (id == null) {
            id = SignTool.sign(filename);
        } else if (id.equals("_auto_")) {
            // We are using a specific id which tells us to generate a unique _id like elasticsearch does
            id = TIME_UUID_GENERATOR.getBase64UUID();
        }

        doc.getPath().setVirtual(filename);
        doc.getPath().setReal(filename);
        // Path

        // Read the file content
        TikaDocParser.generate(settings, filecontent, filename, doc, messageDigest, filesize);

        String url = null;
        if (Boolean.parseBoolean(simulate)) {
            logger.debug("Simulate mode is on, so we skip sending document [{}] to elasticsearch.", filename);
        } else {
            logger.debug("Sending document [{}] to elasticsearch.", filename);
            bulkProcessor.add(new org.elasticsearch.action.index.IndexRequest(settings.getElasticsearch().getIndex(), "doc", id)
                    .source(DocParser.toJson(doc), XContentType.JSON));
            // Elasticsearch entity coordinates (we use the first node address)
            Elasticsearch.Node node = settings.getElasticsearch().getNodes().get(0);
            url = buildUrl(
                    node.getScheme().toLowerCase(), node.getHost(), node.getPort()) + "/" +
                    settings.getElasticsearch().getIndex() + "/" +
                    "doc" + "/" +
                    id;
        }

        UploadResponse response = new UploadResponse();
        response.setOk(true);
        response.setFilename(filename);
        response.setUrl(url);

        if (logger.isDebugEnabled() || Boolean.parseBoolean(debug)) {
            // We send the content back if debug is on or if we got in the query explicitly a debug command
            response.setDoc(doc);
        }

        return response;
    }
}
