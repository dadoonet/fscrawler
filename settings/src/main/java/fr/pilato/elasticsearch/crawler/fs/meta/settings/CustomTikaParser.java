package fr.pilato.elasticsearch.crawler.fs.meta.settings;

import java.util.ArrayList;
import java.util.List;

public class CustomTikaParser {

    private String className = "";

    private String pathToJar = "";

    private List<String> mimeTypes = new ArrayList<String>();

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String className = "";

        public CustomTikaParser.Builder setClassName(String className) {
            this.className = className;
            return this;
        }

        private String pathToJar = "";

        public CustomTikaParser.Builder setPathToJar(String pathToJar) {
            this.pathToJar = pathToJar;
            return this;
        }

        private List<String> mimeTypes = new ArrayList<String>();

        public CustomTikaParser.Builder setMimeTypes(ArrayList<String> mimeTypes) {
            this.mimeTypes = mimeTypes;
            return this;
        }
    }

    public CustomTikaParser() {

    }

    private CustomTikaParser(String className, String pathToJar, ArrayList<String> mimeTypes) {

        this.className = className;
        this.pathToJar = pathToJar;
        this.mimeTypes = mimeTypes;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getPathToJar() {
        return pathToJar;
    }

    public void setPathToJar(String pathToJar) {
        this.pathToJar = pathToJar;
    }

    public List<String> getMimeTypes() {
        return mimeTypes;
    }

    public void setMimeTypes(ArrayList<String> mimeTypes) {
        this.mimeTypes = mimeTypes;
    }

}