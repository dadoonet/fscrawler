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
package fr.pilato.elasticsearch.crawler.fs.kibana;

import com.jayway.jsonpath.JsonPath;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil;
import fr.pilato.elasticsearch.crawler.fs.settings.Elasticsearch;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.Kibana;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.jersey.apache5.connector.Apache5ConnectorProvider;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;

/** HTTP client for the Kibana Dashboards and Data Views APIs. */
public class KibanaClient implements IKibanaClient {

    private static final Logger logger = LogManager.getLogger();

    private static final String KBN_XSRF_HEADER = "kbn-xsrf";
    private static final String KBN_XSRF_VALUE = "true";
    private static final String USER_AGENT = "fscrawler-kibana-client";

    private final FsSettings settings;
    private final Kibana kibanaSettings;

    private Client client;
    private String authorizationHeader;
    private String version;
    private boolean dashboardProvisioningEnabled;

    public KibanaClient(FsSettings settings) {
        this.settings = settings;
        this.kibanaSettings = settings.getKibana();
        this.dashboardProvisioningEnabled = kibanaSettings != null && kibanaSettings.isPushDashboard();
    }

    /** Returns {@code true} when the Kibana Dashboards API is generally available (9.5+). */
    public static boolean supportsDashboardsApi(String kibanaVersion) {
        int major = FsCrawlerUtil.extractMajorVersion(kibanaVersion);
        int minor = FsCrawlerUtil.extractMinorVersion(kibanaVersion);
        return major > MIN_DASHBOARDS_API_MAJOR_VERSION
                || (major == MIN_DASHBOARDS_API_MAJOR_VERSION && minor >= MIN_DASHBOARDS_API_MINOR_VERSION);
    }

    @Override
    public void start() throws KibanaClientException {
        if (client != null) {
            return;
        }
        if (kibanaSettings == null) {
            throw new KibanaClientException("Kibana settings are not configured");
        }

        ClientConfig config = new ClientConfig();
        config.connectorProvider(new Apache5ConnectorProvider());

        ClientBuilder clientBuilder = ClientBuilder.newBuilder()
                .hostnameVerifier(new NullHostNameVerifier())
                .withConfig(config);

        configureSsl(clientBuilder);

        authorizationHeader = resolveAuthorizationHeader();
        if (authorizationHeader == null) {
            HttpAuthenticationFeature feature = HttpAuthenticationFeature.basic(
                    settings.getElasticsearch().getUsername(),
                    settings.getElasticsearch().getPassword());
            clientBuilder.register(feature);
        }

        client = clientBuilder.build();

        try {
            String statusResponse = httpGet("api/status");
            version = JsonPath.read(statusResponse, "$.version.number");
            logger.debug("Kibana client connected to {} running version {}", kibanaSettings.getUrl(), version);

            if (!supportsDashboardsApi(version)) {
                logger.warn(
                        "Kibana Dashboards API requires version {}.{}, but this cluster runs {}. "
                                + "Disabling dashboard provisioning.",
                        MIN_DASHBOARDS_API_MAJOR_VERSION,
                        MIN_DASHBOARDS_API_MINOR_VERSION,
                        version);
                dashboardProvisioningEnabled = false;
            }
        } catch (KibanaClientException e) {
            throw e;
        } catch (Exception e) {
            throw new KibanaClientException(
                    "Failed to create Kibana client on " + kibanaSettings.getUrl() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public boolean isDashboardProvisioningEnabled() {
        return dashboardProvisioningEnabled;
    }

    @Override
    public boolean isAvailable() throws KibanaClientException {
        try {
            httpGet("api/status");
            return true;
        } catch (NotFoundException e) {
            return false;
        } catch (KibanaClientException e) {
            if (e.getCause() instanceof WebApplicationException wae
                    && wae.getResponse().getStatus() >= 500) {
                throw e;
            }
            if (e.getCause() instanceof WebApplicationException) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public boolean createDataViewIfMissing(String dataViewId, String indexPattern, String timeField)
            throws KibanaClientException {
        if (dataViewExists(dataViewId)) {
            logger.debug("Kibana data view [{}] already exists", dataViewId);
            return false;
        }

        String payload = KibanaDashboardBuilder.buildDataViewPayload(dataViewId, indexPattern, timeField);
        httpPost("api/data_views/data_view", payload);
        logger.info("Created Kibana data view [{}] for index pattern [{}]", dataViewId, indexPattern);
        return true;
    }

    @Override
    public boolean dashboardExists(String dashboardId) throws KibanaClientException {
        try {
            httpGet("api/dashboards/" + dashboardId);
            return true;
        } catch (NotFoundException e) {
            return false;
        }
    }

    @Override
    public String createDashboard(String dashboardId, String dashboardPayload) throws KibanaClientException {
        // POST /api/dashboards does not accept an id in the body. Use PUT upsert for stable ids.
        String response = httpPut("api/dashboards/" + dashboardId, dashboardPayload);
        return JsonPath.read(response, "$.id");
    }

    @Override
    public String updateDashboard(String dashboardId, String dashboardPayload) throws KibanaClientException {
        String response = httpPut("api/dashboards/" + dashboardId, dashboardPayload);
        return JsonPath.read(response, "$.id");
    }

    @Override
    public void deleteDashboard(String dashboardId) throws KibanaClientException {
        try {
            httpCall("DELETE", "api/dashboards/" + dashboardId, null, true);
            logger.info("Deleted Kibana dashboard [{}]", dashboardId);
        } catch (NotFoundException e) {
            logger.debug("Kibana dashboard [{}] was already absent", dashboardId);
        }
    }

    @Override
    public void close() throws IOException {
        if (client != null) {
            client.close();
            client = null;
        }
    }

    private boolean dataViewExists(String dataViewId) throws KibanaClientException {
        try {
            httpGet("api/data_views/data_view/" + dataViewId);
            return true;
        } catch (NotFoundException e) {
            return false;
        }
    }

    private String resolveAuthorizationHeader() {
        if (!FsCrawlerUtil.isNullOrEmpty(kibanaSettings.getApiKey())) {
            return "ApiKey " + kibanaSettings.getApiKey();
        }
        Elasticsearch elasticsearch = settings.getElasticsearch();
        if (!FsCrawlerUtil.isNullOrEmpty(elasticsearch.getApiKey())) {
            return "ApiKey " + elasticsearch.getApiKey();
        }
        return null;
    }

    private void configureSsl(ClientBuilder clientBuilder) throws KibanaClientException {
        Elasticsearch elasticsearch = settings.getElasticsearch();
        if (elasticsearch.isSslVerification()) {
            String caCertificatePath = elasticsearch.getCaCertificate();
            if (caCertificatePath != null) {
                File certFile = new File(caCertificatePath);
                clientBuilder.sslContext(sslContextFromHttpCaCrt(certFile));
            }
            return;
        }

        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());
            clientBuilder.sslContext(sslContext);
            logger.warn("Kibana SSL verification is disabled. This is not recommended for production.");
        } catch (KeyManagementException | NoSuchAlgorithmException e) {
            throw new KibanaClientException("Failed to initialize SSL context for Kibana client", e);
        }
    }

    private SSLContext sslContextFromHttpCaCrt(File certFile) throws KibanaClientException {
        try (var inputStream = java.nio.file.Files.newInputStream(certFile.toPath())) {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            Certificate trustedCa = factory.generateCertificate(inputStream);
            KeyStore trustStore = KeyStore.getInstance("pkcs12");
            trustStore.load(null, null);
            trustStore.setCertificateEntry("ca", trustedCa);

            TrustManagerFactory trustManagerFactory =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagerFactory.getTrustManagers(), new SecureRandom());
            return sslContext;
        } catch (IOException
                | CertificateException
                | KeyStoreException
                | NoSuchAlgorithmException
                | KeyManagementException e) {
            throw new KibanaClientException("Failed to load CA certificate from " + certFile, e);
        }
    }

    private String httpGet(String path) throws KibanaClientException {
        return httpCall("GET", path, null, false);
    }

    private String httpPost(String path, String payload) throws KibanaClientException {
        return httpCall("POST", path, payload, true);
    }

    private String httpPut(String path, String payload) throws KibanaClientException {
        return httpCall("PUT", path, payload, true);
    }

    private String httpCall(String method, String path, String payload, boolean writeOperation)
            throws KibanaClientException {
        String baseUrl = kibanaSettings.getUrl();
        String apiPath = buildApiPath(path);
        logger.trace("Calling {} {}/{}", method, baseUrl, apiPath);
        try {
            WebTarget target = client.target(baseUrl).path(apiPath);
            Invocation.Builder builder =
                    target.request(MediaType.APPLICATION_JSON).header(HttpHeaders.CONTENT_TYPE, "application/json");
            builder.header(HttpHeaders.USER_AGENT, USER_AGENT);
            if (authorizationHeader != null) {
                builder.header(HttpHeaders.AUTHORIZATION, authorizationHeader);
            }
            if (writeOperation) {
                builder.header(KBN_XSRF_HEADER, KBN_XSRF_VALUE);
            }

            String response;
            if (payload == null) {
                response = builder.method(method, String.class);
            } else {
                response = builder.method(method, Entity.json(payload), String.class);
            }
            logger.trace("{} {}/{} gives {}", method, baseUrl, apiPath, response);
            return response;
        } catch (NotFoundException e) {
            throw e;
        } catch (WebApplicationException e) {
            String errorBody = readErrorBody(e);
            int status = e.getResponse().getStatus();
            logger.debug("{} {}/{} failed with HTTP {}: {}", method, baseUrl, apiPath, status, errorBody);
            throw new KibanaClientException(
                    "Can not execute " + method + " " + baseUrl + "/" + apiPath + " (HTTP " + status + "): "
                            + errorBody,
                    e);
        } catch (ProcessingException e) {
            if (e.getCause() instanceof ConnectException) {
                throw new KibanaClientException(
                        "Can not connect to Kibana at " + baseUrl + ": "
                                + e.getCause().getMessage(),
                        e);
            }
            throw new KibanaClientException(
                    "Can not execute " + method + " " + baseUrl + "/" + apiPath + ": " + e.getMessage(), e);
        }
    }

    private static String readErrorBody(WebApplicationException e) {
        try {
            if (e.getResponse().hasEntity()) {
                return e.getResponse().readEntity(String.class);
            }
        } catch (Exception ignored) {
            // Fall through to status info when the entity cannot be read.
        }
        return e.getMessage() != null ? e.getMessage() : "no response body";
    }

    private String buildApiPath(String path) {
        String space = kibanaSettings.getSpace();
        if (FsCrawlerUtil.isNullOrEmpty(space)) {
            return path;
        }
        return "s/" + space + "/" + path;
    }

    private static final TrustManager[] trustAllCerts = new TrustManager[] {
        new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {}

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {}

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        }
    };

    public static class NullHostNameVerifier implements HostnameVerifier {
        @Override
        public boolean verify(String urlHostName, SSLSession session) {
            if (!urlHostName.equalsIgnoreCase(session.getPeerHost())) {
                logger.warn("URL host [{}] is different to SSLSession host [{}].", urlHostName, session.getPeerHost());
            }
            return true;
        }
    }
}
