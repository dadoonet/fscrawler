package fr.pilato.elasticsearch.crawler.fs.beans;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

import java.io.InputStream;
import java.nio.file.Path;

import static fr.pilato.elasticsearch.crawler.fs.beans.DocUtils.prettyPrint;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class DocUtilsTest {
    private static final Logger logger = LogManager.getLogger(DocUtilsTest.class);

    // Get the tags path from resources
    Path path = Path.of(DocUtilsTest.class.getResource("/tags").getPath());

    @Test
    public void testMergeDocsWithNothing() {
        Doc mergedDoc = DocUtils.getMergedJsonDoc(getDocSample(), (InputStream) null);
        logger.trace("Merged doc: {}", prettyPrint(mergedDoc));

        assertThat(mergedDoc.getContent(), is("This is a test"));
        assertThat(mergedDoc.getFile().getFilename(), is("foo.txt"));
        assertThat(mergedDoc.getMeta(), notNullValue());
        assertThat(mergedDoc.getPath(), notNullValue());
        assertThat(mergedDoc.getAttachment(), nullValue());
        assertThat(mergedDoc.getExternal(), nullValue());
        assertThat(mergedDoc.getAttributes(), nullValue());
    }

    @Test
    public void testMergeDocsWithAJsonFile() {
        testMergeDocsWithAFile(path.resolve("meta-as-json.json"));
    }

    @Test
    public void testMergeDocsWithAYamlFile() {
        testMergeDocsWithAFile(path.resolve(".meta.yml"));
    }

    private void testMergeDocsWithAFile(Path file) {
        Doc mergedDoc = DocUtils.getMergedDoc(getDocSample(), file);
        logger.trace("Merged doc: {}", prettyPrint(mergedDoc));

        assertThat(mergedDoc.getContent(), is("This is a test"));
        assertThat(mergedDoc.getFile().getFilename(), is("foo.txt"));
        assertThat(mergedDoc.getMeta(), notNullValue());
        assertThat(mergedDoc.getPath(), notNullValue());
        assertThat(mergedDoc.getExternal(), notNullValue());
        assertThat(mergedDoc.getExternal().get("tenantId"), is(23));
        assertThat(mergedDoc.getAttachment(), nullValue());
        assertThat(mergedDoc.getAttributes(), nullValue());
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
