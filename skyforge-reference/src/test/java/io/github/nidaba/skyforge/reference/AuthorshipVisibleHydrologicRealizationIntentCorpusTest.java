package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

final class AuthorshipVisibleHydrologicRealizationIntentCorpusTest {

    @Test
    void generatesOneForOneVisibleHydrologyProjectionProof() throws Exception {
        Path output = Path.of(
                "build",
                "evidence",
                AuthorshipVisibleHydrologicRealizationIntentCorpusCli.EVIDENCE_ID);

        AuthorshipVisibleHydrologicRealizationIntentCorpusCli.main(
                new String[] {output.toString()});

        assertTrue(Files.isRegularFile(output.resolve("index.html")));
        assertTrue(Files.isRegularFile(output.resolve("atlas.png")));
        assertTrue(Files.isRegularFile(output.resolve("manifest.csv")));
        assertTrue(Files.isRegularFile(output.resolve("provenance.csv")));

        BufferedImage atlas = ImageIO.read(output.resolve("atlas.png").toFile());
        assertNotNull(atlas);
        assertEquals(1280, atlas.getWidth());
        assertEquals(720, atlas.getHeight());

        String manifest = Files.readString(output.resolve("manifest.csv"));
        assertEquals(7, manifest.lines().count());

        long sourceChannels = 0L;
        long sourceWaterbodies = 0L;
        long sourceDrops = 0L;
        for (String line : manifest.lines().skip(1).toList()) {
            String[] columns = line.split(",", -1);
            assertEquals(13, columns.length);
            assertEquals(columns[2], columns[3], "channel source/intent count drift");
            assertEquals(columns[4], columns[5], "waterbody source/intent count drift");
            assertEquals(columns[6], columns[7], "drop source/intent count drift");
            sourceChannels += Long.parseLong(columns[2]);
            sourceWaterbodies += Long.parseLong(columns[4]);
            sourceDrops += Long.parseLong(columns[6]);
        }
        assertTrue(sourceChannels > 0L);
        assertTrue(sourceWaterbodies > 0L);
        assertTrue(sourceDrops > 0L);

        String provenance = Files.readString(output.resolve("provenance.csv"));
        assertTrue(provenance.lines().count() > 1L);
        assertTrue(provenance.contains("CHANNEL_WATER"));
        assertTrue(provenance.contains("RETAINED_WATER"));

        String html = Files.readString(output.resolve("index.html"));
        assertTrue(html.contains("one-for-one"));
        assertTrue(html.contains("No Minecraft block selection, block placement or fluid scheduling"));
    }
}
