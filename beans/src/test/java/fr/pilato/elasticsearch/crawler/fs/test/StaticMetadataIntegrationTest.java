package fr.pilato.elasticsearch.crawler.fs.test;

import fr.pilato.elasticsearch.crawler.fs.beans.Doc;
import fr.pilato.elasticsearch.crawler.fs.beans.DocUtils;
import fr.pilato.elasticsearch.crawler.fs.beans.File;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for static metadata functionality
 */
public class StaticMetadataIntegrationTest {
    private static final Logger logger = LogManager.getLogger(StaticMetadataIntegrationTest.class);

    @Test
    public void testStaticMetadataIntegration() {
        // Create a sample document
        Doc doc = new Doc();
        File file = new File();
        file.setFilename("test-document.txt");
        doc.setFile(file);
        doc.setContent("This is a test document");

        // Create static metadata configuration as would be loaded from settings
        Map<String, Object> staticTags = new HashMap<>();
        Map<String, Object> external = new HashMap<>();
        external.put("hostname", "server001");
        external.put("environment", "production");
        staticTags.put("external", external);

        // Apply static metadata (this simulates what happens in FsParserAbstract)
        Doc mergedDoc = DocUtils.getMergedStaticDoc(doc, staticTags);

        // Verify the results
        assertThat(mergedDoc).isNotNull();
        assertThat(mergedDoc.getContent()).isEqualTo("This is a test document");
        assertThat(mergedDoc.getFile().getFilename()).isEqualTo("test-document.txt");
        
        // Verify external metadata was applied
        assertThat(mergedDoc.getExternal()).isNotNull();
        assertThat(mergedDoc.getExternal()).containsEntry("hostname", "server001");
        assertThat(mergedDoc.getExternal()).containsEntry("environment", "production");

        logger.info("Static metadata integration test passed - all metadata was correctly applied to the document");
    }
}