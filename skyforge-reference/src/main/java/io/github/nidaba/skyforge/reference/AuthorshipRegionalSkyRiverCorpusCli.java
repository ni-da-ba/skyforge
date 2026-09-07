package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyFamily;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviders;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoLayout;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoPlan;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoPlanner;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoRequest;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandGroupRole;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandGroupTemplate;
import io.github.nidaba.skyforge.recipes.skyisland.group.ProviderMorphologySpec;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupLayout;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandMorphologySpec;
import io.github.nidaba.skyforge.world.SkyIslandAcceptedConvergenceCompilation;
import io.github.nidaba.skyforge.world.SkyIslandAcceptedConvergenceCompiler;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationAssociation;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationCatalog;
import io.github.nidaba.skyforge.world.SkyIslandCompiledWorldPublication;
import io.github.nidaba.skyforge.world.SkyIslandCompiledWorldPublisher;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandPublishedAuthoredRealizationBinding;
import io.github.nidaba.skyforge.world.SkyIslandRegionalSkyRiverPlan;
import io.github.nidaba.skyforge.world.SkyIslandRegionalSkyRiverPlanner;
import io.github.nidaba.skyforge.world.SkyIslandRegionalSkyRiverWaypoint;
import io.github.nidaba.skyforge.world.SkyIslandSupportConvergenceExecutor;
import io.github.nidaba.skyforge.world.SkyIslandSupportConvergenceOutcome;
import io.github.nidaba.skyforge.world.SkyIslandSupportConvergenceReport;
import io.github.nidaba.skyforge.world.SkyIslandSupportReplanMargin;
import io.github.nidaba.skyforge.world.SkyIslandSupportReplanProposal;
import io.github.nidaba.skyforge.world.SkyIslandSupportReplanProposalBuilder;
import io.github.nidaba.skyforge.world.SkyIslandSupportReservationRequirementSynthesis;
import io.github.nidaba.skyforge.world.SkyIslandSupportReservationRequirementSynthesizer;
import io.github.nidaba.skyforge.world.SkyIslandWorldVerticalReservation;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
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
import java.util.Collections;
import java.util.List;
import javax.imageio.ImageIO;

/** Generates AUTH-0094 regional floating sky-river architecture/provenance evidence. */
public final class AuthorshipRegionalSkyRiverCorpusCli {
    public static final String EVIDENCE_ID = "authorship-regional-sky-river-v1";

    private static final long AUTHORED_WORLD = 0x4155544830303934L;
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int PANEL_W = WIDTH / 3;
    private static final int PANEL_H = HEIGHT / 2;

    private AuthorshipRegionalSkyRiverCorpusCli() {}

    public static void main(String[] args) throws IOException {
        if (args.length > 1) {
            throw new IllegalArgumentException(
                    "usage: AuthorshipRegionalSkyRiverCorpusCli [output-directory]");
        }
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", EVIDENCE_ID);
        Files.createDirectories(out);

        SkyIslandRegionalSkyRiverPlanner planner = new SkyIslandRegionalSkyRiverPlanner();
        Fixture baseFixture = fixture(94101L, 3, 0.0, 0.0, 0.0, 1.0);
        Fixture movedFixture = fixture(94101L, 3, 9_000.0, 220.0, -6_000.0, 1.0);
        Fixture scaledFixture = fixture(94101L, 3, 0.0, 0.0, 0.0, 2.0);
        Fixture singletonFixture = fixture(94102L, 1, 0.0, 0.0, 0.0, 1.0);

        SkyIslandRegionalSkyRiverPlan base =
                planner.plan(baseFixture.binding(), 0x524956455231L).orElseThrow();
        SkyIslandRegionalSkyRiverPlan repeat =
                planner.plan(baseFixture.binding(), 0x524956455231L).orElseThrow();
        SkyIslandRegionalSkyRiverPlan moved =
                planner.plan(movedFixture.binding(), 0x524956455231L).orElseThrow();
        SkyIslandRegionalSkyRiverPlan scaled =
                planner.plan(scaledFixture.binding(), 0x524956455231L).orElseThrow();

        boolean exactBinding = base.binding().equals(baseFixture.binding())
                && base.source().equals(baseFixture.binding()
                        .associationCatalog()
                        .associationFor(base.source().realizedVolumeId())
                        .orElseThrow())
                && base.sink().equals(baseFixture.binding()
                        .associationCatalog()
                        .associationFor(base.sink().realizedVolumeId())
                        .orElseThrow());
        boolean deterministic = base.source().equals(repeat.source())
                && base.sink().equals(repeat.sink())
                && base.trajectory().equals(repeat.trajectory());
        boolean singletonEmpty =
                planner.plan(singletonFixture.binding(), 0x524956455231L).isEmpty();
        boolean orderedCurved = orderedCurved(base);
        boolean translation = translated(base, moved, 9_000.0, 220.0, -6_000.0);
        boolean scale = scaled(base, scaled, 2.0);

        BufferedImage atlas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = atlas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        panel(g, 0, 0, "EXACT_BINDING", exactBinding,
                "one exact AUTH-0087 published region",
                "source/sink retain exact AUTH-0046 associations",
                "no spatial identity inference");
        panel(g, PANEL_W, 0, "DETERMINISTIC", deterministic,
                "same binding + phenomenon key",
                "same participants + same trajectory",
                "no backend/random runtime state");
        panel(g, PANEL_W * 2, 0, "SINGLETON_EMPTY", singletonEmpty,
                "cross-island phenomenon requires >= 2 islands",
                "no fabricated peer",
                "no fallback local watershed relabeling");
        panel(g, 0, PANEL_H, "ORDERED_CURVED_PATH", orderedCurved,
                "source -> 1/3 -> 2/3 -> sink",
                "world-space lateral open-sky curve",
                "guide trajectory, not fluid cells");
        panel(g, PANEL_W, PANEL_H, "TRANSLATION", translation,
                "uniform XYZ translation",
                "participant identity preserved",
                "all guide points shift exactly");
        panel(g, PANEL_W * 2, PANEL_H, "SCALE_NO_POLICY", scale,
                "uniform world scale",
                "trajectory coordinates scale",
                "no rarity / rewards / rendering / fluids");

        g.dispose();
        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());

        Files.writeString(
                out.resolve("manifest.csv"),
                "scenario,pass\n"
                        + "EXACT_BINDING," + exactBinding + "\n"
                        + "DETERMINISTIC," + deterministic + "\n"
                        + "SINGLETON_EMPTY," + singletonEmpty + "\n"
                        + "ORDERED_CURVED_PATH," + orderedCurved + "\n"
                        + "TRANSLATION," + translation + "\n"
                        + "SCALE_NO_POLICY," + scale + "\n",
                StandardCharsets.UTF_8);

        StringBuilder trajectory = new StringBuilder(
                "parameter,worldX,worldY,worldZ\n");
        for (SkyIslandRegionalSkyRiverWaypoint waypoint : base.trajectory()) {
            trajectory.append(waypoint.parameter()).append(',')
                    .append(waypoint.worldX()).append(',')
                    .append(waypoint.worldY()).append(',')
                    .append(waypoint.worldZ()).append('\n');
        }
        Files.writeString(out.resolve("trajectory.csv"), trajectory, StandardCharsets.UTF_8);

        Files.writeString(
                out.resolve("participants.csv"),
                "role,association,volumeId,upperReferenceY\n"
                        + "SOURCE," + base.source().canonicalToken() + ","
                        + base.source().realizedVolumeId().path() + ","
                        + upperReferenceY(base.source()) + "\n"
                        + "SINK," + base.sink().canonicalToken() + ","
                        + base.sink().realizedVolumeId().path() + ","
                        + upperReferenceY(base.sink()) + "\n",
                StandardCharsets.UTF_8);

        Files.writeString(
                out.resolve("index.html"),
                """
                <!doctype html><html lang="en"><head><meta charset="utf-8">
                <title>AUTH-0094 regional floating sky-river evidence</title>
                <style>body{font-family:system-ui,sans-serif;max-width:1400px;margin:2rem auto;padding:0 1rem;background:#f4f3ed;color:#30343b}img{width:100%;border:1px solid #bbb;background:white}</style>
                </head><body>
                <h1>Regional floating sky-river semantic plan</h1>
                <p>AUTH-0094 binds one cross-island floating river to an exact AUTH-0087 published region, exact AUTH-0046 source/sink provenance, and one ordered backend-neutral 3D guide trajectory.</p>
                <p>The guide path is not a Minecraft fluid/block path or exact terrain attachment. Content owns rarity/reward/hazard decisions; Implementation owns fluids, rendering, persistence, and runtime behavior.</p>
                <img src="atlas.png" alt="AUTH-0094 proof atlas">
                <p><a href="manifest.csv">manifest.csv</a> · <a href="participants.csv">participants.csv</a> · <a href="trajectory.csv">trajectory.csv</a></p>
                </body></html>
                """,
                StandardCharsets.UTF_8);
    }

    private static boolean orderedCurved(SkyIslandRegionalSkyRiverPlan plan) {
        List<SkyIslandRegionalSkyRiverWaypoint> points = plan.trajectory();
        if (points.size() != 4
                || points.get(0).parameter() != 0.0
                || points.get(3).parameter() != 1.0
                || !(points.get(0).parameter() < points.get(1).parameter()
                        && points.get(1).parameter() < points.get(2).parameter()
                        && points.get(2).parameter() < points.get(3).parameter())) {
            return false;
        }
        SkyIslandRegionalSkyRiverWaypoint source = points.getFirst();
        SkyIslandRegionalSkyRiverWaypoint sink = points.getLast();
        SkyIslandRegionalSkyRiverWaypoint control = points.get(1);
        double linearX = lerp(source.worldX(), sink.worldX(), control.parameter());
        double linearZ = lerp(source.worldZ(), sink.worldZ(), control.parameter());
        return Math.abs(control.worldX() - linearX) > 1.0e-9
                || Math.abs(control.worldZ() - linearZ) > 1.0e-9;
    }

    private static boolean translated(
            SkyIslandRegionalSkyRiverPlan first,
            SkyIslandRegionalSkyRiverPlan second,
            double dx,
            double dy,
            double dz) {
        if (!first.source().canonicalToken().equals(second.source().canonicalToken())
                || !first.sink().canonicalToken().equals(second.sink().canonicalToken())) {
            return false;
        }
        for (int index = 0; index < first.trajectory().size(); index++) {
            SkyIslandRegionalSkyRiverWaypoint a = first.trajectory().get(index);
            SkyIslandRegionalSkyRiverWaypoint b = second.trajectory().get(index);
            if (Math.abs(a.worldX() + dx - b.worldX()) > 1.0e-9
                    || Math.abs(a.worldY() + dy - b.worldY()) > 1.0e-9
                    || Math.abs(a.worldZ() + dz - b.worldZ()) > 1.0e-9
                    || a.parameter() != b.parameter()) {
                return false;
            }
        }
        return true;
    }

    private static boolean scaled(
            SkyIslandRegionalSkyRiverPlan first,
            SkyIslandRegionalSkyRiverPlan second,
            double scale) {
        if (!first.source().canonicalToken().equals(second.source().canonicalToken())
                || !first.sink().canonicalToken().equals(second.sink().canonicalToken())) {
            return false;
        }
        for (int index = 0; index < first.trajectory().size(); index++) {
            SkyIslandRegionalSkyRiverWaypoint a = first.trajectory().get(index);
            SkyIslandRegionalSkyRiverWaypoint b = second.trajectory().get(index);
            if (Math.abs(a.worldX() * scale - b.worldX()) > 1.0e-9
                    || Math.abs(a.worldY() * scale - b.worldY()) > 1.0e-9
                    || Math.abs(a.worldZ() * scale - b.worldZ()) > 1.0e-9
                    || a.parameter() != b.parameter()) {
                return false;
            }
        }
        return true;
    }

    private static double upperReferenceY(
            SkyIslandAuthoredRealizationAssociation association) {
        var descriptor = association.realizedVolume().compiledVolume().descriptor();
        return descriptor.suspensionElevation() + descriptor.upperElevation();
    }

    private static double lerp(double first, double second, double parameter) {
        return first + (second - first) * parameter;
    }

    private static Fixture fixture(
            long rootSeed,
            int memberCount,
            double centerX,
            double centerY,
            double centerZ,
            double scale) {
        SkyIslandCompiledWorldPublication publication =
                new SkyIslandCompiledWorldPublisher()
                        .publish(
                                acceptedCompilation(
                                        rootSeed,
                                        memberCount,
                                        centerX,
                                        centerY,
                                        centerZ,
                                        scale),
                                1L);
        ArrayList<SkyIslandAuthoredRealizationAssociation> associations =
                new ArrayList<>();
        int ordinal = 0;
        for (SkyIslandWorldVolume volume : publication.catalog().volumes()) {
            associations.add(SkyIslandAuthoredRealizationAssociation.of(
                    authored(60_000L + ordinal, volume),
                    volume));
            ordinal++;
        }
        Collections.reverse(associations);
        return new Fixture(new SkyIslandPublishedAuthoredRealizationBinding(
                publication,
                new SkyIslandAuthoredRealizationCatalog(
                        AUTHORED_WORLD,
                        publication.catalog().rootSeed(),
                        associations)));
    }

    private static SkyIslandDescriptor authored(
            long islandKey,
            SkyIslandWorldVolume volume) {
        SkyIslandDescriptor base = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(AUTHORED_WORLD, 9L, 94L, islandKey));
        var realized = volume.compiledVolume().descriptor();
        var morphology = realized.hasSemanticMorphologyFamily()
                ? realized.morphologyFamily()
                : base.morphologyFamily();
        return new SkyIslandDescriptor(
                base.schemaVersion(),
                base.identity(),
                base.authorshipSeed(),
                morphology,
                realized.nominalRadius(),
                base.reliefBudget(),
                base.rockCompetence(),
                base.permeability(),
                base.temperatureTendency(),
                base.moistureTendency(),
                base.exposureTendency(),
                base.erosionMaturity(),
                base.hydrologicalPotential(),
                base.ecologicalPotential());
    }

    private static SkyIslandAcceptedConvergenceCompilation acceptedCompilation(
            long rootSeed,
            int memberCount,
            double centerX,
            double centerY,
            double centerZ,
            double scale) {
        var registry = SkyIslandMorphologyProviders.builtInRegistry();
        var morphology = new ProviderMorphologySpec(
                SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF),
                0.0,
                0.0);
        SkyIslandArchipelagoRequest request =
                request(rootSeed, memberCount, centerX, centerY, centerZ, scale, morphology);
        SkyIslandArchipelagoPlan original =
                new SkyIslandArchipelagoPlanner().plan(request);
        SkyIslandSupportReservationRequirementSynthesis synthesis =
                new SkyIslandSupportReservationRequirementSynthesizer().synthesize(original, registry);
        SkyIslandSupportReplanProposal proposal =
                new SkyIslandSupportReplanProposalBuilder()
                        .propose(
                                request,
                                original,
                                synthesis,
                                new SkyIslandWorldVerticalReservation(
                                        520.0 * scale,
                                        320.0 * scale),
                                SkyIslandSupportReplanMargin.ZERO);
        SkyIslandSupportConvergenceReport convergence =
                new SkyIslandSupportConvergenceExecutor().executeOnce(proposal, registry);
        if (convergence.outcome()
                != SkyIslandSupportConvergenceOutcome.ACCEPTED_ONE_PASS) {
            throw new IllegalStateException("AUTH-0094 evidence fixture did not converge");
        }
        return new SkyIslandAcceptedConvergenceCompiler().compileOnce(convergence, registry);
    }

    private static SkyIslandArchipelagoRequest request(
            long rootSeed,
            int memberCount,
            double centerX,
            double centerY,
            double centerZ,
            double scale,
            ProviderMorphologySpec morphology) {
        List<SkyIslandMorphologySpec> morphologies =
                java.util.stream.IntStream.range(0, memberCount)
                        .mapToObj(index -> (SkyIslandMorphologySpec) morphology)
                        .toList();
        SkyIslandGroupTemplate template =
                new SkyIslandGroupTemplate(
                        "auth94",
                        SkyIslandGroupRole.ANCHOR,
                        descriptor(scale),
                        360.0 * scale,
                        48.0 * scale,
                        0.0,
                        morphologies,
                        new SkyIslandGroupLayout.Chain(
                                0.15,
                                800.0 * scale,
                                0.0,
                                0.0,
                                0.0,
                                0.0),
                        1_400.0 * scale);
        return new SkyIslandArchipelagoRequest(
                rootSeed,
                centerX,
                centerZ,
                centerY + 320.0 * scale,
                500.0 * scale,
                List.of(template),
                new SkyIslandArchipelagoLayout.Hub(
                        1_600.0 * scale,
                        0.0,
                        0.0,
                        0.0,
                        0.0));
    }

    private static SkyIslandVolumeDescriptor descriptor(double scale) {
        return new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                0L,
                0.0,
                0.0,
                320.0 * scale,
                96.0 * scale,
                48.0 * scale,
                64.0 * scale,
                24.0 * scale,
                Math.PI / 6.0,
                0.65,
                0.60,
                0.25,
                0.0,
                24.0 * scale);
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

    private record Fixture(SkyIslandPublishedAuthoredRealizationBinding binding) {}
}
