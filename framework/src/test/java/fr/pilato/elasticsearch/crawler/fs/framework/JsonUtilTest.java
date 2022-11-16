/*
 * Licensed to David Pilato under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

package fr.pilato.elasticsearch.crawler.fs.framework;

import com.jayway.jsonpath.DocumentContext;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import org.junit.Test;

import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.parseJsonAsDocumentContext;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class JsonUtilTest extends AbstractFSCrawlerTestCase {

    @Test
    public void testJsonPath() {
        String json = "{\n" +
                "   \"content\":\"Some Text\",\n" +
                "   \"file\":{\n" +
                "      \"extension\":\"txt\",\n" +
                "      \"content_type\":\"text/plain; charset=ISO-8859-1\",\n" +
                "      \"created\":\"2022-02-08T21:57:51.000+00:00\",\n" +
                "      \"last_modified\":\"2022-02-08T21:57:51.394+00:00\",\n" +
                "      \"last_accessed\":\"2022-02-08T21:57:51.394+00:00\",\n" +
                "      \"indexing_date\":\"2022-02-08T21:57:52.033+00:00\",\n" +
                "      \"filesize\":12230,\n" +
                "      \"filename\":\"roottxtfile.txt\",\n" +
                "      \"url\":\"file:///var/folders/xn/47mdpxd12vq4zrjhkwbhd5_r0000gn/T/junit16929133427221182897/resources/test_attributes/roottxtfile.txt\"\n" +
                "   },\n" +
                "   \"path\":{\n" +
                "      \"root\":\"e366ee2f42db246720b82a82fdb4e15e\",\n" +
                "      \"virtual\":\"/roottxtfile.txt\",\n" +
                "      \"real\":\"/var/folders/xn/47mdpxd12vq4zrjhkwbhd5_r0000gn/T/junit16929133427221182897/resources/test_attributes/roottxtfile.txt\"\n" +
                "   },\n" +
                "   \"attributes\":{\n" +
                "      \"owner\":\"dpilato\",\n" +
                "      \"group\":\"staff\",\n" +
                "      \"permissions\":644\n" +
                "   }\n" +
                "}";

        DocumentContext context = parseJsonAsDocumentContext(json);
        assertThat(context.read("$.attributes.owner"), is("dpilato"));
        assertThat(context.read("$.attributes.permissions"), is(644));
    }
}
