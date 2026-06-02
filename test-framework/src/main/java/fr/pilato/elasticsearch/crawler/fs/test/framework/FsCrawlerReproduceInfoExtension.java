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
package fr.pilato.elasticsearch.crawler.fs.test.framework;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.opentest4j.TestAbortedException;

/**
 * JUnit 6 extension that prints an exact reproduction command line when a test fails, adapted to the original build
 * tool (Maven or Gradle).
 */
public class FsCrawlerReproduceInfoExtension implements AfterTestExecutionCallback {

    /** Annotation to put on test classes to inject additional system properties into the reproduction command. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface Properties {
        String[] value();
    }

    @Override
    public void afterTestExecution(ExtensionContext context) throws Exception {
        // 1. We only handle failures (successes and ignored assumptions are skipped)
        if (context.getExecutionException().isPresent()) {
            Throwable exception = context.getExecutionException().get();
            if (exception instanceof TestAbortedException) {
                return; // Assumption was not met, ignore it.
            }

            StringBuilder commandBuilder = new StringBuilder();
            commandBuilder.append("REPRODUCE WITH:\n");

            // 2. Dynamic detection of the test launcher (command line tool)
            String buildTool = detectBuildTool();
            commandBuilder.append(buildTool);

            // 3. Retrieve class and method names from the JUnit 6 context
            Class<?> testClass = context.getRequiredTestClass();
            String className = testClass.getName();
            String methodName = context.getRequiredTestMethod().getName();

            // 4. Build standardized arguments
            appendOpt(commandBuilder, "tests.class", className);
            appendOpt(commandBuilder, "tests.method", methodName);

            // If timeout is used, we can print which version it was
            Timeout timeoutAnnotation = context.getRequiredTestClass().getAnnotation(Timeout.class);
            if (timeoutAnnotation != null) {
                long seconds = timeoutAnnotation.unit().toSeconds(timeoutAnnotation.value());
                appendOpt(commandBuilder, "tests.timeout", String.valueOf(seconds * 1000)); // En ms
            }

            // Default Java/ES environment options
            appendOpt(commandBuilder, "tests.locale", Locale.getDefault().toLanguageTag());
            appendOpt(commandBuilder, "tests.timezone", TimeZone.getDefault().getID());

            // 5. Recursively scan custom @Properties annotations
            List<String> extraProperties = new ArrayList<>();
            scanProperties(testClass, extraProperties);
            for (String sysProp : extraProperties) {
                String val = System.getProperty(sysProp);
                if (val != null && !val.trim().isEmpty()) {
                    appendOpt(commandBuilder, sysProp, val);
                }
            }

            // 6. Print to standard error output
            System.err.println(commandBuilder);
        }
    }

    /**
     * Inspect the global Java command to infer whether the test was launched by Maven or Gradle. If it cannot be
     * detected (for example from an IDE), fall back to a default Maven syntax.
     */
    private String detectBuildTool() {
        String javaCommand = System.getProperty("sun.java.command", "").toLowerCase();

        if (javaCommand.contains("surefire") || javaCommand.contains("failsafe") || javaCommand.contains("maven")) {
            return "mvn integration-test";
        } else if (javaCommand.contains("gradle")) {
            return "./gradlew test --tests";
        }

        // Generic fallback if launched from an IDE or an unknown runner
        return "mvn test";
    }

    /** Safely append a -D option to the StringBuilder, with space handling. */
    private void appendOpt(StringBuilder sb, String key, String value) {
        if (value != null && !value.trim().isEmpty()) {
            sb.append(" ");
            sb.append("-D").append(key).append("=");
            if (value.indexOf(' ') >= 0 && !value.startsWith("\"")) {
                sb.append('"').append(value).append('"');
            } else {
                sb.append(value);
            }
        }
    }

    /** Walk through the class and its superclasses to collect @Properties annotations. */
    private void scanProperties(Class<?> c, List<String> properties) {
        if (c == null || Object.class.equals(c)) {
            return;
        }
        scanProperties(c.getSuperclass(), properties);

        Properties annotation = c.getAnnotation(Properties.class);
        if (annotation != null) {
            Collections.addAll(properties, annotation.value());
        }
    }
}
