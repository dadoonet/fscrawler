package fr.pilato.elasticsearch.crawler.fs.test.framework;

import com.carrotsearch.randomizedtesting.ThreadFilter;

public class WindowsSpecificThreadFilter implements ThreadFilter {
    @Override
    public boolean reject(Thread t) {
        return System.getProperty("os.name").toLowerCase().contains("win")
                && t.getThreadGroup() != null
                && "TGRP-ElasticsearchClientIT".equals(t.getThreadGroup().getName());
    }
}
