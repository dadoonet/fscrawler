package fr.pilato.elasticsearch.crawler.fs.framework;

import fr.pilato.elasticsearch.crawler.fs.test.framework.TestContainerHelper;
import org.junit.Test;
import org.testcontainers.DockerClientFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeTrue;

public class TestContainerHelperIT {

    @Test
    public void testStartingTestcontainer() {
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
