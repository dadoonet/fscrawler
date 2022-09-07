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
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchHit;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.rest.UploadResponse;
import fr.pilato.elasticsearch.crawler.fs.settings.Fs;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.Rest;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractRestITCase;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@SuppressWarnings("ALL")
public class FsCrawlerRestFilenameAsIdIT extends AbstractRestITCase {

    public FsSettings getFsSettings() {
        return FsSettings.builder(getCrawlerName())
                .setFs(Fs.builder().setFilenameAsId(true).build())
                .setRest(new Rest("http://127.0.0.1:" + testRestPort + "/fscrawler"))
                .setElasticsearch(elasticsearchWithSecurity)
                .build();
    }

    @Test
    public void testUploadOneDocument() throws Exception {
        Path from = rootTmpDir.resolve("resources").resolve("documents").resolve("test.txt");
        if (Files.notExists(from)) {
            staticLogger.error("file [{}] should exist before we start tests", from);
            throw new RuntimeException(from + " doesn't seem to exist. Check your JUnit tests.");
        }
        UploadResponse uploadResponse = uploadFile(target, from);
        assertThat(uploadResponse.isOk(), is(true));

        // We wait until we have our document
        ESSearchResponse response = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);
        for (ESSearchHit hit : response.getHits()) {
            assertThat(hit.getId(), is((String) JsonPath.read(hit.getSource(), "$.file.filename")));
        }
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
            assertThat(hit.getId(), is((String) JsonPath.read(hit.getSource(), "$.file.filename")));
        }
    }
}
