package fr.pilato.elasticsearch.crawler.fs.test;

import fr.pilato.elasticsearch.crawler.fs.beans.Doc;
import fr.pilato.elasticsearch.crawler.fs.beans.DocUtils;
import fr.pilato.elasticsearch.crawler.fs.beans.File;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for static metadata functionality
 */
public class StaticMetadataTest {

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
    }

    @Test
    public void testStaticMetadataWithExternalFileOverride() {
        // Create a document that already has external metadata (simulating .meta.yml file)
        Doc doc = new Doc();
        File file = new File();
        file.setFilename("test-document.txt");
        doc.setFile(file);
        doc.setContent("This is a test document");
        
        // Set external metadata that would come from .meta.yml file
        Map<String, Object> existingExternal = new HashMap<>();
        existingExternal.put("environment", "development"); // This should override static metadata
        existingExternal.put("priority", "high"); // This is additional from .meta.yml
        doc.setExternal(existingExternal);

        // Create static metadata configuration
        Map<String, Object> staticTags = new HashMap<>();
        Map<String, Object> staticExternal = new HashMap<>();
        staticExternal.put("hostname", "server001");
        staticExternal.put("environment", "production"); // This should be overridden by .meta.yml
        staticTags.put("external", staticExternal);

        // Apply static metadata (this simulates what happens in FsParserAbstract)
        Doc mergedDoc = DocUtils.getMergedStaticDoc(doc, staticTags);

        // Verify the results
        assertThat(mergedDoc).isNotNull();
        assertThat(mergedDoc.getContent()).isEqualTo("This is a test document");
        assertThat(mergedDoc.getFile().getFilename()).isEqualTo("test-document.txt");
        
        // Verify external metadata precedence
        assertThat(mergedDoc.getExternal()).isNotNull();
        assertThat(mergedDoc.getExternal()).containsEntry("hostname", "server001"); // From static metadata
        assertThat(mergedDoc.getExternal()).containsEntry("environment", "development"); // From .meta.yml, overrides static
        assertThat(mergedDoc.getExternal()).containsEntry("priority", "high"); // From .meta.yml, additional field
    }
}