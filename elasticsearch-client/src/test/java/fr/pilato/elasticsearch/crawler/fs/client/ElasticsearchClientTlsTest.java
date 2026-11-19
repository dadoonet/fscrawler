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
package fr.pilato.elasticsearch.crawler.fs.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.carrotsearch.randomizedtesting.jupiter.RandomizedTest;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsLoader;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import fr.pilato.elasticsearch.crawler.fs.test.framework.Slow;
import java.io.StringWriter;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.CertificateExpiredException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Test;

/** Real TLS handshakes through the production Jersey/Apache connector, with temporary test-only certificates. */
@Slow
class ElasticsearchClientTlsTest extends AbstractFSCrawlerTestCase {
    /** CERT-01: a trusted CA and matching DNS SAN must permit initialization. */
    @Test
    void trustedMatchingHostnameConnects() throws Exception {
        exercise("localhost", "localhost", false, true, true, null);
    }

    /** CERT-02: trust alone must not permit a certificate issued for another DNS name (F05). */
    @Test
    void trustedWrongHostnameIsRejected() throws Exception {
        exercise(randomName() + ".invalid", "localhost", false, true, true, "hostname");
    }

    /** CERT-03: a DNS SAN for localhost is not an IP SAN for 127.0.0.1. */
    @Test
    void trustedDnsCertificateDoesNotAuthenticateIpAddress() throws Exception {
        exercise("localhost", "127.0.0.1", false, true, true, "hostname");
    }

    /** CERT-04: a matching name cannot compensate for an untrusted issuer. */
    @Test
    void untrustedIssuerIsRejected() throws Exception {
        exercise("localhost", "localhost", false, false, true, "trust");
    }

    /** CERT-05: a matching name and trusted CA cannot compensate for an expired leaf. */
    @Test
    void expiredCertificateIsRejected() throws Exception {
        exercise("localhost", "localhost", true, true, true, "expired");
    }

    /** CERT-06: explicit opt-out must still allow both an untrusted issuer and a mismatched name. */
    @Test
    void explicitVerificationBypassConnects() throws Exception {
        exercise(randomName() + ".invalid", "localhost", false, false, false, null);
    }

    private String randomName() {
        return RandomizedTest.randomAsciiLettersOfLengthBetween(randomizedRandomForTests, 8, 16)
                .toLowerCase(Locale.ROOT);
    }

    private void exercise(
            String dnsName,
            String urlHost,
            boolean expired,
            boolean trustIssuer,
            boolean verify,
            String expectedFailure)
            throws Exception {
        Instant now = Instant.now();
        KeyPair caKey = keyPair();
        X500Name caName = new X500Name("CN=" + randomName());
        X509Certificate ca = certificate(
                caKey, caName, caKey, caName, now.minus(30, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS), null);
        KeyPair serverKey = keyPair();
        X509Certificate leaf = certificate(
                serverKey,
                new X500Name("CN=" + dnsName),
                caKey,
                caName,
                now.minus(2, ChronoUnit.DAYS),
                expired ? now.minus(1, ChronoUnit.DAYS) : now.plus(7, ChronoUnit.DAYS),
                dnsName);
        // Verify fixture properties independently, so a negative case cannot pass for the wrong reason.
        ca.checkValidity();
        leaf.verify(ca.getPublicKey());
        assertThat(leaf.getSubjectAlternativeNames()).contains(List.of(GeneralName.dNSName, dnsName));
        if (expired) {
            assertThat(catchThrowable(leaf::checkValidity)).isInstanceOf(CertificateExpiredException.class);
        } else {
            leaf.checkValidity();
        }

        // An unrelated trusted CA makes the untrusted-issuer case independent of the machine trust store.
        KeyPair unrelatedKey = keyPair();
        X500Name unrelatedName = new X500Name("CN=" + randomName());
        X509Certificate unrelatedCa = certificate(
                unrelatedKey,
                unrelatedName,
                unrelatedKey,
                unrelatedName,
                now.minus(1, ChronoUnit.DAYS),
                now.plus(30, ChronoUnit.DAYS),
                null);
        Path caFile = testTmpDir.resolve(randomName() + ".pem");
        StringWriter pem = new StringWriter();
        try (JcaPEMWriter writer = new JcaPEMWriter(pem)) {
            writer.writeObject(trustIssuer ? ca : unrelatedCa);
        }
        Files.writeString(caFile, pem.toString(), StandardCharsets.US_ASCII);

        char[] password = randomName().toCharArray();
        KeyStore keys = KeyStore.getInstance("PKCS12");
        keys.load(null, password);
        keys.setKeyEntry("server", serverKey.getPrivate(), password, new X509Certificate[] {leaf, ca});
        KeyManagerFactory managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        managers.init(keys, password);
        SSLContext tls = SSLContext.getInstance("TLS");
        tls.init(managers.getKeyManagers(), null, null);

        // Listen on both localhost address families: Apache can try IPv6 after an IPv4 TLS failure.
        HttpsServer server = HttpsServer.create(new InetSocketAddress(0), 0);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicInteger requests = new AtomicInteger();
        server.setExecutor(executor);
        server.setHttpsConfigurator(new HttpsConfigurator(tls));
        String version = "9.0.0";
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            byte[] body = ("{\"version\":{\"number\":\"" + version + "\"}}").getBytes(StandardCharsets.UTF_8);
            try (exchange) {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
        });
        server.start();
        FsSettings settings = FsSettingsLoader.load();
        settings.setName(randomName());
        settings.getElasticsearch()
                .setUrls(
                        List.of("https://" + urlHost + ":" + server.getAddress().getPort()));
        settings.getElasticsearch().setCaCertificate(caFile.toString());
        settings.getElasticsearch().setSslVerification(verify);
        settings.getElasticsearch().setSemanticSearch(false);
        settings.getElasticsearch().setRetryMaxDuration(TimeValue.timeValueMillis(200));
        try (ElasticsearchClient client = new ElasticsearchClient(settings) {
            @Override
            protected void initBulkProcessor() {
                // Startup and its real HTTPS version request are under test; no bulk worker is needed.
            }
        }) {
            if (expectedFailure == null) {
                client.start();
                assertThat(client.getVersion()).isEqualTo(version);
                assertThat(requests.get()).isPositive();
            } else {
                Throwable failure = catchThrowable(client::start);
                assertThat(failure).isInstanceOf(ElasticsearchClientException.class);
                assertThat(hasCause(failure, SSLException.class))
                        .as("TLS validation failure")
                        .isTrue();
                String causes = causeDescriptions(failure).toLowerCase(Locale.ROOT);
                switch (expectedFailure) {
                    case "hostname" ->
                        assertThat(causes).containsAnyOf("subject alternative", "doesn't match", "does not match");
                    case "trust" -> assertThat(causes).containsAnyOf("pkix", "certification path");
                    case "expired" ->
                        assertThat(hasCause(failure, CertificateExpiredException.class))
                                .isTrue();
                    default -> throw new AssertionError(expectedFailure);
                }
                assertThat(requests.get())
                        .as("rejected before sending HTTP application data")
                        .isZero();
            }
        } finally {
            server.stop(0);
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private KeyPair keyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        return generator.generateKeyPair();
    }

    private X509Certificate certificate(
            KeyPair subjectKey,
            X500Name subject,
            KeyPair issuerKey,
            X500Name issuer,
            Instant from,
            Instant to,
            String dnsName)
            throws Exception {
        BigInteger serial =
                BigInteger.valueOf(RandomizedTest.randomLongBetween(randomizedRandomForTests, 1, Long.MAX_VALUE));
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                issuer, serial, Date.from(from), Date.from(to), subject, subjectKey.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(dnsName == null));
        builder.addExtension(
                Extension.keyUsage,
                true,
                new KeyUsage(dnsName == null ? KeyUsage.keyCertSign | KeyUsage.cRLSign : KeyUsage.digitalSignature));
        if (dnsName != null) {
            builder.addExtension(
                    Extension.extendedKeyUsage, false, new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth));
            builder.addExtension(
                    Extension.subjectAlternativeName,
                    false,
                    new GeneralNames(new GeneralName(GeneralName.dNSName, dnsName)));
        }
        return new JcaX509CertificateConverter()
                .getCertificate(
                        builder.build(new JcaContentSignerBuilder("SHA256withECDSA").build(issuerKey.getPrivate())));
    }

    private static boolean hasCause(Throwable failure, Class<? extends Throwable> type) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) return true;
        }
        return false;
    }

    private static String causeDescriptions(Throwable failure) {
        StringBuilder descriptions = new StringBuilder();
        for (Throwable cause = failure; cause != null; cause = cause.getCause())
            descriptions.append(cause).append('\n');
        return descriptions.toString();
    }
}
