package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandBaseMetalKind;
import io.github.nidaba.skyforge.world.SkyIslandBaseMetalOpportunityCell;
import io.github.nidaba.skyforge.world.SkyIslandBaseMetalOpportunityProfile;
import io.github.nidaba.skyforge.world.SkyIslandBaseMetalOpportunityProfiler;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Generates AUTH-0093 base-metal geological-opportunity evidence. */
public final class AuthorshipBaseMetalOpportunityCorpusCli {
    public static final String EVIDENCE_ID = "authorship-base-metal-opportunity-v1";

    private static final long WORLD = 0x4155544830303933L;
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int PANEL_W = WIDTH / 3;
    private static final int PANEL_H = HEIGHT / 2;

    private AuthorshipBaseMetalOpportunityCorpusCli() {}

    public static void main(String[] args) throws IOException {
        if (args.length > 1) {
            throw new IllegalArgumentException(
                    "usage: AuthorshipBaseMetalOpportunityCorpusCli [output-directory]");
        }
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", EVIDENCE_ID);
        Files.createDirectories(out);

        SkyIslandBaseMetalOpportunityProfiler profiler =
                new SkyIslandBaseMetalOpportunityProfiler();
        SkyIslandDescriptor referenceDescriptor = descriptor(93101L, 180.0);
        SkyIslandBaseMetalOpportunityProfile reference =
                profiler.profile(referenceDescriptor);
        SkyIslandBaseMetalOpportunityProfile repeat =
                profiler.profile(referenceDescriptor);
        SkyIslandBaseMetalOpportunityProfile scaled =
                profiler.profile(withRadius(referenceDescriptor, 360.0));

        boolean exactProvenance = reference.cells().stream()
                .map(SkyIslandBaseMetalOpportunityCell::sourceCell)
                .toList()
                .equals(reference.sourcePlan().cells());
        boolean subordinate = reference.cells().stream().allMatch(cell -> {
            double support = cell.sourceCell().mineralBearingStructuralHost();
            return cell.ironOpportunity() <= support
                    && cell.copperOpportunity() <= support
                    && cell.zincOpportunity() <= support
                    && (support != 0.0
                            || (cell.ironOpportunity() == 0.0
                                    && cell.copperOpportunity() == 0.0
                                    && cell.zincOpportunity() == 0.0));
        });
        boolean deterministic = reference.cells().equals(repeat.cells());
        boolean differentiated = reference.cells().stream().anyMatch(cell ->
                cell.sourceCell().mineralBearingStructuralHost() > 0.0
                        && (Double.doubleToLongBits(cell.ironOpportunity())
                                        != Double.doubleToLongBits(cell.copperOpportunity())
                                || Double.doubleToLongBits(cell.ironOpportunity())
                                        != Double.doubleToLongBits(cell.zincOpportunity())));
        boolean normalized = normalized(reference);
        boolean scaleCovariant = scaleCovariant(reference, scaled);

        BufferedImage atlas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = atlas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        panel(g, 0, 0, "AUTH0033_PROVENANCE", exactProvenance,
                "fixed AUTH-0033 host planning lattice",
                "exact cell identity/order retained",
                "no caller resolution or cell injection");
        panel(g, PANEL_W, 0, "MINERAL_SUPPORT", subordinate,
                "Fe/Cu/Zn require mineral-bearing structure",
                "opportunity never exceeds source support",
                "no free-floating ore noise");
        panel(g, PANEL_W * 2, 0, "DETERMINISTIC", deterministic,
                "same descriptor -> same opportunity",
                "stable authorship seed domains",
                "no backend state");
        panel(g, 0, PANEL_H, "ELEMENT_DIFFERENTIATION", differentiated,
                "Iron / Copper / Zinc are distinct",
                "broad coherent affinity only",
                "geology remains common cause");
        panel(g, PANEL_W, PANEL_H, "RELATIVE_SHARE", normalized,
                "mean opportunity shares normalize",
                "comparative planning evidence only",
                "not ore composition or reserves");
        panel(g, PANEL_W * 2, PANEL_H, "SCALE_NO_POLICY", scaleCovariant,
                "radius-only scale preserves normalized values",
                "no ore blocks / tiers / deposit counts",
                "Content owns gameplay availability");

        g.dispose();
        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());

        String manifest = "scenario,pass\n"
                + "AUTH0033_PROVENANCE," + exactProvenance + "\n"
                + "MINERAL_SUPPORT," + subordinate + "\n"
                + "DETERMINISTIC," + deterministic + "\n"
                + "ELEMENT_DIFFERENTIATION," + differentiated + "\n"
                + "RELATIVE_SHARE," + normalized + "\n"
                + "SCALE_NO_POLICY," + scaleCovariant + "\n";
        Files.writeString(out.resolve("manifest.csv"), manifest, StandardCharsets.UTF_8);

        StringBuilder profiles = new StringBuilder(
                "key,mineralCells,meanIron,meanCopper,meanZinc,peakIron,peakCopper,peakZinc,shareIron,shareCopper,shareZinc\n");
        for (long key : new long[] {93101L, 93102L, 93103L, 93104L, 93105L, 93106L}) {
            SkyIslandBaseMetalOpportunityProfile profile =
                    profiler.profile(descriptor(key, 180.0));
            profiles.append(key).append(',')
                    .append(profile.mineralBearingCellCount()).append(',')
                    .append(profile.meanOpportunity(SkyIslandBaseMetalKind.IRON)).append(',')
                    .append(profile.meanOpportunity(SkyIslandBaseMetalKind.COPPER)).append(',')
                    .append(profile.meanOpportunity(SkyIslandBaseMetalKind.ZINC)).append(',')
                    .append(profile.peakOpportunity(SkyIslandBaseMetalKind.IRON)).append(',')
                    .append(profile.peakOpportunity(SkyIslandBaseMetalKind.COPPER)).append(',')
                    .append(profile.peakOpportunity(SkyIslandBaseMetalKind.ZINC)).append(',')
                    .append(profile.relativeOpportunityShare(SkyIslandBaseMetalKind.IRON)).append(',')
                    .append(profile.relativeOpportunityShare(SkyIslandBaseMetalKind.COPPER)).append(',')
                    .append(profile.relativeOpportunityShare(SkyIslandBaseMetalKind.ZINC)).append('\n');
        }
        Files.writeString(out.resolve("profiles.csv"), profiles, StandardCharsets.UTF_8);

        StringBuilder cells = new StringBuilder(
                "index,xIndex,depthIndex,zIndex,mineralSupport,iron,copper,zinc\n");
        for (SkyIslandBaseMetalOpportunityCell cell : reference.cells()) {
            cells.append(cell.sourceCell().index()).append(',')
                    .append(cell.sourceCell().xIndex()).append(',')
                    .append(cell.sourceCell().depthIndex()).append(',')
                    .append(cell.sourceCell().zIndex()).append(',')
                    .append(cell.sourceCell().mineralBearingStructuralHost()).append(',')
                    .append(cell.ironOpportunity()).append(',')
                    .append(cell.copperOpportunity()).append(',')
                    .append(cell.zincOpportunity()).append('\n');
        }
        Files.writeString(out.resolve("cells.csv"), cells, StandardCharsets.UTF_8);

        Files.writeString(
                out.resolve("index.html"),
                """
                <!doctype html><html lang="en"><head><meta charset="utf-8">
                <title>AUTH-0093 base-metal geological opportunity</title>
                <style>body{font-family:system-ui,sans-serif;max-width:1400px;margin:2rem auto;padding:0 1rem;background:#f4f3ed;color:#30343b}img{width:100%;border:1px solid #bbb;background:white}</style>
                </head><body>
                <h1>Base-metal geological opportunity</h1>
                <p>AUTH-0093 adds normalized Iron, Copper, and Zinc opportunity over the exact AUTH-0033 material-family planning lattice. Every value is subordinate to accepted mineral-bearing structural support.</p>
                <p>This package proves semantic resource geology only. It does not define ore blocks, deposit counts, grade, reserves, availability classes, bootstrap guarantees, or Minecraft placement.</p>
                <img src="atlas.png" alt="AUTH-0093 proof atlas">
                <p><a href="manifest.csv">manifest.csv</a> · <a href="profiles.csv">profiles.csv</a> · <a href="cells.csv">cells.csv</a></p>
                </body></html>
                """,
                StandardCharsets.UTF_8);
    }

    private static boolean normalized(SkyIslandBaseMetalOpportunityProfile profile) {
        double total = 0.0;
        for (SkyIslandBaseMetalKind kind : SkyIslandBaseMetalKind.values()) {
            double share = profile.relativeOpportunityShare(kind);
            if (!Double.isFinite(share) || share <= 0.0 || share >= 1.0) {
                return false;
            }
            total += share;
        }
        return Math.abs(total - 1.0) <= 1.0e-12;
    }

    private static boolean scaleCovariant(
            SkyIslandBaseMetalOpportunityProfile first,
            SkyIslandBaseMetalOpportunityProfile second) {
        if (first.cells().size() != second.cells().size()) {
            return false;
        }
        for (int index = 0; index < first.cells().size(); index++) {
            SkyIslandBaseMetalOpportunityCell a = first.cells().get(index);
            SkyIslandBaseMetalOpportunityCell b = second.cells().get(index);
            if (a.sourceCell().index() != b.sourceCell().index()) {
                return false;
            }
            for (SkyIslandBaseMetalKind kind : SkyIslandBaseMetalKind.values()) {
                if (Math.abs(a.opportunity(kind) - b.opportunity(kind)) > 1.0e-12) {
                    return false;
                }
            }
        }
        return true;
    }

    private static SkyIslandDescriptor descriptor(long islandKey, double radius) {
        SkyIslandDescriptor base = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(WORLD, 9L, 93L, islandKey));
        return new SkyIslandDescriptor(
                base.schemaVersion(),
                base.identity(),
                base.authorshipSeed(),
                base.morphologyFamily(),
                radius,
                base.reliefBudget(),
                0.68,
                0.76,
                base.temperatureTendency(),
                0.72,
                base.exposureTendency(),
                0.66,
                0.74,
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
