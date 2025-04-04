package fr.pilato.elasticsearch.crawler.fs.beans;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

import java.io.InputStream;
import java.nio.file.Path;

import static fr.pilato.elasticsearch.crawler.fs.beans.DocUtils.prettyPrint;
import static org.assertj.core.api.Assertions.assertThat;

public class DocUtilsTest {
    private static final Logger logger = LogManager.getLogger(DocUtilsTest.class);

    // Get the tags path from resources
    Path path = Path.of(DocUtilsTest.class.getResource("/tags").getPath());

    @Test
    public void mergeDocsWithNothing() {
        Doc mergedDoc = DocUtils.getMergedJsonDoc(getDocSample(), (InputStream) null);
        logger.trace("Merged doc: {}", prettyPrint(mergedDoc));

        assertThat(mergedDoc.getContent()).isEqualTo("This is a test");
        assertThat(mergedDoc.getFile().getFilename()).isEqualTo("foo.txt");
        assertThat(mergedDoc.getMeta()).isNotNull();
        assertThat(mergedDoc.getPath()).isNotNull();
        assertThat(mergedDoc.getAttachment()).isNull();
        assertThat(mergedDoc.getExternal()).isNull();
        assertThat(mergedDoc.getAttributes()).isNull();
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
        logger.trace("Merged doc: {}", prettyPrint(mergedDoc));

        assertThat(mergedDoc.getContent()).isEqualTo("This is a test");
        assertThat(mergedDoc.getFile().getFilename()).isEqualTo("foo.txt");
        assertThat(mergedDoc.getMeta()).isNotNull();
        assertThat(mergedDoc.getPath()).isNotNull();
        assertThat(mergedDoc.getExternal()).isNotNull();
        assertThat(mergedDoc.getExternal()).containsEntry("tenantId", 23);
        assertThat(mergedDoc.getAttachment()).isNull();
        assertThat(mergedDoc.getAttributes()).isNull();
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
