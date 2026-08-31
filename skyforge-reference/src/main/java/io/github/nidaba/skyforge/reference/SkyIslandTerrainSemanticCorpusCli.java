package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoPlanner;
import io.github.nidaba.skyforge.reference.evidence.SkyIslandTerrainSemanticEvidenceWriter;
import io.github.nidaba.skyforge.reference.volume.SkyIslandArchipelagoReferenceCorpus;
import io.github.nidaba.skyforge.world.ReferenceTiledSkyIslandTerrainBackend;
import io.github.nidaba.skyforge.world.SkyIslandTerrainProfile;
import io.github.nidaba.skyforge.world.SkyIslandWorldCatalog;
import io.github.nidaba.skyforge.world.SkyIslandWorldCatalogCompiler;
import io.github.nidaba.skyforge.world.SkyIslandWorldVerticalReservation;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import io.github.nidaba.skyforge.world.WorldBounds;
import io.github.nidaba.skyforge.world.WorldRegionTerrain;
import io.github.nidaba.skyforge.world.WorldSampleGrid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Generates the first backend-neutral terrain-semantic visual evidence package. */
public final class SkyIslandTerrainSemanticCorpusCli {
    private static final SkyIslandWorldVerticalReservation VERTICAL =
            new SkyIslandWorldVerticalReservation(180.0, 140.0);
    private static final SkyIslandTerrainProfile PROFILE = SkyIslandTerrainProfile.reference();

    private SkyIslandTerrainSemanticCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path output = args.length == 0
                ? Path.of("build/evidence/terrain-semantics-v1")
                : Path.of(args[0]);
        String version = System.getProperty("skyforge.version", "unknown");
        Files.createDirectories(output);

        var planner = new SkyIslandArchipelagoPlanner();
        var request = SkyIslandArchipelagoReferenceCorpus.hub(
                SkyIslandArchipelagoReferenceCorpus.SKYFORGE_SEED);
        var plan = planner.plan(request);
        SkyIslandWorldCatalog regionalCatalog = new SkyIslandWorldCatalogCompiler().compile(
                plan, SkyIslandArchipelagoReferenceCorpus.registry(), VERTICAL);

        ReferenceTiledSkyIslandTerrainBackend backend = new ReferenceTiledSkyIslandTerrainBackend();
        SkyIslandTerrainSemanticEvidenceWriter writer = new SkyIslandTerrainSemanticEvidenceWriter();

        SkyIslandWorldVolume specimenVolume = regionalCatalog.volumes().get(0);
        SkyIslandWorldCatalog specimenCatalog = new SkyIslandWorldCatalog(
                regionalCatalog.rootSeed(), List.of(specimenVolume));
        WorldSampleGrid specimenGrid = gridAround(specimenCatalog, 4.0, 4.0, 4.0, 16.0);
        WorldRegionTerrain specimen = backend.realizeTiled(specimenCatalog, specimenGrid, PROFILE, 32, 32);
        writer.write(specimen, output.resolve("specimen"), "single anchor specimen", version);

        WorldSampleGrid regionalGrid = gridAround(regionalCatalog, 48.0, 8.0, 48.0, 96.0);
        WorldRegionTerrain regional = backend.realizeTiled(regionalCatalog, regionalGrid, PROFILE, 12, 12);
        writer.write(regional, output.resolve("regional-hub"), "hierarchical Hub semantic scene", version);

        writeIndex(output, specimen, regional, version);
        System.out.println("Wrote SF-IMP-0029 terrain semantic evidence to " + output.toAbsolutePath());
        System.out.println("Specimen semantic SHA-256: " + specimen.sha256());
        System.out.println("Regional semantic SHA-256: " + regional.sha256());
    }

    private static WorldSampleGrid gridAround(
            SkyIslandWorldCatalog catalog,
            double spacingX,
            double spacingY,
            double spacingZ,
            double margin) {
        WorldBounds bounds = catalogBounds(catalog);
        double minimumX = floorTo(bounds.minimumX() - margin, spacingX);
        double maximumX = ceilTo(bounds.maximumX() + margin, spacingX);
        double minimumY = floorTo(bounds.minimumY() - margin, spacingY);
        double maximumY = ceilTo(bounds.maximumY() + margin, spacingY);
        double minimumZ = floorTo(bounds.minimumZ() - margin, spacingZ);
        double maximumZ = ceilTo(bounds.maximumZ() + margin, spacingZ);
        return new WorldSampleGrid(
                minimumX,
                minimumY,
                minimumZ,
                spacingX,
                spacingY,
                spacingZ,
                sampleCount(minimumX, maximumX, spacingX),
                sampleCount(minimumY, maximumY, spacingY),
                sampleCount(minimumZ, maximumZ, spacingZ));
    }

    private static WorldBounds catalogBounds(SkyIslandWorldCatalog catalog) {
        if (catalog.volumes().isEmpty()) {
            throw new IllegalArgumentException("terrain semantic corpus requires at least one world volume");
        }
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (SkyIslandWorldVolume volume : catalog.volumes()) {
            WorldBounds bounds = volume.bounds();
            minX = Math.min(minX, bounds.minimumX());
            maxX = Math.max(maxX, bounds.maximumX());
            minY = Math.min(minY, bounds.minimumY());
            maxY = Math.max(maxY, bounds.maximumY());
            minZ = Math.min(minZ, bounds.minimumZ());
            maxZ = Math.max(maxZ, bounds.maximumZ());
        }
        return new WorldBounds(minX, maxX, minY, maxY, minZ, maxZ);
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

    private static void writeIndex(
            Path output, WorldRegionTerrain specimen, WorldRegionTerrain regional, String version)
            throws IOException {
        String html = """
                <!doctype html>
                <html><head><meta charset=\"utf-8\"><title>Skyforge terrain semantics</title>
                <style>body{font-family:sans-serif;max-width:1200px;margin:30px auto;background:#f6f4ee;color:#282c34}img{max-width:100%%;border:1px solid #bbb;margin:8px 0 28px}code{background:#e9e6dd;padding:2px 5px}</style>
                </head><body>
                <h1>SF-IMP-0029 backend-neutral terrain semantics</h1>
                <p>Skyforge version: <code>%s</code></p>
                <p>Specimen SHA-256: <code>%s</code><br>Regional SHA-256: <code>%s</code></p>
                <h2>Single anchor specimen</h2>
                <img src=\"specimen/legend.png\"><img src=\"specimen/east-west-section.png\">
                <img src=\"specimen/north-south-section.png\"><img src=\"specimen/top-surface-semantics.png\">
                <img src=\"specimen/isometric-top-semantics.png\">
                <h2>Regional Hub</h2>
                <img src=\"regional-hub/top-surface-semantics.png\"><img src=\"regional-hub/isometric-top-semantics.png\">
                </body></html>
                """.formatted(escape(version), specimen.sha256(), regional.sha256());
        Files.writeString(output.resolve("index.html"), html, StandardCharsets.UTF_8);
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
