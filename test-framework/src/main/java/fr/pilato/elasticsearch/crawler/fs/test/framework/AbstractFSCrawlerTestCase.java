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
package fr.pilato.elasticsearch.crawler.fs.test.framework;

import com.carrotsearch.randomizedtesting.RandomizedContext;
import com.carrotsearch.randomizedtesting.RandomizedRunner;
import com.carrotsearch.randomizedtesting.annotations.*;
import com.carrotsearch.randomizedtesting.generators.RandomNumbers;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.TimeZone;

import static com.carrotsearch.randomizedtesting.RandomizedTest.randomLocale;
import static com.carrotsearch.randomizedtesting.RandomizedTest.randomTimeZone;
import static org.apache.commons.lang3.StringUtils.split;

@RunWith(RandomizedRunner.class)
@Listeners({FSCrawlerReproduceInfoPrinter.class})
@ThreadLeakScope(ThreadLeakScope.Scope.SUITE)
@ThreadLeakLingering(linger = 5000) // 5 sec lingering
@ThreadLeakFilters(filters = {
        WindowsSpecificThreadFilter.class,
        TestContainerThreadFilter.class,
        JNACleanerThreadFilter.class
})
public abstract class AbstractFSCrawlerTestCase {

    public static final int TIMEOUT_MINUTE_AS_MS = 60 * 1000;

    private static final Logger logger = LogManager.getLogger();
    private static final String RANDOM = "random";
    private static final Locale savedLocale = Locale.getDefault();
    private static final TimeZone savedTimeZone = TimeZone.getDefault();
    protected static final String indexPrefix = getSystemProperty("tests.index.prefix", "");

    @Rule
    public TestName name = new TestName();

    @ClassRule
    public static final TemporaryFolder folder = new TemporaryFolder();
    protected static Path rootTmpDir;

    @BeforeClass
    public static void createTmpDir() throws IOException {
        folder.create();
        rootTmpDir = Paths.get(folder.getRoot().toURI());
    }

    @BeforeClass
    public static void setLocale() {
        String testLocale = getSystemProperty("tests.locale", RANDOM);
        Locale locale = testLocale.equals(RANDOM) ? randomLocale() : new Locale.Builder().setLanguageTag(testLocale).build();
        logger.debug("Running test suite with Locale [{}]", locale);
        Locale.setDefault(locale);
    }

    @AfterClass
    public static void resetLocale() {
        Locale.setDefault(savedLocale);
    }

    @BeforeClass
    public static void setTimeZone() {
        String testTimeZone = getSystemProperty("tests.timezone", RANDOM);
        TimeZone timeZone = testTimeZone.equals(RANDOM) ? randomTimeZone() : TimeZone.getTimeZone(testTimeZone);
        logger.debug("Running test suite with TimeZone [{}]/[{}]", timeZone.getID(), timeZone.getDisplayName());
        TimeZone.setDefault(timeZone);
    }

    @AfterClass
    public static void resetTimeZone() {
        TimeZone.setDefault(savedTimeZone);
    }

    protected String getCurrentTestName() {
        return toUnderscoreCase(name.getMethodName());
    }

    /**
     * Get the crawler name which is also used as the index name.
     * This is a combination of the index prefix, the current class name and the current test name.
     * Note that the index prefix might be empty. It's normally the pull request number if set with -Dtests.index.prefix
     * @return the crawler name to use
     */
    protected String getCrawlerName() {
        return getCrawlerName(this.getClass(), getCurrentTestName());
    }

    /**
     * Get the crawler name from a class and a method name.
     * @param clazz the class to use
     * @param methodName the method name
     * @return the crawler name to use
     */
    protected static String getCrawlerName(Class<?> clazz, String methodName) {
        String testName;
        if (indexPrefix.isEmpty()) {
            testName = "fscrawler_".concat(toUnderscoreCase(clazz.getSimpleName())).concat("_").concat(methodName);
        } else {
            testName = "fscrawler_".concat(indexPrefix).concat("_").concat(toUnderscoreCase(clazz.getSimpleName())).concat("_").concat(methodName);
        }
        return testName.contains(" ") ? split(testName, " ")[0] : testName;
    }

    public static int between(int min, int max) {
        return RandomNumbers.randomIntBetween(RandomizedContext.current().getRandom(), min, max);
    }

    public static String toUnderscoreCase(String value) {
        StringBuilder sb = new StringBuilder();
        boolean changed = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isUpperCase(c)) {
                if (!changed) {
                    sb.setLength(0);
                    // copy it over here
                    for (int j = 0; j < i; j++) {
                        sb.append(value.charAt(j));
                    }
                    changed = true;
                    if (i != 0) {
                        sb.append('_');
                    }
                } else {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                if (changed) {
                    sb.append(c);
                }
            }
        }
        if (!changed) {
            return value;
        }
        return sb.toString();
    }

    public static File URLtoFile(URL url) {
        try {
            return new File(url.toURI());
        } catch(URISyntaxException e) {
            return new File(url.getPath());
        }
    }

    /**
     * Get a System Property. If it does not exist or if it's empty, the
     * fallback value will be returned.
     * @param envName       The system property name
     * @param defaultValue  The fallback value
     * @return              The property value or its default value
     */
    protected static String getSystemProperty(String envName, String defaultValue) {
        String property = System.getProperty(envName);
        if (property == null || property.isBlank()) {
            return defaultValue;
        } else {
            return property;
        }
    }

    /**
     * Get a System Property. If it does not exist or if it's empty, the
     * fallback value will be returned.
     * @param envName       The system property name
     * @param defaultValue  The fallback value
     * @return              The property value or its default value
     */
    protected static int getSystemProperty(String envName, int defaultValue) {
        String property = System.getProperty(envName);
        if (property == null || property.isBlank()) {
            return defaultValue;
        } else {
            return Integer.parseInt(property);
        }
    }

    /**
     * Get a System Property. If it does not exist or if it's empty, the
     * fallback value will be returned.
     * @param envName       The system property name
     * @param defaultValue  The fallback value
     * @return              The property value or its default value
     */
    protected static boolean getSystemProperty(String envName, boolean defaultValue) {
        String property = System.getProperty(envName);
        if (property == null || property.isBlank()) {
            return defaultValue;
        } else {
            return Boolean.parseBoolean(property);
        }
    }
}
