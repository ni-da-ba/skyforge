package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

final class AuthorshipPublishedAuthoredRealizationBindingCorpusTest {
    @Test
    void generatesExactPublishedAuthoredRealizationBindingProof() throws Exception {
        Path output = Path.of(
                "build",
                "evidence",
                AuthorshipPublishedAuthoredRealizationBindingCorpusCli.EVIDENCE_ID);

        AuthorshipPublishedAuthoredRealizationBindingCorpusCli.main(
                new String[] {output.toString()});

        assertTrue(Files.isRegularFile(output.resolve("index.html")));
        assertTrue(Files.isRegularFile(output.resolve("atlas.png")));
        assertTrue(Files.isRegularFile(output.resolve("manifest.csv")));
        assertTrue(Files.isRegularFile(output.resolve("coverage.csv")));

        BufferedImage atlas = ImageIO.read(output.resolve("atlas.png").toFile());
        assertNotNull(atlas);
        assertEquals(1280, atlas.getWidth());
        assertEquals(720, atlas.getHeight());

        String manifest = Files.readString(output.resolve("manifest.csv"));
        assertEquals(7L, manifest.lines().count());
        for (String line : manifest.lines().skip(1).toList()) {
            assertTrue(line.endsWith(",true"), line);
        }

        String coverage = Files.readString(output.resolve("coverage.csv"));
        assertTrue(coverage.lines().count() > 1L);
        for (String line : coverage.lines().skip(1).toList()) {
            assertTrue(line.endsWith(",true"), line);
        }

        String html = Files.readString(output.resolve("index.html"));
        assertTrue(html.contains("AUTH-0046"));
        assertTrue(html.contains("AUTH-0049"));
        assertTrue(html.contains("No Minecraft block"));
    }
}
