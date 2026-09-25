package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** First terrain-mutating reset plan: only fully qualified and realizable fluvial reaches. */
public record SkyIslandQualifiedFluvialRealizationPlan(
        SkyIslandDescriptor descriptor,
        SkyIslandHydraulicChannelNetworkPlan hydraulicPlan,
        List<SkyIslandGeomorphicReachQualification> realizedQualifications,
        List<SkyIslandQualifiedFluvialDeferral> deferredQualifications,
        List<SkyIslandGeomorphicReachQualification> rejectedQualifications,
        SkyIslandQualifiedFluvialTerrainField terrainField) {

    public SkyIslandQualifiedFluvialRealizationPlan {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        hydraulicPlan = Objects.requireNonNull(hydraulicPlan, "hydraulicPlan");
        realizedQualifications = List.copyOf(realizedQualifications);
        deferredQualifications = List.copyOf(deferredQualifications);
        rejectedQualifications = List.copyOf(rejectedQualifications);
        terrainField = Objects.requireNonNull(terrainField, "terrainField");

        realizedQualifications.forEach(q -> Objects.requireNonNull(q, "realized qualification"));
        deferredQualifications.forEach(d -> Objects.requireNonNull(d, "deferred qualification"));
        rejectedQualifications.forEach(q -> Objects.requireNonNull(q, "rejected qualification"));

        if (!descriptor.equals(hydraulicPlan.descriptor())) {
            throw new IllegalArgumentException(
                    "hydraulic plan descriptor must match realization descriptor");
        }
        if (terrainField.acceptedReaches().size() != realizedQualifications.size()) {
            throw new IllegalArgumentException(
                    "terrain field must contain exactly the realized reaches");
        }
        if (realizedQualifications.stream().anyMatch(q -> !q.accepted())) {
            throw new IllegalArgumentException(
                    "realizedQualifications contains a rejected reach");
        }
        if (rejectedQualifications.stream().anyMatch(SkyIslandGeomorphicReachQualification::accepted)) {
            throw new IllegalArgumentException(
                    "rejectedQualifications contains an accepted reach");
        }

        int classified =
                realizedQualifications.size()
                        + deferredQualifications.size()
                        + rejectedQualifications.size();
        if (classified != hydraulicPlan.reaches().size()) {
            throw new IllegalArgumentException(
                    "every hydraulic reach must be realized, deferred, or rejected exactly once");
        }

        Set<Long> identities = new HashSet<>();
        for (SkyIslandGeomorphicReachQualification qualification : realizedQualifications) {
            SkyIslandHydraulicReachGeometry reach = qualification.diagnostics().hydraulicReach();
            requireUnique(identities, reach);
            if (!containsReach(terrainField.acceptedReaches(), reach)) {
                throw new IllegalArgumentException(
                        "terrain field is missing a realized qualified reach");
            }
        }
        for (SkyIslandQualifiedFluvialDeferral deferral : deferredQualifications) {
            SkyIslandHydraulicReachGeometry reach =
                    deferral.qualification().diagnostics().hydraulicReach();
            requireUnique(identities, reach);
            if (containsReach(terrainField.acceptedReaches(), reach)) {
                throw new IllegalArgumentException(
                        "deferred reach must not receive terrain authority");
            }
        }
        for (SkyIslandGeomorphicReachQualification qualification : rejectedQualifications) {
            requireUnique(identities, qualification.diagnostics().hydraulicReach());
        }
    }

    private static void requireUnique(
            Set<Long> identities,
            SkyIslandHydraulicReachGeometry reach) {
        if (!identities.add(reachIdentity(reach))) {
            throw new IllegalArgumentException("reach classified more than once");
        }
    }

    private static boolean containsReach(
            List<SkyIslandHydraulicReachGeometry> reaches,
            SkyIslandHydraulicReachGeometry candidate) {
        long identity = reachIdentity(candidate);
        return reaches.stream().anyMatch(reach -> reachIdentity(reach) == identity);
    }

    private static long reachIdentity(SkyIslandHydraulicReachGeometry reach) {
        SkyIslandSemanticChannelReach semantic =
                reach.geomorphicRoute().semanticReach();
        return ((long) semantic.startCellIndex() << 32)
                ^ Integer.toUnsignedLong(semantic.endCellIndex());
    }
}
