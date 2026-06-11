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

import java.lang.reflect.Proxy;
import java.util.Optional;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;

class FsCrawlerReproduceInfoExtensionTest {

    private final FsCrawlerReproduceInfoExtension extension = new FsCrawlerReproduceInfoExtension();

    @Test
    void shouldBuildUnitTestCommandWithMethod() {
        String command = extension.buildReproduceCommand(context(PlainTest.class), "myMethod");

        Assertions.assertThat(command)
                .contains("mvn verify -DskipIntegTests -Dtest=" + PlainTest.class.getName() + "#myMethod");
        Assertions.assertThat(command).contains("-Dtests.locale=");
        Assertions.assertThat(command).contains("-Dtests.timezone=");
    }

    @Test
    void shouldBuildIntegrationCommandForLifecycleFailure() {
        String command = extension.buildReproduceCommand(context(DummyIT.class), null);

        Assertions.assertThat(command).contains("mvn verify -DskipUnitTests -Dit.test=" + DummyIT.class.getName());
        Assertions.assertThat(command).doesNotContain("#");
    }

    private static ExtensionContext context(Class<?> testClass) {
        return (ExtensionContext) Proxy.newProxyInstance(
                ExtensionContext.class.getClassLoader(),
                new Class[] {ExtensionContext.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getRequiredTestClass" -> testClass;
                    case "getTestMethod", "getExecutionException" -> Optional.empty();
                    case "toString" -> "ExtensionContext(" + testClass.getName() + ")";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException("Unexpected method: " + method.getName());
                });
    }

    static class PlainTest {}

    static class DummyIT {}
}
