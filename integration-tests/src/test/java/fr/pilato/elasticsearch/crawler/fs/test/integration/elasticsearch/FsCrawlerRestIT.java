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

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchHit;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.framework.Version;
import fr.pilato.elasticsearch.crawler.fs.rest.DeleteResponse;
import fr.pilato.elasticsearch.crawler.fs.rest.ServerStatusResponse;
import fr.pilato.elasticsearch.crawler.fs.rest.UploadResponse;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.Rest;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractRestITCase;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@SuppressWarnings("ALL")
public class FsCrawlerRestIT extends AbstractRestITCase {

    public FsSettings getFsSettings() {
        return FsSettings.builder(getCrawlerName())
                .setRest(new Rest("http://127.0.0.1:" + testRestPort + "/fscrawler"))
                .setElasticsearch(elasticsearchWithSecurity)
                .build();
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
            assertThat(JsonPath.read(hit.getSource(), "$.file.extension"), notNullValue());
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
            assertThat(JsonPath.read(hit.getSource(), "$.file.extension"), notNullValue());
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
            assertThat(JsonPath.read(hit.getSource(), "$.file.extension"), notNullValue());
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
            assertThat(JsonPath.read(hit.getSource(), "$.file.extension"), notNullValue());
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
            assertThat(JsonPath.read(hit.getSource(), "$.content"), containsString("This file content will be extracted"));
            assertThat(JsonPath.read(hit.getSource(), "$.file.extension"), notNullValue());
            expectThrows(PathNotFoundException.class, () -> JsonPath.read(hit.getSource(), "$.meta"));
            assertThat(JsonPath.read(hit.getSource(), "$.external.tenantId"), is(23));
            assertThat(JsonPath.read(hit.getSource(), "$.external.company"), is("shoe company"));
            assertThat(JsonPath.read(hit.getSource(), "$.external.daysOpen[0]"), is("Mon"));
            assertThat(JsonPath.read(hit.getSource(), "$.external.daysOpen[4]"), is("Fri"));
            assertThat(JsonPath.read(hit.getSource(), "$.external.products[0].brand"), is("nike"));
            assertThat(JsonPath.read(hit.getSource(), "$.external.products[0].size"), is(41));
            assertThat(JsonPath.read(hit.getSource(), "$.external.products[0].sub"), is("Air MAX"));
            assertThat(JsonPath.read(hit.getSource(), "$.external.products[1].brand"), is("reebok"));
            assertThat(JsonPath.read(hit.getSource(), "$.external.products[1].size"), is(43));
            assertThat(JsonPath.read(hit.getSource(), "$.external.products[1].sub"), is("Pump"));
        });
        checkDocument("replace_content_and_external.txt", hit -> {
            assertThat(JsonPath.read(hit.getSource(), "$.content"), is("OVERWRITTEN CONTENT"));
            assertThat(JsonPath.read(hit.getSource(), "$.file.extension"), notNullValue());
            expectThrows(PathNotFoundException.class, () -> JsonPath.read(hit.getSource(), "$.meta"));
            assertThat(JsonPath.read(hit.getSource(), "$.external.tenantId"), is(23));
            assertThat(JsonPath.read(hit.getSource(), "$.external.company"), is("shoe company"));
            assertThat(JsonPath.read(hit.getSource(), "$.external.daysOpen[0]"), is("Mon"));
            assertThat(JsonPath.read(hit.getSource(), "$.external.daysOpen[4]"), is("Fri"));
            assertThat(JsonPath.read(hit.getSource(), "$.external.products[0].brand"), is("nike"));
            assertThat(JsonPath.read(hit.getSource(), "$.external.products[0].size"), is(41));
            assertThat(JsonPath.read(hit.getSource(), "$.external.products[0].sub"), is("Air MAX"));
            assertThat(JsonPath.read(hit.getSource(), "$.external.products[1].brand"), is("reebok"));
            assertThat(JsonPath.read(hit.getSource(), "$.external.products[1].size"), is(43));
            assertThat(JsonPath.read(hit.getSource(), "$.external.products[1].sub"), is("Pump"));
        });
        checkDocument("replace_content_only.txt", hit -> {
            assertThat(JsonPath.read(hit.getSource(), "$.content"), is("OVERWRITTEN CONTENT"));
            assertThat(JsonPath.read(hit.getSource(), "$.file.extension"), notNullValue());
            expectThrows(PathNotFoundException.class, () -> JsonPath.read(hit.getSource(), "$.meta"));
            expectThrows(PathNotFoundException.class, () -> JsonPath.read(hit.getSource(), "$.external"));
        });
        checkDocument("replace_meta_only.txt", hit -> {
            assertThat(JsonPath.read(hit.getSource(), "$.content"), containsString("This file content will be extracted"));
            assertThat(JsonPath.read(hit.getSource(), "$.meta.raw.resourceName"), is("another-file-name.txt"));
            expectThrows(PathNotFoundException.class, () -> JsonPath.read(hit.getSource(), "$.external"));
        });
    }
}
