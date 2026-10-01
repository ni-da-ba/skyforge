package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate3;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * AUTH-0048 backend-neutral exact multi-island ownership resolver.
 *
 * <p>AUTH-0046 associated volume bounds are used only as a conservative culling stage. Exact
 * physical membership is determined by the associated compiled upper/underside column at the
 * world-derived island-local X/Z. Native authored ownership then follows the current authoritative
 * semantic interiority domain at the recovered semantic point.
 *
 * <p>The resolver never selects by nearest center, catalog order, seed similarity, backend
 * encounter order, or material identity. Multiple true native owners remain explicitly ambiguous.
 */
public final class SkyIslandAuthoredRealizationOwnershipResolver {
    private final SkyIslandAuthoredRealizationCatalog catalog;
    private final List<Entry> entries;

    public SkyIslandAuthoredRealizationOwnershipResolver(
            SkyIslandAuthoredRealizationCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        ArrayList<Entry> built = new ArrayList<>(catalog.size());

        for (SkyIslandAuthoredRealizationAssociation association :
                catalog.associations()) {
            SkyIslandCompiledVolumeColumnField columns =
                    new SkyIslandCompiledVolumeColumnField(
                            association.realizedVolume().compiledVolume());
            built.add(
                    new Entry(
                            association,
                            new SkyIslandSemanticDepthRealizationTransform(columns),
                            SkyIslandSemanticFieldSet.create(
                                            association.authoredDescriptor())
                                    .interiority()));
        }
        this.entries = List.copyOf(built);
    }

    public SkyIslandAuthoredRealizationCatalog catalog() {
        return catalog;
    }

    public SkyIslandAuthoredRealizationOwnershipSelection resolve(
            Coordinate3 worldPosition) {
        Objects.requireNonNull(worldPosition, "worldPosition");
        ArrayList<SkyIslandAuthoredRealizationOwnershipCandidate> candidates =
                new ArrayList<>();

        for (Entry entry : entries) {
            SkyIslandAuthoredRealizationAssociation association =
                    entry.association();
            SkyIslandWorldVolume volume = association.realizedVolume();
            if (!volume.bounds()
                    .contains(
                            worldPosition.x(),
                            worldPosition.y(),
                            worldPosition.z())) {
                continue;
            }

            var descriptor = volume.compiledVolume().descriptor();
            SkyIslandLocalPosition local =
                    new SkyIslandLocalPosition(
                            worldPosition.x() - descriptor.centerX(),
                            worldPosition.z() - descriptor.centerZ());
            SkyIslandRealizedSubsurfacePosition realized =
                    new SkyIslandRealizedSubsurfacePosition(
                            local, worldPosition.y());
            Optional<SkyIslandSubsurfacePosition> semantic =
                    entry.transform().toSemantic(realized);
            boolean authoredOwned =
                    semantic.isPresent()
                            && entry.interiority()
                                            .sample(
                                                    semantic.orElseThrow()
                                                            .surfacePosition())
                                    > 0.0;

            candidates.add(
                    new SkyIslandAuthoredRealizationOwnershipCandidate(
                            association,
                            realized,
                            semantic.orElse(null),
                            authoredOwned));
        }

        return new SkyIslandAuthoredRealizationOwnershipSelection(
                worldPosition, candidates);
    }

    private record Entry(
            SkyIslandAuthoredRealizationAssociation association,
            SkyIslandSemanticDepthRealizationTransform transform,
            SkyIslandSemanticField interiority) {}
}
