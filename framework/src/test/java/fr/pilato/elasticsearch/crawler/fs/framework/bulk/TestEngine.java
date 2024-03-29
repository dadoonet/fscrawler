package fr.pilato.elasticsearch.crawler.fs.framework.bulk;

class TestEngine implements Engine<TestOperation, TestBulkRequest, TestBulkResponse> {
    @Override
    public TestBulkResponse bulk(TestBulkRequest request) {
        return new TestBulkResponse();
    }
}
