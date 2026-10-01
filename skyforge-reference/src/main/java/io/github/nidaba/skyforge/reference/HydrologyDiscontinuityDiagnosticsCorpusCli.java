package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandHydraulicDiscontinuityDiagnostics;
import io.github.nidaba.skyforge.world.SkyIslandHydraulicDiscontinuityDiagnosticsPlanner;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class HydrologyDiscontinuityDiagnosticsCorpusCli {
    public static final String EVIDENCE_ID = "hydrology-discontinuity-diagnostics-v1";
    private static final long SEED = 0x534B59464F524745L;

    private HydrologyDiscontinuityDiagnosticsCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", EVIDENCE_ID);
        Files.createDirectories(out);

        List<Specimen> specimens = List.of(
                new Specimen("primary-287", descriptor(287L)),
                new Specimen("legacy-649", descriptor(649L)),
                new Specimen("stress-811", descriptor(811L)),
                new Specimen("control-512", descriptor(512L)));

        StringBuilder csv = new StringBuilder(
                "specimen,islandKey,startCell,endCell,profiles,transitions,cascadeProfiles,"
                        + "sourceSurface,terminalSurface,netDrop,accumulatedDownhillDrop,"
                        + "cascadeDrop,cascadeShare,maxSingleDrop\n");

        for (Specimen specimen : specimens) {
            for (SkyIslandHydraulicDiscontinuityDiagnostics d :
                    SkyIslandHydraulicDiscontinuityDiagnosticsPlanner.measure(specimen.descriptor())) {
                csv.append(specimen.name()).append(',')
                        .append(specimen.descriptor().identity().islandKey()).append(',')
                        .append(d.reach().startCellIndex()).append(',')
                        .append(d.reach().endCellIndex()).append(',')
                        .append(d.reach().coarseSegmentCount()).append(',')
                        .append(d.profileTransitionCount()).append(',')
                        .append(d.cascadeProfileCount()).append(',')
                        .append(format(d.sourceSurfacePotential())).append(',')
                        .append(format(d.terminalSurfacePotential())).append(',')
                        .append(format(d.netAuthoredDropPotential())).append(',')
                        .append(format(d.accumulatedDownhillDropPotential())).append(',')
                        .append(format(d.cascadeDownhillDropPotential())).append(',')
                        .append(format(d.cascadeShareOfDownhillDrop())).append(',')
                        .append(format(d.maximumSingleSegmentDropPotential())).append('\n');
            }
        }

        Files.writeString(out.resolve("manifest.csv"), csv, StandardCharsets.UTF_8);
        System.out.println(out.resolve("manifest.csv").toAbsolutePath());
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 8L, 81L, key));
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.9f", value);
    }

    private record Specimen(String name, SkyIslandDescriptor descriptor) {}
}
