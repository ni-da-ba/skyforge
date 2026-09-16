package io.github.nidaba.skyforge.neoforge1211;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.SectionPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** ClientLevel half of the SF-IMP-0068 persisted production composed-cave proof. */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID, value = Dist.CLIENT)
final class SkyforgeNeoForge1211ProductionComposedCaveReloadClientDevRuntime {
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211ProductionComposedCaveReloadClientDevRuntime.class.getName());
    private static final long CLIENT_TIMEOUT_NANOS = 90_000_000_000L;
    private static boolean proofComplete;
    private static long firstClientTickNanos = Long.MIN_VALUE;

    private SkyforgeNeoForge1211ProductionComposedCaveReloadClientDevRuntime() {}

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (proofComplete || !SkyforgeNeoForge1211ProductionComposedCaveReloadDevRuntime.enabled()) {
            return;
        }
        if (firstClientTickNanos == Long.MIN_VALUE) {
            firstClientTickNanos = System.nanoTime();
        }
        if (SkyforgeAutomatedAcceptanceHarness.clientMode()
                && System.nanoTime() - firstClientTickNanos > CLIENT_TIMEOUT_NANOS) {
            SkyforgeAutomatedAcceptanceHarness.failClientCase(
                    "SF-IMP-0068 reload client did not reach production composed-cave verification within 90 seconds");
        }

        var expectation = SkyforgeNeoForge1211ProductionComposedCaveReloadDevRuntime.clientExpectation();
        if (expectation == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        BlockPos nativeOnly = BlockPos.of(expectation.nativeOnlyPosition());
        BlockPos mouth = BlockPos.of(expectation.mouthPosition());
        BlockPos outward = BlockPos.of(expectation.outwardPosition());
        BlockPos base = BlockPos.of(expectation.basePosition());
        java.util.List<BlockPos> requiredPositions = new java.util.ArrayList<>(
                java.util.List.of(nativeOnly, mouth, outward, base));
        if (expectation.dr50() != null) {
            requiredPositions.add(expectation.dr50().materialPosition());
            requiredPositions.add(expectation.dr50().hydrologyPosition());
            requiredPositions.add(expectation.dr50().structurePosition());
        }
        for (BlockPos position : requiredPositions) {
            if (!minecraft.level.getChunkSource().hasChunk(
                    SectionPos.blockToSectionCoord(position.getX()),
                    SectionPos.blockToSectionCoord(position.getZ()))) {
                return;
            }
        }

        for (var biomeExpectation : expectation.dr40Biomes()) {
            if (!minecraft.level.getBiome(biomeExpectation.position()).is(biomeExpectation.biome())) {
                throw new IllegalStateException(
                        "DR-40 ClientLevel lost persisted ecology biome at "
                                + biomeExpectation.position()
                                + ": expected=" + biomeExpectation.biome().location()
                                + ", actual=" + minecraft.level.getBiome(biomeExpectation.position()));
            }
        }

        if (expectation.dr50() != null) {
            var dr50 = expectation.dr50();
            if (!minecraft.level.getBlockState(dr50.materialPosition()).is(net.minecraft.world.level.block.Blocks.IRON_ORE)
                    || !minecraft.level.getBlockState(dr50.hydrologyPosition()).is(net.minecraft.world.level.block.Blocks.WATER)) {
                throw new IllegalStateException("DR-50 ClientLevel lost persisted material or hydrology state");
            }
            var structureBlock = BuiltInRegistries.BLOCK.getKey(
                    minecraft.level.getBlockState(dr50.structurePosition()).getBlock());
            if (!structureBlock.equals(dr50.structureBlockId())) {
                throw new IllegalStateException(
                        "DR-50 ClientLevel changed representative structure block: expected="
                                + dr50.structureBlockId() + ", actual=" + structureBlock);
            }
        }

        var nativeOnlyState = minecraft.level.getBlockState(nativeOnly);
        var mouthState = minecraft.level.getBlockState(mouth);
        var outwardState = minecraft.level.getBlockState(outward);
        var baseState = minecraft.level.getBlockState(base);
        if (!nativeOnlyState.equals(expectation.nativeOnlyState())
                || !mouthState.equals(expectation.mouthState())
                || !outwardState.equals(expectation.outwardState())
                || !baseState.equals(expectation.baseState())) {
            throw new IllegalStateException(
                    "SF-IMP-0068 ClientLevel disagreed with persisted production cave union: nativeOnly="
                            + nativeOnlyState + ", mouth=" + mouthState
                            + ", outward=" + outwardState + ", base=" + baseState);
        }

        proofComplete = true;
        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0068 RELOAD CLIENT PASS: nativeOnly=" + nativeOnly
                        + ", mouth=" + mouth + ", outward=" + outward + ", base=" + base + ".");

        java.util.Map<String, Object> clientEvidence = new java.util.LinkedHashMap<>();
        clientEvidence.put("reloadClientPass", true);
        clientEvidence.put("clientNativeOnlyPos", Long.toString(nativeOnly.asLong()));
        clientEvidence.put("clientNativeOnlyState", nativeOnlyState.toString());
        clientEvidence.put("clientMouthState", mouthState.toString());
        clientEvidence.put("clientOutwardState", outwardState.toString());
        clientEvidence.put("clientBaseState", baseState.toString());
        if (!expectation.dr40Biomes().isEmpty()) {
            clientEvidence.put("dr40ReloadClientBiomePass", true);
        }
        if (expectation.dr50() != null) {
            clientEvidence.put("dr50ReloadClientPass", true);
        }
        SkyforgeAutomatedAcceptanceHarness.completeClientCase(clientEvidence);
        if (SkyforgeAutomatedAcceptanceHarness.clientMode()) {
            // The proof file is durably written before this point. Ordinarily Minecraft.stop()
            // shuts down the integrated server within a few seconds, but headless CI has
            // occasionally remained alive indefinitely after an otherwise complete ClientLevel
            // PASS. Keep graceful shutdown as the primary path, with a development-only bounded
            // fallback so acceptance never depends on a wedged UI/audio process after proof.
            Thread exitFallback = new Thread(() -> {
                try {
                    Thread.sleep(5_000L);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return;
                }
                Runtime.getRuntime().halt(0);
            }, "skyforge-sf-imp-0068-client-pass-exit");
            exitFallback.setDaemon(true);
            exitFallback.start();
            minecraft.stop();
        }
    }
}
