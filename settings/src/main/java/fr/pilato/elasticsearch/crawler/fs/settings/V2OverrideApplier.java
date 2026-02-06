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

import fr.pilato.elasticsearch.crawler.fs.settings.pipeline.OutputSection;
import fr.pilato.elasticsearch.crawler.fs.settings.pipeline.PipelineSection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Applies environment variable and system property overrides to v2 pipeline settings.
 * <p>
 * <b>TEMPORARY CLASS - TO BE REMOVED</b>
 * <p>
 * This class exists as a bridge between the legacy V1 architecture (where the core
 * reads settings from {@code FsSettings.getElasticsearch()}, {@code FsSettings.getFs()}, etc.)
 * and the new V2 plugin architecture (where each plugin loads its own settings from
 * separate configuration files in {@code _settings/inputs/}, {@code _settings/outputs/}, etc.).
 * <p>
 * It handles overrides for inputs, filters, and outputs sections that Gestalt
 * cannot properly merge due to array/map conflicts, and synchronizes legacy settings
 * with the new plugin rawConfig structure.
 * <p>
 * Supported formats:
 * <ul>
 *   <li>Simple env variables: FSCRAWLER_ELASTICSEARCH_SSL_VERIFICATION=false</li>
 *   <li>Indexed env variables: FSCRAWLER_OUTPUTS_0_ELASTICSEARCH_SSL_VERIFICATION=false</li>
 *   <li>System properties: -Doutputs[0].elasticsearch.ssl_verification=false</li>
 * </ul>
 * <p>
 * <b>Removal conditions:</b> This class will be removed once:
 * <ul>
 *   <li>The core components (ElasticsearchClient, FsCrawlerImpl, FsCrawlerManagementService, etc.)
 *       are refactored to use plugin-provided settings instead of {@code FsSettings.getElasticsearch()}</li>
 *   <li>All settings loading goes through per-plugin configuration files with Gestalt</li>
 *   <li>The indexed format (outputs[0].xxx) is no longer needed</li>
 * </ul>
 *
 * @deprecated This is transitional code for the V1 to V2 migration. Will be removed when
 *             the V2 plugin architecture is fully implemented and the core no longer
 *             relies on legacy FsSettings fields (getElasticsearch(), getFs(), etc.).
 */
@Deprecated(since = "2.10", forRemoval = true)
public class V2OverrideApplier {

    private static final Logger logger = LogManager.getLogger();

    private static final String ENV_PREFIX = "FSCRAWLER_";
    
    // Pattern for env vars: OUTPUTS_0_ELASTICSEARCH_SSL_VERIFICATION
    // Captures: section (OUTPUTS), index (0), path (ELASTICSEARCH_SSL_VERIFICATION)
    private static final Pattern ENV_PATTERN = Pattern.compile(
            "^(INPUTS|FILTERS|OUTPUTS)_(\\d+)_(.+)$", Pattern.CASE_INSENSITIVE);
    
    // Pattern for system properties: outputs[0].elasticsearch.ssl_verification
    // Captures: section (outputs), index (0), path (elasticsearch.ssl_verification)
    private static final Pattern PROP_PATTERN = Pattern.compile(
            "^(inputs|filters|outputs)\\[(\\d+)]\\.(.*+)$", Pattern.CASE_INSENSITIVE);

    /**
     * Apply environment variable and system property overrides to v2 settings.
     *
     * @param settings the settings to modify
     */
    public static void applyOverrides(FsSettings settings) {
        // Apply simple environment variable overrides (e.g., FSCRAWLER_ELASTICSEARCH_SSL_VERIFICATION)
        applySimpleEnvOverrides(settings);
        
        // Apply indexed environment variable overrides (e.g., FSCRAWLER_OUTPUTS_0_ELASTICSEARCH_SSL_VERIFICATION)
        applyEnvOverrides(settings);
        
        // Apply system property overrides (higher priority)
        applySystemPropertyOverrides(settings);
    }

    /**
     * Apply simple environment variable overrides directly to legacy settings.
     * Format: FSCRAWLER_ELASTICSEARCH_SSL_VERIFICATION=false
     * Format: FSCRAWLER_FS_URL=/path/to/crawl
     * 
     * This handles the common case where users want to override settings without
     * using the full V2 indexed format (FSCRAWLER_OUTPUTS_0_...).
     */
    private static void applySimpleEnvOverrides(FsSettings settings) {
        for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith(ENV_PREFIX)) {
                continue;
            }
            
            String keyWithoutPrefix = key.substring(ENV_PREFIX.length());
            
            // Skip if it matches the indexed pattern (handled by applyEnvOverrides)
            if (ENV_PATTERN.matcher(keyWithoutPrefix).matches()) {
                continue;
            }
            
            String value = entry.getValue();
            
            // Handle ELASTICSEARCH_* variables
            if (keyWithoutPrefix.startsWith("ELASTICSEARCH_")) {
                String esProperty = keyWithoutPrefix.substring("ELASTICSEARCH_".length()).toLowerCase();
                logger.debug("Applying simple env override to elasticsearch: {} = {}", esProperty, value);
                applyElasticsearchOverride(settings, esProperty, value);
                // Also apply to output sections (V2 format)
                applyToOutputSections(settings, "elasticsearch." + esProperty, value);
            }
            // Handle FS_* variables
            else if (keyWithoutPrefix.startsWith("FS_")) {
                String fsProperty = keyWithoutPrefix.substring("FS_".length()).toLowerCase();
                logger.debug("Applying simple env override to fs: {} = {}", fsProperty, value);
                applyFsOverride(settings, fsProperty, value);
            }
            // Handle REST_* variables
            else if (keyWithoutPrefix.startsWith("REST_")) {
                String restProperty = keyWithoutPrefix.substring("REST_".length()).toLowerCase();
                logger.debug("Applying simple env override to rest: {} = {}", restProperty, value);
                applyRestOverride(settings, restProperty, value);
            }
        }
    }

    /**
     * Apply environment variable overrides.
     * Format: FSCRAWLER_OUTPUTS_0_ELASTICSEARCH_SSL_VERIFICATION=false
     */
    private static void applyEnvOverrides(FsSettings settings) {
        for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith(ENV_PREFIX)) {
                continue;
            }
            
            String keyWithoutPrefix = key.substring(ENV_PREFIX.length());
            Matcher matcher = ENV_PATTERN.matcher(keyWithoutPrefix);
            
            if (matcher.matches()) {
                String section = matcher.group(1).toLowerCase();
                int index = Integer.parseInt(matcher.group(2));
                String rawPath = matcher.group(3).toLowerCase();
                // Convert env var format: ELASTICSEARCH_SSL_VERIFICATION -> elasticsearch.ssl_verification
                // Only the first underscore becomes a dot (separates plugin type from property)
                String path = convertEnvPathToPropertyPath(rawPath);
                String value = entry.getValue();
                
                logger.debug("Applying env override: {}[{}].{} = {}", section, index, path, value);
                applyOverride(settings, section, index, path, value);
            }
        }
    }

    /**
     * Convert environment variable path format to property path format.
     * ELASTICSEARCH_SSL_VERIFICATION -> elasticsearch.ssl_verification
     * LOCAL_PATH -> local.path
     * TIKA_OCR_ENABLED -> tika.ocr.enabled (nested properties use dots)
     */
    private static String convertEnvPathToPropertyPath(String envPath) {
        // Known plugin types that mark the first segment
        String[] pluginTypes = {"elasticsearch", "local", "tika", "json", "xml", "none", "ssh", "ftp", "s3", "http"};
        
        for (String pluginType : pluginTypes) {
            if (envPath.startsWith(pluginType + "_")) {
                String remaining = envPath.substring(pluginType.length() + 1);
                // For nested properties (like ocr_enabled), convert underscores to dots
                // But keep underscores in property names (like ssl_verification)
                String propertyPath = convertNestedProperty(remaining);
                return pluginType + "." + propertyPath;
            }
        }
        
        // Unknown format, just replace all underscores with dots
        return envPath.replace("_", ".");
    }

    /**
     * Convert nested property names, keeping underscores within known property names.
     * ssl_verification -> ssl_verification (keep underscore)
     * ocr_enabled -> ocr.enabled (nested property)
     * ocr_pdf_strategy -> ocr.pdf_strategy (nested with underscore in name)
     */
    private static String convertNestedProperty(String path) {
        // Known nested sections that should have a dot before them
        String[] nestedSections = {"ocr"};
        
        for (String nested : nestedSections) {
            if (path.startsWith(nested + "_")) {
                String remaining = path.substring(nested.length() + 1);
                return nested + "." + remaining;
            }
        }
        
        // No nested section found, return as-is (keeping underscores)
        return path;
    }

    /**
     * Apply system property overrides.
     * Format: -Doutputs[0].elasticsearch.ssl_verification=false
     */
    private static void applySystemPropertyOverrides(FsSettings settings) {
        for (String key : System.getProperties().stringPropertyNames()) {
            Matcher matcher = PROP_PATTERN.matcher(key);
            
            if (matcher.matches()) {
                String section = matcher.group(1).toLowerCase();
                int index = Integer.parseInt(matcher.group(2));
                String path = matcher.group(3);
                String value = System.getProperty(key);
                
                logger.debug("Applying system property override: {}[{}].{} = {}", section, index, path, value);
                applyOverride(settings, section, index, path, value);
            }
        }
    }

    /**
     * Apply a single override to the appropriate section.
     */
    private static void applyOverride(FsSettings settings, String section, int index, String path, String value) {
        List<? extends PipelineSection> sections = switch (section) {
            case "inputs" -> settings.getInputs();
            case "filters" -> settings.getFilters();
            case "outputs" -> settings.getOutputs();
            default -> null;
        };
        
        if (sections == null || index >= sections.size()) {
            logger.warn("Cannot apply override to {}[{}]: section not found or index out of bounds", section, index);
            return;
        }
        
        PipelineSection pipelineSection = sections.get(index);
        setNestedValue(pipelineSection, path, value);
        
        // Also apply to legacy v1 settings for backward compatibility
        applyToLegacySettings(settings, section, path, value);
    }

    /**
     * Apply override to all output sections that have elasticsearch type.
     * This ensures V2 format output plugins also receive the override.
     */
    private static void applyToOutputSections(FsSettings settings, String path, String value) {
        if (settings.getOutputs() == null) {
            return;
        }
        
        for (OutputSection output : settings.getOutputs()) {
            if ("elasticsearch".equals(output.getType())) {
                setNestedValue(output, path, value);
                logger.debug("Applied override to output section [{}]: {} = {}", output.getId(), path, value);
            }
        }
    }

    /**
     * Apply overrides to legacy v1 settings for backward compatibility.
     * This ensures that code using settings.getElasticsearch() still gets the overridden values.
     */
    private static void applyToLegacySettings(FsSettings settings, String section, String path, String value) {
        if ("outputs".equals(section) && path.startsWith("elasticsearch.")) {
            String esPath = path.substring("elasticsearch.".length());
            applyElasticsearchOverride(settings, esPath, value);
        } else if ("inputs".equals(section) && path.startsWith("local.")) {
            String fsPath = path.substring("local.".length());
            applyFsOverride(settings, fsPath, value);
        }
    }

    /**
     * Apply override to legacy Elasticsearch settings.
     */
    private static void applyElasticsearchOverride(FsSettings settings, String path, String value) {
        if (settings.getElasticsearch() == null) {
            settings.setElasticsearch(new Elasticsearch());
        }
        
        Elasticsearch es = settings.getElasticsearch();
        switch (path.replace("_", "").toLowerCase()) {
            case "sslverification" -> es.setSslVerification(Boolean.parseBoolean(value));
            case "index" -> es.setIndex(value);
            case "indexfolder" -> es.setIndexFolder(value);
            case "bulksize" -> es.setBulkSize(Integer.parseInt(value));
            case "pushtemplates" -> es.setPushTemplates(Boolean.parseBoolean(value));
            case "pipeline" -> es.setPipeline(value);
            case "username" -> es.setUsername(value);
            case "password" -> es.setPassword(value);
            case "apikey" -> es.setApiKey(value);
            case "cacertificate" -> es.setCaCertificate(value);
            case "pathprefix" -> es.setPathPrefix(value);
            default -> logger.trace("Unknown elasticsearch property for legacy override: {}", path);
        }
        logger.debug("Applied legacy elasticsearch override: {} = {}", path, value);
    }

    /**
     * Apply override to legacy Fs settings.
     */
    private static void applyFsOverride(FsSettings settings, String path, String value) {
        if (settings.getFs() == null) {
            settings.setFs(new Fs());
        }
        
        Fs fs = settings.getFs();
        switch (path.replace("_", "").toLowerCase()) {
            case "path", "url" -> fs.setUrl(value);
            case "updaterate" -> fs.setUpdateRate(fr.pilato.elasticsearch.crawler.fs.framework.TimeValue.parseTimeValue(value));
            case "indexcontent" -> fs.setIndexContent(Boolean.parseBoolean(value));
            case "langdetect" -> fs.setLangDetect(Boolean.parseBoolean(value));
            case "storesource" -> fs.setStoreSource(Boolean.parseBoolean(value));
            case "followsymlinks" -> fs.setFollowSymlinks(Boolean.parseBoolean(value));
            case "jsonsupport" -> fs.setJsonSupport(Boolean.parseBoolean(value));
            case "xmlsupport" -> fs.setXmlSupport(Boolean.parseBoolean(value));
            default -> logger.trace("Unknown fs property for legacy override: {}", path);
        }
        logger.debug("Applied legacy fs override: {} = {}", path, value);
    }

    /**
     * Apply override to Rest settings.
     */
    private static void applyRestOverride(FsSettings settings, String path, String value) {
        if (settings.getRest() == null) {
            settings.setRest(new Rest());
        }
        
        Rest rest = settings.getRest();
        switch (path.replace("_", "").toLowerCase()) {
            case "url" -> rest.setUrl(value);
            case "enablecors" -> rest.setEnableCors(Boolean.parseBoolean(value));
            default -> logger.trace("Unknown rest property for legacy override: {}", path);
        }
        logger.debug("Applied legacy rest override: {} = {}", path, value);
    }

    /**
     * Set a nested value in a pipeline section.
     * Path format: "elasticsearch.ssl_verification" or "local.path"
     */
    private static void setNestedValue(PipelineSection section, String path, String value) {
        String[] parts = path.split("\\.", 2);
        
        if (parts.length < 2) {
            // Direct property on section (like "type", "id", "when")
            setDirectProperty(section, path, value);
            return;
        }
        
        String pluginType = parts[0];
        String propertyPath = parts[1];
        
        // Ensure rawConfig exists
        Map<String, Object> rawConfig = section.getRawConfig();
        if (rawConfig == null) {
            rawConfig = new HashMap<>();
            section.setRawConfig(rawConfig);
        }
        
        // Get or create the plugin config map
        @SuppressWarnings("unchecked")
        Map<String, Object> pluginConfig = (Map<String, Object>) rawConfig.computeIfAbsent(
                pluginType, k -> new HashMap<String, Object>());
        
        // Set the nested value
        setNestedMapValue(pluginConfig, propertyPath, parseValue(value));
        
        logger.trace("Set {}[{}].{} = {} in rawConfig", section.getType(), section.getId(), path, value);
    }

    /**
     * Set a direct property on the section (type, id, when, update_rate, etc.)
     */
    private static void setDirectProperty(PipelineSection section, String property, String value) {
        switch (property.toLowerCase().replace("_", "")) {
            case "type" -> section.setType(value);
            case "id" -> section.setId(value);
            case "when" -> section.setWhen(value);
            default -> logger.warn("Unknown direct property: {}", property);
        }
    }

    /**
     * Set a nested value in a map, creating intermediate maps as needed.
     * Path format: "ssl_verification" or "ocr.enabled"
     */
    @SuppressWarnings("unchecked")
    private static void setNestedMapValue(Map<String, Object> map, String path, Object value) {
        String[] parts = path.split("\\.", 2);
        String key = toSnakeCase(parts[0]);
        
        if (parts.length == 1) {
            // Final key
            map.put(key, value);
        } else {
            // Intermediate key - create nested map if needed
            Map<String, Object> nested = (Map<String, Object>) map.computeIfAbsent(
                    key, k -> new HashMap<String, Object>());
            setNestedMapValue(nested, parts[1], value);
        }
    }

    /**
     * Convert camelCase or snake_case to snake_case.
     */
    private static String toSnakeCase(String input) {
        // Already snake_case or lowercase
        if (input.contains("_") || input.equals(input.toLowerCase())) {
            return input.toLowerCase();
        }
        // Convert camelCase to snake_case
        return input.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    /**
     * Parse a string value into the appropriate type.
     */
    private static Object parseValue(String value) {
        if (value == null) {
            return null;
        }
        
        // Boolean
        if (value.equalsIgnoreCase("true")) {
            return true;
        }
        if (value.equalsIgnoreCase("false")) {
            return false;
        }
        
        // Integer
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
        }
        
        // Long
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
        }
        
        // Double
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
        }
        
        // String (default)
        return value;
    }
}
