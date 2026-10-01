package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.recipes.skyisland.SignalFreeSkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.reference.evidence.SuspendedVolumeEvidence;
import io.github.nidaba.skyforge.reference.evidence.SuspendedVolumeEvidenceGenerator;
import io.github.nidaba.skyforge.reference.evidence.SuspendedVolumeEvidenceWriter;
import io.github.nidaba.skyforge.reference.sampling.SamplingOrder;
import io.github.nidaba.skyforge.reference.volume.SuspendedVolumeReferenceDomain;
import java.io.IOException;
import java.nio.file.Path;

/** Generates the canonical, not-yet-golden signal-free suspended-volume evidence package. */
public final class SuspendedVolumeEvidenceCli {
    /** Stable evidence-package identifier. */
    public static final String EVIDENCE_ID = "signal-free-suspended-volume-v1";

    private SuspendedVolumeEvidenceCli() {}

    /** Samples 4,805,121 densities and writes the complete 3D evidence package. */
    public static void main(String[] arguments) throws IOException {
        if (arguments.length > 1) {
            throw new IllegalArgumentException(
                    "usage: SuspendedVolumeEvidenceCli [output-directory]");
        }
        Path output = arguments.length == 1
                ? Path.of(arguments[0])
                : Path.of("build", "evidence", EVIDENCE_ID);
        CompiledSkyIslandVolume compiled = new SignalFreeSkyIslandVolumeRecipe()
                .compile(SuspendedVolumeReferenceDomain.descriptor());
        SuspendedVolumeEvidence evidence = new SuspendedVolumeEvidenceGenerator().generate(
                compiled, SuspendedVolumeReferenceDomain.grid(), SamplingOrder.FORWARD);
        String version = System.getProperty("skyforge.version", "development");
        Path manifest = new SuspendedVolumeEvidenceWriter().write(evidence, output, version);
        System.out.printf(
                "solid samples: %d; components: %d; face contacts: %d; minimum clearance: %.3f%n",
                evidence.metrics().solidSampleCount(),
                evidence.metrics().connectedSolidComponents(),
                evidence.metrics().faceContacts().total(),
                evidence.metrics().airClearance().minimum());
        System.out.println(manifest.toAbsolutePath());
        System.out.println(output.resolve("index.html").toAbsolutePath());
    }
}
