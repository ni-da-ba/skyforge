package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandBoundedHydraulicConvergenceDiagnostics;
import io.github.nidaba.skyforge.world.SkyIslandBoundedHydraulicConvergencePlanner;
import io.github.nidaba.skyforge.world.SkyIslandBoundedHydraulicProfilePlan;
import io.github.nidaba.skyforge.world.SkyIslandBoundedHydraulicProfilePlanner;
import io.github.nidaba.skyforge.world.SkyIslandBoundedHydraulicReachOutcome;
import io.github.nidaba.skyforge.world.SkyIslandChannelTerminalFate;
import io.github.nidaba.skyforge.world.SkyIslandChannelTerminalFatePlanner;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandGeomorphicReachDiagnostics;
import io.github.nidaba.skyforge.world.SkyIslandHydraulicQpResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class HydrologyBoundedProfileCorpusCli {
    public static final String EVIDENCE_ID = "hydrology-bounded-profile-v1";
    private static final long SEED = 0x534B59464F524745L;

    private HydrologyBoundedProfileCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", EVIDENCE_ID);
        Files.createDirectories(out);

        List<Specimen> specimens = List.of(
                new Specimen("primary-287", descriptor(8L, 81L, 287L)),
                new Specimen("lake-609", descriptor(8L, 81L, 609L)),
                new Specimen("control-241", descriptor(8L, 81L, 241L)),
                new Specimen("confluence-632", descriptor(8L, 81L, 632L)),
                new Specimen("stress-811", descriptor(8L, 81L, 811L)),
                new Specimen("retained-83", descriptor(6L, 61L, 83L)),
                new Specimen("control-77", descriptor(6L, 61L, 77L)),
                new Specimen("control-118", descriptor(6L, 61L, 118L)),
                new Specimen("stress-512", descriptor(6L, 61L, 512L)),
                // Exact 8/81 ordinary-solve proving grounds used by the F2C world test corpus.
                // At least one must remain a solved transition-free reach so convergence evidence
                // cannot silently collapse to a header-only manifest.
                new Specimen("ordinary-77-8-81", descriptor(8L, 81L, 77L)),
                new Specimen("ordinary-118-8-81", descriptor(8L, 81L, 118L)),
                new Specimen("ordinary-512-8-81", descriptor(8L, 81L, 512L)));

        StringBuilder csv = new StringBuilder(
                "specimen,islandKey,startCell,endCell,status,deferrals,diagnostic,solverStatus,"
                        + "objective,primalResidual,stationarityResidual,dualResidual,"
                        + "complementarityResidual,d2Accepted,violations,maxLoweringPotential,"
                        + "maxLateralRecoveryGrade,maxContainmentDeficitWorld,"
                        + "normalizedExcavationBurden,maxLongitudinalGradeWorld\n");

        StringBuilder terminalFateCsv = new StringBuilder(
                "specimen,islandKey,channelTerminalCell,watershedTerminalCell,fate,waterbodyKind,watershedPath\n");

        StringBuilder convergenceCsv = new StringBuilder(
                "specimen,islandKey,startCell,endCell,coarseSamples,mediumSamples,fineSamples,"
                        + "coarseStartHeadWorld,mediumStartHeadWorld,fineStartHeadWorld,"
                        + "coarseEndHeadWorld,mediumEndHeadWorld,fineEndHeadWorld,"
                        + "coarseObjectivePerLength,mediumObjectivePerLength,fineObjectivePerLength,"
                        + "coarseMaxGrade,mediumMaxGrade,fineMaxGrade,"
                        + "coarseExcavation,mediumExcavation,fineExcavation,"
                        + "coarseMaxLoweringWorld,mediumMaxLoweringWorld,fineMaxLoweringWorld,"
                        + "coarseHeadDependentD2Pass,mediumHeadDependentD2Pass,"
                        + "fineHeadDependentD2Pass\n");

        for (Specimen specimen : specimens) {
            SkyIslandBoundedHydraulicProfilePlan plan =
                    SkyIslandBoundedHydraulicProfilePlanner.plan(specimen.descriptor());
            for (SkyIslandBoundedHydraulicReachOutcome outcome : plan.outcomes()) {
                var semantic = outcome.skeleton().geomorphicRoute().semanticReach();
                csv.append(specimen.name()).append(',')
                        .append(specimen.descriptor().identity().islandKey()).append(',')
                        .append(semantic.startCellIndex()).append(',')
                        .append(semantic.endCellIndex()).append(',')
                        .append(outcome.status()).append(',')
                        .append(join(outcome.deferralReasons())).append(',')
                        .append(csvField(outcome.diagnostic().orElse(""))).append(',');

                if (outcome.solverResult().isPresent()) {
                    SkyIslandHydraulicQpResult solve = outcome.solverResult().orElseThrow();
                    csv.append(solve.status()).append(',')
                            .append(format(solve.objective())).append(',')
                            .append(format(solve.primalResidual())).append(',')
                            .append(format(solve.stationarityResidual())).append(',')
                            .append(format(solve.dualFeasibilityResidual())).append(',')
                            .append(format(solve.complementarityResidual())).append(',');
                } else {
                    csv.append(",,,,,,");
                }

                if (outcome.qualification().isPresent()) {
                    var qualification = outcome.qualification().orElseThrow();
                    SkyIslandGeomorphicReachDiagnostics d = qualification.diagnostics();
                    csv.append(qualification.accepted()).append(',')
                            .append(join(qualification.violations())).append(',')
                            .append(format(d.maximumCenterlineLoweringPotential())).append(',')
                            .append(format(d.maximumLateralRecoveryGrade())).append(',')
                            .append(format(d.maximumBankContainmentDeficitWorldUnits())).append(',')
                            .append(format(d.normalizedExcavationBurden())).append(',')
                            .append(format(d.maximumLongitudinalGrade()));
                } else {
                    csv.append(",,,,,,");
                }
                csv.append('\n');
            }

            for (SkyIslandChannelTerminalFate fate :
                    SkyIslandChannelTerminalFatePlanner.plan(
                            specimen.descriptor(), plan.skeleton().geomorphicNetwork())) {
                terminalFateCsv.append(specimen.name()).append(',')
                        .append(specimen.descriptor().identity().islandKey()).append(',')
                        .append(fate.channelTerminalCellIndex()).append(',')
                        .append(fate.watershedTerminalCellIndex()).append(',')
                        .append(fate.kind()).append(',')
                        .append(fate.waterbodyKind().map(Enum::name).orElse("")).append(',')
                        .append(fate.watershedPath().stream()
                                .map(String::valueOf)
                                .collect(Collectors.joining(">")))
                        .append('\n');
            }

            for (SkyIslandBoundedHydraulicConvergenceDiagnostics d :
                    SkyIslandBoundedHydraulicConvergencePlanner.measure(specimen.descriptor())) {
                convergenceCsv.append(specimen.name()).append(',')
                        .append(specimen.descriptor().identity().islandKey()).append(',')
                        .append(d.startCellIndex()).append(',')
                        .append(d.endCellIndex()).append(',')
                        .append(d.coarseSampleCount()).append(',')
                        .append(d.mediumSampleCount()).append(',')
                        .append(d.fineSampleCount()).append(',')
                        .append(format(d.coarseStartHeadWorld())).append(',')
                        .append(format(d.mediumStartHeadWorld())).append(',')
                        .append(format(d.fineStartHeadWorld())).append(',')
                        .append(format(d.coarseEndHeadWorld())).append(',')
                        .append(format(d.mediumEndHeadWorld())).append(',')
                        .append(format(d.fineEndHeadWorld())).append(',')
                        .append(format(d.coarseObjectivePerLength())).append(',')
                        .append(format(d.mediumObjectivePerLength())).append(',')
                        .append(format(d.fineObjectivePerLength())).append(',')
                        .append(format(d.coarseMaximumLongitudinalGrade())).append(',')
                        .append(format(d.mediumMaximumLongitudinalGrade())).append(',')
                        .append(format(d.fineMaximumLongitudinalGrade())).append(',')
                        .append(format(d.coarseExcavationVolumeProxy())).append(',')
                        .append(format(d.mediumExcavationVolumeProxy())).append(',')
                        .append(format(d.fineExcavationVolumeProxy())).append(',')
                        .append(format(d.coarseMaximumLoweringWorld())).append(',')
                        .append(format(d.mediumMaximumLoweringWorld())).append(',')
                        .append(format(d.fineMaximumLoweringWorld())).append(',')
                        .append(d.coarseHeadDependentD2Pass()).append(',')
                        .append(d.mediumHeadDependentD2Pass()).append(',')
                        .append(d.fineHeadDependentD2Pass()).append('\n');
            }
        }

        Files.writeString(out.resolve("manifest.csv"), csv, StandardCharsets.UTF_8);
        Files.writeString(
                out.resolve("convergence-manifest.csv"),
                convergenceCsv,
                StandardCharsets.UTF_8);
        Files.writeString(
                out.resolve("terminal-fate-manifest.csv"),
                terminalFateCsv,
                StandardCharsets.UTF_8);
        Files.writeString(
                out.resolve("README.txt"),
                """
                F2C bounded hydraulic profile evidence.

                This is diagnostic/qualification evidence, not terrain authority.
                SOLVED means the convex head problem was solved; only SOLVED_QUALIFIED also passed
                the unchanged post-solve D2 evaluator. TRANSITION_DEFERRED is intentionally unsolved.
                terminal-fate-manifest.csv records topology-backed channel-terminal fate through the
                watershed graph; channel-terminal and retained-sink cell identity are not assumed.
                Do not tune D2 thresholds to preserve a particular specimen classification.
                """,
                StandardCharsets.UTF_8);
        System.out.println(out.resolve("manifest.csv").toAbsolutePath());
    }

    private static String csvField(String value) {
        return "\"" + value.replace("\"", "\"\"")
                .replace("\r", " ")
                .replace("\n", " ") + "\"";
    }

    private static String join(List<?> values) {
        return values.stream().map(Object::toString).collect(Collectors.joining("|"));
    }

    private static SkyIslandDescriptor descriptor(long province, long cluster, long island) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, province, cluster, island));
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.9f", value);
    }

    private record Specimen(String name, SkyIslandDescriptor descriptor) {}
}
