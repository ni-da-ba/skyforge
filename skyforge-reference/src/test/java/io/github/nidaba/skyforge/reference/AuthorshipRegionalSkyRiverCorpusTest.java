package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

final class AuthorshipRegionalSkyRiverCorpusTest {
    @Test
    void generatesRegionalSkyRiverProof() throws Exception {
        Path output = Path.of(
                "build",
                "evidence",
                AuthorshipRegionalSkyRiverCorpusCli.EVIDENCE_ID);

        AuthorshipRegionalSkyRiverCorpusCli.main(
                new String[] {output.toString()});

        assertTrue(Files.isRegularFile(output.resolve("index.html")));
        assertTrue(Files.isRegularFile(output.resolve("atlas.png")));
        assertTrue(Files.isRegularFile(output.resolve("manifest.csv")));
        assertTrue(Files.isRegularFile(output.resolve("participants.csv")));
        assertTrue(Files.isRegularFile(output.resolve("trajectory.csv")));

        BufferedImage atlas = ImageIO.read(output.resolve("atlas.png").toFile());
        assertNotNull(atlas);
        assertEquals(1280, atlas.getWidth());
        assertEquals(720, atlas.getHeight());

        String manifest = Files.readString(output.resolve("manifest.csv"));
        assertEquals(7L, manifest.lines().count());
        for (String line : manifest.lines().skip(1).toList()) {
            assertTrue(line.endsWith(",true"), line);
        }

        String participants = Files.readString(output.resolve("participants.csv"));
        assertEquals(3L, participants.lines().count());
        assertTrue(participants.contains("SOURCE"));
        assertTrue(participants.contains("SINK"));

        String trajectory = Files.readString(output.resolve("trajectory.csv"));
        assertEquals(5L, trajectory.lines().count());
        assertTrue(trajectory.contains("worldY"));

        String html = Files.readString(output.resolve("index.html"));
        assertTrue(html.contains("cross-island floating river"));
        assertTrue(html.contains("not a Minecraft fluid/block path"));
        assertTrue(html.contains("Content owns rarity"));
    }
}
