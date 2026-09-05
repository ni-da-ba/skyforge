package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.recipes.skyisland.SemanticSkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationAssociation;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationCatalog;
import io.github.nidaba.skyforge.world.SkyIslandCompiledVolumeColumnField;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandLocalPosition;
import io.github.nidaba.skyforge.world.SkyIslandNaturalizedDomainField;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.imageio.ImageIO;

/** Generates deterministic AUTH-0046 authored-island realization-association evidence. */
public final class AuthorshipAuthoredRealizationAssociationCorpusCli {
    private static final long AUTHORED_WORLD = 0x534B59464F524745L;
    private static final long REALIZATION_ROOT = 0x5245414C495A4552L;
    private static final int MAP = 176;
    private static final int HEADER = 76;
    private static final int PANELS = 3;
    private static final int SAMPLE = 61;
    private static final int SPECIMEN_WIDTH = PANELS * MAP;
    private static final int SPECIMEN_HEIGHT = HEADER + MAP;

    private static final Color BACKGROUND = Color.WHITE;
    private static final Color AUTHORED = new Color(92, 132, 102);
    private static final Color REALIZED = new Color(112, 118, 126);
    private static final Color OVERLAP = new Color(78, 96, 84);
    private static final Color AUTHORED_ONLY = new Color(176, 126, 72);
    private static final Color REALIZED_ONLY = new Color(82, 118, 164);

    private AuthorshipAuthoredRealizationAssociationCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out =
                args.length == 1
                        ? Path.of(args[0])
                        : Path.of(
                                "build",
                                "evidence",
                                "authorship-authored-realization-association-v1");
        Files.createDirectories(out);

        List<Selection> selections =
                List.of(
                        new Selection("competent", 2332L),
                        new Selection("weak", 653L),
                        new Selection("permeable", 1051L),
                        new Selection("hydrologic", 2211L),
                        new Selection("eroded", 1439L),
                        new Selection("spine", 3670L));

        List<Fixture> fixtures = new ArrayList<>();
        for (int index = 0; index < selections.size(); index++) {
            fixtures.add(fixture(selections.get(index), index));
        }

        List<SkyIslandAuthoredRealizationAssociation> associations =
                fixtures.stream().map(Fixture::association).toList();
        SkyIslandAuthoredRealizationCatalog forward =
                new SkyIslandAuthoredRealizationCatalog(
                        AUTHORED_WORLD,
                        REALIZATION_ROOT,
                        associations);
        List<SkyIslandAuthoredRealizationAssociation> reversed =
                new ArrayList<>(associations);
        java.util.Collections.reverse(reversed);
        SkyIslandAuthoredRealizationCatalog reverse =
                new SkyIslandAuthoredRealizationCatalog(
                        AUTHORED_WORLD,
                        REALIZATION_ROOT,
                        reversed);
        if (!forward.associations().equals(reverse.associations())) {
            throw new IllegalStateException(
                    "AUTH-0046 catalog order depends on caller encounter order");
        }

        BufferedImage atlas =
                new BufferedImage(
                        2 * SPECIMEN_WIDTH,
                        3 * SPECIMEN_HEIGHT,
                        BufferedImage.TYPE_INT_RGB);
        Graphics2D atlasGraphics = atlas.createGraphics();
        atlasGraphics.setColor(BACKGROUND);
        atlasGraphics.fillRect(0, 0, atlas.getWidth(), atlas.getHeight());

        StringBuilder manifest =
                new StringBuilder(
                        "role,islandKey,authoredMorphology,authoredRadius,"
                                + "realizationRoot,groupOrdinal,memberOrdinal,geometrySeed,"
                                + "worldCenterX,worldCenterZ,realizedMorphology,realizedRadius,"
                                + "authoredOwnedSamples,realizedColumnSamples,overlapSamples,"
                                + "authoredOnlySamples,realizedOnlySamples,associationToken\n");

        Set<String> tokens = new HashSet<>();
        for (int index = 0; index < fixtures.size(); index++) {
            Fixture fixture = fixtures.get(index);
            Stats stats = measure(fixture);
            String token = fixture.association().canonicalToken();
            if (!tokens.add(token)) {
                throw new IllegalStateException(
                        "AUTH-0046 generated duplicate association token");
            }

            BufferedImage specimen = renderSpecimen(fixture, stats);
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

            SkyIslandDescriptor authored =
                    fixture.association().authoredDescriptor();
            SkyIslandVolumeDescriptor realized =
                    fixture.association()
                            .realizedVolume()
                            .compiledVolume()
                            .descriptor();
            SkyIslandWorldVolumeId volumeId =
                    fixture.association().realizedVolumeId();

            manifest.append(fixture.selection().role()).append(',')
                    .append(fixture.selection().key()).append(',')
                    .append(authored.morphologyFamily().identifier()).append(',')
                    .append(format(authored.nominalRadius())).append(',')
                    .append(Long.toUnsignedString(volumeId.archipelagoRootSeed())).append(',')
                    .append(volumeId.groupOrdinal()).append(',')
                    .append(volumeId.memberOrdinal()).append(',')
                    .append(Long.toUnsignedString(volumeId.geometrySeed())).append(',')
                    .append(format(realized.centerX())).append(',')
                    .append(format(realized.centerZ())).append(',')
                    .append(realized.morphologyFamily().identifier()).append(',')
                    .append(format(realized.nominalRadius())).append(',')
                    .append(stats.authoredOwned()).append(',')
                    .append(stats.realizedColumns()).append(',')
                    .append(stats.overlap()).append(',')
                    .append(stats.authoredOnly()).append(',')
                    .append(stats.realizedOnly()).append(',')
                    .append(token).append('\n');
        }
        atlasGraphics.dispose();

        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());
        Files.writeString(
                out.resolve("manifest.csv"),
                manifest.toString(),
                StandardCharsets.UTF_8);
        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\"><title>AUTH-0046</title>"
                        + "<h1>Authored-island realization association</h1>"
                        + "<p>AUTHORED DOMAIN shows native naturalized local ownership. "
                        + "REALIZED DOMAIN shows positive compiled physical columns through the "
                        + "same nominal local frame. FRAME OVERLAP is diagnostic: dark denotes "
                        + "overlap, orange authored-only, blue realized-only. AUTH-0046 associates "
                        + "identity and direct local scale; it does not claim these independently "
                        + "defined shapes are pixel-identical.</p>"
                        + "<p>The authored world seed and realization root are deliberately "
                        + "different to prove the two identity domains are explicit rather than "
                        + "numerically inferred.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static Fixture fixture(Selection selection, int index) {
        SkyIslandDescriptor authored =
                SkyIslandDescriptorGenerator.derive(
                        SkyIslandIdentity.of(
                                AUTHORED_WORLD,
                                8L,
                                81L,
                                selection.key()));
        long geometrySeed =
                0x4600000000000000L ^ selection.key();
        int groupOrdinal = index / 2;
        int memberOrdinal = index % 2;
        double centerX = 1200.0 + 760.0 * groupOrdinal + 260.0 * memberOrdinal;
        double centerZ = -900.0 + 540.0 * groupOrdinal - 210.0 * memberOrdinal;

        SkyIslandVolumeDescriptor physical =
                SkyIslandVolumeDescriptor.schema2(
                        geometrySeed,
                        centerX,
                        centerZ,
                        256.0 + 12.0 * index,
                        authored.nominalRadius(),
                        Math.max(40.0, 0.60 * authored.reliefBudget()),
                        Math.max(64.0, 0.82 * authored.reliefBudget()),
                        Math.min(32.0, authored.nominalRadius()),
                        0.31 + 0.07 * index,
                        0.50 + 0.05 * (index % 4),
                        0.54,
                        -0.20 + 0.08 * index,
                        authored.morphologyFamily(),
                        0.17,
                        34.0,
                        0.36);

        CompiledSkyIslandVolume compiled =
                new SemanticSkyIslandVolumeRecipe().compile(physical);
        SkyIslandWorldVolumeId id =
                new SkyIslandWorldVolumeId(
                        REALIZATION_ROOT,
                        "auth46-group-" + groupOrdinal,
                        groupOrdinal,
                        memberOrdinal,
                        geometrySeed);
        double radius = authored.nominalRadius();
        SkyIslandWorldVolume volume =
                new SkyIslandWorldVolume(
                        id,
                        new WorldBounds(
                                centerX - radius,
                                centerX + radius,
                                physical.suspensionElevation() - 300.0,
                                physical.suspensionElevation() + 220.0,
                                centerZ - radius,
                                centerZ + radius),
                        compiled);
        return new Fixture(
                selection,
                SkyIslandAuthoredRealizationAssociation.of(authored, volume));
    }

    private static Stats measure(Fixture fixture) {
        SkyIslandDescriptor authored =
                fixture.association().authoredDescriptor();
        SkyIslandNaturalizedDomainField authoredDomain =
                SkyIslandNaturalizedDomainField.create(authored);
        SkyIslandCompiledVolumeColumnField realizedDomain =
                new SkyIslandCompiledVolumeColumnField(
                        fixture.association()
                                .realizedVolume()
                                .compiledVolume());
        double radius = authored.nominalRadius();

        int authoredOwned = 0;
        int realizedColumns = 0;
        int overlap = 0;
        int authoredOnly = 0;
        int realizedOnly = 0;

        for (int iz = 0; iz < SAMPLE; iz++) {
            double z = -radius + 2.0 * radius * iz / (SAMPLE - 1.0);
            for (int ix = 0; ix < SAMPLE; ix++) {
                double x = -radius + 2.0 * radius * ix / (SAMPLE - 1.0);
                SkyIslandLocalPosition position = new SkyIslandLocalPosition(x, z);
                boolean authoredHere = authoredDomain.sample(position) > 0.0;
                boolean realizedHere = realizedDomain.columnAt(position).isPresent();
                if (authoredHere) {
                    authoredOwned++;
                }
                if (realizedHere) {
                    realizedColumns++;
                }
                if (authoredHere && realizedHere) {
                    overlap++;
                } else if (authoredHere) {
                    authoredOnly++;
                } else if (realizedHere) {
                    realizedOnly++;
                }
            }
        }

        return new Stats(
                authoredOwned,
                realizedColumns,
                overlap,
                authoredOnly,
                realizedOnly);
    }

    private static BufferedImage renderSpecimen(
            Fixture fixture,
            Stats stats) {
        BufferedImage image =
                new BufferedImage(
                        SPECIMEN_WIDTH,
                        SPECIMEN_HEIGHT,
                        BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(BACKGROUND);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());

        SkyIslandDescriptor authored =
                fixture.association().authoredDescriptor();
        SkyIslandWorldVolumeId volume =
                fixture.association().realizedVolumeId();

        graphics.setColor(Color.BLACK);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        graphics.drawString(
                fixture.selection().role()
                        + " / island="
                        + fixture.selection().key()
                        + " / "
                        + authored.morphologyFamily().identifier(),
                7,
                17);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        graphics.drawString(
                "authored world="
                        + Long.toUnsignedString(AUTHORED_WORLD)
                        + " -> realization root="
                        + Long.toUnsignedString(REALIZATION_ROOT),
                7,
                34);
        graphics.drawString(
                String.format(
                        Locale.ROOT,
                        "radius=%.2f group=%d member=%d overlap=%d A-only=%d R-only=%d",
                        authored.nominalRadius(),
                        volume.groupOrdinal(),
                        volume.memberOrdinal(),
                        stats.overlap(),
                        stats.authoredOnly(),
                        stats.realizedOnly()),
                7,
                50);

        String[] labels = {
            "AUTHORED DOMAIN",
            "REALIZED DOMAIN",
            "FRAME OVERLAP"
        };
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        for (int panel = 0; panel < PANELS; panel++) {
            centered(graphics, labels[panel], panel * MAP, MAP, 68);
        }

        renderPanels(image, fixture);
        graphics.setColor(new Color(25, 25, 25));
        for (int panel = 0; panel < PANELS; panel++) {
            graphics.drawRect(panel * MAP, HEADER, MAP - 1, MAP - 1);
        }
        graphics.dispose();
        return image;
    }

    private static void renderPanels(
            BufferedImage image,
            Fixture fixture) {
        SkyIslandDescriptor authored =
                fixture.association().authoredDescriptor();
        SkyIslandNaturalizedDomainField authoredDomain =
                SkyIslandNaturalizedDomainField.create(authored);
        SkyIslandCompiledVolumeColumnField realizedDomain =
                new SkyIslandCompiledVolumeColumnField(
                        fixture.association()
                                .realizedVolume()
                                .compiledVolume());
        double radius = authored.nominalRadius();

        for (int py = 0; py < MAP; py++) {
            double z = radius - 2.0 * radius * py / (MAP - 1.0);
            for (int px = 0; px < MAP; px++) {
                double x = -radius + 2.0 * radius * px / (MAP - 1.0);
                SkyIslandLocalPosition position = new SkyIslandLocalPosition(x, z);
                boolean authoredHere = authoredDomain.sample(position) > 0.0;
                boolean realizedHere = realizedDomain.columnAt(position).isPresent();

                image.setRGB(
                        px,
                        HEADER + py,
                        (authoredHere ? AUTHORED : BACKGROUND).getRGB());
                image.setRGB(
                        MAP + px,
                        HEADER + py,
                        (realizedHere ? REALIZED : BACKGROUND).getRGB());

                Color overlapColor =
                        authoredHere && realizedHere
                                ? OVERLAP
                                : authoredHere
                                        ? AUTHORED_ONLY
                                        : realizedHere
                                                ? REALIZED_ONLY
                                                : BACKGROUND;
                image.setRGB(
                        2 * MAP + px,
                        HEADER + py,
                        overlapColor.getRGB());
            }
        }
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
            SkyIslandAuthoredRealizationAssociation association) {}

    private record Stats(
            int authoredOwned,
            int realizedColumns,
            int overlap,
            int authoredOnly,
            int realizedOnly) {}
}
