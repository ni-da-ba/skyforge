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
import io.github.nidaba.skyforge.world.SkyIslandRegionalPetroleumSystemOpportunityEntry;
import io.github.nidaba.skyforge.world.SkyIslandRegionalPetroleumSystemOpportunityProfile;
import io.github.nidaba.skyforge.world.SkyIslandRegionalPetroleumSystemOpportunityProfiler;
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
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

/** Generates AUTH-0100 exact regional petroleum-system opportunity inventory evidence. */
public final class AuthorshipRegionalPetroleumSystemOpportunityCorpusCli {
    public static final String EVIDENCE_ID =
            "authorship-regional-petroleum-system-opportunity-v1";

    private static final long AUTHORED_WORLD = 0x4155544830313030L;
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int PANEL_W = WIDTH / 3;
    private static final int PANEL_H = HEIGHT / 2;
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    private AuthorshipRegionalPetroleumSystemOpportunityCorpusCli() {}

    public static void main(String[] args) throws IOException {
        if (args.length > 1) {
            throw new IllegalArgumentException(
                    "usage: AuthorshipRegionalPetroleumSystemOpportunityCorpusCli [output-directory]");
        }
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", EVIDENCE_ID);
        Files.createDirectories(out);

        SkyIslandPublishedAuthoredRealizationBinding binding = fixture(100101L);
        SkyIslandRegionalPetroleumSystemOpportunityProfiler profiler =
                new SkyIslandRegionalPetroleumSystemOpportunityProfiler();
        SkyIslandRegionalPetroleumSystemOpportunityProfile profile = profiler.profile(binding);
        SkyIslandRegionalPetroleumSystemOpportunityProfile repeat = profiler.profile(binding);

        boolean exactCoverage = profile.islands().stream()
                .map(SkyIslandRegionalPetroleumSystemOpportunityEntry::association)
                .toList()
                .equals(binding.associationCatalog().associations());
        boolean exactProfiles = profile.islands().stream().allMatch(entry ->
                entry.islandProfile().descriptor().equals(entry.association().authoredDescriptor()));
        boolean deterministic = deterministic(profile, repeat);
        boolean eligibility = exactEligibility(profile);
        boolean ranking = exactRanking(profile);
        boolean noPolicy = noPolicySurface();

        BufferedImage atlas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = atlas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        panel(
                g,
                0,
                0,
                "AUTH0087_COVERAGE",
                exactCoverage,
                "exact publication/authorship association set",
                "canonical association order retained",
                "no caller island subset");
        panel(
                g,
                PANEL_W,
                0,
                "AUTH0098_PROFILES",
                exactProfiles,
                "each profile belongs to exact descriptor",
                "petroleum-system opportunity unchanged",
                "no competing subsurface planner");
        panel(
                g,
                PANEL_W * 2,
                0,
                "DETERMINISTIC",
                deterministic,
                "same binding -> same raw inventory",
                "no new random/key source",
                "stable canonical provenance");
        panel(
                g,
                0,
                PANEL_H,
                "ZERO_NONZERO",
                eligibility,
                "eligibility exactly peakSystem > 0",
                "no new magnitude threshold",
                "province replan remains Content-owned");
        panel(
                g,
                PANEL_W,
                PANEL_H,
                "MEAN_RANKING",
                ranking,
                "descending unchanged AUTH-0098 mean",
                "stable canonical order for exact ties",
                "not strategic-node selection");
        panel(
                g,
                PANEL_W * 2,
                PANEL_H,
                "POLICY_BOUNDARY",
                noPolicy,
                "C22 owns STRATEGIC_NODE policy",
                "no guarantees / reserves / trade",
                "Implementation owns deposits/lifecycle");

        g.dispose();
        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());

        String manifest = "scenario,pass\n"
                + "AUTH0087_COVERAGE," + exactCoverage + "\n"
                + "AUTH0098_PROFILES," + exactProfiles + "\n"
                + "DETERMINISTIC," + deterministic + "\n"
                + "ZERO_NONZERO," + eligibility + "\n"
                + "MEAN_RANKING," + ranking + "\n"
                + "POLICY_BOUNDARY," + noPolicy + "\n";
        Files.writeString(out.resolve("manifest.csv"), manifest, StandardCharsets.UTF_8);

        StringBuilder inventory = new StringBuilder(
                "ordinal,volumeId,eligible,meanSystemOpportunity,peakSystemOpportunity,nonzeroSystemCells\n");
        for (int ordinal = 0; ordinal < profile.islands().size(); ordinal++) {
            SkyIslandRegionalPetroleumSystemOpportunityEntry entry =
                    profile.islands().get(ordinal);
            inventory.append(ordinal).append(',')
                    .append(entry.association().realizedVolume().id().path()).append(',')
                    .append(entry.geologicallyEligible()).append(',')
                    .append(entry.islandProfile().meanSystemOpportunity()).append(',')
                    .append(entry.islandProfile().peakSystemOpportunity()).append(',')
                    .append(entry.islandProfile().nonzeroSystemCellCount()).append('\n');
        }
        Files.writeString(out.resolve("inventory.csv"), inventory, StandardCharsets.UTF_8);

        StringBuilder rankingCsv =
                new StringBuilder("rank,volumeId,meanSystemOpportunity,peakSystemOpportunity\n");
        List<SkyIslandRegionalPetroleumSystemOpportunityEntry> ranked =
                profile.rankedEligibleIslands();
        for (int index = 0; index < ranked.size(); index++) {
            SkyIslandRegionalPetroleumSystemOpportunityEntry entry = ranked.get(index);
            rankingCsv.append(index + 1).append(',')
                    .append(entry.association().realizedVolume().id().path()).append(',')
                    .append(entry.meanSystemOpportunity()).append(',')
                    .append(entry.islandProfile().peakSystemOpportunity()).append('\n');
        }
        Files.writeString(out.resolve("ranking.csv"), rankingCsv, StandardCharsets.UTF_8);

        Files.writeString(
                out.resolve("index.html"),
                """
                <!doctype html><html lang="en"><head><meta charset="utf-8">
                <title>AUTH-0100 regional petroleum-system opportunity inventory</title>
                <style>body{font-family:system-ui,sans-serif;max-width:1400px;margin:2rem auto;padding:0 1rem;background:#f4f3ed;color:#30343b}img{width:100%;border:1px solid #bbb;background:white}</style>
                </head><body>
                <h1>Regional petroleum-system opportunity inventory</h1>
                <p>AUTH-0100 preserves one exact AUTH-0087 published association set and profiles each island through accepted AUTH-0098 petroleum-system opportunity.</p>
                <p>Eligibility is exactly the existing zero/nonzero geological boundary. Ranking is unchanged mean system opportunity. C22 owns R3 / STRATEGIC_NODE availability, province selection/replanning, guarantees, and trade/salvage; Implementation owns concrete petroleum deposits, extraction, and lifecycle.</p>
                <img src="atlas.png" alt="AUTH-0100 proof atlas">
                <p><a href="manifest.csv">manifest.csv</a> · <a href="inventory.csv">inventory.csv</a> · <a href="ranking.csv">ranking.csv</a></p>
                </body></html>
                """,
                StandardCharsets.UTF_8);

        System.out.println(out.resolve("index.html").toAbsolutePath());
    }

    private static boolean deterministic(
            SkyIslandRegionalPetroleumSystemOpportunityProfile first,
            SkyIslandRegionalPetroleumSystemOpportunityProfile second) {
        if (first.islandCount() != second.islandCount()) {
            return false;
        }
        for (int index = 0; index < first.islandCount(); index++) {
            SkyIslandRegionalPetroleumSystemOpportunityEntry a = first.islands().get(index);
            SkyIslandRegionalPetroleumSystemOpportunityEntry b = second.islands().get(index);
            if (!a.association().equals(b.association())
                    || !a.islandProfile().cells().equals(b.islandProfile().cells())) {
                return false;
            }
        }
        return true;
    }

    private static boolean exactEligibility(
            SkyIslandRegionalPetroleumSystemOpportunityProfile profile) {
        int expected = (int) profile.islands().stream()
                .filter(entry -> entry.islandProfile().peakSystemOpportunity() > 0.0)
                .count();
        return expected == profile.eligibleIslandCount()
                && profile.eligibleIslandCount() > 0;
    }

    private static boolean exactRanking(
            SkyIslandRegionalPetroleumSystemOpportunityProfile profile) {
        List<SkyIslandRegionalPetroleumSystemOpportunityEntry> canonical =
                profile.eligibleIslands();
        List<SkyIslandRegionalPetroleumSystemOpportunityEntry> ranked =
                profile.rankedEligibleIslands();
        if (canonical.size() != ranked.size()) {
            return false;
        }
        for (int index = 1; index < ranked.size(); index++) {
            SkyIslandRegionalPetroleumSystemOpportunityEntry previous = ranked.get(index - 1);
            SkyIslandRegionalPetroleumSystemOpportunityEntry current = ranked.get(index);
            if (previous.meanSystemOpportunity() < current.meanSystemOpportunity()) {
                return false;
            }
            if (Double.doubleToLongBits(previous.meanSystemOpportunity())
                            == Double.doubleToLongBits(current.meanSystemOpportunity())
                    && canonical.indexOf(previous) > canonical.indexOf(current)) {
                return false;
            }
        }
        return true;
    }

    private static boolean noPolicySurface() {
        List<String> forbidden = List.of(
                "availability",
                "guarantee",
                "strategicnode",
                "deposit",
                "reserve",
                "trade",
                "salvage",
                "minecraft");
        for (Class<?> type : List.of(
                SkyIslandRegionalPetroleumSystemOpportunityEntry.class,
                SkyIslandRegionalPetroleumSystemOpportunityProfile.class,
                SkyIslandRegionalPetroleumSystemOpportunityProfiler.class)) {
            for (Method method : type.getDeclaredMethods()) {
                String name = method.getName().toLowerCase(Locale.ROOT);
                if (forbidden.stream().anyMatch(name::contains)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static SkyIslandPublishedAuthoredRealizationBinding fixture(long rootSeed) {
        SkyIslandCompiledWorldPublication publication =
                new SkyIslandCompiledWorldPublisher().publish(acceptedCompilation(rootSeed), 1L);
        ArrayList<SkyIslandAuthoredRealizationAssociation> associations = new ArrayList<>();
        int ordinal = 0;
        for (SkyIslandWorldVolume volume : publication.catalog().volumes()) {
            associations.add(SkyIslandAuthoredRealizationAssociation.of(
                    authored(51_000L + ordinal, volume), volume));
            ordinal++;
        }
        Collections.reverse(associations);
        return new SkyIslandPublishedAuthoredRealizationBinding(
                publication,
                new SkyIslandAuthoredRealizationCatalog(
                        AUTHORED_WORLD, publication.catalog().rootSeed(), associations));
    }

    private static SkyIslandDescriptor authored(long islandKey, SkyIslandWorldVolume volume) {
        SkyIslandDescriptor base = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(AUTHORED_WORLD, 9L, 100L, islandKey));
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
                0.72,
                0.68,
                0.70,
                0.70,
                base.exposureTendency(),
                0.60,
                0.76,
                base.ecologicalPotential());
    }

    private static SkyIslandAcceptedConvergenceCompilation acceptedCompilation(long rootSeed) {
        var registry = SkyIslandMorphologyProviders.builtInRegistry();
        ProviderMorphologySpec morphology = new ProviderMorphologySpec(
                SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF), 0.0, 0.0);
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
            throw new IllegalStateException("AUTH-0100 evidence fixture did not converge");
        }
        return new SkyIslandAcceptedConvergenceCompiler().compileOnce(convergence, registry);
    }

    private static SkyIslandArchipelagoRequest request(
            long rootSeed, ProviderMorphologySpec morphology) {
        List<SkyIslandMorphologySpec> morphologies = List.of(morphology, morphology, morphology);
        SkyIslandGroupTemplate template = new SkyIslandGroupTemplate(
                "auth100-evidence",
                SkyIslandGroupRole.ANCHOR,
                descriptor(),
                360.0,
                48.0,
                0.0,
                morphologies,
                new SkyIslandGroupLayout.Chain(0.15, 800.0, 0.0, 0.0, 0.0, 0.0),
                1_400.0);
        return new SkyIslandArchipelagoRequest(
                rootSeed,
                0.0,
                0.0,
                320.0,
                500.0,
                List.of(template),
                new SkyIslandArchipelagoLayout.Hub(1_600.0, 0.0, 0.0, 0.0, 0.0));
    }

    private static SkyIslandVolumeDescriptor descriptor() {
        return new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                0L,
                0.0,
                0.0,
                320.0,
                96.0,
                48.0,
                64.0,
                24.0,
                Math.PI / 6.0,
                0.65,
                0.60,
                0.25,
                0.0,
                24.0);
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
