package fr.pilato.elasticsearch.river.fs;


import fr.pilato.elasticsearch.river.fs.river.ScanStatistic;
import fr.pilato.elasticsearch.river.fs.util.FsRiverUtil;
import junit.framework.Assert;
import org.junit.Test;

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
		testHelper("C:\\tmp", "C:\\tmp\\myfile.txt", "/myfile.txt");
		testHelper("C:\\tmp", "C:\\tmp\\dir\\myfile.txt", "/dir/myfile.txt");
	}

	private void testHelper(String rootPath, String realPath, String expectedPath) {
		Assert.assertEquals(expectedPath, FsRiverUtil.computeVirtualPathName(new ScanStatistic(rootPath), realPath));
	}
}
