package fr.pilato.elasticsearch.crawler.fs;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Tracer;

public abstract class ProcessorAbstract implements Processor {
    Tracer tracer = GlobalOpenTelemetry
        .getTracerProvider()
        .tracerBuilder("fs-crawler")
        .build();

}
