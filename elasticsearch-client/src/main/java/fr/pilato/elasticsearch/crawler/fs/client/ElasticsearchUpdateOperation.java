package fr.pilato.elasticsearch.crawler.fs.client;

public class ElasticsearchUpdateOperation extends ElasticsearchOperation {
    private final String json;

    public ElasticsearchUpdateOperation(String index, String id, String pipeline, String json) {
        super(Operation.UPDATE, index, id);
        this.json = json;
    }
    
    public String getJson() {
        return json;
    }
}
