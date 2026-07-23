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
package fr.pilato.elasticsearch.crawler.fs.test.integration.elasticsearch;

import com.jayway.jsonpath.JsonPath;
import fr.pilato.elasticsearch.crawler.fs.client.ESBoolQuery;
import fr.pilato.elasticsearch.crawler.fs.client.ESMatchQuery;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchHit;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.client.ESTermQuery;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil;
import fr.pilato.elasticsearch.crawler.fs.settings.ChainedPasswordProviderSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.DiskPasswordProviderSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.PasswordProviders;
import fr.pilato.elasticsearch.crawler.fs.settings.Passwords;
import fr.pilato.elasticsearch.crawler.fs.settings.StaticPasswordProviderSettings;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class FsCrawlerTestPasswordIT extends AbstractFsCrawlerITCase {
    private static final String PROTECTED_DOCUMENT_TEXT = "This is a sample text available in page";
    private static final String PROTECTED_DOCX_PASSWORD = "david";
    private static final String PROTECTED_PDF_PASSWORD = "pdfpassword";

    @Test
    void crawlProtectedDocumentWithExternalDiskMirrorAndStaticFallback() throws Exception {
        Path crawlDir = Files.createDirectories(testTmpDir.resolve("crawl"));
        Path crawlNestedDir = Files.createDirectories(crawlDir.resolve("nested"));
        Path protectedDocument = crawlNestedDir.resolve("test-protected.pdf");
        Files.copy(testDocumentsDir.resolve("test-protected.pdf"), protectedDocument);

        Path passwordMirrorDir = Files.createDirectories(testTmpDir.resolve("password-mirror"));
        Path passwordMirrorNestedDir = Files.createDirectories(passwordMirrorDir.resolve("nested"));
        Files.writeString(passwordMirrorNestedDir.resolve("test-protected.pdf.password"), PROTECTED_PDF_PASSWORD);

        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setUrl(crawlDir.toString());
        configureChainedPasswords(fsSettings, passwordMirrorDir, List.of(PROTECTED_DOCX_PASSWORD));

        crawler = startCrawler(fsSettings);

        ESSearchResponse response = countTestHelper(
                new ESSearchRequest()
                        .withIndex(getCrawlerName() + FsCrawlerUtil.INDEX_SUFFIX_DOCS)
                        .withESQuery(new ESBoolQuery()
                                .addMust(new ESTermQuery("file.filename", "test-protected.pdf"))
                                .addMust(new ESMatchQuery("content", PROTECTED_DOCUMENT_TEXT))),
                1L,
                crawlDir,
                MAX_WAIT_FOR_SEARCH);

        ESSearchHit hit = findHitByFilename(response, "test-protected.pdf");
        Assertions.assertThat((String) JsonPath.read(hit.getSource(), "$.content"))
                .contains(PROTECTED_DOCUMENT_TEXT);
    }

    @Test
    void passwordSidecarIsNotIndexed() throws Exception {
        Path crawlDir = Files.createDirectories(testTmpDir.resolve("crawl"));
        Path document = crawlDir.resolve("foo.txt");
        Files.writeString(document, "This file contains some words.");
        Files.writeString(crawlDir.resolve("foo.txt.password"), PROTECTED_DOCX_PASSWORD);

        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setUrl(crawlDir.toString());

        crawler = startCrawler(fsSettings);

        ESSearchResponse response = countTestHelper(
                new ESSearchRequest().withIndex(getCrawlerName() + FsCrawlerUtil.INDEX_SUFFIX_DOCS), 1L, crawlDir);
        ESSearchHit hit = response.getHits().get(0);
        Assertions.assertThat((String) JsonPath.read(hit.getSource(), "$.file.filename"))
                .isEqualTo("foo.txt");
        Assertions.assertThat((String) JsonPath.read(hit.getSource(), "$.content"))
                .contains("some words");

        countTestHelper(
                new ESSearchRequest()
                        .withIndex(getCrawlerName() + FsCrawlerUtil.INDEX_SUFFIX_DOCS)
                        .withESQuery(new ESTermQuery("file.filename", "foo.txt.password")),
                0L,
                crawlDir);
    }

    private void configureChainedPasswords(
            FsSettings fsSettings, Path passwordMirrorDir, List<String> staticFallbackPasswords) {
        Passwords passwords = new Passwords();
        passwords.setProvider("chained");

        DiskPasswordProviderSettings diskSettings = new DiskPasswordProviderSettings();
        diskSettings.setUrl(passwordMirrorDir.toString());

        StaticPasswordProviderSettings staticSettings = new StaticPasswordProviderSettings();
        staticSettings.setValues(staticFallbackPasswords);

        ChainedPasswordProviderSettings chainedSettings = new ChainedPasswordProviderSettings();
        chainedSettings.setProviders(List.of("disk", "static"));

        PasswordProviders providers = new PasswordProviders();
        providers.setDisk(diskSettings);
        providers.setStatic(staticSettings);
        providers.setChained(chainedSettings);

        passwords.setProviders(providers);
        fsSettings.setPasswords(passwords);
    }
}
