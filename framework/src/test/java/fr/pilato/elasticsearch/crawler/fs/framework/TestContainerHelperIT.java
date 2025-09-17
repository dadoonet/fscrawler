package fr.pilato.elasticsearch.crawler.fs.framework;

import fr.pilato.elasticsearch.crawler.fs.test.framework.TestContainerHelper;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TestContainerHelperIT {

    @Test
    public void testStartingTestcontainer() {
        TestContainerHelper helper = new TestContainerHelper();
        assertThat(helper.isStarted()).isFalse();
        assertThat(helper.getElasticsearchVersion()).isNotBlank();
        String url = helper.startElasticsearch(false);
        assertThat(url).startsWithIgnoringCase("https://");
        assertThat(helper.isStarted()).isTrue();
    }
}
