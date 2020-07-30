package fr.pilato.elasticsearch.crawler.fs.service;

import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClient;

import java.io.Closeable;
import java.io.IOException;

public interface FsCrawlerService extends Closeable {

    /**
     * Start the service
     */
    void start() throws IOException;

    /**
     * Get the elasticsearch client
     * @return elasticsearch client
     */
    ElasticsearchClient getClient();
}
