package fr.pilato.elasticsearch.crawler.fs.settings;

public class ThumbnailSettings {
    private boolean enabled = true;
    private int width = 300;
    private int height = 410;
    private String format = "png";
    private String fieldName = "thumbnail";
    
    public ThumbnailSettings() {}
    
    public ThumbnailSettings(boolean enabled, int width, int height, String format, float quality, String fieldName) {
        this.enabled = enabled;
        this.width = width;
        this.height = height;
        this.format = format;
        this.fieldName = fieldName;
    }
    
    // Getters and setters
    public boolean isEnabled() { 
        return enabled; 
    }
    
    public void setEnabled(boolean enabled) { 
        this.enabled = enabled; 
    }
    
    public int getWidth() { 
        return width; 
    }
    
    public void setWidth(int width) { 
        this.width = width; 
    }
    
    public int getHeight() { 
        return height; 
    }
    
    public void setHeight(int height) { 
        this.height = height; 
    }
    
    public String getFormat() { 
        return format; 
    }
    
    public void setFormat(String format) { 
        this.format = format; 
    }
    
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
