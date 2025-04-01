package fr.pilato.elasticsearch.crawler.fs.settings;

import org.github.gestalt.config.annotations.Config;

import java.util.Objects;

public class Rest {

    @Config(defaultVal = Defaults.URL_DEFAULT)
    private String url;
    @Config(defaultVal = "false")
    private boolean enableCors;

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
