package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate3;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.recipes.skyisland.SemanticSkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationAssociation;
import io.github.nidaba.skyforge.world.SkyIslandCompiledVolumeColumnField;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandMaterialBindingRequest;
import io.github.nidaba.skyforge.world.SkyIslandMaterialBindingRequestField;
import io.github.nidaba.skyforge.world.SkyIslandMaterialBindingRequestSelection;
import io.github.nidaba.skyforge.world.SkyIslandMaterialBindingRequestUse;
import io.github.nidaba.skyforge.world.SkyIslandMaterialCapabilityProfile;
import io.github.nidaba.skyforge.world.SkyIslandMaterialExpressionAllocator;
import io.github.nidaba.skyforge.world.SkyIslandMaterialExpressionRealizer;
import io.github.nidaba.skyforge.world.SkyIslandMaterialRealizationSelection;
import io.github.nidaba.skyforge.world.SkyIslandMaterialResolutionDecision;
import io.github.nidaba.skyforge.world.SkyIslandMaterialResolutionDecisionFactory;
import io.github.nidaba.skyforge.world.SkyIslandMaterialResolutionFrontier;
import io.github.nidaba.skyforge.world.SkyIslandMaterialResolutionSelectionMethod;
import io.github.nidaba.skyforge.world.SkyIslandSemanticDepthRealizationTransform;
import io.github.nidaba.skyforge.world.SkyIslandSemanticMaterialPaletteRole;
import io.github.nidaba.skyforge.world.SkyIslandSemanticPaletteBindingKey;
import io.github.nidaba.skyforge.world.SkyIslandSubsurfacePosition;
import io.github.nidaba.skyforge.world.SkyIslandWorldAuthoredMaterialSample;
import io.github.nidaba.skyforge.world.SkyIslandWorldAuthoredMaterialSampler;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import io.github.nidaba.skyforge.world.WorldBounds;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;

/** Generates deterministic AUTH-0047 world-space material-sampling evidence. */
public final class AuthorshipWorldSpaceMaterialSamplingCorpusCli {
    private static final long AUTHORED_WORLD = 0x534B59464F524745L;
    private static final long REALIZATION_ROOT = 0x574F524C4453414DL;
    private static final int SAMPLE = 42;
    private static final int MAP = 126;
    private static final int HEADER = 74;
    private static final int PANELS = 4;
    private static final int SPECIMEN_WIDTH = PANELS * MAP;
    private static final int SPECIMEN_HEIGHT = HEADER + MAP;

    private static final List<SkyIslandMaterialCapabilityProfile> CANDIDATES =
            List.of(
                    new SkyIslandMaterialCapabilityProfile(
                            0.94, 0.20, 0.20, 0.20, 0.20),
                    new SkyIslandMaterialCapabilityProfile(
                            0.92, 0.94, 0.20, 0.20, 0.20),
                    new SkyIslandMaterialCapabilityProfile(
                            0.20, 0.20, 0.94, 0.20, 0.20),
                    new SkyIslandMaterialCapabilityProfile(
                            0.20, 0.20, 0.20, 0.94, 0.20),
                    new SkyIslandMaterialCapabilityProfile(
                            0.20, 0.20, 0.20, 0.20, 0.94),
                    SkyIslandMaterialCapabilityProfile.uniform(0.86));

    private AuthorshipWorldSpaceMaterialSamplingCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out =
                args.length == 1
                        ? Path.of(args[0])
                        : Path.of(
                                "build",
                                "evidence",
                                "authorship-world-space-material-sampling-v1");
        Files.createDirectories(out);

        List<Selection> selections =
                List.of(
                        new Selection("competent", 2332L),
                        new Selection("weak", 653L),
                        new Selection("permeable", 1051L),
                        new Selection("hydrologic", 2211L),
                        new Selection("eroded", 1439L),
                        new Selection("spine", 3670L));

        BufferedImage atlas =
                new BufferedImage(
                        2 * SPECIMEN_WIDTH,
                        3 * SPECIMEN_HEIGHT,
                        BufferedImage.TYPE_INT_RGB);
        Graphics2D atlasGraphics = atlas.createGraphics();
        atlasGraphics.setColor(Color.WHITE);
        atlasGraphics.fillRect(0, 0, atlas.getWidth(), atlas.getHeight());

        StringBuilder manifest =
                new StringBuilder(
                        "role,islandKey,morphology,worldCenterX,worldCenterZ,"
                                + "mappableSamples,ownedSamples,authoredVoidSamples,"
                                + "materialSamples,applicationSamples,conditionedSamples,"
                                + "winnerMismatches,applicationKeyMismatches,"
                                + "localFrameMismatches,maxDepthError,uniqueApplicationKeys\n");

        for (int index = 0; index < selections.size(); index++) {
            Fixture fixture = fixture(selections.get(index), index);
            Metrics metrics = measure(fixture);
            Grid grid = grid(fixture, 0.52);
            BufferedImage specimen = render(fixture, grid, metrics);

            ImageIO.write(
                    specimen,
                    "png",
                    out.resolve(
                                    fixture.selection().role()
                                            + "-"
                                            + fixture.selection().key()
                                            + ".png")
                            .toFile());
            atlasGraphics.drawImage(
                    specimen,
                    (index % 2) * SPECIMEN_WIDTH,
                    (index / 2) * SPECIMEN_HEIGHT,
                    null);

            manifest.append(fixture.selection().role()).append(',')
                    .append(fixture.selection().key()).append(',')
                    .append(fixture.authored().morphologyFamily().identifier()).append(',')
                    .append(format(fixture.physical().centerX())).append(',')
                    .append(format(fixture.physical().centerZ())).append(',')
                    .append(metrics.mappable()).append(',')
                    .append(metrics.owned()).append(',')
                    .append(metrics.authoredVoid()).append(',')
                    .append(metrics.material()).append(',')
                    .append(metrics.applications()).append(',')
                    .append(metrics.conditioned()).append(',')
                    .append(metrics.winnerMismatches()).append(',')
                    .append(metrics.applicationKeyMismatches()).append(',')
                    .append(metrics.localFrameMismatches()).append(',')
                    .append(format(metrics.maximumDepthError())).append(',')
                    .append(metrics.uniqueApplicationKeys()).append('\n');
        }
        atlasGraphics.dispose();

        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());
        Files.writeString(
                out.resolve("manifest.csv"),
                manifest.toString(),
                StandardCharsets.UTF_8);
        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\"><title>AUTH-0047</title>"
                        + "<h1>World-space authored-material sampling</h1>"
                        + "<p>At semantic depth 0.52, DIRECT NATIVE is AUTH-0044 evaluated "
                        + "directly in island-local semantic coordinates. WORLD SAMPLE maps the "
                        + "same point through the associated compiled physical column into abstract "
                        + "world Coordinate3 and queries AUTH-0047. EQUIVALENCE is green where "
                        + "absence/winner identity agrees and red on failure. APPLICATION KEY "
                        + "shows AUTH-0045 stable binding-key coherence for authored material. "
                        + "White denotes a point that is not physically mappable through the "
                        + "associated compiled column.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static Metrics measure(Fixture fixture) {
        int mappable = 0;
        int owned = 0;
        int authoredVoid = 0;
        int material = 0;
        int applications = 0;
        int conditioned = 0;
        int winnerMismatches = 0;
        int applicationKeyMismatches = 0;
        int localFrameMismatches = 0;
        double maxDepthError = 0.0;
        Set<SkyIslandSemanticPaletteBindingKey> uniqueKeys = new HashSet<>();
        double radius = fixture.authored().nominalRadius();

        for (double depth : new double[] {0.20, 0.52, 0.84}) {
            for (int iz = 0; iz < 41; iz++) {
                double z = -radius + iz * (2.0 * radius / 40.0);
                for (int ix = 0; ix < 41; ix++) {
                    double x = -radius + ix * (2.0 * radius / 40.0);
                    SkyIslandSubsurfacePosition semantic =
                            new SkyIslandSubsurfacePosition(x, z, depth);
                    Coordinate3 world = toWorld(fixture, semantic);
                    if (world == null) {
                        continue;
                    }
                    mappable++;

                    SkyIslandMaterialRealizationSelection direct =
                            direct(fixture.directField(), semantic);
                    SkyIslandWorldAuthoredMaterialSample worldSample =
                            fixture.sampler().sample(
                                    world,
                                    AuthorshipWorldSpaceMaterialSamplingCorpusCli::decision);
                    SkyIslandSubsurfacePosition recovered =
                            worldSample.semantic().orElseThrow();

                    if (Math.abs(recovered.x() - semantic.x()) > 1.0e-12
                            || Math.abs(recovered.z() - semantic.z()) > 1.0e-12) {
                        localFrameMismatches++;
                    }
                    maxDepthError =
                            Math.max(
                                    maxDepthError,
                                    Math.abs(
                                            recovered.depthFraction()
                                                    - semantic.depthFraction()));

                    if (worldSample.authoredOwned()) {
                        owned++;
                    }
                    if (worldSample.authoredVoid()) {
                        authoredVoid++;
                    }
                    if (worldSample.materialPresent()) {
                        material++;
                    }
                    if (worldSample.materialApplication().isPresent()) {
                        applications++;
                        uniqueKeys.add(worldSample.applicationKey().orElseThrow());
                    }
                    if (worldSample.materialRealization()
                            .orElseThrow()
                            .conditionedWinner()) {
                        conditioned++;
                    }

                    if (!direct.winnerBindingKey()
                            .equals(
                                    worldSample.materialRealization()
                                            .orElseThrow()
                                            .winnerBindingKey())) {
                        winnerMismatches++;
                    }
                    if (worldSample.materialPresent()
                            && !worldSample.applicationKey()
                                    .equals(direct.winnerBindingKey())) {
                        applicationKeyMismatches++;
                    }
                }
            }
        }

        return new Metrics(
                mappable,
                owned,
                authoredVoid,
                material,
                applications,
                conditioned,
                winnerMismatches,
                applicationKeyMismatches,
                localFrameMismatches,
                maxDepthError,
                uniqueKeys.size());
    }

    private static Grid grid(Fixture fixture, double depth) {
        SkyIslandMaterialRealizationSelection[][] direct =
                new SkyIslandMaterialRealizationSelection[SAMPLE][SAMPLE];
        SkyIslandWorldAuthoredMaterialSample[][] world =
                new SkyIslandWorldAuthoredMaterialSample[SAMPLE][SAMPLE];
        double radius = fixture.authored().nominalRadius();

        for (int iz = 0; iz < SAMPLE; iz++) {
            double z = -radius + iz * (2.0 * radius / (SAMPLE - 1.0));
            for (int ix = 0; ix < SAMPLE; ix++) {
                double x = -radius + ix * (2.0 * radius / (SAMPLE - 1.0));
                SkyIslandSubsurfacePosition semantic =
                        new SkyIslandSubsurfacePosition(x, z, depth);
                Coordinate3 worldPoint = toWorld(fixture, semantic);
                if (worldPoint == null) {
                    continue;
                }
                direct[iz][ix] = direct(fixture.directField(), semantic);
                world[iz][ix] =
                        fixture.sampler().sample(
                                worldPoint,
                                AuthorshipWorldSpaceMaterialSamplingCorpusCli::decision);
            }
        }
        return new Grid(direct, world);
    }

    private static BufferedImage render(
            Fixture fixture,
            Grid grid,
            Metrics metrics) {
        BufferedImage image =
                new BufferedImage(
                        SPECIMEN_WIDTH,
                        SPECIMEN_HEIGHT,
                        BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());

        graphics.setColor(Color.BLACK);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        graphics.drawString(
                fixture.selection().role()
                        + " / island="
                        + fixture.selection().key()
                        + " / "
                        + fixture.authored().morphologyFamily().identifier(),
                7,
                17);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        graphics.drawString(
                String.format(
                        Locale.ROOT,
                        "world center=(%.1f, %.1f) mapped=%d material=%d void=%d conditioned=%d",
                        fixture.physical().centerX(),
                        fixture.physical().centerZ(),
                        metrics.mappable(),
                        metrics.material(),
                        metrics.authoredVoid(),
                        metrics.conditioned()),
                7,
                35);
        graphics.drawString(
                String.format(
                        Locale.ROOT,
                        "winner mismatch=%d app mismatch=%d frame mismatch=%d depth err<=%.3g",
                        metrics.winnerMismatches(),
                        metrics.applicationKeyMismatches(),
                        metrics.localFrameMismatches(),
                        metrics.maximumDepthError()),
                7,
                50);

        String[] labels = {
            "DIRECT NATIVE",
            "WORLD SAMPLE",
            "EQUIVALENCE",
            "APPLICATION KEY"
        };
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        for (int panel = 0; panel < PANELS; panel++) {
            centered(graphics, labels[panel], panel * MAP, MAP, 68);
            renderPanel(image, panel * MAP, grid, panel);
            graphics.drawRect(panel * MAP, HEADER, MAP - 1, MAP - 1);
        }

        graphics.dispose();
        return image;
    }

    private static void renderPanel(
            BufferedImage image,
            int offsetX,
            Grid grid,
            int panel) {
        for (int py = 0; py < MAP; py++) {
            int sy =
                    Math.min(
                            SAMPLE - 1,
                            (int) Math.round(py * (SAMPLE - 1.0) / (MAP - 1.0)));
            for (int px = 0; px < MAP; px++) {
                int sx =
                        Math.min(
                                SAMPLE - 1,
                                (int) Math.round(px * (SAMPLE - 1.0) / (MAP - 1.0)));
                SkyIslandMaterialRealizationSelection direct = grid.direct()[sy][sx];
                SkyIslandWorldAuthoredMaterialSample world = grid.world()[sy][sx];
                image.setRGB(
                        offsetX + px,
                        HEADER + py,
                        panelColor(direct, world, panel).getRGB());
            }
        }
    }

    private static Color panelColor(
            SkyIslandMaterialRealizationSelection direct,
            SkyIslandWorldAuthoredMaterialSample world,
            int panel) {
        if (direct == null || world == null) {
            return Color.WHITE;
        }
        return switch (panel) {
            case 0 -> winnerColor(direct);
            case 1 -> winnerColor(world.materialRealization().orElseThrow());
            case 2 ->
                    direct.winnerBindingKey()
                                    .equals(
                                            world.materialRealization()
                                                    .orElseThrow()
                                                    .winnerBindingKey())
                            ? new Color(86, 140, 92)
                            : new Color(188, 66, 66);
            case 3 ->
                    world.applicationKey()
                            .map(AuthorshipWorldSpaceMaterialSamplingCorpusCli::keyColor)
                            .orElse(Color.WHITE);
            default -> throw new IllegalArgumentException("unknown panel " + panel);
        };
    }

    private static Color winnerColor(SkyIslandMaterialRealizationSelection selection) {
        if (!selection.materialPresent()) {
            return selection.expressionSample().source().owned()
                    ? new Color(78, 116, 160)
                    : Color.WHITE;
        }
        SkyIslandSemanticMaterialPaletteRole role =
                selection.winner().role();
        return switch (role) {
            case PRIMARY_MATRIX -> new Color(112, 112, 112);
            case SECONDARY_MATRIX -> new Color(88, 126, 88);
            case ALTERATION_OVERPRINT -> new Color(156, 96, 86);
            case HYDROLOGIC_CONDITIONING -> new Color(72, 110, 156);
            case MINERAL_BEARING_STRUCTURE -> new Color(138, 112, 68);
        };
    }

    private static Color keyColor(SkyIslandSemanticPaletteBindingKey key) {
        int hash = key.canonicalToken().hashCode();
        return Color.getHSBColor(
                Math.floorMod(hash, 360) / 360.0f,
                0.42f,
                0.78f);
    }

    private static SkyIslandMaterialRealizationSelection direct(
            SkyIslandMaterialBindingRequestField field,
            SkyIslandSubsurfacePosition semantic) {
        SkyIslandMaterialBindingRequestSelection source = field.sample(semantic);
        Map<SkyIslandSemanticPaletteBindingKey, SkyIslandMaterialResolutionDecision>
                decisions = new HashMap<>();
        for (SkyIslandMaterialBindingRequestUse use : source.uses()) {
            decisions.put(use.request().bindingKey(), decision(use.request()));
        }
        return SkyIslandMaterialExpressionRealizer.realize(
                semantic,
                SkyIslandMaterialExpressionAllocator.allocate(source, decisions));
    }

    private static Coordinate3 toWorld(
            Fixture fixture,
            SkyIslandSubsurfacePosition semantic) {
        var physical = fixture.transform().toPhysical(semantic);
        if (physical.isEmpty()) {
            return null;
        }
        return new Coordinate3(
                fixture.physical().centerX() + semantic.x(),
                physical.orElseThrow().physicalY(),
                fixture.physical().centerZ() + semantic.z());
    }

    private static SkyIslandMaterialResolutionDecision decision(
            SkyIslandMaterialBindingRequest request) {
        SkyIslandMaterialResolutionFrontier frontier =
                SkyIslandMaterialResolutionDecisionFactory.frontier(
                        request, CANDIDATES);
        return SkyIslandMaterialResolutionDecisionFactory.decide(
                frontier,
                frontier.topRank().profile(),
                SkyIslandMaterialResolutionSelectionMethod.SEMANTIC_RANK_WINNER);
    }

    private static Fixture fixture(Selection selection, int index) {
        SkyIslandDescriptor authored =
                SkyIslandDescriptorGenerator.derive(
                        SkyIslandIdentity.of(
                                AUTHORED_WORLD,
                                8L,
                                81L,
                                selection.key()));
        double centerX = 1400.0 + 840.0 * (index / 2) + 280.0 * (index % 2);
        double centerZ = -1200.0 + 610.0 * (index / 2) - 230.0 * (index % 2);
        long geometrySeed = 0x4700000000000000L ^ selection.key();

        SkyIslandVolumeDescriptor physical =
                SkyIslandVolumeDescriptor.schema2(
                        geometrySeed,
                        centerX,
                        centerZ,
                        280.0 + 10.0 * index,
                        authored.nominalRadius(),
                        Math.max(48.0, 0.62 * authored.reliefBudget()),
                        Math.max(72.0, 0.86 * authored.reliefBudget()),
                        Math.min(32.0, authored.nominalRadius()),
                        0.17 * index,
                        0.58,
                        0.57,
                        -0.12 + 0.05 * index,
                        authored.morphologyFamily(),
                        0.18,
                        36.0,
                        0.34);
        CompiledSkyIslandVolume compiled =
                new SemanticSkyIslandVolumeRecipe().compile(physical);
        SkyIslandWorldVolume volume =
                new SkyIslandWorldVolume(
                        new SkyIslandWorldVolumeId(
                                REALIZATION_ROOT,
                                "auth47-" + (index / 2),
                                index / 2,
                                index % 2,
                                geometrySeed),
                        new WorldBounds(
                                centerX - 1.5 * authored.nominalRadius(),
                                centerX + 1.5 * authored.nominalRadius(),
                                0.0,
                                650.0,
                                centerZ - 1.5 * authored.nominalRadius(),
                                centerZ + 1.5 * authored.nominalRadius()),
                        compiled);
        SkyIslandAuthoredRealizationAssociation association =
                SkyIslandAuthoredRealizationAssociation.of(authored, volume);
        SkyIslandCompiledVolumeColumnField columns =
                new SkyIslandCompiledVolumeColumnField(compiled);
        return new Fixture(
                selection,
                authored,
                physical,
                association,
                new SkyIslandSemanticDepthRealizationTransform(columns),
                SkyIslandMaterialBindingRequestField.create(authored),
                new SkyIslandWorldAuthoredMaterialSampler(association));
    }

    private static void centered(
            Graphics2D graphics,
            String label,
            int x,
            int width,
            int y) {
        int textWidth = graphics.getFontMetrics().stringWidth(label);
        graphics.drawString(label, x + (width - textWidth) / 2, y);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.12g", value);
    }

    private record Selection(String role, long key) {}

    private record Fixture(
            Selection selection,
            SkyIslandDescriptor authored,
            SkyIslandVolumeDescriptor physical,
            SkyIslandAuthoredRealizationAssociation association,
            SkyIslandSemanticDepthRealizationTransform transform,
            SkyIslandMaterialBindingRequestField directField,
            SkyIslandWorldAuthoredMaterialSampler sampler) {}

    private record Grid(
            SkyIslandMaterialRealizationSelection[][] direct,
            SkyIslandWorldAuthoredMaterialSample[][] world) {}

    private record Metrics(
            int mappable,
            int owned,
            int authoredVoid,
            int material,
            int applications,
            int conditioned,
            int winnerMismatches,
            int applicationKeyMismatches,
            int localFrameMismatches,
            double maximumDepthError,
            int uniqueApplicationKeys) {}
}
