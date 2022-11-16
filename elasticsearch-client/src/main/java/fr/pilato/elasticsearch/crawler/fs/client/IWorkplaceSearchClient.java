package fr.pilato.elasticsearch.crawler.fs.client;

import fr.pilato.elasticsearch.crawler.fs.beans.Doc;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;

public interface IWorkplaceSearchClient extends Closeable {
    void start() throws IOException;

    void index(String id, Doc doc);

    /**
     * Remove a document from Workplace Search
     * @param id the document id
     */
    void delete(String id);

    String search(String query, Map<String, Object> filters);

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
