package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandMorphologyFamily;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.SemanticSkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredOverlapAdmissionAuditor;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredOverlapAdmissionPolicy;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredOverlapPairAudit;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredOverlapPairRule;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationAssociation;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationCatalog;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationSupportCatalog;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationSupportCertificate;
import io.github.nidaba.skyforge.world.SkyIslandCompiledVolumeColumnField;
import io.github.nidaba.skyforge.world.SkyIslandLocalPosition;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import io.github.nidaba.skyforge.world.WorldBounds;
import java.awt.BasicStroke;
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
import javax.imageio.ImageIO;

/** Generates deterministic AUTH-0051 certified-support evidence and visual atlas. */
public final class AuthorshipCertifiedSupportEnvelopeCorpusCli {
    private static final long AUTHORED_WORLD = 0x4155544830303531L;
    private static final long REALIZATION_ROOT = 0x5245414C30303531L;
    private static final double RADIUS = 100.0;
    private static final int SPECIMEN_WIDTH = 540;
    private static final int SPECIMEN_HEIGHT = 240;
    private static final int PLOT_X = 52;
    private static final int PLOT_Y = 54;
    private static final int PLOT_WIDTH = 430;
    private static final int PLOT_HEIGHT = 150;
    private static final double DISPLAY_MIN_Y = 20.0;
    private static final double DISPLAY_MAX_Y = 500.0;

    private AuthorshipCertifiedSupportEnvelopeCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out =
                args.length == 1
                        ? Path.of(args[0])
                        : Path.of(
                                "build",
                                "evidence",
                                "authorship-certified-support-envelope-v1");
        Files.createDirectories(out);

        ArrayList<FamilyRow> rows = new ArrayList<>();
        ArrayList<BufferedImage> specimens = new ArrayList<>();
        int ordinal = 0;
        for (SkyIslandMorphologyFamily family : SkyIslandMorphologyFamily.values()) {
            SkyIslandAuthoredRealizationAssociation association =
                    association(
                            5100L + ordinal,
                            family,
                            0.0,
                            0.0,
                            250.0,
                            55100L + ordinal);
            SkyIslandAuthoredRealizationCatalog catalog =
                    catalog(List.of(association));
            SkyIslandAuthoredRealizationSupportCertificate certificate =
                    SkyIslandAuthoredRealizationSupportCatalog.certifyAccepted(catalog)
                            .certificateFor(association)
                            .orElseThrow();
            FamilyRow row = measure(family, association, certificate);
            rows.add(row);
            specimens.add(renderFamily(row, association, certificate));
            ordinal++;
        }

        StackRow stack = stackScenario();
        specimens.add(renderStack(stack));

        BufferedImage atlas =
                new BufferedImage(
                        SPECIMEN_WIDTH * 2,
                        SPECIMEN_HEIGHT * 3,
                        BufferedImage.TYPE_INT_RGB);
        Graphics2D atlasGraphics = atlas.createGraphics();
        atlasGraphics.setColor(Color.WHITE);
        atlasGraphics.fillRect(0, 0, atlas.getWidth(), atlas.getHeight());
        for (int i = 0; i < specimens.size(); i++) {
            atlasGraphics.drawImage(
                    specimens.get(i),
                    (i % 2) * SPECIMEN_WIDTH,
                    (i / 2) * SPECIMEN_HEIGHT,
                    null);
        }
        atlasGraphics.dispose();
        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());

        StringBuilder manifest =
                new StringBuilder(
                        "family,queryMinimumY,queryMaximumY,supportMinimumY,supportMaximumY,"
                                + "sampledMinimumY,sampledMaximumY,sampledColumns,"
                                + "containmentViolations,querySpan,supportSpan\n");
        for (FamilyRow row : rows) {
            manifest.append(row.family()).append(',')
                    .append(format(row.queryMinimumY())).append(',')
                    .append(format(row.queryMaximumY())).append(',')
                    .append(format(row.supportMinimumY())).append(',')
                    .append(format(row.supportMaximumY())).append(',')
                    .append(format(row.sampledMinimumY())).append(',')
                    .append(format(row.sampledMaximumY())).append(',')
                    .append(row.sampledColumns()).append(',')
                    .append(row.containmentViolations()).append(',')
                    .append(format(row.querySpan())).append(',')
                    .append(format(row.supportSpan())).append('\n');
        }
        Files.writeString(
                out.resolve("manifest.csv"),
                manifest.toString(),
                StandardCharsets.UTF_8);

        String stackManifest =
                "queryBoundsIntersect,broadStatus,certifiedStatus,proofVerticalGap,"
                        + "minimumRequiredGap,lowerSupportMaximumY,upperSupportMinimumY\n"
                        + stack.queryBoundsIntersect()
                        + ","
                        + stack.broadStatus()
                        + ","
                        + stack.certifiedStatus()
                        + ","
                        + format(stack.proofVerticalGap())
                        + ","
                        + format(stack.minimumRequiredGap())
                        + ","
                        + format(stack.lowerSupportMaximumY())
                        + ","
                        + format(stack.upperSupportMinimumY())
                        + "\n";
        Files.writeString(
                out.resolve("stack.csv"),
                stackManifest,
                StandardCharsets.UTF_8);

        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\">"
                        + "<title>AUTH-0051</title>"
                        + "<h1>Certified realized support envelopes</h1>"
                        + "<p>Each family panel is an X/Y slice at local Z=0. The pale outer box is "
                        + "the broad backend query reservation; the inner green box is AUTH-0051 "
                        + "proof-grade support; the dark filled slice is the actual compiled volume. "
                        + "The final panel shows why these contracts must remain distinct: broad "
                        + "reservations overlap and reject STACKED under AUTH-0050, while certified "
                        + "support proves the required physical gap without shrinking query bounds.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">family manifest</a> · "
                        + "<a href=\"stack.csv\">stack proof</a></p>",
                StandardCharsets.UTF_8);
    }

    private static FamilyRow measure(
            SkyIslandMorphologyFamily family,
            SkyIslandAuthoredRealizationAssociation association,
            SkyIslandAuthoredRealizationSupportCertificate certificate) {
        SkyIslandCompiledVolumeColumnField columns =
                new SkyIslandCompiledVolumeColumnField(
                        association.realizedVolume().compiledVolume());
        WorldBounds query = association.realizedVolume().bounds();
        WorldBounds support = certificate.supportBounds();
        double sampledMinimum = Double.POSITIVE_INFINITY;
        double sampledMaximum = Double.NEGATIVE_INFINITY;
        int sampledColumns = 0;
        int violations = 0;
        double horizontal = certificate.envelope().maximumHorizontalRadius();

        for (int i = 0; i < 257; i++) {
            double x = -horizontal + i * (2.0 * horizontal / 256.0);
            var column = columns.columnAt(new SkyIslandLocalPosition(x, 0.0));
            if (column.isEmpty()) {
                continue;
            }
            sampledColumns++;
            sampledMinimum =
                    Math.min(sampledMinimum, column.orElseThrow().undersideY());
            sampledMaximum =
                    Math.max(sampledMaximum, column.orElseThrow().upperY());
            if (column.orElseThrow().undersideY() < support.minimumY() - 1.0e-9
                    || column.orElseThrow().upperY() > support.maximumY() + 1.0e-9) {
                violations++;
            }
        }

        if (sampledColumns == 0) {
            throw new IllegalStateException("family slice produced no compiled columns");
        }
        return new FamilyRow(
                family.identifier(),
                query.minimumY(),
                query.maximumY(),
                support.minimumY(),
                support.maximumY(),
                sampledMinimum,
                sampledMaximum,
                sampledColumns,
                violations,
                query.maximumY() - query.minimumY(),
                support.maximumY() - support.minimumY());
    }

    private static BufferedImage renderFamily(
            FamilyRow row,
            SkyIslandAuthoredRealizationAssociation association,
            SkyIslandAuthoredRealizationSupportCertificate certificate) {
        BufferedImage image =
                new BufferedImage(
                        SPECIMEN_WIDTH,
                        SPECIMEN_HEIGHT,
                        BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = graphics(image);
        graphics.setColor(Color.BLACK);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        graphics.drawString(row.family().toUpperCase(Locale.ROOT), 10, 20);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        graphics.drawString(
                "query span="
                        + format(row.querySpan())
                        + "  support span="
                        + format(row.supportSpan())
                        + "  sampled violations="
                        + row.containmentViolations(),
                10,
                37);

        drawVerticalBounds(
                graphics,
                association.realizedVolume().bounds(),
                new Color(232, 232, 232),
                new Color(155, 155, 155));
        drawVerticalBounds(
                graphics,
                certificate.supportBounds(),
                new Color(224, 240, 225),
                new Color(70, 130, 78));
        drawSlice(graphics, association, new Color(82, 88, 96));

        graphics.dispose();
        return image;
    }

    private static StackRow stackScenario() {
        SkyIslandAuthoredRealizationAssociation lower =
                association(
                        5201L,
                        SkyIslandMorphologyFamily.MASSIF,
                        0.0,
                        0.0,
                        150.0,
                        55201L);
        SkyIslandAuthoredRealizationAssociation upper =
                association(
                        5202L,
                        SkyIslandMorphologyFamily.TABLELAND,
                        0.0,
                        0.0,
                        360.0,
                        55202L);
        SkyIslandAuthoredRealizationCatalog associations =
                catalog(List.of(upper, lower));
        double required = 40.0;
        SkyIslandAuthoredOverlapAdmissionPolicy policy =
                new SkyIslandAuthoredOverlapAdmissionPolicy(
                        List.of(
                                SkyIslandAuthoredOverlapPairRule.stacked(
                                        lower, upper, required)));
        SkyIslandAuthoredOverlapPairAudit broad =
                new SkyIslandAuthoredOverlapAdmissionAuditor(associations, policy)
                        .audit()
                        .pairAudits()
                        .get(0);
        SkyIslandAuthoredRealizationSupportCatalog support =
                SkyIslandAuthoredRealizationSupportCatalog.certifyAccepted(associations);
        SkyIslandAuthoredOverlapPairAudit certified =
                new SkyIslandAuthoredOverlapAdmissionAuditor(
                                associations, policy, support)
                        .audit()
                        .pairAudits()
                        .get(0);

        return new StackRow(
                lower,
                upper,
                support.certificateFor(lower).orElseThrow(),
                support.certificateFor(upper).orElseThrow(),
                broad.conservativeBoundsIntersect(),
                broad.status().name(),
                certified.status().name(),
                certified.conservativeVerticalGap(),
                required,
                support.certificateFor(lower)
                        .orElseThrow()
                        .supportBounds()
                        .maximumY(),
                support.certificateFor(upper)
                        .orElseThrow()
                        .supportBounds()
                        .minimumY());
    }

    private static BufferedImage renderStack(StackRow row) {
        BufferedImage image =
                new BufferedImage(
                        SPECIMEN_WIDTH,
                        SPECIMEN_HEIGHT,
                        BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = graphics(image);
        graphics.setColor(Color.BLACK);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        graphics.drawString("STACK PROOF", 10, 20);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        graphics.drawString(
                "broad="
                        + row.broadStatus()
                        + "  certified="
                        + row.certifiedStatus(),
                10,
                36);
        graphics.drawString(
                "query overlap="
                        + row.queryBoundsIntersect()
                        + "  proof gap="
                        + format(row.proofVerticalGap())
                        + " >= "
                        + format(row.minimumRequiredGap()),
                10,
                49);

        drawVerticalBounds(
                graphics,
                row.lower().realizedVolume().bounds(),
                new Color(238, 238, 238),
                new Color(170, 170, 170));
        drawVerticalBounds(
                graphics,
                row.upper().realizedVolume().bounds(),
                new Color(238, 238, 238),
                new Color(170, 170, 170));
        drawVerticalBounds(
                graphics,
                row.lowerCertificate().supportBounds(),
                new Color(224, 240, 225),
                new Color(65, 130, 75));
        drawVerticalBounds(
                graphics,
                row.upperCertificate().supportBounds(),
                new Color(224, 240, 225),
                new Color(65, 130, 75));
        drawSlice(graphics, row.lower(), new Color(82, 88, 96));
        drawSlice(graphics, row.upper(), new Color(82, 88, 96));

        int gapTop = yPixel(row.upperSupportMinimumY());
        int gapBottom = yPixel(row.lowerSupportMaximumY());
        graphics.setColor(new Color(40, 95, 165));
        graphics.setStroke(new BasicStroke(2.0f));
        graphics.drawLine(PLOT_X + PLOT_WIDTH - 14, gapTop, PLOT_X + PLOT_WIDTH - 14, gapBottom);
        graphics.drawLine(PLOT_X + PLOT_WIDTH - 20, gapTop, PLOT_X + PLOT_WIDTH - 8, gapTop);
        graphics.drawLine(PLOT_X + PLOT_WIDTH - 20, gapBottom, PLOT_X + PLOT_WIDTH - 8, gapBottom);

        graphics.dispose();
        return image;
    }

    private static void drawVerticalBounds(
            Graphics2D graphics,
            WorldBounds bounds,
            Color fill,
            Color border) {
        int left = clamp(xPixel(bounds.minimumX()), PLOT_X, PLOT_X + PLOT_WIDTH);
        int right = clamp(xPixel(bounds.maximumX()), PLOT_X, PLOT_X + PLOT_WIDTH);
        int top = clamp(yPixel(bounds.maximumY()), PLOT_Y, PLOT_Y + PLOT_HEIGHT);
        int bottom = clamp(yPixel(bounds.minimumY()), PLOT_Y, PLOT_Y + PLOT_HEIGHT);
        graphics.setColor(fill);
        graphics.fillRect(left, top, Math.max(1, right - left), Math.max(1, bottom - top));
        graphics.setColor(border);
        graphics.setStroke(new BasicStroke(1.2f));
        graphics.drawRect(left, top, Math.max(1, right - left), Math.max(1, bottom - top));
    }

    private static void drawSlice(
            Graphics2D graphics,
            SkyIslandAuthoredRealizationAssociation association,
            Color color) {
        SkyIslandCompiledVolumeColumnField columns =
                new SkyIslandCompiledVolumeColumnField(
                        association.realizedVolume().compiledVolume());
        var descriptor =
                association.realizedVolume().compiledVolume().descriptor();
        graphics.setColor(color);
        for (int px = 0; px < PLOT_WIDTH; px++) {
            double worldX = worldX(px);
            double localX = worldX - descriptor.centerX();
            var column = columns.columnAt(new SkyIslandLocalPosition(localX, 0.0));
            if (column.isEmpty()) {
                continue;
            }
            int top = yPixel(column.orElseThrow().upperY());
            int bottom = yPixel(column.orElseThrow().undersideY());
            graphics.drawLine(PLOT_X + px, top, PLOT_X + px, bottom);
        }
    }

    private static Graphics2D graphics(BufferedImage image) {
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.setColor(new Color(220, 220, 220));
        graphics.drawRect(PLOT_X, PLOT_Y, PLOT_WIDTH, PLOT_HEIGHT);
        return graphics;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static int xPixel(double worldX) {
        double minimum = -180.0;
        double maximum = 180.0;
        return PLOT_X
                + (int)
                        Math.round(
                                (worldX - minimum)
                                        / (maximum - minimum)
                                        * PLOT_WIDTH);
    }

    private static double worldX(int plotPixel) {
        double minimum = -180.0;
        double maximum = 180.0;
        return minimum + plotPixel * (maximum - minimum) / (PLOT_WIDTH - 1.0);
    }

    private static int yPixel(double y) {
        return PLOT_Y
                + PLOT_HEIGHT
                - (int)
                        Math.round(
                                (y - DISPLAY_MIN_Y)
                                        / (DISPLAY_MAX_Y - DISPLAY_MIN_Y)
                                        * PLOT_HEIGHT);
    }

    private static SkyIslandAuthoredRealizationAssociation association(
            long islandKey,
            SkyIslandMorphologyFamily family,
            double centerX,
            double centerZ,
            double suspension,
            long geometrySeed) {
        SkyIslandDescriptor authored =
                new SkyIslandDescriptor(
                        SkyIslandDescriptor.SCHEMA_VERSION,
                        SkyIslandIdentity.of(AUTHORED_WORLD, 8L, 81L, islandKey),
                        0x5100000000000000L ^ islandKey,
                        family,
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
        SkyIslandVolumeDescriptor physical =
                SkyIslandVolumeDescriptor.schema2(
                        geometrySeed,
                        centerX,
                        centerZ,
                        suspension,
                        RADIUS,
                        30.0,
                        40.0,
                        24.0,
                        0.17,
                        0.56,
                        0.55,
                        0.12,
                        family,
                        0.20,
                        34.0,
                        0.20);
        var compiled = new SemanticSkyIslandVolumeRecipe().compile(physical);
        return SkyIslandAuthoredRealizationAssociation.of(
                authored,
                new SkyIslandWorldVolume(
                        new SkyIslandWorldVolumeId(
                                REALIZATION_ROOT,
                                "auth51-" + islandKey,
                                0,
                                (int) islandKey,
                                geometrySeed),
                        new WorldBounds(
                                centerX - 180.0,
                                centerX + 180.0,
                                suspension - 220.0,
                                suspension + 220.0,
                                centerZ - 180.0,
                                centerZ + 180.0),
                        compiled));
    }

    private static SkyIslandAuthoredRealizationCatalog catalog(
            List<SkyIslandAuthoredRealizationAssociation> associations) {
        return new SkyIslandAuthoredRealizationCatalog(
                AUTHORED_WORLD, REALIZATION_ROOT, associations);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private record FamilyRow(
            String family,
            double queryMinimumY,
            double queryMaximumY,
            double supportMinimumY,
            double supportMaximumY,
            double sampledMinimumY,
            double sampledMaximumY,
            int sampledColumns,
            int containmentViolations,
            double querySpan,
            double supportSpan) {}

    private record StackRow(
            SkyIslandAuthoredRealizationAssociation lower,
            SkyIslandAuthoredRealizationAssociation upper,
            SkyIslandAuthoredRealizationSupportCertificate lowerCertificate,
            SkyIslandAuthoredRealizationSupportCertificate upperCertificate,
            boolean queryBoundsIntersect,
            String broadStatus,
            String certifiedStatus,
            double proofVerticalGap,
            double minimumRequiredGap,
            double lowerSupportMaximumY,
            double upperSupportMinimumY) {}
}
