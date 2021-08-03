package fr.pilato.elasticsearch.crawler.fs.client;

import fr.pilato.elasticsearch.crawler.fs.beans.Doc;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface WorkplaceSearchClient extends Closeable {
    void start() throws IOException;

    void index(String id, Doc doc);

    void delete(String id);

    String search(String query, Map<String, List<String>> filters);

    /**
     * Check that a document exists
     * @param id    Document id
     * @return true if it exists, false otherwise
     */
    boolean exists(String id);

    /**
     * Get a document
     * @param id    Document id
     * @return the document or null
     */
    String get(String id);

    /**
     * Flush any pending operation
     */
    void flush();
}
