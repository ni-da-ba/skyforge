package io.github.nidaba.skyforge.neoforge1211;

import java.util.Objects;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.material.Fluids;

/**
 * Thread-confined compatibility scope for native features that are semantically allowed to dress
 * already-authored visible hydrology.
 *
 * <p>Skyforge keeps authority over where the lake/river exists. Dressing remains registry-native:
 * configured features that reuse Minecraft's built-in aquatic feature types can populate authored
 * water during their ordinary biome pass without granting the same write authority to trees,
 * arbitrary random patches, ores, magma or other unrelated features.
 */
final class SkyforgeNativeHydrologyDressingStage {
    enum Role {
        NONE,
        AQUATIC_VEGETATION,
        SUBSTRATE_DISK
    }

    private static final ThreadLocal<Role> ACTIVE = new ThreadLocal<>();

    private SkyforgeNativeHydrologyDressingStage() {}

    static Scope open(PlacedFeature placedFeature) {
        Objects.requireNonNull(placedFeature, "placedFeature");
        Role role = classify(placedFeature);
        if (role == Role.NONE) {
            return Scope.inactive();
        }
        if (ACTIVE.get() != null) {
            throw new IllegalStateException("nested native hydrology dressing scopes are not supported");
        }
        ACTIVE.set(role);
        return new Scope(role);
    }

    static Role classify(PlacedFeature placedFeature) {
        Objects.requireNonNull(placedFeature, "placedFeature");
        return classifyFeature(placedFeature.feature().value().feature());
    }

    static Role classifyFeature(Feature<?> feature) {
        Objects.requireNonNull(feature, "feature");
        if (feature == Feature.SEAGRASS || feature == Feature.KELP || feature == Feature.SEA_PICKLE) {
            return Role.AQUATIC_VEGETATION;
        }
        if (feature == Feature.DISK) {
            return Role.SUBSTRATE_DISK;
        }
        return Role.NONE;
    }

    static boolean active() {
        return ACTIVE.get() != null;
    }

    static BlockState populationReadState(BlockState authoredState) {
        Objects.requireNonNull(authoredState, "authoredState");
        Role role = ACTIVE.get();
        if (role == Role.SUBSTRATE_DISK
                && SkyforgeAuthoredVisibleHydrologyAdapter.isHydrologySurfaceMaterial(authoredState)) {
            // Vanilla disk targets are commonly authored against ordinary dirt/grass terrain.
            // Present a virtual dirt target while keeping the real authored bed stable until the
            // registered DiskFeature commits its own state.
            return Blocks.DIRT.defaultBlockState();
        }
        return authoredState;
    }

    static boolean allowsPreflight(BlockState authoredState) {
        Objects.requireNonNull(authoredState, "authoredState");
        return switch (activeRole()) {
            case AQUATIC_VEGETATION -> authoredState.is(Blocks.WATER);
            case SUBSTRATE_DISK -> SkyforgeAuthoredVisibleHydrologyAdapter
                    .isHydrologySurfaceMaterial(authoredState);
            case NONE -> false;
        };
    }

    static boolean allowsReplacement(BlockState authoredState, BlockState replacementState) {
        return allowsReplacement(activeRole(), authoredState, replacementState);
    }

    static boolean allowsReplacement(
            Role role,
            BlockState authoredState,
            BlockState replacementState) {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(authoredState, "authoredState");
        Objects.requireNonNull(replacementState, "replacementState");
        return switch (role) {
            case AQUATIC_VEGETATION -> authoredState.is(Blocks.WATER)
                    && isWaterBearing(replacementState);
            case SUBSTRATE_DISK -> SkyforgeAuthoredVisibleHydrologyAdapter
                            .isHydrologySurfaceMaterial(authoredState)
                    && !replacementState.isAir()
                    && replacementState.getFluidState().isEmpty()
                    && !replacementState.is(Blocks.MAGMA_BLOCK)
                    && !replacementState.is(Blocks.GRASS_BLOCK)
                    && !replacementState.is(Blocks.MYCELIUM)
                    && !replacementState.is(Blocks.PODZOL);
            case NONE -> false;
        };
    }

    static boolean allowsVisibleWaterReplacement(BlockState replacementState) {
        Objects.requireNonNull(replacementState, "replacementState");
        return activeRole() == Role.AQUATIC_VEGETATION && isWaterBearing(replacementState);
    }

    private static boolean isWaterBearing(BlockState state) {
        var fluid = state.getFluidState().getType();
        return fluid == Fluids.WATER || fluid == Fluids.FLOWING_WATER;
    }

    private static Role activeRole() {
        Role role = ACTIVE.get();
        return role == null ? Role.NONE : role;
    }

    static final class Scope implements AutoCloseable {
        private final Role role;
        private boolean closed;

        private Scope(Role role) {
            this.role = role;
        }

        private static Scope inactive() {
            return new Scope(Role.NONE);
        }

        void requireActive() {
            if (closed) {
                throw new IllegalStateException("native hydrology dressing scope is closed");
            }
            if (role != Role.NONE && ACTIVE.get() != role) {
                throw new IllegalStateException("native hydrology dressing scope changed before use");
            }
        }

        @Override
        public void close() {
            if (closed) {
                throw new IllegalStateException("native hydrology dressing scope already closed");
            }
            closed = true;
            if (role == Role.NONE) {
                return;
            }
            if (ACTIVE.get() != role) {
                throw new IllegalStateException("native hydrology dressing scope changed before close");
            }
            ACTIVE.remove();
        }
    }
}
