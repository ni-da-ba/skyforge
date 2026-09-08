package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.recipes.skyisland.MorphologyFamily;
import io.github.nidaba.skyforge.world.SkyIslandTerrainProfile;
import io.github.nidaba.skyforge.world.WorldBounds;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Mutation-inert actual-client viewer for one selected SF-IMP-0083 seed/scale specimen. */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID)
final class SkyforgeNeoForge1211ProductionMorphologySeedScaleViewer {
    static final String FAMILY_PROPERTY = "skyforge.dev.productionMorphologySeedScaleViewerFamily";
    static final String MEMBER_PROPERTY = "skyforge.dev.productionMorphologySeedScaleViewerMember";

    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211ProductionMorphologySeedScaleViewer.class.getName());

    private static AutoCloseable persistentTerrainOwnershipBinding;
    private static volatile boolean acceptanceServerProofComplete;
    private static volatile String acceptanceEvidence = "pending";

    private SkyforgeNeoForge1211ProductionMorphologySeedScaleViewer() {}

    static boolean enabled() {
        String value = System.getProperty(FAMILY_PROPERTY);
        return value != null && !value.isBlank();
    }

    static MorphologyFamily selectedFamily() {
        String value = System.getProperty(FAMILY_PROPERTY);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("SF-IMP-0083 seed/scale viewer family is not configured");
        }
        return SkyforgeProductionMorphologySeedScaleMatrixFixture.family(value);
    }

    static String selectedMemberCommand() {
        String value = System.getProperty(MEMBER_PROPERTY);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("SF-IMP-0083 seed/scale viewer member is not configured");
        }
        return value;
    }

    static SkyforgeProductionMorphologySeedScaleMatrixFixture.FamilyFixture selectedFixture() {
        return FixtureHolder.FIXTURE;
    }

    private static final class FixtureHolder {
        private static final SkyforgeProductionMorphologySeedScaleMatrixFixture.FamilyFixture FIXTURE =
                SkyforgeProductionMorphologySeedScaleMatrixFixture.buildMember(
                        selectedFamily(), selectedMemberCommand());
    }

    static synchronized void installFromSystemProperty() {
        if (!enabled() || persistentTerrainOwnershipBinding != null) {
            return;
        }
        if (SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()
                || SkyforgePhysicalVolumeAdmissionStage.active()
                || SkyforgeNativeSurfacePopulationStage.hasActiveBinding()
                || SkyforgeComposedCaveStage.active()
                || SkyforgeNativeInteriorPopulationStage.active()) {
            throw new IllegalStateException(
                    "SF-IMP-0083 seed/scale viewer must begin without a live mutation lifecycle");
        }

        var fixture = selectedFixture();
        SkyforgeAutomatedAcceptanceHarness.installWarmupChunkKeys(fixture.footprintChunkKeys());
        persistentTerrainOwnershipBinding = SkyforgeNeoForge1211SurfaceStage.install(
                new SkyforgeNeoForge1211ChunkAdapter(
                        fixture.catalog(),
                        SkyIslandTerrainProfile.reference(),
                        new SkyforgeMinecraftBlockPalette()),
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()));

        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0083 seed/scale viewer restored ownership only: family="
                        + fixture.family().identifier()
                        + ", member=" + fixture.member().member().id()
                        + ", footprintChunks=" + fixture.footprintChunkKeys().size());
    }

    @SubscribeEvent
    static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!enabled() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        var member = selectedFixture().members().getFirst();
        move(player, member, stop(member, "above"));
        player.sendSystemMessage(Component.literal(
                "[Skyforge Seed/Scale Review] " + selectedFamily().identifier()
                        + " / " + member.member().commandId()
                        + " loaded as one mutation-inert exact AUTH-0083 specimen."));
        player.sendSystemMessage(Component.literal(
                "[Skyforge Seed/Scale Atlas] /skyforge_morphology_matrix <member> "
                        + "above | approach | below | orbit"));
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        if (!enabled()) {
            return;
        }
        var root = Commands.literal("skyforge_morphology_matrix")
                .executes(context -> showHelp(context.getSource().getPlayerOrException()));
        for (var member : selectedFixture().members()) {
            var memberLiteral = Commands.literal(member.member().commandId());
            for (String view : java.util.List.of("above", "approach", "below", "orbit")) {
                memberLiteral.then(Commands.literal(view)
                        .executes(context -> move(
                                context.getSource().getPlayerOrException(),
                                member,
                                stop(member, view))));
            }
            root.then(memberLiteral);
        }
        event.getDispatcher().register(root);
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (!enabled()
                || !SkyforgeAutomatedAcceptanceHarness.clientMode()
                || acceptanceServerProofComplete) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (level.dimension().equals(Level.OVERWORLD) && !level.players().isEmpty()) {
                provePersistedMorphology(level, event);
            }
        }
    }

    private static synchronized void provePersistedMorphology(
            ServerLevel level,
            ServerTickEvent.Post event) {
        if (acceptanceServerProofComplete) {
            return;
        }
        if (!SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()
                || SkyforgePhysicalVolumeAdmissionStage.active()
                || SkyforgeNativeSurfacePopulationStage.hasActiveBinding()
                || SkyforgeComposedCaveStage.active()
                || SkyforgeNativeInteriorPopulationStage.active()) {
            SkyforgeAutomatedAcceptanceHarness.fail(
                    event.getServer(),
                    "SF-IMP-0083 seed/scale viewer did not preserve ownership-only runtime isolation");
            return;
        }

        var fixture = selectedFixture();
        if (level.getMinBuildHeight()
                        != SkyforgeProductionMorphologySeedScaleMatrixFixture.REVIEW_DIMENSION_MIN_Y
                || level.getHeight()
                        != SkyforgeProductionMorphologySeedScaleMatrixFixture.REVIEW_DIMENSION_HEIGHT) {
            SkyforgeAutomatedAcceptanceHarness.fail(
                    event.getServer(),
                    "SF-IMP-0083 viewer reopened the wrong dimension interval");
            return;
        }

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("viewerFamily", fixture.family().identifier());
        evidence.put("viewerMemberCount", fixture.members().size());
        evidence.put("viewerTerrainOwnershipRestored", true);
        evidence.put("viewerMutationBindingsInert", true);
        evidence.put("viewerFamilyFootprintChunks", fixture.footprintChunkKeys().size());

        StringBuilder summary = new StringBuilder();
        for (int index = 0; index < fixture.members().size(); index++) {
            var member = fixture.members().get(index);
            var optional =
                    SkyforgeNeoForge1211ProductionMorphologySeedScaleDevRuntime.scanLoadedSurface(level, member);
            if (optional.isEmpty()) {
                return;
            }
            var surface = optional.orElseThrow();
            if (!surface.valid()) {
                SkyforgeAutomatedAcceptanceHarness.fail(
                        event.getServer(),
                        "SF-IMP-0083 " + member.member().id()
                                + " changed across persistence/reopen: " + surface);
                return;
            }
            String prefix = "viewerMember" + index;
            evidence.put(prefix + "Id", member.member().id());
            evidence.put(prefix + "SampledClaims", surface.claimedColumns());
            evidence.put(prefix + "StoredTop", surface.storedTopBlocks());
            evidence.put(prefix + "AirAbove", surface.airAboveColumns());
            evidence.put(prefix + "StoredUnderside", surface.storedUndersideBlocks());
            evidence.put(prefix + "AirBelow", surface.airBelowColumns());
            evidence.put(prefix + "HeightMismatches", surface.heightMismatches());
            evidence.put(prefix + "LandTop", surface.landTopBlocks());
            evidence.put(prefix + "GrassTop", surface.grassTopBlocks());
            evidence.put(prefix + "SurfaceDigest", Long.toUnsignedString(surface.digest()));
            if (!summary.isEmpty()) {
                summary.append("; ");
            }
            summary.append(member.member().commandId()).append('=').append(surface);
        }

        acceptanceEvidence = summary.toString();
        acceptanceServerProofComplete = true;
        SkyforgeAutomatedAcceptanceHarness.record(evidence);
        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0083 SEED/SCALE VIEWER SERVER PASS: family="
                        + fixture.family().identifier() + ", " + acceptanceEvidence);
    }

    static boolean acceptanceServerProofComplete() {
        return acceptanceServerProofComplete;
    }

    static String acceptanceEvidence() {
        return acceptanceEvidence;
    }

    private static int showHelp(ServerPlayer player) {
        player.sendSystemMessage(Component.literal(
                "[Skyforge Seed/Scale Atlas] Reviewing " + selectedFamily().identifier()));
        for (var member : selectedFixture().members()) {
            player.sendSystemMessage(Component.literal(
                    "  " + member.member().commandId() + " -> " + member.member().id()));
        }
        player.sendSystemMessage(Component.literal(
                "  /skyforge_morphology_matrix <member> above|approach|below|orbit"));
        return 1;
    }

    private static int move(
            ServerPlayer player,
            SkyforgeProductionMorphologySeedScaleMatrixFixture.MemberFixture member,
            Stop stop) {
        player.setGameMode(GameType.SPECTATOR);
        player.teleportTo(stop.x(), stop.y(), stop.z());
        player.setYRot(stop.yaw());
        player.setXRot(stop.pitch());
        player.sendSystemMessage(Component.literal(
                "[Skyforge Seed/Scale Atlas] " + member.member().id()
                        + " / " + stop.name() + ": " + stop.description()));
        return 1;
    }

    private static Stop stop(
            SkyforgeProductionMorphologySeedScaleMatrixFixture.MemberFixture fixture,
            String name) {
        WorldBounds bounds = fixture.exactSupport().bounds();
        double centerX = (bounds.minimumX() + bounds.maximumX()) * 0.5;
        double centerZ = (bounds.minimumZ() + bounds.maximumZ()) * 0.5;
        double suspension = fixture.translatedDescriptor().suspensionElevation();
        double width = bounds.maximumX() - bounds.minimumX() + 1.0;
        double depth = bounds.maximumZ() - bounds.minimumZ() + 1.0;
        double maximumViewerY =
                SkyforgeProductionMorphologySeedScaleMatrixFixture.REVIEW_DIMENSION_MAX_Y_EXCLUSIVE - 4.0;

        return switch (name) {
            case "above" -> new Stop(
                    name,
                    centerX,
                    Math.min(maximumViewerY, bounds.maximumY() + 58.0),
                    centerZ,
                    0.0f,
                    90.0f,
                    "Planform, family identity, and upper-surface macro/meso hierarchy.");
            case "approach" -> new Stop(
                    name,
                    centerX,
                    suspension + 18.0,
                    bounds.maximumZ() + Math.max(72.0, depth * 0.45),
                    180.0f,
                    8.0f,
                    "Horizon approach: silhouette, rim transition, and family readability.");
            case "below" -> new Stop(
                    name,
                    centerX,
                    Math.max(
                            SkyforgeProductionMorphologySeedScaleMatrixFixture.REVIEW_DIMENSION_MIN_Y + 4.0,
                            bounds.minimumY() - 12.0),
                    centerZ + depth * 0.18,
                    0.0f,
                    -72.0f,
                    "Underside: taper, asymmetry, coherence, shelves, pinches, or detached spikes.");
            case "orbit" -> new Stop(
                    name,
                    bounds.maximumX() + Math.max(60.0, width * 0.30),
                    suspension - 18.0,
                    bounds.maximumZ() + Math.max(60.0, depth * 0.30),
                    135.0f,
                    -4.0f,
                    "Oblique orbit start: circle the rim, descend, and pass underneath.");
            default -> throw new IllegalArgumentException("unknown seed/scale atlas review stop: " + name);
        };
    }

    private record Stop(
            String name,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            String description) {}
}
