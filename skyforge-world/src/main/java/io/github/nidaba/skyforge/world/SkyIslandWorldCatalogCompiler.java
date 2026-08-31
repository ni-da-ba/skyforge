package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviderRegistry;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoGroupPlan;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoPlan;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupMemberPlan;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandMorphologySpecCompiler;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Compiles an accepted archipelago hierarchy into a backend-queryable bounded world catalog. */
public final class SkyIslandWorldCatalogCompiler {
    private final SkyIslandMorphologySpecCompiler morphologyCompiler =
            new SkyIslandMorphologySpecCompiler();

    /**
     * Compiles every island independently and wraps it in conservative backend query bounds.
     *
     * <p>Horizontal bounds use the accepted explicit member reservation. Vertical bounds are
     * supplied explicitly because arbitrary morphology providers are not required to obey built-in
     * descriptor height semantics. No provider or morphology-family switch occurs in this layer.
     */
    public SkyIslandWorldCatalog compile(
            SkyIslandArchipelagoPlan plan,
            SkyIslandMorphologyProviderRegistry registry,
            SkyIslandWorldVerticalReservation verticalReservation) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(verticalReservation, "verticalReservation");
        ArrayList<SkyIslandWorldVolume> volumes = new ArrayList<>(plan.totalMemberCount());

        for (SkyIslandArchipelagoGroupPlan group : plan.groups()) {
            List<CompiledSkyIslandVolume> compiledGroup =
                    morphologyCompiler.compile(group.groupPlan(), registry);
            for (int memberOrdinal = 0; memberOrdinal < compiledGroup.size(); memberOrdinal++) {
                SkyIslandGroupMemberPlan member = group.groupPlan().members().get(memberOrdinal);
                CompiledSkyIslandVolume compiled = compiledGroup.get(memberOrdinal);
                var descriptor = member.descriptor();
                double radius = member.reservedHorizontalRadius();
                WorldBounds bounds = new WorldBounds(
                        descriptor.centerX() - radius,
                        descriptor.centerX() + radius,
                        descriptor.suspensionElevation() - verticalReservation.belowSuspension(),
                        descriptor.suspensionElevation() + verticalReservation.aboveSuspension(),
                        descriptor.centerZ() - radius,
                        descriptor.centerZ() + radius);
                SkyIslandWorldVolumeId id = new SkyIslandWorldVolumeId(
                        plan.rootSeed(),
                        group.identifier(),
                        group.ordinal(),
                        memberOrdinal,
                        descriptor.seed());
                volumes.add(new SkyIslandWorldVolume(id, bounds, compiled));
            }
        }
        return new SkyIslandWorldCatalog(plan.rootSeed(), volumes);
    }
}
