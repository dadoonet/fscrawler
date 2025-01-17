package fr.pilato.elasticsearch.crawler.fs.framework.bulk;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class TestBulkListener extends FsCrawlerSimpleBulkProcessorListener<TestOperation, TestBulkRequest, TestBulkResponse> {
    private static final Logger logger = LogManager.getLogger();
    TestBulkRequest latestRequest;
    int nbSuccessfulExecutions = 0;

    @Override
    public void beforeBulk(long executionId, TestBulkRequest request) {
        logger.trace("Execution [{}] starting with [{}] actions", executionId, request.numberOfActions());
        super.beforeBulk(executionId, request);
    }

    @Override
    public void afterBulk(long executionId, TestBulkRequest request, TestBulkResponse response) {
        logger.trace("Execution [{}] done", nbSuccessfulExecutions);
        latestRequest = request;
        nbSuccessfulExecutions++;
        super.afterBulk(executionId, request, response);
    }
}
