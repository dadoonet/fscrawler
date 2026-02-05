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

package fr.pilato.elasticsearch.crawler.fs.settings;

import fr.pilato.elasticsearch.crawler.fs.settings.pipeline.FilterSection;
import fr.pilato.elasticsearch.crawler.fs.settings.pipeline.InputSection;
import fr.pilato.elasticsearch.crawler.fs.settings.pipeline.OutputSection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Migrator for FsSettings from v1 (legacy) format to v2 (pipeline) format.
 * Also handles normalization from singular (input/filter/output) to plural (inputs/filters/outputs).
 */
public class FsSettingsMigrator {

    private static final Logger logger = LogManager.getLogger(FsSettingsMigrator.class);

    public static final int VERSION_1 = 1;
    public static final int VERSION_2 = 2;

    /**
     * Detects the settings format version.
     * 
     * @param settings The settings to check
     * @return VERSION_1 if legacy format (fs/server at root), VERSION_2 if pipeline format
     */
    public static int detectVersion(FsSettings settings) {
        // If version is explicitly set, use it
        if (settings.getVersion() != null) {
            return settings.getVersion();
        }
        
        // Legacy format has fs or server at root level
        if (settings.getFs() != null || settings.getServer() != null) {
            return VERSION_1;
        }
        
        // New format has inputs/filters/outputs
        if (settings.getInputs() != null ||
            settings.getFilters() != null ||
            settings.getOutputs() != null) {
            return VERSION_2;
        }
        
        // Default to v2 for new configurations
        return VERSION_2;
    }

    /**
     * Migrates v1 (legacy) settings to v2 (pipeline) format.
     * The original settings object is not modified.
     * 
     * @param v1Settings The v1 settings to migrate
     * @return A new FsSettings object in v2 format
     */
    public static FsSettings migrateV1ToV2(FsSettings v1Settings) {
        logger.debug("Migrating settings from v1 to v2 format");
        
        FsSettings v2 = new FsSettings();
        v2.setVersion(VERSION_2);
        v2.setName(v1Settings.getName());
        
        // Keep legacy settings for fallback during transition
        v2.setFs(v1Settings.getFs());
        v2.setServer(v1Settings.getServer());
        v2.setElasticsearch(v1Settings.getElasticsearch());
        v2.setRest(v1Settings.getRest());
        v2.setTags(v1Settings.getTags());
        
        // Create input section
        String inputType = determineInputType(v1Settings);
        InputSection inputSection = createInputSection(inputType, v1Settings);
        inputSection.setId("default");
        v2.setInputs(List.of(inputSection));
        
        // Create filter section
        String filterType = determineFilterType(v1Settings);
        FilterSection filterSection = createFilterSection(filterType, v1Settings);
        filterSection.setId("default");
        v2.setFilters(List.of(filterSection));
        
        // Create output section
        OutputSection outputSection = createOutputSection(v1Settings);
        outputSection.setId("default");
        v2.setOutputs(List.of(outputSection));
        
        return v2;
    }

    /**
     * Determines the input type from v1 settings.
     * Priority: fs.provider > server.protocol > "local"
     */
    private static String determineInputType(FsSettings v1Settings) {
        // Check if provider is explicitly set
        if (v1Settings.getFs() != null && v1Settings.getFs().getProvider() != null) {
            return v1Settings.getFs().getProvider();
        }
        
        // Check server protocol
        if (v1Settings.getServer() != null && v1Settings.getServer().getProtocol() != null) {
            return v1Settings.getServer().getProtocol();
        }
        
        // Default to local
        return Server.PROTOCOL.LOCAL;
    }

    /**
     * Determines the filter type from v1 settings.
     * JSON and XML support take precedence, otherwise defaults to Tika.
     */
    private static String determineFilterType(FsSettings v1Settings) {
        if (v1Settings.getFs() == null) {
            return "tika";
        }
        
        Fs fs = v1Settings.getFs();
        
        // If indexContent is false and OCR is disabled, no content parsing is needed
        if (!fs.isIndexContent()) {
            Ocr ocr = fs.getOcr();
            if (ocr == null || !ocr.isEnabled()) {
                return "none";
            }
        }
        
        if (fs.isJsonSupport()) {
            return "json";
        }
        
        if (fs.isXmlSupport()) {
            return "xml";
        }
        
        return "tika";
    }

    /**
     * Creates an InputSection from v1 settings.
     */
    private static InputSection createInputSection(String type, FsSettings v1Settings) {
        InputSection section = new InputSection();
        section.setType(type);
        
        Fs fs = v1Settings.getFs();
        if (fs != null) {
            if (fs.getUpdateRate() != null) {
                section.setUpdateRate(fs.getUpdateRate().toString());
            }
            section.setIncludes(fs.getIncludes());
            section.setExcludes(fs.getExcludes());
        }
        
        // Build raw config for the specific input type
        Map<String, Object> rawConfig = new HashMap<>();
        Map<String, Object> typeConfig = new HashMap<>();
        
        // Add path from fs.url
        if (fs != null && fs.getUrl() != null) {
            typeConfig.put("path", fs.getUrl());
        }
        
        // Add server-specific config for remote protocols
        Server server = v1Settings.getServer();
        if (server != null) {
            if (server.getHostname() != null) {
                typeConfig.put("hostname", server.getHostname());
            }
            if (server.getPort() > 0) {
                typeConfig.put("port", server.getPort());
            }
            if (server.getUsername() != null) {
                typeConfig.put("username", server.getUsername());
            }
            if (server.getPassword() != null) {
                typeConfig.put("password", server.getPassword());
            }
            if (server.getPemPath() != null) {
                typeConfig.put("pem_path", server.getPemPath());
            }
        }
        
        rawConfig.put(type, typeConfig);
        section.setRawConfig(rawConfig);
        
        return section;
    }

    /**
     * Creates a FilterSection from v1 settings.
     */
    private static FilterSection createFilterSection(String type, FsSettings v1Settings) {
        FilterSection section = new FilterSection();
        section.setType(type);
        
        Map<String, Object> rawConfig = new HashMap<>();
        Map<String, Object> typeConfig = new HashMap<>();
        
        Fs fs = v1Settings.getFs();
        if (fs != null) {
            if ("tika".equals(type)) {
                typeConfig.put("index_content", fs.isIndexContent());
                if (fs.getIndexedChars() != null) {
                    typeConfig.put("indexed_chars", fs.getIndexedChars().toString());
                }
                typeConfig.put("lang_detect", fs.isLangDetect());
                typeConfig.put("store_source", fs.isStoreSource());
                
                // OCR config
                if (fs.getOcr() != null) {
                    Map<String, Object> ocrConfig = new HashMap<>();
                    Ocr ocr = fs.getOcr();
                    ocrConfig.put("enabled", ocr.isEnabled());
                    if (ocr.getLanguage() != null) {
                        ocrConfig.put("language", ocr.getLanguage());
                    }
                    if (ocr.getPdfStrategy() != null) {
                        ocrConfig.put("pdf_strategy", ocr.getPdfStrategy());
                    }
                    typeConfig.put("ocr", ocrConfig);
                }
                
                if (fs.getTikaConfigPath() != null) {
                    typeConfig.put("tika_config_path", fs.getTikaConfigPath());
                }
            } else if ("json".equals(type)) {
                typeConfig.put("add_as_inner_object", fs.isAddAsInnerObject());
            }
            // XML filter doesn't have specific config from v1
        }
        
        rawConfig.put(type, typeConfig);
        section.setRawConfig(rawConfig);
        
        return section;
    }

    /**
     * Creates an OutputSection from v1 settings.
     */
    private static OutputSection createOutputSection(FsSettings v1Settings) {
        OutputSection section = new OutputSection();
        section.setType("elasticsearch");
        
        Map<String, Object> rawConfig = new HashMap<>();
        Map<String, Object> esConfig = new HashMap<>();
        
        Elasticsearch es = v1Settings.getElasticsearch();
        if (es != null) {
            if (es.getUrls() != null && !es.getUrls().isEmpty()) {
                esConfig.put("urls", new ArrayList<>(es.getUrls()));
            }
            if (es.getIndex() != null) {
                esConfig.put("index", es.getIndex());
            }
            if (es.getIndexFolder() != null) {
                esConfig.put("index_folder", es.getIndexFolder());
            }
            if (es.getApiKey() != null) {
                esConfig.put("api_key", es.getApiKey());
            }
            if (es.getUsername() != null) {
                esConfig.put("username", es.getUsername());
            }
            if (es.getPassword() != null) {
                esConfig.put("password", es.getPassword());
            }
            if (es.getPipeline() != null) {
                esConfig.put("pipeline", es.getPipeline());
            }
            esConfig.put("ssl_verification", es.isSslVerification());
        }
        
        rawConfig.put("elasticsearch", esConfig);
        section.setRawConfig(rawConfig);
        
        return section;
    }

    /**
     * Generates a YAML representation of the v2 settings for display to the user.
     * This is a simplified YAML output for migration purposes.
     * 
     * @param settings The v2 settings to convert
     * @return YAML string representation
     */
    public static String generateV2Yaml(FsSettings settings) {
        StringBuilder yaml = new StringBuilder();
        yaml.append("name: \"").append(settings.getName()).append("\"\n");
        yaml.append("version: 2\n\n");
        
        // Inputs
        if (settings.getInputs() != null && !settings.getInputs().isEmpty()) {
            yaml.append("inputs:\n");
            for (InputSection input : settings.getInputs()) {
                yaml.append("  - type: \"").append(input.getType()).append("\"\n");
                yaml.append("    id: \"").append(input.getId()).append("\"\n");
                
                // Type-specific config
                if (input.getRawConfig() != null && input.getRawConfig().containsKey(input.getType())) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> typeConfig = (Map<String, Object>) input.getRawConfig().get(input.getType());
                    yaml.append("    ").append(input.getType()).append(":\n");
                    appendMapAsYaml(yaml, typeConfig, 6);
                }
                
                if (input.getUpdateRate() != null) {
                    yaml.append("    update_rate: \"").append(input.getUpdateRate()).append("\"\n");
                }
                if (input.getIncludes() != null && !input.getIncludes().isEmpty()) {
                    yaml.append("    includes: ").append(formatList(input.getIncludes())).append("\n");
                }
                if (input.getExcludes() != null && !input.getExcludes().isEmpty()) {
                    yaml.append("    excludes: ").append(formatList(input.getExcludes())).append("\n");
                }
            }
            yaml.append("\n");
        }
        
        // Filters
        if (settings.getFilters() != null && !settings.getFilters().isEmpty()) {
            yaml.append("filters:\n");
            for (FilterSection filter : settings.getFilters()) {
                yaml.append("  - type: \"").append(filter.getType()).append("\"\n");
                yaml.append("    id: \"").append(filter.getId()).append("\"\n");
                
                if (filter.getWhen() != null) {
                    yaml.append("    when: \"").append(filter.getWhen()).append("\"\n");
                }
                
                // Type-specific config
                if (filter.getRawConfig() != null && filter.getRawConfig().containsKey(filter.getType())) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> typeConfig = (Map<String, Object>) filter.getRawConfig().get(filter.getType());
                    yaml.append("    ").append(filter.getType()).append(":\n");
                    appendMapAsYaml(yaml, typeConfig, 6);
                }
            }
            yaml.append("\n");
        }
        
        // Outputs
        if (settings.getOutputs() != null && !settings.getOutputs().isEmpty()) {
            yaml.append("outputs:\n");
            for (OutputSection output : settings.getOutputs()) {
                yaml.append("  - type: \"").append(output.getType()).append("\"\n");
                yaml.append("    id: \"").append(output.getId()).append("\"\n");
                
                if (output.getWhen() != null) {
                    yaml.append("    when: \"").append(output.getWhen()).append("\"\n");
                }
                
                // Type-specific config
                if (output.getRawConfig() != null && output.getRawConfig().containsKey(output.getType())) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> typeConfig = (Map<String, Object>) output.getRawConfig().get(output.getType());
                    yaml.append("    ").append(output.getType()).append(":\n");
                    appendMapAsYaml(yaml, typeConfig, 6);
                }
            }
        }
        
        return yaml.toString();
    }

    private static void appendMapAsYaml(StringBuilder yaml, Map<String, Object> map, int indent) {
        String indentStr = " ".repeat(indent);
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map) {
                yaml.append(indentStr).append(entry.getKey()).append(":\n");
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedMap = (Map<String, Object>) value;
                appendMapAsYaml(yaml, nestedMap, indent + 2);
            } else if (value instanceof List) {
                yaml.append(indentStr).append(entry.getKey()).append(": ");
                @SuppressWarnings("unchecked")
                List<String> list = (List<String>) value;
                yaml.append(formatList(list)).append("\n");
            } else if (value instanceof String) {
                yaml.append(indentStr).append(entry.getKey()).append(": \"").append(value).append("\"\n");
            } else if (value != null) {
                yaml.append(indentStr).append(entry.getKey()).append(": ").append(value).append("\n");
            }
        }
    }

    private static String formatList(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("\"").append(list.get(i)).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Generates split YAML configuration files for v2 settings.
     * Creates separate files for common settings, each input, filter, and output.
     * Files are prefixed with numbers to ensure correct loading order.
     * 
     * @param settings The v2 settings to convert
     * @return Map of filename to YAML content
     */
    public static Map<String, String> generateV2SplitFiles(FsSettings settings) {
        Map<String, String> files = new java.util.LinkedHashMap<>();
        
        // 00-common.yaml - name and version
        StringBuilder common = new StringBuilder();
        common.append("# Common settings\n");
        common.append("name: \"").append(settings.getName()).append("\"\n");
        common.append("version: 2\n");
        files.put("00-common.yaml", common.toString());
        
        // 10-input-{id}.yaml - each input using indexed syntax
        if (settings.getInputs() != null) {
            int index = 0;
            for (InputSection input : settings.getInputs()) {
                StringBuilder inputYaml = new StringBuilder();
                inputYaml.append("# Input: ").append(input.getId()).append("\n");
                String prefix = "inputs[" + index + "]";
                inputYaml.append(prefix).append(".type: \"").append(input.getType()).append("\"\n");
                inputYaml.append(prefix).append(".id: \"").append(input.getId()).append("\"\n");
                
                if (input.getUpdateRate() != null) {
                    inputYaml.append(prefix).append(".update_rate: \"").append(input.getUpdateRate()).append("\"\n");
                }
                if (input.getIncludes() != null && !input.getIncludes().isEmpty()) {
                    inputYaml.append(prefix).append(".includes: ").append(formatList(input.getIncludes())).append("\n");
                }
                if (input.getExcludes() != null && !input.getExcludes().isEmpty()) {
                    inputYaml.append(prefix).append(".excludes: ").append(formatList(input.getExcludes())).append("\n");
                }
                
                // Type-specific config using dot notation
                if (input.getRawConfig() != null && input.getRawConfig().containsKey(input.getType())) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> typeConfig = (Map<String, Object>) input.getRawConfig().get(input.getType());
                    appendMapAsDotNotation(inputYaml, prefix + "." + input.getType(), typeConfig);
                }
                
                String filename = String.format("10-input-%s.yaml", sanitizeFilename(input.getId()));
                files.put(filename, inputYaml.toString());
                index++;
            }
        }
        
        // 20-filter-{id}.yaml - each filter using indexed syntax
        if (settings.getFilters() != null) {
            int index = 0;
            for (FilterSection filter : settings.getFilters()) {
                StringBuilder filterYaml = new StringBuilder();
                filterYaml.append("# Filter: ").append(filter.getId()).append("\n");
                String prefix = "filters[" + index + "]";
                filterYaml.append(prefix).append(".type: \"").append(filter.getType()).append("\"\n");
                filterYaml.append(prefix).append(".id: \"").append(filter.getId()).append("\"\n");
                
                if (filter.getWhen() != null) {
                    filterYaml.append(prefix).append(".when: \"").append(filter.getWhen()).append("\"\n");
                }
                
                // Type-specific config using dot notation
                if (filter.getRawConfig() != null && filter.getRawConfig().containsKey(filter.getType())) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> typeConfig = (Map<String, Object>) filter.getRawConfig().get(filter.getType());
                    appendMapAsDotNotation(filterYaml, prefix + "." + filter.getType(), typeConfig);
                }
                
                String filename = String.format("20-filter-%s.yaml", sanitizeFilename(filter.getId()));
                files.put(filename, filterYaml.toString());
                index++;
            }
        }
        
        // 30-output-{id}.yaml - each output using indexed syntax
        if (settings.getOutputs() != null) {
            int index = 0;
            for (OutputSection output : settings.getOutputs()) {
                StringBuilder outputYaml = new StringBuilder();
                outputYaml.append("# Output: ").append(output.getId()).append("\n");
                String prefix = "outputs[" + index + "]";
                outputYaml.append(prefix).append(".type: \"").append(output.getType()).append("\"\n");
                outputYaml.append(prefix).append(".id: \"").append(output.getId()).append("\"\n");
                
                if (output.getWhen() != null) {
                    outputYaml.append(prefix).append(".when: \"").append(output.getWhen()).append("\"\n");
                }
                
                // Type-specific config using dot notation
                if (output.getRawConfig() != null && output.getRawConfig().containsKey(output.getType())) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> typeConfig = (Map<String, Object>) output.getRawConfig().get(output.getType());
                    appendMapAsDotNotation(outputYaml, prefix + "." + output.getType(), typeConfig);
                }
                
                String filename = String.format("30-output-%s.yaml", sanitizeFilename(output.getId()));
                files.put(filename, outputYaml.toString());
                index++;
            }
        }
        
        return files;
    }

    /**
     * Appends a map as dot notation YAML (e.g., prefix.key: value)
     */
    private static void appendMapAsDotNotation(StringBuilder yaml, String prefix, Map<String, Object> map) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = prefix + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedMap = (Map<String, Object>) value;
                appendMapAsDotNotation(yaml, key, nestedMap);
            } else if (value instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> list = (List<String>) value;
                yaml.append(key).append(": ").append(formatList(list)).append("\n");
            } else if (value instanceof String) {
                yaml.append(key).append(": \"").append(value).append("\"\n");
            } else if (value != null) {
                yaml.append(key).append(": ").append(value).append("\n");
            }
        }
    }

    /**
     * Sanitizes a string for use as a filename (removes/replaces problematic characters)
     */
    private static String sanitizeFilename(String name) {
        if (name == null) return "unknown";
        return name.replaceAll("[^a-zA-Z0-9_-]", "_").toLowerCase();
    }
}
