package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

final class AuthorshipRegionalPetroleumSystemOpportunityCorpusTest {
    @Test
    void generatesRegionalPetroleumSystemOpportunityProof() throws Exception {
        Path output = Path.of(
                "build",
                "evidence",
                AuthorshipRegionalPetroleumSystemOpportunityCorpusCli.EVIDENCE_ID);

        AuthorshipRegionalPetroleumSystemOpportunityCorpusCli.main(
                new String[] {output.toString()});

        assertTrue(Files.isRegularFile(output.resolve("index.html")));
        assertTrue(Files.isRegularFile(output.resolve("atlas.png")));
        assertTrue(Files.isRegularFile(output.resolve("manifest.csv")));
        assertTrue(Files.isRegularFile(output.resolve("inventory.csv")));
        assertTrue(Files.isRegularFile(output.resolve("ranking.csv")));

        BufferedImage atlas = ImageIO.read(output.resolve("atlas.png").toFile());
        assertNotNull(atlas);
        assertEquals(1280, atlas.getWidth());
        assertEquals(720, atlas.getHeight());

        String manifest = Files.readString(output.resolve("manifest.csv"));
        assertEquals(7L, manifest.lines().count());
        for (String line : manifest.lines().skip(1).toList()) {
            assertTrue(line.endsWith(",true"), line);
        }

        String inventory = Files.readString(output.resolve("inventory.csv"));
        assertTrue(inventory.lines().count() >= 4);
        assertTrue(inventory.contains("meanSystemOpportunity"));
        assertTrue(inventory.contains("peakSystemOpportunity"));
        assertTrue(inventory.contains(",true,"));

        String ranking = Files.readString(output.resolve("ranking.csv"));
        assertTrue(ranking.contains("meanSystemOpportunity"));

        String html = Files.readString(output.resolve("index.html"));
        assertTrue(html.contains("exact AUTH-0087"));
        assertTrue(html.contains("accepted AUTH-0098"));
        assertTrue(html.contains("C22 owns R3 / STRATEGIC_NODE"));
        assertTrue(html.contains("Implementation owns concrete petroleum deposits"));
    }
}
