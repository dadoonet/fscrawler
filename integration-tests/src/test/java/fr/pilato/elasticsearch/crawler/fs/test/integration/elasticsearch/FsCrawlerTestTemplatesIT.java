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

package fr.pilato.elasticsearch.crawler.fs.test.integration.elasticsearch;

import fr.pilato.elasticsearch.crawler.fs.FsCrawlerImpl;
import fr.pilato.elasticsearch.crawler.fs.framework.ExponentialBackoffPollInterval;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import org.junit.Test;

import java.time.Duration;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Test template management settings
 * See <a href="https://github.com/dadoonet/fscrawler/issues/1797">https://github.com/dadoonet/fscrawler/issues/1797</a>
 */
public class FsCrawlerTestTemplatesIT extends AbstractFsCrawlerITCase {

    /**
     * Test that an existing component template is preserved when FSCrawler starts
     * (default behavior with force_push_templates = false)
     */
    @Test
    public void existing_component_template_is_preserved() throws Exception {
        // Create settings first to get the correct index name
        FsSettings fsSettings = createTestSettings();
        String indexName = fsSettings.getElasticsearch().getIndex();
        String componentTemplateName = "fscrawler_" + indexName + "_mapping_content";

        // Create a custom component template with a French analyzer before starting FSCrawler
        String customComponentTemplate = "{\n" +
                "  \"template\": {\n" +
                "    \"mappings\": {\n" +
                "      \"properties\": {\n" +
                "        \"content\": {\n" +
                "          \"type\": \"text\",\n" +
                "          \"analyzer\": \"french\"\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}";
        client.pushComponentTemplate(componentTemplateName, customComponentTemplate);

        // Verify the component template exists
        assertThat(client.isExistingComponentTemplate(componentTemplateName)).isTrue();

        // Start FSCrawler with loop = 0 (no document indexing, just template creation)
        crawler = new FsCrawlerImpl(metadataDir, fsSettings, 0);
        crawler.start();

        // Wait for the crawler to complete
        await()
                .pollInterval(ExponentialBackoffPollInterval.exponential(Duration.ofMillis(100), Duration.ofSeconds(5)))
                .atMost(30, SECONDS)
                .until(() -> crawler.getFsParser().isClosed());

        // Verify the component template still exists
        assertThat(client.isExistingComponentTemplate(componentTemplateName)).isTrue();

        // Verify that the custom component template content is preserved by checking
        // that the "french" analyzer is still defined
        String templateContent = client.performLowLevelRequest("GET", "_component_template/" + componentTemplateName, null);
        assertThat(templateContent).contains("french");
    }

    /**
     * Test that force_push_templates = true overrides existing component templates
     */
    @Test
    public void force_push_templates_overrides_existing() throws Exception {
        // Create settings first to get the correct index name
        FsSettings fsSettings = createTestSettings();
        String indexName = fsSettings.getElasticsearch().getIndex();
        String componentTemplateName = "fscrawler_" + indexName + "_mapping_content";

        // Create a custom component template with a French analyzer before starting FSCrawler
        String customComponentTemplate = "{\n" +
                "  \"template\": {\n" +
                "    \"mappings\": {\n" +
                "      \"properties\": {\n" +
                "        \"content\": {\n" +
                "          \"type\": \"text\",\n" +
                "          \"analyzer\": \"french\"\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}";
        client.pushComponentTemplate(componentTemplateName, customComponentTemplate);

        // Verify the component template exists with french analyzer
        assertThat(client.isExistingComponentTemplate(componentTemplateName)).isTrue();
        String templateContentBefore = client.performLowLevelRequest("GET", "_component_template/" + componentTemplateName, null);
        assertThat(templateContentBefore).contains("french");

        // Start FSCrawler with force_push_templates = true
        fsSettings.getElasticsearch().setForcePushTemplates(true);
        crawler = new FsCrawlerImpl(metadataDir, fsSettings, 0);
        crawler.start();

        // Wait for the crawler to complete
        await()
                .pollInterval(ExponentialBackoffPollInterval.exponential(Duration.ofMillis(100), Duration.ofSeconds(5)))
                .atMost(30, SECONDS)
                .until(() -> crawler.getFsParser().isClosed());

        // Verify the component template was overridden (french analyzer should be gone)
        String templateContentAfter = client.performLowLevelRequest("GET", "_component_template/" + componentTemplateName, null);
        assertThat(templateContentAfter).doesNotContain("french");
    }

    /**
     * Test that templates are skipped when the index template already exists
     */
    @Test
    public void templates_skipped_when_index_template_exists() throws Exception {
        // Create settings first to get the correct index name
        FsSettings fsSettings = createTestSettings();
        String indexName = fsSettings.getElasticsearch().getIndex();
        String indexTemplateName = "fscrawler_" + indexName + "_docs";
        String componentTemplateName = "fscrawler_" + indexName + "_mapping_content";

        // First, run FSCrawler to create all templates
        crawler = new FsCrawlerImpl(metadataDir, fsSettings, 0);
        crawler.start();

        // Wait for the crawler to complete
        await()
                .pollInterval(ExponentialBackoffPollInterval.exponential(Duration.ofMillis(100), Duration.ofSeconds(5)))
                .atMost(30, SECONDS)
                .until(() -> crawler.getFsParser().isClosed());

        // Verify the index template exists
        assertThat(client.isExistingIndexTemplate(indexTemplateName)).isTrue();

        // Close the crawler
        crawler.close();
        crawler = null;

        // Now modify the component template manually
        String customComponentTemplate = "{\n" +
                "  \"template\": {\n" +
                "    \"mappings\": {\n" +
                "      \"properties\": {\n" +
                "        \"content\": {\n" +
                "          \"type\": \"text\",\n" +
                "          \"analyzer\": \"german\"\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}";
        client.pushComponentTemplate(componentTemplateName, customComponentTemplate);

        // Verify the component template has the German analyzer
        String templateContentBefore = client.performLowLevelRequest("GET", "_component_template/" + componentTemplateName, null);
        assertThat(templateContentBefore).contains("german");

        // Start FSCrawler again - it should skip template management
        crawler = new FsCrawlerImpl(metadataDir, fsSettings, 0);
        crawler.start();

        // Wait for the crawler to complete
        await()
                .pollInterval(ExponentialBackoffPollInterval.exponential(Duration.ofMillis(100), Duration.ofSeconds(5)))
                .atMost(30, SECONDS)
                .until(() -> crawler.getFsParser().isClosed());

        // Verify the component template was NOT overridden (german analyzer should still be there)
        String templateContentAfter = client.performLowLevelRequest("GET", "_component_template/" + componentTemplateName, null);
        assertThat(templateContentAfter).contains("german");
    }
}
