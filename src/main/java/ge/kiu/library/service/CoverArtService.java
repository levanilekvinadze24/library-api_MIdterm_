package ge.kiu.library.service;

import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates simple, original cover artwork (PNG) for books that have no uploaded image.
 * Used by the demo data so the UI shows real image files stored on disk.
 */
@Service
public class CoverArtService {

    private static final int WIDTH = 600;
    private static final int HEIGHT = 900;

    /** Pairs of gradient colors (top, bottom). */
    private static final Color[][] PALETTES = {
            {new Color(0x1D6B5F), new Color(0x0B3B34)},
            {new Color(0x8A3B2E), new Color(0x3E1510)},
            {new Color(0x2F4A8A), new Color(0x121F42)},
            {new Color(0x6B4C9A), new Color(0x2B1B45)},
            {new Color(0xB07D2B), new Color(0x4F3510)},
            {new Color(0x2E6E8E), new Color(0x0E2E3D)},
            {new Color(0x7A2E52), new Color(0x33101F)},
            {new Color(0x3F6B2E), new Color(0x182C10)},
    };

    public byte[] generatePng(String title, String author, String seed) {
        int hash = Math.abs(seed.hashCode());
        Color[] palette = PALETTES[hash % PALETTES.length];

        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            // background gradient
            g.setPaint(new GradientPaint(0, 0, palette[0], 0, HEIGHT, palette[1]));
            g.fillRect(0, 0, WIDTH, HEIGHT);

            // decorative translucent circles, positions derived from the seed
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.10f));
            g.setColor(Color.WHITE);
            for (int i = 0; i < 4; i++) {
                int size = 220 + ((hash >> (i * 3)) % 260);
                int x = ((hash >> (i * 5)) % WIDTH) - size / 3;
                int y = ((hash >> (i * 4)) % (HEIGHT / 2)) + 40;
                g.fill(new Ellipse2D.Double(x, y, size, size));
            }
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));

            // inner frame
            g.setColor(new Color(255, 255, 255, 90));
            g.setStroke(new BasicStroke(3f));
            g.drawRect(36, 36, WIDTH - 72, HEIGHT - 72);

            // title (wrapped, bottom area)
            Font titleFont = new Font(Font.SERIF, Font.BOLD, 58);
            g.setFont(titleFont);
            FontMetrics tm = g.getFontMetrics();
            List<String> lines = wrap(title, tm, WIDTH - 140);
            if (lines.size() > 4) {
                titleFont = new Font(Font.SERIF, Font.BOLD, 46);
                g.setFont(titleFont);
                tm = g.getFontMetrics();
                lines = wrap(title, tm, WIDTH - 140);
            }
            int lineHeight = tm.getHeight();
            int y = HEIGHT - 170 - (lines.size() - 1) * lineHeight;
            g.setColor(Color.WHITE);
            for (String line : lines) {
                g.drawString(line, 70, y);
                y += lineHeight;
            }

            // accent line + author
            g.setColor(new Color(255, 255, 255, 170));
            g.fillRect(70, HEIGHT - 135, 60, 4);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 26));
            g.drawString(author.toUpperCase(), 70, HEIGHT - 90);
        } finally {
            g.dispose();
        }

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static List<String> wrap(String text, FontMetrics metrics, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (metrics.stringWidth(candidate) <= maxWidth || current.isEmpty()) {
                current = new StringBuilder(candidate);
            } else {
                lines.add(current.toString());
                current = new StringBuilder(word);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }
}
