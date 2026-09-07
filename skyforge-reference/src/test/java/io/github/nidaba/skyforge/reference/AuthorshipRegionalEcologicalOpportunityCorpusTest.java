package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

final class AuthorshipRegionalEcologicalOpportunityCorpusTest {
    @Test
    void generatesRegionalEcologicalOpportunityProof() throws Exception {
        Path output = Path.of(
                "build",
                "evidence",
                AuthorshipRegionalEcologicalOpportunityCorpusCli.EVIDENCE_ID);

        AuthorshipRegionalEcologicalOpportunityCorpusCli.main(
                new String[] {output.toString()});

        assertTrue(Files.isRegularFile(output.resolve("index.html")));
        assertTrue(Files.isRegularFile(output.resolve("atlas.png")));
        assertTrue(Files.isRegularFile(output.resolve("manifest.csv")));
        assertTrue(Files.isRegularFile(output.resolve("islands.csv")));
        assertTrue(Files.isRegularFile(output.resolve("regional.csv")));

        BufferedImage atlas = ImageIO.read(output.resolve("atlas.png").toFile());
        assertNotNull(atlas);
        assertEquals(1280, atlas.getWidth());
        assertEquals(720, atlas.getHeight());

        String manifest = Files.readString(output.resolve("manifest.csv"));
        assertEquals(7L, manifest.lines().count());
        for (String line : manifest.lines().skip(1).toList()) {
            assertTrue(line.endsWith(",true"), line);
        }

        String islands = Files.readString(output.resolve("islands.csv"));
        assertEquals(4L, islands.lines().count());
        assertTrue(islands.contains("association"));
        assertTrue(islands.contains("horizontalArea"));

        String regional = Files.readString(output.resolve("regional.csv"));
        assertEquals(2L, regional.lines().count());
        assertTrue(regional.contains("totalHorizontalArea"));
        assertTrue(regional.contains("TEMPERATE_WOODLAND"));

        String html = Files.readString(output.resolve("index.html"));
        assertTrue(html.contains("AUTH-0089"));
        assertTrue(html.contains("Area-weighted"));
        assertTrue(html.contains("no species"));
    }
}
