package io.github.nidaba.skyforge.neoforge1211;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;

/**
 * First production-shaped NeoForge lifecycle seam for Skyforge chunk realization.
 *
 * <p>The subscriber reacts only to chunks that NeoForge identifies as newly generated. It never
 * queries neighboring chunks or the level from inside the event callback; all work is restricted
 * to the event's own {@link ChunkAccess}. A runtime binding must be installed explicitly before
 * the subscriber performs any realization.
 */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID)
public final class SkyforgeNeoForge1211ChunkLifecycle {
    private static final AtomicReference<RuntimeBinding> ACTIVE = new AtomicReference<>();

    private SkyforgeNeoForge1211ChunkLifecycle() {}

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        Objects.requireNonNull(event, "event");
        if (!event.isNewChunk()) {
            return;
        }

        RuntimeBinding binding = ACTIVE.get();
        if (binding == null || !binding.levelSelector().test(event.getLevel())) {
            return;
        }

        ChunkAccess chunk = event.getChunk();
        MinecraftChunkMaterialization materialization = binding.adapter().materialize(
                chunk.getPos(),
                chunk.getMinBuildHeight(),
                chunk.getHeight());
        binding.writer().writeSolidOverlay(chunk, materialization);
    }

    /**
     * Installs exactly one backend runtime binding and returns a handle that removes that same
     * binding when closed.
     *
     * <p>This is intentionally adapter-local. Dimension/environment selection remains a Minecraft
     * concern and is represented here only as a level predicate; no backend-neutral world model is
     * expanded merely to support this first lifecycle proof.
     */
    static AutoCloseable install(
            Predicate<LevelAccessor> levelSelector,
            SkyforgeNeoForge1211ChunkAdapter adapter,
            SkyforgeNeoForge1211ChunkWriter writer) {
        RuntimeBinding binding = new RuntimeBinding(levelSelector, adapter, writer);
        if (!ACTIVE.compareAndSet(null, binding)) {
            throw new IllegalStateException("a Skyforge NeoForge runtime binding is already installed");
        }
        return () -> {
            if (!ACTIVE.compareAndSet(binding, null)) {
                throw new IllegalStateException("Skyforge NeoForge runtime binding changed before close");
            }
        };
    }

    static boolean hasActiveBinding() {
        return ACTIVE.get() != null;
    }

    private record RuntimeBinding(
            Predicate<LevelAccessor> levelSelector,
            SkyforgeNeoForge1211ChunkAdapter adapter,
            SkyforgeNeoForge1211ChunkWriter writer) {
        private RuntimeBinding {
            Objects.requireNonNull(levelSelector, "levelSelector");
            Objects.requireNonNull(adapter, "adapter");
            Objects.requireNonNull(writer, "writer");
        }
    }
}
