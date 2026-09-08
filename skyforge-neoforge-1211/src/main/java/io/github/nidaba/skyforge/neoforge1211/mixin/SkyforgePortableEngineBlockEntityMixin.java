package io.github.nidaba.skyforge.neoforge1211.mixin;

import io.github.nidaba.skyforge.neoforge1211.SkyforgePortableEngineCutoffAccess;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Optional, Portable-Engine-only ignition cutoff.
 *
 * <p>While an opted-in engine receives redstone, its active burn timer is temporarily replaced with
 * an infinite sentinel for the duration of Simulated's own tick. This prevents decrementing an
 * active fuel item and prevents consumption of the next queued item. The original timer is restored
 * before the tick returns. Simulated's own local {@code isLit} value is forced false while cut, so
 * upstream code owns kinetic-network/light transitions rather than Skyforge reimplementing them.
 */
@Pseudo
@Mixin(
        targets = "dev.simulated_team.simulated.content.blocks.portable_engine.PortableEngineBlockEntity",
        remap = false)
abstract class SkyforgePortableEngineBlockEntityMixin
        implements SkyforgePortableEngineCutoffAccess {
    private static final String SKYFORGE_CUTOFF_NBT = "SkyforgeRedstoneCutoffEnabled";
    private static final int SKYFORGE_INFINITE_BURN_SENTINEL = Integer.MAX_VALUE;

    @Shadow private int burnTime;

    @Unique private boolean skyforge$redstoneCutoffEnabled;
    @Unique private int skyforge$preservedBurnTime;
    @Unique private boolean skyforge$burnTimeTemporarilyMasked;

    @Override
    public boolean skyforge$isRedstoneCutoffEnabled() {
        return this.skyforge$redstoneCutoffEnabled;
    }

    @Override
    public void skyforge$setRedstoneCutoffEnabled(boolean enabled) {
        if (this.skyforge$redstoneCutoffEnabled == enabled) {
            return;
        }

        this.skyforge$redstoneCutoffEnabled = enabled;

        BlockEntity self = (BlockEntity) (Object) this;
        self.setChanged();

        Level level = self.getLevel();
        if (level != null) {
            var state = self.getBlockState();
            level.sendBlockUpdated(self.getBlockPos(), state, state, 3);
        }
    }

    @Override
    public boolean skyforge$isRedstoneCutoffActive() {
        if (!this.skyforge$redstoneCutoffEnabled) {
            return false;
        }

        BlockEntity self = (BlockEntity) (Object) this;
        Level level = self.getLevel();
        return level != null && level.hasNeighborSignal(self.getBlockPos());
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void skyforge$maskBurnTimerWhileCut(CallbackInfo ci) {
        if (!this.skyforge$isRedstoneCutoffActive()) {
            this.skyforge$burnTimeTemporarilyMasked = false;
            return;
        }

        this.skyforge$preservedBurnTime = this.burnTime;
        this.burnTime = SKYFORGE_INFINITE_BURN_SENTINEL;
        this.skyforge$burnTimeTemporarilyMasked = true;
    }

    /**
     * The first boolean stored by the audited pinned PortableEngineBlockEntity#tick is
     * {@code isLit = burnTime > 0}. Requiring exactly this local turns upstream bytecode drift into
     * an explicit compatibility failure instead of silently consuming fuel or generating power.
     */
    @ModifyVariable(method = "tick", at = @At("STORE"), ordinal = 0, require = 1)
    private boolean skyforge$forceEngineUnlitWhileCut(boolean upstreamIsLit) {
        return upstreamIsLit && !this.skyforge$isRedstoneCutoffActive();
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void skyforge$restoreBurnTimerAfterCutTick(CallbackInfo ci) {
        if (!this.skyforge$burnTimeTemporarilyMasked) {
            return;
        }

        this.burnTime = this.skyforge$preservedBurnTime;
        this.skyforge$burnTimeTemporarilyMasked = false;
    }

    @Inject(method = "write", at = @At("TAIL"))
    private void skyforge$writeCutoffMode(
            CompoundTag compound,
            HolderLookup.Provider registries,
            boolean clientPacket,
            CallbackInfo ci) {
        compound.putBoolean(SKYFORGE_CUTOFF_NBT, this.skyforge$redstoneCutoffEnabled);
    }

    @Inject(method = "read", at = @At("TAIL"))
    private void skyforge$readCutoffMode(
            CompoundTag compound,
            HolderLookup.Provider registries,
            boolean clientPacket,
            CallbackInfo ci) {
        this.skyforge$redstoneCutoffEnabled = compound.getBoolean(SKYFORGE_CUTOFF_NBT);
    }
}
