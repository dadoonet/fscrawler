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

package fr.pilato.elasticsearch.river.fs;


import fr.pilato.elasticsearch.river.fs.util.FsRiverUtil;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.junit.Assert;
import org.junit.Test;

import java.util.Map;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

public class FsIncludesExcludesTest {

    @Test
    public void extract_from_array_and_remove_duplicates() throws Exception {

        XContentBuilder xb = jsonBuilder()
                .startObject()
                .startObject("fs")
                .startArray("includes")
                .value("*.txt")
                .value(" *.txt ")
                .value("        *.txt")
                .endArray()
                .endObject()
                .endObject();
        String jsonContent = xb.string();
        Map<String, Object> map = XContentHelper.convertToMap(jsonContent.getBytes(), 0, jsonContent.length(), false).v2();

        String[] includes = FsRiverUtil.buildArrayFromSettings(map, "fs.includes");

        Assert.assertEquals(1, includes.length);
    }

    @Test
    public void extract_from_string_and_remove_duplicates() throws Exception {

        XContentBuilder xb = jsonBuilder()
                .startObject()
                .startObject("fs")
                .field("includes", "*.txt,  *.txt, *.txt ")
                .endObject()
                .endObject();
        String jsonContent = xb.string();
        Map<String, Object> map = XContentHelper.convertToMap(jsonContent.getBytes(), 0, jsonContent.length(), false).v2();

        String[] includes = FsRiverUtil.buildArrayFromSettings(map, "fs.includes");

        Assert.assertEquals(1, includes.length);
    }

    @Test
    public void extract_from_string_3_values() throws Exception {

        XContentBuilder xb = jsonBuilder()
                .startObject()
                .startObject("fs")
                .field("includes", "*.txt,*.doc,*.xls")
                .endObject()
                .endObject();
        String jsonContent = xb.string();
        Map<String, Object> map = XContentHelper.convertToMap(jsonContent.getBytes(), 0, jsonContent.length(), false).v2();

        String[] includes = FsRiverUtil.buildArrayFromSettings(map, "fs.includes");

        Assert.assertEquals(3, includes.length);
    }

    @Test
    public void extract_from_array_3_values() throws Exception {

        XContentBuilder xb = jsonBuilder()
                .startObject()
                .startObject("fs")
                .startArray("includes")
                .value("*.txt")
                .value("*.doc ")
                .value("*.xls")
                .endArray()
                .endObject()
                .endObject();
        String jsonContent = xb.string();
        Map<String, Object> map = XContentHelper.convertToMap(jsonContent.getBytes(), 0, jsonContent.length(), false).v2();

        String[] includes = FsRiverUtil.buildArrayFromSettings(map, "fs.includes");

        Assert.assertEquals(3, includes.length);
    }

    @Test
    public void extract_from_string_single_value() throws Exception {

        XContentBuilder xb = jsonBuilder()
                .startObject()
                .startObject("fs")
                .field("includes", "*.txt")
                .endObject()
                .endObject();
        String jsonContent = xb.string();
        Map<String, Object> map = XContentHelper.convertToMap(jsonContent.getBytes(), 0, jsonContent.length(), false).v2();

        String[] includes = FsRiverUtil.buildArrayFromSettings(map, "fs.includes");

        Assert.assertEquals(1, includes.length);
    }

    @Test
    public void extract_from_array_single_value() throws Exception {

        XContentBuilder xb = jsonBuilder()
                .startObject()
                .startObject("fs")
                .startArray("includes")
                .value("*.txt")
                .endArray()
                .endObject()
                .endObject();
        String jsonContent = xb.string();
        Map<String, Object> map = XContentHelper.convertToMap(jsonContent.getBytes(), 0, jsonContent.length(), false).v2();

        String[] includes = FsRiverUtil.buildArrayFromSettings(map, "fs.includes");

        Assert.assertEquals(1, includes.length);
    }

    @Test
    public void extract_from_string_no_value() throws Exception {

        XContentBuilder xb = jsonBuilder()
                .startObject()
                .startObject("fs")
                .endObject()
                .endObject();
        String jsonContent = xb.string();
        Map<String, Object> map = XContentHelper.convertToMap(jsonContent.getBytes(), 0, jsonContent.length(), false).v2();

        String[] includes = FsRiverUtil.buildArrayFromSettings(map, "fs.includes");

        Assert.assertEquals(0, includes.length);
    }

    @Test
    public void extract_from_array_no_value() throws Exception {

        XContentBuilder xb = jsonBuilder()
                .startObject()
                .startObject("fs")
                .endObject()
                .endObject();
        String jsonContent = xb.string();
        Map<String, Object> map = XContentHelper.convertToMap(jsonContent.getBytes(), 0, jsonContent.length(), false).v2();

        String[] includes = FsRiverUtil.buildArrayFromSettings(map, "fs.includes");

        Assert.assertEquals(0, includes.length);
    }

    @Test
    public void extract_from_array_empty_value() throws Exception {

        XContentBuilder xb = jsonBuilder()
                .startObject()
                .startObject("fs")
                .startArray("includes")
                .endArray()
                .endObject()
                .endObject();
        String jsonContent = xb.string();
        Map<String, Object> map = XContentHelper.convertToMap(jsonContent.getBytes(), 0, jsonContent.length(), false).v2();

        String[] includes = FsRiverUtil.buildArrayFromSettings(map, "fs.includes");

        Assert.assertEquals(0, includes.length);
    }
}
