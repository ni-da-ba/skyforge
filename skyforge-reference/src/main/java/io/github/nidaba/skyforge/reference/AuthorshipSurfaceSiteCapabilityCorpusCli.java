package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandMorphologyFamily;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.recipes.skyisland.SemanticSkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationAssociation;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandSurfaceSiteCapabilityCell;
import io.github.nidaba.skyforge.world.SkyIslandSurfaceSiteCapabilityProfile;
import io.github.nidaba.skyforge.world.SkyIslandSurfaceSiteCapabilityProfiler;
import io.github.nidaba.skyforge.world.SkyIslandVisibleHydrologicRealizationPlanner;
import io.github.nidaba.skyforge.world.SkyIslandWatershedPlanner;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import io.github.nidaba.skyforge.world.WorldBounds;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import javax.imageio.ImageIO;

/** Generates AUTH-0096 local surface-site capability evidence. */
public final class AuthorshipSurfaceSiteCapabilityCorpusCli {
    public static final String EVIDENCE_ID = "authorship-surface-site-capability-v1";

    private static final long AUTHORED_WORLD = 0x4155544830303936L;
    private static final long REALIZATION_ROOT = 0x5355524653495445L;
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int PANEL_W = WIDTH / 3;
    private static final int PANEL_H = HEIGHT / 2;

    private AuthorshipSurfaceSiteCapabilityCorpusCli() {}

    public static void main(String[] args) throws IOException {
        if (args.length > 1) {
            throw new IllegalArgumentException(
                    "usage: AuthorshipSurfaceSiteCapabilityCorpusCli [output-directory]");
        }
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", EVIDENCE_ID);
        Files.createDirectories(out);

        SkyIslandAuthoredRealizationAssociation association = association(96096L, 640.0, -480.0);
        SkyIslandSurfaceSiteCapabilityProfiler profiler =
                new SkyIslandSurfaceSiteCapabilityProfiler();
        SkyIslandSurfaceSiteCapabilityProfile profile = profiler.profile(association);
        SkyIslandSurfaceSiteCapabilityProfile repeat = profiler.profile(association);

        boolean exactAssociation = profile.association().equals(association);
        boolean watershedCoverage = exactWatershedCoverage(profile);
        boolean deterministic = profile.cells().equals(repeat.cells())
                && profile.watershed().equals(repeat.watershed())
                && profile.visibleHydrology().equals(repeat.visibleHydrology());
        boolean normalizedPhysical = normalizedPhysicalEvidence(profile);
        boolean hydrologyProvenance = profile.visibleHydrology().equals(
                        SkyIslandVisibleHydrologicRealizationPlanner.plan(
                                association.authoredDescriptor()))
                && profile.watershed().equals(
                        SkyIslandWatershedPlanner.plan(association.authoredDescriptor()));
        boolean noSitePolicy = noSitePolicySurface();

        BufferedImage atlas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = atlas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);
        panel(g, 0, 0, "AUTH0046_ASSOCIATION", exactAssociation,
                "exact authored/realized pair retained",
                "no island discovery or ranking",
                association.canonicalToken());
        panel(g, PANEL_W, 0, "WATERSHED_COVERAGE", watershedCoverage,
                "all accepted watershed cells",
                "canonical order + local positions",
                "adjacent spacing = R/24");
        panel(g, PANEL_W * 2, 0, "DETERMINISTIC", deterministic,
                "same exact association -> same cells",
                "same accepted source plans",
                "no new random source");
        panel(g, 0, PANEL_H, "LOCAL_PHYSICAL", normalizedPhysical,
                "3x3 / 5x5 / 9x9 support",
                "relief normalized by radius",
                "cardinal grade remains descriptive");
        panel(g, PANEL_W, PANEL_H, "HYDROLOGY_SOURCE", hydrologyProvenance,
                "AUTH watershed + visible hydrology",
                "waterbody / margin / riparian / channel",
                "no river re-authoring");
        panel(g, PANEL_W * 2, PANEL_H, "NO_SITE_POLICY", noSitePolicy,
                "no village/airfield/dungeon class",
                "no buildability threshold",
                "exact pairing remains downstream");
        g.dispose();
        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());

        String manifest = "scenario,pass\n"
                + "AUTH0046_ASSOCIATION," + exactAssociation + "\n"
                + "WATERSHED_COVERAGE," + watershedCoverage + "\n"
                + "DETERMINISTIC," + deterministic + "\n"
                + "LOCAL_PHYSICAL," + normalizedPhysical + "\n"
                + "HYDROLOGY_SOURCE," + hydrologyProvenance + "\n"
                + "NO_SITE_POLICY," + noSitePolicy + "\n";
        Files.writeString(out.resolve("manifest.csv"), manifest, StandardCharsets.UTF_8);

        StringBuilder cells = new StringBuilder(
                "watershedCellIndex,x,z,physicalSurface,upperOffsetR,interiority,"
                        + "support3,support5,support9,relief3R,relief5R,relief9R,meanCardinalGrade,"
                        + "flow,retainedWaterbody,shoreline,waterDepth,waterMargin,riparian,"
                        + "channelDischarge,hydrologicAdjustment\n");
        for (SkyIslandSurfaceSiteCapabilityCell cell : profile.cells()) {
            cells.append(cell.watershedCellIndex()).append(',')
                    .append(cell.position().x()).append(',')
                    .append(cell.position().z()).append(',')
                    .append(cell.physicalSurfacePresent()).append(',')
                    .append(optional(cell.upperSurfaceOffsetNormalized())).append(',')
                    .append(cell.authoredInteriority()).append(',')
                    .append(cell.support3x3Fraction()).append(',')
                    .append(cell.support5x5Fraction()).append(',')
                    .append(cell.support9x9Fraction()).append(',')
                    .append(optional(cell.relief3x3Normalized())).append(',')
                    .append(optional(cell.relief5x5Normalized())).append(',')
                    .append(optional(cell.relief9x9Normalized())).append(',')
                    .append(optional(cell.meanCardinalGrade())).append(',')
                    .append(cell.normalizedFlowAccumulation()).append(',')
                    .append(cell.retainedWaterbody()).append(',')
                    .append(cell.shoreline()).append(',')
                    .append(cell.waterDepthPotential()).append(',')
                    .append(cell.waterbodyMarginPotential()).append(',')
                    .append(cell.riparianPotential()).append(',')
                    .append(cell.channelRelativeDischarge()).append(',')
                    .append(cell.hydrologicAdjustmentMagnitude()).append('\n');
        }
        Files.writeString(out.resolve("cells.csv"), cells, StandardCharsets.UTF_8);

        Files.writeString(
                out.resolve("index.html"),
                """
                <!doctype html><html lang="en"><head><meta charset="utf-8">
                <title>AUTH-0096 surface-site capability evidence</title>
                <style>body{font-family:system-ui,sans-serif;max-width:1400px;margin:2rem auto;padding:0 1rem;background:#f4f3ed;color:#30343b}img{width:100%;border:1px solid #bbb;background:white}code{background:#eee;padding:.1rem .25rem}</style>
                </head><body>
                <h1>Local surface-site capability evidence</h1>
                <p>AUTH-0096 projects exact AUTH-0046 physical surface support together with accepted authored watershed / visible-hydrology semantics at the existing 49×49 planning anchors.</p>
                <p>These are continuous local measurements for downstream requirement matching. They do not declare a village, airfield, ruin, dungeon, settlement, or other concrete site valid.</p>
                <img src="atlas.png" alt="AUTH-0096 proof atlas">
                <p><a href="manifest.csv">manifest.csv</a> · <a href="cells.csv">cells.csv</a></p>
                </body></html>
                """,
                StandardCharsets.UTF_8);

        System.out.println(out.resolve("index.html").toAbsolutePath());
    }

    private static boolean exactWatershedCoverage(
            SkyIslandSurfaceSiteCapabilityProfile profile) {
        if (profile.cells().size() != profile.watershed().cells().size()) {
            return false;
        }
        for (int ordinal = 0; ordinal < profile.cells().size(); ordinal++) {
            var source = profile.watershed().cells().get(ordinal);
            var cell = profile.cells().get(ordinal);
            if (source.index() != cell.watershedCellIndex()
                    || !source.position().equals(cell.position())) {
                return false;
            }
        }
        double spacingR =
                profile.spacing() / profile.association().authoredDescriptor().nominalRadius();
        return Math.abs(spacingR - 1.0 / 24.0) <= 1.0e-15;
    }

    private static boolean normalizedPhysicalEvidence(
            SkyIslandSurfaceSiteCapabilityProfile profile) {
        if (profile.physicalSurfaceCellCount() <= 0) {
            return false;
        }
        for (SkyIslandSurfaceSiteCapabilityCell cell : profile.cells()) {
            if (!fraction(cell.authoredInteriority())
                    || !fraction(cell.support3x3Fraction())
                    || !fraction(cell.support5x5Fraction())
                    || !fraction(cell.support9x9Fraction())
                    || !fraction(cell.normalizedFlowAccumulation())
                    || !fraction(cell.waterDepthPotential())
                    || !fraction(cell.waterbodyMarginPotential())
                    || !fraction(cell.riparianPotential())
                    || !fraction(cell.channelRelativeDischarge())
                    || !fraction(cell.hydrologicAdjustmentMagnitude())) {
                return false;
            }
            if (cell.physicalSurfacePresent()) {
                if (cell.upperSurfaceOffsetNormalized().isEmpty()
                        || cell.relief3x3Normalized().isEmpty()
                        || cell.relief5x5Normalized().isEmpty()
                        || cell.relief9x9Normalized().isEmpty()) {
                    return false;
                }
                if (!nonNegative(cell.relief3x3Normalized().orElseThrow())
                        || !nonNegative(cell.relief5x5Normalized().orElseThrow())
                        || !nonNegative(cell.relief9x9Normalized().orElseThrow())) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean noSitePolicySurface() {
        List<String> forbidden =
                List.of("accepted", "buildable", "walkable", "village", "airfield", "dungeon");
        for (Method method : SkyIslandSurfaceSiteCapabilityProfile.class.getDeclaredMethods()) {
            String name = method.getName().toLowerCase(Locale.ROOT);
            if (forbidden.stream().anyMatch(name::contains)) {
                return false;
            }
        }
        Method[] profiles = Arrays.stream(
                        SkyIslandSurfaceSiteCapabilityProfiler.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> method.getName().equals("profile"))
                .toArray(Method[]::new);
        return profiles.length == 1
                && profiles[0].getParameterCount() == 1
                && profiles[0].getParameterTypes()[0]
                        == SkyIslandAuthoredRealizationAssociation.class;
    }

    private static boolean fraction(double value) {
        return Double.isFinite(value) && value >= 0.0 && value <= 1.0;
    }

    private static boolean nonNegative(double value) {
        return Double.isFinite(value) && value >= 0.0;
    }

    private static String optional(OptionalDouble value) {
        return value.isPresent() ? Double.toString(value.orElseThrow()) : "";
    }

    private static SkyIslandAuthoredRealizationAssociation association(
            long islandKey,
            double centerX,
            double centerZ) {
        SkyIslandDescriptor authored = authored(islandKey);
        return SkyIslandAuthoredRealizationAssociation.of(
                authored,
                realized(authored, 0, 0, 960960L, centerX, centerZ));
    }

    private static SkyIslandDescriptor authored(long islandKey) {
        SkyIslandDescriptor base = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(AUTHORED_WORLD, 9L, 96L, islandKey));
        return new SkyIslandDescriptor(
                base.schemaVersion(),
                base.identity(),
                base.authorshipSeed(),
                base.morphologyFamily(),
                base.nominalRadius(),
                base.reliefBudget(),
                0.67,
                0.74,
                base.temperatureTendency(),
                0.76,
                base.exposureTendency(),
                0.68,
                0.82,
                base.ecologicalPotential());
    }

    private static SkyIslandWorldVolume realized(
            SkyIslandDescriptor authored,
            int groupOrdinal,
            int memberOrdinal,
            long geometrySeed,
            double centerX,
            double centerZ) {
        double radius = authored.nominalRadius();
        SkyIslandMorphologyFamily morphology = authored.morphologyFamily();
        SkyIslandVolumeDescriptor physical = SkyIslandVolumeDescriptor.schema2(
                geometrySeed,
                centerX,
                centerZ,
                320.0,
                radius,
                0.46 * radius,
                0.62 * radius,
                Math.min(0.20 * radius, 36.0),
                0.43,
                0.62,
                0.57,
                0.18,
                morphology,
                0.22,
                0.20 * radius,
                0.31);
        CompiledSkyIslandVolume compiled =
                new SemanticSkyIslandVolumeRecipe().compile(physical);
        SkyIslandWorldVolumeId id =
                new SkyIslandWorldVolumeId(
                        REALIZATION_ROOT,
                        "auth96-" + groupOrdinal,
                        groupOrdinal,
                        memberOrdinal,
                        geometrySeed);
        WorldBounds bounds =
                new WorldBounds(
                        centerX - radius * 2.0,
                        centerX + radius * 2.0,
                        0.0,
                        640.0,
                        centerZ - radius * 2.0,
                        centerZ + radius * 2.0);
        return new SkyIslandWorldVolume(id, bounds, compiled);
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
        g.drawString(third.length() > 54 ? third.substring(0, 54) : third, x + 18, y + 136);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        g.drawString(pass ? "PASS" : "FAIL", x + 18, y + 205);
    }
}
