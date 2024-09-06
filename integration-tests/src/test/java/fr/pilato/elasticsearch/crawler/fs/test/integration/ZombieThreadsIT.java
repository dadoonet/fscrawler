package fr.pilato.elasticsearch.crawler.fs.test.integration;

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakLingering;
import org.junit.Test;

@ThreadLeakLingering(linger = 500) // 5 sec lingering
public class ZombieThreadsIT extends AbstractITCase {

    static {
        byPass = true;
    }

    @Test
    public void testZombieThreads() {
        logger.info("--> start");
        // Do nothing
    }

}
