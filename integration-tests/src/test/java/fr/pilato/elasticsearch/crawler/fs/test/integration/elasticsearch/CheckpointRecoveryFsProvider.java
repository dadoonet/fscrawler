/*
 * Licensed to David Pilato (the "Author") under one
 * or more contributor license agreements. See the NOTICE file
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
 *
 * Made from 🇫🇷🇪🇺 with ❤️ - 2011-2026
 */
package fr.pilato.elasticsearch.crawler.fs.test.integration.elasticsearch;

import fr.pilato.elasticsearch.crawler.fs.beans.FileAbstractModel;
import java.io.InputStream;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.pf4j.Extension;

/** Instance-scoped instrumentation over real files; hooks run on the crawler thread, without timing races. */
@Extension
public class CheckpointRecoveryFsProvider extends CheckpointBenchmarkFsProvider {
    final Map<String, Integer> listings = new ConcurrentHashMap<>();
    final Map<String, Integer> reads = new ConcurrentHashMap<>();
    Consumer<String> beforeListing = path -> {};
    Consumer<String> beforeRead = path -> {};

    @Override
    public String getType() {
        return "checkpoint-recovery";
    }

    @Override
    public Collection<FileAbstractModel> getFiles(String directory) {
        listings.merge(directory, 1, Integer::sum);
        beforeListing.accept(directory);
        return super.getFiles(directory);
    }

    @Override
    public InputStream getInputStream(FileAbstractModel file) {
        reads.merge(file.getFullpath(), 1, Integer::sum);
        beforeRead.accept(file.getFullpath());
        return super.getInputStream(file);
    }
}
