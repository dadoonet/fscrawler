package fr.pilato.elasticsearch.crawler.fs.framework.bulk;

public class TestOperation implements FsCrawlerOperation<TestOperation> {
    TestBean payload;

    public TestOperation(TestBean payload) {
        this.payload = payload;
    }

    @Override
    public int compareTo(TestOperation request) {
        return 0;
    }

    public void payload(TestBean payload) {
        this.payload = payload;
    }

    public TestBean getPayload() {
        return payload;
    }
}
