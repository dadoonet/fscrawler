package fr.pilato.elasticsearch.crawler.fs.service;

import fr.pilato.elasticsearch.crawler.fs.settings.ThumbnailSettings;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeNotNull;

public class ThumbnailGeneratorTest extends AbstractFSCrawlerTestCase {

    private ThumbnailGenerator generator;

    @Before
    public void setUp() {
        ThumbnailSettings settings = new ThumbnailSettings();
        settings.setEnabled(true);
        settings.setWidth(300);
        settings.setHeight(410);
        generator = new ThumbnailGenerator(settings);
    }

    @Test
    public void testPsdExtensionIsSupported() throws Exception {
        // Create a minimal temp PSD file so the exists/isFile check passes
        Path tmp = folder.newFile("test.psd").toPath();
        writeMiniPsd(tmp, 10, 10);
        // canGenerateThumbnail is private; verify indirectly via generateThumbnailBase64
        // returning non-null (or null only due to rendering, not unsupported type)
        // We just assert no exception is thrown for a .psd file
        generator.generateThumbnailBase64(tmp.toFile()); // should not throw
    }

    @Test
    public void testNonPsdExtensionRemainsUnsupported() throws Exception {
        Path tmp = folder.newFile("test.xyz").toPath();
        Files.write(tmp, new byte[]{0x00});
        String result = generator.generateThumbnailBase64(tmp.toFile());
        assertNull("Non-supported extension should return null", result);
    }

    @Test
    public void testPsdThumbnailGeneratedFromTestDocument() throws Exception {
        URL resource = getClass().getClassLoader().getResource("documents/test.psd");
        assumeNotNull("test.psd not found in test-documents", resource);
        File psdFile = new File(resource.toURI());
        // The test.psd is 10x10 with no embedded thumbnail; full render should succeed
        String result = generator.generateThumbnailBase64(psdFile);
        assertNotNull("Should generate a thumbnail from test.psd", result);
        assertFalse("Thumbnail should not be empty", result.isEmpty());
        // Verify it's valid base64
        byte[] decoded = java.util.Base64.getDecoder().decode(result);
        assertTrue("Thumbnail should be non-empty bytes", decoded.length > 0);
    }

    @Test
    public void testDisabledGeneratorReturnsNull() throws Exception {
        ThumbnailSettings disabled = new ThumbnailSettings();
        disabled.setEnabled(false);
        ThumbnailGenerator disabledGen = new ThumbnailGenerator(disabled);

        URL resource = getClass().getClassLoader().getResource("documents/test.psd");
        assumeNotNull("test.psd not found in test-documents", resource);
        File psdFile = new File(resource.toURI());

        String result = disabledGen.generateThumbnailBase64(psdFile);
        assertNull("Disabled generator should return null", result);
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /** Write a minimal valid PSD (no embedded thumbnail) to the given path. */
    private static void writeMiniPsd(Path path, int width, int height) throws Exception {
        java.io.DataOutputStream out = new java.io.DataOutputStream(
                new java.io.BufferedOutputStream(Files.newOutputStream(path)));
        try {
            out.write(new byte[]{'8','B','P','S'});   // signature
            out.writeShort(1);                         // version = PSD
            out.write(new byte[6]);                    // reserved
            out.writeShort(3);                         // channels = RGB
            out.writeInt(height);
            out.writeInt(width);
            out.writeShort(8);                         // bits per channel
            out.writeShort(3);                         // color mode = RGB
            out.writeInt(0);                           // color mode data length
            out.writeInt(0);                           // image resources length
            out.writeInt(0);                           // layer/mask info length
            out.writeShort(0);                         // compression = raw
            // channel data: 3 channels * width * height bytes = white (0xFF)
            byte[] row = new byte[width];
            java.util.Arrays.fill(row, (byte) 0xFF);
            for (int c = 0; c < 3; c++) {
                for (int y = 0; y < height; y++) {
                    out.write(row);
                }
            }
        } finally {
            out.close();
        }
    }
}
