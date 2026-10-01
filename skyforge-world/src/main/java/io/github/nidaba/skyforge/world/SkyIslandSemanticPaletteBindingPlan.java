package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Deterministic AUTH-0038 palette-binding coherence plan on the accepted planning lattice. */
public record SkyIslandSemanticPaletteBindingPlan(
        SkyIslandDescriptor descriptor,
        int gridSize,
        int depthSamples,
        double horizontalSpacing,
        double depthSpacing,
        List<SkyIslandSemanticPaletteBindingDomain> domains) {

    public SkyIslandSemanticPaletteBindingPlan {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        if (gridSize < 2 || depthSamples < 2) {
            throw new IllegalArgumentException("binding plan requires at least a 2 x 2 x 2 grid");
        }
        if (!Double.isFinite(horizontalSpacing)
                || horizontalSpacing <= 0.0
                || !Double.isFinite(depthSpacing)
                || depthSpacing <= 0.0) {
            throw new IllegalArgumentException("binding-plan spacing must be positive and finite");
        }
        domains = List.copyOf(domains);
        Set<SkyIslandSemanticPaletteBindingKey> keys = new HashSet<>();
        Set<Long> cellRoles = new HashSet<>();
        for (SkyIslandSemanticPaletteBindingDomain domain : domains) {
            Objects.requireNonNull(domain, "binding domain");
            if (!domain.key().islandIdentity().equals(descriptor.identity())) {
                throw new IllegalArgumentException(
                        "binding-domain key must retain parent island identity");
            }
            if (!keys.add(domain.key())) {
                throw new IllegalArgumentException("binding-domain keys must be unique");
            }
            for (SkyIslandSemanticPaletteBindingCell cell : domain.cells()) {
                long cellRole = (((long) cell.index()) << 8)
                        | (cell.candidate().role().ordinal() & 0xFFL);
                if (!cellRoles.add(cellRole)) {
                    throw new IllegalArgumentException(
                            "one planning-cell role cannot belong to multiple binding domains");
                }
            }
        }
    }

    public long domainCount(SkyIslandSemanticMaterialPaletteRole role) {
        return domains.stream().filter(domain -> domain.key().role() == role).count();
    }

    public long conditionedDomainsCrossingAssemblages() {
        return domains.stream()
                .filter(domain -> domain.key().domainKind()
                        == SkyIslandSemanticPaletteBindingDomainKind.CONDITIONED_REGION)
                .filter(domain -> domain.assemblageCount() > 1)
                .count();
    }
}
