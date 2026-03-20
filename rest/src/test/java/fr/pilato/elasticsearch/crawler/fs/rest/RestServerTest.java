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

package fr.pilato.elasticsearch.crawler.fs.rest;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.assertj.core.api.Assertions;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.junit.Test;

/**
 * Test for REST server behaviour, including issue #474: hostname with underscore in the Host header causes
 * URISyntaxException in Jersey/Grizzly.
 *
 * @see <a href="https://github.com/dadoonet/fscrawler/issues/474">Issue 474</a>
 * @see <a href="https://github.com/eclipse-ee4j/jersey/issues/728>Issue 728 in Jersey</a>
 */
public class RestServerTest {

    // This test should fail when Jersey is upgraded with a fix for this issue.
    // Then we could remove this test class.

    /**
     * Reproduces issue #474: when a client sends a request with a Host header containing an underscore (e.g. Docker
     * service name "fscrawler_rest"), Jersey's GrizzlyHttpContainer.getBaseUri() parses the request URI and throws
     * URISyntaxException because java.net.URI treats underscore as illegal in hostnames.
     *
     * <p>We do not need to bind the server to a hostname with underscore. We start the server on localhost and send a
     * request with a custom Host header to trigger the same code path.
     */
    @Test
    public void requestWithHostHeaderContainingUnderscoreReturnsServerError() throws Exception {
        // Bind to localhost with random port (0 = assign any free port)
        URI baseUri = URI.create("http://127.0.0.1:0");
        ResourceConfig rc = new ResourceConfig().register(new RootResource());
        HttpServer server = GrizzlyHttpServerFactory.createHttpServer(baseUri, rc);
        try {
            int port = server.getListeners().iterator().next().getPort();
            String hostWithUnderscore = "fscrawler_rest:" + port;
            String hostWithoutUnderscore = "fscrawlerrest:" + port;

            // java.net.http.HttpClient does not allow setting the Host header (restricted).
            // Use a raw socket so we can send Host: fscrawler_rest:port and trigger the bug.
            String request = "GET / HTTP/1.1\r\nHost: " + hostWithUnderscore + "\r\nConnection: close\r\n\r\n";
            String response;
            try (Socket socket = new Socket("127.0.0.1", port)) {
                socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
                response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            }

            Assertions.assertThat(response)
                    .startsWith("HTTP/1.1 500")
                    .contains("URISyntaxException")
                    .contains("Illegal character in hostname");

            // java.net.http.HttpClient does not allow setting the Host header (restricted).
            // Use a raw socket so we can send Host: fscrawler_rest:port and trigger the bug.
            String request2 = "GET / HTTP/1.1\r\nHost: " + hostWithoutUnderscore + "\r\nConnection: close\r\n\r\n";
            String response2;
            try (Socket socket = new Socket("127.0.0.1", port)) {
                socket.getOutputStream().write(request2.getBytes(StandardCharsets.US_ASCII));
                response2 = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            }

            Assertions.assertThat(response2).startsWith("HTTP/1.1 200 OK").contains("ok");
        } finally {
            server.shutdownNow();
        }
    }

    @Path("/")
    public static class RootResource {
        @GET
        @Produces(MediaType.TEXT_PLAIN)
        public String get() {
            return "ok";
        }
    }
}
