package fr.pilato.elasticsearch.crawler.fs.framework;

import org.awaitility.pollinterval.PollInterval;

import java.time.Duration;

/**
 * An implementation of {@link PollInterval} that provides an exponential backoff strategy.
 * The wait time starts at a specified duration and doubles with each poll, up to a defined maximum duration.
 * This is useful for scenarios where you want to gradually increase the wait time between polling attempts,
 * such as when waiting for a resource to become available without going above a max duration.
 */
public class ExponentialBackoffPollInterval implements PollInterval {

    private final Duration startDuration;
    private final Duration maxDuration;

    public ExponentialBackoffPollInterval(Duration startDuration, Duration maxDuration) {
        this.startDuration = startDuration;
        this.maxDuration = maxDuration;
    }

    /**
     * Syntactic sugar for creating a {@link ExponentialBackoffPollInterval}.
     *
     * @param startDuration      The start duration (initial function value)
     * @param maxDuration        The max duration (we don't go beyond this value)
     * @return A new instance of {@link ExponentialBackoffPollInterval}
     */
    public static ExponentialBackoffPollInterval exponential(Duration startDuration, Duration maxDuration) {
        return new ExponentialBackoffPollInterval(startDuration, maxDuration);
    }

    @Override
    public Duration next(int pollCount, Duration previousDuration) {
        if (pollCount == 1) {
            return startDuration;
        }
        Duration duration = previousDuration.multipliedBy(2);
        if (duration.compareTo(maxDuration) > 0) {
            duration = maxDuration;
        }
        return duration;
    }
}
