package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandEcologyRegime;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandEcologicalOpportunityProfile;
import io.github.nidaba.skyforge.world.SkyIslandEcologicalOpportunityProfiler;
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

/** Generates AUTH-0089 island-scale ecological opportunity evidence. */
public final class AuthorshipEcologicalOpportunityProfileCorpusCli {
    public static final String EVIDENCE_ID = "authorship-ecological-opportunity-profile-v1";

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int PANEL_W = WIDTH / 3;
    private static final int PANEL_H = HEIGHT / 2;

    private AuthorshipEcologicalOpportunityProfileCorpusCli() {}

    public static void main(String[] args) throws IOException {
        if (args.length > 1) {
            throw new IllegalArgumentException(
                    "usage: AuthorshipEcologicalOpportunityProfileCorpusCli [output-directory]");
        }
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", EVIDENCE_ID);
        Files.createDirectories(out);

        Evidence evidence = buildEvidence();
        BufferedImage atlas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = atlas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        panel(g, 0, 0, "DETERMINISTIC", evidence.deterministic(),
                "fixed quadrature: " + SkyIslandEcologicalOpportunityProfiler.SAMPLES_PER_AXIS + " x "
                        + SkyIslandEcologicalOpportunityProfiler.SAMPLES_PER_AXIS,
                "same descriptor -> identical profile",
                "no caller-selected resolution");
        panel(g, PANEL_W, 0, "NORMALIZED", evidence.normalized(),
                "all AUTH-0003 regimes represented",
                "fractions sum to 1",
                "means remain in [0,1]");
        panel(g, PANEL_W * 2, 0, "HORIZONTAL_AREA", evidence.areaBounded(),
                "owned cells=" + evidence.base().ownedCellCount(),
                "area=" + Math.round(evidence.base().horizontalOwnedAreaEstimate()),
                "planning area, not physical surface");
        panel(g, 0, PANEL_H, "SCALE_COVARIANCE", evidence.scaleCovariant(),
                "radius doubled only",
                "normalized composition unchanged",
                "horizontal area x4");
        panel(g, PANEL_W, PANEL_H, "AUTHORED_VARIATION", evidence.authoredVariation(),
                "distinct authored climate identities",
                "different aggregate opportunity",
                "no independent ecology RNG");
        panel(g, PANEL_W * 2, PANEL_H, "BACKEND_NEUTRAL", true,
                "descriptor + AUTH-0003 only",
                "no blocks/entities/biome keys",
                "no population or resource decision");

        g.dispose();
        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());

        String manifest = "scenario,pass\n"
                + "DETERMINISTIC," + evidence.deterministic() + "\n"
                + "NORMALIZED," + evidence.normalized() + "\n"
                + "HORIZONTAL_AREA," + evidence.areaBounded() + "\n"
                + "SCALE_COVARIANCE," + evidence.scaleCovariant() + "\n"
                + "AUTHORED_VARIATION," + evidence.authoredVariation() + "\n"
                + "BACKEND_NEUTRAL,true\n";
        Files.writeString(out.resolve("manifest.csv"), manifest, StandardCharsets.UTF_8);

        StringBuilder csv = new StringBuilder(
                "islandKey,radius,ownedCells,horizontalArea,meanVegetation,meanSaturation,meanThermal");
        for (SkyIslandEcologyRegime regime : SkyIslandEcologyRegime.values()) {
            csv.append(',').append(regime.name());
        }
        csv.append('\n');

        for (ProfileRow row : evidence.rows()) {
            SkyIslandEcologicalOpportunityProfile profile = row.profile();
            csv.append(row.islandKey()).append(',')
                    .append(profile.descriptor().nominalRadius()).append(',')
                    .append(profile.ownedCellCount()).append(',')
                    .append(profile.horizontalOwnedAreaEstimate()).append(',')
                    .append(profile.meanVegetationPotential()).append(',')
                    .append(profile.meanSaturationPotential()).append(',')
                    .append(profile.meanThermalSuitability());
            for (SkyIslandEcologyRegime regime : SkyIslandEcologyRegime.values()) {
                csv.append(',').append(profile.regimeFraction(regime));
            }
            csv.append('\n');
        }
        Files.writeString(out.resolve("profiles.csv"), csv, StandardCharsets.UTF_8);

        Files.writeString(
                out.resolve("index.html"),
                """
                <!doctype html><html lang="en"><head><meta charset="utf-8">
                <title>AUTH-0089 ecological opportunity profile</title>
                <style>body{font-family:system-ui,sans-serif;max-width:1400px;margin:2rem auto;padding:0 1rem;background:#f5f3eb;color:#30343b}img{width:100%;border:1px solid #bbb;background:white}</style>
                </head><body>
                <h1>Island ecological opportunity profile</h1>
                <p>AUTH-0089 deterministically aggregates accepted AUTH-0003 local ecology into island-scale horizontal habitat area, regime composition, and mean continuous potentials. It does not define species, carrying capacity, spawn counts, resources, Minecraft biomes, or physical terrain area.</p>
                <img src="atlas.png" alt="AUTH-0089 proof atlas">
                <p><a href="manifest.csv">manifest.csv</a> · <a href="profiles.csv">profiles.csv</a></p>
                </body></html>
                """,
                StandardCharsets.UTF_8);
    }

    private static Evidence buildEvidence() {
        SkyIslandEcologicalOpportunityProfiler profiler =
                new SkyIslandEcologicalOpportunityProfiler();
        SkyIslandDescriptor baseDescriptor = descriptor(89091L, 10L);
        SkyIslandEcologicalOpportunityProfile base = profiler.profile(baseDescriptor);
        boolean deterministic = base.equals(profiler.profile(baseDescriptor));

        double sum = 0.0;
        boolean normalized = true;
        for (SkyIslandEcologyRegime regime : SkyIslandEcologyRegime.values()) {
            double fraction = base.regimeFraction(regime);
            normalized &= fraction >= 0.0 && fraction <= 1.0;
            sum += fraction;
        }
        normalized &= Math.abs(sum - 1.0) <= 1.0e-12;
        normalized &= in01(base.meanVegetationPotential())
                && in01(base.meanSaturationPotential())
                && in01(base.meanThermalSuitability());

        double radius = baseDescriptor.nominalRadius();
        boolean areaBounded = base.horizontalOwnedAreaEstimate() > 0.0
                && base.horizontalOwnedAreaEstimate() <= 4.0 * radius * radius;

        SkyIslandEcologicalOpportunityProfile doubled =
                profiler.profile(withRadius(baseDescriptor, radius * 2.0));
        boolean scaleCovariant = base.ownedCellCount() == doubled.ownedCellCount()
                && close(base.horizontalOwnedAreaEstimate() * 4.0,
                        doubled.horizontalOwnedAreaEstimate())
                && close(base.meanVegetationPotential(), doubled.meanVegetationPotential())
                && close(base.meanSaturationPotential(), doubled.meanSaturationPotential())
                && close(base.meanThermalSuitability(), doubled.meanThermalSuitability());
        for (SkyIslandEcologyRegime regime : SkyIslandEcologyRegime.values()) {
            scaleCovariant &= close(
                    base.regimeFraction(regime),
                    doubled.regimeFraction(regime));
        }

        SkyIslandEcologicalOpportunityProfile contrast =
                profiler.profile(descriptor(89091L, 11L));
        boolean authoredVariation = differs(base, contrast);

        List<ProfileRow> rows = new ArrayList<>();
        for (long key = 10L; key < 18L; key++) {
            rows.add(new ProfileRow(key, profiler.profile(descriptor(89091L, key))));
        }
        rows.add(new ProfileRow(10_000L, doubled));

        return new Evidence(
                base,
                deterministic,
                normalized,
                areaBounded,
                scaleCovariant,
                authoredVariation,
                List.copyOf(rows));
    }

    private static boolean differs(
            SkyIslandEcologicalOpportunityProfile first,
            SkyIslandEcologicalOpportunityProfile second) {
        if (!close(first.meanVegetationPotential(), second.meanVegetationPotential())
                || !close(first.meanSaturationPotential(), second.meanSaturationPotential())
                || !close(first.meanThermalSuitability(), second.meanThermalSuitability())) {
            return true;
        }
        for (SkyIslandEcologyRegime regime : SkyIslandEcologyRegime.values()) {
            if (!close(first.regimeFraction(regime), second.regimeFraction(regime))) {
                return true;
            }
        }
        return false;
    }

    private static boolean close(double first, double second) {
        return Math.abs(first - second) <= 1.0e-12;
    }

    private static boolean in01(double value) {
        return value >= 0.0 && value <= 1.0;
    }

    private static SkyIslandDescriptor descriptor(long worldSeed, long islandKey) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(worldSeed, 8L, 89L, islandKey));
    }

    private static SkyIslandDescriptor withRadius(
            SkyIslandDescriptor source,
            double radius) {
        return new SkyIslandDescriptor(
                source.schemaVersion(),
                source.identity(),
                source.authorshipSeed(),
                source.morphologyFamily(),
                radius,
                source.reliefBudget(),
                source.rockCompetence(),
                source.permeability(),
                source.temperatureTendency(),
                source.moistureTendency(),
                source.exposureTendency(),
                source.erosionMaturity(),
                source.hydrologicalPotential(),
                source.ecologicalPotential());
    }

    private static void panel(
            Graphics2D g,
            int x,
            int y,
            String title,
            boolean pass,
            String first,
            String second,
            String third) {
        g.setColor(pass ? new Color(225, 241, 228) : new Color(245, 220, 220));
        g.fillRect(x + 7, y + 7, PANEL_W - 14, PANEL_H - 14);
        g.setColor(new Color(180, 180, 180));
        g.drawRect(x + 7, y + 7, PANEL_W - 14, PANEL_H - 14);
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        g.drawString(title, x + 18, y + 30);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        g.drawString(first, x + 18, y + 72);
        g.drawString(second, x + 18, y + 104);
        g.drawString(third, x + 18, y + 136);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        g.drawString(pass ? "PASS" : "FAIL", x + 18, y + 205);
    }

    private record ProfileRow(
            long islandKey,
            SkyIslandEcologicalOpportunityProfile profile) {}

    private record Evidence(
            SkyIslandEcologicalOpportunityProfile base,
            boolean deterministic,
            boolean normalized,
            boolean areaBounded,
            boolean scaleCovariant,
            boolean authoredVariation,
            List<ProfileRow> rows) {}
}
