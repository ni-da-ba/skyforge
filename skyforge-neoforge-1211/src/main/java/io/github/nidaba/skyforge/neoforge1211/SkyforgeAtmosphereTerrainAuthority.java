package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldCatalog;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

/**
 * Read-only semantic terrain authority for runtime consumers such as server atmosphere.
 *
 * <p>This stage owns no world mutation, chunk admission, population, persistence, or block lookup.
 * It reconstructs deterministic surface semantics from the same immutable world catalog and
 * compiled terrain interpreter used by realization. One X/Z query returns only the highest unique
 * semantic surface, matching the 2.5-D terrain contract consumed by A4MC L0/L1.
 */
final class SkyforgeAtmosphereTerrainAuthority {
    private static final AtomicReference<Binding> ACTIVE = new AtomicReference<>();

    private SkyforgeAtmosphereTerrainAuthority() {}

    static AutoCloseable install(
            SkyIslandWorldCatalog catalog,
            SkyforgeNeoForge1211ChunkAdapter terrain,
            Map<SkyIslandWorldVolumeId, SkyforgeExactVolumeBiomeResolver> biomeResolvers) {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(terrain, "terrain");
        Objects.requireNonNull(biomeResolvers, "biomeResolvers");
        Map<SkyIslandWorldVolumeId, SkyforgeExactVolumeBiomeResolver> frozen =
                Map.copyOf(biomeResolvers);
        for (SkyIslandWorldVolumeId volumeId : frozen.keySet()) {
            if (catalog.volumes().stream().noneMatch(volume -> volume.id().equals(volumeId))) {
                throw new IllegalArgumentException(
                        "atmosphere terrain biome resolver references unknown volume "
                                + volumeId.path());
            }
        }
        Binding binding = new Binding(terrain, frozen);
        if (!ACTIVE.compareAndSet(null, binding)) {
            throw new IllegalStateException("a Skyforge atmosphere terrain authority is already installed");
        }
        return () -> {
            if (!ACTIVE.compareAndSet(binding, null)) {
                throw new IllegalStateException(
                        "Skyforge atmosphere terrain authority changed before close");
            }
        };
    }

    static boolean active() {
        return ACTIVE.get() != null;
    }

    static Optional<Sample> sample(
            int worldX,
            int worldZ,
            int minimumY,
            int height) {
        if (height <= 0) {
            throw new IllegalArgumentException("height must be positive");
        }
        Binding binding = ACTIVE.get();
        if (binding == null) {
            return Optional.empty();
        }
        var top = binding.terrain().atmosphereTopSurface(
                worldX,
                worldZ,
                binding.biomeResolvers()::containsKey);
        if (top.isEmpty()) {
            return Optional.empty();
        }
        var surface = top.orElseThrow();
        long maximumYExclusive = Math.addExact((long) minimumY, (long) height);
        if (surface.firstFreeY() <= minimumY || surface.firstFreeY() > maximumYExclusive) {
            return Optional.empty();
        }

        SkyforgeExactVolumeBiomeResolver resolver =
                binding.biomeResolvers().get(surface.volumeId());
        if (resolver == null) {
            return Optional.empty();
        }
        int surfaceY = surface.firstFreeY() - 1;
        if (!resolver.supportsSurface(surface.volumeId(), worldX, surfaceY, worldZ)) {
            return Optional.empty();
        }
        ResourceKey<Biome> biome = Objects.requireNonNull(
                resolver.resolve(surface.volumeId(), worldX, surfaceY, worldZ),
                "atmosphere terrain biome resolver returned null");
        return Optional.of(new Sample(surface.volumeId(), surface.firstFreeY(), biome));
    }

    record Sample(
            SkyIslandWorldVolumeId volumeId,
            int firstFreeHeight,
            ResourceKey<Biome> biome) {
        Sample {
            Objects.requireNonNull(volumeId, "volumeId");
            Objects.requireNonNull(biome, "biome");
        }
    }

    private record Binding(
            SkyforgeNeoForge1211ChunkAdapter terrain,
            Map<SkyIslandWorldVolumeId, SkyforgeExactVolumeBiomeResolver> biomeResolvers) {}
}
