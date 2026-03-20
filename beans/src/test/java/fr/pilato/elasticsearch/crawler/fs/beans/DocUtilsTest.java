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
package fr.pilato.elasticsearch.crawler.fs.beans;

import java.io.ByteArrayInputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public class DocUtilsTest {
    private static final Logger logger = LogManager.getLogger(DocUtilsTest.class);

    // Get the tags path from resources
    Path path;

    public DocUtilsTest() throws URISyntaxException {
        path = Path.of(DocUtilsTest.class.getResource("/tags").toURI());
    }

    @Test
    public void mergeDocsWithNoJsonFile() {
        Doc mergedDoc = DocUtils.getMergedDoc(getDocSample(), "empty.json", null);
        logger.trace("Merged doc: {}", DocUtils.prettyPrint(mergedDoc));

        Assertions.assertThat(mergedDoc.getContent()).isEqualTo("This is a test");
        Assertions.assertThat(mergedDoc.getFile().getFilename()).isEqualTo("foo.txt");
        Assertions.assertThat(mergedDoc.getMeta()).isNotNull();
        Assertions.assertThat(mergedDoc.getPath()).isNotNull();
        Assertions.assertThat(mergedDoc.getAttachment()).isNull();
        Assertions.assertThat(mergedDoc.getExternal()).isNull();
        Assertions.assertThat(mergedDoc.getAttributes()).isNull();
    }

    @Test
    public void mergeDocsWithEmptyJsonFile() {
        Doc mergedDoc = DocUtils.getMergedDoc(
                getDocSample(), "empty.json", new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8)));
        logger.trace("Merged doc: {}", DocUtils.prettyPrint(mergedDoc));

        Assertions.assertThat(mergedDoc.getContent()).isEqualTo("This is a test");
        Assertions.assertThat(mergedDoc.getFile().getFilename()).isEqualTo("foo.txt");
        Assertions.assertThat(mergedDoc.getMeta()).isNotNull();
        Assertions.assertThat(mergedDoc.getPath()).isNotNull();
        Assertions.assertThat(mergedDoc.getAttachment()).isNull();
        Assertions.assertThat(mergedDoc.getExternal()).isNull();
        Assertions.assertThat(mergedDoc.getAttributes()).isNull();
    }

    @Test
    public void mergeDocsWithNoYamlFile() {
        Doc mergedDoc = DocUtils.getMergedDoc(getDocSample(), "empty.yaml", null);
        logger.trace("Merged doc: {}", DocUtils.prettyPrint(mergedDoc));

        Assertions.assertThat(mergedDoc.getContent()).isEqualTo("This is a test");
        Assertions.assertThat(mergedDoc.getFile().getFilename()).isEqualTo("foo.txt");
        Assertions.assertThat(mergedDoc.getMeta()).isNotNull();
        Assertions.assertThat(mergedDoc.getPath()).isNotNull();
        Assertions.assertThat(mergedDoc.getAttachment()).isNull();
        Assertions.assertThat(mergedDoc.getExternal()).isNull();
        Assertions.assertThat(mergedDoc.getAttributes()).isNull();
    }

    @Test
    public void mergeDocsWithAJsonFile() {
        testMergeDocsWithAFile(path.resolve("meta-as-json.json"));
    }

    @Test
    public void mergeDocsWithAYamlFile() {
        testMergeDocsWithAFile(path.resolve(".meta.yml"));
    }

    private void testMergeDocsWithAFile(Path file) {
        Doc mergedDoc = DocUtils.getMergedDoc(getDocSample(), file);
        logger.trace("Merged doc: {}", DocUtils.prettyPrint(mergedDoc));

        Assertions.assertThat(mergedDoc.getContent()).isEqualTo("This is a test");
        Assertions.assertThat(mergedDoc.getFile().getFilename()).isEqualTo("foo.txt");
        Assertions.assertThat(mergedDoc.getMeta()).isNotNull();
        Assertions.assertThat(mergedDoc.getPath()).isNotNull();
        Assertions.assertThat(mergedDoc.getExternal()).isNotNull();
        Assertions.assertThat(mergedDoc.getExternal()).containsEntry("tenantId", 23);
        Assertions.assertThat(mergedDoc.getAttachment()).isNull();
        Assertions.assertThat(mergedDoc.getAttributes()).isNull();
    }

    private Doc getDocSample() {
        File file = new File();
        file.setFilename("foo.txt");
        Doc doc = new Doc();
        doc.setFile(file);
        doc.setContent("This is a test");
        return doc;
    }
}
