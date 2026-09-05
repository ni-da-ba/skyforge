package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate3;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandMorphologyFamily;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.SemanticSkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationAssociation;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationCatalog;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationOwnershipCandidate;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationOwnershipResolver;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationOwnershipSelection;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationOwnershipStatus;
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
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

/** Generates deterministic AUTH-0048 multi-island ownership evidence. */
public final class AuthorshipMultiIslandOwnershipCorpusCli {
    private static final long AUTHORED_WORLD = 0x4155544830303438L;
    private static final long REALIZATION_ROOT = 0x5245414C30303438L;
    private static final double RADIUS = 120.0;
    private static final int SAMPLE_Z = 190;
    private static final int SAMPLE_Y = 120;
    private static final int SCALE = 2;
    private static final int PANEL_WIDTH = SAMPLE_Z * SCALE;
    private static final int PANEL_HEIGHT = SAMPLE_Y * SCALE;
    private static final int HEADER = 92;
    private static final int PANELS = 4;

    private AuthorshipMultiIslandOwnershipCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out =
                args.length == 1
                        ? Path.of(args[0])
                        : Path.of(
                                "build",
                                "evidence",
                                "authorship-multi-island-ownership-v1");
        Files.createDirectories(out);

        Fixture fixture = fixture();
        Grid grid = sample(fixture);
        BufferedImage atlas = render(fixture, grid);

        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());

        String manifest =
                "sampledPoints,multiConservativePoints,multiPhysicalPoints,"
                        + "multipleConservativeUniquePoints,multipleConservativeEmptyPoints,"
                        + "uniqueOwnedPoints,ambiguousOwnedPoints,physicalUnownedPoints,"
                        + "twoPhysicalOneNativePoints,stackedCrossContaminationViolations,"
                        + "orderDependenceViolations\n"
                        + grid.sampledPoints()
                        + ","
                        + grid.multiConservativePoints()
                        + ","
                        + grid.multiPhysicalPoints()
                        + ","
                        + grid.multipleConservativeUniquePoints()
                        + ","
                        + grid.multipleConservativeEmptyPoints()
                        + ","
                        + grid.uniqueOwnedPoints()
                        + ","
                        + grid.ambiguousOwnedPoints()
                        + ","
                        + grid.physicalUnownedPoints()
                        + ","
                        + grid.twoPhysicalOneNativePoints()
                        + ","
                        + grid.stackedCrossContaminationViolations()
                        + ","
                        + grid.orderDependenceViolations()
                        + "\n";
        Files.writeString(
                out.resolve("manifest.csv"), manifest, StandardCharsets.UTF_8);

        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\">"
                        + "<title>AUTH-0048</title>"
                        + "<h1>Multi-island authored-realization ownership</h1>"
                        + "<p>This Z/Y slice uses X=0. The left fixture contains two same-X/Z "
                        + "vertically stacked associations with deliberately overlapping conservative "
                        + "bounds. The right fixture contains two co-located physical realizations "
                        + "(MASSIF and SPINE), producing both true native ambiguity in their shared "
                        + "core and UNIQUE ownership where both physical volumes exist but only the "
                        + "MASSIF native domain owns the point.</p>"
                        + "<p>CONSERVATIVE shows bound-candidate multiplicity. PHYSICAL shows exact "
                        + "compiled occupants. AUTHORED shows native owner multiplicity. RESOLUTION "
                        + "shows NONE, stable unique-owner identity, or red AMBIGUOUS. Bounds never "
                        + "rank an owner.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static Grid sample(Fixture fixture) {
        SkyIslandAuthoredRealizationOwnershipSelection[][] selections =
                new SkyIslandAuthoredRealizationOwnershipSelection[SAMPLE_Y][SAMPLE_Z];

        int sampled = 0;
        int multiConservative = 0;
        int multiPhysical = 0;
        int multipleConservativeUnique = 0;
        int multipleConservativeEmpty = 0;
        int unique = 0;
        int ambiguous = 0;
        int physicalUnowned = 0;
        int twoPhysicalOneNative = 0;
        int stackedCrossContamination = 0;
        int orderDependence = 0;

        for (int iy = 0; iy < SAMPLE_Y; iy++) {
            double y = 35.0 + iy * (420.0 - 35.0) / (SAMPLE_Y - 1.0);
            for (int iz = 0; iz < SAMPLE_Z; iz++) {
                double z = -370.0 + iz * (770.0 / (SAMPLE_Z - 1.0));
                Coordinate3 point = new Coordinate3(0.0, y, z);
                SkyIslandAuthoredRealizationOwnershipSelection selection =
                        fixture.forward().resolve(point);
                SkyIslandAuthoredRealizationOwnershipSelection reverse =
                        fixture.reverse().resolve(point);
                selections[iy][ix] = selection;
                sampled++;

                int conservative = selection.conservativeCandidateCount();
                int physical = selection.exactPhysicalOccupants().size();
                int owners = selection.authoredOwners().size();
                if (conservative > 1) {
                    multiConservative++;
                    if (selection.status()
                            == SkyIslandAuthoredRealizationOwnershipStatus.UNIQUE) {
                        multipleConservativeUnique++;
                    }
                    if (physical == 0) {
                        multipleConservativeEmpty++;
                    }
                }
                if (physical > 1) {
                    multiPhysical++;
                }
                if (selection.status()
                        == SkyIslandAuthoredRealizationOwnershipStatus.UNIQUE) {
                    unique++;
                } else if (selection.status()
                        == SkyIslandAuthoredRealizationOwnershipStatus.AMBIGUOUS) {
                    ambiguous++;
                }
                if (physical > 0 && owners == 0) {
                    physicalUnowned++;
                }
                if (physical >= 2 && owners == 1) {
                    twoPhysicalOneNative++;
                }

                boolean lowerPresent =
                        contains(
                                selection.exactPhysicalOccupants(),
                                fixture.stackLower().authoredIdentity().islandKey());
                boolean upperPresent =
                        contains(
                                selection.exactPhysicalOccupants(),
                                fixture.stackUpper().authoredIdentity().islandKey());
                if (lowerPresent && upperPresent) {
                    stackedCrossContamination++;
                }

                if (!equivalent(selection, reverse)) {
                    orderDependence++;
                }
            }
        }

        return new Grid(
                selections,
                sampled,
                multiConservative,
                multiPhysical,
                multipleConservativeUnique,
                multipleConservativeEmpty,
                unique,
                ambiguous,
                physicalUnowned,
                twoPhysicalOneNative,
                stackedCrossContamination,
                orderDependence);
    }

    private static boolean contains(
            List<SkyIslandAuthoredRealizationOwnershipCandidate> candidates,
            long islandKey) {
        return candidates.stream()
                .anyMatch(
                        candidate ->
                                candidate.association()
                                                .authoredIdentity()
                                                .islandKey()
                                        == islandKey);
    }

    private static boolean equivalent(
            SkyIslandAuthoredRealizationOwnershipSelection first,
            SkyIslandAuthoredRealizationOwnershipSelection second) {
        return first.status() == second.status()
                && tokens(first.conservativeCandidates())
                        .equals(tokens(second.conservativeCandidates()))
                && tokens(first.exactPhysicalOccupants())
                        .equals(tokens(second.exactPhysicalOccupants()))
                && tokens(first.authoredOwners())
                        .equals(tokens(second.authoredOwners()));
    }

    private static List<String> tokens(
            List<SkyIslandAuthoredRealizationOwnershipCandidate> candidates) {
        return candidates.stream()
                .map(candidate -> candidate.association().canonicalToken())
                .toList();
    }

    private static BufferedImage render(Fixture fixture, Grid grid) {
        int width = PANELS * PANEL_WIDTH;
        int height = HEADER + PANEL_HEIGHT;
        BufferedImage image =
                new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, width, height);

        graphics.setColor(Color.BLACK);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        graphics.drawString(
                "AUTH-0048 exact multi-island ownership — Z/Y slice at X=0",
                8,
                19);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        graphics.drawString(
                "left: vertically stacked / right: co-located MASSIF + SPINE",
                8,
                36);
        graphics.drawString(
                String.format(
                        Locale.ROOT,
                        "multi-bounds=%d  multi-physical=%d  ambiguous=%d  two-physical/one-native=%d  orderViolations=%d",
                        grid.multiConservativePoints(),
                        grid.multiPhysicalPoints(),
                        grid.ambiguousOwnedPoints(),
                        grid.twoPhysicalOneNativePoints(),
                        grid.orderDependenceViolations()),
                8,
                51);
        graphics.drawString(
                String.format(
                        Locale.ROOT,
                        "stackedCrossContamination=%d  physicalUnowned=%d  multi-bounds/empty=%d",
                        grid.stackedCrossContaminationViolations(),
                        grid.physicalUnownedPoints(),
                        grid.multipleConservativeEmptyPoints()),
                8,
                65);

        String[] labels = {"CONSERVATIVE", "PHYSICAL", "AUTHORED", "RESOLUTION"};
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        for (int panel = 0; panel < PANELS; panel++) {
            centered(graphics, labels[panel], panel * PANEL_WIDTH, PANEL_WIDTH, 86);
            renderPanel(image, panel * PANEL_WIDTH, grid, panel);
            graphics.drawRect(
                    panel * PANEL_WIDTH,
                    HEADER,
                    PANEL_WIDTH - 1,
                    PANEL_HEIGHT - 1);
        }
        graphics.dispose();
        return image;
    }

    private static void renderPanel(
            BufferedImage image,
            int offsetX,
            Grid grid,
            int panel) {
        for (int iy = 0; iy < SAMPLE_Y; iy++) {
            for (int ix = 0; ix < SAMPLE_Z; ix++) {
                SkyIslandAuthoredRealizationOwnershipSelection selection =
                        grid.selections()[iy][ix];
                Color color =
                        switch (panel) {
                            case 0 -> countColor(selection.conservativeCandidateCount());
                            case 1 -> countColor(selection.exactPhysicalOccupants().size());
                            case 2 -> authoredCountColor(selection.authoredOwners().size());
                            case 3 -> resolutionColor(selection);
                            default -> throw new IllegalArgumentException(
                                    "unknown panel " + panel);
                        };
                for (int dy = 0; dy < SCALE; dy++) {
                    for (int dx = 0; dx < SCALE; dx++) {
                        image.setRGB(
                                offsetX + ix * SCALE + dx,
                                HEADER + (SAMPLE_Y - 1 - iy) * SCALE + dy,
                                color.getRGB());
                    }
                }
            }
        }
    }

    private static Color countColor(int count) {
        if (count <= 0) {
            return Color.WHITE;
        }
        if (count == 1) {
            return new Color(152, 176, 199);
        }
        return new Color(87, 94, 113);
    }

    private static Color authoredCountColor(int count) {
        if (count <= 0) {
            return Color.WHITE;
        }
        if (count == 1) {
            return new Color(88, 143, 95);
        }
        return new Color(186, 66, 66);
    }

    private static Color resolutionColor(
            SkyIslandAuthoredRealizationOwnershipSelection selection) {
        if (selection.status()
                == SkyIslandAuthoredRealizationOwnershipStatus.NONE) {
            return Color.WHITE;
        }
        if (selection.status()
                == SkyIslandAuthoredRealizationOwnershipStatus.AMBIGUOUS) {
            return new Color(190, 62, 62);
        }
        String token =
                selection.uniqueOwner()
                        .orElseThrow()
                        .association()
                        .canonicalToken();
        int hash = token.hashCode();
        return Color.getHSBColor(
                Math.floorMod(hash, 360) / 360.0f,
                0.43f,
                0.76f);
    }

    private static Fixture fixture() {
        SkyIslandAuthoredRealizationAssociation stackLower =
                association(
                        authored(1001L, SkyIslandMorphologyFamily.MASSIF),
                        -185.0,
                        150.0,
                        0x4800000000001001L,
                        "stack/lower",
                        0,
                        0);
        SkyIslandAuthoredRealizationAssociation stackUpper =
                association(
                        authored(1002L, SkyIslandMorphologyFamily.TABLELAND),
                        -185.0,
                        310.0,
                        0x4800000000001002L,
                        "stack/upper",
                        0,
                        1);
        SkyIslandAuthoredRealizationAssociation overlapBroad =
                association(
                        authored(2001L, SkyIslandMorphologyFamily.MASSIF),
                        220.0,
                        240.0,
                        0x4800000000002001L,
                        "overlap/broad",
                        1,
                        0);
        SkyIslandAuthoredRealizationAssociation overlapNarrow =
                association(
                        authored(2002L, SkyIslandMorphologyFamily.SPINE),
                        220.0,
                        240.0,
                        0x4800000000002002L,
                        "overlap/narrow",
                        1,
                        1);

        List<SkyIslandAuthoredRealizationAssociation> forward =
                List.of(
                        stackLower,
                        stackUpper,
                        overlapBroad,
                        overlapNarrow);
        List<SkyIslandAuthoredRealizationAssociation> reversed =
                List.of(
                        overlapNarrow,
                        overlapBroad,
                        stackUpper,
                        stackLower);
        return new Fixture(
                stackLower,
                stackUpper,
                new SkyIslandAuthoredRealizationOwnershipResolver(
                        new SkyIslandAuthoredRealizationCatalog(
                                AUTHORED_WORLD, REALIZATION_ROOT, forward)),
                new SkyIslandAuthoredRealizationOwnershipResolver(
                        new SkyIslandAuthoredRealizationCatalog(
                                AUTHORED_WORLD, REALIZATION_ROOT, reversed)));
    }

    private static SkyIslandAuthoredRealizationAssociation association(
            SkyIslandDescriptor authored,
            double centerZ,
            double suspension,
            long geometrySeed,
            String group,
            int groupOrdinal,
            int memberOrdinal) {
        SkyIslandVolumeDescriptor physical =
                SkyIslandVolumeDescriptor.schema2(
                        geometrySeed,
                        0.0,
                        centerZ,
                        suspension,
                        authored.nominalRadius(),
                        32.0,
                        44.0,
                        30.0,
                        0.15,
                        0.46,
                        0.61,
                        0.08,
                        authored.morphologyFamily(),
                        0.12,
                        32.0,
                        0.22);
        var compiled = new SemanticSkyIslandVolumeRecipe().compile(physical);
        SkyIslandWorldVolume volume =
                new SkyIslandWorldVolume(
                        new SkyIslandWorldVolumeId(
                                REALIZATION_ROOT,
                                group,
                                groupOrdinal,
                                memberOrdinal,
                                geometrySeed),
                        new WorldBounds(
                                -1.35 * RADIUS,
                                1.35 * RADIUS,
                                suspension - 180.0,
                                suspension + 180.0,
                                centerZ - 1.35 * RADIUS,
                                centerZ + 1.35 * RADIUS),
                        compiled);
        return SkyIslandAuthoredRealizationAssociation.of(authored, volume);
    }

    private static SkyIslandDescriptor authored(
            long islandKey,
            SkyIslandMorphologyFamily morphology) {
        return new SkyIslandDescriptor(
                SkyIslandDescriptor.SCHEMA_VERSION,
                SkyIslandIdentity.of(AUTHORED_WORLD, 8L, 81L, islandKey),
                0x4900000000000000L ^ islandKey,
                morphology,
                RADIUS,
                82.0,
                0.72,
                0.42,
                0.54,
                0.58,
                0.50,
                0.46,
                0.57,
                0.63);
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

    private record Fixture(
            SkyIslandAuthoredRealizationAssociation stackLower,
            SkyIslandAuthoredRealizationAssociation stackUpper,
            SkyIslandAuthoredRealizationOwnershipResolver forward,
            SkyIslandAuthoredRealizationOwnershipResolver reverse) {}

    private record Grid(
            SkyIslandAuthoredRealizationOwnershipSelection[][] selections,
            int sampledPoints,
            int multiConservativePoints,
            int multiPhysicalPoints,
            int multipleConservativeUniquePoints,
            int multipleConservativeEmptyPoints,
            int uniqueOwnedPoints,
            int ambiguousOwnedPoints,
            int physicalUnownedPoints,
            int twoPhysicalOneNativePoints,
            int stackedCrossContaminationViolations,
            int orderDependenceViolations) {}
}
