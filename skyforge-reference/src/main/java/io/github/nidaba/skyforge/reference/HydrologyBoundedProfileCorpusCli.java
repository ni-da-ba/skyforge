package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandBoundedHydraulicConvergenceDiagnostics;
import io.github.nidaba.skyforge.world.SkyIslandBoundedHydraulicConvergencePlanner;
import io.github.nidaba.skyforge.world.SkyIslandBoundedHydraulicProfilePlan;
import io.github.nidaba.skyforge.world.SkyIslandBoundedHydraulicProfilePlanner;
import io.github.nidaba.skyforge.world.SkyIslandBoundedHydraulicReachOutcome;
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
                new Specimen("control-241", descriptor(8L, 81L, 241L)),
                new Specimen("confluence-632", descriptor(8L, 81L, 632L)),
                new Specimen("stress-811", descriptor(8L, 81L, 811L)),
                new Specimen("retained-83", descriptor(6L, 61L, 83L)),
                new Specimen("control-77", descriptor(6L, 61L, 77L)),
                new Specimen("control-118", descriptor(6L, 61L, 118L)),
                new Specimen("stress-512", descriptor(6L, 61L, 512L)));

        StringBuilder csv = new StringBuilder(
                "specimen,islandKey,startCell,endCell,status,deferrals,solverStatus,"
                        + "objective,primalResidual,stationarityResidual,dualResidual,"
                        + "complementarityResidual,d2Accepted,violations,maxLoweringPotential,"
                        + "maxLateralRecoveryGrade,maxContainmentDeficitWorld,"
                        + "normalizedExcavationBurden,maxLongitudinalGradeWorld\n");

        StringBuilder convergenceCsv = new StringBuilder(
                "specimen,islandKey,startCell,endCell,nativeSamples,refinedSamples,"
                        + "nativeStartHeadWorld,refinedStartHeadWorld,nativeEndHeadWorld,"
                        + "refinedEndHeadWorld,nativeObjectivePerLength,refinedObjectivePerLength,"
                        + "nativeMaxGrade,refinedMaxGrade,nativeExcavation,refinedExcavation,"
                        + "nativeMaxLoweringWorld,refinedMaxLoweringWorld,"
                        + "nativeHeadDependentD2Pass,refinedHeadDependentD2Pass\n");

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
                        .append(join(outcome.deferralReasons())).append(',');

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

            for (SkyIslandBoundedHydraulicConvergenceDiagnostics d :
                    SkyIslandBoundedHydraulicConvergencePlanner.measure(specimen.descriptor())) {
                convergenceCsv.append(specimen.name()).append(',')
                        .append(specimen.descriptor().identity().islandKey()).append(',')
                        .append(d.startCellIndex()).append(',')
                        .append(d.endCellIndex()).append(',')
                        .append(d.nativeSampleCount()).append(',')
                        .append(d.refinedSampleCount()).append(',')
                        .append(format(d.nativeStartHeadWorld())).append(',')
                        .append(format(d.refinedStartHeadWorld())).append(',')
                        .append(format(d.nativeEndHeadWorld())).append(',')
                        .append(format(d.refinedEndHeadWorld())).append(',')
                        .append(format(d.nativeObjectivePerLength())).append(',')
                        .append(format(d.refinedObjectivePerLength())).append(',')
                        .append(format(d.nativeMaximumLongitudinalGrade())).append(',')
                        .append(format(d.refinedMaximumLongitudinalGrade())).append(',')
                        .append(format(d.nativeExcavationVolumeProxy())).append(',')
                        .append(format(d.refinedExcavationVolumeProxy())).append(',')
                        .append(format(d.nativeMaximumLoweringWorld())).append(',')
                        .append(format(d.refinedMaximumLoweringWorld())).append(',')
                        .append(d.nativeHeadDependentD2Pass()).append(',')
                        .append(d.refinedHeadDependentD2Pass()).append('\n');
            }
        }

        Files.writeString(out.resolve("manifest.csv"), csv, StandardCharsets.UTF_8);
        Files.writeString(
                out.resolve("convergence-manifest.csv"),
                convergenceCsv,
                StandardCharsets.UTF_8);
        Files.writeString(
                out.resolve("README.txt"),
                """
                F2C bounded hydraulic profile evidence.

                This is diagnostic/qualification evidence, not terrain authority.
                SOLVED means the convex head problem was solved; only SOLVED_QUALIFIED also passed
                the unchanged post-solve D2 evaluator. TRANSITION_DEFERRED is intentionally unsolved.
                Do not tune D2 thresholds to preserve a particular specimen classification.
                """,
                StandardCharsets.UTF_8);
        System.out.println(out.resolve("manifest.csv").toAbsolutePath());
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
