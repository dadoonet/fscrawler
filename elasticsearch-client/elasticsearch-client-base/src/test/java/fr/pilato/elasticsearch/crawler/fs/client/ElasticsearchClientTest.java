/*
 * Licensed to David Pilato (the "Author") under one
 * or more contributor license agreements.  See the NOTICE file
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
 */

package fr.pilato.elasticsearch.crawler.fs.client;

import fr.pilato.elasticsearch.crawler.fs.client.v0.ElasticsearchClientV0;
import fr.pilato.elasticsearch.crawler.fs.client.v1.ElasticsearchClientV1;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import org.junit.Test;

import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

@SuppressWarnings("ConstantConditions")
public class ElasticsearchClientTest extends AbstractFSCrawlerTestCase {

    @Test
    public void testGetInstanceWithNullSettings() {
        NullPointerException npe = expectThrows(NullPointerException.class,
                () -> {
                    try {
                        return ElasticsearchClientUtil.getInstance(null, null, 0);
                    } catch (ClassNotFoundException e) {
                        throw new AssertionError("We should have been able to load a client version 0");
                    }
                });
        assertThat(npe.getMessage(), is("settings can not be null"));
    }

    @Test
    public void testGetInstance() throws IOException, ClassNotFoundException {
        ElasticsearchClient instance = ElasticsearchClientUtil.getInstance(null, FsSettings.builder("foo").build(), 0);
        assertThat(instance, instanceOf(ElasticsearchClientV0.class));
        instance.checkVersion();
    }

    @Test
    public void testGetInstanceWrongVersions() throws ClassNotFoundException {
        ElasticsearchClient instance = ElasticsearchClientUtil.getInstance(null, FsSettings.builder("foo").build(), 1);
        assertThat(instance, instanceOf(ElasticsearchClientV1.class));
        RuntimeException exception = expectThrows(RuntimeException.class, () -> {
            try {
                instance.checkVersion();
                return null;
            } catch (IOException e) {
                return e;
            }
        });
        assertThat(exception.getMessage(), is("The Elasticsearch client version [1] is not compatible with the Elasticsearch cluster version [6.4.1]."));
    }
}
