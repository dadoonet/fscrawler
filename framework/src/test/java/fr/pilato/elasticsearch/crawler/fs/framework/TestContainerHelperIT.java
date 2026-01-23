package fr.pilato.elasticsearch.crawler.fs.framework;

import fr.pilato.elasticsearch.crawler.fs.test.framework.TestContainerHelper;
import org.junit.Test;
import org.testcontainers.DockerClientFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

public class TestContainerHelperIT {

    private static final String DEFAULT_TEST_CLUSTER_URL = "https://127.0.0.1:9200";

    @Test
    public void testStartingTestcontainer() {
        // Don't run it if an external cluster for Elasticsearch is set
        boolean isExternalClusterSet = System.getProperty("tests.cluster.url") != null && !DEFAULT_TEST_CLUSTER_URL.equals(System.getProperty("tests.cluster.url"));
        assumeFalse("External Elasticsearch cluster is set, skipping TestContainerHelperIT.", isExternalClusterSet);

        // Check if Docker is available on this OS
        assumeTrue("Docker is not available on this machine.", DockerClientFactory.instance().isDockerAvailable());

        TestContainerHelper helper = new TestContainerHelper();
        assertThat(helper.isStarted()).isFalse();
        assertThat(helper.getElasticsearchVersion()).isNotBlank();
        String url = helper.startElasticsearch(false);
        assertThat(url).isNotBlank();
        assertThat(helper.isStarted()).isTrue();
    }
}
