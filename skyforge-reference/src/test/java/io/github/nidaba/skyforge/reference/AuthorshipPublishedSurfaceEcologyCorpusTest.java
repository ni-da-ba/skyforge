package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

final class AuthorshipPublishedSurfaceEcologyCorpusTest {
    @Test
    void generatesPublishedSurfaceEcologyProof() throws Exception {
        Path output = Path.of(
                "build",
                "evidence",
                AuthorshipPublishedSurfaceEcologyCorpusCli.EVIDENCE_ID);

        AuthorshipPublishedSurfaceEcologyCorpusCli.main(
                new String[] {output.toString()});

        assertTrue(Files.isRegularFile(output.resolve("index.html")));
        assertTrue(Files.isRegularFile(output.resolve("atlas.png")));
        assertTrue(Files.isRegularFile(output.resolve("manifest.csv")));
        assertTrue(Files.isRegularFile(output.resolve("exact.csv")));
        assertTrue(Files.isRegularFile(output.resolve("regimes.csv")));

        BufferedImage atlas = ImageIO.read(output.resolve("atlas.png").toFile());
        assertNotNull(atlas);
        assertEquals(1280, atlas.getWidth());
        assertEquals(720, atlas.getHeight());

        String manifest = Files.readString(output.resolve("manifest.csv"));
        assertEquals(8L, manifest.lines().count());
        for (String line : manifest.lines().skip(1).toList()) {
            assertTrue(line.endsWith(",true"), line);
        }

        String exact = Files.readString(output.resolve("exact.csv"));
        assertEquals(2L, exact.lines().count());
        assertTrue(exact.contains(",true,"));

        String regimes = Files.readString(output.resolve("regimes.csv"));
        assertTrue(regimes.lines().count() >= 6L);

        String html = Files.readString(output.resolve("index.html"));
        assertTrue(html.contains("AUTH-0003"));
        assertTrue(html.contains("AUTH-0087"));
        assertTrue(html.contains("without introducing Minecraft biome keys"));
    }
}
