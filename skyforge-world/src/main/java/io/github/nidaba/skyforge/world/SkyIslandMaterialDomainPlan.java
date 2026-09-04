package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.List;
import java.util.Objects;

/** Deterministic AUTH-0032 mesoscale material-domain plan for one authored island interior. */
public record SkyIslandMaterialDomainPlan(
        SkyIslandDescriptor descriptor,
        int gridSize,
        int depthSamples,
        double horizontalSpacing,
        double depthSpacing,
        int mineralCarrierCount,
        int fabricCarrierCount,
        int activeHostCells,
        List<SkyIslandMaterialDomain> domains) {

    public SkyIslandMaterialDomainPlan {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        if (gridSize < 2 || depthSamples < 2) {
            throw new IllegalArgumentException("material-domain plan requires at least a 2 x 2 x 2 grid");
        }
        if (!Double.isFinite(horizontalSpacing) || horizontalSpacing <= 0.0
                || !Double.isFinite(depthSpacing) || depthSpacing <= 0.0) {
            throw new IllegalArgumentException("material-domain plan spacing must be positive and finite");
        }
        if (mineralCarrierCount < 1 || fabricCarrierCount < 1) {
            throw new IllegalArgumentException("material-domain carrier counts must be positive");
        }
        if (activeHostCells < 1 || activeHostCells > gridSize * depthSamples * gridSize) {
            throw new IllegalArgumentException("activeHostCells must fit inside planning lattice");
        }
        domains = List.copyOf(domains);
        domains.forEach(domain -> Objects.requireNonNull(domain, "material domain"));
    }

    public long domainCount(SkyIslandMaterialDomainKind kind) {
        return domains.stream().filter(domain -> domain.kind() == kind).count();
    }

    public int cellCount(SkyIslandMaterialDomainKind kind) {
        return domains.stream()
                .filter(domain -> domain.kind() == kind)
                .mapToInt(SkyIslandMaterialDomain::cellCount)
                .sum();
    }

    public int largestDomainCellCount(SkyIslandMaterialDomainKind kind) {
        return domains.stream()
                .filter(domain -> domain.kind() == kind)
                .mapToInt(SkyIslandMaterialDomain::cellCount)
                .max()
                .orElse(0);
    }
}
