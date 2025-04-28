package fr.pilato.elasticsearch.crawler.fs.test.framework;

import com.carrotsearch.randomizedtesting.ThreadFilter;

/**
 * This is temporary until https://github.com/minio/minio-java/issues/1584 is solved
 */
public class MinioThreadFilter implements ThreadFilter {
    @Override
    public boolean reject(Thread t) {
        return "Okio Watchdog".equals(t.getName())
                || "OkHttp TaskRunner".equals(t.getName())
                || "ForkJoinPool.commonPool-worker-1".equals(t.getName());
    }
}
