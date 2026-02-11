package com.xuesinuo.muppet.tool;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Page.PdfOptions;
import com.microsoft.playwright.options.Margin;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

/**
 * A single HTML→PDF generator using Playwright/Chromium.
 *
 * Features:
 * - Custom HTML text
 * - Supports inline <style> and external network CSS/IMG/Fonts
 * - Custom output directory and filename
 * - Custom paper size via millimeters (width/height in mm)
 * - Accepts a list of local resource files to place alongside the HTML so they can be referenced relatively
 */
@Slf4j
public class PdfGenerator {

    /**
     * Generate a PDF from HTML content.
     *
     * @param html              HTML string (can contain inline CSS, external URLs work too)
     * @param outputDir         Directory to write the PDF to (created if missing)
     * @param filenameNoExt     Output filename without ".pdf"; if null/blank, an auto name will be used
     * @param pageWidthMm       Page width in millimeters (e.g., 210 for A4 width)
     * @param pageHeightMm      Page height in millimeters (e.g., 297 for A4 height)
     * @param siblingFilePaths  Optional list of file paths to copy next to the HTML file for relative references
     * @return Path to the generated PDF, or null on failure
     */
    public static String generatePdf(
            String html,
            String outputDir,
            String filenameNoExt,
            double pageWidthMm,
            double pageHeightMm,
            List<String> siblingFilePaths) {
        Objects.requireNonNull(html, "html must not be null");
        Objects.requireNonNull(outputDir, "outputDir must not be null");

        try {
            Path outDir = Paths.get(outputDir);
            Files.createDirectories(outDir);

            String fileBase = (filenameNoExt != null && !filenameNoExt.isBlank()) ? filenameNoExt : AutoNames.autoFilename();
            Path pdfPath = outDir.resolve(fileBase + ".pdf");

            // Prepare a temporary working directory with the HTML and its sibling assets
            Path workDir = Files.createTempDirectory("pdfgen_");
            Path htmlPath = workDir.resolve("index.html");
            Files.writeString(htmlPath, html);

            // Copy sibling files next to the HTML so that relative paths like ./image.png work
            if (siblingFilePaths != null) {
                for (String p : siblingFilePaths) {
                    if (p == null || p.isBlank()) continue;
                    Path src = Paths.get(p);
                    if (Files.isRegularFile(src)) {
                        Path dst = workDir.resolve(src.getFileName().toString());
                        try {
                            Files.copy(src, dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException copyEx) {
                            log.warn("Failed to copy resource {}: {}", p, copyEx.toString());
                        }
                    } else {
                        log.warn("Sibling path is not a file or does not exist: {}", p);
                    }
                }
            }

            // Render with Playwright/Chromium
            try (Playwright playwright = Playwright.create();
                 Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
                 Page page = browser.newPage()) {

                // Navigate to local HTML so that relative resources resolve to the working dir
                page.navigate(htmlPath.toUri().toString());

                PdfOptions options = new PdfOptions()
                        .setPrintBackground(true)
                        .setWidth(mm(pageWidthMm))
                        .setHeight(mm(pageHeightMm))
                        .setMargin(new Margin().setTop("0mm").setBottom("0mm").setLeft("0mm").setRight("0mm"))
                        .setPath(pdfPath);

                page.pdf(options);
                log.info("PDF generated: {}", pdfPath);
            }

            // Best-effort cleanup of working dir
            cleanupDir(workDir);
            return pdfPath.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String mm(double v) { return String.format("%smm", stripTrailingZeros(v)); }

    private static String stripTrailingZeros(double v) {
        String s = Double.toString(v);
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return s;
    }

    private static void cleanupDir(Path dir) {
        try {
            if (dir == null) return;
            // Delete files first, then the directory
            Files.list(dir).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
            Files.deleteIfExists(dir);
        } catch (IOException ignored) { }
    }

    private static class AutoNames {
        static String autoFilename() {
            java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
            String ts = java.time.LocalDateTime.now().format(fmt);
            String rnd = java.util.UUID.randomUUID().toString().substring(0, 6);
            return "pdf_" + ts + "_" + rnd;
        }
    }
}
