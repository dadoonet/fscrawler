/*
 * Licensed to David Pilato under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

package fr.pilato.elasticsearch.crawler.fs.thirdparty.wpsearch;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlPasswordInput;
import com.gargoylesoftware.htmlunit.html.HtmlTextInput;
import com.gargoylesoftware.htmlunit.util.Cookie;
import fr.pilato.elasticsearch.crawler.fs.framework.Waiter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Closeable;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static fr.pilato.elasticsearch.crawler.fs.thirdparty.wpsearch.WPSearchClient.DEFAULT_HOST;

public class WPSearchAdminClient implements Closeable {

    private static final Logger logger = LogManager.getLogger(WPSearchAdminClient.class);
    private final static String DEFAULT_USERNAME = "enterprise_search";
    private final static String DEFAULT_PASSWORD = "changeme";

    private String host = DEFAULT_HOST;
    private String username = DEFAULT_USERNAME;
    private String password = DEFAULT_PASSWORD;
    WebClient webClient = null;
    String csrfToken = null;
    Cookie session = null;

    /**
     * Define a specific host. Defaults to "http://localhost:3002"
     * @param host  If we need to change the default host
     * @return the current instance
     */
    public WPSearchAdminClient withHost(String host) {
        this.host = host;
        return this;
    }

    /**
     * Define the username. Defaults to "enterprise_search"
     * @param username  If we need to change the default username
     * @return the current instance
     */
    public WPSearchAdminClient withUsername(String username) {
        this.username = username;
        return this;
    }

    /**
     * Define the password. Defaults to "changeme"
     * @param password  If we need to change the default password
     * @return the current instance
     */
    public WPSearchAdminClient withPassword(String password) {
        this.password = password;
        return this;
    }

    public void start() throws Exception {
        webClient = new WebClient();
        login(username, password);
    }

    @Override
    public void close() {
        if (webClient != null) {
            webClient.close();
        }
    }

    private void checkStarted() {
        if (webClient == null) {
            throw new RuntimeException("start() must be called before calling any API.");
        }
    }

    public Map<String, Object> createCustomSource(String sourceName) throws Exception {
        checkStarted();

        String response = callApi(HttpMethod.POST, "/ws/org/sources/form_create",
                "{service_type: \"custom\", name: \"" + sourceName + "\"}");
        JsonMapper mapper = JsonMapper.builder().build();
        Map<String, Object> map = mapper.readValue(response, Map.class);

        logger.debug("Source [{}] created. id={}, acces_token={}, key={}",
                sourceName, map.get("id"), map.get("accessToken"), map.get("key"));

        return map;
    }

    public void removeCustomSource(String id) throws Exception {
        checkStarted();

        // Delete the source
        callApi(HttpMethod.DELETE, "/ws/org/sources/" + id, null);
    }

    public void login(String username, String password) throws Exception {
        HtmlPage page = openPage("/login");

        HtmlForm form = page.getForms().get(0);
        HtmlTextInput inputEmail = form.getOneHtmlElementByAttribute("input", "type", "text");
        inputEmail.setText(username);
        HtmlPasswordInput inputPassword = form.getOneHtmlElementByAttribute("input", "type", "password");
        inputPassword.setText(password);
        HtmlButton submit = form.getOneHtmlElementByAttribute("button", "type", "submit");

        // Log in
        submit.click();

        HtmlPage entSelect = new Waiter<HtmlPage>(10, TimeUnit.SECONDS).awaitBusy(() -> {
            try {
                // Wait to be logged in and enter the product selection page
                HtmlPage tryPage = openPage("/ent/select");
                if (tryPage == null || !tryPage.getUrl().getPath().equals("/ent/select")) {
                    logger.debug("We did not found yet the page we are waiting for: /ent/select. We got {}",
                            tryPage == null ? "null" : tryPage.getUrl().getPath());
                    return null;
                }

                logger.debug("We found the page we are looking for: {}", tryPage.getUrl().getPath());
                return tryPage;
            } catch (Exception e) {
                logger.error("We got an error while waiting for Enterprise Search to start", e);
            }
            return null;
        });

        if (entSelect == null) {
            // We have not been able to connect to the service. Let's fail the process.
            throw new RuntimeException("Can't open a session on " + host);
        }

        csrfToken = entSelect.getElementByName("csrf-token").getAttribute("content");
        session = webClient.getCookieManager().getCookie("_st_togo_session");
    }

    private String callApi(HttpMethod method, String url, String data)
            throws IOException {
        logger.debug("Calling {}", url);
        WebRequest webRequest = new WebRequest(new URL(host + url));
        webRequest.setHttpMethod(method);
        if (data != null) {
            webRequest.setRequestBody(data);
            webRequest.setAdditionalHeader("Content-Type", "application/json;charset=UTF-8");
        }
        webRequest.setAdditionalHeader("Cookie", session.getName() + "=" + session.getValue());
        webRequest.setAdditionalHeader("x-csrf-token", csrfToken);
        webRequest.setAdditionalHeader("Accept", "application/json, text/plain, */*");
        WebResponse webResponse = webClient.loadWebResponse(webRequest);
        String response = webResponse.getContentAsString();
        logger.debug("Response: {}", response);
        return response;
    }

    private HtmlPage openPage(String url) throws IOException {
        HtmlPage page = webClient.getPage(host + url);
        if (page == null || page.getBody() == null) {
            return null;
        }
        logger.debug("Page {}: {}", page.getBaseURL(), page.getBody().asText());
        return page;
    }
}
