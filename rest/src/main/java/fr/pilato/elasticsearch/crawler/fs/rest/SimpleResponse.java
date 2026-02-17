package fr.pilato.elasticsearch.crawler.fs.rest;

/**
 * Simple response for operations
 */
public class SimpleResponse {
    private boolean ok;
    private String message;

    public SimpleResponse() {
    }

    public SimpleResponse(boolean ok, String message) {
        this.ok = ok;
        this.message = message;
    }

    public boolean isOk() {
        return ok;
    }

    public void setOk(boolean ok) {
        this.ok = ok;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
