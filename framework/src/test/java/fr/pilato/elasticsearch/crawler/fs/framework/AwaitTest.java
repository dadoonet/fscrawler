package fr.pilato.elasticsearch.crawler.fs.framework;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hamcrest.Matchers;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static fr.pilato.elasticsearch.crawler.fs.framework.Await.awaitBusy;
import static org.hamcrest.MatcherAssert.assertThat;

public class AwaitTest {
    private static final Logger logger = LogManager.getLogger();

    @Test
    public void testSimulateElasticsearchException() throws InterruptedException {
        AtomicLong l = new AtomicLong(-1);

        long hits = awaitBusy(() -> {
            // Let's search for entries
            try {
                throw new RuntimeException("foo bar");
            } catch (RuntimeException e) {
                logger.warn("error caught", e);
                return l.getAndIncrement();
            }
        }, null, 1, TimeUnit.SECONDS);

        assertThat(hits, Matchers.greaterThan(-1L));
    }
}
