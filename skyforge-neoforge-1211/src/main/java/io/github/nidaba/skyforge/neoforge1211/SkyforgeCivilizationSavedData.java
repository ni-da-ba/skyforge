package io.github.nidaba.skyforge.neoforge1211;

import java.util.Objects;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Per-level persistence host for the bounded civilization authority specimen. */
final class SkyforgeCivilizationSavedData extends SavedData {
    static final String DATA_NAME = "skyforge_bootstrap_civilization";
    static final SavedData.Factory<SkyforgeCivilizationSavedData> FACTORY =
            new SavedData.Factory<>(SkyforgeCivilizationSavedData::new, SkyforgeCivilizationSavedData::load);
    private SkyforgeCivilizationRuntimeState state = new SkyforgeCivilizationRuntimeState();

    private SkyforgeCivilizationSavedData() {}
    static SkyforgeCivilizationSavedData forLevel(ServerLevel level) { return Objects.requireNonNull(level, "level").getDataStorage().computeIfAbsent(FACTORY, DATA_NAME); }
    static SkyforgeCivilizationSavedData load(CompoundTag tag, HolderLookup.Provider registries) { SkyforgeCivilizationSavedData data = new SkyforgeCivilizationSavedData(); data.state = SkyforgeCivilizationRuntimeState.load(tag); return data; }
    SkyforgeCivilizationRuntimeState state() { return state; }
    void stateChanged() { setDirty(); }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) { tag.merge(state.save()); return tag; }
}
