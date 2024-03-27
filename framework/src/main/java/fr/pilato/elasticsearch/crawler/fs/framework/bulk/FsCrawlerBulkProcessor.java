/*
 * Licensed to David Pilato (the "Author") under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Author licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package fr.pilato.elasticsearch.crawler.fs.framework.bulk;

import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchDeleteOperation;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchIndexOperation;
import fr.pilato.elasticsearch.crawler.fs.framework.ByteSizeValue;
import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Bulk processor
 */
public class FsCrawlerBulkProcessor<
        O extends FsCrawlerOperation<O>,
        Req extends FsCrawlerBulkRequest<O>,
        Res extends FsCrawlerBulkResponse<O>
        > implements Closeable {

    private static final Logger logger = LogManager.getLogger(FsCrawlerBulkProcessor.class);

    private final int bulkActions;
    private final ByteSizeValue byteSize;
    private final Listener<O, Req, Res> listener;
    private final Engine<O, Req, Res> engine;
    private Req bulkRequest;
    private final Supplier<Req> requestSupplier;
    private final ScheduledExecutorService executor;
    private volatile boolean closed = false;
    private final AtomicLong executionIdGen = new AtomicLong();
    //modified code starts
    private int totalByteSize= 0;
    private int lastJsonSize = 0;
    //modified code ends
    
    public FsCrawlerBulkProcessor(Engine<O, Req, Res> engine,
                                   Listener<O, Req, Res> listener,
                                   int bulkActions,
                                   TimeValue flushInterval,
                                   ByteSizeValue byteSize,
                                   Supplier<Req> requestSupplier) {
        this.byteSize = byteSize;
		this.engine = engine;
        this.listener = listener;
        this.bulkActions = bulkActions;
        this.requestSupplier = requestSupplier;
        this.bulkRequest = requestSupplier.get();
        this.listener.setBulkProcessor(this);

        if (flushInterval != null) {
            executor = Executors.newScheduledThreadPool(1);
            executor.scheduleWithFixedDelay(this::executeWhenNeeded, 0, flushInterval.millis(), TimeUnit.MILLISECONDS);
        } else {
            executor = null;
        }
    }

    @Override
    public void close() throws IOException {
        try {
            internalClose();
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    private void internalClose() throws InterruptedException {
        if (closed) {
            return;
        }
        closed = true;

        if (executor != null) {
            logger.debug("Closing BulkProcessor");
            executor.shutdown();
            if(!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.warn("We waited for the bulk processor shutdown but it did not close properly. " +
                        "We might be missing some documents.");
            }
            logger.debug("BulkProcessor is now closed");
        }

        if (bulkRequest.numberOfActions() > 0) {
            logger.debug("Executing [{}] remaining actions", bulkRequest.numberOfActions());
            execute();
        }
    }

    /**
     * Add a request to the processor
     * @param request   request to add
     * @return this so we can link methods.
     */
    public synchronized FsCrawlerBulkProcessor<O, Req, Res> add(Object request) {
    	
        ensureOpen();      
        //modified code starts
        if(request.getClass().getSimpleName().equals("ElasticsearchIndexOperation")) {
        	//
        	addingByteSize(((ElasticsearchIndexOperation) request).getJson());
        	executeIfNeeded(request);
        } else {
        	bulkRequest.add(request);
        	executeIfNeededForRemainingCases();
        }
      //modified code ends
        
        return this;
    }
    

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("bulk process already closed");
        }
    }

    private void executeIfNeeded(Object request) {
        ensureOpen();
        //for ByteSize comparison
        if (compareWithByteSize(byteSize.getBytes(),totalByteSize)) {
        	logger.trace("crossed barrier of bytes size");
            execute();
            totalByteSize = lastJsonSize;
            //adding last file
            bulkRequest.add(request);
        } else if (isOverTheLimit()) {
        	//for bulk size comparison
        	bulkRequest.add(request);
        	logger.trace("crossed barrier of bulk size");
        	execute();
        	totalByteSize = 0;
        } else {
        	bulkRequest.add(request);
        }
    }
    
    private void executeIfNeededForRemainingCases() {
        ensureOpen();
        if (isOverTheLimit()) {
            execute();
        }
    }

    private void executeWhenNeeded() {
        ensureOpen();
        if (bulkRequest.numberOfActions() > 0) {
            execute();
        }
    }

    private void execute() {
        final Req bulkRequest = this.bulkRequest;
        this.bulkRequest = requestSupplier.get();
        final long executionId = executionIdGen.incrementAndGet();

        // execute in a blocking fashion...
        boolean afterCalled = false;
        try {
            listener.beforeBulk(executionId, bulkRequest);
            Res bulkItemResponses = engine.bulk(bulkRequest);
            afterCalled = true;
            listener.afterBulk(executionId, bulkRequest, bulkItemResponses);
        } catch (Exception e) {
            if (!afterCalled) {
                listener.afterBulk(executionId, bulkRequest, e);
            }
        }
    }

    private boolean isOverTheLimit() {
        return (bulkActions != -1) && (bulkRequest.numberOfActions() >= (bulkActions-1));
    }
    
    //modified code starts
    //adding byte size of each file in a variable named total byte size
    private void addingByteSize(String jsonObj) {
    	byte[] bytes = jsonObj.getBytes();
    	logger.trace("current file size is :{}", bytes.length);
    	lastJsonSize = bytes.length;
        totalByteSize += bytes.length;
        logger.trace("after adding new file total size is :{}", totalByteSize);
    }
    //comparing bulk byteSize with total byte size
    private boolean compareWithByteSize(long limit, int totalSize) {
    	return (totalSize >= limit);
    }
    //modified code ends

    public Listener<O, Req, Res> getListener() {
        return listener;
    }

    public void flush() {
        execute();
    }

    public static class Builder<O extends FsCrawlerOperation<O>,
            Req extends FsCrawlerBulkRequest<O>,
            Res extends FsCrawlerBulkResponse<O>> {

        private int bulkActions;
        private TimeValue flushInterval;
        private ByteSizeValue byteSize;
        private final Engine<O, Req, Res> engine;
        private final Listener<O, Req, Res> listener;
        private final Supplier<Req> requestSupplier;

        public Builder(Engine<O, Req, Res> engine, Listener<O, Req, Res> listener, Supplier<Req> requestSupplier) {
            this.engine = engine;
            this.listener = listener;
            this.requestSupplier = requestSupplier;
        }

        public Builder<O, Req, Res> setBulkActions(int bulkActions) {
            this.bulkActions = bulkActions;
            return this;
        }

        public Builder<O, Req, Res> setFlushInterval(TimeValue flushInterval) {
            this.flushInterval = flushInterval;
            return this;
        }
        
        public Builder<O, Req, Res> setByteSize(ByteSizeValue byteSizeValue) {
            this.byteSize = byteSizeValue;
            return this;
        }

        public FsCrawlerBulkProcessor<O, Req, Res> build() {
            return new FsCrawlerBulkProcessor<>(engine, listener, bulkActions, flushInterval, byteSize, requestSupplier);
        }
    }

    public interface Listener<O extends FsCrawlerOperation<O>,
            Req extends FsCrawlerBulkRequest<O>,
            Res extends FsCrawlerBulkResponse<O>> {

        void beforeBulk(long executionId, Req request);

        void afterBulk(long executionId, Req request, Res response);

        void afterBulk(long executionId, Req request, Throwable failure);

        void setBulkProcessor(FsCrawlerBulkProcessor<O, Req, Res> bulkProcessor);
    }
}
