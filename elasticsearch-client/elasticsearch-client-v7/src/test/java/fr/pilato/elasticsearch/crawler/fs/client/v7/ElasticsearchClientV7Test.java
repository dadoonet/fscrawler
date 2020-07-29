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

package fr.pilato.elasticsearch.crawler.fs.client.v7;

import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClient;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClientUtil;
import fr.pilato.elasticsearch.crawler.fs.client.WorkplaceSearchClient;
import fr.pilato.elasticsearch.crawler.fs.client.WorkplaceSearchClientUtil;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;

public class ElasticsearchClientV7Test extends AbstractFSCrawlerTestCase {

    @Test(expected = NullPointerException.class)
    public void testGetInstanceWithNullSettings() {
        ElasticsearchClientUtil.getInstance(null, null);
    }

    @Test
    public void testGetInstance() {
        ElasticsearchClient instance = ElasticsearchClientUtil.getInstance(null, FsSettings.builder("foo").build());
        assertThat(instance, instanceOf(ElasticsearchClientV7.class));
    }

    @Test
    public void testGetWorkplaceSearchInstance() {
        WorkplaceSearchClient instance = WorkplaceSearchClientUtil.getInstance(null, FsSettings.builder("foo").build());
        assertThat(instance, instanceOf(WorkplaceSearchClientV7.class));
    }
}
