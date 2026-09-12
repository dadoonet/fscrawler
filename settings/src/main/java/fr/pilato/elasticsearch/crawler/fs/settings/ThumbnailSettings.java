package fr.pilato.elasticsearch.crawler.fs.settings;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ThumbnailSettings {
    
    private boolean enabled = false;
    private int width = 310;
    private int height = 430;
    private String format = "png"; // png or jpg
    private String fieldName = "_thumbnail";
    
    public ThumbnailSettings() {
    }
    
    public ThumbnailSettings(boolean enabled, int width, int height, String format, String fieldName) {
        this.enabled = enabled;
        this.width = width;
        this.height = height;
        this.format = format;
        this.fieldName = fieldName;
    }
    
    @JsonProperty("enabled")
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    @JsonProperty("width")
    public int getWidth() {
        return width;
    }
    
    public void setWidth(int width) {
        this.width = width;
    }
    
    @JsonProperty("height")
    public int getHeight() {
        return height;
    }
    
    public void setHeight(int height) {
        this.height = height;
    }
    
    @JsonProperty("format")
    public String getFormat() {
        return format;
    }
    
    public void setFormat(String format) {
        this.format = format;
    }
    
    @JsonProperty("field_name")
    public String getFieldName() {
        return fieldName;
    }
    
    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }
    
    @Override
    public String toString() {
        return "ThumbnailSettings{" +
                "enabled=" + enabled +
                ", width=" + width +
                ", height=" + height +
                ", format='" + format + '\'' +
                ", fieldName='" + fieldName + '\'' +
                '}';
    }
}
