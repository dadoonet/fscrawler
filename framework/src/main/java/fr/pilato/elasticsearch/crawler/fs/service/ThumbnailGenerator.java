package fr.pilato.elasticsearch.crawler.fs.service;

import fr.pilato.elasticsearch.crawler.fs.settings.ThumbnailSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import co.elastic.thumbnails4j.core.Thumbnailer;
import co.elastic.thumbnails4j.core.Dimensions;
import co.elastic.thumbnails4j.core.ThumbnailingException;
import co.elastic.thumbnails4j.pdf.PDFThumbnailer;
import co.elastic.thumbnails4j.image.ImageThumbnailer;
import co.elastic.thumbnails4j.doc.DOCThumbnailer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class ThumbnailGenerator {

    private static final Logger logger = LogManager.getLogger(ThumbnailGenerator.class);

    private final ThumbnailSettings settings;

    public ThumbnailGenerator(ThumbnailSettings settings) {
        this.settings = settings;
    }

    /**
     * Get the appropriate thumbnailer for the given file type
     */
    private Thumbnailer getThumbnailerForFile(File file) {
        String fileName = file.getName().toLowerCase();
        logger.debug("Getting thumbnailer for file: {} (extension check)", fileName);

        // PDF files
        if (fileName.endsWith(".pdf")) {
            logger.debug("Using PDFThumbnailer for: {}", fileName);
            return new PDFThumbnailer();
        }

        // Document files (.doc, .docx)
        if (fileName.endsWith(".doc") || fileName.endsWith(".docx")) {
            logger.debug("Using DOCThumbnailer for: {}", fileName);
            return new DOCThumbnailer();
        }

        // Image files - ImageThumbnailer requires output format as parameter
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") ||
            fileName.endsWith(".png") || fileName.endsWith(".gif") ||
            fileName.endsWith(".bmp") || fileName.endsWith(".tiff") ||
            fileName.endsWith(".tif") || fileName.endsWith(".webp")) {
            logger.debug("Using ImageThumbnailer for: {}", fileName);
            return new ImageThumbnailer("png");
        }

        // PSD files are handled by generatePsdThumbnail(), not via a Thumbnailer
        if (fileName.endsWith(".psd")) {
            logger.debug("PSD file will be handled via TwelveMonkeys ImageReader: {}", fileName);
            return null;
        }

        logger.debug("No thumbnailer available for file type: {}", fileName);
        return null;
    }

    /**
     * Generate a base64 encoded thumbnail from a file
     * @param file The file to generate thumbnail from
     * @return Base64 encoded thumbnail string, or null if generation failed
     */
    public String generateThumbnailBase64(File file) {
        logger.debug("=== THUMBNAIL GENERATION START ===");
        logger.debug("File: {}", file.getAbsolutePath());
        logger.debug("Settings enabled: {}", settings.isEnabled());

        if (!settings.isEnabled()) {
            logger.debug("Thumbnail generation is disabled");
            return null;
        }

        if (!canGenerateThumbnail(file)) {
            logger.debug("Cannot generate thumbnail for file type: {}", file.getName());
            return null;
        }

        logger.debug("File passed all checks, proceeding with thumbnail generation");

        try {
            logger.debug("Generating thumbnail for file: {}", file.getAbsolutePath());

            List<BufferedImage> thumbnails;
            String fileName = file.getName().toLowerCase();

            // PSD files - use TwelveMonkeys ImageReader with embedded thumbnail awareness
            if (fileName.endsWith(".psd")) {
                logger.debug("Using PSD-specific thumbnail path for: {}", file.getName());
                BufferedImage psdThumb = generatePsdThumbnail(file);
                thumbnails = (psdThumb != null) ? Collections.singletonList(psdThumb) : null;

            // Standard image files - use direct ImageIO for consistent aspect-ratio scaling
            } else if (fileName.matches(".*\\.(jpg|jpeg|png|gif|bmp|tiff|tif|webp)$")) {
                logger.debug("Using direct ImageIO processing for image file: {}", file.getName());
                BufferedImage imageThumb = generateImageThumbnailDirect(file);
                thumbnails = (imageThumb != null) ? Collections.singletonList(imageThumb) : null;

            } else {
                // Use thumbnails4j for PDFs and office documents
                Dimensions targetDimensions = new Dimensions(settings.getWidth(), settings.getHeight());
                List<Dimensions> dimensionsList = Collections.singletonList(targetDimensions);

                Thumbnailer thumbnailer = getThumbnailerForFile(file);
                logger.debug("Selected thumbnailer: {}", thumbnailer != null ? thumbnailer.getClass().getSimpleName() : "null");

                if (thumbnailer == null) {
                    logger.debug("No thumbnailer available for file type: {}", file.getName());
                    return null;
                }

                logger.debug("Calling thumbnailer.getThumbnails() for file: {}", file.getName());
                thumbnails = thumbnailer.getThumbnails(file, dimensionsList);
            }

            logger.debug("getThumbnails() returned: {}", thumbnails != null ? "non-null list" : "null");

            if (thumbnails == null || thumbnails.isEmpty()) {
                // Fallback for PDFs: try rendering with PDFBox directly
                if (file.getName().toLowerCase().endsWith(".pdf")) {
                    logger.debug("thumbnails4j returned empty list, trying PDFBox rendering for: {}", file.getName());
                    BufferedImage pdfImage = renderPdfPageWithPDFBox(file);
                    if (pdfImage != null) {
                        thumbnails = Collections.singletonList(pdfImage);
                        logger.debug("PDFBox rendering successful, created thumbnail list");
                    }
                }

                if (thumbnails == null || thumbnails.isEmpty()) {
                    logger.warn("No thumbnails generated for file: {}", file.getName());
                    logger.debug("Thumbnail request details - file: {}, dimensions: {}x{}",
                                file.getAbsolutePath(), settings.getWidth(), settings.getHeight());
                    return null;
                }
            }

            logger.debug("Number of thumbnails generated: {}", thumbnails.size());

            // Get the first (and only) thumbnail
            BufferedImage thumbnail = thumbnails.get(0);

            if (thumbnail == null) {
                logger.warn("Generated thumbnail is null for file: {}", file.getName());
                return null;
            }

            // Convert BufferedImage to base64
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(thumbnail, "png", outputStream);
            byte[] thumbnailBytes = outputStream.toByteArray();

            if (thumbnailBytes.length == 0) {
                logger.warn("Generated thumbnail is empty for file: {}", file.getName());
                return null;
            }

            String base64Thumbnail = Base64.getEncoder().encodeToString(thumbnailBytes);

            logger.debug("Successfully generated thumbnail for file: {} (size: {} bytes, base64 length: {})",
                        file.getName(), thumbnailBytes.length, base64Thumbnail.length());

            return base64Thumbnail;

        } catch (ThumbnailingException e) {
            logger.error("ThumbnailingException for file: {} - Message: {}", file.getName(), e.getMessage());
            logger.error("Full exception details:", e);

            // Log the cause if present
            if (e.getCause() != null) {
                logger.error("Caused by: {} - {}", e.getCause().getClass().getName(), e.getCause().getMessage());
                logger.error("Root cause stack trace:", e.getCause());
            }
            return null;

        } catch (NoSuchMethodError e) {
            logger.error("NoSuchMethodError for file: {} - Missing method: {}", file.getName(), e.getMessage());
            logger.error("This indicates a PDFBox version conflict. Stack trace:", e);
            return null;

        } catch (NoClassDefFoundError e) {
            logger.error("NoClassDefFoundError for file: {} - Missing class: {}", file.getName(), e.getMessage());
            logger.error("This indicates a missing JAR dependency. Stack trace:", e);
            return null;

        } catch (IOException e) {
            logger.error("IO error generating thumbnail for file: {}", file.getName(), e);
            return null;

        } catch (Exception e) {
            logger.error("Unexpected error generating thumbnail for file: {}", file.getName());
            logger.error("Exception type: {}", e.getClass().getName());
            logger.error("Exception message: {}", e.getMessage());
            logger.error("Full stack trace:", e);
            return null;
        }
    }

    /**
     * Check if we can generate a thumbnail for this file type
     */
    private boolean canGenerateThumbnail(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            logger.debug("File check failed: null={}, exists={}, isFile={}",
                        file == null, file != null && file.exists(), file != null && file.isFile());
            return false;
        }

        String fileName = file.getName().toLowerCase();
        logger.debug("Checking if can generate thumbnail for: {}", fileName);

        // PDF files (supported by PDFThumbnailer)
        if (fileName.endsWith(".pdf")) {
            logger.debug("PDF file detected: {}", fileName);
            return true;
        }

        // Document files (supported by DOCThumbnailer)
        if (fileName.endsWith(".doc") || fileName.endsWith(".docx")) {
            logger.debug("Document file detected: {}", fileName);
            return true;
        }

        // Image files (supported by ImageThumbnailer / direct ImageIO)
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") ||
            fileName.endsWith(".png") || fileName.endsWith(".gif") ||
            fileName.endsWith(".bmp") || fileName.endsWith(".tiff") ||
            fileName.endsWith(".tif") || fileName.endsWith(".webp")) {
            logger.debug("Image file detected: {}", fileName);
            return true;
        }

        // PSD files (supported via TwelveMonkeys imageio-psd)
        if (fileName.endsWith(".psd")) {
            logger.debug("PSD file detected: {}", fileName);
            return true;
        }

        logger.debug("File type not supported for thumbnails: {}", fileName);
        return false;
    }

    /**
     * Check if thumbnail generation is enabled
     */
    public boolean isEnabled() {
        return settings.isEnabled();
    }

    /**
     * Get the thumbnail settings
     */
    public ThumbnailSettings getSettings() {
        return settings;
    }

    /**
     * Generate a thumbnail for a PSD file using TwelveMonkeys ImageIO PSD plugin.
     *
     * PSD files may contain an embedded JPEG thumbnail in Image Resource 0x040C,
     * exposed by TwelveMonkeys as sub-image index 1. If that embedded thumbnail
     * is >= 400px in either dimension it is used directly (then scaled to target
     * dimensions). Otherwise the full composite image (index 0) is rendered and
     * scaled.
     */
    private BufferedImage generatePsdThumbnail(File psdFile) {
        logger.debug("Generating PSD thumbnail for: {}", psdFile.getName());

        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("psd");
        if (!readers.hasNext()) {
            logger.warn("No PSD ImageReader found - is imageio-psd on the classpath?");
            return null;
        }

        ImageReader reader = readers.next();
        try (ImageInputStream iis = ImageIO.createImageInputStream(psdFile)) {
            if (iis == null) {
                logger.warn("Could not create ImageInputStream for PSD file: {}", psdFile.getName());
                return null;
            }
            reader.setInput(iis, true, true);

            // TwelveMonkeys PSD reader: index 0 = full composite, index 1 = embedded thumbnail
            int numImages = reader.getNumImages(false);
            if (numImages > 1) {
                try {
                    BufferedImage embedded = reader.read(1);
                    if (embedded != null &&
                            (embedded.getWidth() >= 400 || embedded.getHeight() >= 400)) {
                        logger.debug("Using embedded PSD thumbnail ({}x{}) for: {}",
                                embedded.getWidth(), embedded.getHeight(), psdFile.getName());
                        return scaleImage(embedded, settings.getWidth(), settings.getHeight());
                    }
                    logger.debug("Embedded PSD thumbnail too small ({}x{}), falling back to full render",
                            embedded != null ? embedded.getWidth() : 0,
                            embedded != null ? embedded.getHeight() : 0);
                } catch (IOException e) {
                    logger.debug("Could not read embedded PSD thumbnail, falling back to full render: {}", e.getMessage());
                }
            }

            // Fallback: render the full composite image
            logger.debug("Rendering full PSD composite image for: {}", psdFile.getName());
            BufferedImage full = reader.read(0);
            if (full == null) {
                logger.warn("PSD full image read returned null for: {}", psdFile.getName());
                return null;
            }
            logger.debug("Full PSD image size: {}x{}", full.getWidth(), full.getHeight());
            return scaleImage(full, settings.getWidth(), settings.getHeight());

        } catch (IOException e) {
            logger.error("Failed to generate PSD thumbnail for: {}", psdFile.getName(), e);
            return null;
        } catch (Exception e) {
            logger.error("Unexpected error generating PSD thumbnail for: {}", psdFile.getName(), e);
            return null;
        } finally {
            reader.dispose();
        }
    }

    /**
     * Fallback method: render PDF page using PDFBox directly
     * This is used when thumbnails4j fails (e.g., for scanned/image-based PDFs)
     */
    private BufferedImage renderPdfPageWithPDFBox(File pdfFile) {
        logger.debug("Attempting to render PDF page directly with PDFBox for: {}", pdfFile.getName());

        try (PDDocument document = PDDocument.load(pdfFile)) {
            if (document.getNumberOfPages() == 0) {
                logger.warn("PDF has no pages: {}", pdfFile.getName());
                return null;
            }

            PDFRenderer pdfRenderer = new PDFRenderer(document);

            // Render first page at 72 DPI
            BufferedImage image = pdfRenderer.renderImageWithDPI(0, 72);

            logger.debug("Successfully rendered PDF page: {}x{}", image.getWidth(), image.getHeight());

            // Scale to thumbnail size
            return scaleImage(image, settings.getWidth(), settings.getHeight());

        } catch (IOException e) {
            logger.error("Failed to render PDF with PDFBox: {}", pdfFile.getName(), e);
            return null;
        } catch (Exception e) {
            logger.error("Unexpected error rendering PDF with PDFBox: {}", pdfFile.getName(), e);
            return null;
        }
    }

    /**
     * Scale an image to fit within target dimensions while maintaining aspect ratio
     */
    private BufferedImage scaleImage(BufferedImage original, int targetWidth, int targetHeight) {
        // Calculate scaling to maintain aspect ratio
        double scale = Math.min(
            (double) targetWidth / original.getWidth(),
            (double) targetHeight / original.getHeight()
        );

        int scaledWidth = (int) (original.getWidth() * scale);
        int scaledHeight = (int) (original.getHeight() * scale);

        logger.debug("Scaling image from {}x{} to {}x{}",
                    original.getWidth(), original.getHeight(), scaledWidth, scaledHeight);

        BufferedImage scaled = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_RGB);
        scaled.createGraphics().drawImage(
            original.getScaledInstance(scaledWidth, scaledHeight, Image.SCALE_SMOOTH),
            0, 0, null
        );

        return scaled;
    }

    /**
     * Generate thumbnail for image files using ImageIO directly
     * This ensures consistent scaling behavior across all image types
     */
    private BufferedImage generateImageThumbnailDirect(File imageFile) {
        logger.debug("Generating thumbnail directly with ImageIO for: {}", imageFile.getName());

        try {
            BufferedImage original = ImageIO.read(imageFile);

            if (original == null) {
                logger.warn("ImageIO.read returned null for: {}", imageFile.getName());
                return null;
            }

            logger.debug("Original image size: {}x{}", original.getWidth(), original.getHeight());

            // Scale to thumbnail size using our scaleImage method
            BufferedImage scaled = scaleImage(original, settings.getWidth(), settings.getHeight());

            logger.debug("Scaled image to: {}x{}", scaled.getWidth(), scaled.getHeight());

            return scaled;

        } catch (IOException e) {
            logger.error("Failed to read image with ImageIO: {}", imageFile.getName(), e);
            return null;
        } catch (Exception e) {
            logger.error("Unexpected error processing image: {}", imageFile.getName(), e);
            return null;
        }
    }
}
