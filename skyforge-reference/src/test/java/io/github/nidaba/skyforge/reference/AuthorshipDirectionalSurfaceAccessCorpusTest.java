package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

final class AuthorshipDirectionalSurfaceAccessCorpusTest {

    @Test
    void generatesDirectionalSurfaceAccessProof() throws Exception {
        Path output = Path.of(
                "build",
                "evidence",
                AuthorshipDirectionalSurfaceAccessCorpusCli.EVIDENCE_ID);

        AuthorshipDirectionalSurfaceAccessCorpusCli.main(new String[] {output.toString()});

        assertTrue(Files.isRegularFile(output.resolve("index.html")));
        assertTrue(Files.isRegularFile(output.resolve("atlas.png")));
        assertTrue(Files.isRegularFile(output.resolve("manifest.csv")));
        assertTrue(Files.isRegularFile(output.resolve("rays.csv")));

        BufferedImage atlas = ImageIO.read(output.resolve("atlas.png").toFile());
        assertNotNull(atlas);
        assertEquals(1280, atlas.getWidth());
        assertEquals(720, atlas.getHeight());

        String manifest = Files.readString(output.resolve("manifest.csv"));
        assertEquals(7L, manifest.lines().count());
        for (String line : manifest.lines().skip(1).toList()) {
            assertTrue(line.endsWith(",true"), line);
        }

        String rays = Files.readString(output.resolve("rays.csv"));
        assertTrue(rays.lines().count() > 100);
        assertTrue(rays.contains("openTailR"));
        assertTrue(rays.contains("transitions"));
        assertTrue(rays.contains("meanAdjacentGrade"));

        String html = Files.readString(output.resolve("index.html"));
        assertTrue(html.contains("exact AUTH-0096"));
        assertTrue(html.contains("do not declare a runway"));
    }
}
