package io.github.nidaba.skyforge.neoforge1211;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.structures.WoodlandMansionStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * Vanilla noise generator with explicit Skyforge terrain-domain isolation.
 *
 * <p>No active island-generation scope means BASE_WORLD. Ordinary worlds retain vanilla behavior.
 * The opt-in alpha void-base mode suppresses base terrain and routes native content only through
 * exact-volume population. An explicit {@link SkyforgeGenerationDomainStage} island scope may expose
 * one exact compiled Skyforge volume to native generation machinery.
 */
public final class SkyforgeNoiseBasedChunkGenerator extends NoiseBasedChunkGenerator {
    public static final MapCodec<SkyforgeNoiseBasedChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                            BiomeSource.CODEC.fieldOf("biome_source").forGetter(generator -> generator.biomeSource),
                            NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(generator -> generator.settings),
                            NoiseGeneratorSettings.CODEC.optionalFieldOf("terrain_provider_settings")
                                    .forGetter(generator -> Optional.of(generator.terrainProviderSettings)),
                            com.mojang.serialization.Codec.BOOL.optionalFieldOf("void_base", false)
                                    .forGetter(generator -> generator.voidBase))
                    .apply(instance, instance.stable(SkyforgeNoiseBasedChunkGenerator::new)));

    private final Holder<NoiseGeneratorSettings> settings;
    private final Holder<NoiseGeneratorSettings> terrainProviderSettings;
    private final NoiseBasedChunkGenerator terrainProvider;
    private final boolean voidBase;
    private final ConcurrentHashMap<Long, RandomState> terrainProviderRandomStates = new ConcurrentHashMap<>();

    public SkyforgeNoiseBasedChunkGenerator(
            BiomeSource biomeSource,
            Holder<NoiseGeneratorSettings> settings) {
        this(biomeSource, settings, Optional.empty(), false);
    }

    public SkyforgeNoiseBasedChunkGenerator(
            BiomeSource biomeSource,
            Holder<NoiseGeneratorSettings> settings,
            Optional<Holder<NoiseGeneratorSettings>> terrainProviderSettings,
            boolean voidBase) {
        super(biomeSource, settings);
        this.settings = settings;
        this.terrainProviderSettings = terrainProviderSettings.orElse(settings);
        this.terrainProvider = new NoiseBasedChunkGenerator(biomeSource, this.terrainProviderSettings);
        this.voidBase = voidBase;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public int getBaseHeight(
            int x,
            int z,
            Heightmap.Types type,
            LevelHeightAccessor level,
            RandomState random) {
        var islandVolumeId = SkyforgeGenerationDomainStage.activeIslandVolumeId();
        if (islandVolumeId.isEmpty()) {
            return voidBase ? level.getMinBuildHeight() : super.getBaseHeight(x, z, type, level, random);
        }

        var skyforgeClaim = SkyforgeNeoForge1211SurfaceStage.queryBaseHeightClaim(
                islandVolumeId.orElseThrow(),
                x,
                z,
                type,
                level.getMinBuildHeight(),
                level.getHeight());
        if (skyforgeClaim.isEmpty()) {
            return level.getMinBuildHeight();
        }

        MinecraftSkyforgeHeightClaim claim = skyforgeClaim.orElseThrow();
        SkyforgeStructureCandidateStage.record(claim);
        return claim.height();
    }

    @Override
    public int getSpawnHeight(LevelHeightAccessor level) {
        if (!voidBase) {
            return super.getSpawnHeight(level);
        }
        return SkyforgeNeoForge1211SurfaceStage.querySpawnHeight(
                        level.getMinBuildHeight(),
                        level.getHeight())
                .orElseThrow(() -> new IllegalStateException(
                        "void-base Skyforge Overworld requires an admitted island surface at the origin spawn column"));
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState random) {
        if (!voidBase || SkyforgeGenerationDomainStage.activeIslandVolumeId().isPresent()) {
            return super.getBaseColumn(x, z, height, random);
        }
        BlockState[] states = new BlockState[height.getHeight()];
        Arrays.fill(states, Blocks.AIR.defaultBlockState());
        return new NoiseColumn(height.getMinBuildHeight(), states);
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(
            Blender blender,
            RandomState random,
            StructureManager structureManager,
            ChunkAccess chunk) {
        return voidBase
                ? CompletableFuture.completedFuture(chunk)
                : super.fillFromNoise(blender, random, structureManager, chunk);
    }

    private RandomState terrainProviderRandomState(WorldGenRegion level) {
        return terrainProviderRandomState(level.getSeed(), level.registryAccess());
    }

    net.minecraft.resources.ResourceLocation terrainProviderSettingsKey() {
        return terrainProviderSettings.unwrapKey()
                .map(key -> key.location())
                .orElseThrow(() -> new IllegalStateException(
                        "terrain provider settings holder is not registry keyed"));
    }

    NoiseBasedChunkGenerator terrainProviderGenerator() {
        return terrainProvider;
    }

    RandomState terrainProviderRandomState(long seed, RegistryAccess registryAccess) {
        Objects.requireNonNull(registryAccess, "registryAccess");
        return terrainProviderRandomStates.computeIfAbsent(
                seed,
                ignored -> RandomState.create(
                        terrainProviderSettings.value(),
                        registryAccess.lookupOrThrow(Registries.NOISE),
                        seed));
    }

    /**
     * Invokes Minecraft's native structure-start lifecycle for one already-admitted exact volume.
     *
     * <p>This is deliberately a package-visible adapter instead of an override of
     * {@link ChunkGenerator#createStructures}. BASE_WORLD therefore keeps Minecraft's ordinary
     * lifecycle unchanged. The inherited lifecycle retains structure-set selection, weighted
     * fallback, start construction, and its calls to this generator's
     * {@link #tryGenerateStructure(StructureSet.StructureSelectionEntry, StructureManager,
     * RegistryAccess, RandomState, StructureTemplateManager, long, ChunkAccess, ChunkPos,
     * SectionPos)} admission/support/accommodation override.
     *
     * <p>Callers must invoke this after exact terrain realization and before downstream
     * population. This seam intentionally records neither placement completion nor mutation
     * policy; DR-30 remains responsible for those later lifecycle concerns.
     */
    void createStructuresForExactSkyforgeVolume(
            RegistryAccess registryAccess,
            ChunkGeneratorStructureState structureState,
            StructureManager structureManager,
            ChunkAccess chunk,
            StructureTemplateManager structureTemplateManager,
            SkyIslandWorldVolumeId volumeId) {
        java.util.Objects.requireNonNull(registryAccess, "registryAccess");
        java.util.Objects.requireNonNull(structureState, "structureState");
        java.util.Objects.requireNonNull(structureManager, "structureManager");
        java.util.Objects.requireNonNull(chunk, "chunk");
        java.util.Objects.requireNonNull(structureTemplateManager, "structureTemplateManager");
        java.util.Objects.requireNonNull(volumeId, "volumeId");

        SkyforgeGenerationDomainStage.requireExactIslandVolume(volumeId);
        SkyforgeNeoForge1211SurfaceStage.requireCandidateVolume(volumeId, chunk);
        if (!SkyforgePhysicalVolumeAdmissionStage.allowsPopulation(volumeId)) {
            throw new IllegalStateException(
                    "native structure lifecycle requires an admitted exact Skyforge volume");
        }
        SkyforgeStructureCandidateStage.requireInactive();

        super.createStructures(
                registryAccess,
                structureState,
                structureManager,
                chunk,
                structureTemplateManager);
    }

    /**
     * Places one already-created native start after exact Skyforge terrain realization.
     *
     * <p>This is the placement half of the bounded post-admission structure lifecycle. It does not
     * replay biome decoration or rediscover/select starts. Minecraft retains piece placement through
     * {@link StructureStart#placeInChunk}; Skyforge supplies only the exact-volume write fence, the
     * same structure-step random seed vanilla decoration would use, and the target-chunk writable
     * area. Completion/idempotence remains a caller-owned DR-30 concern.
     */
    void placeStructureStartForExactSkyforgeVolume(
            WorldGenLevel level,
            ChunkAccess chunk,
            StructureManager structureManager,
            StructureStart start,
            SkyIslandWorldVolumeId volumeId) {
        java.util.Objects.requireNonNull(level, "level");
        java.util.Objects.requireNonNull(chunk, "chunk");
        java.util.Objects.requireNonNull(structureManager, "structureManager");
        java.util.Objects.requireNonNull(start, "start");
        java.util.Objects.requireNonNull(volumeId, "volumeId");
        if (!start.isValid()) {
            throw new IllegalArgumentException("native structure placement requires a valid StructureStart");
        }

        SkyforgeGenerationDomainStage.requireExactIslandVolume(volumeId);
        SkyforgeNeoForge1211SurfaceStage.requireCandidateVolume(volumeId, chunk);
        if (!SkyforgePhysicalVolumeAdmissionStage.allowsPopulation(volumeId)) {
            throw new IllegalStateException(
                    "native structure placement requires an admitted exact Skyforge volume");
        }
        BoundingBox writableArea = writableArea(chunk);
        if (!start.getBoundingBox().intersects(writableArea)) {
            throw new IllegalArgumentException(
                    "native structure start does not intersect its target chunk writable area");
        }

        var structureRegistry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        List<Structure> stepStructures = structureRegistry.stream()
                .filter(structure -> structure.step() == start.getStructure().step())
                .toList();
        int structureIndex = stepStructures.indexOf(start.getStructure());
        if (structureIndex < 0) {
            throw new IllegalStateException("native structure start is not registered in its generation step");
        }

        SectionPos sectionPos = SectionPos.of(chunk.getPos(), level.getMinSection());
        BlockPos origin = sectionPos.origin();
        WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(RandomSupport.generateUniqueSeed()));
        long decorationSeed = random.setDecorationSeed(level.getSeed(), origin.getX(), origin.getZ());
        random.setFeatureSeed(decorationSeed, structureIndex, start.getStructure().step().ordinal());

        SkyforgeDeferredPopulationPostProcessingBridge.Scope postProcessing = null;
        if (level instanceof ServerLevel serverLevel && chunk instanceof LevelChunk levelChunk) {
            if (levelChunk.getLevel() != serverLevel) {
                throw new IllegalArgumentException("stable native structure target belongs to another level");
            }
            postProcessing = SkyforgeDeferredPopulationPostProcessingBridge.open(serverLevel);
        }
        var placement = SkyforgeStructurePlacementExecutionStage.open(volumeId, start.getBoundingBox());
        try {
            level.setCurrentlyGenerating(() -> "Skyforge exact-volume structure "
                    + structureRegistry.getKey(start.getStructure()));
            start.placeInChunk(level, structureManager, this, random, writableArea, chunk.getPos());
            SkyforgeDeferredPopulationPostProcessingBridge.flushIfActive();
        } finally {
            placement.close();
            level.setCurrentlyGenerating(null);
            if (postProcessing != null) {
                postProcessing.close();
            }
        }
    }

    private static BoundingBox writableArea(ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        LevelHeightAccessor height = chunk.getHeightAccessorForGeneration();
        return new BoundingBox(
                minX,
                height.getMinBuildHeight() + 1,
                minZ,
                minX + 15,
                height.getMaxBuildHeight() - 1,
                minZ + 15);
    }

    /**
     * Wraps a native structure candidate only inside an explicit exact-island generation scope.
     *
     * <p>Ordinary base-world candidates delegate directly to vanilla and never see Skyforge height,
     * support or contradiction policy. The accepted admission/accommodation machinery remains
     * available for the later island-owned structure population pass without coupling the base
     * world back to suspended terrain.
     */
    @Override
    protected boolean tryGenerateStructure(
            StructureSet.StructureSelectionEntry structureSelectionEntry,
            StructureManager structureManager,
            RegistryAccess registryAccess,
            RandomState random,
            StructureTemplateManager structureTemplateManager,
            long seed,
            ChunkAccess chunk,
            ChunkPos chunkPos,
            SectionPos sectionPos) {
        var activeIslandVolumeId = SkyforgeGenerationDomainStage.activeIslandVolumeId();
        Structure structure = structureSelectionEntry.structure().value();
        if (activeIslandVolumeId.isEmpty()) {
            if (voidBase || SkyforgeDr30NativeStructureAcceptance.suppressBaseWorldProbe(structure, chunkPos)) {
                return false;
            }
            return super.tryGenerateStructure(
                    structureSelectionEntry,
                    structureManager,
                    registryAccess,
                    random,
                    structureTemplateManager,
                    seed,
                    chunk,
                    chunkPos,
                    sectionPos);
        }
        if (!SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()) {
            throw new IllegalStateException("island generation domain opened without an active Skyforge runtime binding");
        }

        SkyIslandWorldVolumeId domainVolumeId = activeIslandVolumeId.orElseThrow();
        if (!SkyforgeDr30NativeStructureAcceptance.allowsExactProbe(structure, chunkPos, domainVolumeId)) {
            return false;
        }
        boolean dr30ProbeCandidate = SkyforgeDr30NativeStructureAcceptance.isProbeCandidate(structure, chunkPos);
        boolean accommodationProofCandidate = isAccommodationProofCandidate(structure, chunkPos);
        boolean undersideContradictionProofCandidate =
                SkyforgeNeoForge1211UndersideContradictionDevRuntime.isProofCandidate(structure, chunkPos);
        var previousStarts = new HashMap<>(chunk.getAllStarts());
        boolean generated;
        List<MinecraftSkyforgeHeightClaim> heightClaims;
        try (SkyforgeStructureCandidateStage.Scope scope = SkyforgeStructureCandidateStage.open()) {
            generated = super.tryGenerateStructure(
                    structureSelectionEntry,
                    structureManager,
                    registryAccess,
                    random,
                    structureTemplateManager,
                    seed,
                    chunk,
                    chunkPos,
                    sectionPos);
            heightClaims = scope.claims();
        }

        if (!generated || heightClaims.isEmpty()) {
            if (dr30ProbeCandidate) {
                throw SkyforgeDr30NativeStructureAcceptance.exactProbeFailure(
                        structure,
                        chunkPos,
                        domainVolumeId,
                        "generated=" + generated + ", heightClaims=" + heightClaims.size());
            }
            if (accommodationProofCandidate) {
                throw new IllegalStateException(
                        "SF-IMP-0046 fixture invalid: forced origin mansion did not produce a Skyforge-height native start");
            }
            if (undersideContradictionProofCandidate) {
                throw new IllegalStateException(
                        "SF-IMP-0050 fixture invalid: forced origin mansion did not produce a Skyforge-height native start");
            }
            return generated;
        }

        StructureStart start = chunk.getStartForStructure(structure);
        if (start == null || !start.isValid()) {
            if (dr30ProbeCandidate) {
                throw SkyforgeDr30NativeStructureAcceptance.exactProbeFailure(
                        structure, chunkPos, domainVolumeId, "native start missing or invalid");
            }
            if (accommodationProofCandidate) {
                throw new IllegalStateException(
                        "SF-IMP-0046 fixture invalid: forced origin mansion produced no valid StructureStart");
            }
            if (undersideContradictionProofCandidate) {
                throw new IllegalStateException(
                        "SF-IMP-0050 fixture invalid: forced origin mansion produced no valid StructureStart");
            }
            chunk.setAllStarts(previousStarts);
            return false;
        }

        List<MinecraftSkyforgeHeightClaim> resolvedClaims = heightClaims.stream()
                .filter(claim -> claimResolvesSurfacePlane(start.getBoundingBox(), claim))
                .toList();
        if (resolvedClaims.isEmpty()) {
            if (dr30ProbeCandidate) {
                throw SkyforgeDr30NativeStructureAcceptance.exactProbeFailure(
                        structure,
                        chunkPos,
                        domainVolumeId,
                        "start did not resolve at claimed surface; bounds=" + start.getBoundingBox()
                                + ", claims=" + heightClaims);
            }
            if (accommodationProofCandidate) {
                throw new IllegalStateException(
                        "SF-IMP-0046 fixture invalid: forced origin mansion did not resolve its start at the claimed "
                                + "Skyforge surface; bounds=" + start.getBoundingBox() + ", claims=" + heightClaims);
            }
            if (undersideContradictionProofCandidate) {
                throw new IllegalStateException(
                        "SF-IMP-0050 fixture invalid: forced origin mansion did not resolve its start at the claimed "
                                + "Skyforge surface; bounds=" + start.getBoundingBox() + ", claims=" + heightClaims);
            }
            return generated;
        }

        Set<SkyIslandWorldVolumeId> claimedVolumeIds = new LinkedHashSet<>();
        resolvedClaims.forEach(claim -> claimedVolumeIds.addAll(claim.volumeIds()));
        if (claimedVolumeIds.size() != 1 || !claimedVolumeIds.contains(domainVolumeId)) {
            if (dr30ProbeCandidate) {
                throw SkyforgeDr30NativeStructureAcceptance.exactProbeFailure(
                        structure,
                        chunkPos,
                        domainVolumeId,
                        "resolved claims referenced volumes=" + claimedVolumeIds);
            }
            chunk.setAllStarts(previousStarts);
            return false;
        }

        int resolvedFirstFreeY = resolvedClaims.stream()
                .filter(claim -> claim.volumeIds().contains(domainVolumeId))
                .mapToInt(MinecraftSkyforgeHeightClaim::height)
                .max()
                .orElseThrow();
        int structureFloorY = start.getBoundingBox().minY();

        List<BoundingBox> contradictionPieceBoxes =
                SkyforgeNeoForge1211UndersideContradictionDevRuntime.candidatePieceBoxes(start, structure, chunkPos);
        var undersideContradiction = MinecraftStructureUndersideContradictionPolicy.evaluate(
                contradictionPieceBoxes,
                structureFloorY,
                domainVolumeId);
        if (undersideContradiction.isPresent()) {
            if (dr30ProbeCandidate) {
                throw SkyforgeDr30NativeStructureAcceptance.exactProbeFailure(
                        structure,
                        chunkPos,
                        domainVolumeId,
                        "underside contradiction=" + undersideContradiction.orElseThrow());
            }
            if (undersideContradictionProofCandidate) {
                SkyforgeNeoForge1211UndersideContradictionDevRuntime.recordRejected(
                        start.getBoundingBox(),
                        undersideContradiction.orElseThrow());
            }
            chunk.setAllStarts(previousStarts);
            return false;
        }
        if (undersideContradictionProofCandidate) {
            SkyforgeNeoForge1211UndersideContradictionDevRuntime.requireContradiction(
                    start.getBoundingBox(),
                    domainVolumeId);
        }

        List<BoundingBox> supportBoxes = MinecraftStructureSupportGeometry.floorContactBoxes(start);
        var naturalRequirements = MinecraftStructureSupportPolicy.requirements(supportBoxes);
        boolean naturallyAccepted = SkyforgeNeoForge1211SurfaceStage.assessSurfaceSupport(naturalRequirements)
                .orElseThrow(() -> new IllegalStateException("active Skyforge binding disappeared during structure generation"))
                .stream()
                .filter(assessment -> assessment.supportingVolumeId().equals(domainVolumeId))
                .findFirst()
                .map(assessment -> assessment.accepted())
                .orElse(false);
        if (naturallyAccepted) {
            if (accommodationProofCandidate) {
                SkyforgeNeoForge1211AccommodationDevRuntime.requireNaturalRejection(start.getBoundingBox());
            }
            return true;
        }

        var foundationRequirements = MinecraftStructureSupportPolicy.foundationRequirements(
                supportBoxes,
                structureFloorY,
                resolvedFirstFreeY);
        var foundationAssessment = SkyforgeNeoForge1211SurfaceStage.assessSurfaceFoundation(foundationRequirements)
                .orElseThrow(() -> new IllegalStateException("active Skyforge binding disappeared during structure generation"))
                .stream()
                .filter(assessment -> assessment.supportingVolumeId().equals(domainVolumeId))
                .findFirst();
        if (foundationAssessment.isEmpty() || !foundationAssessment.orElseThrow().accepted()) {
            if (dr30ProbeCandidate) {
                throw SkyforgeDr30NativeStructureAcceptance.exactProbeFailure(
                        structure,
                        chunkPos,
                        domainVolumeId,
                        "natural support rejected and bounded foundation accommodation was not accepted");
            }
            if (accommodationProofCandidate) {
                SkyforgeNeoForge1211AccommodationDevRuntime.requireFoundationAcceptance(start.getBoundingBox());
            }
            chunk.setAllStarts(previousStarts);
            return false;
        }

        var acceptedFoundation = foundationAssessment.orElseThrow();
        int maximumFillDepth = Math.max(
                1,
                (int) Math.ceil(acceptedFoundation.maximumRequiredFillDepth()));
        SkyforgeFoundationPiece foundation = new SkyforgeFoundationPiece(
                supportBoxes,
                structureFloorY,
                domainVolumeId,
                maximumFillDepth);
        List<StructurePiece> pieces = new ArrayList<>(start.getPieces().size() + 1);
        pieces.add(foundation);
        pieces.addAll(start.getPieces());
        StructureStart accommodatedStart = new StructureStart(
                structure,
                start.getChunkPos(),
                start.getReferences(),
                new PiecesContainer(List.copyOf(pieces)));
        structureManager.setStartForStructure(sectionPos, structure, accommodatedStart, chunk);
        if (accommodationProofCandidate) {
            SkyforgeNeoForge1211AccommodationDevRuntime.recordFoundationAttached(
                    start.getBoundingBox(),
                    domainVolumeId,
                    acceptedFoundation.maximumRequiredFillDepth());
        }
        return true;
    }

    static boolean claimResolvesSurfacePlane(BoundingBox bounds, MinecraftSkyforgeHeightClaim claim) {
        long delta = (long) claim.height() - bounds.minY();
        return delta >= -1L && delta <= 1L;
    }

    private static boolean isAccommodationProofCandidate(Structure structure, ChunkPos chunkPos) {
        return SkyforgeNeoForge1211AccommodationDevRuntime.enabled()
                && structure instanceof WoodlandMansionStructure
                && chunkPos.x == 0
                && chunkPos.z == 0;
    }

    @Override
    public void buildSurface(
            WorldGenRegion level,
            StructureManager structureManager,
            RandomState random,
            ChunkAccess chunk) {
        if (!voidBase) {
            super.buildSurface(level, structureManager, random, chunk);
        }
        if (SkyforgeNeoForge1211SurfaceStage.hasNativeSurfaceAdaptation()
                && SkyforgeNeoForge1211SurfaceStage.hasCandidateVolume(chunk)) {
            if (voidBase) {
                SkyforgeNativeSurfaceSnapshotStage.capture(
                        level,
                        structureManager,
                        chunk,
                        terrainProvider,
                        terrainProviderRandomState(level));
            } else {
                SkyforgeNativeSurfaceSnapshotStage.capture(chunk, getSeaLevel());
            }
        }
    }

    @Override
    public void applyBiomeDecoration(
            WorldGenLevel level,
            ChunkAccess chunk,
            StructureManager structureManager) {
        // In alpha void-base mode, native content is run only through exact-volume population
        // after Skyforge terrain realization. Ordinary BASE_WORLD decoration sees no land.
        if (!voidBase) {
            // BASE_WORLD completes its ordinary structure/feature/decoration stream before any Skyforge
            // block exists in the live chunk. This is the core SF-IMP-0052 isolation invariant.
            super.applyBiomeDecoration(level, chunk, structureManager);
        }

        if (!SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()
                || !SkyforgeNeoForge1211SurfaceStage.hasCandidateVolume(chunk)) {
            return;
        }
        if (SkyforgeNeoForge1211SurfaceStage.hasNativeSurfaceAdaptation()) {
            MinecraftNativeSurfaceSnapshot snapshot;
            if (voidBase) {
                if (!(level instanceof WorldGenRegion worldGenRegion)) {
                    throw new IllegalStateException(
                            "void-base native surface recovery requires an active worldgen region");
                }
                snapshot = SkyforgeNativeSurfaceSnapshotStage.consumeOrCaptureProviderSurface(
                        worldGenRegion,
                        structureManager,
                        chunk,
                        terrainProvider,
                        terrainProviderRandomState(level.getSeed(), level.registryAccess()));
            } else {
                snapshot = SkyforgeNativeSurfaceSnapshotStage.consume(chunk);
            }
            SkyforgeNeoForge1211SurfaceStage.realize(chunk, snapshot);
        } else {
            SkyforgeNeoForge1211SurfaceStage.realize(chunk);
        }

        // Development-only SF-IMP-0053 proof. The runtime is inert unless its explicit JVM
        // property is enabled; production population orchestration will replace this fixture seam.
        SkyforgeNeoForge1211PopulationDevRuntime.populate(level, chunk, this);
    }
}
