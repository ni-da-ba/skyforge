package io.github.nidaba.skyforge.neoforge1211;

import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Creates the blank quick-play world used by the WBY Wave 1 actual-client visibility gate. */
final class SkyforgeWbyWave1VisibilityWorldPrepareAcceptance {
    static final String ENABLE_PROPERTY = "skyforge.dev.wbyWave1VisibilityWorldPrepare";

    private SkyforgeWbyWave1VisibilityWorldPrepareAcceptance() {}

    static void installFromSystemProperty() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(SkyforgeWbyWave1VisibilityWorldPrepareAcceptance::onServerStarted);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        boolean policyFixture = Boolean.getBoolean("skyforge.dev.wbyS05PolicyFixture");
        if (policyFixture) {
            requirePaxiProbeTag();
        }

        SkyforgeAutomatedAcceptanceHarness.completeServerCase(
                event.getServer(),
                policyFixture
                        ? Map.of(
                                "wbyWave1VisibilityWorldPrepared", true,
                                "preassembledDistantSableBodyPersisted", false,
                                "paxiProbeTagLoaded", true)
                        : Map.of(
                                "wbyWave1VisibilityWorldPrepared", true,
                                "preassembledDistantSableBodyPersisted", false));
    }

    private static void requirePaxiProbeTag() {
        var probeTag = TagKey.create(
                Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath("skyforge_s05", "paxi_probe"));
        if (!Items.STICK.builtInRegistryHolder().is(probeTag)) {
            throw new IllegalStateException(
                    "S0.5A Paxi no-op datapack did not bind #skyforge_s05:paxi_probe to minecraft:stick");
        }
    }
}
