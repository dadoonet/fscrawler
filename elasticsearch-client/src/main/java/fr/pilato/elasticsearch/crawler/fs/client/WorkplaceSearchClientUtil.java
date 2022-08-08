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

import fr.pilato.elasticsearch.crawler.fs.beans.Doc;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public abstract class WorkplaceSearchClientUtil {

    protected static final Logger logger = LogManager.getLogger(WorkplaceSearchClientUtil.class);
    private static final SimpleDateFormat RFC_3339 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZZZZZ");

    private WorkplaceSearchClientUtil() {

    }

    /**
     * Prefix to use when no name for a source is provided
     */
    private static final String DEFAULT_SOURCE_NAME_PREFIX = "Local files for ";

    /**
     * Generate the default custom source name to use. It will be something like:
     * "Local files for job test". The name will be truncated to 64 characters.
     * @param suffix the suffix to append to "Local files for job "
     * @return the custom source name
     */
    public static String generateDefaultCustomSourceName(String suffix) {
        String name = DEFAULT_SOURCE_NAME_PREFIX + suffix;

        logger.debug("generated source name: [{}]", suffix);

        /*
        The name may not be longer than 64 characters.
        */
        if (name.length() > 64) {
            name = name.substring(0, 64);
            logger.warn("The generated name for the job exceeds the Workplace Search limit of 64 characters. " +
                    "It has been truncated to [{}]. You could manually force the name you want to use by setting " +
                    "fs.", name);
        }

        return name;
    }

    public synchronized static String toRFC3339(Date d) {
        if (logger.isDebugEnabled() && d != null) {
            String format = RFC_3339.format(d);
            String finalDate = format.replaceAll("(\\d\\d)(\\d\\d)$", "$1:$2");
            logger.debug("Transforming date to RFC_3339 [{}] -> [{}] -> [{}]", d, format, finalDate);
        }
        return d == null ? null : RFC_3339.format(d).replaceAll("(\\d\\d)(\\d\\d)$", "$1:$2");
    }

    /**
     * Generate a Json ready document from a FSCrawler Doc bean
     * @param id        id of the document
     * @param doc       FSCrawler doc bean
     * @param urlPrefix Prefix to use for url generation
     * @return a JSon object as a Map
     */
    public static Map<String, Object> docToJson(String id, Doc doc, String urlPrefix) {
        Map<String, Object> document = new HashMap<>();
        // Id
        document.put("id", id);

        // Index content
        document.put("body", doc.getContent());

        // Index main metadata
        // We use the name of the file if no title has been found in the document metadata
        document.put("title", FsCrawlerUtil.isNullOrEmpty(doc.getMeta().getTitle()) ? doc.getFile().getFilename() : doc.getMeta().getTitle());
        document.put("author", doc.getMeta().getAuthor());
        document.put("keywords", doc.getMeta().getKeywords());
        document.put("language", doc.getMeta().getLanguage());
        document.put("comments", doc.getMeta().getComments());

        // Index main file attributes
        document.put("name", doc.getFile().getFilename());
        document.put("mime_type", doc.getFile().getContentType());
        document.put("extension", doc.getFile().getExtension());
        document.put("size", doc.getFile().getFilesize());
        document.put("text_size", doc.getFile().getIndexedChars());
        document.put("last_modified", toRFC3339(doc.getFile().getLastModified()));
        document.put("created_at", toRFC3339(doc.getFile().getCreated()));

        // Index main path attributes
        document.put("url", urlPrefix + doc.getPath().getVirtual());
        document.put("path", doc.getPath().getReal());

        return document;
    }
}
