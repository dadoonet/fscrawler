package fr.pilato.elasticsearch.crawler.fs.settings;

import java.util.Objects;

public class Rest {
    public static final String URL_DEFAULT = "http://127.0.0.1:8080/fscrawler";
    public static final boolean CORS_ENABLE_DEFAULT = false;
    public static final Rest DEFAULT = Rest.builder().setUrl(URL_DEFAULT).setEnableCors(CORS_ENABLE_DEFAULT).build();

    private String url;
    private boolean enableCors;

    public Rest() {

    }

    public Rest(String url) {
        this(url, CORS_ENABLE_DEFAULT);
    }

    public Rest(String url, boolean enableCors) {
        this.url = url;
        this.enableCors = enableCors;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }

    public void setEnableCors(boolean enableCors) {
        this.enableCors = enableCors;
    }

    public boolean isEnableCors() {
        return enableCors;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String url = URL_DEFAULT;
        private boolean enableCors = CORS_ENABLE_DEFAULT;

        public Builder setUrl(String url) {
            this.url = url;
            return this;
        }

        public Builder setEnableCors(boolean enableCors) {
            this.enableCors = enableCors;
            return this;
        }

        public Rest build() {
            return new Rest(url, enableCors);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Rest rest = (Rest) o;
        return enableCors == rest.enableCors &&
                Objects.equals(url, rest.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enableCors, url);
    }

    @Override
    public String toString() {
        return "Rest{" + "url='" + url + '\'' +
                ", enableCors=" + enableCors +
                '}';
    }
}
