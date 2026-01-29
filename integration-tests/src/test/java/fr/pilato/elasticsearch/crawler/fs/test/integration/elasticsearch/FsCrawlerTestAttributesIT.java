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

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.PathNotFoundException;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchHit;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.framework.OsValidator;
import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.INDEX_SUFFIX_DOCS;
import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.parseJsonAsDocumentContext;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.junit.Assume.assumeTrue;

/**
 * Test attributes crawler settings
 */
public class FsCrawlerTestAttributesIT extends AbstractFsCrawlerITCase {
    @Test
    public void attributes() throws Exception {
        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setAttributesSupport(true);
        crawler = startCrawler(fsSettings);
        ESSearchResponse searchResponse = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName() + INDEX_SUFFIX_DOCS), 1L, null);
        for (ESSearchHit hit : searchResponse.getHits()) {
            DocumentContext document = parseJsonAsDocumentContext(hit.getSource());
            assertThat((String) document.read("$.attributes.owner")).isNotEmpty();
            if (OsValidator.WINDOWS) {
                // We should not have values for group and permissions on Windows OS
                assertThatThrownBy(() -> document.read("$.attributes.group")).isInstanceOf(PathNotFoundException.class);
                assertThat((Integer) document.read("$.attributes.permissions")).isEqualTo(0);
            } else {
                // We test group and permissions only on non Windows OS
                assertThat((String) document.read("$.attributes.group")).isNotEmpty();
                assertThat((Integer) document.read("$.attributes.permissions")).isGreaterThanOrEqualTo(400);
            }
        }
    }

    @Test
    public void aclAttributes() throws Exception {
        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setAttributesSupport(true);
        fsSettings.getFs().setAclSupport(true);
        crawler = startCrawler(fsSettings);

        ESSearchResponse searchResponse = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);
        for (ESSearchHit hit : searchResponse.getHits()) {
            DocumentContext document = parseJsonAsDocumentContext(hit.getSource());

            List<?> aclEntries;
            try {
                aclEntries = document.read("$.attributes.acl");
            } catch (PathNotFoundException e) {
                aclEntries = null;
            }

            if (OsValidator.WINDOWS) {
                assertThat(aclEntries).as("ACL metadata should be collected on Windows").isNotNull().isNotEmpty();
                assertThat((String) document.read("$.attributes.acl[0].principal")).isNotBlank();
                assertThat((String) document.read("$.attributes.acl[0].type")).isNotBlank();
            } else {
                assertThat(aclEntries).as("ACL metadata should not be present when the platform does not expose ACLs").isNullOrEmpty();
            }
        }
    }

    @Test
    public void aclChangeTriggersReindex() throws Exception {
        Path file = currentTestResourceDir.resolve("roottxtfile.txt");
        AclFileAttributeView aclView = Files.getFileAttributeView(file, AclFileAttributeView.class);
        assumeTrue("ACL change tests require an ACL-capable filesystem", aclView != null);

        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setAttributesSupport(true);
        fsSettings.getFs().setAclSupport(true);
        fsSettings.getFs().setUpdateRate(TimeValue.timeValueSeconds(1));

        crawler = startCrawler(fsSettings);

        DocumentContext initialDocument = fetchSingleDocument(fsSettings);
        int initialAclSize = readAclSize(initialDocument);
        String initialIndexingDate = initialDocument.read("$.file.indexing_date");

        addCustomAclEntry(file);

        AtomicReference<DocumentContext> updatedDocument = new AtomicReference<>();
        await().atMost(60, SECONDS)
                .alias("Document should be reindexed when ACL metadata changes")
                .until(() -> {
                    try {
                        DocumentContext current = fetchSingleDocument(fsSettings);
                        updatedDocument.set(current);
                        String currentIndexingDate = current.read("$.file.indexing_date");
                        return !Objects.equals(initialIndexingDate, currentIndexingDate);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

        assertThat(readAclSize(updatedDocument.get())).isGreaterThan(initialAclSize);
    }

    private DocumentContext fetchSingleDocument(FsSettings fsSettings) throws Exception {
        refresh(fsSettings.getElasticsearch().getIndex());
        ESSearchResponse response = client.search(new ESSearchRequest()
                .withIndex(fsSettings.getElasticsearch().getIndex())
                .withSize(1));
        assertThat(response.getHits()).isNotEmpty();
        return parseJsonAsDocumentContext(response.getHits().get(0).getSource());
    }

    private void addCustomAclEntry(Path file) throws IOException {
        AclFileAttributeView view = Files.getFileAttributeView(file, AclFileAttributeView.class);
        if (view == null) {
            throw new IOException("ACL view not supported");
        }
        List<AclEntry> entries = new ArrayList<>(view.getAcl());
        UserPrincipal owner = Files.getOwner(file);
        EnumSet<AclEntryPermission> permissions = EnumSet.of(AclEntryPermission.READ_ACL, AclEntryPermission.WRITE_ACL);
        EnumSet<AclEntryFlag> flags = EnumSet.of(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT);
        AclEntry newEntry = AclEntry.newBuilder()
                .setPrincipal(owner)
                .setType(AclEntryType.ALLOW)
                .setPermissions(permissions)
                .setFlags(flags)
                .build();
        entries.add(newEntry);
        view.setAcl(entries);
    }

    private int readAclSize(DocumentContext document) {
        try {
            List<?> aclEntries = document.read("$.attributes.acl");
            return aclEntries != null ? aclEntries.size() : 0;
        } catch (PathNotFoundException e) {
            return 0;
        }
    }
}
