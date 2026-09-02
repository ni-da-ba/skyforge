package io.github.nidaba.skyforge.neoforge1211;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
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
 * <p>No active island-generation scope means BASE_WORLD. In that state all native height queries,
 * structures and biome decoration run through vanilla unchanged and Skyforge is observationally
 * absent. Only an explicit {@link SkyforgeGenerationDomainStage} island scope may expose one exact
 * compiled Skyforge volume to native generation machinery.
 */
public final class SkyforgeNoiseBasedChunkGenerator extends NoiseBasedChunkGenerator {
    public static final MapCodec<SkyforgeNoiseBasedChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                            BiomeSource.CODEC.fieldOf("biome_source").forGetter(generator -> generator.biomeSource),
                            NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(generator -> generator.settings))
                    .apply(instance, instance.stable(SkyforgeNoiseBasedChunkGenerator::new)));

    private final Holder<NoiseGeneratorSettings> settings;

    public SkyforgeNoiseBasedChunkGenerator(
            BiomeSource biomeSource,
            Holder<NoiseGeneratorSettings> settings) {
        super(biomeSource, settings);
        this.settings = settings;
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
            return super.getBaseHeight(x, z, type, level, random);
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
        if (activeIslandVolumeId.isEmpty()) {
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
        Structure structure = structureSelectionEntry.structure().value();
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
        super.buildSurface(level, structureManager, random, chunk);
        if (SkyforgeNeoForge1211SurfaceStage.hasNativeSurfaceAdaptation()) {
            SkyforgeNativeSurfaceSnapshotStage.capture(chunk);
        }
    }

    @Override
    public void applyBiomeDecoration(
            WorldGenLevel level,
            ChunkAccess chunk,
            StructureManager structureManager) {
        // BASE_WORLD completes its ordinary structure/feature/decoration stream before any Skyforge
        // block exists in the live chunk. This is the core SF-IMP-0052 isolation invariant.
        super.applyBiomeDecoration(level, chunk, structureManager);

        if (!SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()) {
            return;
        }
        if (SkyforgeNeoForge1211SurfaceStage.hasNativeSurfaceAdaptation()) {
            MinecraftNativeSurfaceSnapshot snapshot = SkyforgeNativeSurfaceSnapshotStage.consume(chunk);
            SkyforgeNeoForge1211SurfaceStage.realize(chunk, snapshot);
        } else {
            SkyforgeNeoForge1211SurfaceStage.realize(chunk);
        }
    }
}
