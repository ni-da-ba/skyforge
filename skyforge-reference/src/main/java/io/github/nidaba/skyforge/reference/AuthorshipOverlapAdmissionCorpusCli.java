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
import javax.imageio.ImageIO;

/** Generates deterministic AUTH-0050 authored-overlap admission evidence. */
public final class AuthorshipOverlapAdmissionCorpusCli {
    private static final long AUTHORED_WORLD = 0x4155544830303530L;
    private static final long REALIZATION_ROOT = 0x5245414C30303530L;
    private static final double RADIUS = 100.0;
    private static final int WIDTH = 1180;
    private static final int HEADER = 54;
    private static final int ROW_HEIGHT = 48;

    private AuthorshipOverlapAdmissionCorpusCli() {}

    public static void main(String[] args) throws IOException {
        Path out =
                args.length == 1
                        ? Path.of(args[0])
                        : Path.of("build", "evidence", "authorship-overlap-admission-v1");
        Files.createDirectories(out);

        List<Row> rows = scenarios();
        BufferedImage atlas =
                new BufferedImage(
                        WIDTH,
                        HEADER + rows.size() * ROW_HEIGHT,
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
                        "scenario,mode,status,admitted,boundsIntersect,supportDisjoint,"
                                + "verticalGap,minimumVerticalSeparation,witnessPresent\n");
        for (Row row : rows) {
            manifest.append(row.scenario()).append(',')
                    .append(row.audit().rule().mode()).append(',')
                    .append(row.audit().status()).append(',')
                    .append(row.audit().admitted()).append(',')
                    .append(row.audit().conservativeBoundsIntersect()).append(',')
                    .append(row.audit().nativeSupportDiscsDisjoint()).append(',')
                    .append(row.audit().conservativeVerticalGap()).append(',')
                    .append(row.audit().rule().minimumVerticalSeparation()).append(',')
                    .append(row.audit().witness().isPresent()).append('\n');
        }
        Files.writeString(
                out.resolve("manifest.csv"),
                manifest.toString(),
                StandardCharsets.UTF_8);

        Files.writeString(
                out.resolve("index.html"),
                "<!doctype html><meta charset=\"utf-8\">"
                        + "<title>AUTH-0050</title>"
                        + "<h1>Authored-realization overlap admission</h1>"
                        + "<p>Green rows are proof-grade separation/stack admission. Blue is explicit "
                        + "COMPOSE permission. Red rows are fail-closed rejection. Witness presence "
                        + "proves actual AUTH-0048 overlap when present; witness absence never proves "
                        + "continuous separation.</p>"
                        + "<img src=\"atlas.png\" style=\"max-width:100%\">"
                        + "<p><a href=\"manifest.csv\">manifest.csv</a></p>",
                StandardCharsets.UTF_8);
    }

    private static List<Row> scenarios() {
        ArrayList<Row> rows = new ArrayList<>();

        var disjointA = association(101L, -400, 0, 200, 50101L, bounds(-550, -250, 80, 320, -150, 150));
        var disjointB = association(102L, 400, 0, 200, 50102L, bounds(250, 550, 80, 320, -150, 150));
        rows.add(row("DISJOINT_BOUNDS", List.of(disjointA, disjointB), SkyIslandAuthoredOverlapAdmissionPolicy.strict()));

        var supportA = association(201L, -110, 0, 200, 50201L, bounds(-280, 60, 80, 320, -170, 170));
        var supportB = association(202L, 110, 0, 200, 50202L, bounds(-60, 280, 80, 320, -170, 170));
        rows.add(row("OVERLAP_BOUNDS_SUPPORT_DISJOINT", List.of(supportA, supportB), SkyIslandAuthoredOverlapAdmissionPolicy.strict()));

        var stackLower = association(301L, 0, 0, 140, 50301L, bounds(-140, 140, 50, 210, -140, 140));
        var stackUpper = association(302L, 0, 0, 360, 50302L, bounds(-140, 140, 290, 440, -140, 140));
        rows.add(
                row(
                        "STACK_CERTIFIED",
                        List.of(stackUpper, stackLower),
                        new SkyIslandAuthoredOverlapAdmissionPolicy(
                                List.of(
                                        SkyIslandAuthoredOverlapPairRule.stacked(
                                                stackLower, stackUpper, 60.0)))));

        var broadLower = association(401L, 0, 0, 140, 50401L, bounds(-140, 140, 0, 300, -140, 140));
        var broadUpper = association(402L, 0, 0, 360, 50402L, bounds(-140, 140, 180, 500, -140, 140));
        rows.add(
                row(
                        "STACK_BROAD_UNCERTIFIED",
                        List.of(broadLower, broadUpper),
                        new SkyIslandAuthoredOverlapAdmissionPolicy(
                                List.of(
                                        SkyIslandAuthoredOverlapPairRule.stacked(
                                                broadLower, broadUpper, 40.0)))));
        rows.add(row("STRICT_UNCERTIFIED", List.of(broadUpper, broadLower), SkyIslandAuthoredOverlapAdmissionPolicy.strict()));

        var overlapA = association(501L, 500, -300, 240, 50501L, bounds(350, 650, 80, 400, -450, -150));
        var overlapB = association(502L, 500, -300, 240, 50502L, bounds(350, 650, 80, 400, -450, -150));
        rows.add(row("STRICT_TRUE_OVERLAP", List.of(overlapA, overlapB), SkyIslandAuthoredOverlapAdmissionPolicy.strict()));
        rows.add(
                row(
                        "COMPOSE_TRUE_OVERLAP",
                        List.of(overlapB, overlapA),
                        new SkyIslandAuthoredOverlapAdmissionPolicy(
                                List.of(
                                        SkyIslandAuthoredOverlapPairRule.compose(
                                                overlapA, overlapB)))));

        return List.copyOf(rows);
    }

    private static Row row(
            String scenario,
            List<SkyIslandAuthoredRealizationAssociation> associations,
            SkyIslandAuthoredOverlapAdmissionPolicy policy) {
        SkyIslandAuthoredOverlapPairAudit audit =
                new SkyIslandAuthoredOverlapAdmissionAuditor(
                                new SkyIslandAuthoredRealizationCatalog(
                                        AUTHORED_WORLD, REALIZATION_ROOT, associations),
                                policy)
                        .audit()
                        .pairAudits()
                        .get(0);
        return new Row(scenario, audit);
    }

    private static void renderHeader(Graphics2D graphics) {
        graphics.setColor(Color.BLACK);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        graphics.drawString("AUTH-0050 authored-realization overlap admission", 10, 20);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        graphics.drawString(
                "proof-grade admission only; finite witness absence never becomes a separation certificate",
                10,
                38);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 9));
        int y = 51;
        graphics.drawString("SCENARIO", 10, y);
        graphics.drawString("MODE", 286, y);
        graphics.drawString("STATUS", 372, y);
        graphics.drawString("ADMIT", 650, y);
        graphics.drawString("BOUNDS", 718, y);
        graphics.drawString("SUPPORT", 795, y);
        graphics.drawString("VGAP", 880, y);
        graphics.drawString("MIN", 945, y);
        graphics.drawString("WITNESS", 1005, y);
    }

    private static void renderRow(Graphics2D graphics, Row row, int y) {
        graphics.setColor(rowColor(row.audit()));
        graphics.fillRect(0, y, WIDTH, ROW_HEIGHT - 2);
        graphics.setColor(Color.BLACK);
        graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
        int baseline = y + 28;
        graphics.drawString(row.scenario(), 10, baseline);
        graphics.drawString(row.audit().rule().mode().name(), 286, baseline);
        graphics.drawString(row.audit().status().name(), 372, baseline);
        graphics.drawString(mark(row.audit().admitted()), 660, baseline);
        graphics.drawString(mark(row.audit().conservativeBoundsIntersect()), 730, baseline);
        graphics.drawString(mark(row.audit().nativeSupportDiscsDisjoint()), 810, baseline);
        graphics.drawString(String.format(java.util.Locale.ROOT, "%.1f", row.audit().conservativeVerticalGap()), 880, baseline);
        graphics.drawString(String.format(java.util.Locale.ROOT, "%.1f", row.audit().rule().minimumVerticalSeparation()), 945, baseline);
        graphics.drawString(mark(row.audit().witness().isPresent()), 1020, baseline);
    }

    private static Color rowColor(SkyIslandAuthoredOverlapPairAudit audit) {
        if (audit.status().name().equals("ACCEPTED_EXPLICIT_COMPOSITION")) {
            return new Color(205, 221, 240);
        }
        return audit.admitted()
                ? new Color(207, 230, 210)
                : new Color(241, 207, 207);
    }

    private static String mark(boolean value) {
        return value ? "YES" : "NO";
    }

    private static SkyIslandAuthoredRealizationAssociation association(
            long islandKey,
            double centerX,
            double centerZ,
            double suspension,
            long geometrySeed,
            WorldBounds bounds) {
        SkyIslandDescriptor authored =
                new SkyIslandDescriptor(
                        SkyIslandDescriptor.SCHEMA_VERSION,
                        SkyIslandIdentity.of(AUTHORED_WORLD, 8L, 81L, islandKey),
                        0x5000000000000000L ^ islandKey,
                        SkyIslandMorphologyFamily.MASSIF,
                        RADIUS,
                        80.0,
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
                        32.0,
                        44.0,
                        28.0,
                        0.0,
                        0.45,
                        0.60,
                        0.06,
                        SkyIslandMorphologyFamily.MASSIF,
                        0.10,
                        30.0,
                        0.18);
        var compiled = new SemanticSkyIslandVolumeRecipe().compile(physical);
        return SkyIslandAuthoredRealizationAssociation.of(
                authored,
                new SkyIslandWorldVolume(
                        new SkyIslandWorldVolumeId(
                                REALIZATION_ROOT,
                                "auth50-" + islandKey,
                                0,
                                (int) (islandKey & 0x7fff),
                                geometrySeed),
                        bounds,
                        compiled));
    }

    private static WorldBounds bounds(
            double minimumX,
            double maximumX,
            double minimumY,
            double maximumY,
            double minimumZ,
            double maximumZ) {
        return new WorldBounds(
                minimumX, maximumX, minimumY, maximumY, minimumZ, maximumZ);
    }

    private record Row(String scenario, SkyIslandAuthoredOverlapPairAudit audit) {}
}
