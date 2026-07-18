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
package fr.pilato.elasticsearch.crawler.fs;

import static org.assertj.core.api.Assertions.assertThat;

import fr.pilato.elasticsearch.crawler.fs.beans.FileAbstractModel;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsLoader;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import java.lang.reflect.Method;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class FsParserFileChangeTest extends AbstractFSCrawlerTestCase {

    @Test
    void fresh_scan_indexes_file_with_epoch_timestamp() throws Exception {
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.setName(jobName);
        fsSettings.getFs().setJsonSupport(true);
        FsParser parser = new FsParser(fsSettings, testTmpDir, null, null, 1, false, null);
        FileAbstractModel file = new FileAbstractModel(
                "archive.json",
                true,
                Instant.EPOCH,
                null,
                null,
                "json",
                "/",
                "/archive.json",
                2,
                null,
                null,
                0,
                null,
                null);

        Method shouldIndex = FsParser.class.getDeclaredMethod(
                "shouldIndexBecauseOfChanges", FileAbstractModel.class, Instant.class, String.class, String.class);
        shouldIndex.setAccessible(true);

        assertThat(shouldIndex.invoke(parser, file, Instant.EPOCH, file.getName(), file.getPath()))
                .isEqualTo(true);
    }
}
