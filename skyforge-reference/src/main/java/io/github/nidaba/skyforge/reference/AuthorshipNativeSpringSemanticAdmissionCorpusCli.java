package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.*;
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
import javax.imageio.ImageIO;

/** Generates AUTH-0085 native-spring semantic-admission proof evidence. */
public final class AuthorshipNativeSpringSemanticAdmissionCorpusCli {
    public static final String EVIDENCE_ID = "authorship-native-spring-semantic-admission-v1";

    private static final long SEED = 0x534B59464F524745L;
    private static final long[] REPRESENTATIVE_KEYS = {
        653L, 3670L, 1051L, 1439L, 913L, 512L, 811L, 83L, 118L, 241L, 7L, 10L
    };

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int PANEL_W = WIDTH / 3;
    private static final int PANEL_H = HEIGHT / 2;

    private AuthorshipNativeSpringSemanticAdmissionCorpusCli() {}

    public static void main(String[] arguments) throws IOException {
        if (arguments.length > 1) {
            throw new IllegalArgumentException(
                    "usage: AuthorshipNativeSpringSemanticAdmissionCorpusCli [output-directory]");
        }

        Path output = arguments.length == 1
                ? Path.of(arguments[0])
                : Path.of("build", "evidence", EVIDENCE_ID);
        Files.createDirectories(output);

        Fixture admittedFixture = admittedFixture();
        SkyIslandNativeSpringAdmission admitted = evaluate(
                admittedFixture,
                admittedFixture.position(),
                SkyIslandNativeSpringFluidKind.WATER);
        SkyIslandNativeSpringAdmission molten = evaluate(
                admittedFixture,
                admittedFixture.position(),
                SkyIslandNativeSpringFluidKind.MOLTEN);

        SkyIslandSubsurfacePosition nonCavePosition = nonCaveOwnedPosition(admittedFixture);
        SkyIslandNativeSpringAdmission nonCave = evaluate(
                admittedFixture,
                nonCavePosition,
                SkyIslandNativeSpringFluidKind.WATER);

        Candidate noAquiferCandidate = noAquiferCaveCandidate();
        SkyIslandNativeSpringAdmission noAquifer = SkyIslandNativeSpringAdmissionPolicy.evaluate(
                noAquiferCandidate.regions(),
                noAquiferCandidate.caves(),
                noAquiferCandidate.position(),
                SkyIslandNativeSpringFluidKind.WATER);

        double radius = admittedFixture.regions().descriptor().nominalRadius();
        SkyIslandSubsurfacePosition outsidePosition =
                new SkyIslandSubsurfacePosition(radius * 1.5, 0.0, 0.5);
        SkyIslandNativeSpringAdmission outside = evaluate(
                admittedFixture,
                outsidePosition,
                SkyIslandNativeSpringFluidKind.WATER);

        writeAtlas(output, admitted, molten, nonCave, noAquifer, outside);
        writeManifest(output, admitted, molten, nonCave, noAquifer, outside);
        writePositions(output, admitted, molten, nonCave, noAquifer, outside);
        Files.writeString(output.resolve("index.html"), indexHtml(), StandardCharsets.UTF_8);

        System.out.println(output.resolve("index.html").toAbsolutePath());
    }

    private static SkyIslandNativeSpringAdmission evaluate(
            Fixture fixture,
            SkyIslandSubsurfacePosition position,
            SkyIslandNativeSpringFluidKind fluidKind) {
        return SkyIslandNativeSpringAdmissionPolicy.evaluate(
                fixture.regions(), fixture.caves(), position, fluidKind);
    }

    private static void writeAtlas(
            Path output,
            SkyIslandNativeSpringAdmission admitted,
            SkyIslandNativeSpringAdmission molten,
            SkyIslandNativeSpringAdmission nonCave,
            SkyIslandNativeSpringAdmission noAquifer,
            SkyIslandNativeSpringAdmission outside)
            throws IOException {
        BufferedImage atlas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = atlas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        panel(g, 0, 0, "AQUIFER_CAVE_WATER", admitted);
        line(g, 0, 0, 70, "semantic result: ADMITTED");
        line(g, 0, 0, 100, "cave source: " + admitted.caveSourceKind());
        line(g, 0, 0, 130, "aquifer region/cell: "
                + admitted.aquiferRegionId() + "/" + admitted.aquiferCellIndex());

        panel(g, PANEL_W, 0, "SAME_SITE_MOLTEN", molten);
        line(g, PANEL_W, 0, 70, "semantic result: BLOCKED");
        line(g, PANEL_W, 0, 100, "reason: MISSING_GEOTHERMAL_SEMANTICS");
        line(g, PANEL_W, 0, 130, "same authored cave position as admitted water");

        panel(g, PANEL_W * 2, 0, "OWNED_NON_CAVE_WATER", nonCave);
        line(g, PANEL_W * 2, 0, 70, "semantic result: BLOCKED");
        line(g, PANEL_W * 2, 0, 100, "reason: NOT_AUTHORED_CAVE_INTERIOR");
        line(g, PANEL_W * 2, 0, 130, "island geology ownership alone is insufficient");

        panel(g, 0, PANEL_H, "CAVE_NO_AQUIFER", noAquifer);
        line(g, 0, PANEL_H, 70, "semantic result: BLOCKED");
        line(g, 0, PANEL_H, 100, "reason: NO_AQUIFER_SUPPORT");
        line(g, 0, PANEL_H, 130, "authored cave alone is insufficient");

        panel(g, PANEL_W, PANEL_H, "OUTSIDE_ISLAND", outside);
        line(g, PANEL_W, PANEL_H, 70, "semantic result: BLOCKED");
        line(g, PANEL_W, PANEL_H, 100, "reason: OUTSIDE_AUTHORED_ISLAND");
        line(g, PANEL_W, PANEL_H, 130, "no cave/aquifer provenance may be claimed");

        panelTitleOnly(g, PANEL_W * 2, PANEL_H, "BACKEND_NEUTRAL_POLICY");
        line(g, PANEL_W * 2, PANEL_H, 70, "Minecraft feature/fluid keys: NONE");
        line(g, PANEL_W * 2, PANEL_H, 100, "block placement / propagation: NONE");
        line(g, PANEL_W * 2, PANEL_H, 130, "consumer: downstream FLUID_SPRINGS admission");

        g.dispose();
        ImageIO.write(atlas, "png", output.resolve("atlas.png").toFile());
    }

    private static void panel(
            Graphics2D g,
            int x,
            int y,
            String title,
            SkyIslandNativeSpringAdmission admission) {
        panelTitleOnly(g, x, y, title);
        line(g, x, y, 42, admission.fluidKind() + " / " + admission.status());
    }

    private static void panelTitleOnly(Graphics2D g, int x, int y, String title) {
        g.setColor(new Color(244, 240, 230));
        g.fillRect(x + 8, y + 8, PANEL_W - 16, PANEL_H - 16);
        g.setColor(new Color(180, 180, 180));
        g.drawRect(x + 8, y + 8, PANEL_W - 16, PANEL_H - 16);
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        g.drawString(title, x + 18, y + 28);
    }

    private static void line(Graphics2D g, int x, int y, int offsetY, String text) {
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        g.drawString(text, x + 18, y + offsetY);
    }

    private static void writeManifest(
            Path output,
            SkyIslandNativeSpringAdmission admitted,
            SkyIslandNativeSpringAdmission molten,
            SkyIslandNativeSpringAdmission nonCave,
            SkyIslandNativeSpringAdmission noAquifer,
            SkyIslandNativeSpringAdmission outside)
            throws IOException {
        StringBuilder csv = new StringBuilder(
                "scenario,fluidKind,admitted,status,caveSource,caveSystem,aquiferRegion,aquiferCell,aquiferMembership\n");
        row(csv, "aquifer-cave-water", admitted);
        row(csv, "same-site-molten", molten);
        row(csv, "owned-non-cave-water", nonCave);
        row(csv, "cave-no-aquifer-water", noAquifer);
        row(csv, "outside-island-water", outside);
        Files.writeString(output.resolve("manifest.csv"), csv, StandardCharsets.UTF_8);
    }

    private static void row(
            StringBuilder csv,
            String scenario,
            SkyIslandNativeSpringAdmission admission) {
        csv.append(scenario).append(',')
                .append(admission.fluidKind()).append(',')
                .append(admission.admitted()).append(',')
                .append(admission.status()).append(',')
                .append(admission.caveSourceKind()).append(',')
                .append(admission.caveSystemId()).append(',')
                .append(admission.aquiferRegionId()).append(',')
                .append(admission.aquiferCellIndex()).append(',')
                .append(admission.aquiferMembership()).append('\n');
    }

    private static void writePositions(
            Path output,
            SkyIslandNativeSpringAdmission... admissions)
            throws IOException {
        StringBuilder csv = new StringBuilder(
                "status,x,z,depthFraction\n");
        for (SkyIslandNativeSpringAdmission admission : admissions) {
            csv.append(admission.status()).append(',')
                    .append(admission.position().x()).append(',')
                    .append(admission.position().z()).append(',')
                    .append(admission.position().depthFraction()).append('\n');
        }
        Files.writeString(output.resolve("positions.csv"), csv, StandardCharsets.UTF_8);
    }

    private static String indexHtml() {
        return """
                <!doctype html>
                <html lang="en"><head><meta charset="utf-8">
                <title>AUTH-0085 native spring semantic admission</title>
                <style>body{font-family:system-ui,sans-serif;max-width:1400px;margin:2rem auto;padding:0 1rem;background:#f4f0e6;color:#30343b}img{width:100%;border:1px solid #bbb;background:white}</style>
                </head><body>
                <h1>Native spring semantic admission</h1>
                <p>AUTH-0085 proof: native water requires accepted aquifer + authored cave semantics. Molten fluid fails closed until geothermal/volcanic authorship exists. This corpus performs no Minecraft placement or fluid propagation.</p>
                <img src="atlas.png" alt="AUTH-0085 proof atlas">
                <p><a href="manifest.csv">manifest.csv</a> · <a href="positions.csv">positions.csv</a></p>
                </body></html>
                """;
    }

    private static Fixture admittedFixture() {
        for (long key : REPRESENTATIVE_KEYS) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandGeologicRegionPlan regions = SkyIslandGeologicRegionPlanner.plan(descriptor);
            SkyIslandExteriorConnectedCaveVolumeField caves =
                    SkyIslandExteriorConnectedCaveVolumeField.create(descriptor);
            for (SkyIslandGeologicRegion region : regions.regions()) {
                if (region.kind() != SkyIslandGeologicRegionKind.AQUIFER_BODY) {
                    continue;
                }
                for (SkyIslandGeologicRegionCell cell : region.cells()) {
                    if (caves.contains(cell.position())) {
                        return new Fixture(regions, caves, cell.position());
                    }
                }
            }
        }
        throw new IllegalStateException(
                "representative corpus contains no aquifer/cave water admission fixture");
    }

    private static Candidate noAquiferCaveCandidate() {
        for (long key : REPRESENTATIVE_KEYS) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandGeologicRegionPlan regions = SkyIslandGeologicRegionPlanner.plan(descriptor);
            SkyIslandExteriorConnectedCaveVolumeField caves =
                    SkyIslandExteriorConnectedCaveVolumeField.create(descriptor);
            double radius = descriptor.nominalRadius();

            for (int iz = 0; iz < regions.gridSize(); iz++) {
                double z = -radius + iz * regions.horizontalSpacing();
                for (int id = 0; id < regions.depthSamples(); id++) {
                    double depth = id * regions.depthSpacing();
                    for (int ix = 0; ix < regions.gridSize(); ix++) {
                        double x = -radius + ix * regions.horizontalSpacing();
                        SkyIslandSubsurfacePosition position =
                                new SkyIslandSubsurfacePosition(x, z, depth);
                        if (!caves.contains(position)) {
                            continue;
                        }
                        SkyIslandNativeSpringAdmission admission =
                                SkyIslandNativeSpringAdmissionPolicy.evaluate(
                                        regions,
                                        caves,
                                        position,
                                        SkyIslandNativeSpringFluidKind.WATER);
                        if (admission.status()
                                == SkyIslandNativeSpringAdmissionStatus.NO_AQUIFER_SUPPORT) {
                            return new Candidate(regions, caves, position);
                        }
                    }
                }
            }
        }
        throw new IllegalStateException(
                "representative corpus contains no cave/no-aquifer rejection fixture");
    }

    private static SkyIslandSubsurfacePosition nonCaveOwnedPosition(Fixture fixture) {
        double radius = fixture.regions().descriptor().nominalRadius();
        SkyIslandGeologyFieldSet geology =
                SkyIslandGeologyFieldSet.create(fixture.regions().descriptor());

        for (int iz = 1; iz < 12; iz++) {
            double z = -radius + 2.0 * radius * iz / 12.0;
            for (int ix = 1; ix < 12; ix++) {
                double x = -radius + 2.0 * radius * ix / 12.0;
                for (int id = 1; id < 8; id++) {
                    SkyIslandSubsurfacePosition position =
                            new SkyIslandSubsurfacePosition(x, z, id / 8.0);
                    if (geology.sample(position).owned() && !fixture.caves().contains(position)) {
                        return position;
                    }
                }
            }
        }
        throw new IllegalStateException("representative island has no owned non-cave sample");
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 8L, 81L, key));
    }

    private record Fixture(
            SkyIslandGeologicRegionPlan regions,
            SkyIslandExteriorConnectedCaveVolumeField caves,
            SkyIslandSubsurfacePosition position) {}

    private record Candidate(
            SkyIslandGeologicRegionPlan regions,
            SkyIslandExteriorConnectedCaveVolumeField caves,
            SkyIslandSubsurfacePosition position) {}
}
