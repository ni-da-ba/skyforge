package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.SemanticSkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.world.SkyIslandCaveExposureConnectionGeometry;
import io.github.nidaba.skyforge.world.SkyIslandCaveExposureSide;
import io.github.nidaba.skyforge.world.SkyIslandCompiledVolumeColumnField;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandExteriorConnectedCaveVolumeField;
import io.github.nidaba.skyforge.world.SkyIslandExteriorConnectedCaveVolumeSample;
import io.github.nidaba.skyforge.world.SkyIslandLocalPosition;
import io.github.nidaba.skyforge.world.SkyIslandRealizedExteriorConnectedCaveVolumeField;
import io.github.nidaba.skyforge.world.SkyIslandRealizedSubsurfacePosition;
import io.github.nidaba.skyforge.world.SkyIslandWorldCatalog;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import io.github.nidaba.skyforge.world.WorldBounds;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Development-only SF-IMP-0066 AUTH-0030 exterior-connected cave realization proof. */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID)
final class SkyforgeNeoForge1211ExteriorConnectedCaveDevRuntime {
    static final String ENABLE_PROPERTY = "skyforge.dev.exteriorConnectedCave";

    private static final long WORLD_SEED = 0x534B59464F524745L;
    private static final long PROVINCE_KEY = 8L;
    private static final long CLUSTER_KEY = 81L;
    private static final long PHYSICAL_SEED = 660066L;
    private static final double SUSPENSION_Y = 236.0;
    private static final List<Long> CANONICAL_KEYS =
            List.of(2332L, 653L, 1051L, 2211L, 1439L, 3670L);
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211ExteriorConnectedCaveDevRuntime.class.getName());

    private static final Fixture FIXTURE = fixture();

    private static AutoCloseable persistentTerrainBinding;
    private static boolean proofComplete;

    private SkyforgeNeoForge1211ExteriorConnectedCaveDevRuntime() {}

    static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
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
                    "SF-IMP-0066 proof requires an isolated terrain binding without admission replay");
        }
        persistentTerrainBinding = SkyforgeNeoForge1211SurfaceStage.install(
                new SkyforgeNeoForge1211ChunkAdapter(
                        FIXTURE.catalog(),
                        io.github.nidaba.skyforge.world.SkyIslandTerrainProfile.reference(),
                        new SkyforgeMinecraftBlockPalette()),
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()));

        LOGGER.log(
                System.Logger.Level.INFO,
                "Skyforge SF-IMP-0066 AUTH-0030 specimen enabled: islandKey="
                        + FIXTURE.islandKey()
                        + ", morphology=" + FIXTURE.descriptor().morphologyFamily()
                        + ", exposureSide=" + FIXTURE.connection().side()
                        + ", connectionPoints=" + FIXTURE.connection().points().size()
                        + ", proofChunks=" + FIXTURE.proofChunks().size()
                        + ". The selected canonical AUTH-0030 connection is translated near spawn; "
                        + "the backend samples the accepted union field and never reconstructs corridor geometry.");
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (!enabled() || proofComplete) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (level.dimension().equals(Level.OVERWORLD)) {
                observeLoaded(level);
            }
        }
    }

    private static synchronized void observeLoaded(ServerLevel level) {
        if (proofComplete) {
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
        int positive = 0;
        int basePositive = 0;
        int exposurePositive = 0;
        int upperExposurePositive = 0;
        int undersideExposurePositive = 0;
        int unsafe = 0;
        int mouthCells = 0;
        int changed = 0;
        long changedDigest = FNV_OFFSET_BASIS;
        long provenanceDigest = FNV_OFFSET_BASIS;
        BlockPos firstMouth = null;
        SkyIslandCaveExposureSide mouthSide = null;

        for (LevelChunk chunk : chunks) {
            var result = SkyforgeExteriorConnectedCaveRealizer.realize(
                    level,
                    FIXTURE.volume(),
                    FIXTURE.field(),
                    chunk);
            if (!result.accepted()) {
                throw new IllegalStateException(
                        "SF-IMP-0066 AUTH-0030 target chunk failed owner preflight: chunk="
                                + chunk.getPos() + ", unsafe=" + result.unsafePositiveSamples()
                                + ", firstUnsafe=" + result.firstUnsafePosition());
            }
            positive = Math.addExact(positive, result.positiveSamples());
            basePositive = Math.addExact(basePositive, result.basePositiveSamples());
            exposurePositive = Math.addExact(exposurePositive, result.exposurePositiveSamples());
            upperExposurePositive = Math.addExact(
                    upperExposurePositive, result.upperExposureSamples());
            undersideExposurePositive = Math.addExact(
                    undersideExposurePositive, result.undersideExposureSamples());
            unsafe = Math.addExact(unsafe, result.unsafePositiveSamples());
            mouthCells = Math.addExact(mouthCells, result.mouthCells());
            changed = Math.addExact(changed, result.changedBlocks());
            changedDigest = mix(changedDigest, chunk.getPos().toLong());
            changedDigest = mix(changedDigest, result.changedPositionDigest());
            provenanceDigest = mix(provenanceDigest, chunk.getPos().toLong());
            provenanceDigest = mix(provenanceDigest, result.provenanceDigest());
            if (firstMouth == null && result.firstMouthPosition() != null) {
                firstMouth = result.firstMouthPosition();
                mouthSide = result.firstMouthSide();
            }
        }

        if (positive <= 0
                || basePositive <= 0
                || exposurePositive <= 0
                || unsafe != 0
                || mouthCells <= 0
                || upperExposurePositive + undersideExposurePositive != exposurePositive
                || (mouthSide == SkyIslandCaveExposureSide.UPPER_SURFACE
                        ? undersideExposurePositive != 0
                        : upperExposurePositive != 0)
                || changed != positive
                || firstMouth == null
                || mouthSide == null) {
            throw new IllegalStateException(
                    "SF-IMP-0066 exterior-connected cave evidence incomplete: positive=" + positive
                            + ", base=" + basePositive + ", exposure=" + exposurePositive
                            + ", unsafe=" + unsafe + ", mouthCells=" + mouthCells
                            + ", changed=" + changed + ", firstMouth=" + firstMouth);
        }

        BlockPos outward = mouthSide == SkyIslandCaveExposureSide.UPPER_SURFACE
                ? firstMouth.above()
                : firstMouth.below();
        if (!level.getBlockState(firstMouth).isAir()) {
            throw new IllegalStateException("SF-IMP-0066 mouth owner cell did not realize as AIR: " + firstMouth);
        }
        if (!level.getBlockState(outward).isAir()) {
            throw new IllegalStateException(
                    "SF-IMP-0066 accepted mouth is not contiguous with exterior Minecraft AIR: mouth="
                            + firstMouth + ", side=" + mouthSide
                            + ", outward=" + outward + ", outwardState=" + level.getBlockState(outward));
        }

        Connectivity connectivity = verifyConnectivity(level, firstMouth, chunks);
        if (!connectivity.reachedBaseCave() || connectivity.basePosition() == null) {
            throw new IllegalStateException(
                    "SF-IMP-0066 mouth AIR component did not reach AUTH-0030 BASE_CAVE provenance");
        }

        proofComplete = true;
        String changedDigestText = Long.toUnsignedString(changedDigest, 16);
        String provenanceDigestText = Long.toUnsignedString(provenanceDigest, 16);

        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0066 EXTERIOR CAVE PASS: islandKey=" + FIXTURE.islandKey()
                        + ", morphology=" + FIXTURE.descriptor().morphologyFamily()
                        + ", exposureSide=" + mouthSide
                        + ", proofChunks=" + chunks.size()
                        + ", positiveSamples=" + positive
                        + ", basePositiveSamples=" + basePositive
                        + ", exposurePositiveSamples=" + exposurePositive
                        + ", upperExposurePositiveSamples=" + upperExposurePositive
                        + ", undersideExposurePositiveSamples=" + undersideExposurePositive
                        + ", unsafePositiveSamples=0"
                        + ", mouthCells=" + mouthCells
                        + ", changedBlocks=" + changed
                        + ", changedDigest=" + changedDigestText
                        + ", provenanceDigest=" + provenanceDigestText
                        + ", mouth=" + firstMouth
                        + ", outwardExteriorAir=" + outward
                        + ", componentVisited=" + connectivity.visited()
                        + ", componentBase=" + connectivity.basePosition()
                        + ", componentReachedBase=true"
                        + ", exactOwnerOnly=true.");

        SkyforgeAutomatedAcceptanceHarness.completeServerCase(
                level.getServer(),
                java.util.Map.ofEntries(
                        java.util.Map.entry("islandKey", FIXTURE.islandKey()),
                        java.util.Map.entry("exposureSide", mouthSide.name()),
                        java.util.Map.entry("proofChunks", chunks.size()),
                        java.util.Map.entry("positiveSamples", positive),
                        java.util.Map.entry("basePositiveSamples", basePositive),
                        java.util.Map.entry("exposurePositiveSamples", exposurePositive),
                        java.util.Map.entry("upperExposurePositiveSamples", upperExposurePositive),
                        java.util.Map.entry("undersideExposurePositiveSamples", undersideExposurePositive),
                        java.util.Map.entry("unsafePositiveSamples", 0),
                        java.util.Map.entry("mouthCells", mouthCells),
                        java.util.Map.entry("changedBlocks", changed),
                        java.util.Map.entry("changedDigest", changedDigestText),
                        java.util.Map.entry("provenanceDigest", provenanceDigestText),
                        java.util.Map.entry("mouthPos", Long.toString(firstMouth.asLong())),
                        java.util.Map.entry("mouthState", level.getBlockState(firstMouth).toString()),
                        java.util.Map.entry("outwardPos", Long.toString(outward.asLong())),
                        java.util.Map.entry("outwardState", level.getBlockState(outward).toString()),
                        java.util.Map.entry("componentReachedBase", true),
                        java.util.Map.entry("componentVisited", connectivity.visited()),
                        java.util.Map.entry("baseCavePos", Long.toString(connectivity.basePosition().asLong())),
                        java.util.Map.entry("baseCaveState", level.getBlockState(connectivity.basePosition()).toString()),
                        java.util.Map.entry("exactOwnerOnly", true)));
    }

    private static Connectivity verifyConnectivity(
            ServerLevel level,
            BlockPos mouth,
            List<LevelChunk> chunks) {
        Set<Long> allowedChunks = new HashSet<>();
        for (LevelChunk chunk : chunks) {
            allowedChunks.add(chunk.getPos().toLong());
        }
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        queue.add(mouth.immutable());

        var realized = new SkyIslandRealizedExteriorConnectedCaveVolumeField(
                FIXTURE.field(),
                new SkyIslandCompiledVolumeColumnField(FIXTURE.volume().compiledVolume()));
        var descriptor = FIXTURE.volume().compiledVolume().descriptor();
        BlockPos basePosition = null;

        while (!queue.isEmpty()) {
            BlockPos position = queue.removeFirst();
            if (!visited.add(position.asLong())) {
                continue;
            }
            long chunkKey = new net.minecraft.world.level.ChunkPos(position).toLong();
            if (!allowedChunks.contains(chunkKey) || !level.getBlockState(position).isAir()) {
                continue;
            }

            SkyIslandLocalPosition local = new SkyIslandLocalPosition(
                    position.getX() - descriptor.centerX(),
                    position.getZ() - descriptor.centerZ());
            SkyIslandExteriorConnectedCaveVolumeSample sample = realized.sample(
                    new SkyIslandRealizedSubsurfacePosition(local, position.getY()));
            if (!sample.inside()) {
                continue;
            }
            if (sample.sourceKind()
                    == SkyIslandExteriorConnectedCaveVolumeSample.SourceKind.BASE_CAVE) {
                basePosition = position.immutable();
                break;
            }

            queue.add(position.above());
            queue.add(position.below());
            queue.add(position.north());
            queue.add(position.south());
            queue.add(position.east());
            queue.add(position.west());
        }
        return new Connectivity(basePosition != null, visited.size(), basePosition);
    }

    private static List<LevelChunk> loadedProofChunks(ServerLevel level) {
        List<LevelChunk> result = new ArrayList<>();
        for (long packed : FIXTURE.proofChunks()) {
            var pos = new net.minecraft.world.level.ChunkPos(packed);
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
            if (chunk == null) {
                return List.of();
            }
            result.add(chunk);
        }
        return List.copyOf(result);
    }

    private static Fixture fixture() {
        CandidateSelection selected = selectConnection();
        SkyIslandDescriptor descriptor = selected.descriptor();
        SkyIslandExteriorConnectedCaveVolumeField field = selected.field();
        SkyIslandCaveExposureConnectionGeometry connection = selected.connection();

        double minimumX = Double.POSITIVE_INFINITY;
        double maximumX = Double.NEGATIVE_INFINITY;
        double minimumZ = Double.POSITIVE_INFINITY;
        double maximumZ = Double.NEGATIVE_INFINITY;
        double maximumRadius = 0.0;
        for (var point : connection.points()) {
            minimumX = Math.min(minimumX, point.position().x());
            maximumX = Math.max(maximumX, point.position().x());
            minimumZ = Math.min(minimumZ, point.position().z());
            maximumZ = Math.max(maximumZ, point.position().z());
            maximumRadius = Math.max(maximumRadius, point.horizontalRadius());
        }
        double localMidX = 0.5 * (minimumX + maximumX);
        double localMidZ = 0.5 * (minimumZ + maximumZ);
        double centerX = -localMidX;
        double centerZ = -localMidZ;
        double radius = descriptor.nominalRadius();

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
                descriptor.morphologyFamily(),
                0.12,
                30.0,
                0.20);
        var compiled = new SemanticSkyIslandVolumeRecipe().compile(physicalDescriptor);
        var id = new SkyIslandWorldVolumeId(
                WORLD_SEED,
                "sf-imp-0066-exterior-connected-cave",
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

        double pad = maximumRadius + 3.0;
        int worldMinX = (int) Math.floor(centerX + minimumX - pad);
        int worldMaxX = (int) Math.ceil(centerX + maximumX + pad);
        int worldMinZ = (int) Math.floor(centerZ + minimumZ - pad);
        int worldMaxZ = (int) Math.ceil(centerZ + maximumZ + pad);

        List<Long> proofChunks = new ArrayList<>();
        for (int chunkX = SectionPos.blockToSectionCoord(worldMinX);
                chunkX <= SectionPos.blockToSectionCoord(worldMaxX);
                chunkX++) {
            for (int chunkZ = SectionPos.blockToSectionCoord(worldMinZ);
                    chunkZ <= SectionPos.blockToSectionCoord(worldMaxZ);
                    chunkZ++) {
                proofChunks.add(new net.minecraft.world.level.ChunkPos(chunkX, chunkZ).toLong());
            }
        }
        if (proofChunks.size() > 121) {
            throw new IllegalStateException(
                    "SF-IMP-0066 selected canonical exposure connection requires too many proof chunks: "
                            + proofChunks.size());
        }

        return new Fixture(
                selected.islandKey(),
                descriptor,
                field,
                connection,
                volume,
                catalog,
                List.copyOf(proofChunks));
    }

    private static CandidateSelection selectConnection() {
        CandidateSelection best = null;
        double bestSpan = Double.POSITIVE_INFINITY;
        for (long key : CANONICAL_KEYS) {
            SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                    SkyIslandIdentity.of(WORLD_SEED, PROVINCE_KEY, CLUSTER_KEY, key));
            SkyIslandExteriorConnectedCaveVolumeField field =
                    SkyIslandExteriorConnectedCaveVolumeField.create(descriptor);
            if (field.exposureGeometry().connections().isEmpty()) {
                continue;
            }
            for (SkyIslandCaveExposureConnectionGeometry connection
                    : field.exposureGeometry().connections()) {
                double minX = Double.POSITIVE_INFINITY;
                double maxX = Double.NEGATIVE_INFINITY;
                double minZ = Double.POSITIVE_INFINITY;
                double maxZ = Double.NEGATIVE_INFINITY;
                for (var point : connection.points()) {
                    minX = Math.min(minX, point.position().x());
                    maxX = Math.max(maxX, point.position().x());
                    minZ = Math.min(minZ, point.position().z());
                    maxZ = Math.max(maxZ, point.position().z());
                }
                double span = Math.max(maxX - minX, maxZ - minZ);
                if (span < bestSpan) {
                    bestSpan = span;
                    best = new CandidateSelection(key, descriptor, field, connection);
                }
            }
        }
        if (best == null) {
            throw new IllegalStateException(
                    "SF-IMP-0066 canonical AUTH-0030 corpus contains no accepted exposure connection");
        }
        return best;
    }

    private static long mix(long digest, long value) {
        long mixed = digest;
        for (int shift = 0; shift < Long.SIZE; shift += Byte.SIZE) {
            mixed ^= (value >>> shift) & 0xffL;
            mixed *= FNV_PRIME;
        }
        return mixed;
    }

    private record Connectivity(
            boolean reachedBaseCave,
            int visited,
            BlockPos basePosition) {}

    private record CandidateSelection(
            long islandKey,
            SkyIslandDescriptor descriptor,
            SkyIslandExteriorConnectedCaveVolumeField field,
            SkyIslandCaveExposureConnectionGeometry connection) {}

    record Fixture(
            long islandKey,
            SkyIslandDescriptor descriptor,
            SkyIslandExteriorConnectedCaveVolumeField field,
            SkyIslandCaveExposureConnectionGeometry connection,
            SkyIslandWorldVolume volume,
            SkyIslandWorldCatalog catalog,
            List<Long> proofChunks) {}
}
