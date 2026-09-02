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
 * Vanilla noise generator with supported Skyforge early-query, structure-admission, post-surface
 * and supplemental feature seams.
 *
 * <p>All vanilla structure selection, noise, surface and biome-decoration behavior is retained.
 * Skyforge intervenes only when its early height answer actually elevates and vertically resolves a
 * native structure start. Resolved starts are assessed from generic native-piece geometry and exact
 * Skyforge terrain provenance, never from structure identity or a per-structure policy table.
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
        int vanillaHeight = super.getBaseHeight(x, z, type, level, random);
        var skyforgeClaim = SkyforgeNeoForge1211SurfaceStage.queryBaseHeightClaim(
                x,
                z,
                type,
                level.getMinBuildHeight(),
                level.getHeight());
        if (skyforgeClaim.isEmpty()) {
            return vanillaHeight;
        }

        MinecraftSkyforgeHeightClaim claim = skyforgeClaim.orElseThrow();
        if (claim.height() > vanillaHeight) {
            SkyforgeStructureCandidateStage.record(claim);
            return claim.height();
        }
        return vanillaHeight;
    }

    /**
     * Wraps one vanilla structure candidate after NeoForge widens the otherwise-private method.
     *
     * <p>Vanilla still chooses structure sets, weights and alternatives. A Skyforge height query is
     * treated only as provenance evidence, not as proof that the native start has already resolved
     * its vertical placement. Admission/accommodation therefore runs only when an actual start floor
     * is coincident with a claimed Skyforge first-free height (allowing one block for discrete
     * surface conventions). Once that plane and exactly one supporting island are proven, Skyforge
     * may reject only positive physical contradictions before evaluating ordinary support and bounded
     * fill accommodation. Rejection always restores the pre-candidate start map and returns false so
     * Minecraft's normal weighted fallback remains authoritative.
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
        if (!SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()) {
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
        if (claimedVolumeIds.size() != 1) {
            if (accommodationProofCandidate) {
                throw new IllegalStateException(
                        "SF-IMP-0046 fixture invalid: forced origin mansion claimed multiple resolved Skyforge volumes: "
                                + claimedVolumeIds);
            }
            if (undersideContradictionProofCandidate) {
                throw new IllegalStateException(
                        "SF-IMP-0050 fixture invalid: forced origin mansion claimed multiple resolved Skyforge volumes: "
                                + claimedVolumeIds);
            }
            chunk.setAllStarts(previousStarts);
            return false;
        }

        SkyIslandWorldVolumeId claimedVolumeId = claimedVolumeIds.iterator().next();
        int resolvedFirstFreeY = resolvedClaims.stream()
                .filter(claim -> claim.volumeIds().contains(claimedVolumeId))
                .mapToInt(MinecraftSkyforgeHeightClaim::height)
                .max()
                .orElseThrow();
        int structureFloorY = start.getBoundingBox().minY();

        List<BoundingBox> contradictionPieceBoxes =
                SkyforgeNeoForge1211UndersideContradictionDevRuntime.candidatePieceBoxes(start, structure, chunkPos);
        var undersideContradiction = MinecraftStructureUndersideContradictionPolicy.evaluate(
                contradictionPieceBoxes,
                structureFloorY,
                claimedVolumeId);
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
                    claimedVolumeId);
        }

        List<BoundingBox> supportBoxes = MinecraftStructureSupportGeometry.floorContactBoxes(start);
        var naturalRequirements = MinecraftStructureSupportPolicy.requirements(supportBoxes);
        boolean naturallyAccepted = SkyforgeNeoForge1211SurfaceStage.assessSurfaceSupport(naturalRequirements)
                .orElseThrow(() -> new IllegalStateException("active Skyforge binding disappeared during structure generation"))
                .stream()
                .filter(assessment -> assessment.supportingVolumeId().equals(claimedVolumeId))
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
                .filter(assessment -> assessment.supportingVolumeId().equals(claimedVolumeId))
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
                claimedVolumeId,
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
                    claimedVolumeId,
                    acceptedFoundation.maximumRequiredFillDepth());
        }
        return true;
    }

    /**
     * Returns whether a native start floor is actually resolved at this Skyforge height claim.
     *
     * <p>The claim height is Minecraft's first-free block. A one-block tolerance covers native
     * piece conventions that represent the surface by the adjacent occupied/free block coordinate.
     * Anything farther away remains vanilla-owned rather than being interpreted speculatively.
     */
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
        SkyforgeNeoForge1211SurfaceStage.realize(chunk);
    }

    @Override
    public void applyBiomeDecoration(
            WorldGenLevel level,
            ChunkAccess chunk,
            StructureManager structureManager) {
        try (SkyforgeNeoForge1211FeatureStage.Scope scope = SkyforgeNeoForge1211FeatureStage.open(chunk)) {
            scope.requireActive();
            super.applyBiomeDecoration(level, chunk, structureManager);
        }
    }
}
