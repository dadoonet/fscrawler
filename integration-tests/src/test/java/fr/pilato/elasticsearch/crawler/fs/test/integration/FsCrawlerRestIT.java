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

import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.framework.Version;
import fr.pilato.elasticsearch.crawler.fs.rest.ServerStatusResponse;
import fr.pilato.elasticsearch.crawler.fs.rest.UploadResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.file.FileDataBodyPart;
import org.junit.Before;
import org.junit.Test;

import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.copyDirs;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

public class FsCrawlerRestIT extends AbstractRestITCase {

    private Path currentTestTagDir;

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

    @Test
    public void testCallRoot() {
        ServerStatusResponse status = restCall("/", ServerStatusResponse.class);
        assertThat(status.getVersion(), is(Version.getVersion()));
        assertThat(status.getElasticsearch(), notNullValue());
    }

    @Test
    public void testAllDocumentsWithRest() throws Exception {
        Path from = rootTmpDir.resolve("resources").resolve("documents");
        if (Files.notExists(from)) {
            staticLogger.error("directory [{}] should exist before wa start tests", from);
            throw new RuntimeException(from + " doesn't seem to exist. Check your JUnit tests.");
        }
        Files.walk(from)
                .filter(path -> Files.isRegularFile(path))
                .forEach(path -> {
                    UploadResponse response = uploadFile(target, path);
                    assertThat(response.getFilename(), is(path.getFileName().toString()));
                });

        // We wait until we have all docs
        SearchResponse response = countTestHelper(new SearchRequest(getCrawlerName()), Files.list(from).count(), null, TimeValue
                .timeValueMinutes(2));
        for (SearchHit hit : response.getHits()) {
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
                .filter(path -> Files.isRegularFile(path))
                .forEach(path -> {
                    Path tagsFilePath = currentTestTagDir.resolve(path.getFileName().toString() + ".json");
                    logger.debug("Upload file #[{}]: [{}] with tags [{}]", numFiles.incrementAndGet(), path.getFileName(), tagsFilePath.getFileName());
                    UploadResponse response = uploadFile(target, path, tagsFilePath);
                    assertThat(response.getFilename(), is(path.getFileName().toString()));
                });

        // We wait until we have all our documents docs
        countTestHelper(new SearchRequest(getCrawlerName()), numFiles.longValue(), null, TimeValue.timeValueMinutes(2));

        // Let's test every single document that has been enriched
        checkDocument("add_external.txt", hit -> {
            String content = (String) hit.getSourceAsMap().get("content");
            assertThat(content, containsString("This file content will be extracted"));

            assertThat(hit.getSourceAsMap(), hasKey("file"));
            assertThat((Map<String, Object>) hit.getSourceAsMap().get("file"), hasKey("extension"));
            assertThat(hit.getSourceAsMap(), hasKey("meta"));
            assertThat((Map<String, Object>) hit.getSourceAsMap().get("meta"), hasKey("raw"));

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
            assertThat((Map<String, Object>) hit.getSourceAsMap().get("meta"), hasKey("raw"));

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
            assertThat((Map<String, Object>) hit.getSourceAsMap().get("meta"), hasKey("raw"));

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
        SearchResponse response = elasticsearchClient.search(new SearchRequest(getCrawlerName()).source(new SearchSourceBuilder().query(
                QueryBuilders.termQuery("file.filename", filename)
        )), RequestOptions.DEFAULT);

        assertThat(response.getHits().totalHits, is(1L));
        SearchHit hit = response.getHits().getHits()[0];
        logger.debug("For [file.filename:{}], we got: {}", filename, hit.getSourceAsString());

        checker.check(hit);
    }

    private interface HitChecker {
        void check(SearchHit hit);
    }

    private static final Map<String, Object> debugOption = new HashMap<>();

    static {
        debugOption.put("debug", true);
        debugOption.put("simulate", true);
    }

    public static UploadResponse uploadFile(WebTarget target, Path file) {
        return uploadFile(target, file, null);
    }

    public static UploadResponse uploadFile(WebTarget target, Path file, Path tagsFile) {
        assertThat(Files.exists(file), is(true));

        // MediaType of the body part will be derived from the file.
        FileDataBodyPart filePart = new FileDataBodyPart("file", file.toFile(), MediaType.APPLICATION_OCTET_STREAM_TYPE);
        FormDataMultiPart mp = new FormDataMultiPart();
        mp.bodyPart(filePart);

        if (tagsFile != null) {
            FileDataBodyPart tagsFilePart = new FileDataBodyPart("tags", tagsFile.toFile(), MediaType.APPLICATION_OCTET_STREAM_TYPE);
            mp.bodyPart(tagsFilePart);
        }

        if (staticLogger.isDebugEnabled()) {
            staticLogger.debug("Rest response: {}", restCall(target, "/_upload", mp, String.class, debugOption));
        }

        return restCall(target, "/_upload", mp, UploadResponse.class, Collections.emptyMap());
    }
}
