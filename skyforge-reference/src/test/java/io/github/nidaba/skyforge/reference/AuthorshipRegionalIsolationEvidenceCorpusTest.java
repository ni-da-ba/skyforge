package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

final class AuthorshipRegionalIsolationEvidenceCorpusTest {
    @Test
    void generatesRegionalIsolationEvidenceProof() throws Exception {
        Path output = Path.of(
                "build",
                "evidence",
                AuthorshipRegionalIsolationEvidenceCorpusCli.EVIDENCE_ID);

        AuthorshipRegionalIsolationEvidenceCorpusCli.main(
                new String[] {output.toString()});

        assertTrue(Files.isRegularFile(output.resolve("index.html")));
        assertTrue(Files.isRegularFile(output.resolve("atlas.png")));
        assertTrue(Files.isRegularFile(output.resolve("manifest.csv")));
        assertTrue(Files.isRegularFile(output.resolve("islands.csv")));
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

        String islands = Files.readString(output.resolve("islands.csv"));
        assertEquals(4L, islands.lines().count());
        assertTrue(islands.contains("neighborAssociation"));
        assertTrue(islands.contains("nominalRadialGap"));

        String profiles = Files.readString(output.resolve("profiles.csv"));
        assertEquals(5L, profiles.lines().count());
        assertTrue(profiles.contains("TRANSLATED"));
        assertTrue(profiles.contains("SCALED_2X"));
        assertTrue(profiles.contains("SINGLETON"));

        String html = Files.readString(output.resolve("index.html"));
        assertTrue(html.contains("nominal radial gap"));
        assertTrue(html.contains("not physical terrain-edge separation"));
        assertTrue(html.contains("not physical terrain-edge separation")
                || html.contains("not physical terrain-edge"));
    }
}
