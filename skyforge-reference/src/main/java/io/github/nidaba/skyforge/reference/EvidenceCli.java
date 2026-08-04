package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.island.IslandDescriptor;
import io.github.nidaba.skyforge.recipes.island.CompiledIsland;
import io.github.nidaba.skyforge.recipes.island.SignalFreeIslandRecipe;
import io.github.nidaba.skyforge.reference.evidence.EvidencePackageWriter;
import io.github.nidaba.skyforge.reference.evidence.IslandEvidence;
import io.github.nidaba.skyforge.reference.evidence.IslandEvidenceGenerator;
import io.github.nidaba.skyforge.reference.sampling.SamplingOrder;
import java.io.IOException;
import java.nio.file.Path;

/** Generates the first fixed signal-free Skyforge evidence package. */
public final class EvidenceCli {
    private EvidenceCli() {}

    /** Generates a standard 1024-square evidence directory at the optional output path. */
    public static void main(String[] arguments) throws IOException {
        if (arguments.length > 1) {
            throw new IllegalArgumentException("usage: EvidenceCli [output-directory]");
        }
        Path output = arguments.length == 1
                ? Path.of(arguments[0])
                : Path.of("build", "evidence", "signal-free-island-v1");
        IslandDescriptor descriptor = new IslandDescriptor(
                IslandDescriptor.SCHEMA_VERSION,
                0x534b59464f524745L,
                0.0,
                0.0,
                256.0,
                96.0,
                64.0,
                Math.PI / 6.0,
                0.65,
                0.0,
                32.0);
        CompiledIsland compiled = new SignalFreeIslandRecipe().compile(descriptor);
        IslandEvidenceGenerator generator = new IslandEvidenceGenerator();
        IslandEvidence evidence = generator.generate(
                compiled, generator.standardGrid(descriptor), SamplingOrder.FORWARD);
        String version = System.getProperty("skyforge.version", "development");
        Path manifest = new EvidencePackageWriter().write(evidence, output, version);
        System.out.println(manifest.toAbsolutePath());
    }
}
