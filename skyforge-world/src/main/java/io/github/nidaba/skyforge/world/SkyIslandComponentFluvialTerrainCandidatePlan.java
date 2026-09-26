package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * F4A backend-neutral terrain-delta candidate derived only from complete F3E-qualified components.
 *
 * <p>This remains evidence. The field is not Minecraft authority.
 */
public record SkyIslandComponentFluvialTerrainCandidatePlan(
        SkyIslandDescriptor descriptor,
        SkyIslandHydraulicNetworkAssemblyPlan assemblyPlan,
        List<SkyIslandHydraulicTerminalComponent> realizedComponents,
        List<SkyIslandComponentFluvialTerrainDeferral> deferredQualifiedComponents,
        List<SkyIslandGeomorphicReachQualification> postRealizationQualifications,
        SkyIslandQualifiedFluvialTerrainField terrainField) {

    public SkyIslandComponentFluvialTerrainCandidatePlan {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        assemblyPlan = Objects.requireNonNull(assemblyPlan, "assemblyPlan");
        realizedComponents = List.copyOf(realizedComponents);
        deferredQualifiedComponents = List.copyOf(deferredQualifiedComponents);
        postRealizationQualifications = List.copyOf(postRealizationQualifications);
        terrainField = Objects.requireNonNull(terrainField, "terrainField");

        if (!descriptor.equals(assemblyPlan.descriptor())) {
            throw new IllegalArgumentException(
                    "F4A assembly descriptor must match candidate descriptor");
        }
        realizedComponents.forEach(value -> Objects.requireNonNull(value, "realized component"));
        deferredQualifiedComponents.forEach(value ->
                Objects.requireNonNull(value, "deferred qualified component"));
        postRealizationQualifications.forEach(value ->
                Objects.requireNonNull(value, "post-realization qualification"));

        if (realizedComponents.stream()
                .anyMatch(component ->
                        component.status() != SkyIslandHydraulicAssemblyStatus.QUALIFIED)) {
            throw new IllegalArgumentException(
                    "only F3E-qualified components may receive F4A candidate terrain");
        }
        if (postRealizationQualifications.stream()
                .anyMatch(qualification -> !qualification.accepted())) {
            throw new IllegalArgumentException(
                    "F4A may publish only post-realization D2-accepted reaches");
        }
        if (terrainField.acceptedReaches().size() != postRealizationQualifications.size()) {
            throw new IllegalArgumentException(
                    "F4A terrain field must contain exactly the post-qualified reaches");
        }

        Set<Integer> classifiedQualifiedTerminals = new HashSet<>();
        for (SkyIslandHydraulicTerminalComponent component : realizedComponents) {
            if (!classifiedQualifiedTerminals.add(
                    component.terminalFate().channelTerminalCellIndex())) {
                throw new IllegalArgumentException(
                        "F3E-qualified terminal component classified more than once");
            }
        }
        for (SkyIslandComponentFluvialTerrainDeferral deferral :
                deferredQualifiedComponents) {
            int terminal = deferral.component().terminalFate().channelTerminalCellIndex();
            if (!classifiedQualifiedTerminals.add(terminal)) {
                throw new IllegalArgumentException(
                        "F3E-qualified terminal component classified more than once");
            }
        }

        long expectedQualified =
                assemblyPlan.terminalComponents().stream()
                        .filter(component ->
                                component.status()
                                        == SkyIslandHydraulicAssemblyStatus.QUALIFIED)
                        .count();
        if (classifiedQualifiedTerminals.size() != expectedQualified) {
            throw new IllegalArgumentException(
                    "every F3E-qualified terminal component must be realized or explicitly deferred");
        }

        Set<Long> realizedReachIdentities = new HashSet<>();
        for (SkyIslandHydraulicTerminalComponent component : realizedComponents) {
            for (SkyIslandHydraulicReachAssembly reach : component.reaches()) {
                if (!realizedReachIdentities.add(reach.identity())) {
                    throw new IllegalArgumentException(
                            "realized F3E reach appears in more than one component");
                }
            }
        }

        Set<Long> terrainReachIdentities = new HashSet<>();
        for (SkyIslandHydraulicReachGeometry reach : terrainField.acceptedReaches()) {
            if (!terrainReachIdentities.add(reachIdentity(reach))) {
                throw new IllegalArgumentException(
                        "terrain field contains duplicate realized reach identity");
            }
        }
        if (!terrainReachIdentities.equals(realizedReachIdentities)) {
            throw new IllegalArgumentException(
                    "terrain field reach identities must equal realized F3E reach identities");
        }

        Set<Long> qualificationIdentities = new HashSet<>();
        for (SkyIslandGeomorphicReachQualification qualification :
                postRealizationQualifications) {
            if (!qualificationIdentities.add(
                    reachIdentity(qualification.diagnostics().hydraulicReach()))) {
                throw new IllegalArgumentException(
                        "post-realization qualification repeated a reach identity");
            }
        }
        if (!qualificationIdentities.equals(realizedReachIdentities)) {
            throw new IllegalArgumentException(
                    "post-realization qualifications must equal realized F3E reach identities");
        }
    }

    private static long reachIdentity(SkyIslandHydraulicReachGeometry reach) {
        SkyIslandSemanticChannelReach semantic =
                reach.geomorphicRoute().semanticReach();
        return ((long) semantic.startCellIndex() << 32)
                ^ Integer.toUnsignedLong(semantic.endCellIndex());
    }
}
