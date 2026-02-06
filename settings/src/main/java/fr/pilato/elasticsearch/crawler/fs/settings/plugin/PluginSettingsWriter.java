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

package fr.pilato.elasticsearch.crawler.fs.settings.plugin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utility class for writing plugin settings to files.
 * Supports YAML and JSON formats based on file extension.
 */
public class PluginSettingsWriter {

    private static final Logger logger = LogManager.getLogger(PluginSettingsWriter.class);

    private static final ObjectMapper yamlMapper;
    private static final ObjectMapper jsonMapper;

    static {
        // Configure YAML mapper
        YAMLFactory yamlFactory = YAMLFactory.builder()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .build();
        yamlMapper = new ObjectMapper(yamlFactory);
        yamlMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        yamlMapper.enable(SerializationFeature.INDENT_OUTPUT);

        // Configure JSON mapper
        jsonMapper = new ObjectMapper();
        jsonMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        jsonMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Writes plugin settings to a file.
     * The format is determined by the file extension (.yaml/.yml for YAML, .json for JSON).
     *
     * @param settings The settings to write
     * @param configFile The target file path
     * @throws IOException if the file cannot be written
     */
    public static void write(PluginSettings settings, Path configFile) throws IOException {
        // Ensure parent directory exists
        Path parentDir = configFile.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }

        String extension = PluginSettingsLoader.getFileExtension(configFile);
        ObjectMapper mapper = getMapperForExtension(extension);

        logger.debug("Writing plugin settings to [{}]", configFile);
        mapper.writeValue(configFile.toFile(), settings);
        logger.debug("Successfully wrote plugin settings to [{}]", configFile);
    }

    /**
     * Converts plugin settings to a YAML string.
     *
     * @param settings The settings to convert
     * @return YAML string representation
     * @throws IOException if conversion fails
     */
    public static String toYaml(PluginSettings settings) throws IOException {
        return yamlMapper.writeValueAsString(settings);
    }

    /**
     * Converts plugin settings to a JSON string.
     *
     * @param settings The settings to convert
     * @return JSON string representation
     * @throws IOException if conversion fails
     */
    public static String toJson(PluginSettings settings) throws IOException {
        return jsonMapper.writeValueAsString(settings);
    }

    /**
     * Gets the appropriate ObjectMapper for the given file extension.
     */
    private static ObjectMapper getMapperForExtension(String extension) {
        return switch (extension) {
            case "json" -> jsonMapper;
            default -> yamlMapper; // Default to YAML for .yaml, .yml, and unknown
        };
    }
}
