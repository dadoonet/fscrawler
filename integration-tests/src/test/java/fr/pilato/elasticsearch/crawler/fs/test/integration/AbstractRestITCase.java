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

package fr.pilato.elasticsearch.crawler.fs.test.integration;

import fr.pilato.elasticsearch.crawler.fs.client.ESSearchHit;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.client.ESTermQuery;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClientException;
import fr.pilato.elasticsearch.crawler.fs.rest.DeleteResponse;
import fr.pilato.elasticsearch.crawler.fs.rest.RestJsonProvider;
import fr.pilato.elasticsearch.crawler.fs.rest.RestServer;
import fr.pilato.elasticsearch.crawler.fs.rest.UploadResponse;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerDocumentService;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerDocumentServiceElasticsearchImpl;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerDocumentServiceWorkplaceSearchImpl;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerManagementServiceElasticsearchImpl;
import fr.pilato.elasticsearch.crawler.fs.settings.FsCrawlerValidator;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.media.multipart.file.FileDataBodyPart;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static com.carrotsearch.randomizedtesting.RandomizedTest.rarely;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.INDEX_SUFFIX_FOLDER;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.copyDirs;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public abstract class AbstractRestITCase extends AbstractITCase {

    protected static WebTarget target;
    protected static Client client;

    protected Path currentTestTagDir;
    private FsCrawlerManagementServiceElasticsearchImpl managementService;
    protected FsCrawlerDocumentService documentService;

    public abstract FsSettings getFsSettings();
    @Before
    public void copyTags() throws IOException {
        Path testResourceTarget = rootTmpDir.resolve("resources");
        if (Files.notExists(testResourceTarget)) {
            Files.createDirectory(testResourceTarget);
        }

        String currentTestName = getCurrentTestName();
        // We copy files from the src dir to the temp dir
        String url = getUrl("tags", currentTestName);
        Path from = Paths.get(url);

        currentTestTagDir = testResourceTarget.resolve(currentTestName + ".tags");
        if (Files.exists(from)) {
            staticLogger.debug("  --> Copying test resources from [{}]", from);
            copyDirs(from, currentTestTagDir);
            staticLogger.debug("  --> Tags ready in [{}]", currentTestTagDir);
        }
    }

    @Before
    public void startRestServer() throws Exception {
        FsSettings fsSettings = getFsSettings();
        fsSettings.getElasticsearch().setIndex(getCrawlerName());
        FsCrawlerValidator.validateSettings(logger, fsSettings, true);

        this.managementService = new FsCrawlerManagementServiceElasticsearchImpl(metadataDir, fsSettings);

        if (fsSettings.getWorkplaceSearch() == null) {
            // The documentService is using the esSearch instance
            this.documentService = new FsCrawlerDocumentServiceElasticsearchImpl(metadataDir, fsSettings);
        } else {
            // The documentService is using the wpSearch instance
            this.documentService = new FsCrawlerDocumentServiceWorkplaceSearchImpl(metadataDir, fsSettings);
        }

        managementService.start();
        documentService.start();

        RestServer.start(fsSettings, managementService, documentService);

        logger.info(" -> Removing existing index [{}]", getCrawlerName() + "*");
        managementService.getClient().deleteIndex(getCrawlerName());
        managementService.getClient().deleteIndex(getCrawlerName() + INDEX_SUFFIX_FOLDER);

        logger.info(" -> Creating index [{}]", fsSettings.getElasticsearch().getIndex());
    }

    @After
    public void stopRestServer() throws IOException {
        RestServer.close();
        if (managementService != null) {
            managementService.close();
            managementService = null;
        }
        if (documentService != null) {
            documentService.close();
            documentService = null;
        }
    }

    public static <T> T get(String path, Class<T> clazz) {
        if (staticLogger.isDebugEnabled()) {
            String response = target.path(path).request().get(String.class);
            staticLogger.debug("Rest response: {}", response);
        }
        return target.path(path).request().get(clazz);
    }

    public static <T> T post(WebTarget target, String path, FormDataMultiPart mp, Class<T> clazz, Map<String, Object> params) {
        WebTarget targetPath = target.path(path);
        // TODO check this as it does not seem to produce anything
        params.forEach(targetPath::queryParam);

        return targetPath.request(MediaType.MULTIPART_FORM_DATA)
                .accept(MediaType.APPLICATION_JSON)
                .post(Entity.entity(mp, mp.getMediaType()), clazz);
    }

    public static <T> T put(WebTarget target, String path, FormDataMultiPart mp, Class<T> clazz, Map<String, Object> params) {
        WebTarget targetPath = target.path(path);
        // TODO check this as it does not seem to produce anything
        params.forEach(targetPath::queryParam);

        return targetPath.request(MediaType.MULTIPART_FORM_DATA)
                .accept(MediaType.APPLICATION_JSON)
                .put(Entity.entity(mp, mp.getMediaType()), clazz);
    }

    public static <T> T delete(WebTarget target, String path, Class<T> clazz, Map<String, Object> params) {
        WebTarget targetPath = target.path(path);
        Invocation.Builder builder = targetPath.request();
        // Sadly headers by default only support ISO-8859-1 and not UTF-8: https://www.jmix.io/cuba-blog/utf-8-in-http-headers/
        // So we need to hack around this and support rfc6266 https://datatracker.ietf.org/doc/html/rfc6266#section-5
        params.forEach((k, v) -> {
            builder.header(k, v);
            builder.header(k + "*", "UTF-8''" + URLEncoder.encode((String) v, StandardCharsets.UTF_8));
        });

        // params.forEach(builder::property);
        return builder.delete(clazz);
    }

    @BeforeClass
    public static void startRestClient() {
        // create the client
        client = ClientBuilder.newBuilder()
                .register(MultiPartFeature.class)
                .register(RestJsonProvider.class)
                .register(JacksonFeature.class)
                .build();

        target = client.target("http://127.0.0.1:" + testRestPort + "/fscrawler");
    }

    @AfterClass
    public static void stopRestClient() {
        if (client != null) {
            client.close();
            client = null;
        }
    }

    protected void checkDocument(String filename, HitChecker checker) throws IOException, ElasticsearchClientException {
        ESSearchResponse response = documentService.search(new ESSearchRequest()
                .withIndex(getCrawlerName())
                .withESQuery(new ESTermQuery("file.filename", filename)));

        assertThat(response.getTotalHits(), is(1L));
        ESSearchHit hit = response.getHits().get(0);
        logger.debug("For [file.filename:{}], we got: {}", filename, hit.getSource());

        checker.check(hit);
    }

    protected interface HitChecker {
        void check(ESSearchHit hit);
    }

    private static final Map<String, Object> debugOption = new HashMap<>();

    static {
        debugOption.put("debug", true);
        debugOption.put("simulate", true);
    }

    public static UploadResponse uploadFile(WebTarget target, Path file) {
        return uploadFileUsingApi(target, file, null, null, null, null);
    }

    public static UploadResponse uploadFileOnIndex(WebTarget target, Path file, String index) {
        return uploadFileUsingApi(target, file, null, index, null, null);
    }

    public static UploadResponse uploadFileWithId(WebTarget target, Path file, String id) {
        return uploadFileUsingApi(target, file, null, null, null, id);
    }

    public static UploadResponse uploadFile(WebTarget target, Path file, Path tagsFile, String index) {
        return uploadFileUsingApi(target, file, tagsFile, index, null, null);
    }

    public static UploadResponse uploadFileUsingApi(WebTarget target, Path file, Path tagsFile, String index, String api, String id) {
        assertThat(Files.exists(file), is(true));

        Map<String, Object> params = new HashMap<>();

        if (api == null) {
            api = "/_document";
        }

        // MediaType of the body part will be derived from the file.
        FileDataBodyPart filePart = new FileDataBodyPart("file", file.toFile(), MediaType.APPLICATION_OCTET_STREAM_TYPE);
        FormDataMultiPart mp = new FormDataMultiPart();
        mp.bodyPart(filePart);

        if (index != null) {
            mp.field("index", index);
            // Sadly this does not work
            /*
            if (rarely()) {
                staticLogger.info("Force index name to {} using a form field", index);
                mp.field("index", index);
            } else {
                staticLogger.info("Force index name to {} using a query string parameter", index);
                params.put("index", index);
            }
            */
        }

        if (id != null) {
            mp.field("id", id);
            // Sadly this does not work
            /*
            if (rarely()) {
                staticLogger.info("Force id to {} using a form field", id);
                mp.field("id", id);
            } else {
                staticLogger.info("Force id to {} using a query string parameter", id);
                params.put("id", id);
            }
             */
        }

        if (tagsFile != null) {
            FileDataBodyPart tagsFilePart = new FileDataBodyPart("tags", tagsFile.toFile(), MediaType.APPLICATION_OCTET_STREAM_TYPE);
            mp.bodyPart(tagsFilePart);
        }

        if (staticLogger.isDebugEnabled()) {
            staticLogger.debug("Rest response: {}", post(target, api, mp, String.class, debugOption));
        }

        return post(target, api, mp, UploadResponse.class, params);
    }

    public static UploadResponse putDocument(WebTarget target, Path file, Path tagsFile, String index, String id) {
        assertThat(Files.exists(file), is(true));

        Map<String, Object> params = new HashMap<>();

        // MediaType of the body part will be derived from the file.
        FileDataBodyPart filePart = new FileDataBodyPart("file", file.toFile(), MediaType.APPLICATION_OCTET_STREAM_TYPE);
        FormDataMultiPart mp = new FormDataMultiPart();
        mp.bodyPart(filePart);

        if (index != null) {
            if (rarely()) {
                staticLogger.info("Force index name to {} using a form field", index);
                mp.field("index", index);
            } else {
                staticLogger.info("Force index name to {} using a query string parameter", index);
                params.put("index", index);
            }
        }

        if (tagsFile != null) {
            FileDataBodyPart tagsFilePart = new FileDataBodyPart("tags", tagsFile.toFile(), MediaType.APPLICATION_OCTET_STREAM_TYPE);
            mp.bodyPart(tagsFilePart);
        }

        return put(target, "/_document/" + id, mp, UploadResponse.class, params);
    }

    public static DeleteResponse deleteDocument(WebTarget target, String index, String id, String filename, String api) {
        if (id != null) {
            api = api + "/" + id;
            staticLogger.info("Using id {}. Api is now {}", id, api);
        }

        Map<String, Object> options = new HashMap<>();

        if (index != null) {
            staticLogger.info("Using index {}", index);
            options.put("index", index);
        }

        if (filename != null) {
            staticLogger.info("Using filename {}", filename);
            options.put("filename", filename);
        }

        return delete(target, api, DeleteResponse.class, options);
    }
}
