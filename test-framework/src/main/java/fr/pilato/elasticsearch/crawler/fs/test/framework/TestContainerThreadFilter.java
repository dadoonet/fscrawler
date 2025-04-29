package fr.pilato.elasticsearch.crawler.fs.test.framework;

import com.carrotsearch.randomizedtesting.ThreadFilter;

public class TestContainerThreadFilter implements ThreadFilter {
    @Override
    public boolean reject(Thread t) {
        return
                t.getName().startsWith("ducttape-") ||
                        t.getThreadGroup() != null && "testcontainers".equals(t.getThreadGroup().getName());
    }
}
