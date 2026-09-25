package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyCandidate;
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyKind;
import io.github.nidaba.skyforge.world.SkyIslandWaterbodyPlanner;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Bounded discovery scan for fixed POND/LAKE calibration specimens. */
public final class HydrologyWaterbodyDiscoveryCorpusCli {
    public static final String EVIDENCE_ID = "hydrology-waterbody-discovery-v1";
    private static final long SEED = 0x534B59464F524745L;
    private static final int MAX_KEY = 1024;
    private static final int TARGET_PER_KIND = 3;

    private HydrologyWaterbodyDiscoveryCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", EVIDENCE_ID);
        Files.createDirectories(out);

        StringBuilder csv = new StringBuilder(
                "province,cluster,islandKey,morphology,kind,sinkCell,catchmentFraction,"
                        + "relativeInflow,retentionPotential,saturationPotential,persistence,basinScale\n");

        int ponds = 0;
        int lakes = 0;

        outer:
        for (long[] namespace : new long[][] {{6L, 61L}, {8L, 81L}}) {
            for (long key = 1; key <= MAX_KEY; key++) {
                SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                        SkyIslandIdentity.of(SEED, namespace[0], namespace[1], key));
                for (SkyIslandWaterbodyCandidate candidate :
                        SkyIslandWaterbodyPlanner.plan(descriptor).candidates()) {
                    SkyIslandWaterbodyKind kind = candidate.kind();
                    if (kind == SkyIslandWaterbodyKind.WETLAND) {
                        continue;
                    }
                    if (kind == SkyIslandWaterbodyKind.POND && ponds >= TARGET_PER_KIND) {
                        continue;
                    }
                    if (kind == SkyIslandWaterbodyKind.LAKE && lakes >= TARGET_PER_KIND) {
                        continue;
                    }

                    csv.append(namespace[0]).append(',')
                            .append(namespace[1]).append(',')
                            .append(key).append(',')
                            .append(descriptor.morphologyFamily().identifier()).append(',')
                            .append(kind.name().toLowerCase()).append(',')
                            .append(candidate.sinkCellIndex()).append(',')
                            .append(candidate.catchmentFraction()).append(',')
                            .append(candidate.relativeInflow()).append(',')
                            .append(candidate.retentionPotential()).append(',')
                            .append(candidate.saturationPotential()).append(',')
                            .append(candidate.persistence()).append(',')
                            .append(candidate.basinScale()).append('\n');

                    if (kind == SkyIslandWaterbodyKind.POND) {
                        ponds++;
                    } else if (kind == SkyIslandWaterbodyKind.LAKE) {
                        lakes++;
                    }

                    if (ponds >= TARGET_PER_KIND && lakes >= TARGET_PER_KIND) {
                        break outer;
                    }
                }
            }
        }

        csv.append("#summary,ponds=").append(ponds)
                .append(",lakes=").append(lakes)
                .append(",maxKey=").append(MAX_KEY)
                .append('\n');

        Files.writeString(out.resolve("discovery.csv"), csv, StandardCharsets.UTF_8);
        System.out.println(out.resolve("discovery.csv").toAbsolutePath());
    }
}
