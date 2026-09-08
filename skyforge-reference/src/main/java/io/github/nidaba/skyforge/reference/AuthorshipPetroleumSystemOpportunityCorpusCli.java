package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandGeologyFieldSet;
import io.github.nidaba.skyforge.world.SkyIslandGeologySample;
import io.github.nidaba.skyforge.world.SkyIslandPetroleumSystemOpportunityCell;
import io.github.nidaba.skyforge.world.SkyIslandPetroleumSystemOpportunityProfile;
import io.github.nidaba.skyforge.world.SkyIslandPetroleumSystemOpportunityProfiler;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;

/** Generates AUTH-0098 petroleum-system geological-opportunity evidence. */
public final class AuthorshipPetroleumSystemOpportunityCorpusCli {
    public static final String EVIDENCE_ID = "authorship-petroleum-system-opportunity-v1";

    private static final long WORLD = 0x4155544830303938L;
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int PANEL_W = WIDTH / 3;
    private static final int PANEL_H = HEIGHT / 2;

    private AuthorshipPetroleumSystemOpportunityCorpusCli() {}

    public static void main(String[] args) throws IOException {
        if (args.length > 1) {
            throw new IllegalArgumentException(
                    "usage: AuthorshipPetroleumSystemOpportunityCorpusCli [output-directory]");
        }
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", EVIDENCE_ID);
        Files.createDirectories(out);

        SkyIslandPetroleumSystemOpportunityProfiler profiler =
                new SkyIslandPetroleumSystemOpportunityProfiler();
        SkyIslandDescriptor referenceDescriptor = descriptor(98101L, 180.0);
        SkyIslandPetroleumSystemOpportunityProfile reference =
                profiler.profile(referenceDescriptor);
        SkyIslandPetroleumSystemOpportunityProfile repeat =
                profiler.profile(referenceDescriptor);
        SkyIslandPetroleumSystemOpportunityProfile scaled =
                profiler.profile(withRadius(referenceDescriptor, 360.0));

        boolean exactProvenance = reference.cells().stream()
                .map(SkyIslandPetroleumSystemOpportunityCell::sourceCell)
                .toList()
                .equals(reference.sourcePlan().cells());
        boolean componentGating = componentGating(reference);
        boolean verticalSystem = verticalSystem(reference);
        boolean deterministic = reference.cells().equals(repeat.cells());
        boolean scaleCovariant = scaleCovariant(reference, scaled);
        boolean noPolicy = noPolicySurface(reference);

        BufferedImage atlas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = atlas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        panel(g, 0, 0, "AUTH0033_PROVENANCE", exactProvenance,
                "exact host planning lattice retained",
                "exact cell identity/order",
                "no caller grid or cell injection");
        panel(g, PANEL_W, 0, "COMPONENT_GATING", componentGating,
                "source <= layered host",
                "reservoir <= connected permeability",
                "seal <= low-permeability support");
        panel(g, PANEL_W * 2, 0, "VERTICAL_SYSTEM", verticalSystem,
                "deeper source + local reservoir",
                "shallower seal required",
                "no eligibility threshold");
        panel(g, 0, PANEL_H, "DETERMINISTIC", deterministic,
                "same authored descriptor -> same cells",
                "stable organic-source affinity",
                "no backend/runtime state");
        panel(g, PANEL_W, PANEL_H, "SCALE_COVARIANT", scaleCovariant,
                "radius-only scale preserves values",
                "normalized island/depth coordinates",
                "physical deposit scale remains downstream");
        panel(g, PANEL_W * 2, PANEL_H, "NO_RESOURCE_POLICY", noPolicy,
                "no reserves / deposit count / grade",
                "no Strategic-Node frequency",
                "no Minecraft/mod placement");

        g.dispose();
        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());

        String manifest = "scenario,pass\n"
                + "AUTH0033_PROVENANCE," + exactProvenance + "\n"
                + "COMPONENT_GATING," + componentGating + "\n"
                + "VERTICAL_SYSTEM," + verticalSystem + "\n"
                + "DETERMINISTIC," + deterministic + "\n"
                + "SCALE_COVARIANT," + scaleCovariant + "\n"
                + "NO_RESOURCE_POLICY," + noPolicy + "\n";
        Files.writeString(out.resolve("manifest.csv"), manifest, StandardCharsets.UTF_8);

        StringBuilder profiles = new StringBuilder(
                "key,activeHostCells,meanSource,meanReservoir,meanSeal,meanSystem,peakSystem,nonzeroSystemCells\n");
        for (long key : new long[] {98101L, 98102L, 98103L, 98104L, 98105L, 98106L}) {
            SkyIslandPetroleumSystemOpportunityProfile profile =
                    profiler.profile(descriptor(key, 180.0));
            profiles.append(key).append(',')
                    .append(profile.activeHostCells()).append(',')
                    .append(profile.meanSourcePotential()).append(',')
                    .append(profile.meanReservoirPotential()).append(',')
                    .append(profile.meanSealPotential()).append(',')
                    .append(profile.meanSystemOpportunity()).append(',')
                    .append(profile.peakSystemOpportunity()).append(',')
                    .append(profile.nonzeroSystemCellCount()).append('\n');
        }
        Files.writeString(out.resolve("profiles.csv"), profiles, StandardCharsets.UTF_8);

        StringBuilder cells = new StringBuilder(
                "index,xIndex,depthIndex,zIndex,layeredHost,source,reservoir,seal,deeperSource,shallowerSeal,system\n");
        for (SkyIslandPetroleumSystemOpportunityCell cell : reference.cells()) {
            cells.append(cell.sourceCell().index()).append(',')
                    .append(cell.sourceCell().xIndex()).append(',')
                    .append(cell.sourceCell().depthIndex()).append(',')
                    .append(cell.sourceCell().zIndex()).append(',')
                    .append(cell.sourceCell().layeredFabricRichHost()).append(',')
                    .append(cell.sourcePotential()).append(',')
                    .append(cell.reservoirPotential()).append(',')
                    .append(cell.sealPotential()).append(',')
                    .append(cell.deeperSourceSupport()).append(',')
                    .append(cell.shallowerSealSupport()).append(',')
                    .append(cell.systemOpportunity()).append('\n');
        }
        Files.writeString(out.resolve("cells.csv"), cells, StandardCharsets.UTF_8);

        Files.writeString(
                out.resolve("index.html"),
                """
                <!doctype html><html lang="en"><head><meta charset="utf-8">
                <title>AUTH-0098 petroleum-system geological opportunity</title>
                <style>body{font-family:system-ui,sans-serif;max-width:1400px;margin:2rem auto;padding:0 1rem;background:#f4f3ed;color:#30343b}img{width:100%;border:1px solid #bbb;background:white}</style>
                </head><body>
                <h1>Petroleum-system geological opportunity</h1>
                <p>AUTH-0098 adds normalized source/reservoir/seal system opportunity over the exact AUTH-0033 material-family planning lattice.</p>
                <p>This package proves backend-neutral geological planning evidence only. It does not define literal oil deposits, reserves, pressure, grade, availability classes, pumpjacks, refineries, or Minecraft/mod placement.</p>
                <img src="atlas.png" alt="AUTH-0098 proof atlas">
                <p><a href="manifest.csv">manifest.csv</a> · <a href="profiles.csv">profiles.csv</a> · <a href="cells.csv">cells.csv</a></p>
                </body></html>
                """,
                StandardCharsets.UTF_8);

        System.out.println(out.resolve("index.html").toAbsolutePath());
    }

    private static boolean componentGating(
            SkyIslandPetroleumSystemOpportunityProfile profile) {
        SkyIslandGeologyFieldSet geology =
                SkyIslandGeologyFieldSet.create(profile.descriptor());
        boolean observedSource = false;
        boolean observedReservoir = false;
        boolean observedSeal = false;
        for (SkyIslandPetroleumSystemOpportunityCell cell : profile.cells()) {
            SkyIslandGeologySample geologic = geology.sample(cell.sourceCell().position());
            if (cell.sourcePotential() > cell.sourceCell().layeredFabricRichHost() + 1.0e-15
                    || cell.reservoirPotential() > geologic.connectedPermeability() + 1.0e-15
                    || cell.sealPotential() > 1.0 - geologic.connectedPermeability() + 1.0e-15) {
                return false;
            }
            observedSource |= cell.sourcePotential() > 0.0;
            observedReservoir |= cell.reservoirPotential() > 0.0;
            observedSeal |= cell.sealPotential() > 0.0;
        }
        return observedSource && observedReservoir && observedSeal;
    }

    private static boolean verticalSystem(
            SkyIslandPetroleumSystemOpportunityProfile profile) {
        Map<Integer, SkyIslandPetroleumSystemOpportunityCell> byIndex = new HashMap<>();
        for (SkyIslandPetroleumSystemOpportunityCell cell : profile.cells()) {
            byIndex.put(cell.sourceCell().index(), cell);
        }

        int grid = profile.gridSize();
        int depths = profile.depthSamples();
        boolean observed = false;
        for (SkyIslandPetroleumSystemOpportunityCell cell : profile.cells()) {
            var source = cell.sourceCell();

            double deeper = 0.0;
            for (int d = source.depthIndex() + 1; d < depths; d++) {
                SkyIslandPetroleumSystemOpportunityCell candidate =
                        byIndex.get(index(source.xIndex(), d, source.zIndex(), grid, depths));
                if (candidate != null) {
                    deeper = Math.max(
                            deeper,
                            candidate.sourcePotential() / (d - source.depthIndex()));
                }
            }

            double shallower = 0.0;
            for (int d = source.depthIndex() - 1; d >= 0; d--) {
                SkyIslandPetroleumSystemOpportunityCell candidate =
                        byIndex.get(index(source.xIndex(), d, source.zIndex(), grid, depths));
                if (candidate != null) {
                    shallower = Math.max(
                            shallower,
                            candidate.sealPotential() / (source.depthIndex() - d));
                }
            }

            double expected = cell.reservoirPotential() * deeper * shallower;
            if (Double.doubleToLongBits(deeper)
                            != Double.doubleToLongBits(cell.deeperSourceSupport())
                    || Double.doubleToLongBits(shallower)
                            != Double.doubleToLongBits(cell.shallowerSealSupport())
                    || Double.doubleToLongBits(expected)
                            != Double.doubleToLongBits(cell.systemOpportunity())) {
                return false;
            }
            observed |= expected > 0.0;
        }
        return observed;
    }

    private static boolean scaleCovariant(
            SkyIslandPetroleumSystemOpportunityProfile first,
            SkyIslandPetroleumSystemOpportunityProfile second) {
        if (first.cells().size() != second.cells().size()) {
            return false;
        }
        for (int ordinal = 0; ordinal < first.cells().size(); ordinal++) {
            SkyIslandPetroleumSystemOpportunityCell a = first.cells().get(ordinal);
            SkyIslandPetroleumSystemOpportunityCell b = second.cells().get(ordinal);
            if (a.sourceCell().index() != b.sourceCell().index()
                    || Math.abs(a.sourcePotential() - b.sourcePotential()) > 1.0e-12
                    || Math.abs(a.reservoirPotential() - b.reservoirPotential()) > 1.0e-12
                    || Math.abs(a.sealPotential() - b.sealPotential()) > 1.0e-12
                    || Math.abs(a.deeperSourceSupport() - b.deeperSourceSupport()) > 1.0e-12
                    || Math.abs(a.shallowerSealSupport() - b.shallowerSealSupport()) > 1.0e-12
                    || Math.abs(a.systemOpportunity() - b.systemOpportunity()) > 1.0e-12) {
                return false;
            }
        }
        return true;
    }

    private static boolean noPolicySurface(
            SkyIslandPetroleumSystemOpportunityProfile profile) {
        if (profile.nonzeroSystemCellCount() <= 0L || profile.peakSystemOpportunity() <= 0.0) {
            return false;
        }
        List<String> forbidden =
                List.of("deposit", "reserve", "grade", "availability", "pumpjack", "minecraft", "strategicnode");
        for (Class<?> type : List.of(
                SkyIslandPetroleumSystemOpportunityProfile.class,
                SkyIslandPetroleumSystemOpportunityCell.class,
                SkyIslandPetroleumSystemOpportunityProfiler.class)) {
            for (Method method : type.getDeclaredMethods()) {
                String name = method.getName().toLowerCase(Locale.ROOT);
                if (forbidden.stream().anyMatch(name::contains)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int index(int x, int depth, int z, int grid, int depths) {
        return (z * depths + depth) * grid + x;
    }

    private static SkyIslandDescriptor descriptor(long islandKey, double radius) {
        SkyIslandDescriptor base = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(WORLD, 9L, 98L, islandKey));
        return new SkyIslandDescriptor(
                base.schemaVersion(),
                base.identity(),
                base.authorshipSeed(),
                base.morphologyFamily(),
                radius,
                base.reliefBudget(),
                0.72,
                0.68,
                0.70,
                0.70,
                base.exposureTendency(),
                0.60,
                0.76,
                base.ecologicalPotential());
    }

    private static SkyIslandDescriptor withRadius(
            SkyIslandDescriptor descriptor,
            double radius) {
        return new SkyIslandDescriptor(
                descriptor.schemaVersion(),
                descriptor.identity(),
                descriptor.authorshipSeed(),
                descriptor.morphologyFamily(),
                radius,
                descriptor.reliefBudget(),
                descriptor.rockCompetence(),
                descriptor.permeability(),
                descriptor.temperatureTendency(),
                descriptor.moistureTendency(),
                descriptor.exposureTendency(),
                descriptor.erosionMaturity(),
                descriptor.hydrologicalPotential(),
                descriptor.ecologicalPotential());
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
}
