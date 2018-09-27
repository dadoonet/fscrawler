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

import fr.pilato.elasticsearch.crawler.fs.settings.Elasticsearch.Node;

import java.util.Base64;

public class ElasticsearchClientUtil {

    /**
     * Decode a cloudId to a Node representation. This helps when using
     * official elasticsearch as a service: https://cloud.elastic.co
     * The cloudId can be found from the cloud console.
     * @param cloudId The cloud ID to decode.
     * @return A Node running on https://address:443
     */
    public static Node decodeCloudId(String cloudId) {
        // 1. Ignore anything before `:`.
        String id = cloudId.substring(cloudId.indexOf(':')+1);

        // 2. base64 decode
        String decoded = new String(Base64.getDecoder().decode(id));

        // 3. separate based on `$`
        String[] words = decoded.split("\\$");

        // 4. form the URLs
        return Node.builder().setHost(words[1] + "." + words[0]).setPort(443).setScheme(Node.Scheme.HTTPS).build();
    }
}
