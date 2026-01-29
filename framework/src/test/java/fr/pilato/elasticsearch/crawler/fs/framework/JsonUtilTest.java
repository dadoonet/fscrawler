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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.DocumentContext;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.parseJsonAsDocumentContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JsonUtilTest extends AbstractFSCrawlerTestCase {

    private static final Logger logger = LogManager.getLogger();

    @Test
    public void jsonPath() {
        String json = """
                {
                   "content":"Some Text",
                   "file":{
                      "extension":"txt",
                      "content_type":"text/plain; charset=ISO-8859-1",
                      "created":"2022-02-08T21:57:51.000+00:00",
                      "last_modified":"2022-02-08T21:57:51.394+00:00",
                      "last_accessed":"2022-02-08T21:57:51.394+00:00",
                      "indexing_date":"2022-02-08T21:57:52.033+00:00",
                      "filesize":12230,
                      "filename":"roottxtfile.txt",
                      "url":"file:///var/folders/xn/47mdpxd12vq4zrjhkwbhd5_r0000gn/T/junit16929133427221182897/resources/test_attributes/roottxtfile.txt"
                   },
                   "path":{
                      "root":"e366ee2f42db246720b82a82fdb4e15e",
                      "virtual":"/roottxtfile.txt",
                      "real":"/var/folders/xn/47mdpxd12vq4zrjhkwbhd5_r0000gn/T/junit16929133427221182897/resources/test_attributes/roottxtfile.txt"
                   },
                   "attributes":{
                      "owner":"dpilato",
                      "group":"staff",
                      "permissions":644,
                      "foobar":null,
                      "acl":[{
                         "principal":"dpilato",
                         "type":"ALLOW",
                         "permissions":["READ_DATA"],
                         "flags":["FILE_INHERIT"]
                      }]
                   }
                }""";

        DocumentContext context = parseJsonAsDocumentContext(json);
        assertThat((String) context.read("$.attributes.owner")).isEqualTo("dpilato");
        assertThat((Integer) context.read("$.attributes.permissions")).isEqualTo(644);
        assertThat((Integer) context.read("$.attributes.foobar")).isNull();
        assertThat((String) context.read("$.attributes.acl[0].principal")).isEqualTo("dpilato");
        assertThat((String) context.read("$.attributes.acl[0].type")).isEqualTo("ALLOW");
    }

    public static class Country {
        private String name;
        private List<String> cities;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<String> getCities() {
            return cities;
        }

        public void setCities(List<String> cities) {
            this.cities = cities;
        }
    }

    @Test
    public void mappersWithStringsArray() throws IOException {
        // We try with multiple elements in the cities field as an array
        mapperTester(JsonUtil.mapper, "{\"name\":\"Netherlands\",\"cities\":[\"Amsterdam\",\"Tamassint\"]}",
                List.of("Amsterdam", "Tamassint"));
        mapperTester(JsonUtil.prettyMapper, "{\n" +
                "  \"name\" : \"Netherlands\",\n" +
                "  \"cities\" : [ \"Amsterdam\", \"Tamassint\" ]\n" +
                "}",
                List.of("Amsterdam", "Tamassint"));
        mapperTester(JsonUtil.ymlMapper, "---\n" +
                "name: \"Netherlands\"\n" +
                "cities:\n" +
                "- \"Amsterdam\"\n" +
                "- \"Tamassint\"\n",
                List.of("Amsterdam", "Tamassint"));
        // We try with one single element in the cities field as an array
        mapperTester(JsonUtil.mapper, "{\"name\":\"Netherlands\",\"cities\":[\"Amsterdam\"]}",
                List.of("Amsterdam"));
        mapperTester(JsonUtil.prettyMapper, "{\n" +
                        "  \"name\" : \"Netherlands\",\n" +
                        "  \"cities\" : [ \"Amsterdam\" ]\n" +
                        "}",
                List.of("Amsterdam"));
        mapperTester(JsonUtil.ymlMapper, "---\n" +
                "name: \"Netherlands\"\n" +
                "cities:\n" +
                "- \"Amsterdam\"\n",
                List.of("Amsterdam"));
        // We try with one single element in the cities field as a string and this should fail
        assertThatThrownBy(() -> JsonUtil.mapper.readValue( "{\"name\":\"Netherlands\",\"cities\":\"Amsterdam\"}", Country.class))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Cannot construct instance of `java.util.ArrayList` (although at least one Creator exists)");
        assertThatThrownBy(() -> JsonUtil.prettyMapper.readValue( "{\"name\":\"Netherlands\",\"cities\":\"Amsterdam\"}", Country.class))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Cannot construct instance of `java.util.ArrayList` (although at least one Creator exists)");
        assertThatThrownBy(() -> JsonUtil.ymlMapper.readValue( "---\n" +
                "name: \"Netherlands\"\n" +
                "cities: \"Amsterdam\"\n", Country.class))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Cannot construct instance of `java.util.ArrayList` (although at least one Creator exists)");
    }

    /**
     * Helper to test a mapper with given input. We also test serialization here and we compare the input with the
     * generated output.
     * @param mapper the mapper to test
     * @param input  the input to use
     * @param expectedCities the expected cities list
     * @throws IOException in case of error
     */
    private void mapperTester(ObjectMapper mapper, String input, List<String> expectedCities) throws IOException {
        logger.debug("Testing mapper: {} with {}", mapper.version().toFullString(), input);
        Country country = mapper.readValue(input, Country.class);
        assertThat(country.name).isEqualTo("Netherlands");
        assertThat(country.cities).hasSize(expectedCities.size());
        assertThat(country.cities).containsAll(expectedCities);

        String generated = mapper.writeValueAsString(country);
        logger.debug(generated);
		/*automatically convert all \r\n (Windows) and \n (Unix) to a single \n before comparing the strings, making the test platform-independent.
		*/
		assertThat(generated).isEqualToNormalizingNewlines(input);
    }
}
