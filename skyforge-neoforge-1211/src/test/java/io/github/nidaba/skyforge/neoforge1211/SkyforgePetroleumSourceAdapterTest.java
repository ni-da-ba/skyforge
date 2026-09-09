package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandPetroleumSystemOpportunityCell;
import io.github.nidaba.skyforge.world.SkyIslandPetroleumSystemOpportunityProfiler;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class SkyforgePetroleumSourceAdapterTest {
    private static final long WORLD = 0x4155544830303938L;
    private static final ResourceLocation LOWER = ResourceLocation.fromNamespaceAndPath("skyforge", "lower");
    private static final ResourceLocation UPPER = ResourceLocation.fromNamespaceAndPath("skyforge", "upper");
    private static final BlockPos LOWER_POS = new BlockPos(24, 96, -8);
    private static final BlockPos UPPER_POS = new BlockPos(24, 176, -8);

    @Test
    void c26AdmissionRejectsZeroSupportAndBaseWorldOrUnownedLocations() {
        var eligible = cells().stream().filter(cell -> cell.systemOpportunity() > 0.0).findFirst().orElseThrow();
        var zero = cells().stream().filter(cell -> cell.systemOpportunity() == 0.0).findFirst().orElseThrow();
        var adapter = adapter(eligible);
        assertTrue(adapter.pumpjackTermination(LOWER, LOWER_POS, false).isPresent());
        assertFalse(adapter.pumpjackTermination(LOWER, LOWER_POS, true).isPresent());
        assertFalse(adapter.pumpjackTermination(LOWER, LOWER_POS.above(), false).isPresent());
        assertThrows(IllegalArgumentException.class, () -> new SkyforgePetroleumSourceAdapter.AdmissibleSupport(
                new SkyforgePetroleumSourceAdapter.SourceAddress(LOWER, LOWER_POS.above()), zero));
    }

    @Test
    void quantityPressureDepletionReloadAndStackedVolumeIsolationAreExplicit() {
        var eligible = cells().stream().filter(cell -> cell.systemOpportunity() > 0.0).findFirst().orElseThrow();
        var spec = new SkyforgePetroleumSourceAdapter.Specification(1_000, 40);
        var lowerAddress = new SkyforgePetroleumSourceAdapter.SourceAddress(LOWER, LOWER_POS);
        var upperAddress = new SkyforgePetroleumSourceAdapter.SourceAddress(UPPER, UPPER_POS);
        var adapter = new SkyforgePetroleumSourceAdapter(spec, List.of(
                new SkyforgePetroleumSourceAdapter.AdmissibleSupport(lowerAddress, eligible),
                new SkyforgePetroleumSourceAdapter.AdmissibleSupport(upperAddress, eligible)));
        var lower = adapter.pumpjackTermination(LOWER, LOWER_POS, false).orElseThrow();
        assertEquals(40, lower.pressure());
        assertEquals(275, adapter.extract(lower, 275));
        assertEquals(725, adapter.save().get(lowerAddress));
        assertEquals(1_000, adapter.save().get(upperAddress));
        var reloaded = new SkyforgePetroleumSourceAdapter(spec, List.of(
                new SkyforgePetroleumSourceAdapter.AdmissibleSupport(lowerAddress, eligible),
                new SkyforgePetroleumSourceAdapter.AdmissibleSupport(upperAddress, eligible)));
        reloaded.reload(adapter.save());
        assertEquals(adapter.save(), reloaded.save());
        assertEquals(725, reloaded.extract(reloaded.pumpjackTermination(LOWER, LOWER_POS, false).orElseThrow(), 9_999));
        assertFalse(reloaded.pumpjackTermination(LOWER, LOWER_POS, false).isPresent());
        assertTrue(reloaded.pumpjackTermination(UPPER, UPPER_POS, false).isPresent());
    }

    private static SkyforgePetroleumSourceAdapter adapter(SkyIslandPetroleumSystemOpportunityCell cell) {
        return new SkyforgePetroleumSourceAdapter(new SkyforgePetroleumSourceAdapter.Specification(1_000, 40), List.of(
                new SkyforgePetroleumSourceAdapter.AdmissibleSupport(
                        new SkyforgePetroleumSourceAdapter.SourceAddress(LOWER, LOWER_POS), cell)));
    }

    private static List<SkyIslandPetroleumSystemOpportunityCell> cells() {
        SkyIslandDescriptor base = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(WORLD, 9L, 98L, 98005L));
        SkyIslandDescriptor descriptor = new SkyIslandDescriptor(
                base.schemaVersion(),
                base.identity(),
                base.authorshipSeed(),
                base.morphologyFamily(),
                180.0,
                base.reliefBudget(),
                0.72,
                0.68,
                0.70,
                0.70,
                base.exposureTendency(),
                0.60,
                0.76,
                base.ecologicalPotential());
        return new SkyIslandPetroleumSystemOpportunityProfiler().profile(descriptor).cells();
    }
}
