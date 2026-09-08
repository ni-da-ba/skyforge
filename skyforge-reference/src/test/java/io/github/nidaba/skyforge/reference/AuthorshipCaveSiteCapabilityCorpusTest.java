package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

final class AuthorshipCaveSiteCapabilityCorpusTest {

    @Test
    void generatesCaveSiteCapabilityProof() throws Exception {
        Path output = Path.of(
                "build",
                "evidence",
                AuthorshipCaveSiteCapabilityCorpusCli.EVIDENCE_ID);

        AuthorshipCaveSiteCapabilityCorpusCli.main(new String[] {output.toString()});

        assertTrue(Files.isRegularFile(output.resolve("index.html")));
        assertTrue(Files.isRegularFile(output.resolve("atlas.png")));
        assertTrue(Files.isRegularFile(output.resolve("manifest.csv")));
        assertTrue(Files.isRegularFile(output.resolve("systems.csv")));
        assertTrue(Files.isRegularFile(output.resolve("nodes.csv")));

        BufferedImage atlas = ImageIO.read(output.resolve("atlas.png").toFile());
        assertNotNull(atlas);
        assertEquals(1280, atlas.getWidth());
        assertEquals(720, atlas.getHeight());

        String manifest = Files.readString(output.resolve("manifest.csv"));
        assertEquals(7L, manifest.lines().count());
        for (String line : manifest.lines().skip(1).toList()) {
            assertTrue(line.endsWith(",true"), line);
        }

        String systems = Files.readString(output.resolve("systems.csv"));
        assertTrue(systems.lines().count() > 1);
        assertTrue(systems.contains("meanMineralHost"));
        assertTrue(systems.contains("meanHostDistance"));

        String nodes = Files.readString(output.resolve("nodes.csv"));
        assertTrue(nodes.lines().count() > 1);
        assertTrue(nodes.contains("nearestHostCell"));
        assertTrue(nodes.contains("mineralHost"));

        String html = Files.readString(output.resolve("index.html"));
        assertTrue(html.contains("exact accepted cave topology"));
        assertTrue(html.contains("does not declare a cave useful"));
    }
}
