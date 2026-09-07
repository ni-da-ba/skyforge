package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

final class AuthorshipEcologicalOpportunityProfileCorpusTest {
    @Test
    void generatesEcologicalOpportunityProfileProof() throws Exception {
        Path output = Path.of(
                "build",
                "evidence",
                AuthorshipEcologicalOpportunityProfileCorpusCli.EVIDENCE_ID);

        AuthorshipEcologicalOpportunityProfileCorpusCli.main(
                new String[] {output.toString()});

        assertTrue(Files.isRegularFile(output.resolve("index.html")));
        assertTrue(Files.isRegularFile(output.resolve("atlas.png")));
        assertTrue(Files.isRegularFile(output.resolve("manifest.csv")));
        assertTrue(Files.isRegularFile(output.resolve("profiles.csv")));

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
        assertEquals(10L, profiles.lines().count());
        assertTrue(profiles.contains("horizontalArea"));
        assertTrue(profiles.contains("meanVegetation"));
        assertTrue(profiles.contains("TEMPERATE_WOODLAND"));

        String html = Files.readString(output.resolve("index.html"));
        assertTrue(html.contains("AUTH-0003"));
        assertTrue(html.contains("horizontal habitat area"));
        assertTrue(html.contains("does not define species"));
    }
}
