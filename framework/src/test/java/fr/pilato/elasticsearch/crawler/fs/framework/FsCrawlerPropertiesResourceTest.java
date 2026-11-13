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

import com.carrotsearch.randomizedtesting.jupiter.RandomizedTest;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class FsCrawlerPropertiesResourceTest extends AbstractFSCrawlerTestCase {
    @Test
    void closesResourceAfterSuccessfulPropertiesLoad() throws Exception {
        String key = word(), value = word();
        TrackingStream stream = new TrackingStream(key + "=" + value, false);
        Properties properties = (Properties) load(stream);
        Assertions.assertThat(properties.getProperty(key)).isEqualTo(value);
        Assertions.assertThat(stream.closes).isEqualTo(1);
    }

    @Test
    void closesResourceEvenWhenReadFails() throws Exception {
        TrackingStream stream = new TrackingStream("", true);
        Assertions.assertThatThrownBy(() -> load(stream))
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(FsCrawlerIOException.class);
        Assertions.assertThat(stream.closes).isEqualTo(1);
    }

    @Test
    void missingResourceHasNamedIoError() {
        Assertions.assertThatThrownBy(() -> load(null))
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(FsCrawlerIOException.class);
    }

    private String word() {
        return RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 5, 15);
    }

    private Object load(InputStream resource) throws Exception {
        String requested = word() + ".properties";
        String utility = FsCrawlerUtil.class.getName();
        // Isolate only this utility class so its actual class-loader acquisition path is exercised.
        ClassLoader loader = new ClassLoader(FsCrawlerUtil.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.equals(utility)) return super.loadClass(name, resolve);
                synchronized (getClassLoadingLock(name)) {
                    Class<?> type = findLoadedClass(name);
                    if (type == null) {
                        try (InputStream bytes = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                            byte[] content = bytes.readAllBytes();
                            type = defineClass(name, content, 0, content.length);
                        } catch (IOException e) {
                            throw new ClassNotFoundException(name, e);
                        }
                    }
                    if (resolve) resolveClass(type);
                    return type;
                }
            }

            @Override
            public InputStream getResourceAsStream(String name) {
                return name.equals(requested) ? resource : super.getResourceAsStream(name);
            }
        };
        return loader.loadClass(utility)
                .getMethod("readPropertiesFromClassLoader", String.class)
                .invoke(null, requested);
    }

    private static class TrackingStream extends InputStream {
        final ByteArrayInputStream bytes;
        final boolean fail;
        int closes;

        TrackingStream(String value, boolean fail) {
            bytes = new ByteArrayInputStream(value.getBytes(StandardCharsets.ISO_8859_1));
            this.fail = fail;
        }

        @Override
        public int read() throws IOException {
            if (fail) throw new IOException("read failed");
            return bytes.read();
        }

        @Override
        public void close() {
            closes++;
        }
    }
}
