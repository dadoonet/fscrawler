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

package fr.pilato.elasticsearch.crawler.fs.test.integration.elasticsearch;

import fr.pilato.elasticsearch.crawler.fs.client.ESSearchHit;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.client.ESTermQuery;
import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.framework.Version;
import fr.pilato.elasticsearch.crawler.fs.rest.DeleteResponse;
import fr.pilato.elasticsearch.crawler.fs.rest.RestServer;
import fr.pilato.elasticsearch.crawler.fs.rest.ServerStatusResponse;
import fr.pilato.elasticsearch.crawler.fs.rest.UploadResponse;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerDocumentService;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerDocumentServiceElasticsearchImpl;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerDocumentServiceWorkplaceSearchImpl;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerManagementServiceElasticsearchImpl;
import fr.pilato.elasticsearch.crawler.fs.settings.FsCrawlerValidator;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.Rest;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractRestITCase;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.file.FileDataBodyPart;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static com.carrotsearch.randomizedtesting.RandomizedTest.rarely;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.copyDirs;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@SuppressWarnings("ALL")
public class FsCrawlerRestIT extends AbstractRestITCase {

    private Path currentTestTagDir;
    private FsCrawlerManagementServiceElasticsearchImpl managementService;
    private FsCrawlerDocumentService documentService;

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
        FsSettings fsSettings = FsSettings.builder(getCrawlerName())
                .setRest(new Rest("http://127.0.0.1:" + testRestPort + "/fscrawler"))
                .setElasticsearch(elasticsearchWithSecurity)
                .build();
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
        managementService.getClient().deleteIndex(getCrawlerName() + "*");

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

    @Test
    public void testCallRoot() {
        ServerStatusResponse status = get("/", ServerStatusResponse.class);
        assertThat(status.getVersion(), is(Version.getVersion()));
        assertThat(status.getElasticsearch(), notNullValue());
    }

    @Test
    public void testUploadAllDocuments() throws Exception {
        Path from = rootTmpDir.resolve("resources").resolve("documents");
        if (Files.notExists(from)) {
            staticLogger.error("directory [{}] should exist before we start tests", from);
            throw new RuntimeException(from + " doesn't seem to exist. Check your JUnit tests.");
        }
        Files.walk(from)
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    UploadResponse response = uploadFile(target, path);
                    assertThat(response.getFilename(), is(path.getFileName().toString()));
                });

        // We wait until we have all docs
        ESSearchResponse response = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), Files.list(from).count(), null, TimeValue
                .timeValueMinutes(2));
        for (ESSearchHit hit : response.getHits()) {
            assertThat(hit.getSourceAsMap(), hasKey("file"));
            assertThat((Map<String, Object>) hit.getSourceAsMap().get("file"), hasKey("extension"));
        }
    }

    @Deprecated
    @Test
    public void testUploadTxtDocumentsWithDeprecatedApi() throws Exception {
        Path from = rootTmpDir.resolve("resources").resolve("documents");
        if (Files.notExists(from)) {
            staticLogger.error("directory [{}] should exist before we start tests", from);
            throw new RuntimeException(from + " doesn't seem to exist. Check your JUnit tests.");
        }
        AtomicInteger number = new AtomicInteger();
        Files.walk(from)
                .filter(Files::isRegularFile)
                .filter(new Predicate<Path>() {
                    @Override
                    public boolean test(Path path) {
                        return path.toString().endsWith("txt");
                    }
                })
                .forEach(path -> {
                    number.getAndIncrement();
                    UploadResponse response = uploadFileUsingApi(target, path, null, null, "/_upload", null);
                    assertThat(response.getFilename(), is(path.getFileName().toString()));
                });

        // We wait until we have all txt docs
        ESSearchResponse response = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), number.longValue(), null, TimeValue
                .timeValueMinutes(2));
        for (ESSearchHit hit : response.getHits()) {
            assertThat(hit.getSourceAsMap(), hasKey("file"));
            assertThat((Map<String, Object>) hit.getSourceAsMap().get("file"), hasKey("extension"));
        }
    }

    @Test
    public void testUploadDocumentWithId() throws Exception {
        Path from = rootTmpDir.resolve("resources").resolve("documents").resolve("test.txt");
        if (Files.notExists(from)) {
            staticLogger.error("file [{}] should exist before we start tests", from);
            throw new RuntimeException(from + " doesn't seem to exist. Check your JUnit tests.");
        }
        UploadResponse uploadResponse = uploadFileWithId(target, from, "1234");
        assertThat(uploadResponse.isOk(), is(true));

        // We wait until we have our document
        ESSearchResponse response = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);
        assertThat(response.getHits().get(0).getId(), is("1234"));
    }

    @Test
    public void testUploadDocumentWithIdUsingPut() throws Exception {
        Path from = rootTmpDir.resolve("resources").resolve("documents").resolve("test.txt");
        if (Files.notExists(from)) {
            staticLogger.error("file [{}] should exist before we start tests", from);
            throw new RuntimeException(from + " doesn't seem to exist. Check your JUnit tests.");
        }
        UploadResponse uploadResponse = putDocument(target, from, null, null, "1234");
        assertThat(uploadResponse.isOk(), is(true));

        // We wait until we have our document
        ESSearchResponse response = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);
        assertThat(response.getHits().get(0).getId(), is("1234"));
    }

    @Test
    public void testDeleteDocumentApi() throws Exception {
        // We need to create first the index
        DeleteResponse deleteResponse = deleteDocument(target, null, "foo", null, "/_document");
        assertThat(deleteResponse.isOk(), is(false));
        assertThat(deleteResponse.getMessage(), startsWith("Can not remove document ["));

        Path from = rootTmpDir.resolve("resources").resolve("documents");
        if (Files.notExists(from)) {
            staticLogger.error("directory [{}] should exist before we start tests", from);
            throw new RuntimeException(from + " doesn't seem to exist. Check your JUnit tests.");
        }
        AtomicInteger number = new AtomicInteger();
        List<String> toBeRemoved = new ArrayList<>();

        Files.walk(from)
                .filter(Files::isRegularFile)
                .filter(new Predicate<Path>() {
                    @Override
                    public boolean test(Path path) {
                        return path.toString().endsWith("txt");
                    }
                })
                .forEach(path -> {
                    number.getAndIncrement();
                    UploadResponse response = uploadFileUsingApi(target, path, null, null, "/_document", null);
                    assertThat(response.getFilename(), is(path.getFileName().toString()));

                    toBeRemoved.add(response.getFilename());
                });

        // We wait until we have all txt docs
        ESSearchResponse response = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), number.longValue(), null, TimeValue
                .timeValueMinutes(2));
        for (ESSearchHit hit : response.getHits()) {
            assertThat(hit.getSourceAsMap(), hasKey("file"));
            assertThat((Map<String, Object>) hit.getSourceAsMap().get("file"), hasKey("extension"));
        }

        // We can now remove all docs
        for (String filename : toBeRemoved) {
            deleteResponse = deleteDocument(target, null, null, filename, "/_document");
            if (!deleteResponse.isOk()) {
                logger.error("{}", deleteResponse.getMessage());
            }
            assertThat(deleteResponse.isOk(), is(true));
        }

        // We wait until we have removed all documents
        response = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 0L, null, TimeValue
                .timeValueMinutes(2));
    }

    @Test
    public void testAllDocumentsWithRestExternalIndex() throws Exception {
        Path from = rootTmpDir.resolve("resources").resolve("documents");
        if (Files.notExists(from)) {
            staticLogger.error("directory [{}] should exist before we start tests", from);
            throw new RuntimeException(from + " doesn't seem to exist. Check your JUnit tests.");
        }
        String index = "fscrawler_fs_custom";
        Files.walk(from)
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    UploadResponse response = uploadFileOnIndex(target, path, index);
                    assertThat(response.getFilename(), is(path.getFileName().toString()));
                });

        // We wait until we have all docs
        ESSearchResponse response = countTestHelper(new ESSearchRequest().withIndex(index), Files.list(from).count(), null, TimeValue
                .timeValueMinutes(2));
        for (ESSearchHit hit : response.getHits()) {
            assertThat(hit.getSourceAsMap(), hasKey("file"));
            assertThat((Map<String, Object>) hit.getSourceAsMap().get("file"), hasKey("extension"));
        }
    }

    @Test
    public void testDocumentWithExternalTags() throws Exception {
        // We iterate over all sample files and we try to locate any existing tag file
        // which can overwrite the data we extracted
        AtomicInteger numFiles = new AtomicInteger();
        Files.walk(currentTestResourceDir)
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    Path tagsFilePath = currentTestTagDir.resolve(path.getFileName().toString() + ".json");
                    logger.debug("Upload file #[{}]: [{}] with tags [{}]", numFiles.incrementAndGet(), path.getFileName(), tagsFilePath.getFileName());
                    UploadResponse response = uploadFileUsingApi(target, path, tagsFilePath, null, "/_document", null);
                    assertThat(response.getFilename(), is(path.getFileName().toString()));
                });

        // We wait until we have all our documents docs
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), numFiles.longValue(), null, TimeValue.timeValueMinutes(2));

        // Let's test every single document that has been enriched
        checkDocument("add_external.txt", hit -> {
            String content = (String) hit.getSourceAsMap().get("content");
            assertThat(content, containsString("This file content will be extracted"));

            assertThat(hit.getSourceAsMap(), hasKey("file"));
            assertThat((Map<String, Object>) hit.getSourceAsMap().get("file"), hasKey("extension"));
            assertThat(hit.getSourceAsMap(), hasKey("meta"));
            assertThat((Map<String, Object>) hit.getSourceAsMap().get("meta"), not(hasKey("raw")));

            assertThat(hit.getSourceAsMap(), hasKey("external"));
            Map<String, Object> external = (Map<String, Object>) hit.getSourceAsMap().get("external");
            assertThat(external, hasKey("tenantId"));
            Integer tenant = (Integer) external.get("tenantId");
            assertThat(tenant, is(23));

            assertThat(external, hasKey("company"));
            String company = (String) external.get("company");
            assertThat(company, is("shoe company"));

            assertThat(external, hasKey("daysOpen"));
            List<String> daysOpen = (List<String>) external.get("daysOpen");
            assertThat(daysOpen.size(), is(5));
            assertThat(daysOpen.get(0), is("Mon"));
            assertThat(daysOpen.get(4), is("Fri"));

            assertThat(external, hasKey("products"));
            List<Object> products = (List<Object>) external.get("products");
            assertThat(products.size(), is(2));

            Map<String, Object> nike = (Map<String, Object>) products.get(0);
            assertThat(nike, hasKey("brand"));
            assertThat(nike, hasKey("size"));
            assertThat(nike, hasKey("sub"));
            String brand = (String) nike.get("brand");
            Integer size = (Integer) nike.get("size");
            String sub = (String) nike.get("sub");
            assertThat(brand, is("nike"));
            assertThat(size, is(41));
            assertThat(sub, is("Air MAX"));
        });
        checkDocument("replace_content_and_external.txt", hit -> {
            String content = (String) hit.getSourceAsMap().get("content");
            assertThat(content, is("OVERWRITTEN CONTENT"));

            assertThat(hit.getSourceAsMap(), hasKey("file"));
            assertThat((Map<String, Object>) hit.getSourceAsMap().get("file"), hasKey("extension"));
            assertThat(hit.getSourceAsMap(), hasKey("meta"));
            assertThat((Map<String, Object>) hit.getSourceAsMap().get("meta"), not(hasKey("raw")));

            assertThat(hit.getSourceAsMap(), hasKey("external"));
            Map<String, Object> external = (Map<String, Object>) hit.getSourceAsMap().get("external");
            assertThat(external, hasKey("tenantId"));
            Integer tenant = (Integer) external.get("tenantId");
            assertThat(tenant, is(23));

            assertThat(external, hasKey("company"));
            String company = (String) external.get("company");
            assertThat(company, is("shoe company"));

            assertThat(external, hasKey("daysOpen"));
            List<String> daysOpen = (List<String>) external.get("daysOpen");
            assertThat(daysOpen.size(), is(5));
            assertThat(daysOpen.get(0), is("Mon"));
            assertThat(daysOpen.get(4), is("Fri"));

            assertThat(external, hasKey("products"));
            List<Object> products = (List<Object>) external.get("products");
            assertThat(products.size(), is(2));

            Map<String, Object> nike = (Map<String, Object>) products.get(0);
            assertThat(nike, hasKey("brand"));
            assertThat(nike, hasKey("size"));
            assertThat(nike, hasKey("sub"));
            String brand = (String) nike.get("brand");
            Integer size = (Integer) nike.get("size");
            String sub = (String) nike.get("sub");
            assertThat(brand, is("nike"));
            assertThat(size, is(41));
            assertThat(sub, is("Air MAX"));
        });
        checkDocument("replace_content_only.txt", hit -> {
            String content = (String) hit.getSourceAsMap().get("content");
            assertThat(content, is("OVERWRITTEN CONTENT"));

            assertThat(hit.getSourceAsMap(), hasKey("file"));
            assertThat((Map<String, Object>) hit.getSourceAsMap().get("file"), hasKey("extension"));
            assertThat(hit.getSourceAsMap(), hasKey("meta"));
            assertThat((Map<String, Object>) hit.getSourceAsMap().get("meta"), not(hasKey("raw")));

            assertThat(hit.getSourceAsMap(), not(hasKey("external")));
        });
        checkDocument("replace_meta_only.txt", hit -> {
            String content = (String) hit.getSourceAsMap().get("content");
            assertThat(content, containsString("This file content will be extracted"));

            assertThat(hit.getSourceAsMap(), hasKey("meta"));
            Map<String, Object> meta = (Map<String, Object>) hit.getSourceAsMap().get("meta");
            assertThat(meta, hasKey("raw"));
            Map<String, Object> raw = (Map<String, Object>) meta.get("raw");
            assertThat(raw, hasKey("resourceName"));
            assertThat(raw.get("resourceName"), is("another-file-name.txt"));

            assertThat(hit.getSourceAsMap(), not(hasKey("external")));
        });
    }

    private void checkDocument(String filename, HitChecker checker) throws IOException {
        ESSearchResponse response = documentService.search(new ESSearchRequest()
                .withIndex(getCrawlerName())
                .withESQuery(new ESTermQuery("file.filename", filename)));

        assertThat(response.getTotalHits(), is(1L));
        ESSearchHit hit = response.getHits().get(0);
        logger.debug("For [file.filename:{}], we got: {}", filename, hit.getSource());

        checker.check(hit);
    }

    private interface HitChecker {
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
