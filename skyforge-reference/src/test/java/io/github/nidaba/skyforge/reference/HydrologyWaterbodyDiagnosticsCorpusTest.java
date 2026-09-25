package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HydrologyWaterbodyDiagnosticsCorpusTest {
    @TempDir Path temp;

    @Test
    void retainedBasinCorpusIsDeterministic() throws Exception {
        Path first = temp.resolve("a");
        Path second = temp.resolve("b");
        HydrologyWaterbodyDiagnosticsCorpusCli.main(new String[] {first.toString()});
        HydrologyWaterbodyDiagnosticsCorpusCli.main(new String[] {second.toString()});

        String a = Files.readString(first.resolve("basin-manifest.csv"));
        String b = Files.readString(second.resolve("basin-manifest.csv"));
        assertEquals(a, b);
        assertTrue(a.contains("maxShorelineGrade"));
        assertTrue(a.contains("reachesSearchBoundary"));
        assertTrue(Files.isRegularFile(first.resolve("README.txt")));
        System.out.println("--- basin-manifest.csv ---");
        System.out.println(a);
    }
}
