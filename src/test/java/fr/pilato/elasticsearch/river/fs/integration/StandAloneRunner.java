/*
 * Licensed to Elasticsearch under one or more contributor
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

package fr.pilato.elasticsearch.river.fs.integration;

import org.elasticsearch.common.io.FileSystemUtils;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;

import java.io.File;
import java.io.IOException;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

/**
 * Standalone runner test.
 * Just launch it and add files to scan in /tmp/tmp_es dir
 */
public class StandAloneRunner {

    public static void main(String[] args) {
        Node node = null;
        try {
            // Remove old data
            FileSystemUtils.deleteRecursively(new File("./target/data"));

            node = NodeBuilder.nodeBuilder().settings(
                    ImmutableSettings.builder().put("path.data", "./target/data").build()
            ).node();

            XContentBuilder river = jsonBuilder().prettyPrint().startObject()
                    .field("type", "fs")
                    .startObject("fs")
                    .field("url", "/tmp/tmp_es")
                    .field("update_rate", 500)
                    .endObject()
                    .startObject("index")
                    .field("bulk_size", 1)
                    .endObject()
                    .endObject();

            System.out.println("Startin river");
            System.out.println(river.string());
            node.client().prepareIndex("_river", "main_fs", "_meta").setSource(river).execute().actionGet();

            // Waiting for kill signal
            System.out.print("press return when finished:");
            System.in.read();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (node != null) {
                node.close();
            }
        }
    }
}
