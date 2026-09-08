package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * AUTH-0099 producer for threshold-free cave-system site capability evidence.
 *
 * <p>The producer composes accepted cave/geology/material causes. It adds no cave topology,
 * exposure threshold, resource eligibility, structure role, or backend placement policy.
 */
public final class SkyIslandCaveSiteCapabilityProfiler {

    public SkyIslandCaveSiteCapabilityProfile profile(SkyIslandDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");

        SkyIslandCaveExposurePlan exposure = SkyIslandCaveExposurePlanner.plan(descriptor);
        SkyIslandCaveGeometryPlan geometry = exposure.geometry();
        SkyIslandCaveSystemPlan topology = geometry.topology();
        SkyIslandMaterialFamilyPlan materialPlan =
                SkyIslandMaterialFamilyPlanner.plan(descriptor);
        SkyIslandGeologyFieldSet geology = SkyIslandGeologyFieldSet.create(descriptor);

        Map<Integer, SkyIslandCaveSystemGeometry> geometryBySystem = new HashMap<>();
        for (SkyIslandCaveSystemGeometry system : geometry.systems()) {
            geometryBySystem.put(system.systemId(), system);
        }
        Map<Integer, SkyIslandCaveExposureIntent> exposureBySystem = new HashMap<>();
        for (SkyIslandCaveExposureIntent intent : exposure.intents()) {
            exposureBySystem.put(intent.systemId(), intent);
        }

        ArrayList<SkyIslandCaveSiteCapabilitySystem> systems =
                new ArrayList<>(topology.systems().size());
        for (SkyIslandCaveSystem sourceSystem : topology.systems()) {
            SkyIslandCaveSystemGeometry sourceGeometry =
                    geometryBySystem.get(sourceSystem.systemId());
            if (sourceGeometry == null) {
                throw new IllegalStateException(
                        "AUTH-0099 cave geometry lost exact topology system");
            }

            ArrayList<SkyIslandCaveSiteNodeEvidence> nodes =
                    new ArrayList<>(sourceSystem.nodes().size());
            for (SkyIslandCaveNode node : sourceSystem.nodes()) {
                SkyIslandGeologySample geologySample = geology.sample(node.position());
                SkyIslandMaterialFamilyCell nearestHost =
                        nearestHostCell(descriptor, materialPlan, node.position());
                nodes.add(new SkyIslandCaveSiteNodeEvidence(
                        node,
                        geologySample,
                        nearestHost,
                        normalizedDistance(
                                descriptor,
                                node.position(),
                                nearestHost.position())));
            }

            systems.add(new SkyIslandCaveSiteCapabilitySystem(
                    sourceSystem,
                    sourceGeometry,
                    Optional.ofNullable(exposureBySystem.get(sourceSystem.systemId())),
                    nodes,
                    descriptor.nominalRadius()));
        }

        systems.sort(Comparator.comparingInt(SkyIslandCaveSiteCapabilitySystem::systemId));
        return new SkyIslandCaveSiteCapabilityProfile(
                descriptor,
                topology,
                geometry,
                exposure,
                materialPlan,
                systems);
    }

    private static SkyIslandMaterialFamilyCell nearestHostCell(
            SkyIslandDescriptor descriptor,
            SkyIslandMaterialFamilyPlan materialPlan,
            SkyIslandSubsurfacePosition position) {
        SkyIslandMaterialFamilyCell best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (SkyIslandMaterialFamilyCell candidate : materialPlan.cells()) {
            double distance = normalizedDistance(
                    descriptor, position, candidate.position());
            int comparison = Double.compare(distance, bestDistance);
            if (best == null
                    || comparison < 0
                    || (comparison == 0 && candidate.index() < best.index())) {
                best = candidate;
                bestDistance = distance;
            }
        }
        if (best == null) {
            throw new IllegalStateException(
                    "AUTH-0099 requires at least one accepted AUTH-0033 active host cell");
        }
        return best;
    }

    private static double normalizedDistance(
            SkyIslandDescriptor descriptor,
            SkyIslandSubsurfacePosition first,
            SkyIslandSubsurfacePosition second) {
        double radius = descriptor.nominalRadius();
        double dx = (second.x() - first.x()) / radius;
        double dz = (second.z() - first.z()) / radius;
        double dd = second.depthFraction() - first.depthFraction();
        return Math.sqrt(dx * dx + dz * dz + dd * dd);
    }
}
