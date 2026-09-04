package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Persistent provenance and asynchronous exact-volume fence for fluids created by native
 * {@code FLUID_SPRINGS} population.
 *
 * <p>Synchronous spring feature execution remains governed by the existing population execution
 * scope. This stage records only fluid states/ticks produced while that explicitly admitted phase
 * is active. Later {@link net.minecraft.world.level.material.FlowingFluid} ticks recover the
 * originating exact Skyforge volume from per-level SavedData and temporarily reinstate a
 * propagation-only ownership scope.
 *
 * <p>Ordinary Minecraft fluids carry no provenance and are therefore untouched. The propagation
 * scope treats positions outside the original compiled owner as an impermeable boundary and rejects
 * writes there. Carved cave AIR remains traversable because those positions are still compiled
 * owner-solid coordinates even though their live Minecraft block state is air.
 */
public final class SkyforgeGeneratedFluidPropagationStage {
    private static final String DATA_NAME = "skyforge_generated_fluid_provenance";
    private static final int MAX_TRACKED_POSITIONS = 250_000;
    private static final ThreadLocal<Context> ACTIVE = new ThreadLocal<>();
    private static final Map<ServerLevel, GeneratedFluidData> DATA_CACHE = new WeakHashMap<>();
    private static final Map<ServerLevel, Boolean> DATA_LOOKUP_COMPLETE = new WeakHashMap<>();
    private static final Map<ServerLevel, Map<SkyIslandWorldVolumeId, Counters>> COUNTERS =
            new WeakHashMap<>();

    private static final SavedData.Factory<GeneratedFluidData> DATA_FACTORY =
            new SavedData.Factory<>(GeneratedFluidData::new, GeneratedFluidData::load);

    private SkyforgeGeneratedFluidPropagationStage() {}

    /** Opens schedule/write capture only for one admitted native FLUID_SPRINGS operation. */
    static Scope openPopulation(
            WorldGenLevel level,
            SkyforgePopulationOperation operation) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(operation, "operation");
        if (operation.generationStep()
                != net.minecraft.world.level.levelgen.GenerationStep.Decoration.FLUID_SPRINGS.ordinal()) {
            return Scope.inactive();
        }
        if (ACTIVE.get() != null) {
            throw new IllegalStateException("nested Skyforge generated-fluid scopes are not supported");
        }
        ServerLevel serverLevel = serverLevel(level);
        Context context = new Context(serverLevel, operation.volumeId(), Mode.CAPTURE);
        ACTIVE.set(context);
        return new Scope(context);
    }

    /**
     * Opens a propagation fence if the scheduled fluid position has persisted Skyforge provenance.
     *
     * <p>A stale provenance record is removed if the current fluid no longer matches the recorded
     * registry identity.
     */
    public static void beginFluidTick(
            Level level,
            BlockPos position,
            FluidState state) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(state, "state");
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (ACTIVE.get() != null) {
            throw new IllegalStateException("nested Skyforge generated-fluid tick scopes are not supported");
        }
        GeneratedFluidData data = dataIfPresent(serverLevel);
        if (data == null) {
            return;
        }
        Provenance provenance = data.provenance(position.asLong());
        if (provenance == null) {
            return;
        }
        ResourceLocation actualFluidKey = BuiltInRegistries.FLUID.getKey(state.getType());
        if (actualFluidKey == null || !actualFluidKey.equals(provenance.fluidKey())) {
            data.remove(position.asLong());
            return;
        }
        if (!ownerSolid(provenance.volumeId(), position)) {
            data.remove(position.asLong());
            return;
        }
        counters(serverLevel, provenance.volumeId()).propagationTicks++;
        ACTIVE.set(new Context(serverLevel, provenance.volumeId(), Mode.PROPAGATION));
    }

    /** Closes the propagation scope opened at the start of one generated FlowingFluid tick. */
    public static void endFluidTick() {
        Context context = ACTIVE.get();
        if (context != null && context.mode() == Mode.PROPAGATION) {
            ACTIVE.remove();
        }
    }

    /** Returns whether an asynchronous generated-fluid propagation scope is active. */
    public static boolean propagationActive() {
        Context context = ACTIVE.get();
        return context != null && context.mode() == Mode.PROPAGATION;
    }

    /**
     * Returns whether a block/fluid read belongs to the originating compiled exact volume.
     *
     * <p>This is intentionally based on immutable compiled occupancy, not the live Minecraft block
     * state. A carved AIR cell therefore remains part of the fluid domain while exterior air does
     * not.
     */
    public static boolean isVisible(BlockPos position) {
        Objects.requireNonNull(position, "position");
        Context context = ACTIVE.get();
        if (context == null || context.mode() != Mode.PROPAGATION) {
            return true;
        }
        boolean visible = ownerSolid(context.volumeId(), position);
        if (!visible) {
            counters(context.serverLevel(), context.volumeId()).hiddenBoundaryReads++;
        }
        return visible;
    }

    /** Exact-volume write fence for asynchronous generated-fluid propagation. */
    public static boolean acceptWrite(BlockPos position) {
        Objects.requireNonNull(position, "position");
        Context context = ACTIVE.get();
        if (context == null || context.mode() != Mode.PROPAGATION) {
            return true;
        }
        boolean accepted = ownerSolid(context.volumeId(), position);
        if (!accepted) {
            counters(context.serverLevel(), context.volumeId()).rejectedBoundaryWrites++;
        }
        return accepted;
    }

    /**
     * Captures a scheduled fluid tick while either the initial spring or one of its descendants is
     * active. Scheduling outside the compiled owner is ignored; the propagation write fence remains
     * authoritative and no exterior provenance is created.
     */
    public static void observeScheduledTick(
            BlockPos position,
            Object type) {
        Objects.requireNonNull(position, "position");
        Context context = ACTIVE.get();
        if (context == null || !(type instanceof Fluid fluid)) {
            return;
        }
        Counters counters = counters(context.serverLevel(), context.volumeId());
        if (!ownerSolid(context.volumeId(), position)) {
            counters.scheduledOutsideOwner++;
            return;
        }
        counters.capturedSchedules++;
        track(context.serverLevel(), context.volumeId(), position, fluid);
    }

    /**
     * Records a successfully committed fluid block while a spring capture/propagation scope is
     * active, and opportunistically removes stale records for tracked worlds when a block loses the
     * recorded fluid outside such a scope.
     */
    public static void observeCommittedBlockWrite(
            Level level,
            BlockPos position,
            BlockState state,
            boolean changed) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(state, "state");
        if (!changed || !(level instanceof ServerLevel serverLevel)) {
            return;
        }

        Context context = ACTIVE.get();
        FluidState fluidState = state.getFluidState();
        if (context != null && context.serverLevel() == serverLevel) {
            if (ownerSolid(context.volumeId(), position)) {
                if (!fluidState.isEmpty()) {
                    track(serverLevel, context.volumeId(), position, fluidState.getType());
                } else {
                    GeneratedFluidData activeData = dataIfPresent(serverLevel);
                    if (activeData != null) {
                        activeData.remove(position.asLong());
                    }
                }
            }
            return;
        }

        GeneratedFluidData data;
        synchronized (DATA_CACHE) {
            data = DATA_CACHE.get(serverLevel);
        }
        if (data == null) {
            return;
        }
        Provenance provenance = data.provenance(position.asLong());
        if (provenance == null) {
            return;
        }
        ResourceLocation actual = fluidState.isEmpty()
                ? null
                : BuiltInRegistries.FLUID.getKey(fluidState.getType());
        if (!provenance.fluidKey().equals(actual)) {
            data.remove(position.asLong());
        }
    }

    /** Development/runtime evidence for one exact volume without exposing mutable SavedData. */
    static Snapshot snapshot(
            ServerLevel level,
            SkyIslandWorldVolumeId volumeId) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(volumeId, "volumeId");
        GeneratedFluidData data = dataIfPresent(level);
        int count = 0;
        long digest = 0xcbf29ce484222325L;
        if (data != null) {
            for (var entry : data.entries.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .toList()) {
                if (!entry.getValue().volumeId().equals(volumeId)) {
                    continue;
                }
                count++;
                digest = mix(digest, entry.getKey());
                digest = mix(digest, entry.getValue().fluidKey().toString().hashCode());
            }
        }
        Counters counters = counters(level, volumeId);
        return new Snapshot(
                count,
                digest,
                counters.capturedSchedules,
                counters.scheduledOutsideOwner,
                counters.propagationTicks,
                counters.hiddenBoundaryReads,
                counters.rejectedBoundaryWrites);
    }

    /** Immutable sorted generated-fluid positions used only by runtime acceptance scans. */
    static List<TrackedFluid> trackedFluids(
            ServerLevel level,
            SkyIslandWorldVolumeId volumeId) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(volumeId, "volumeId");
        GeneratedFluidData data = dataIfPresent(level);
        if (data == null) {
            return List.of();
        }
        List<TrackedFluid> result = new ArrayList<>();
        for (var entry : data.entries.entrySet()) {
            if (entry.getValue().volumeId().equals(volumeId)) {
                result.add(new TrackedFluid(entry.getKey(), entry.getValue().fluidKey()));
            }
        }
        result.sort(Comparator.comparingLong(TrackedFluid::position));
        return List.copyOf(result);
    }

    private static void track(
            ServerLevel level,
            SkyIslandWorldVolumeId volumeId,
            BlockPos position,
            Fluid fluid) {
        ResourceLocation fluidKey = BuiltInRegistries.FLUID.getKey(fluid);
        if (fluidKey == null) {
            throw new IllegalStateException("generated Skyforge fluid is absent from the built-in registry");
        }
        data(level).put(
                position.asLong(),
                new Provenance(volumeId, fluidKey));
    }

    private static boolean ownerSolid(
            SkyIslandWorldVolumeId volumeId,
            BlockPos position) {
        return SkyforgeNeoForge1211SurfaceStage.isSolidOwnedBy(
                        volumeId,
                        position.getX(),
                        position.getY(),
                        position.getZ())
                .orElseThrow(() -> new IllegalStateException(
                        "generated-fluid propagation requires the active compiled Skyforge terrain binding"));
    }

    @SuppressWarnings("deprecation")
    private static ServerLevel serverLevel(WorldGenLevel level) {
        if (level instanceof ServerLevel serverLevel) {
            return serverLevel;
        }
        if (level instanceof WorldGenRegion region) {
            return region.getLevel();
        }
        throw new IllegalStateException(
                "generated-fluid capture requires ServerLevel or WorldGenRegion, found " + level.getClass());
    }

    private static Counters counters(
            ServerLevel level,
            SkyIslandWorldVolumeId volumeId) {
        synchronized (COUNTERS) {
            return COUNTERS
                    .computeIfAbsent(level, ignored -> new HashMap<>())
                    .computeIfAbsent(volumeId, ignored -> new Counters());
        }
    }

    private static GeneratedFluidData data(ServerLevel level) {
        synchronized (DATA_CACHE) {
            GeneratedFluidData cached = DATA_CACHE.get(level);
            if (cached != null) {
                return cached;
            }
            GeneratedFluidData loaded = level.getDataStorage().computeIfAbsent(DATA_FACTORY, DATA_NAME);
            DATA_CACHE.put(level, loaded);
            DATA_LOOKUP_COMPLETE.put(level, true);
            return loaded;
        }
    }

    private static GeneratedFluidData dataIfPresent(ServerLevel level) {
        synchronized (DATA_CACHE) {
            if (DATA_LOOKUP_COMPLETE.containsKey(level)) {
                return DATA_CACHE.get(level);
            }
            GeneratedFluidData loaded = level.getDataStorage().get(DATA_FACTORY, DATA_NAME);
            DATA_LOOKUP_COMPLETE.put(level, true);
            if (loaded != null) {
                DATA_CACHE.put(level, loaded);
            }
            return loaded;
        }
    }

    private static long mix(long digest, long value) {
        long mixed = digest;
        for (int shift = 0; shift < Long.SIZE; shift += Byte.SIZE) {
            mixed ^= (value >>> shift) & 0xffL;
            mixed *= 0x100000001b3L;
        }
        return mixed;
    }

    enum Mode {
        CAPTURE,
        PROPAGATION
    }

    private record Context(
            ServerLevel serverLevel,
            SkyIslandWorldVolumeId volumeId,
            Mode mode) {
        private Context {
            Objects.requireNonNull(serverLevel, "serverLevel");
            Objects.requireNonNull(volumeId, "volumeId");
            Objects.requireNonNull(mode, "mode");
        }
    }

    record Provenance(
            SkyIslandWorldVolumeId volumeId,
            ResourceLocation fluidKey) {
        Provenance {
            Objects.requireNonNull(volumeId, "volumeId");
            Objects.requireNonNull(fluidKey, "fluidKey");
        }
    }

    record Snapshot(
            int trackedPositions,
            long digest,
            long capturedSchedules,
            long scheduledOutsideOwner,
            long propagationTicks,
            long hiddenBoundaryReads,
            long rejectedBoundaryWrites) {}

    record TrackedFluid(long position, ResourceLocation fluidKey) {
        TrackedFluid {
            Objects.requireNonNull(fluidKey, "fluidKey");
        }
    }

    private static final class Counters {
        private long capturedSchedules;
        private long scheduledOutsideOwner;
        private long propagationTicks;
        private long hiddenBoundaryReads;
        private long rejectedBoundaryWrites;
    }

    static final class Scope implements AutoCloseable {
        private final Context context;
        private boolean closed;

        private Scope(Context context) {
            this.context = context;
        }

        static Scope inactive() {
            return new Scope(null);
        }

        void requireActive() {
            if (closed) {
                throw new IllegalStateException("generated-fluid scope is closed");
            }
            if (context != null && ACTIVE.get() != context) {
                throw new IllegalStateException("generated-fluid scope changed before completion");
            }
        }

        @Override
        public void close() {
            if (closed) {
                throw new IllegalStateException("generated-fluid scope already closed");
            }
            closed = true;
            if (context != null) {
                if (ACTIVE.get() != context) {
                    throw new IllegalStateException("generated-fluid scope changed before close");
                }
                ACTIVE.remove();
            }
        }
    }

    private static final class GeneratedFluidData extends SavedData {
        private final Map<Long, Provenance> entries = new HashMap<>();

        private GeneratedFluidData() {}

        private static GeneratedFluidData load(
                CompoundTag tag,
                HolderLookup.Provider registries) {
            GeneratedFluidData data = new GeneratedFluidData();
            ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);
            if (list.size() > MAX_TRACKED_POSITIONS) {
                throw new IllegalStateException(
                        "Skyforge generated-fluid provenance exceeds safety cap: " + list.size());
            }
            for (int index = 0; index < list.size(); index++) {
                CompoundTag entry = list.getCompound(index);
                String fluidText = entry.getString("fluid");
                ResourceLocation fluidKey = ResourceLocation.tryParse(fluidText);
                if (fluidKey == null || !BuiltInRegistries.FLUID.containsKey(fluidKey)) {
                    continue;
                }
                SkyIslandWorldVolumeId volumeId = new SkyIslandWorldVolumeId(
                        entry.getLong("rootSeed"),
                        entry.getString("group"),
                        entry.getInt("groupOrdinal"),
                        entry.getInt("memberOrdinal"),
                        entry.getLong("geometrySeed"));
                data.entries.put(
                        entry.getLong("position"),
                        new Provenance(volumeId, fluidKey));
            }
            return data;
        }

        private Provenance provenance(long position) {
            return entries.get(position);
        }

        private void put(long position, Provenance provenance) {
            Provenance existing = entries.get(position);
            if (provenance.equals(existing)) {
                return;
            }
            if (existing == null && entries.size() >= MAX_TRACKED_POSITIONS) {
                throw new IllegalStateException(
                        "Skyforge generated-fluid provenance reached safety cap "
                                + MAX_TRACKED_POSITIONS);
            }
            entries.put(position, provenance);
            setDirty();
        }

        private void remove(long position) {
            if (entries.remove(position) != null) {
                setDirty();
            }
        }

        @Override
        public CompoundTag save(
                CompoundTag tag,
                HolderLookup.Provider registries) {
            ListTag list = new ListTag();
            entries.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        Provenance provenance = entry.getValue();
                        SkyIslandWorldVolumeId volumeId = provenance.volumeId();
                        CompoundTag encoded = new CompoundTag();
                        encoded.putLong("position", entry.getKey());
                        encoded.putLong("rootSeed", volumeId.archipelagoRootSeed());
                        encoded.putString("group", volumeId.groupIdentifier());
                        encoded.putInt("groupOrdinal", volumeId.groupOrdinal());
                        encoded.putInt("memberOrdinal", volumeId.memberOrdinal());
                        encoded.putLong("geometrySeed", volumeId.geometrySeed());
                        encoded.putString("fluid", provenance.fluidKey().toString());
                        list.add(encoded);
                    });
            tag.put("entries", list);
            return tag;
        }
    }
}
