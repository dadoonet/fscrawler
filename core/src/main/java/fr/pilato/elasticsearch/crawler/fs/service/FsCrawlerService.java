package fr.pilato.elasticsearch.crawler.fs.service;

import java.io.Closeable;
import java.io.IOException;

public interface FsCrawlerService extends Closeable {

    /**
     * Start the service
     */
    void start() throws IOException;

    /**
     * Get the version of the service
     * @return a version
     */
    String getVersion() throws IOException;
}
