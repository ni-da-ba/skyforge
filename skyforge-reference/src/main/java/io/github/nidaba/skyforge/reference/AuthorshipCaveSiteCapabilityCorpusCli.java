package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandCaveExposureIntent;
import io.github.nidaba.skyforge.world.SkyIslandCaveSiteCapabilityProfile;
import io.github.nidaba.skyforge.world.SkyIslandCaveSiteCapabilityProfiler;
import io.github.nidaba.skyforge.world.SkyIslandCaveSiteCapabilitySystem;
import io.github.nidaba.skyforge.world.SkyIslandCaveSiteNodeEvidence;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandGeologyFieldSet;
import io.github.nidaba.skyforge.world.SkyIslandMaterialFamilyCell;
import io.github.nidaba.skyforge.world.SkyIslandMaterialFamilyPlan;
import io.github.nidaba.skyforge.world.SkyIslandSubsurfacePosition;
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

/** Generates AUTH-0099 cave-system site capability evidence. */
public final class AuthorshipCaveSiteCapabilityCorpusCli {
    public static final String EVIDENCE_ID = "authorship-cave-site-capability-v1";

    private static final long SEED = 0x534B59464F524745L;
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int PANEL_W = WIDTH / 3;
    private static final int PANEL_H = HEIGHT / 2;

    private AuthorshipCaveSiteCapabilityCorpusCli() {}

    public static void main(String[] args) throws IOException {
        if (args.length > 1) {
            throw new IllegalArgumentException(
                    "usage: AuthorshipCaveSiteCapabilityCorpusCli [output-directory]");
        }
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", EVIDENCE_ID);
        Files.createDirectories(out);

        SkyIslandCaveSiteCapabilityProfiler profiler =
                new SkyIslandCaveSiteCapabilityProfiler();
        SkyIslandDescriptor descriptor = descriptor(653L);
        SkyIslandCaveSiteCapabilityProfile profile = profiler.profile(descriptor);
        SkyIslandCaveSiteCapabilityProfile repeat = profiler.profile(descriptor);
        SkyIslandCaveSiteCapabilityProfile scaled =
                profiler.profile(withRadius(descriptor, descriptor.nominalRadius() * 2.0));

        boolean exactSources = exactSources(profile);
        boolean nearestHost = nearestHost(profile);
        boolean exactExposure = exactExposure(profile);
        boolean deterministic = profile.equals(repeat);
        boolean scaleCovariant = scaleCovariant(profile, scaled);
        boolean noRolePolicy = noRolePolicy(profile);

        BufferedImage atlas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = atlas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        panel(g, 0, 0, "EXACT_CAVE_SOURCES", exactSources,
                "AUTH-0024 topology retained",
                "AUTH-0025 geometry retained",
                "one output per source system");
        panel(g, PANEL_W, 0, "NEAREST_AUTH0033_HOST", nearestHost,
                "node geology sampled exactly",
                "nearest active host reconstructed",
                "normalized host distance retained");
        panel(g, PANEL_W * 2, 0, "EXACT_EXPOSURE", exactExposure,
                "AUTH-0028 intent preserved",
                "sealed systems remain empty",
                "no synthetic entrance");
        panel(g, 0, PANEL_H, "DETERMINISTIC", deterministic,
                "same descriptor -> same profile",
                "stable system/node order",
                "no new random source");
        panel(g, PANEL_W, PANEL_H, "SCALE_COVARIANT", scaleCovariant,
                "horizontal extent normalized by R",
                "semantic depth already normalized",
                "host relationship stable");
        panel(g, PANEL_W * 2, PANEL_H, "NO_ROLE_POLICY", noRolePolicy,
                "no useful/mine/dungeon class",
                "Content owns teaching/site selection",
                "Implementation owns exact cave/structure proof");

        g.dispose();
        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());

        String manifest = "scenario,pass\n"
                + "EXACT_CAVE_SOURCES," + exactSources + "\n"
                + "NEAREST_AUTH0033_HOST," + nearestHost + "\n"
                + "EXACT_EXPOSURE," + exactExposure + "\n"
                + "DETERMINISTIC," + deterministic + "\n"
                + "SCALE_COVARIANT," + scaleCovariant + "\n"
                + "NO_ROLE_POLICY," + noRolePolicy + "\n";
        Files.writeString(out.resolve("manifest.csv"), manifest, StandardCharsets.UTF_8);

        StringBuilder systems = new StringBuilder(
                "key,systemId,nodes,links,chambers,passages,exposed,minDepth,meanDepth,maxDepth,"
                        + "meanChamberR,maxChamberR,maxDepthRadius,meanGroundwater,meanFracture,"
                        + "peakFracture,meanVoid,peakVoid,meanPermeability,peakPermeability,"
                        + "meanMineralHost,peakMineralHost,meanHostDistance,maxHostDistance\n");
        for (long key : new long[] {653L, 1051L, 1439L, 3670L}) {
            SkyIslandCaveSiteCapabilityProfile candidate =
                    profiler.profile(descriptor(key));
            for (SkyIslandCaveSiteCapabilitySystem system : candidate.systems()) {
                systems.append(key).append(',')
                        .append(system.systemId()).append(',')
                        .append(system.nodeCount()).append(',')
                        .append(system.linkCount()).append(',')
                        .append(system.chamberCount()).append(',')
                        .append(system.passageCount()).append(',')
                        .append(system.hasAcceptedExteriorExposure()).append(',')
                        .append(system.minimumNodeDepth()).append(',')
                        .append(system.meanNodeDepth()).append(',')
                        .append(system.maximumNodeDepth()).append(',')
                        .append(system.meanNormalizedChamberHorizontalRadius()).append(',')
                        .append(system.maximumNormalizedChamberHorizontalRadius()).append(',')
                        .append(system.maximumChamberDepthRadius()).append(',')
                        .append(system.meanGroundwaterPotential()).append(',')
                        .append(system.meanFractureIntensity()).append(',')
                        .append(system.peakFractureIntensity()).append(',')
                        .append(system.meanVoidFormationPotential()).append(',')
                        .append(system.peakVoidFormationPotential()).append(',')
                        .append(system.meanConnectedPermeability()).append(',')
                        .append(system.peakConnectedPermeability()).append(',')
                        .append(system.meanMineralBearingHostSupport()).append(',')
                        .append(system.peakMineralBearingHostSupport()).append(',')
                        .append(system.meanNearestHostDistance()).append(',')
                        .append(system.maximumNearestHostDistance()).append('\n');
            }
        }
        Files.writeString(out.resolve("systems.csv"), systems, StandardCharsets.UTF_8);

        StringBuilder nodes = new StringBuilder(
                "systemId,nodeId,depth,fracture,voidPotential,groundwater,permeability,"
                        + "nearestHostCell,nearestHostDistance,mineralHost\n");
        for (SkyIslandCaveSiteCapabilitySystem system : profile.systems()) {
            for (SkyIslandCaveSiteNodeEvidence evidence : system.nodeEvidence()) {
                nodes.append(system.systemId()).append(',')
                        .append(evidence.sourceNode().nodeId()).append(',')
                        .append(evidence.sourceNode().position().depthFraction()).append(',')
                        .append(evidence.geology().fractureIntensity()).append(',')
                        .append(evidence.geology().voidFormationPotential()).append(',')
                        .append(evidence.geology().groundwaterPotential()).append(',')
                        .append(evidence.geology().connectedPermeability()).append(',')
                        .append(evidence.nearestHostCell().index()).append(',')
                        .append(evidence.normalizedHostDistance()).append(',')
                        .append(evidence.nearestHostCell().mineralBearingStructuralHost()).append('\n');
            }
        }
        Files.writeString(out.resolve("nodes.csv"), nodes, StandardCharsets.UTF_8);

        Files.writeString(
                out.resolve("index.html"),
                """
                <!doctype html><html lang="en"><head><meta charset="utf-8">
                <title>AUTH-0099 cave-system site capability</title>
                <style>body{font-family:system-ui,sans-serif;max-width:1400px;margin:2rem auto;padding:0 1rem;background:#f4f3ed;color:#30343b}img{width:100%;border:1px solid #bbb;background:white}</style>
                </head><body>
                <h1>Cave-system site capability evidence</h1>
                <p>AUTH-0099 composes exact accepted cave topology, geometry, sparse exterior exposure, geology, and nearest AUTH-0033 host-planning evidence.</p>
                <p>This is Stage-B authored evidence only. It does not declare a cave useful, mine-capable, dungeon-capable, progression-valid, or guaranteed to contain a Minecraft resource.</p>
                <img src="atlas.png" alt="AUTH-0099 proof atlas">
                <p><a href="manifest.csv">manifest.csv</a> · <a href="systems.csv">systems.csv</a> · <a href="nodes.csv">nodes.csv</a></p>
                </body></html>
                """,
                StandardCharsets.UTF_8);

        System.out.println(out.resolve("index.html").toAbsolutePath());
    }

    private static boolean exactSources(SkyIslandCaveSiteCapabilityProfile profile) {
        if (!profile.geometry().topology().equals(profile.topology())
                || !profile.exposure().geometry().equals(profile.geometry())
                || profile.systems().size() != profile.topology().systems().size()) {
            return false;
        }
        for (int ordinal = 0; ordinal < profile.systems().size(); ordinal++) {
            SkyIslandCaveSiteCapabilitySystem system = profile.systems().get(ordinal);
            if (!system.sourceSystem().equals(profile.topology().systems().get(ordinal))
                    || system.sourceGeometry().systemId() != system.systemId()
                    || system.nodeEvidence().size() != system.sourceSystem().nodes().size()) {
                return false;
            }
        }
        return !profile.systems().isEmpty();
    }

    private static boolean nearestHost(SkyIslandCaveSiteCapabilityProfile profile) {
        SkyIslandGeologyFieldSet geology =
                SkyIslandGeologyFieldSet.create(profile.descriptor());
        for (SkyIslandCaveSiteCapabilitySystem system : profile.systems()) {
            for (SkyIslandCaveSiteNodeEvidence evidence : system.nodeEvidence()) {
                if (!geology.sample(evidence.sourceNode().position()).equals(evidence.geology())) {
                    return false;
                }
                SkyIslandMaterialFamilyCell expected =
                        nearestHost(profile.descriptor(), profile.materialPlan(), evidence.sourceNode().position());
                if (!expected.equals(evidence.nearestHostCell())
                        || Double.doubleToLongBits(
                                        normalizedDistance(
                                                profile.descriptor(),
                                                evidence.sourceNode().position(),
                                                expected.position()))
                                != Double.doubleToLongBits(evidence.normalizedHostDistance())) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean exactExposure(SkyIslandCaveSiteCapabilityProfile profile) {
        Map<Integer, SkyIslandCaveExposureIntent> intents = new HashMap<>();
        for (SkyIslandCaveExposureIntent intent : profile.exposure().intents()) {
            intents.put(intent.systemId(), intent);
        }
        for (SkyIslandCaveSiteCapabilitySystem system : profile.systems()) {
            SkyIslandCaveExposureIntent expected = intents.get(system.systemId());
            if (expected == null) {
                if (system.exteriorExposure().isPresent()) {
                    return false;
                }
            } else if (!system.exteriorExposure().orElseThrow().equals(expected)) {
                return false;
            }
        }
        return profile.exposedSystemCount() == intents.size();
    }

    private static boolean scaleCovariant(
            SkyIslandCaveSiteCapabilityProfile first,
            SkyIslandCaveSiteCapabilityProfile second) {
        if (first.systems().size() != second.systems().size()) {
            return false;
        }
        for (int ordinal = 0; ordinal < first.systems().size(); ordinal++) {
            SkyIslandCaveSiteCapabilitySystem a = first.systems().get(ordinal);
            SkyIslandCaveSiteCapabilitySystem b = second.systems().get(ordinal);
            if (a.systemId() != b.systemId()
                    || a.nodeCount() != b.nodeCount()
                    || a.linkCount() != b.linkCount()
                    || Math.abs(a.meanNodeDepth() - b.meanNodeDepth()) > 1.0e-12
                    || Math.abs(a.meanNormalizedChamberHorizontalRadius()
                                    - b.meanNormalizedChamberHorizontalRadius())
                            > 1.0e-12
                    || Math.abs(a.meanFractureIntensity() - b.meanFractureIntensity()) > 1.0e-12
                    || Math.abs(a.meanMineralBearingHostSupport()
                                    - b.meanMineralBearingHostSupport())
                            > 1.0e-12
                    || a.nodeEvidence().size() != b.nodeEvidence().size()) {
                return false;
            }
            for (int node = 0; node < a.nodeEvidence().size(); node++) {
                SkyIslandCaveSiteNodeEvidence na = a.nodeEvidence().get(node);
                SkyIslandCaveSiteNodeEvidence nb = b.nodeEvidence().get(node);
                if (na.nearestHostCell().index() != nb.nearestHostCell().index()
                        || Math.abs(na.normalizedHostDistance() - nb.normalizedHostDistance())
                                > 1.0e-12) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean noRolePolicy(SkyIslandCaveSiteCapabilityProfile profile) {
        if (profile.systems().isEmpty()) {
            return false;
        }
        List<String> forbidden =
                List.of("useful", "minecapable", "dungeon", "progression", "structuretier", "loot");
        for (Class<?> type : List.of(
                SkyIslandCaveSiteCapabilityProfile.class,
                SkyIslandCaveSiteCapabilitySystem.class,
                SkyIslandCaveSiteNodeEvidence.class,
                SkyIslandCaveSiteCapabilityProfiler.class)) {
            for (Method method : type.getDeclaredMethods()) {
                String name = method.getName().toLowerCase(Locale.ROOT);
                if (forbidden.stream().anyMatch(name::contains)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static SkyIslandMaterialFamilyCell nearestHost(
            SkyIslandDescriptor descriptor,
            SkyIslandMaterialFamilyPlan plan,
            SkyIslandSubsurfacePosition position) {
        return plan.cells().stream()
                .min((first, second) -> {
                    double a = normalizedDistance(descriptor, position, first.position());
                    double b = normalizedDistance(descriptor, position, second.position());
                    int distance = Double.compare(a, b);
                    return distance != 0
                            ? distance
                            : Integer.compare(first.index(), second.index());
                })
                .orElseThrow();
    }

    private static double normalizedDistance(
            SkyIslandDescriptor descriptor,
            SkyIslandSubsurfacePosition first,
            SkyIslandSubsurfacePosition second) {
        double radius = descriptor.nominalRadius();
        double dx = (second.x() - first.x()) / radius;
        double dz = (second.z() - first.z()) / radius;
        double dd = second.depthFraction() - first.depthFraction();
        return Math.sqrt(dx * dx + dz * dz + dd * dd);
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 8L, 81L, key));
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
