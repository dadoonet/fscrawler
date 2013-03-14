package org.elasticsearch.river.fs;


import junit.framework.Assert;
import org.junit.Test;

import static org.elasticsearch.river.fs.FsRiverUtil.computeVirtualPathName;

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
        // TODO Test it on a windows platform
        testHelper("C:/tmp", "C:/tmp/myfile.txt", "/myfile.txt");
        testHelper("C:/tmp", "C:/tmp/dir/myfile.txt", "/dir/myfile.txt");
    }

    private void testHelper(String rootPath, String realPath, String expectedPath) {
        Assert.assertEquals(expectedPath, computeVirtualPathName(new ScanStatistic(rootPath), realPath));
    }
}
