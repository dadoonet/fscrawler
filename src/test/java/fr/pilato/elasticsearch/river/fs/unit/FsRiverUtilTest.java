package fr.pilato.elasticsearch.river.fs.unit;


import fr.pilato.elasticsearch.river.fs.river.ScanStatistic;
import fr.pilato.elasticsearch.river.fs.util.FsRiverUtil;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * We want to test some utilities
 */
public class FsRiverUtilTest {

    @Test
    public void testComputePathLinux() {
        testHelper("/tmp", "/tmp/myfile.txt", "/myfile.txt");
        testHelper("/tmp", "/tmp/dir/myfile.txt", "/dir/myfile.txt");
    }

    @Test
    public void testComputePathWindows() {
        testHelper("C:\\tmp", "C:\\tmp\\myfile.txt", "/myfile.txt");
        testHelper("C:\\tmp", "C:\\tmp\\dir\\myfile.txt", "/dir/myfile.txt");
    }

    private void testHelper(String rootPath, String realPath, String expectedPath) {
        assertThat(FsRiverUtil.computeVirtualPathName(new ScanStatistic(rootPath), realPath), is(expectedPath));
    }
}
