package fr.pilato.elasticsearch.crawler.fs.beans;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerIllegalConfigurationException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStream;
import java.util.Iterator;

import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.mapper;

public class DocUtils {

    private static final Logger logger = LogManager.getLogger(DocUtils.class);

    /**
     * Merge a json document with tags
     * @param doc   The document to merge
     * @param tags  The tags to merge
     * @return The merged document
     * @throws FsCrawlerIllegalConfigurationException If the tags can not be parsed
     */
    public static Doc getMergedJsonDoc(Doc doc, InputStream tags) throws FsCrawlerIllegalConfigurationException {
        if (tags == null) {
            return doc;
        }

        try {
            JsonNode tagsNode = mapper.readTree(tags);
            JsonNode docNode = mapper.convertValue(doc, JsonNode.class);
            JsonNode mergedNode = merge(tagsNode, docNode);
            return mapper.treeToValue(mergedNode, Doc.class);
        } catch (Exception e) {
            logger.error("Error parsing tags", e);
            throw new FsCrawlerIllegalConfigurationException("Error parsing tags: " + e.getMessage());
        }
    }

    /**
     * Merges two json nodes into one. Main node overwrites the update node's values in the case if both nodes have the same key.
     * @param mainNode Json node that rules over update node
     * @param updateNode Json node that is subordinate to main node
     * @return the merged nodes
     */
    private static JsonNode merge(JsonNode mainNode, JsonNode updateNode) {
        Iterator<String> fieldNames = updateNode.fieldNames();

        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            JsonNode jsonNode = mainNode.get(fieldName);

            if (jsonNode != null) {
                if (jsonNode.isObject()) {
                    merge(jsonNode, updateNode.get(fieldName));
                } else if (jsonNode.isArray()) {
                    for (int i = 0; i < jsonNode.size(); i++) {
                        merge(jsonNode.get(i), updateNode.get(fieldName).get(i));
                    }
                }
            } else {
                if (mainNode instanceof ObjectNode) {
                    // Overwrite field
                    JsonNode value = updateNode.get(fieldName);
                    ((ObjectNode) mainNode).set(fieldName, value);
                }
            }
        }

        return mainNode;
    }
}
