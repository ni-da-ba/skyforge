package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.SemanticSkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.world.SkyIslandCaveChamberGeometry;
import io.github.nidaba.skyforge.world.SkyIslandCaveVolumeField;
import io.github.nidaba.skyforge.world.SkyIslandCompiledVolumeColumnField;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandRealizedCaveVolumeField;
import io.github.nidaba.skyforge.world.SkyIslandWorldCatalog;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import io.github.nidaba.skyforge.world.WorldBounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Development-only SF-IMP-0065 sealed AUTH-0026/AUTH-0027 Minecraft realization proof. */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID)
final class SkyforgeNeoForge1211AuthoredCaveDevRuntime {
    static final String ENABLE_PROPERTY = "skyforge.dev.authoredCave";

    private static final long WORLD_SEED = 0x534B59464F524745L;
    private static final long PROVINCE_KEY = 8L;
    private static final long CLUSTER_KEY = 81L;
    private static final long ISLAND_KEY = 1439L;
    private static final long PHYSICAL_SEED = 650065L;
    private static final double SUSPENSION_Y = 236.0;
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211AuthoredCaveDevRuntime.class.getName());

    private static final Fixture FIXTURE = fixture();

    private static AutoCloseable persistentTerrainBinding;
    private static boolean proofComplete;

    private SkyforgeNeoForge1211AuthoredCaveDevRuntime() {}

    static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    static SkyIslandWorldCatalog catalog() {
        return FIXTURE.catalog();
    }

    static Fixture fixtureDefinition() {
        return FIXTURE;
    }

    static synchronized void installFromSystemProperty() {
        if (!enabled() || persistentTerrainBinding != null) {
            return;
        }
        if (SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()
                || SkyforgePhysicalVolumeAdmissionStage.active()) {
            throw new IllegalStateException(
                    "SF-IMP-0065 proof requires an isolated terrain binding without physical-admission replay");
        }
        persistentTerrainBinding = SkyforgeNeoForge1211SurfaceStage.install(
                new SkyforgeNeoForge1211ChunkAdapter(
                        FIXTURE.catalog(),
                        io.github.nidaba.skyforge.world.SkyIslandTerrainProfile.reference(),
                        new SkyforgeMinecraftBlockPalette()),
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()));

        LOGGER.log(
                System.Logger.Level.INFO,
                "Skyforge SF-IMP-0065 authored cave specimen enabled: AUTH-0026 islandKey="
                        + ISLAND_KEY + ", morphology=" + FIXTURE.authoredDescriptor().morphologyFamily()
                        + ", nominalRadius=" + FIXTURE.authoredDescriptor().nominalRadius()
                        + ", isolatedChamberRadius=" + FIXTURE.chamber().horizontalRadius()
                        + ". The AUTH-0026 chamber is centered at Minecraft X/Z=0 by explicit fixture placement; "
                        + "AUTH-0027 supplies physical Y and the existing carver fence authorizes AIR.");
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (!enabled() || proofComplete) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            observeLoaded(level);
        }
    }

    static synchronized void observeLoaded(ServerLevel level) {
        if (!enabled() || proofComplete || !level.dimension().equals(Level.OVERWORLD)) {
            return;
        }
        if (level.players().isEmpty() && !SkyforgeAutomatedAcceptanceHarness.serverMode()) {
            return;
        }

        List<LevelChunk> chunks = loadedProofChunks(level);
        if (chunks.isEmpty()) {
            return;
        }
        prove(level, chunks);
    }

    private static void prove(
            ServerLevel level,
            List<LevelChunk> chunks) {
        if (proofComplete) {
            return;
        }

        BlockPos baseControl = new BlockPos(0, Math.max(level.getMinBuildHeight() + 4, -60), 0);
        BlockState baseBefore = level.getBlockState(baseControl);

        BlockPos chamberCenter = physicalChamberCenter();
        BlockPos solidControl = physicalSolidControl();
        BlockState centerBefore = level.getBlockState(chamberCenter);
        BlockState solidBefore = level.getBlockState(solidControl);
        if (centerBefore.isAir()) {
            throw new IllegalStateException(
                    "SF-IMP-0065 authored chamber center was already AIR before authored realization: "
                            + chamberCenter);
        }
        if (solidBefore.isAir()) {
            throw new IllegalStateException(
                    "SF-IMP-0065 authored outside-cave control was already AIR before authored realization: "
                            + solidControl);
        }

        int sampledPhysicalBlocks = 0;
        int positiveAuthoredSamples = 0;
        int ownerAuthorizedSamples = 0;
        int unsafePositiveSamples = 0;
        int changedBlocks = 0;
        long changedDigest = FNV_OFFSET_BASIS;
        long provenanceDigest = FNV_OFFSET_BASIS;

        for (LevelChunk chunk : chunks) {
            var result = SkyforgeAuthoredCaveRealizer.realize(
                    level,
                    FIXTURE.volume(),
                    FIXTURE.caveField(),
                    chunk);
            if (!result.sealedAndAccepted()) {
                throw new IllegalStateException(
                        "SF-IMP-0065 authored cave failed sealed preflight in " + chunk.getPos()
                                + ": unsafe=" + result.unsafePositiveSamples()
                                + ", firstUnsafe=" + result.firstUnsafePosition());
            }
            sampledPhysicalBlocks = Math.addExact(sampledPhysicalBlocks, result.sampledPhysicalBlocks());
            positiveAuthoredSamples = Math.addExact(
                    positiveAuthoredSamples, result.positiveAuthoredSamples());
            ownerAuthorizedSamples = Math.addExact(
                    ownerAuthorizedSamples, result.ownerAuthorizedSamples());
            unsafePositiveSamples = Math.addExact(
                    unsafePositiveSamples, result.unsafePositiveSamples());
            changedBlocks = Math.addExact(changedBlocks, result.changedBlocks());
            changedDigest = mix(changedDigest, chunk.getPos().toLong());
            changedDigest = mix(changedDigest, result.changedPositionDigest());
            provenanceDigest = mix(provenanceDigest, chunk.getPos().toLong());
            provenanceDigest = mix(provenanceDigest, result.authoredProvenanceDigest());
        }

        if (positiveAuthoredSamples <= 0
                || ownerAuthorizedSamples != positiveAuthoredSamples
                || unsafePositiveSamples != 0
                || changedBlocks != positiveAuthoredSamples) {
            throw new IllegalStateException(
                    "SF-IMP-0065 authored cave realization evidence is inconsistent: positive="
                            + positiveAuthoredSamples + ", ownerAuthorized=" + ownerAuthorizedSamples
                            + ", unsafe=" + unsafePositiveSamples + ", changed=" + changedBlocks);
        }
        if (!level.getBlockState(chamberCenter).isAir()) {
            throw new IllegalStateException(
                    "SF-IMP-0065 AUTH-0026 chamber center did not realize as persistent AIR: "
                            + chamberCenter);
        }
        if (!level.getBlockState(solidControl).equals(solidBefore)) {
            throw new IllegalStateException(
                    "SF-IMP-0065 changed an AUTH-0026 outside-cave owner-solid control at "
                            + solidControl + ": before=" + solidBefore
                            + ", after=" + level.getBlockState(solidControl));
        }
        if (!level.getBlockState(baseControl).equals(baseBefore)) {
            throw new IllegalStateException(
                    "SF-IMP-0065 mutated vertically unrelated BASE_WORLD at " + baseControl
                            + ": before=" + baseBefore + ", after=" + level.getBlockState(baseControl));
        }

        var realizedField = FIXTURE.realizedField();
        var centerLocal = new io.github.nidaba.skyforge.world.SkyIslandLocalPosition(
                chamberCenter.getX() - FIXTURE.volume().compiledVolume().descriptor().centerX(),
                chamberCenter.getZ() - FIXTURE.volume().compiledVolume().descriptor().centerZ());
        var centerSample = realizedField.sample(
                new io.github.nidaba.skyforge.world.SkyIslandRealizedSubsurfacePosition(
                        centerLocal,
                        chamberCenter.getY()));
        if (!centerSample.inside()) {
            throw new IllegalStateException(
                    "SF-IMP-0065 persisted chamber-center AIR no longer corresponds to positive AUTH-0026 occupancy");
        }

        proofComplete = true;
        String changedDigestText = Long.toUnsignedString(changedDigest, 16);
        String provenanceDigestText = Long.toUnsignedString(provenanceDigest, 16);
        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0065 AUTHORED CAVE PASS: islandKey=" + ISLAND_KEY
                        + ", morphology=" + FIXTURE.authoredDescriptor().morphologyFamily()
                        + ", caveSystems=" + FIXTURE.caveField().geometry().systems().size()
                        + ", chambers=" + FIXTURE.caveField().geometry().chamberCount()
                        + ", passages=" + FIXTURE.caveField().geometry().passageCount()
                        + ", proofChunks=" + chunks.size()
                        + ", sampledPhysicalBlocks=" + sampledPhysicalBlocks
                        + ", positiveAuthoredSamples=" + positiveAuthoredSamples
                        + ", ownerAuthorizedSamples=" + ownerAuthorizedSamples
                        + ", unsafePositiveSamples=0"
                        + ", changedBlocks=" + changedBlocks
                        + ", changedDigest=" + changedDigestText
                        + ", provenanceDigest=" + provenanceDigestText
                        + ", sample=" + chamberCenter
                        + ", sampleSystem=" + centerSample.systemId()
                        + ", samplePrimitive=" + centerSample.primitiveKind()
                        + ":" + centerSample.primitiveId()
                        + ", solidControl=" + solidControl
                        + ", sealed=true, baseWorldPreserved=true.");

        SkyforgeAutomatedAcceptanceHarness.completeServerCase(
                level.getServer(),
                java.util.Map.ofEntries(
                        java.util.Map.entry("islandKey", ISLAND_KEY),
                        java.util.Map.entry("proofChunks", chunks.size()),
                        java.util.Map.entry("positiveAuthoredSamples", positiveAuthoredSamples),
                        java.util.Map.entry("ownerAuthorizedSamples", ownerAuthorizedSamples),
                        java.util.Map.entry("unsafePositiveSamples", 0),
                        java.util.Map.entry("changedBlocks", changedBlocks),
                        java.util.Map.entry("changedDigest", changedDigestText),
                        java.util.Map.entry("provenanceDigest", provenanceDigestText),
                        java.util.Map.entry("sampleCavePos", Long.toString(chamberCenter.asLong())),
                        java.util.Map.entry("sampleCaveState", level.getBlockState(chamberCenter).toString()),
                        java.util.Map.entry("sampleSystemId", centerSample.systemId()),
                        java.util.Map.entry("samplePrimitiveKind", centerSample.primitiveKind().name()),
                        java.util.Map.entry("samplePrimitiveId", centerSample.primitiveId()),
                        java.util.Map.entry("solidControlPos", Long.toString(solidControl.asLong())),
                        java.util.Map.entry("solidControlState", solidBefore.toString()),
                        java.util.Map.entry("sealed", true),
                        java.util.Map.entry("baseWorldPreserved", true)));
    }

    private static List<LevelChunk> loadedProofChunks(ServerLevel level) {
        double radius = FIXTURE.chamber().horizontalRadius() + 2.0;
        int minimumChunk = SectionPos.blockToSectionCoord((int) Math.floor(-radius));
        int maximumChunk = SectionPos.blockToSectionCoord((int) Math.ceil(radius));
        List<LevelChunk> result = new ArrayList<>();
        for (int chunkX = minimumChunk; chunkX <= maximumChunk; chunkX++) {
            for (int chunkZ = minimumChunk; chunkZ <= maximumChunk; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    return List.of();
                }
                result.add(chunk);
            }
        }
        return List.copyOf(result);
    }

    private static BlockPos physicalChamberCenter() {
        var physical = FIXTURE.realizedField().transform()
                .toPhysical(FIXTURE.chamber().center())
                .orElseThrow(() -> new IllegalStateException(
                        "SF-IMP-0065 chamber center has no AUTH-0027 physical realization"));
        double worldX = FIXTURE.volume().compiledVolume().descriptor().centerX()
                + physical.horizontalPosition().x();
        double worldZ = FIXTURE.volume().compiledVolume().descriptor().centerZ()
                + physical.horizontalPosition().z();
        return new BlockPos(
                (int) Math.round(worldX),
                (int) Math.round(physical.physicalY()),
                (int) Math.round(worldZ));
    }

    private static BlockPos physicalSolidControl() {
        SkyIslandCaveChamberGeometry chamber = FIXTURE.chamber();
        var semantic = new io.github.nidaba.skyforge.world.SkyIslandSubsurfacePosition(
                chamber.center().x() + chamber.horizontalRadius() * 1.25,
                chamber.center().z(),
                chamber.center().depthFraction());
        if (FIXTURE.caveField().contains(semantic)) {
            throw new IllegalStateException("SF-IMP-0065 solid control unexpectedly lies inside AUTH-0026 cave");
        }
        var physical = FIXTURE.realizedField().transform()
                .toPhysical(semantic)
                .orElseThrow(() -> new IllegalStateException(
                        "SF-IMP-0065 solid control has no AUTH-0027 physical realization"));
        double worldX = FIXTURE.volume().compiledVolume().descriptor().centerX()
                + semantic.x();
        double worldZ = FIXTURE.volume().compiledVolume().descriptor().centerZ()
                + semantic.z();
        return new BlockPos(
                (int) Math.round(worldX),
                (int) Math.round(physical.physicalY()),
                (int) Math.round(worldZ));
    }

    private static Fixture fixture() {
        SkyIslandDescriptor authored = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(WORLD_SEED, PROVINCE_KEY, CLUSTER_KEY, ISLAND_KEY));
        SkyIslandCaveVolumeField caveField = SkyIslandCaveVolumeField.create(authored);
        if (caveField.geometry().chamberCount() != 1
                || caveField.geometry().passageCount() != 0
                || caveField.geometry().systems().size() != 1) {
            throw new IllegalStateException(
                    "SF-IMP-0065 accepted isolated AUTH-0026 fixture changed: systems="
                            + caveField.geometry().systems().size()
                            + ", chambers=" + caveField.geometry().chamberCount()
                            + ", passages=" + caveField.geometry().passageCount());
        }
        SkyIslandCaveChamberGeometry chamber =
                caveField.geometry().systems().getFirst().chambers().getFirst();

        double centerX = -chamber.center().x();
        double centerZ = -chamber.center().z();
        double radius = authored.nominalRadius();
        var physicalDescriptor = SkyIslandVolumeDescriptor.schema2(
                PHYSICAL_SEED,
                centerX,
                centerZ,
                SUSPENSION_Y,
                radius,
                58.0,
                82.0,
                Math.min(54.0, radius * 0.18),
                0.0,
                0.28,
                0.64,
                0.0,
                authored.morphologyFamily(),
                0.12,
                30.0,
                0.20);
        var compiled = new SemanticSkyIslandVolumeRecipe().compile(physicalDescriptor);
        var id = new SkyIslandWorldVolumeId(
                WORLD_SEED,
                "sf-imp-0065-authored-cave",
                0,
                0,
                PHYSICAL_SEED);
        var bounds = new WorldBounds(
                centerX - radius * 1.08,
                centerX + radius * 1.08,
                SUSPENSION_Y - 110.0,
                SUSPENSION_Y + 80.0,
                centerZ - radius * 1.08,
                centerZ + radius * 1.08);
        var volume = new SkyIslandWorldVolume(id, bounds, compiled);
        var catalog = new SkyIslandWorldCatalog(WORLD_SEED, List.of(volume));
        var realized = new SkyIslandRealizedCaveVolumeField(
                caveField,
                new SkyIslandCompiledVolumeColumnField(compiled));
        return new Fixture(authored, caveField, chamber, volume, catalog, realized);
    }

    private static long mix(long digest, long value) {
        long mixed = digest;
        for (int shift = 0; shift < Long.SIZE; shift += Byte.SIZE) {
            mixed ^= (value >>> shift) & 0xffL;
            mixed *= FNV_PRIME;
        }
        return mixed;
    }

    record Fixture(
            SkyIslandDescriptor authoredDescriptor,
            SkyIslandCaveVolumeField caveField,
            SkyIslandCaveChamberGeometry chamber,
            SkyIslandWorldVolume volume,
            SkyIslandWorldCatalog catalog,
            SkyIslandRealizedCaveVolumeField realizedField) {}
}
