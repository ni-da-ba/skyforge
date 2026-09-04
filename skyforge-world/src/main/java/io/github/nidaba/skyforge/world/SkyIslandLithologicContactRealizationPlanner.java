package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts AUTH-0034 discrete semantic contacts into finite-width continuous transition patches.
 *
 * <p>Patch width and sharpness remain subordinate to the accepted contact contrasts plus local
 * geology, cave exposure, and semantic depth. No named material or backend realization appears
 * here.
 */
public final class SkyIslandLithologicContactRealizationPlanner {
    public static final double MIN_HALF_WIDTH = 0.022;
    public static final double MAX_HALF_WIDTH = 0.135;

    private SkyIslandLithologicContactRealizationPlanner() {}

    public static SkyIslandLithologicContactRealizationPlan plan(
            SkyIslandDescriptor descriptor) {
        SkyIslandLithologicAssemblagePlan assemblagePlan =
                SkyIslandLithologicAssemblagePlanner.plan(descriptor);
        SkyIslandGeologyFieldSet geology = SkyIslandGeologyFieldSet.create(descriptor);
        SkyIslandSubsurfaceMaterialFieldSet material =
                SkyIslandSubsurfaceMaterialFieldSet.create(descriptor);
        SkyIslandExteriorConnectedCaveVolumeField cave = material.caveField();

        int gridSize = assemblagePlan.gridSize();
        int depthSamples = assemblagePlan.depthSamples();
        int total = gridSize * depthSamples * gridSize;
        double radius = descriptor.nominalRadius();
        double horizontalHalfSpanNormalized =
                0.5 * assemblagePlan.horizontalSpacing() / radius;
        double depthHalfSpan = 0.5 * assemblagePlan.depthSpacing();

        SkyIslandLithologicAssemblageCell[] byIndex =
                new SkyIslandLithologicAssemblageCell[total];
        for (SkyIslandLithologicAssemblageCell cell : assemblagePlan.cells()) {
            byIndex[cell.index()] = cell;
        }

        Map<Long, SkyIslandLithologicContact> contactByPair = new HashMap<>();
        Map<Integer, List<SkyIslandLithologicContactPatch>> patchesByContact =
                new HashMap<>();
        for (SkyIslandLithologicContact contact : assemblagePlan.contacts()) {
            contactByPair.put(
                    pair(contact.firstAssemblageId(), contact.secondAssemblageId()),
                    contact);
            patchesByContact.put(contact.contactId(), new ArrayList<>());
        }

        int[][] offsets = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
        for (SkyIslandLithologicAssemblageCell cell : assemblagePlan.cells()) {
            for (int[] offset : offsets) {
                int nx = cell.xIndex() + offset[0];
                int nd = cell.depthIndex() + offset[1];
                int nz = cell.zIndex() + offset[2];
                if (nx < 0
                        || nd < 0
                        || nz < 0
                        || nx >= gridSize
                        || nd >= depthSamples
                        || nz >= gridSize) {
                    continue;
                }
                int neighborIndex = index(nx, nd, nz, gridSize, depthSamples);
                SkyIslandLithologicAssemblageCell neighbor = byIndex[neighborIndex];
                if (neighbor == null || neighbor.assemblageId() == cell.assemblageId()) {
                    continue;
                }

                SkyIslandLithologicContact contact = contactByPair.get(
                        pair(cell.assemblageId(), neighbor.assemblageId()));
                if (contact == null) {
                    throw new IllegalStateException(
                            "AUTH-0034 adjacency is missing its contact");
                }

                SkyIslandSubsurfacePosition center = new SkyIslandSubsurfacePosition(
                        0.5 * (cell.position().x() + neighbor.position().x()),
                        0.5 * (cell.position().z() + neighbor.position().z()),
                        0.5 * (cell.position().depthFraction()
                                + neighbor.position().depthFraction()));
                SkyIslandLithologicContactAxis axis = offset[0] != 0
                        ? SkyIslandLithologicContactAxis.X
                        : offset[1] != 0
                                ? SkyIslandLithologicContactAxis.DEPTH
                                : SkyIslandLithologicContactAxis.Z;

                SkyIslandGeologySample geologic = geology.sample(center);
                SkyIslandExteriorConnectedCaveVolumeSample caveSample =
                        cave.sample(center);
                double caveExposure = caveExposure(caveSample.signedClearance());
                double primaryContrast = primaryContrast(contact);
                double midDepth = clamp01(
                        1.0 - Math.abs(center.depthFraction() - 0.52) / 0.52);

                double width = baseHalfWidth(contact.kind())
                        * (1.08
                                - 0.30 * primaryContrast
                                + 0.22 * geologic.fractureIntensity()
                                + 0.17 * caveExposure
                                + 0.08 * midDepth);
                width = clamp(width, MIN_HALF_WIDTH, MAX_HALF_WIDTH);

                double sharpness = clamp01(
                        0.48 * primaryContrast
                                + 0.30 * (1.0 - width / MAX_HALF_WIDTH)
                                + 0.14 * geologic.bulkCompetence()
                                + 0.08 * (1.0 - caveExposure));
                double structuralInfluence = clamp01(
                        0.62 * geologic.fractureIntensity()
                                + 0.23 * (1.0 - geologic.bulkCompetence())
                                + 0.15 * primaryContrast);

                patchesByContact.get(contact.contactId()).add(
                        new SkyIslandLithologicContactPatch(
                                contact.contactId(),
                                contact.firstAssemblageId(),
                                contact.secondAssemblageId(),
                                axis,
                                center,
                                cell.assemblageId() == contact.firstAssemblageId(),
                                horizontalHalfSpanNormalized,
                                depthHalfSpan,
                                width,
                                sharpness,
                                structuralInfluence,
                                caveExposure));
            }
        }

        List<SkyIslandLithologicContactRealization> realizations =
                new ArrayList<>(assemblagePlan.contacts().size());
        for (SkyIslandLithologicContact contact : assemblagePlan.contacts()) {
            List<SkyIslandLithologicContactPatch> patches =
                    patchesByContact.get(contact.contactId());
            if (patches == null || patches.isEmpty()) {
                throw new IllegalStateException(
                        "every AUTH-0034 contact must retain at least one real face patch");
            }
            realizations.add(new SkyIslandLithologicContactRealization(contact, patches));
        }

        return new SkyIslandLithologicContactRealizationPlan(
                descriptor, assemblagePlan, realizations);
    }

    public static double primaryContrast(SkyIslandLithologicContact contact) {
        return switch (contact.kind()) {
            case GRADATIONAL_CONTACT -> Math.max(
                    Math.max(contact.hostFabricContrast(), contact.alterationContrast()),
                    Math.max(contact.hydrologicContrast(), contact.mineralizationContrast()));
            case HOST_FABRIC_CONTACT -> contact.hostFabricContrast();
            case ALTERATION_FRONT -> contact.alterationContrast();
            case HYDROLOGIC_FRONT -> contact.hydrologicContrast();
            case MINERALIZATION_FRONT -> contact.mineralizationContrast();
        };
    }

    private static double baseHalfWidth(SkyIslandLithologicContactKind kind) {
        return switch (kind) {
            case GRADATIONAL_CONTACT -> 0.092;
            case HOST_FABRIC_CONTACT -> 0.043;
            case ALTERATION_FRONT -> 0.071;
            case HYDROLOGIC_FRONT -> 0.083;
            case MINERALIZATION_FRONT -> 0.058;
        };
    }

    private static double caveExposure(double signedClearance) {
        if (signedClearance > 0.0) {
            return 1.0;
        }
        return clamp01(1.0 - (-signedClearance) / 0.60);
    }

    private static int index(
            int x, int depth, int z, int gridSize, int depthSamples) {
        return (z * depthSamples + depth) * gridSize + x;
    }

    private static long pair(int first, int second) {
        int low = Math.min(first, second);
        int high = Math.max(first, second);
        return ((long) low << 32) | (high & 0xFFFFFFFFL);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double clamp01(double value) {
        return clamp(value, 0.0, 1.0);
    }
}
