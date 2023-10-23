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

import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerDocumentService;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;

import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;

@Path("/_upload")
@Deprecated
public class UploadApi extends RestApi {

    private final DocumentApi documentApi;

    UploadApi(FsSettings settings, FsCrawlerDocumentService documentService) {
        documentApi = new DocumentApi(settings, documentService);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public UploadResponse post(
            @QueryParam("debug") String debug,
            @QueryParam("simulate") String simulate,
            @FormDataParam("id") String id,
            @FormDataParam("index") String index,
            @FormDataParam("tags") InputStream tags,
            @FormDataParam("file") InputStream filecontent,
            @FormDataParam("file") FormDataContentDisposition d) throws IOException, NoSuchAlgorithmException {
        logger.warn("This API has been deprecated by /_document. Please use the new API instead.");
        return documentApi.addDocument(debug, simulate, id, index, null, null, null, null, tags, filecontent, d);
    }
}
