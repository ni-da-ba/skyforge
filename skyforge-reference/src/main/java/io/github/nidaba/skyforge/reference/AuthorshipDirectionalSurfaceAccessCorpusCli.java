package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandMorphologyFamily;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.recipes.skyisland.SemanticSkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationAssociation;
import io.github.nidaba.skyforge.world.SkyIslandCompiledVolumeColumnField;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandLocalPosition;
import io.github.nidaba.skyforge.world.SkyIslandSurfaceAccessCapabilityCell;
import io.github.nidaba.skyforge.world.SkyIslandSurfaceAccessCapabilityProfile;
import io.github.nidaba.skyforge.world.SkyIslandSurfaceAccessCapabilityProfiler;
import io.github.nidaba.skyforge.world.SkyIslandSurfaceAccessDirection;
import io.github.nidaba.skyforge.world.SkyIslandSurfaceAccessRay;
import io.github.nidaba.skyforge.world.SkyIslandSurfaceSiteCapabilityProfile;
import io.github.nidaba.skyforge.world.SkyIslandSurfaceSiteCapabilityProfiler;
import io.github.nidaba.skyforge.world.SkyIslandVerticalColumn;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import javax.imageio.ImageIO;

/** Generates AUTH-0097 threshold-free directional surface-access evidence. */
public final class AuthorshipDirectionalSurfaceAccessCorpusCli {
    public static final String EVIDENCE_ID = "authorship-directional-surface-access-v1";

    private static final long AUTHORED_WORLD = 0x4155544830303937L;
    private static final long REALIZATION_ROOT = 0x4143434553533039L;
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int PANEL_W = WIDTH / 3;
    private static final int PANEL_H = HEIGHT / 2;

    private AuthorshipDirectionalSurfaceAccessCorpusCli() {}

    public static void main(String[] args) throws IOException {
        if (args.length > 1) {
            throw new IllegalArgumentException(
                    "usage: AuthorshipDirectionalSurfaceAccessCorpusCli [output-directory]");
        }
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", EVIDENCE_ID);
        Files.createDirectories(out);

        SkyIslandSurfaceSiteCapabilityProfile source =
                source(97097L, 580.0, -760.0);
        SkyIslandSurfaceAccessCapabilityProfiler profiler =
                new SkyIslandSurfaceAccessCapabilityProfiler();
        SkyIslandSurfaceAccessCapabilityProfile profile = profiler.profile(source);
        SkyIslandSurfaceAccessCapabilityProfile repeat = profiler.profile(source);

        boolean auth0096Source = profile.sourceProfile().equals(source)
                && profile.association().equals(source.association());
        boolean sameLattice = sameLattice(profile);
        boolean deterministic = profile.equals(repeat);
        boolean rayReconstruction = exactRaySupport(profile);
        boolean exactRealization = exactRealization(profile, source);
        boolean noRolePolicy = noRolePolicy();

        BufferedImage atlas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = atlas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);
        panel(g, 0, 0, "AUTH0096_SOURCE", auth0096Source,
                "exact source profile retained",
                "exact AUTH-0046 realization retained",
                "no source island discovery");
        panel(g, PANEL_W, 0, "SAME_LATTICE", sameLattice,
                "49x49 accepted watershed square",
                "adjacent cardinal spacing = R/24",
                "no second access grid");
        panel(g, PANEL_W * 2, 0, "DETERMINISTIC", deterministic,
                "same exact source -> same rays",
                "canonical direction order",
                "no new random/key source");
        panel(g, 0, PANEL_H, "RAY_RECONSTRUCTION", rayReconstruction,
                "support/open-tail from exact columns",
                "transitions preserve re-entry",
                "raw counts + normalized distances");
        panel(g, PANEL_W, PANEL_H, "EXACT_REALIZATION", exactRealization,
                "physical evidence stays with source",
                "world-XZ detail remains authoritative",
                "no translation surrogate");
        panel(g, PANEL_W * 2, PANEL_H, "NO_ROLE_POLICY", noRolePolicy,
                "no runway/dock/site threshold",
                "Content owns requirement matching",
                "Implementation owns exact 3D proof");
        g.dispose();
        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());

        String manifest = "scenario,pass\n"
                + "AUTH0096_SOURCE," + auth0096Source + "\n"
                + "SAME_LATTICE," + sameLattice + "\n"
                + "DETERMINISTIC," + deterministic + "\n"
                + "RAY_RECONSTRUCTION," + rayReconstruction + "\n"
                + "EXACT_REALIZATION," + exactRealization + "\n"
                + "NO_ROLE_POLICY," + noRolePolicy + "\n";
        Files.writeString(out.resolve("manifest.csv"), manifest, StandardCharsets.UTF_8);

        StringBuilder rays = new StringBuilder(
                "association,cell,x,z,direction,availableSteps,stepR,consecutiveSupport,"
                        + "firstOpenR,furthestSupportR,openTailSteps,openTailR,transitions,"
                        + "maxRiseR,maxFallR,meanAdjacentGrade\n");
        for (SkyIslandSurfaceAccessCapabilityCell cell : profile.cells()) {
            for (SkyIslandSurfaceAccessRay ray : cell.rays()) {
                rays.append(profile.association().canonicalToken()).append(',')
                        .append(cell.watershedCellIndex()).append(',')
                        .append(cell.position().x()).append(',')
                        .append(cell.position().z()).append(',')
                        .append(ray.direction()).append(',')
                        .append(ray.availableStepCount()).append(',')
                        .append(ray.stepDistanceNormalized()).append(',')
                        .append(ray.consecutiveSupportedStepCount()).append(',')
                        .append(optional(ray.firstOpenDistanceNormalized())).append(',')
                        .append(ray.furthestSupportedDistanceNormalized()).append(',')
                        .append(ray.boundaryOpenTailStepCount()).append(',')
                        .append(ray.boundaryOpenTailDistanceNormalized()).append(',')
                        .append(ray.supportTransitionCount()).append(',')
                        .append(ray.maximumRiseFromAnchorNormalized()).append(',')
                        .append(ray.maximumFallFromAnchorNormalized()).append(',')
                        .append(optional(ray.meanAdjacentSupportedGrade())).append('\n');
            }
        }
        Files.writeString(out.resolve("rays.csv"), rays, StandardCharsets.UTF_8);

        Files.writeString(
                out.resolve("index.html"),
                """
                <!doctype html><html lang="en"><head><meta charset="utf-8">
                <title>AUTH-0097 directional surface-access evidence</title>
                <style>body{font-family:system-ui,sans-serif;max-width:1400px;margin:2rem auto;padding:0 1rem;background:#f4f3ed;color:#30343b}img{width:100%;border:1px solid #bbb;background:white}</style>
                </head><body>
                <h1>Directional surface-access evidence</h1>
                <p>AUTH-0097 consumes one exact AUTH-0096 site profile and measures eight local-lattice rays toward the accepted watershed-square boundary.</p>
                <p>Support runs, open tails, transitions, rise/fall, and grade are descriptive evidence only. They do not declare a runway, dock, cliff site, or concrete approach corridor valid.</p>
                <img src="atlas.png" alt="AUTH-0097 proof atlas">
                <p><a href="manifest.csv">manifest.csv</a> · <a href="rays.csv">rays.csv</a></p>
                </body></html>
                """,
                StandardCharsets.UTF_8);

        System.out.println(out.resolve("index.html").toAbsolutePath());
    }

    private static boolean exactRealization(
            SkyIslandSurfaceAccessCapabilityProfile profile,
            SkyIslandSurfaceSiteCapabilityProfile source) {
        if (!profile.association().equals(source.association())
                || profile.cells().size() != source.cells().size()) {
            return false;
        }
        for (int ordinal = 0; ordinal < profile.cells().size(); ordinal++) {
            if (!profile.cells().get(ordinal).sourceCell().equals(source.cells().get(ordinal))) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameLattice(SkyIslandSurfaceAccessCapabilityProfile profile) {
        double radius = profile.association().authoredDescriptor().nominalRadius();
        if (profile.gridSize() != 49
                || Math.abs(profile.spacing() / radius - 1.0 / 24.0) > 1.0e-15
                || profile.cells().size() != profile.sourceProfile().cells().size()) {
            return false;
        }
        for (int ordinal = 0; ordinal < profile.cells().size(); ordinal++) {
            if (!profile.cells().get(ordinal).sourceCell()
                    .equals(profile.sourceProfile().cells().get(ordinal))) {
                return false;
            }
            if (profile.cells().get(ordinal).sourceCell().physicalSurfacePresent()) {
                List<SkyIslandSurfaceAccessDirection> actual =
                        profile.cells().get(ordinal).rays().stream()
                                .map(SkyIslandSurfaceAccessRay::direction)
                                .toList();
                if (!actual.equals(Arrays.asList(SkyIslandSurfaceAccessDirection.values()))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean exactRaySupport(SkyIslandSurfaceAccessCapabilityProfile profile) {
        SkyIslandCompiledVolumeColumnField physical =
                new SkyIslandCompiledVolumeColumnField(
                        profile.association().realizedVolume().compiledVolume());
        double radius = profile.association().authoredDescriptor().nominalRadius();
        int gridSize = profile.gridSize();
        double spacing = profile.spacing();

        for (SkyIslandSurfaceAccessCapabilityCell cell : profile.cells()) {
            if (cell.rays().isEmpty()) {
                continue;
            }
            int index = cell.watershedCellIndex();
            int ax = index % gridSize;
            int az = index / gridSize;
            SkyIslandVerticalColumn anchor =
                    physical.columnAt(position(ax, az, gridSize, radius, spacing)).orElse(null);
            if (anchor == null) {
                return false;
            }
            for (SkyIslandSurfaceAccessRay ray : cell.rays()) {
                int available = availableSteps(gridSize, ax, az, ray.direction());
                if (available != ray.availableStepCount()) {
                    return false;
                }
                boolean previous = true;
                int consecutive = 0;
                int transitions = 0;
                int firstOpenStep = -1;
                boolean[] supported = new boolean[available + 1];
                supported[0] = true;
                for (int step = 1; step <= available; step++) {
                    int x = ax + ray.direction().xStep() * step;
                    int z = az + ray.direction().zStep() * step;
                    boolean current = physical.columnAt(
                                    position(x, z, gridSize, radius, spacing))
                            .isPresent();
                    supported[step] = current;
                    if (current != previous) {
                        transitions++;
                    }
                    if (firstOpenStep < 0 && !current) {
                        firstOpenStep = step;
                    }
                    if (firstOpenStep < 0 && current) {
                        consecutive++;
                    }
                    previous = current;
                }
                int openTail = 0;
                for (int step = available; step >= 1 && !supported[step]; step--) {
                    openTail++;
                }
                if (consecutive != ray.consecutiveSupportedStepCount()
                        || transitions != ray.supportTransitionCount()
                        || openTail != ray.boundaryOpenTailStepCount()) {
                    return false;
                }
                if ((firstOpenStep < 0) != ray.firstOpenDistanceNormalized().isEmpty()) {
                    return false;
                }
                if (firstOpenStep >= 0) {
                    double expected = firstOpenStep * ray.stepDistanceNormalized();
                    if (Math.abs(expected - ray.firstOpenDistanceNormalized().orElseThrow())
                            > 1.0e-12) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean noRolePolicy() {
        List<String> forbidden =
                List.of("airfield", "runway", "dock", "buildable", "walkable", "validsite");
        for (Class<?> type : List.of(
                SkyIslandSurfaceAccessCapabilityProfile.class,
                SkyIslandSurfaceAccessCapabilityCell.class,
                SkyIslandSurfaceAccessRay.class)) {
            for (Method method : type.getDeclaredMethods()) {
                String name = method.getName().toLowerCase(Locale.ROOT);
                if (forbidden.stream().anyMatch(name::contains)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int availableSteps(
            int gridSize,
            int x,
            int z,
            SkyIslandSurfaceAccessDirection direction) {
        int xSteps = direction.xStep() > 0
                ? gridSize - 1 - x
                : direction.xStep() < 0 ? x : Integer.MAX_VALUE;
        int zSteps = direction.zStep() > 0
                ? gridSize - 1 - z
                : direction.zStep() < 0 ? z : Integer.MAX_VALUE;
        return Math.min(xSteps, zSteps);
    }

    private static SkyIslandLocalPosition position(
            int x,
            int z,
            int gridSize,
            double radius,
            double spacing) {
        return new SkyIslandLocalPosition(
                x == gridSize - 1 ? radius : -radius + x * spacing,
                z == gridSize - 1 ? radius : -radius + z * spacing);
    }

    private static String optional(OptionalDouble value) {
        return value.isPresent() ? Double.toString(value.orElseThrow()) : "";
    }

    private static SkyIslandSurfaceSiteCapabilityProfile source(
            long islandKey,
            double centerX,
            double centerZ) {
        SkyIslandDescriptor authored = authored(islandKey);
        SkyIslandAuthoredRealizationAssociation association =
                SkyIslandAuthoredRealizationAssociation.of(
                        authored,
                        realized(authored, 970970L, centerX, centerZ));
        return new SkyIslandSurfaceSiteCapabilityProfiler().profile(association);
    }

    private static SkyIslandDescriptor authored(long islandKey) {
        SkyIslandDescriptor base = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(AUTHORED_WORLD, 9L, 97L, islandKey));
        return new SkyIslandDescriptor(
                base.schemaVersion(),
                base.identity(),
                base.authorshipSeed(),
                base.morphologyFamily(),
                base.nominalRadius(),
                base.reliefBudget(),
                0.70,
                0.71,
                base.temperatureTendency(),
                0.73,
                base.exposureTendency(),
                0.64,
                0.79,
                base.ecologicalPotential());
    }

    private static SkyIslandWorldVolume realized(
            SkyIslandDescriptor authored,
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
                0.44 * radius,
                0.60 * radius,
                Math.min(0.18 * radius, 34.0),
                0.37,
                0.61,
                0.55,
                0.20,
                morphology,
                0.24,
                0.19 * radius,
                0.29);
        CompiledSkyIslandVolume compiled =
                new SemanticSkyIslandVolumeRecipe().compile(physical);
        SkyIslandWorldVolumeId id =
                new SkyIslandWorldVolumeId(
                        REALIZATION_ROOT,
                        "auth97",
                        0,
                        0,
                        geometrySeed);
        WorldBounds bounds = new WorldBounds(
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
        g.drawString(third, x + 18, y + 136);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        g.drawString(pass ? "PASS" : "FAIL", x + 18, y + 205);
    }
}
