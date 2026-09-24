package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate2;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationAssociation;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationCatalog;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationSurfaceEcologyResolver;
import io.github.nidaba.skyforge.world.SkyIslandFluvialSurfaceZone;
import io.github.nidaba.skyforge.world.SkyIslandFluvialTerrainField;
import io.github.nidaba.skyforge.world.SkyIslandLocalPosition;
import io.github.nidaba.skyforge.world.SkyIslandSurfaceSiteCapabilityProfiler;
import java.util.HashSet;
import java.util.List;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biomes;
import org.junit.jupiter.api.Test;

final class SkyforgeProductionEcologyResolverTest {
    @Test
    void canonicalCoordinatorSurfaceSamplesPopulateOnlyExactAuthoredSurfaceAndStillDifferentiate() {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.single();
        var association = SkyIslandAuthoredRealizationAssociation.of(fixture.descriptor(), fixture.volume());
        var resolver = new SkyforgeProductionEcologyResolver(association);
        var terrain = new SkyforgeNeoForge1211ChunkAdapter(
                fixture.catalog(),
                io.github.nidaba.skyforge.world.SkyIslandTerrainProfile.reference(),
                new SkyforgeMinecraftBlockPalette(),
                java.util.Map.of(fixture.volume().id(), fixture.descriptor()));

        var carriers = new HashSet<net.minecraft.resources.ResourceKey<net.minecraft.world.level.biome.Biome>>();
        int supportedChunks = 0;
        int omittedEdgeChunks = 0;
        for (long chunkKey : requiredChunkKeys(fixture.volume().bounds())) {
            ChunkPos chunk = new ChunkPos(chunkKey);
            if (!resolver.supportsCoordinatorSurface(terrain, chunk)) {
                omittedEdgeChunks++;
                continue;
            }
            for (var probe : SkyforgeNativeSurfacePopulationCoordinator.surfaceProbeOrder()) {
                int x = chunk.getMinBlockX() + probe.localX();
                int z = chunk.getMinBlockZ() + probe.localZ();
                var range = terrain.integerSolidRange(fixture.volume().id(), x, z);
                if (range.isEmpty()) {
                    continue;
                }
                carriers.add(resolver.resolve(
                        fixture.volume().id(), x, range.orElseThrow().maximumY() + 1, z));
                supportedChunks++;
                break;
            }
        }
        assertTrue(supportedChunks > 0);
        assertTrue(omittedEdgeChunks > 0, "physical-only edge chunks must remain ecology-unclaimed");
        assertTrue(carriers.size() >= 2, "canonical authored surface must expose ecology differentiation");
    }

    @Test
    void fineAuthoredChannelUsesNativeRiverCarrier() {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.single();
        var association = SkyIslandAuthoredRealizationAssociation.of(fixture.descriptor(), fixture.volume());
        var resolver = new SkyforgeProductionEcologyResolver(association);
        var fluvial = SkyIslandFluvialTerrainField.create(fixture.descriptor());
        assertTrue(!fluvial.reaches().isEmpty());

        var reach = fluvial.reaches().getFirst();
        SkyIslandLocalPosition local =
                reach.path().points().get(reach.path().points().size() / 2);
        var realized = fixture.volume().compiledVolume().descriptor();
        int worldX = Math.toIntExact(Math.round(realized.centerX() + local.x()));
        int worldZ = Math.toIntExact(Math.round(realized.centerZ() + local.z()));

        assertEquals(
                SkyIslandFluvialSurfaceZone.WET_CHANNEL,
                fluvial.surfaceZone(new SkyIslandLocalPosition(
                        worldX - realized.centerX(),
                        worldZ - realized.centerZ())));
        assertEquals(
                Biomes.RIVER,
                resolver.resolveAuthoredSurface(fixture.volume().id(), worldX, worldZ).orElseThrow());
    }

    @Test
    void retainedWaterRemainsNativeAquaticCarrierOutsideFineChannel() {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.dr70Review();
        var association = SkyIslandAuthoredRealizationAssociation.of(fixture.descriptor(), fixture.volume());
        var resolver = new SkyforgeProductionEcologyResolver(association);
        var profile = new SkyIslandSurfaceSiteCapabilityProfiler().profile(association);
        var rasterizer = new SkyforgeAuthoredSurfaceCellRasterizer(profile);
        var terrain = new SkyforgeNeoForge1211ChunkAdapter(
                fixture.catalog(),
                io.github.nidaba.skyforge.world.SkyIslandTerrainProfile.reference(),
                new SkyforgeMinecraftBlockPalette(),
                java.util.Map.of(fixture.volume().id(), fixture.descriptor()));
        var fluvial = SkyIslandFluvialTerrainField.create(fixture.descriptor());
        var realized = fixture.volume().compiledVolume().descriptor();

        int supportedRetainedAnchors = 0;
        for (var cell : profile.cells()) {
            if (!cell.physicalSurfacePresent()
                    || !(cell.retainedWaterbody() || cell.waterDepthPotential() > 0.0)) {
                continue;
            }
            var projected = rasterizer.projectAnchor(cell.watershedCellIndex(), terrain);
            if (projected.isEmpty()) {
                continue;
            }
            var anchor = projected.orElseThrow();
            SkyIslandFluvialSurfaceZone zone = fluvial.surfaceZone(new SkyIslandLocalPosition(
                    anchor.worldX() - realized.centerX(),
                    anchor.worldZ() - realized.centerZ()));
            if (zone == SkyIslandFluvialSurfaceZone.WET_CHANNEL) {
                continue;
            }
            assertEquals(
                    Biomes.SWAMP,
                    resolver.resolveAuthoredSurface(
                                    fixture.volume().id(),
                                    anchor.worldX(),
                                    anchor.worldZ())
                            .orElseThrow());
            supportedRetainedAnchors++;
        }
        assertTrue(
                supportedRetainedAnchors > 0,
                "DR-70 review fixture must expose retained standing water outside fine channels");
    }

    @Test
    void dryRiparianAndBankContextKeepsAuthoredLocalEcologyCarrier() {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.dr70Review();
        var association = SkyIslandAuthoredRealizationAssociation.of(fixture.descriptor(), fixture.volume());
        var resolver = new SkyforgeProductionEcologyResolver(association);
        var profile = new SkyIslandSurfaceSiteCapabilityProfiler().profile(association);
        var rasterizer = new SkyforgeAuthoredSurfaceCellRasterizer(profile);
        var terrain = new SkyforgeNeoForge1211ChunkAdapter(
                fixture.catalog(),
                io.github.nidaba.skyforge.world.SkyIslandTerrainProfile.reference(),
                new SkyforgeMinecraftBlockPalette(),
                java.util.Map.of(fixture.volume().id(), fixture.descriptor()));
        var authoredEcology = new SkyIslandAuthoredRealizationSurfaceEcologyResolver(
                new SkyIslandAuthoredRealizationCatalog(
                        association.authoredIdentity().worldSeed(),
                        association.realizedVolumeId().archipelagoRootSeed(),
                        List.of(association)));
        var fluvial = SkyIslandFluvialTerrainField.create(fixture.descriptor());
        var realized = fixture.volume().compiledVolume().descriptor();

        int verified = 0;
        for (var cell : profile.cells()) {
            if (!cell.physicalSurfacePresent()
                    || cell.retainedWaterbody()
                    || cell.waterDepthPotential() > 0.0
                    || !(cell.riparianPotential() > 0.0
                            || cell.waterbodyMarginPotential() > 0.0
                            || cell.shoreline())) {
                continue;
            }
            var projected = rasterizer.projectAnchor(cell.watershedCellIndex(), terrain);
            if (projected.isEmpty()) {
                continue;
            }
            var anchor = projected.orElseThrow();
            SkyIslandFluvialSurfaceZone zone = fluvial.surfaceZone(new SkyIslandLocalPosition(
                    anchor.worldX() - realized.centerX(),
                    anchor.worldZ() - realized.centerZ()));
            if (zone == SkyIslandFluvialSurfaceZone.WET_CHANNEL) {
                continue;
            }
            var ecologySample = authoredEcology.sample(
                    fixture.volume().id(),
                    new Coordinate2(anchor.worldX(), anchor.worldZ())).ecologySample();
            if (ecologySample.isEmpty()) {
                continue;
            }
            assertEquals(
                    SkyforgeProductionEcologyResolver.carrier(
                            ecologySample.orElseThrow().regime()),
                    resolver.resolveAuthoredSurface(
                                    fixture.volume().id(),
                                    anchor.worldX(),
                                    anchor.worldZ())
                            .orElseThrow(),
                    "dry riparian/bank terrain must inherit its authored local ecology carrier");
            verified++;
        }
        assertTrue(verified > 0, "fixture must expose at least one dry riparian/bank ecology sample");
    }

    @Test
    void sharedAuthoredFluvialFieldMatchesStandaloneResolverAndRejectsForeignDescriptor() {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.single();
        var association = SkyIslandAuthoredRealizationAssociation.of(fixture.descriptor(), fixture.volume());
        var fluvial = SkyIslandFluvialTerrainField.create(fixture.descriptor());
        var standalone = new SkyforgeProductionEcologyResolver(association);
        var shared = new SkyforgeProductionEcologyResolver(association, fluvial);

        var realized = fixture.volume().compiledVolume().descriptor();
        for (var reach : fluvial.reaches()) {
            var local = reach.path().points().get(reach.path().points().size() / 2);
            int worldX = Math.toIntExact(Math.round(realized.centerX() + local.x()));
            int worldZ = Math.toIntExact(Math.round(realized.centerZ() + local.z()));
            assertEquals(
                    standalone.resolveAuthoredSurface(fixture.volume().id(), worldX, worldZ),
                    shared.resolveAuthoredSurface(fixture.volume().id(), worldX, worldZ));
        }

        var foreign = SkyforgeNeoForge1211ProductionComposedCaveFixture.dr70Review();
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyforgeProductionEcologyResolver(
                        association,
                        SkyIslandFluvialTerrainField.create(foreign.descriptor())));
    }

    @Test
    void foreignVolumeFailsClosed() {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.single();
        var resolver = new SkyforgeProductionEcologyResolver(
                SkyIslandAuthoredRealizationAssociation.of(fixture.descriptor(), fixture.volume()));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(
                SkyforgeNeoForge1211ProductionComposedCaveFixture.stacked().upper().id(), 0, 220, 0));
    }

    private static java.util.Set<Long> requiredChunkKeys(io.github.nidaba.skyforge.world.WorldBounds bounds) {
        int minX = Math.floorDiv((int) Math.floor(bounds.minimumX()), 16);
        int maxX = Math.floorDiv((int) Math.floor(bounds.maximumX()), 16);
        int minZ = Math.floorDiv((int) Math.floor(bounds.minimumZ()), 16);
        int maxZ = Math.floorDiv((int) Math.floor(bounds.maximumZ()), 16);
        var result = new java.util.LinkedHashSet<Long>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                result.add(ChunkPos.asLong(x, z));
            }
        }
        return java.util.Set.copyOf(result);
    }
}
