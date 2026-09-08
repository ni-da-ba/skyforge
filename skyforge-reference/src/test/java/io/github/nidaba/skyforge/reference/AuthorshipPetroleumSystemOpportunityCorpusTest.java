package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

final class AuthorshipPetroleumSystemOpportunityCorpusTest {

    @Test
    void generatesPetroleumSystemOpportunityProof() throws Exception {
        Path output = Path.of(
                "build",
                "evidence",
                AuthorshipPetroleumSystemOpportunityCorpusCli.EVIDENCE_ID);

        AuthorshipPetroleumSystemOpportunityCorpusCli.main(
                new String[] {output.toString()});

        assertTrue(Files.isRegularFile(output.resolve("index.html")));
        assertTrue(Files.isRegularFile(output.resolve("atlas.png")));
        assertTrue(Files.isRegularFile(output.resolve("manifest.csv")));
        assertTrue(Files.isRegularFile(output.resolve("profiles.csv")));
        assertTrue(Files.isRegularFile(output.resolve("cells.csv")));

        BufferedImage atlas = ImageIO.read(output.resolve("atlas.png").toFile());
        assertNotNull(atlas);
        assertEquals(1280, atlas.getWidth());
        assertEquals(720, atlas.getHeight());

        String manifest = Files.readString(output.resolve("manifest.csv"));
        assertEquals(7L, manifest.lines().count());
        for (String line : manifest.lines().skip(1).toList()) {
            assertTrue(line.endsWith(",true"), line);
        }

        String profiles = Files.readString(output.resolve("profiles.csv"));
        assertEquals(7L, profiles.lines().count());
        assertTrue(profiles.contains("peakSystem"));
        assertTrue(profiles.contains("nonzeroSystemCells"));

        String cells = Files.readString(output.resolve("cells.csv"));
        assertTrue(cells.lines().count() > 100);
        assertTrue(cells.contains("deeperSource"));
        assertTrue(cells.contains("shallowerSeal"));

        String html = Files.readString(output.resolve("index.html"));
        assertTrue(html.contains("exact AUTH-0033"));
        assertTrue(html.contains("does not define literal oil deposits"));
    }
}
