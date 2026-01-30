package fr.pilato.elasticsearch.crawler.fs.framework;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.awaitility.core.ConditionTimeoutException;
import org.awaitility.pollinterval.PollInterval;
import org.junit.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class ExponentialBackoffPollIntervalTest {
    private static final Logger logger = LogManager.getLogger();

    @Test
    public void testExponentialBackoffPollInterval() {
        PollInterval pollInterval = new ExponentialBackoffPollInterval(Duration.ofMillis(500), Duration.ofSeconds(5));
        Duration duration = null;
        int pollCount = 0;

        // We use the start duration for the first poll
        duration = pollInterval.next(++pollCount, duration);
        assertThat(duration).hasMillis(500);

        // Then we double it until we reach the max duration
        duration = pollInterval.next(++pollCount, duration);
        assertThat(duration).hasSeconds(1);

        // Then we double it until we reach the max duration
        duration = pollInterval.next(++pollCount, duration);
        assertThat(duration).hasSeconds(2);

        // Then we double it until we reach the max duration
        duration = pollInterval.next(++pollCount, duration);
        assertThat(duration).hasSeconds(4);

        // Now we have reached the max duration
        duration = pollInterval.next(++pollCount, duration);
        assertThat(duration).hasSeconds(5);

        // We stay at the max duration
        duration = pollInterval.next(++pollCount, duration);
        assertThat(duration).hasSeconds(5);
    }

    @Test
    public void awaitNothing() {
        logger.info("Start");
        try {
            await()
                    .timeout(Duration.ofMillis(1001))
                    .untilTrue(new AtomicBoolean(false));
        } catch (ConditionTimeoutException ignored) {
        }
        logger.info("End");
    }
}
