package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.recipes.skyisland.SemanticSkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.reference.evidence.SkyIslandTerrainSemanticEvidenceWriter;
import io.github.nidaba.skyforge.world.ReferenceTiledSkyIslandTerrainBackend;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationAssociation;
import io.github.nidaba.skyforge.world.SkyIslandComponentFluvialTerrainCandidatePlan;
import io.github.nidaba.skyforge.world.SkyIslandComponentFluvialTerrainCandidatePlanner;
import io.github.nidaba.skyforge.world.SkyIslandComponentFluvialWorldSurfaceProjection;
import io.github.nidaba.skyforge.world.SkyIslandComponentFluvialWorldWaterProjection;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandHydraulicReachGeometry;
import io.github.nidaba.skyforge.world.SkyIslandHydrologyField;
import io.github.nidaba.skyforge.world.SkyIslandHydrologySample;
import io.github.nidaba.skyforge.world.SkyIslandLocalPosition;
import io.github.nidaba.skyforge.world.SkyIslandProjectedFluvialTerrainSample;
import io.github.nidaba.skyforge.world.SkyIslandProjectedFluvialWaterSample;
import io.github.nidaba.skyforge.world.SkyIslandQualifiedFluvialSample;
import io.github.nidaba.skyforge.world.SkyIslandQualifiedFluvialZone;
import io.github.nidaba.skyforge.world.SkyIslandTerrainProfile;
import io.github.nidaba.skyforge.world.SkyIslandWorldCatalog;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import io.github.nidaba.skyforge.world.WorldBounds;
import io.github.nidaba.skyforge.world.WorldRegionTerrain;
import io.github.nidaba.skyforge.world.WorldSampleGrid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;

/**
 * Studio S2 reference package binding one authored hydrology specimen to one exact realized world
 * volume and one exact backend-neutral terrain semantic lattice.
 */
public final class StudioBoundHydrologySemanticCorpusCli {
    public static final String EVIDENCE_ID = "studio-bound-hydrology-semantic-v1";
    public static final String ARTIFACT_KIND = "SKYFORGE_BOUND_HYDROLOGY_SEMANTIC_LAYER";

    private static final long AUTHORED_WORLD_SEED = 0x534B59464F524745L;
    private static final long REALIZATION_ROOT = 0x5245414C495A4552L;
    private static final long PROVINCE_KEY = 8L;
    private static final long CLUSTER_KEY = 81L;
    private static final long ISLAND_KEY = 77L;
    private static final long GEOMETRY_SEED = 910_632L;
    private static final double GRID_SPACING = 4.0;
    private static final double GRID_MARGIN = 8.0;

    private StudioBoundHydrologySemanticCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path output = args.length == 0
                ? Path.of("build", "evidence", EVIDENCE_ID)
                : Path.of(args[0]);
        if (args.length > 1) {
            throw new IllegalArgumentException(
                    "usage: StudioBoundHydrologySemanticCorpusCli [output-directory]");
        }
        Files.createDirectories(output);

        SkyIslandDescriptor authored = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(
                        AUTHORED_WORLD_SEED,
                        PROVINCE_KEY,
                        CLUSTER_KEY,
                        ISLAND_KEY));
        SkyIslandAuthoredRealizationAssociation association =
                productionAssociation(authored, GEOMETRY_SEED);
        SkyIslandComponentFluvialTerrainCandidatePlan candidate =
                SkyIslandComponentFluvialTerrainCandidatePlanner.plan(authored);
        SkyIslandHydrologyField authoredHydrology = SkyIslandHydrologyField.create(authored);

        SkyIslandWorldVolume volume = association.realizedVolume();
        SkyIslandWorldCatalog catalog =
                new SkyIslandWorldCatalog(
                        volume.id().archipelagoRootSeed(),
                        List.of(volume));
        WorldSampleGrid grid = gridAround(volume.bounds(), GRID_SPACING, GRID_MARGIN);
        WorldRegionTerrain terrain =
                new ReferenceTiledSkyIslandTerrainBackend()
                        .realizeTiled(
                                catalog,
                                grid,
                                SkyIslandTerrainProfile.reference(),
                                32,
                                32);

        String version = System.getProperty("skyforge.version", "unknown");
        Path terrainOutput = output.resolve("terrain");
        new SkyIslandTerrainSemanticEvidenceWriter()
                .write(
                        terrain,
                        terrainOutput,
                        "Studio S2 bound hydrology terrain",
                        version);

        SkyIslandComponentFluvialWorldSurfaceProjection surface =
                new SkyIslandComponentFluvialWorldSurfaceProjection(
                        association,
                        candidate);
        SkyIslandComponentFluvialWorldWaterProjection water =
                new SkyIslandComponentFluvialWorldWaterProjection(
                        association,
                        candidate);

        Path hydrology = output.resolve("hydrology-semantic-layer.json");
        Files.writeString(
                hydrology,
                encodeHydrologyArtifact(
                        association,
                        candidate,
                        authoredHydrology,
                        terrain,
                        grid,
                        surface,
                        water,
                        version),
                StandardCharsets.UTF_8);

        Files.writeString(
                output.resolve("index.html"),
                indexHtml(association, terrain),
                StandardCharsets.UTF_8);

        System.out.println(output.resolve("index.html").toAbsolutePath());
        System.out.println("Association token: " + association.canonicalToken());
        System.out.println("Terrain semantic SHA-256: " + terrain.sha256());
    }

    private static String encodeHydrologyArtifact(
            SkyIslandAuthoredRealizationAssociation association,
            SkyIslandComponentFluvialTerrainCandidatePlan candidate,
            SkyIslandHydrologyField authoredHydrology,
            WorldRegionTerrain terrain,
            WorldSampleGrid grid,
            SkyIslandComponentFluvialWorldSurfaceProjection surface,
            SkyIslandComponentFluvialWorldWaterProjection water,
            String version) {
        StringBuilder json = new StringBuilder(512_000);
        var authored = association.authoredIdentity();
        var realizedId = association.realizedVolumeId();
        var physical = association.realizedVolume().compiledVolume().descriptor();
        int causeStride = 2;
        StringBuilder causeSamples = new StringBuilder();
        int causeSampleCount = 0;
        for (int z = 0; z < grid.zSamples(); z += causeStride) {
            for (int x = 0; x < grid.xSamples(); x += causeStride) {
                if (!hasSolidColumn(terrain, x, z)) {
                    continue;
                }
                double worldX = grid.xAt(x);
                double worldZ = grid.zAt(z);
                SkyIslandHydrologySample sample = authoredHydrology.sample(
                        new SkyIslandLocalPosition(
                                worldX - physical.centerX(),
                                worldZ - physical.centerZ()));
                if (causeSampleCount > 0) {
                    causeSamples.append(",\n");
                }
                appendHydrologyCauseSample(
                        causeSamples,
                        x,
                        z,
                        worldX - physical.centerX(),
                        worldZ - physical.centerZ(),
                        worldX,
                        worldZ,
                        sample);
                causeSampleCount++;
            }
        }

        json.append("{\n")
                .append("  \"schema_version\": 1,\n")
                .append("  \"artifact_kind\": \"")
                .append(ARTIFACT_KIND)
                .append("\",\n")
                .append("  \"skyforge_version\": \"")
                .append(escape(version))
                .append("\",\n")
                .append("  \"terrain_semantic_sha256\": \"")
                .append(terrain.sha256())
                .append("\",\n")
                .append("  \"specimen_binding\": {\n")
                .append("    \"association_schema_version\": ")
                .append(association.schemaVersion())
                .append(",\n")
                .append("    \"association_token\": \"")
                .append(escape(association.canonicalToken()))
                .append("\",\n")
                .append("    \"authored_identity\": {\n")
                .append("      \"schema_version\": ")
                .append(authored.schemaVersion())
                .append(",\n")
                .append("      \"world_seed_hex\": \"").append(hex(authored.worldSeed())).append("\",\n")
                .append("      \"province_key_hex\": \"").append(hex(authored.provinceKey())).append("\",\n")
                .append("      \"cluster_key_hex\": \"").append(hex(authored.clusterKey())).append("\",\n")
                .append("      \"island_key_hex\": \"").append(hex(authored.islandKey())).append("\"\n")
                .append("    },\n")
                .append("    \"realized_volume\": {\n")
                .append("      \"path\": \"").append(escape(realizedId.path())).append("\",\n")
                .append("      \"root_seed_hex\": \"").append(hex(realizedId.archipelagoRootSeed())).append("\",\n")
                .append("      \"group_identifier\": \"").append(escape(realizedId.groupIdentifier())).append("\",\n")
                .append("      \"group_ordinal\": ").append(realizedId.groupOrdinal()).append(",\n")
                .append("      \"member_ordinal\": ").append(realizedId.memberOrdinal()).append(",\n")
                .append("      \"geometry_seed_hex\": \"").append(hex(realizedId.geometrySeed())).append("\"\n")
                .append("    },\n")
                .append("    \"world_frame\": {\n")
                .append("      \"center_x\": ").append(number(physical.centerX())).append(",\n")
                .append("      \"center_z\": ").append(number(physical.centerZ())).append(",\n")
                .append("      \"suspension_elevation\": ").append(number(physical.suspensionElevation())).append(",\n")
                .append("      \"nominal_radius\": ").append(number(physical.nominalRadius())).append("\n")
                .append("    }\n")
                .append("  },\n")
                .append("  \"grid_binding\": {\n")
                .append("    \"minimum_x\": ").append(number(grid.minimumX())).append(",\n")
                .append("    \"minimum_z\": ").append(number(grid.minimumZ())).append(",\n")
                .append("    \"spacing_x\": ").append(number(grid.spacingX())).append(",\n")
                .append("    \"spacing_z\": ").append(number(grid.spacingZ())).append(",\n")
                .append("    \"x_samples\": ").append(grid.xSamples()).append(",\n")
                .append("    \"z_samples\": ").append(grid.zSamples()).append(",\n")
                .append("    \"cause_stride\": ").append(causeStride).append(",\n")
                .append("    \"cause_sample_count\": ").append(causeSampleCount).append("\n")
                .append("  },\n")
                .append("  \"hydrology_causes\": [\n")
                .append(causeSamples)
                .append("\n  ],\n")
                .append("  \"field_samples\": [\n");

        boolean first = true;
        int affectedSamples = 0;
        int wetSamples = 0;
        for (int z = 0; z < grid.zSamples(); z++) {
            for (int x = 0; x < grid.xSamples(); x++) {
                if (!hasSolidColumn(terrain, x, z)) {
                    continue;
                }
                double worldX = grid.xAt(x);
                double worldZ = grid.zAt(z);
                SkyIslandProjectedFluvialTerrainSample terrainSample =
                        surface.sampleWorld(worldX, worldZ);
                SkyIslandQualifiedFluvialSample semantic =
                        terrainSample.semanticSample();
                if (semantic.zone() == SkyIslandQualifiedFluvialZone.UNAFFECTED) {
                    continue;
                }
                SkyIslandProjectedFluvialWaterSample waterSample =
                        water.sampleWorld(worldX, worldZ);
                if (!first) {
                    json.append(",\n");
                }
                first = false;
                affectedSamples++;
                if (waterSample.wet()) {
                    wetSamples++;
                }
                appendFieldSample(
                        json,
                        x,
                        z,
                        worldX,
                        worldZ,
                        terrainSample,
                        waterSample);
            }
        }
        json.append("\n  ],\n")
                .append("  \"reaches\": [\n");

        List<SkyIslandHydraulicReachGeometry> reaches =
                candidate.terrainField().acceptedReaches();
        for (int reachIndex = 0; reachIndex < reaches.size(); reachIndex++) {
            appendReach(
                    json,
                    reaches.get(reachIndex),
                    physical.centerX(),
                    physical.centerZ(),
                    surface,
                    water,
                    reachIndex + 1 < reaches.size());
        }
        json.append("  ],\n")
                .append("  \"summary\": {\n")
                .append("    \"accepted_reaches\": ").append(reaches.size()).append(",\n")
                .append("    \"affected_grid_samples\": ").append(affectedSamples).append(",\n")
                .append("    \"wet_grid_samples\": ").append(wetSamples).append("\n")
                .append("  },\n")
                .append("  \"ownership\": {\n")
                .append("    \"backend_neutral_semantics\": true,\n")
                .append("    \"association_authority\": \"AUTH-0046\",\n")
                .append("    \"terrain_projection_authority\": \"F4B\",\n")
                .append("    \"water_projection_authority\": \"F4E\",\n")
                .append("    \"minecraft_dependency\": false,\n")
                .append("    \"studio_recomputes_hydrology\": false\n")
                .append("  }\n")
                .append("}\n");
        return json.toString();
    }

    private static void appendHydrologyCauseSample(
            StringBuilder json,
            int gridX,
            int gridZ,
            double localX,
            double localZ,
            double worldX,
            double worldZ,
            SkyIslandHydrologySample sample) {
        json.append("    {")
                .append("\"grid\":[").append(gridX).append(',').append(gridZ).append("],")
                .append("\"local_x\":").append(number(localX)).append(',')
                .append("\"local_z\":").append(number(localZ)).append(',')
                .append("\"world_x\":").append(number(worldX)).append(',')
                .append("\"world_z\":").append(number(worldZ)).append(',')
                .append("\"runoff_potential\":").append(number(sample.runoffPotential())).append(',')
                .append("\"retention_potential\":").append(number(sample.retentionPotential())).append(',')
                .append("\"drainage_potential\":").append(number(sample.drainagePotential())).append(',')
                .append("\"outflow_potential\":").append(number(sample.outflowPotential())).append(',')
                .append("\"flow_x\":").append(number(sample.flowX())).append(',')
                .append("\"flow_z\":").append(number(sample.flowZ()))
                .append('}');
    }

    private static void appendFieldSample(
            StringBuilder json,
            int gridX,
            int gridZ,
            double worldX,
            double worldZ,
            SkyIslandProjectedFluvialTerrainSample terrain,
            SkyIslandProjectedFluvialWaterSample water) {
        SkyIslandQualifiedFluvialSample semantic = terrain.semanticSample();
        json.append("    {")
                .append("\"grid\":[").append(gridX).append(',').append(gridZ).append("],")
                .append("\"world_x\":").append(number(worldX)).append(',')
                .append("\"world_z\":").append(number(worldZ)).append(',')
                .append("\"original_upper_y\":").append(number(terrain.originalUpperSurfaceWorldY())).append(',')
                .append("\"target_upper_y\":").append(number(terrain.targetUpperSurfaceWorldY())).append(',')
                .append("\"terrain_delta_world\":").append(number(terrain.terrainDeltaWorldUnits())).append(',')
                .append("\"zone\":\"").append(semantic.zone().name()).append("\",")
                .append("\"wet\":").append(water.wet()).append(',')
                .append("\"water_surface_y\":");
        OptionalDouble waterSurface = water.waterSurfaceWorldY();
        if (waterSurface.isPresent()) {
            json.append(number(waterSurface.orElseThrow()));
        } else {
            json.append("null");
        }
        json.append(',')
                .append("\"water_depth_world\":").append(number(water.waterDepthWorldUnits())).append(',')
                .append("\"provenance\":");
        if (semantic.provenance().isPresent()) {
            var provenance = semantic.provenance().orElseThrow();
            json.append("{\"start_cell\":")
                    .append(provenance.startCellIndex())
                    .append(",\"end_cell\":")
                    .append(provenance.endCellIndex())
                    .append(",\"profile_kind\":\"")
                    .append(provenance.profileKind().name())
                    .append("\"}");
        } else {
            json.append("null");
        }
        json.append('}');
    }

    private static void appendReach(
            StringBuilder json,
            SkyIslandHydraulicReachGeometry reach,
            double centerX,
            double centerZ,
            SkyIslandComponentFluvialWorldSurfaceProjection surface,
            SkyIslandComponentFluvialWorldWaterProjection water,
            boolean trailingComma) {
        var semantic = reach.geomorphicRoute().semanticReach();
        json.append("    {\n")
                .append("      \"start_cell\": ").append(semantic.startCellIndex()).append(",\n")
                .append("      \"end_cell\": ").append(semantic.endCellIndex()).append(",\n")
                .append("      \"downstream_stream_order\": ").append(semantic.downstreamStreamOrder()).append(",\n")
                .append("      \"downstream_relative_discharge\": ")
                .append(number(semantic.downstreamRelativeDischarge()))
                .append(",\n")
                .append("      \"maximum_bankfull_half_width\": ")
                .append(number(reach.maximumBankfullHalfWidth()))
                .append(",\n")
                .append("      \"points\": [\n");

        for (int index = 0; index < reach.samples().size(); index++) {
            var hydraulic = reach.samples().get(index);
            double worldX = centerX + hydraulic.position().x();
            double worldZ = centerZ + hydraulic.position().z();
            SkyIslandProjectedFluvialTerrainSample terrain =
                    surface.sampleWorld(worldX, worldZ);
            SkyIslandProjectedFluvialWaterSample waterSample =
                    water.sampleWorld(worldX, worldZ);
            json.append("        {")
                    .append("\"world_x\":").append(number(worldX)).append(',')
                    .append("\"world_z\":").append(number(worldZ)).append(',')
                    .append("\"target_upper_y\":").append(number(terrain.targetUpperSurfaceWorldY())).append(',')
                    .append("\"water_surface_y\":");
            if (waterSample.waterSurfaceWorldY().isPresent()) {
                json.append(number(waterSample.waterSurfaceWorldY().orElseThrow()));
            } else {
                json.append("null");
            }
            json.append(',')
                    .append("\"station_fraction\":").append(number(hydraulic.stationFraction())).append(',')
                    .append("\"relative_discharge\":").append(number(hydraulic.relativeDischarge())).append(',')
                    .append("\"bankfull_half_width\":").append(number(hydraulic.bankfullHalfWidth()))
                    .append('}')
                    .append(index + 1 < reach.samples().size() ? "," : "")
                    .append('\n');
        }
        json.append("      ]\n")
                .append("    }")
                .append(trailingComma ? "," : "")
                .append('\n');
    }

    private static boolean hasSolidColumn(WorldRegionTerrain terrain, int x, int z) {
        for (int y = 0; y < terrain.grid().ySamples(); y++) {
            if (terrain.semanticAt(x, y, z).isSolid()) {
                return true;
            }
        }
        return false;
    }

    private static SkyIslandAuthoredRealizationAssociation productionAssociation(
            SkyIslandDescriptor descriptor,
            long geometrySeed) {
        double radius = descriptor.nominalRadius();
        double centerX = 1200.0;
        double centerZ = -900.0;
        SkyIslandVolumeDescriptor physical =
                SkyIslandVolumeDescriptor.schema2(
                        geometrySeed,
                        centerX,
                        centerZ,
                        256.0,
                        radius,
                        72.0,
                        104.0,
                        Math.min(32.0, radius),
                        0.43,
                        0.62,
                        0.57,
                        0.18,
                        descriptor.morphologyFamily(),
                        0.22,
                        38.0,
                        0.31);
        CompiledSkyIslandVolume compiled =
                new SemanticSkyIslandVolumeRecipe().compile(physical);
        SkyIslandWorldVolumeId id =
                new SkyIslandWorldVolumeId(
                        REALIZATION_ROOT,
                        "studio-s2",
                        0,
                        0,
                        geometrySeed);
        WorldBounds bounds =
                new WorldBounds(
                        centerX - radius,
                        centerX + radius,
                        64.0,
                        448.0,
                        centerZ - radius,
                        centerZ + radius);
        return SkyIslandAuthoredRealizationAssociation.of(
                descriptor,
                new SkyIslandWorldVolume(id, bounds, compiled));
    }

    private static WorldSampleGrid gridAround(
            WorldBounds bounds,
            double spacing,
            double margin) {
        double minimumX = floorTo(bounds.minimumX() - margin, spacing);
        double maximumX = ceilTo(bounds.maximumX() + margin, spacing);
        double minimumY = floorTo(bounds.minimumY() - margin, spacing);
        double maximumY = ceilTo(bounds.maximumY() + margin, spacing);
        double minimumZ = floorTo(bounds.minimumZ() - margin, spacing);
        double maximumZ = ceilTo(bounds.maximumZ() + margin, spacing);
        return new WorldSampleGrid(
                minimumX,
                minimumY,
                minimumZ,
                spacing,
                spacing,
                spacing,
                sampleCount(minimumX, maximumX, spacing),
                sampleCount(minimumY, maximumY, spacing),
                sampleCount(minimumZ, maximumZ, spacing));
    }

    private static int sampleCount(double minimum, double maximum, double spacing) {
        return Math.addExact(1, (int) Math.round((maximum - minimum) / spacing));
    }

    private static double floorTo(double value, double spacing) {
        return Math.floor(value / spacing) * spacing;
    }

    private static double ceilTo(double value, double spacing) {
        return Math.ceil(value / spacing) * spacing;
    }

    private static String indexHtml(
            SkyIslandAuthoredRealizationAssociation association,
            WorldRegionTerrain terrain) {
        return """
                <!doctype html><meta charset="utf-8">
                <title>Skyforge Studio S2 bound hydrology</title>
                <style>
                  body{font-family:system-ui,sans-serif;max-width:1100px;margin:2rem auto;padding:0 1rem;background:#f6f4ee;color:#282c34}
                  code{background:#e9e6dd;padding:2px 5px}
                  .views{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:1rem}
                  figure{margin:0;padding:0.75rem;background:#fff;border:1px solid #d5d0c4}
                  figure img{display:block;width:100%%;height:auto}
                  figcaption{padding-top:.5rem}
                  .gate{padding:1rem;background:#fff2cc;border-left:4px solid #bf8b00}
                </style>
                <h1>Studio S2 bound hydrology semantic specimen</h1>
                <p>One exact AUTH-0046 authored-realization association rendered through F4B terrain and F4E water projection. No Minecraft dependency or Studio-side hydrology solve exists.</p>
                <p>Association: <code>%s</code></p>
                <p>Terrain semantic SHA-256: <code>%s</code></p>
                <h2>Review in Studio</h2>
                <ol>
                  <li>Load the <a href="terrain/terrain-semantic-volume.json">exact terrain semantic volume</a>.</li>
                  <li>Inspect the <a href="hydrology-semantic-layer.json">bound hydrology semantic overlay JSON</a> against it; the overlay carries the terrain semantic SHA-256 and AUTH-0046 association.</li>
                </ol>
                <p class="gate"><strong>Human gate:</strong> these views and machine checks do not approve aesthetics, product quality, or the separate DR-70/key-287 review. Record a human visual finding before advancing that boundary.</p>
                <h2>Terrain semantic views</h2>
                <div class="views">
                  <figure><a href="terrain/top-surface-semantics.png"><img src="terrain/top-surface-semantics.png" alt="Terrain top-surface semantic map"></a><figcaption>Top-surface semantics</figcaption></figure>
                  <figure><a href="terrain/east-west-section.png"><img src="terrain/east-west-section.png" alt="East-west terrain semantic section"></a><figcaption>East-west section</figcaption></figure>
                  <figure><a href="terrain/north-south-section.png"><img src="terrain/north-south-section.png" alt="North-south terrain semantic section"></a><figcaption>North-south section</figcaption></figure>
                  <figure><a href="terrain/isometric-top-semantics.png"><img src="terrain/isometric-top-semantics.png" alt="Isometric terrain semantic overview"></a><figcaption>Isometric overview</figcaption></figure>
                  <figure><a href="terrain/legend.png"><img src="terrain/legend.png" alt="Terrain semantic legend"></a><figcaption>Semantic legend</figcaption></figure>
                </div>
                """.formatted(
                        escape(association.canonicalToken()),
                        terrain.sha256());
    }

    private static String hex(long value) {
        return String.format(Locale.ROOT, "%016x", value);
    }

    private static String number(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalStateException("non-finite evidence value " + value);
        }
        return String.format(Locale.ROOT, "%.17g", value);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
