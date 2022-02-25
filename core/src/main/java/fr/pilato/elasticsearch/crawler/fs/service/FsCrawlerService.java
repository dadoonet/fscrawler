package fr.pilato.elasticsearch.crawler.fs.service;

import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClientException;

import java.io.Closeable;
import java.io.IOException;

public interface FsCrawlerService extends Closeable {

    /**
     * Start the service
     */
    void start() throws IOException, ElasticsearchClientException;

    /**
     * Get the version of the service
     * @return a version
     */
    String getVersion() throws IOException, ElasticsearchClientException;
}
