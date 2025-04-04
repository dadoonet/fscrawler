package fr.pilato.elasticsearch.crawler.fs.framework.bulk;

public class TestEngine implements Engine<TestOperation, TestBulkRequest, TestBulkResponse> {
    @Override
    public TestBulkResponse bulk(TestBulkRequest request) {
        return new TestBulkResponse();
    }
}
