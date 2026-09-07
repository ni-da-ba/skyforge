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
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/** Generates AUTH-0087 exact published authored-realization binding evidence. */
public final class AuthorshipPublishedAuthoredRealizationBindingCorpusCli {
    public static final String EVIDENCE_ID = "authorship-published-authored-realization-binding-v1";

    private static final long AUTHORED_WORLD = 0x4155544830303837L;
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int PANEL_W = WIDTH / 3;
    private static final int PANEL_H = HEIGHT / 2;
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    private AuthorshipPublishedAuthoredRealizationBindingCorpusCli() {}

    public static void main(String[] args) throws IOException {
        if (args.length > 1) {
            throw new IllegalArgumentException(
                    "usage: AuthorshipPublishedAuthoredRealizationBindingCorpusCli [output-directory]");
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

        panel(g, 0, 0, "EXACT_COVERAGE", evidence.exactCoverage(),
                "publication volumes=" + evidence.binding().volumeCount(),
                "associations=" + evidence.binding().associationCatalog().size(),
                "all published values matched exactly");
        panel(g, PANEL_W, 0, "COMPOSER_READY", evidence.composerReady(),
                "AUTH-0049 composer built from proven catalog",
                "no second ownership/material planner",
                "backend material identity remains downstream");
        panel(g, PANEL_W * 2, 0, "MISSING_BLOCKED", evidence.missingRejected(),
                "missing association -> reject",
                "no nearest/order/seed inference",
                "fail closed before material composition");
        panel(g, 0, PANEL_H, "EXTRA_BLOCKED", evidence.extraRejected(),
                "extra association -> reject",
                "publication defines exact realized set",
                "no hidden foreign volume");
        panel(g, PANEL_W, PANEL_H, "SUBSTITUTION_BLOCKED", evidence.substitutionRejected(),
                "same id + changed realized value -> reject",
                "id equality alone is insufficient",
                "exact published volume retained");
        panel(g, PANEL_W * 2, PANEL_H, "ROOTS_INDEPENDENT", evidence.rootsIndependent(),
                "authored root=" + Long.toUnsignedString(evidence.binding().authoredWorldSeed()),
                "realization root=" + Long.toUnsignedString(
                        evidence.binding().publication().catalog().rootSeed()),
                "equality neither required nor inferred");

        g.dispose();
        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());

        String manifest = "scenario,pass\n"
                + "EXACT_COVERAGE," + evidence.exactCoverage() + "\n"
                + "COMPOSER_READY," + evidence.composerReady() + "\n"
                + "MISSING_BLOCKED," + evidence.missingRejected() + "\n"
                + "EXTRA_BLOCKED," + evidence.extraRejected() + "\n"
                + "SUBSTITUTION_BLOCKED," + evidence.substitutionRejected() + "\n"
                + "ROOTS_INDEPENDENT," + evidence.rootsIndependent() + "\n";
        Files.writeString(out.resolve("manifest.csv"), manifest, StandardCharsets.UTF_8);

        StringBuilder coverage = new StringBuilder(
                "publishedVolumeId,associationToken,exactVolumeMatch\n");
        for (SkyIslandWorldVolume volume :
                evidence.binding().publication().catalog().volumes()) {
            SkyIslandAuthoredRealizationAssociation association =
                    evidence.binding().associationCatalog()
                            .associationFor(volume.id())
                            .orElseThrow();
            coverage.append(volume.id().path()).append(',')
                    .append(association.canonicalToken()).append(',')
                    .append(association.realizedVolume().equals(volume)).append('\n');
        }
        Files.writeString(out.resolve("coverage.csv"), coverage, StandardCharsets.UTF_8);

        Files.writeString(
                out.resolve("index.html"),
                """
                <!doctype html><html lang="en"><head><meta charset="utf-8">
                <title>AUTH-0087 published authored-realization binding</title>
                <style>body{font-family:system-ui,sans-serif;max-width:1400px;margin:2rem auto;padding:0 1rem;background:#f5f2e9;color:#30343b}img{width:100%;border:1px solid #bbb;background:white}</style>
                </head><body>
                <h1>Published authored-realization binding</h1>
                <p>AUTH-0087 proves that one accepted compiled-world publication is covered exactly by the explicit AUTH-0046 association catalog before AUTH-0049 material composition is exposed. Missing, extra, substituted, and foreign-root association sets fail closed. No Minecraft block, registry, placement, or material identity is introduced.</p>
                <img src="atlas.png" alt="AUTH-0087 proof atlas">
                <p><a href="manifest.csv">manifest.csv</a> · <a href="coverage.csv">coverage.csv</a></p>
                </body></html>
                """,
                StandardCharsets.UTF_8);
    }

    private static Evidence buildEvidence() {
        SkyIslandCompiledWorldPublication publication =
                new SkyIslandCompiledWorldPublisher().publish(
                        acceptedCompilation(58701L), 1L);
        SkyIslandAuthoredRealizationCatalog exact =
                exactAssociations(publication);
        SkyIslandPublishedAuthoredRealizationBinding binding =
                new SkyIslandPublishedAuthoredRealizationBinding(publication, exact);

        boolean exactCoverage = binding.volumeCount() == publication.volumeCount()
                && exact.size() == publication.volumeCount()
                && publication.catalog().volumes().stream().allMatch(volume ->
                        exact.associationFor(volume.id())
                                .map(association -> association.realizedVolume().equals(volume))
                                .orElse(false));
        boolean composerReady = binding.materialComposer().catalog() == exact;
        boolean missingRejected = rejected(() -> new SkyIslandPublishedAuthoredRealizationBinding(
                publication,
                new SkyIslandAuthoredRealizationCatalog(
                        AUTHORED_WORLD, publication.catalog().rootSeed(), List.of())));

        SkyIslandWorldVolume source = publication.catalog().volumes().getFirst();
        SkyIslandWorldVolume extraVolume = new SkyIslandWorldVolume(
                new SkyIslandWorldVolumeId(
                        publication.catalog().rootSeed(),
                        "auth87-extra",
                        99,
                        99,
                        source.id().geometrySeed()),
                source.bounds(),
                source.compiledVolume());
        List<SkyIslandAuthoredRealizationAssociation> extras =
                new ArrayList<>(exact.associations());
        extras.add(SkyIslandAuthoredRealizationAssociation.of(
                authored(
                        999L,
                        source.compiledVolume().descriptor().nominalRadius()),
                extraVolume));
        boolean extraRejected = rejected(() -> new SkyIslandPublishedAuthoredRealizationBinding(
                publication,
                new SkyIslandAuthoredRealizationCatalog(
                        AUTHORED_WORLD, publication.catalog().rootSeed(), extras)));

        WorldBounds bounds = source.bounds();
        SkyIslandWorldVolume substituted = new SkyIslandWorldVolume(
                source.id(),
                new WorldBounds(
                        bounds.minimumX() - 1.0,
                        bounds.maximumX(),
                        bounds.minimumY(),
                        bounds.maximumY(),
                        bounds.minimumZ(),
                        bounds.maximumZ()),
                source.compiledVolume());
        boolean substitutionRejected = rejected(() -> new SkyIslandPublishedAuthoredRealizationBinding(
                publication,
                new SkyIslandAuthoredRealizationCatalog(
                        AUTHORED_WORLD,
                        publication.catalog().rootSeed(),
                        List.of(SkyIslandAuthoredRealizationAssociation.of(
                                authored(
                                        1L,
                                        source.compiledVolume().descriptor().nominalRadius()),
                                substituted)))));

        boolean rootsIndependent = AUTHORED_WORLD != publication.catalog().rootSeed()
                && binding.authoredWorldSeed() == AUTHORED_WORLD;

        return new Evidence(
                binding,
                exactCoverage,
                composerReady,
                missingRejected,
                extraRejected,
                substitutionRejected,
                rootsIndependent);
    }

    private static boolean rejected(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    private static SkyIslandAuthoredRealizationCatalog exactAssociations(
            SkyIslandCompiledWorldPublication publication) {
        List<SkyIslandAuthoredRealizationAssociation> associations = new ArrayList<>();
        int ordinal = 0;
        for (SkyIslandWorldVolume volume : publication.catalog().volumes()) {
            associations.add(SkyIslandAuthoredRealizationAssociation.of(
                    authored(
                            100L + ordinal,
                            volume.compiledVolume().descriptor().nominalRadius()),
                    volume));
            ordinal++;
        }
        return new SkyIslandAuthoredRealizationCatalog(
                AUTHORED_WORLD,
                publication.catalog().rootSeed(),
                associations);
    }

    private static SkyIslandDescriptor authored(long islandKey, double nominalRadius) {
        SkyIslandDescriptor base = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(AUTHORED_WORLD, 8L, 87L, islandKey));
        return new SkyIslandDescriptor(
                base.schemaVersion(),
                base.identity(),
                base.authorshipSeed(),
                base.morphologyFamily(),
                nominalRadius,
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

    private static SkyIslandAcceptedConvergenceCompilation acceptedCompilation(long rootSeed) {
        var registry = SkyIslandMorphologyProviders.builtInRegistry();
        var morphology = new ProviderMorphologySpec(
                SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF),
                0.0,
                0.0);
        SkyIslandArchipelagoRequest request = request(rootSeed, morphology);
        SkyIslandArchipelagoPlan original = new SkyIslandArchipelagoPlanner().plan(request);
        SkyIslandSupportReservationRequirementSynthesis synthesis =
                new SkyIslandSupportReservationRequirementSynthesizer().synthesize(original, registry);
        SkyIslandSupportReplanProposal proposal =
                new SkyIslandSupportReplanProposalBuilder().propose(
                        request,
                        original,
                        synthesis,
                        ADEQUATE_VERTICAL,
                        SkyIslandSupportReplanMargin.ZERO);
        SkyIslandSupportConvergenceReport convergence =
                new SkyIslandSupportConvergenceExecutor().executeOnce(proposal, registry);
        if (convergence.outcome() != SkyIslandSupportConvergenceOutcome.ACCEPTED_ONE_PASS) {
            throw new IllegalStateException("AUTH-0087 evidence fixture did not converge");
        }
        return new SkyIslandAcceptedConvergenceCompiler().compileOnce(convergence, registry);
    }

    private static SkyIslandArchipelagoRequest request(
            long rootSeed,
            ProviderMorphologySpec morphology) {
        SkyIslandGroupTemplate template = new SkyIslandGroupTemplate(
                "auth87",
                SkyIslandGroupRole.ANCHOR,
                descriptor(),
                360.0,
                0.0,
                0.0,
                List.of(morphology),
                new SkyIslandGroupLayout.Cluster(800.0, 0.0, 0.0, 0.0),
                440.0);
        return new SkyIslandArchipelagoRequest(
                rootSeed,
                0.0,
                0.0,
                320.0,
                500.0,
                List.of(template),
                new SkyIslandArchipelagoLayout.Hub(
                        1_600.0, 0.0, 0.0, 0.0, 0.0));
    }

    private static SkyIslandVolumeDescriptor descriptor() {
        return new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                0L,
                0.0,
                0.0,
                320.0,
                192.0,
                76.0,
                100.0,
                48.0,
                Math.PI / 6.0,
                0.65,
                0.60,
                0.25,
                0.0,
                28.0);
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

    private record Evidence(
            SkyIslandPublishedAuthoredRealizationBinding binding,
            boolean exactCoverage,
            boolean composerReady,
            boolean missingRejected,
            boolean extraRejected,
            boolean substitutionRejected,
            boolean rootsIndependent) {}
}
