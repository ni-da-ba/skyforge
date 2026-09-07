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
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandMorphologySpec;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupLayout;
import io.github.nidaba.skyforge.world.SkyIslandAcceptedConvergenceCompilation;
import io.github.nidaba.skyforge.world.SkyIslandAcceptedConvergenceCompiler;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationAssociation;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationCatalog;
import io.github.nidaba.skyforge.world.SkyIslandCompiledWorldPublication;
import io.github.nidaba.skyforge.world.SkyIslandCompiledWorldPublisher;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandPublishedAuthoredRealizationBinding;
import io.github.nidaba.skyforge.world.SkyIslandRegionalIsolationEntry;
import io.github.nidaba.skyforge.world.SkyIslandRegionalIsolationNeighbor;
import io.github.nidaba.skyforge.world.SkyIslandRegionalIsolationProfile;
import io.github.nidaba.skyforge.world.SkyIslandRegionalIsolationProfiler;
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
import java.util.OptionalDouble;
import javax.imageio.ImageIO;

/** Generates AUTH-0092 deterministic regional island-isolation evidence. */
public final class AuthorshipRegionalIsolationEvidenceCorpusCli {
    public static final String EVIDENCE_ID = "authorship-regional-isolation-evidence-v1";

    private static final long AUTHORED_WORLD = 0x4155544830303932L;
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int PANEL_W = WIDTH / 3;
    private static final int PANEL_H = HEIGHT / 2;
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    private AuthorshipRegionalIsolationEvidenceCorpusCli() {}

    public static void main(String[] args) throws IOException {
        if (args.length > 1) {
            throw new IllegalArgumentException(
                    "usage: AuthorshipRegionalIsolationEvidenceCorpusCli [output-directory]");
        }
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", EVIDENCE_ID);
        Files.createDirectories(out);

        Fixture baseFixture = fixture(92101L, 3, 0.0, 0.0, 1.0);
        Fixture translatedFixture = fixture(92101L, 3, 12_500.0, -8_750.0, 1.0);
        Fixture scaledFixture = fixture(92101L, 3, 0.0, 0.0, 2.0);
        Fixture singletonFixture = fixture(92102L, 1, 0.0, 0.0, 1.0);

        SkyIslandRegionalIsolationProfiler profiler = new SkyIslandRegionalIsolationProfiler();
        SkyIslandRegionalIsolationProfile base = profiler.profile(baseFixture.binding());
        SkyIslandRegionalIsolationProfile translated = profiler.profile(translatedFixture.binding());
        SkyIslandRegionalIsolationProfile scaled = profiler.profile(scaledFixture.binding());
        SkyIslandRegionalIsolationProfile singleton = profiler.profile(singletonFixture.binding());

        boolean exactBinding = base.publicationId().equals(baseFixture.binding().publication().id())
                && base.authoredWorldSeed() == AUTHORED_WORLD
                && base.islands().stream()
                        .map(SkyIslandRegionalIsolationEntry::association)
                        .toList()
                        .equals(baseFixture.binding().associationCatalog().associations());
        boolean deterministic = equivalent(base, profiler.profile(baseFixture.binding()));
        boolean gapFirstNearest = exactGapFirstNearest(base);
        boolean singletonEmpty = singletonEmpty(singleton);
        boolean translation = translationCovariant(base, translated);
        boolean scale = scaleCovariant(base, scaled, 2.0);

        BufferedImage atlas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = atlas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        panel(g, 0, 0, "EXACT_BINDING", exactBinding,
                "AUTH-0058 publication + AUTH-0087 binding",
                "canonical AUTH-0046 association order retained",
                "no spatial association inference");
        panel(g, PANEL_W, 0, "GAP_FIRST_NEAREST", gapFirstNearest,
                "minimum nominal radial gap",
                "then center distance, then canonical order",
                "raw selected-peer provenance retained");
        panel(g, PANEL_W * 2, 0, "DETERMINISTIC", deterministic,
                "same exact binding -> same evidence",
                "no caller island subset or threshold",
                "no new random/key source");
        panel(g, 0, PANEL_H, "SINGLETON_EMPTY", singletonEmpty,
                "one-island publication is valid",
                "no fabricated peer",
                "aggregate nearest summaries remain empty");
        panel(g, PANEL_W, PANEL_H, "TRANSLATION", translation,
                "uniform world-horizontal translation",
                "selected peers preserved",
                "distance evidence preserved within FP tolerance");
        panel(g, PANEL_W * 2, PANEL_H, "SCALE_NO_POLICY", scale,
                "2x centers/radii -> 2x raw distances",
                "no isolated class / fauna / settlement threshold",
                "not physical terrain-edge separation");

        g.dispose();
        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());

        String manifest = "scenario,pass\n"
                + "EXACT_BINDING," + exactBinding + "\n"
                + "GAP_FIRST_NEAREST," + gapFirstNearest + "\n"
                + "DETERMINISTIC," + deterministic + "\n"
                + "SINGLETON_EMPTY," + singletonEmpty + "\n"
                + "TRANSLATION," + translation + "\n"
                + "SCALE_NO_POLICY," + scale + "\n";
        Files.writeString(out.resolve("manifest.csv"), manifest, StandardCharsets.UTF_8);

        StringBuilder islands = new StringBuilder(
                "association,volumeId,neighborAssociation,neighborVolumeId,centerDistance,nominalRadialGap\n");
        for (SkyIslandRegionalIsolationEntry entry : base.islands()) {
            SkyIslandRegionalIsolationNeighbor neighbor = entry.nearestNeighbor().orElseThrow();
            islands.append(entry.association().canonicalToken()).append(',')
                    .append(entry.association().realizedVolumeId().path()).append(',')
                    .append(neighbor.association().canonicalToken()).append(',')
                    .append(neighbor.association().realizedVolumeId().path()).append(',')
                    .append(neighbor.centerDistance()).append(',')
                    .append(neighbor.nominalRadialGap()).append('\n');
        }
        Files.writeString(out.resolve("islands.csv"), islands, StandardCharsets.UTF_8);

        StringBuilder profiles = new StringBuilder(
                "scenario,islandCount,minCenter,meanCenter,maxCenter,minGap,meanGap,maxGap\n");
        appendProfile(profiles, "BASE", base);
        appendProfile(profiles, "TRANSLATED", translated);
        appendProfile(profiles, "SCALED_2X", scaled);
        appendProfile(profiles, "SINGLETON", singleton);
        Files.writeString(out.resolve("profiles.csv"), profiles, StandardCharsets.UTF_8);

        Files.writeString(
                out.resolve("index.html"),
                """
                <!doctype html><html lang="en"><head><meta charset="utf-8">
                <title>AUTH-0092 regional island isolation evidence</title>
                <style>body{font-family:system-ui,sans-serif;max-width:1400px;margin:2rem auto;padding:0 1rem;background:#f4f3ed;color:#30343b}img{width:100%;border:1px solid #bbb;background:white}</style>
                </head><body>
                <h1>Regional island isolation evidence</h1>
                <p>AUTH-0092 derives raw world-horizontal center distance and nominal radial gap only from one exact AUTH-0087 published authored-realization binding. Nearest means minimum nominal radial gap, then center distance, then canonical association order.</p>
                <p>The result is planning evidence, not physical terrain-edge separation, an ecological isolation class, a fauna or settlement threshold, Minecraft range policy, or backend lifecycle behavior.</p>
                <img src="atlas.png" alt="AUTH-0092 proof atlas">
                <p><a href="manifest.csv">manifest.csv</a> · <a href="islands.csv">islands.csv</a> · <a href="profiles.csv">profiles.csv</a></p>
                </body></html>
                """,
                StandardCharsets.UTF_8);
    }

    private static void appendProfile(
            StringBuilder csv,
            String scenario,
            SkyIslandRegionalIsolationProfile profile) {
        csv.append(scenario).append(',')
                .append(profile.islandCount()).append(',')
                .append(format(profile.minimumNearestCenterDistance())).append(',')
                .append(format(profile.meanNearestCenterDistance())).append(',')
                .append(format(profile.maximumNearestCenterDistance())).append(',')
                .append(format(profile.minimumNearestNominalRadialGap())).append(',')
                .append(format(profile.meanNearestNominalRadialGap())).append(',')
                .append(format(profile.maximumNearestNominalRadialGap())).append('\n');
    }

    private static String format(OptionalDouble value) {
        return value.isPresent() ? Double.toString(value.getAsDouble()) : "";
    }

    private static boolean equivalent(
            SkyIslandRegionalIsolationProfile first,
            SkyIslandRegionalIsolationProfile second) {
        return first.publicationId().equals(second.publicationId())
                && first.authoredWorldSeed() == second.authoredWorldSeed()
                && first.islands().equals(second.islands())
                && sameOptional(first.minimumNearestCenterDistance(), second.minimumNearestCenterDistance(), 0.0)
                && sameOptional(first.meanNearestCenterDistance(), second.meanNearestCenterDistance(), 0.0)
                && sameOptional(first.maximumNearestCenterDistance(), second.maximumNearestCenterDistance(), 0.0)
                && sameOptional(first.minimumNearestNominalRadialGap(), second.minimumNearestNominalRadialGap(), 0.0)
                && sameOptional(first.meanNearestNominalRadialGap(), second.meanNearestNominalRadialGap(), 0.0)
                && sameOptional(first.maximumNearestNominalRadialGap(), second.maximumNearestNominalRadialGap(), 0.0);
    }

    private static boolean exactGapFirstNearest(SkyIslandRegionalIsolationProfile profile) {
        List<SkyIslandAuthoredRealizationAssociation> associations =
                profile.binding().associationCatalog().associations();
        for (SkyIslandRegionalIsolationEntry entry : profile.islands()) {
            SkyIslandAuthoredRealizationAssociation best = null;
            double bestCenter = 0.0;
            double bestGap = 0.0;
            for (SkyIslandAuthoredRealizationAssociation candidate : associations) {
                if (candidate.equals(entry.association())) {
                    continue;
                }
                double center = centerDistance(entry.association(), candidate);
                double gap = nominalGap(entry.association(), candidate, center);
                if (best == null
                        || Double.compare(gap, bestGap) < 0
                        || (Double.compare(gap, bestGap) == 0
                                && Double.compare(center, bestCenter) < 0)) {
                    best = candidate;
                    bestCenter = center;
                    bestGap = gap;
                }
            }
            SkyIslandRegionalIsolationNeighbor actual = entry.nearestNeighbor().orElseThrow();
            if (!actual.association().equals(best)
                    || Double.doubleToLongBits(actual.centerDistance())
                            != Double.doubleToLongBits(bestCenter)
                    || Double.doubleToLongBits(actual.nominalRadialGap())
                            != Double.doubleToLongBits(bestGap)) {
                return false;
            }
        }
        return true;
    }

    private static boolean singletonEmpty(SkyIslandRegionalIsolationProfile profile) {
        return profile.islandCount() == 1
                && profile.islands().getFirst().nearestNeighbor().isEmpty()
                && profile.minimumNearestCenterDistance().isEmpty()
                && profile.meanNearestCenterDistance().isEmpty()
                && profile.maximumNearestCenterDistance().isEmpty()
                && profile.minimumNearestNominalRadialGap().isEmpty()
                && profile.meanNearestNominalRadialGap().isEmpty()
                && profile.maximumNearestNominalRadialGap().isEmpty();
    }

    private static boolean translationCovariant(
            SkyIslandRegionalIsolationProfile base,
            SkyIslandRegionalIsolationProfile translated) {
        if (base.islandCount() != translated.islandCount()) {
            return false;
        }
        for (int index = 0; index < base.islandCount(); index++) {
            SkyIslandRegionalIsolationNeighbor a =
                    base.islands().get(index).nearestNeighbor().orElseThrow();
            SkyIslandRegionalIsolationNeighbor b =
                    translated.islands().get(index).nearestNeighbor().orElseThrow();
            if (!a.association().realizedVolumeId().equals(b.association().realizedVolumeId())
                    || Math.abs(a.centerDistance() - b.centerDistance()) > 1.0e-9
                    || Math.abs(a.nominalRadialGap() - b.nominalRadialGap()) > 1.0e-9) {
                return false;
            }
        }
        return true;
    }

    private static boolean scaleCovariant(
            SkyIslandRegionalIsolationProfile base,
            SkyIslandRegionalIsolationProfile scaled,
            double scale) {
        if (base.islandCount() != scaled.islandCount()) {
            return false;
        }
        for (int index = 0; index < base.islandCount(); index++) {
            SkyIslandRegionalIsolationNeighbor a =
                    base.islands().get(index).nearestNeighbor().orElseThrow();
            SkyIslandRegionalIsolationNeighbor b =
                    scaled.islands().get(index).nearestNeighbor().orElseThrow();
            if (!a.association().realizedVolumeId().equals(b.association().realizedVolumeId())
                    || Math.abs(a.centerDistance() * scale - b.centerDistance()) > 1.0e-9
                    || Math.abs(a.nominalRadialGap() * scale - b.nominalRadialGap()) > 1.0e-9) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameOptional(
            OptionalDouble first,
            OptionalDouble second,
            double tolerance) {
        return first.isPresent() == second.isPresent()
                && (first.isEmpty()
                        || Math.abs(first.getAsDouble() - second.getAsDouble()) <= tolerance);
    }

    private static double centerDistance(
            SkyIslandAuthoredRealizationAssociation first,
            SkyIslandAuthoredRealizationAssociation second) {
        var a = first.realizedVolume().compiledVolume().descriptor();
        var b = second.realizedVolume().compiledVolume().descriptor();
        return Math.hypot(b.centerX() - a.centerX(), b.centerZ() - a.centerZ());
    }

    private static double nominalGap(
            SkyIslandAuthoredRealizationAssociation first,
            SkyIslandAuthoredRealizationAssociation second,
            double centerDistance) {
        return Math.max(
                0.0,
                centerDistance
                        - first.authoredDescriptor().nominalRadius()
                        - second.authoredDescriptor().nominalRadius());
    }

    private static Fixture fixture(
            long rootSeed,
            int memberCount,
            double centerX,
            double centerZ,
            double scale) {
        SkyIslandCompiledWorldPublication publication =
                new SkyIslandCompiledWorldPublisher()
                        .publish(
                                acceptedCompilation(rootSeed, memberCount, centerX, centerZ, scale),
                                1L);
        ArrayList<SkyIslandAuthoredRealizationAssociation> associations =
                new ArrayList<>();
        int ordinal = 0;
        for (SkyIslandWorldVolume volume : publication.catalog().volumes()) {
            associations.add(SkyIslandAuthoredRealizationAssociation.of(
                    authored(40_000L + ordinal, volume),
                    volume));
            ordinal++;
        }
        Collections.reverse(associations);
        return new Fixture(
                new SkyIslandPublishedAuthoredRealizationBinding(
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
                SkyIslandIdentity.of(AUTHORED_WORLD, 9L, 92L, islandKey));
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
            double centerZ,
            double scale) {
        var registry = SkyIslandMorphologyProviders.builtInRegistry();
        var morphology = new ProviderMorphologySpec(
                SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF),
                0.0,
                0.0);
        SkyIslandArchipelagoRequest request =
                request(rootSeed, memberCount, centerX, centerZ, scale, morphology);
        SkyIslandArchipelagoPlan original =
                new SkyIslandArchipelagoPlanner().plan(request);
        SkyIslandSupportReservationRequirementSynthesis synthesis =
                new SkyIslandSupportReservationRequirementSynthesizer()
                        .synthesize(original, registry);
        SkyIslandSupportReplanProposal proposal =
                new SkyIslandSupportReplanProposalBuilder()
                        .propose(
                                request,
                                original,
                                synthesis,
                                ADEQUATE_VERTICAL,
                                SkyIslandSupportReplanMargin.ZERO);
        SkyIslandSupportConvergenceReport convergence =
                new SkyIslandSupportConvergenceExecutor()
                        .executeOnce(proposal, registry);
        if (convergence.outcome()
                != SkyIslandSupportConvergenceOutcome.ACCEPTED_ONE_PASS) {
            throw new IllegalStateException(
                    "AUTH-0092 evidence fixture did not converge");
        }
        return new SkyIslandAcceptedConvergenceCompiler()
                .compileOnce(convergence, registry);
    }

    private static SkyIslandArchipelagoRequest request(
            long rootSeed,
            int memberCount,
            double centerX,
            double centerZ,
            double scale,
            ProviderMorphologySpec morphology) {
        List<SkyIslandMorphologySpec> morphologies =
                java.util.stream.IntStream.range(0, memberCount)
                        .mapToObj(index -> morphology)
                        .toList();
        SkyIslandGroupTemplate template =
                new SkyIslandGroupTemplate(
                        "auth92",
                        SkyIslandGroupRole.ANCHOR,
                        descriptor(scale),
                        360.0 * scale,
                        48.0,
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
                320.0,
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
                320.0,
                96.0 * scale,
                48.0,
                64.0,
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

    private record Fixture(
            SkyIslandPublishedAuthoredRealizationBinding binding) {}
}
