package fr.pilato.elasticsearch.crawler.fs.test.framework;

import com.carrotsearch.randomizedtesting.ThreadFilter;

public class JNACleanerThreadFilter implements ThreadFilter {
    @Override
    public boolean reject(Thread t) {
        return "JNA Cleaner".equals(t.getName());
    }
}
