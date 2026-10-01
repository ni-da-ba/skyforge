package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate3;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.SemanticSkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationAssociation;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationCatalog;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationOwnershipStatus;
import io.github.nidaba.skyforge.world.SkyIslandCompiledVolumeColumnField;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandMaterialBindingRequest;
import io.github.nidaba.skyforge.world.SkyIslandMaterialBindingRequestField;
import io.github.nidaba.skyforge.world.SkyIslandMaterialBindingRequestSelection;
import io.github.nidaba.skyforge.world.SkyIslandMaterialCapabilityProfile;
import io.github.nidaba.skyforge.world.SkyIslandMaterialResolutionDecision;
import io.github.nidaba.skyforge.world.SkyIslandMaterialResolutionDecisionFactory;
import io.github.nidaba.skyforge.world.SkyIslandMaterialResolutionFrontier;
import io.github.nidaba.skyforge.world.SkyIslandMaterialResolutionSelectionMethod;
import io.github.nidaba.skyforge.world.SkyIslandSemanticDepthRealizationTransform;
import io.github.nidaba.skyforge.world.SkyIslandSubsurfacePosition;
import io.github.nidaba.skyforge.world.SkyIslandVerticalColumn;
import io.github.nidaba.skyforge.world.SkyIslandWorldAuthoredMaterialComposer;
import io.github.nidaba.skyforge.world.SkyIslandWorldAuthoredMaterialComposition;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;

/** Generates deterministic AUTH-0049 multi-island material-composition evidence. */
public final class AuthorshipMultiIslandMaterialCompositionCorpusCli {
    private static final long AUTHORED_WORLD = 0x4155544830303439L;
    private static final long REALIZATION_ROOT = 0x5245414C30303439L;
    private static final int ROW_HEIGHT = 46;
    private static final int HEADER = 54;
    private static final int WIDTH = 1080;

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

    private AuthorshipMultiIslandMaterialCompositionCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out =
                args.length == 1
                        ? Path.of(args[0])
                        : Path.of(
                                "build",
                                "evidence",
                                "authorship-multi-island-material-composition-v1");
        Files.createDirectories(out);

        List<Row> rows = scenarios();
        BufferedImage atlas =
                new BufferedImage(
                        WIDTH,
                        HEADER + ROW_HEIGHT * rows.size(),
                        BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = atlas.createGraphics();
        graphics.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, atlas.getWidth(), atlas.getHeight());
        renderHeader(graphics);
        for (int i = 0; i < rows.size(); i++) {
            renderRow(graphics, rows.get(i), HEADER + i * ROW_HEIGHT);
        }
        graphics.dispose();
        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());

        StringBuilder manifest =
                new StringBuilder(
                        "scenario,status,conservativeCandidates,physicalOccupants,authoredOwners,"
                                + "samplePresent,materialPresent,authoredVoid,applicationPresent,"
                                + "providerCalls,uniqueIslandKey\n");
        for (Row row : rows) {
            manifest.append(row.scenario()).append(',')
                    .append(row.status()).append(',')
                    .append(row.conservativeCandidates()).append(',')
                    .append(row.physicalOccupants()).append(',')
                    .append(row.authoredOwners()).append(',')
                    .append(row.samplePresent()).append(',')
                    .append(row.materialPresent()).append(',')
                    .append(row.authoredVoid()).append(',')
                    .append(row.applicationPresent()).append(',')
                    .append(row.providerCalls()).append(',')
                    .append(row.uniqueIslandKey()).append('\n');
        }
        Files.writeString(
                out.resolve("manifest.csv"),
                manifest.toString(),
                StandardCharsets.UTF_8);

        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\">"
                        + "<title>AUTH-0049</title>"
                        + "<h1>Multi-island world material composition</h1>"
                        + "<p>Scenario matrix proving AUTH-0048 ownership is authoritative before "
                        + "AUTH-0047 material sampling. NONE and AMBIGUOUS never invoke the material "
                        + "decision provider. UNIQUE authored void retains ownership without an "
                        + "application. UNIQUE material alone produces AUTH-0045 application identity.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static List<Row> scenarios() {
        ArrayList<Row> rows = new ArrayList<>();

        SkyIslandAuthoredRealizationAssociation material =
                association(authored(2332L), -900.0, 0.0, 240.0, 149001L, "material", 0, 0);
        rows.add(run(
                "UNIQUE_MATERIAL",
                List.of(material),
                toWorld(material, firstMaterialSemantic(material))));

        SkyIslandAuthoredRealizationAssociation voidAssociation =
                association(authored(1439L), -300.0, 0.0, 245.0, 149002L, "void", 0, 1);
        rows.add(run(
                "UNIQUE_VOID",
                List.of(voidAssociation),
                toWorld(voidAssociation, firstAuthoredVoidSemantic(voidAssociation))));

        rows.add(run(
                "EMPTY_SKY",
                List.of(material),
                new Coordinate3(50_000.0, 50_000.0, 50_000.0)));

        SkyIslandDescriptor overlapAuthored = authored(1051L);
        SkyIslandDescriptor overlapSecondBase = authored(2211L);
        SkyIslandDescriptor overlapSecond =
                new SkyIslandDescriptor(
                        overlapSecondBase.schemaVersion(),
                        overlapSecondBase.identity(),
                        overlapSecondBase.authorshipSeed(),
                        overlapAuthored.morphologyFamily(),
                        overlapAuthored.nominalRadius(),
                        overlapSecondBase.reliefBudget(),
                        overlapSecondBase.rockCompetence(),
                        overlapSecondBase.permeability(),
                        overlapSecondBase.temperatureTendency(),
                        overlapSecondBase.moistureTendency(),
                        overlapSecondBase.exposureTendency(),
                        overlapSecondBase.erosionMaturity(),
                        overlapSecondBase.hydrologicalPotential(),
                        overlapSecondBase.ecologicalPotential());
        SkyIslandAuthoredRealizationAssociation overlapA =
                association(overlapAuthored, 350.0, 0.0, 250.0, 149003L, "overlap/a", 1, 0);
        SkyIslandAuthoredRealizationAssociation overlapB =
                association(overlapSecond, 350.0, 0.0, 250.0, 149004L, "overlap/b", 1, 1);
        rows.add(run(
                "AMBIGUOUS_OVERLAP",
                List.of(overlapB, overlapA),
                commonCenterInterior(overlapA, overlapB)));

        SkyIslandAuthoredRealizationAssociation lower =
                association(authored(653L), 1000.0, 0.0, 150.0, 149005L, "stack/lower", 2, 0);
        SkyIslandAuthoredRealizationAssociation upper =
                association(authored(3670L), 1000.0, 0.0, 330.0, 149006L, "stack/upper", 2, 1);
        rows.add(run(
                "STACK_LOWER",
                List.of(upper, lower),
                centerWorld(lower, 0.50)));
        rows.add(run(
                "STACK_UPPER",
                List.of(lower, upper),
                centerWorld(upper, 0.50)));

        return List.copyOf(rows);
    }

    private static Row run(
            String scenario,
            List<SkyIslandAuthoredRealizationAssociation> associations,
            Coordinate3 world) {
        AtomicInteger providerCalls = new AtomicInteger();
        SkyIslandWorldAuthoredMaterialComposer composer =
                new SkyIslandWorldAuthoredMaterialComposer(
                        new SkyIslandAuthoredRealizationCatalog(
                                AUTHORED_WORLD,
                                REALIZATION_ROOT,
                                associations));
        SkyIslandWorldAuthoredMaterialComposition composition =
                composer.compose(
                        world,
                        request -> {
                            providerCalls.incrementAndGet();
                            return decision(request);
                        });

        String uniqueKey =
                composition.ownership()
                        .uniqueOwner()
                        .map(
                                candidate ->
                                        Long.toUnsignedString(
                                                candidate.association()
                                                        .authoredIdentity()
                                                        .islandKey()))
                        .orElse("");

        return new Row(
                scenario,
                composition.status().name(),
                composition.ownership().conservativeCandidateCount(),
                composition.ownership().exactPhysicalOccupants().size(),
                composition.ownership().authoredOwners().size(),
                composition.authoredSample().isPresent(),
                composition.materialPresent(),
                composition.authoredVoid(),
                composition.materialApplication().isPresent(),
                providerCalls.get(),
                uniqueKey);
    }

    private static void renderHeader(Graphics2D graphics) {
        graphics.setColor(Color.BLACK);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        graphics.drawString("AUTH-0049 multi-island world material composition", 10, 20);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        graphics.drawString(
                "ownership -> sample -> material/application; provider calls are downstream of UNIQUE only",
                10,
                38);

        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 9));
        int y = 51;
        graphics.drawString("SCENARIO", 10, y);
        graphics.drawString("STATUS", 178, y);
        graphics.drawString("CAND", 280, y);
        graphics.drawString("PHYS", 332, y);
        graphics.drawString("OWN", 382, y);
        graphics.drawString("SAMPLE", 430, y);
        graphics.drawString("MATERIAL", 498, y);
        graphics.drawString("VOID", 580, y);
        graphics.drawString("APP", 632, y);
        graphics.drawString("PROVIDER", 680, y);
        graphics.drawString("UNIQUE ISLAND", 778, y);
    }

    private static void renderRow(Graphics2D graphics, Row row, int y) {
        graphics.setColor(statusColor(row.status()));
        graphics.fillRect(0, y, WIDTH, ROW_HEIGHT - 2);
        graphics.setColor(Color.BLACK);
        graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        int baseline = y + 27;
        graphics.drawString(row.scenario(), 10, baseline);
        graphics.drawString(row.status(), 178, baseline);
        graphics.drawString(Integer.toString(row.conservativeCandidates()), 292, baseline);
        graphics.drawString(Integer.toString(row.physicalOccupants()), 344, baseline);
        graphics.drawString(Integer.toString(row.authoredOwners()), 394, baseline);
        graphics.drawString(mark(row.samplePresent()), 448, baseline);
        graphics.drawString(mark(row.materialPresent()), 522, baseline);
        graphics.drawString(mark(row.authoredVoid()), 590, baseline);
        graphics.drawString(mark(row.applicationPresent()), 640, baseline);
        graphics.drawString(Integer.toString(row.providerCalls()), 712, baseline);
        graphics.drawString(row.uniqueIslandKey().isEmpty() ? "-" : row.uniqueIslandKey(), 790, baseline);
    }

    private static Color statusColor(String status) {
        return switch (SkyIslandAuthoredRealizationOwnershipStatus.valueOf(status)) {
            case NONE -> new Color(238, 238, 238);
            case UNIQUE -> new Color(207, 229, 210);
            case AMBIGUOUS -> new Color(240, 205, 205);
        };
    }

    private static String mark(boolean value) {
        return value ? "YES" : "NO";
    }

    private static SkyIslandMaterialResolutionDecision decision(
            SkyIslandMaterialBindingRequest request) {
        SkyIslandMaterialResolutionFrontier frontier =
                SkyIslandMaterialResolutionDecisionFactory.frontier(request, CANDIDATES);
        return SkyIslandMaterialResolutionDecisionFactory.decide(
                frontier,
                frontier.topRank().profile(),
                SkyIslandMaterialResolutionSelectionMethod.SEMANTIC_RANK_WINNER);
    }

    private static SkyIslandSubsurfacePosition firstMaterialSemantic(
            SkyIslandAuthoredRealizationAssociation association) {
        SkyIslandMaterialBindingRequestField field =
                SkyIslandMaterialBindingRequestField.create(
                        association.authoredDescriptor());
        double radius = association.authoredDescriptor().nominalRadius();
        for (int iz = 0; iz < 31; iz++) {
            double z = -radius + iz * (2.0 * radius / 30.0);
            for (int ix = 0; ix < 31; ix++) {
                double x = -radius + ix * (2.0 * radius / 30.0);
                SkyIslandSubsurfacePosition semantic =
                        new SkyIslandSubsurfacePosition(x, z, 0.52);
                if (field.sample(semantic).materialPresent()
                        && toWorldOrNull(association, semantic) != null) {
                    return semantic;
                }
            }
        }
        throw new IllegalStateException("no mappable material sample");
    }

    private static SkyIslandSubsurfacePosition firstAuthoredVoidSemantic(
            SkyIslandAuthoredRealizationAssociation association) {
        SkyIslandMaterialBindingRequestField field =
                SkyIslandMaterialBindingRequestField.create(
                        association.authoredDescriptor());
        double radius = association.authoredDescriptor().nominalRadius();
        for (int depthIndex = 1; depthIndex < 20; depthIndex++) {
            double depth = depthIndex / 20.0;
            for (int iz = 0; iz < 41; iz++) {
                double z = -radius + iz * (2.0 * radius / 40.0);
                for (int ix = 0; ix < 41; ix++) {
                    double x = -radius + ix * (2.0 * radius / 40.0);
                    SkyIslandSubsurfacePosition semantic =
                            new SkyIslandSubsurfacePosition(x, z, depth);
                    SkyIslandMaterialBindingRequestSelection selection =
                            field.sample(semantic);
                    if (selection.owned()
                            && !selection.materialPresent()
                            && toWorldOrNull(association, semantic) != null) {
                        return semantic;
                    }
                }
            }
        }
        throw new IllegalStateException("no mappable authored void sample");
    }

    private static Coordinate3 centerWorld(
            SkyIslandAuthoredRealizationAssociation association,
            double depth) {
        var descriptor =
                association.realizedVolume().compiledVolume().descriptor();
        SkyIslandVerticalColumn column =
                new SkyIslandCompiledVolumeColumnField(
                                association.realizedVolume().compiledVolume())
                        .columnAt(new io.github.nidaba.skyforge.world.SkyIslandLocalPosition(0.0, 0.0))
                        .orElseThrow();
        return new Coordinate3(
                descriptor.centerX(),
                column.physicalYAt(depth),
                descriptor.centerZ());
    }

    private static Coordinate3 commonCenterInterior(
            SkyIslandAuthoredRealizationAssociation first,
            SkyIslandAuthoredRealizationAssociation second) {
        SkyIslandVerticalColumn firstColumn =
                new SkyIslandCompiledVolumeColumnField(
                                first.realizedVolume().compiledVolume())
                        .columnAt(new io.github.nidaba.skyforge.world.SkyIslandLocalPosition(0.0, 0.0))
                        .orElseThrow();
        SkyIslandVerticalColumn secondColumn =
                new SkyIslandCompiledVolumeColumnField(
                                second.realizedVolume().compiledVolume())
                        .columnAt(new io.github.nidaba.skyforge.world.SkyIslandLocalPosition(0.0, 0.0))
                        .orElseThrow();
        double upper = Math.min(firstColumn.upperY(), secondColumn.upperY());
        double lower = Math.max(firstColumn.undersideY(), secondColumn.undersideY());
        if (!(upper > lower)) {
            throw new IllegalStateException("overlap scenario has no exact physical overlap");
        }
        var descriptor =
                first.realizedVolume().compiledVolume().descriptor();
        return new Coordinate3(
                descriptor.centerX(),
                0.5 * (upper + lower),
                descriptor.centerZ());
    }

    private static Coordinate3 toWorld(
            SkyIslandAuthoredRealizationAssociation association,
            SkyIslandSubsurfacePosition semantic) {
        Coordinate3 result = toWorldOrNull(association, semantic);
        if (result == null) {
            throw new IllegalStateException("semantic sample is not physically mappable");
        }
        return result;
    }

    private static Coordinate3 toWorldOrNull(
            SkyIslandAuthoredRealizationAssociation association,
            SkyIslandSubsurfacePosition semantic) {
        var descriptor =
                association.realizedVolume().compiledVolume().descriptor();
        SkyIslandSemanticDepthRealizationTransform transform =
                new SkyIslandSemanticDepthRealizationTransform(
                        new SkyIslandCompiledVolumeColumnField(
                                association.realizedVolume().compiledVolume()));
        var physical = transform.toPhysical(semantic);
        if (physical.isEmpty()) {
            return null;
        }
        return new Coordinate3(
                descriptor.centerX() + semantic.x(),
                physical.orElseThrow().physicalY(),
                descriptor.centerZ() + semantic.z());
    }

    private static SkyIslandAuthoredRealizationAssociation association(
            SkyIslandDescriptor authored,
            double centerX,
            double centerZ,
            double suspension,
            long geometrySeed,
            String group,
            int groupOrdinal,
            int memberOrdinal) {
        SkyIslandVolumeDescriptor physical =
                SkyIslandVolumeDescriptor.schema2(
                        geometrySeed,
                        centerX,
                        centerZ,
                        suspension,
                        authored.nominalRadius(),
                        Math.max(48.0, 0.62 * authored.reliefBudget()),
                        Math.max(72.0, 0.86 * authored.reliefBudget()),
                        Math.min(32.0, authored.nominalRadius()),
                        0.0,
                        0.55,
                        0.58,
                        0.08,
                        authored.morphologyFamily(),
                        0.16,
                        34.0,
                        0.28);
        var compiled = new SemanticSkyIslandVolumeRecipe().compile(physical);
        double radius = authored.nominalRadius();
        return SkyIslandAuthoredRealizationAssociation.of(
                authored,
                new SkyIslandWorldVolume(
                        new SkyIslandWorldVolumeId(
                                REALIZATION_ROOT,
                                group,
                                groupOrdinal,
                                memberOrdinal,
                                geometrySeed),
                        new WorldBounds(
                                centerX - 1.4 * radius,
                                centerX + 1.4 * radius,
                                suspension - 220.0,
                                suspension + 220.0,
                                centerZ - 1.4 * radius,
                                centerZ + 1.4 * radius),
                        compiled));
    }

    private static SkyIslandDescriptor authored(long islandKey) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(AUTHORED_WORLD, 8L, 81L, islandKey));
    }

    private record Row(
            String scenario,
            String status,
            int conservativeCandidates,
            int physicalOccupants,
            int authoredOwners,
            boolean samplePresent,
            boolean materialPresent,
            boolean authoredVoid,
            boolean applicationPresent,
            int providerCalls,
            String uniqueIslandKey) {}
}
