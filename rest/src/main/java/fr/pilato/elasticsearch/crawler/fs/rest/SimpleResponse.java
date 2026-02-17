package fr.pilato.elasticsearch.crawler.fs.rest;

/**
 * Simple response for operations
 */
public class SimpleResponse extends RestResponse {

    public SimpleResponse() {
        super();
    }

    public SimpleResponse(boolean ok, String message) {
        super(ok, message);
    }

    public SimpleResponse(boolean ok) {
        super(ok);
    }
}
