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
package fr.pilato.elasticsearch.crawler.fs.framework;

import com.jayway.jsonpath.DocumentContext;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

class JsonUtilTest extends AbstractFSCrawlerTestCase {

    private static final Logger logger = LogManager.getLogger();

    @Test
    void jsonPath() {
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

        DocumentContext context = JsonUtil.parseJsonAsDocumentContext(json);
        Assertions.assertThat((String) context.read("$.attributes.owner")).isEqualTo("dpilato");
        Assertions.assertThat((Integer) context.read("$.attributes.permissions"))
                .isEqualTo(644);
        Assertions.assertThat((Integer) context.read("$.attributes.foobar")).isNull();
        Assertions.assertThat((String) context.read("$.attributes.acl[0].principal"))
                .isEqualTo("dpilato");
        Assertions.assertThat((String) context.read("$.attributes.acl[0].type")).isEqualTo("ALLOW");
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

    /** Minimal bean to exercise {@link InstantDeserializer} through JsonUtil mappers. */
    public static class InstantHolder {
        private Instant scanDate;

        public Instant getScanDate() {
            return scanDate;
        }

        public void setScanDate(Instant scanDate) {
            this.scanDate = scanDate;
        }
    }

    static Stream<ObjectMapper> instantMappers() {
        return Stream.of(JsonUtil.mapper, JsonUtil.prettyMapper, JsonUtil.ymlMapper);
    }

    @ParameterizedTest
    @MethodSource("instantMappers")
    void deserializeInstantWithZ(ObjectMapper mapper) {
        InstantHolder holder = mapper.readValue("{\"scan_date\":\"2016-07-07T08:37:00Z\"}", InstantHolder.class);
        Assertions.assertThat(holder.getScanDate()).isEqualTo(Instant.parse("2016-07-07T08:37:00Z"));
    }

    @ParameterizedTest
    @MethodSource("instantMappers")
    void deserializeInstantWithOffset(ObjectMapper mapper) {
        InstantHolder holder =
                mapper.readValue("{\"scan_date\":\"2022-02-08T21:57:51.000+00:00\"}", InstantHolder.class);
        Assertions.assertThat(holder.getScanDate()).isEqualTo(Instant.parse("2022-02-08T21:57:51Z"));
    }

    @ParameterizedTest
    @MethodSource("instantMappers")
    void deserializeLegacyLocalDateTimeAsInstant(ObjectMapper mapper) {
        InstantHolder holder = mapper.readValue("{\"scan_date\":\"2026-07-02T12:10:57\"}", InstantHolder.class);
        Assertions.assertThat(holder.getScanDate())
                .isEqualTo(LocalDateTime.parse("2026-07-02T12:10:57")
                        .atZone(ZoneId.systemDefault())
                        .toInstant());
    }

    @ParameterizedTest
    @MethodSource("instantMappers")
    void deserializeInstantFromEpochMillis(ObjectMapper mapper) {
        long epochMilli = Instant.parse("2016-07-07T08:37:00Z").toEpochMilli();
        InstantHolder holder = mapper.readValue("{\"scan_date\":" + epochMilli + "}", InstantHolder.class);
        Assertions.assertThat(holder.getScanDate()).isEqualTo(Instant.ofEpochMilli(epochMilli));
    }

    @ParameterizedTest
    @MethodSource("instantMappers")
    void roundTripInstantSerialization(ObjectMapper mapper) {
        InstantHolder source = new InstantHolder();
        source.setScanDate(Instant.parse("2016-07-07T08:37:00Z"));
        InstantHolder roundTrip = mapper.readValue(mapper.writeValueAsString(source), InstantHolder.class);
        Assertions.assertThat(roundTrip.getScanDate()).isEqualTo(source.getScanDate());
    }

    @Test
    void mappersWithStringsArray() {
        // We try with multiple elements in the cities field as an array.
        // Jackson 3 serializes properties in alphabetical order by default, so "cities" comes before "name".
        mapperTester(
                JsonUtil.mapper,
                "{\"cities\":[\"Amsterdam\",\"Tamassint\"],\"name\":\"Netherlands\"}",
                List.of("Amsterdam", "Tamassint"));
        mapperTester(JsonUtil.prettyMapper, """
                {
                  "cities" : [ "Amsterdam", "Tamassint" ],
                  "name" : "Netherlands"
                }""", List.of("Amsterdam", "Tamassint"));
        mapperTester(JsonUtil.ymlMapper, """
                ---
                cities:
                - "Amsterdam"
                - "Tamassint"
                name: "Netherlands"
                """, List.of("Amsterdam", "Tamassint"));
        // We try with one single element in the cities field as an array
        mapperTester(JsonUtil.mapper, "{\"cities\":[\"Amsterdam\"],\"name\":\"Netherlands\"}", List.of("Amsterdam"));
        mapperTester(JsonUtil.prettyMapper, """
                {
                  "cities" : [ "Amsterdam" ],
                  "name" : "Netherlands"
                }""", List.of("Amsterdam"));
        mapperTester(JsonUtil.ymlMapper, """
                ---
                cities:
                - "Amsterdam"
                name: "Netherlands"
                """, List.of("Amsterdam"));
        // We try with one single element in the cities field as a string and this should fail
        Assertions.assertThatThrownBy(() ->
                        JsonUtil.mapper.readValue("{\"name\":\"Netherlands\",\"cities\":\"Amsterdam\"}", Country.class))
                .isInstanceOf(JacksonException.class)
                .hasMessageContaining(
                        "Cannot deserialize value of type `java.util.ArrayList<java.lang.String>` from String value");
        Assertions.assertThatThrownBy(() -> JsonUtil.prettyMapper.readValue(
                        "{\"name\":\"Netherlands\",\"cities\":\"Amsterdam\"}", Country.class))
                .isInstanceOf(JacksonException.class)
                .hasMessageContaining(
                        "Cannot deserialize value of type `java.util.ArrayList<java.lang.String>` from String value");
        Assertions.assertThatThrownBy(() -> JsonUtil.ymlMapper.readValue("""
                        ---
                        name: "Netherlands"
                        cities: "Amsterdam"
                        """, Country.class))
                .isInstanceOf(JacksonException.class)
                .hasMessageContaining(
                        "Cannot deserialize value of type `java.util.ArrayList<java.lang.String>` from String value");
    }

    /**
     * Helper to test a mapper with given input. We also test serialization here and we compare the input with the
     * generated output.
     *
     * @param mapper the mapper to test
     * @param input the input to use
     * @param expectedCities the expected cities list
     */
    private void mapperTester(ObjectMapper mapper, String input, List<String> expectedCities) {
        logger.debug("Testing mapper: {} with {}", mapper.version().toFullString(), input);
        Country country = mapper.readValue(input, Country.class);
        Assertions.assertThat(country.name).isEqualTo("Netherlands");
        Assertions.assertThat(country.cities).hasSize(expectedCities.size());
        Assertions.assertThat(country.cities).containsAll(expectedCities);

        String generated = mapper.writeValueAsString(country);
        logger.debug(generated);
        /*automatically convert all \r\n (Windows) and \n (Unix) to a single \n before comparing the strings, making the test platform-independent.
         */
        Assertions.assertThat(generated).isEqualToNormalizingNewlines(input);
    }
}
