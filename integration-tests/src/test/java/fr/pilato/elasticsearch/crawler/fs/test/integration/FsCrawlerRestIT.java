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
import org.elasticsearch.search.SearchHit;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.file.FileDataBodyPart;
import org.junit.Test;

import javax.ws.rs.core.MediaType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;

public class FsCrawlerRestIT extends AbstractRestITCase {

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
                    UploadResponse response = uploadFile(path);
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
    public void testDocumentWithCustomTagsAndWithRest() throws Exception {
        Path from = rootTmpDir.resolve("resources").resolve("tags");
        if (Files.notExists(from)) {
            staticLogger.error("directory [{}] should exist before we start tests", from);
            throw new RuntimeException(from + " doesn't seem to exist. Check your JUnit tests.");
        }

        Path tagsFilePath = from.resolve("fscrawler-test-upload-with-tags.txt").toAbsolutePath();

        Files.walk(from)
                .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().contains("fscrawler-test-upload-with-tags.txt"))
                .limit(1)
                .forEach(path -> {
                    UploadResponse response = uploadFile(path, tagsFilePath);
                    assertThat(response.getFilename(), is(path.getFileName().toString()));
                });

        // We wait until we have one docs
        SearchResponse response = countTestHelper(new SearchRequest(getCrawlerName()), 1L, null, TimeValue
                .timeValueMinutes(2));
        for (SearchHit hit : response.getHits()) {
            String content = (String) hit.getSourceAsMap().get("content");
            assertThat(content, is("OVERWRITTEN CONTENT"));

            assertThat(hit.getSourceAsMap(), hasKey("file"));
            assertThat((Map<String, Object>) hit.getSourceAsMap().get("file"), hasKey("extension"));
            assertThat((Map<String, Object>) hit.getSourceAsMap().get("meta"), hasKey("raw"));

            assertThat(hit.getSourceAsMap(), hasKey("external"));
            Map<String, Object> external = (Map<String, Object>) hit.getSourceAsMap().get("external");
            assertThat(external, hasKey("tenant"));
            Integer tenant = (Integer) external.get("tenant");
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
        }
    }

    private static final Map<String, Object> debugOption = new HashMap<>();

    static {
        debugOption.put("debug", true);
        debugOption.put("simulate", true);
    }

    public static UploadResponse uploadFile(Path file) {
        return uploadFile(file, null);
    }

    public static UploadResponse uploadFile(Path file, Path tagsFile) {
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
            staticLogger.debug("Rest response: {}", restCall("/_upload", mp, String.class, debugOption));
        }

        return restCall("/_upload", mp, UploadResponse.class, Collections.emptyMap());
    }
}
