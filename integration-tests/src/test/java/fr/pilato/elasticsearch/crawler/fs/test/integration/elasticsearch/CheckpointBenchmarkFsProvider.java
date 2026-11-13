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
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerPluginException;
import fr.pilato.elasticsearch.crawler.plugins.fs.local.FsLocalPlugin;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import org.pf4j.Extension;

/**
 * Test-only real-filesystem adapter: stable listing order isolates checkpoint cost from the stock provider's mutable
 * access-time comparator. File reads and all Elasticsearch processing remain real. Never selected by production job
 * settings.
 */
@Extension
public class CheckpointBenchmarkFsProvider extends FsLocalPlugin.FsCrawlerExtensionFsProviderLocal {
    @Override
    public String getType() {
        return "checkpoint-benchmark";
    }

    @Override
    public Collection<FileAbstractModel> getFiles(String directory) {
        var result = new ArrayList<FileAbstractModel>();
        try (var paths = Files.list(Path.of(directory))) {
            paths.filter(p -> fsSettings.getFs().isFollowSymlinks() || !Files.isSymbolicLink(p))
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(path -> {
                        var file = path.toFile();
                        result.add(new FileAbstractModel(
                                file.getName(),
                                file.isFile(),
                                FsCrawlerUtil.getModificationTime(file),
                                FsCrawlerUtil.getCreationTime(file),
                                FsCrawlerUtil.getLastAccessTime(file),
                                FsCrawlerUtil.getFileExtension(file),
                                directory,
                                file.getAbsolutePath(),
                                file.length(),
                                FsCrawlerUtil.getOwnerName(file),
                                FsCrawlerUtil.getGroupName(file),
                                FsCrawlerUtil.getFilePermissions(file),
                                null,
                                null));
                    });
        } catch (IOException | UncheckedIOException | SecurityException e) {
            throw new FsCrawlerPluginException("Cannot list benchmark directory " + directory, e);
        }
        return result;
    }
}
