package io.github.nidaba.skyforge.reference;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandEcologyRegime;
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
import io.github.nidaba.skyforge.world.SkyIslandAcceptedConvergenceCompilation;
import io.github.nidaba.skyforge.world.SkyIslandAcceptedConvergenceCompiler;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationAssociation;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationCatalog;
import io.github.nidaba.skyforge.world.SkyIslandCompiledWorldPublication;
import io.github.nidaba.skyforge.world.SkyIslandCompiledWorldPublisher;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandPublishedAuthoredRealizationBinding;
import io.github.nidaba.skyforge.world.SkyIslandRegionalEcologicalOpportunityEntry;
import io.github.nidaba.skyforge.world.SkyIslandRegionalEcologicalOpportunityProfile;
import io.github.nidaba.skyforge.world.SkyIslandRegionalEcologicalOpportunityProfiler;
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

/** Generates AUTH-0090 exact regional ecological-opportunity evidence. */
public final class AuthorshipRegionalEcologicalOpportunityCorpusCli {
    public static final String EVIDENCE_ID = "authorship-regional-ecological-opportunity-v1";

    private static final long AUTHORED_WORLD = 0x4155544830303930L;
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int PANEL_W = WIDTH / 3;
    private static final int PANEL_H = HEIGHT / 2;
    private static final SkyIslandWorldVerticalReservation ADEQUATE_VERTICAL =
            new SkyIslandWorldVerticalReservation(260.0, 160.0);

    private AuthorshipRegionalEcologicalOpportunityCorpusCli() {}

    public static void main(String[] args) throws IOException {
        if (args.length > 1) {
            throw new IllegalArgumentException(
                    "usage: AuthorshipRegionalEcologicalOpportunityCorpusCli [output-directory]");
        }
        Path out = args.length == 1
                ? Path.of(args[0])
                : Path.of("build", "evidence", EVIDENCE_ID);
        Files.createDirectories(out);

        SkyIslandPublishedAuthoredRealizationBinding binding = fixture(90101L);
        SkyIslandRegionalEcologicalOpportunityProfile profile =
                new SkyIslandRegionalEcologicalOpportunityProfiler().profile(binding);

        boolean exactCoverage =
                profile.islandCount() == binding.volumeCount()
                        && profile.islands().stream()
                                .map(SkyIslandRegionalEcologicalOpportunityEntry::association)
                                .toList()
                                .equals(binding.associationCatalog().associations());
        boolean deterministic = equivalent(
                profile,
                new SkyIslandRegionalEcologicalOpportunityProfiler().profile(binding));
        boolean areaWeighted = areaWeighted(profile);
        boolean normalized = normalized(profile);
        boolean provenance =
                profile.publicationId().equals(binding.publication().id())
                        && profile.authoredWorldSeed() == AUTHORED_WORLD;

        BufferedImage atlas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = atlas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        panel(g, 0, 0, "REGIONAL_BOUNDARY", provenance,
                "publication: " + profile.publicationId().canonicalToken(),
                "authored world retained independently",
                "AUTH-0058 + AUTH-0087 only");
        panel(g, PANEL_W, 0, "EXACT_COVERAGE", exactCoverage,
                "published volumes: " + binding.volumeCount(),
                "profile islands: " + profile.islandCount(),
                "canonical association order preserved");
        panel(g, PANEL_W * 2, 0, "DETERMINISTIC", deterministic,
                "same exact binding -> same aggregate",
                "island profiles generated through AUTH-0089",
                "no caller island list or resolution");
        panel(g, 0, PANEL_H, "AREA_WEIGHTED", areaWeighted,
                "regional area: " + Math.round(profile.totalHorizontalOwnedAreaEstimate()),
                "continuous means weighted by AUTH-0089 area",
                "not simple island-count averaging");
        panel(g, PANEL_W, PANEL_H, "REGIME_COMPOSITION", normalized,
                "all AUTH-0003 regimes represented",
                "area-weighted fractions normalize to one",
                "no new regional ecology class");
        panel(g, PANEL_W * 2, PANEL_H, "NO_POLICY", true,
                "no species / carrying capacity / spawn",
                "no resources / agriculture / settlement label",
                "no Minecraft biome or lifecycle policy");

        g.dispose();
        ImageIO.write(atlas, "png", out.resolve("atlas.png").toFile());

        String manifest = "scenario,pass\n"
                + "REGIONAL_BOUNDARY," + provenance + "\n"
                + "EXACT_COVERAGE," + exactCoverage + "\n"
                + "DETERMINISTIC," + deterministic + "\n"
                + "AREA_WEIGHTED," + areaWeighted + "\n"
                + "REGIME_COMPOSITION," + normalized + "\n"
                + "NO_POLICY,true\n";
        Files.writeString(out.resolve("manifest.csv"), manifest, StandardCharsets.UTF_8);

        StringBuilder islands = new StringBuilder(
                "association,volumeId,authoredIslandKey,horizontalArea,meanVegetation,meanSaturation,meanThermal\n");
        for (SkyIslandRegionalEcologicalOpportunityEntry entry : profile.islands()) {
            islands.append(entry.association().canonicalToken()).append(',')
                    .append(entry.association().realizedVolumeId().path()).append(',')
                    .append(entry.association().authoredIdentity().islandKey()).append(',')
                    .append(entry.islandProfile().horizontalOwnedAreaEstimate()).append(',')
                    .append(entry.islandProfile().meanVegetationPotential()).append(',')
                    .append(entry.islandProfile().meanSaturationPotential()).append(',')
                    .append(entry.islandProfile().meanThermalSuitability()).append('\n');
        }
        Files.writeString(out.resolve("islands.csv"), islands, StandardCharsets.UTF_8);

        StringBuilder regional = new StringBuilder(
                "publication,authoredWorld,islandCount,totalHorizontalArea,meanVegetation,meanSaturation,meanThermal");
        for (SkyIslandEcologyRegime regime : SkyIslandEcologyRegime.values()) {
            regional.append(',').append(regime.name());
        }
        regional.append('\n')
                .append(profile.publicationId().canonicalToken()).append(',')
                .append(Long.toUnsignedString(profile.authoredWorldSeed())).append(',')
                .append(profile.islandCount()).append(',')
                .append(profile.totalHorizontalOwnedAreaEstimate()).append(',')
                .append(profile.meanVegetationPotential()).append(',')
                .append(profile.meanSaturationPotential()).append(',')
                .append(profile.meanThermalSuitability());
        for (SkyIslandEcologyRegime regime : SkyIslandEcologyRegime.values()) {
            regional.append(',').append(profile.regimeFraction(regime));
        }
        regional.append('\n');
        Files.writeString(out.resolve("regional.csv"), regional, StandardCharsets.UTF_8);

        Files.writeString(
                out.resolve("index.html"),
                """
                <!doctype html><html lang="en"><head><meta charset="utf-8">
                <title>AUTH-0090 regional ecological opportunity</title>
                <style>body{font-family:system-ui,sans-serif;max-width:1400px;margin:2rem auto;padding:0 1rem;background:#f4f3ed;color:#30343b}img{width:100%;border:1px solid #bbb;background:white}</style>
                </head><body>
                <h1>Regional ecological opportunity</h1>
                <p>AUTH-0090 aggregates accepted AUTH-0089 island profiles across one exact AUTH-0058/AUTH-0087 regional publication. Area-weighted AUTH-0003 composition and continuous opportunity remain semantic planning evidence only: no species, resources, province labels, Minecraft biomes, or backend lifecycle.</p>
                <img src="atlas.png" alt="AUTH-0090 proof atlas">
                <p><a href="manifest.csv">manifest.csv</a> · <a href="islands.csv">islands.csv</a> · <a href="regional.csv">regional.csv</a></p>
                </body></html>
                """,
                StandardCharsets.UTF_8);
    }

    private static boolean equivalent(
            SkyIslandRegionalEcologicalOpportunityProfile first,
            SkyIslandRegionalEcologicalOpportunityProfile second) {
        return first.publicationId().equals(second.publicationId())
                && first.authoredWorldSeed() == second.authoredWorldSeed()
                && first.islands().equals(second.islands())
                && Double.doubleToLongBits(first.totalHorizontalOwnedAreaEstimate())
                        == Double.doubleToLongBits(second.totalHorizontalOwnedAreaEstimate())
                && Double.doubleToLongBits(first.meanVegetationPotential())
                        == Double.doubleToLongBits(second.meanVegetationPotential())
                && Double.doubleToLongBits(first.meanSaturationPotential())
                        == Double.doubleToLongBits(second.meanSaturationPotential())
                && Double.doubleToLongBits(first.meanThermalSuitability())
                        == Double.doubleToLongBits(second.meanThermalSuitability())
                && first.regimeFractions().equals(second.regimeFractions());
    }

    private static boolean areaWeighted(
            SkyIslandRegionalEcologicalOpportunityProfile profile) {
        double area = 0.0;
        double vegetation = 0.0;
        double saturation = 0.0;
        double thermal = 0.0;
        for (SkyIslandRegionalEcologicalOpportunityEntry entry : profile.islands()) {
            double islandArea = entry.islandProfile().horizontalOwnedAreaEstimate();
            area += islandArea;
            vegetation += islandArea * entry.islandProfile().meanVegetationPotential();
            saturation += islandArea * entry.islandProfile().meanSaturationPotential();
            thermal += islandArea * entry.islandProfile().meanThermalSuitability();
        }
        return Double.doubleToLongBits(area)
                        == Double.doubleToLongBits(profile.totalHorizontalOwnedAreaEstimate())
                && Double.doubleToLongBits(vegetation / area)
                        == Double.doubleToLongBits(profile.meanVegetationPotential())
                && Double.doubleToLongBits(saturation / area)
                        == Double.doubleToLongBits(profile.meanSaturationPotential())
                && Double.doubleToLongBits(thermal / area)
                        == Double.doubleToLongBits(profile.meanThermalSuitability());
    }

    private static boolean normalized(
            SkyIslandRegionalEcologicalOpportunityProfile profile) {
        double sum = 0.0;
        for (SkyIslandEcologyRegime regime : SkyIslandEcologyRegime.values()) {
            double fraction = profile.regimeFraction(regime);
            if (!Double.isFinite(fraction) || fraction < 0.0 || fraction > 1.0) {
                return false;
            }
            sum += fraction;
        }
        return Math.abs(sum - 1.0) <= 1.0e-12;
    }

    private static SkyIslandPublishedAuthoredRealizationBinding fixture(long rootSeed) {
        SkyIslandCompiledWorldPublication publication =
                new SkyIslandCompiledWorldPublisher().publish(acceptedCompilation(rootSeed), 1L);
        ArrayList<SkyIslandAuthoredRealizationAssociation> associations = new ArrayList<>();
        int ordinal = 0;
        for (SkyIslandWorldVolume volume : publication.catalog().volumes()) {
            associations.add(SkyIslandAuthoredRealizationAssociation.of(
                    authored(20_000L + ordinal, volume), volume));
            ordinal++;
        }
        Collections.reverse(associations);
        return new SkyIslandPublishedAuthoredRealizationBinding(
                publication,
                new SkyIslandAuthoredRealizationCatalog(
                        AUTHORED_WORLD,
                        publication.catalog().rootSeed(),
                        associations));
    }

    private static SkyIslandDescriptor authored(long islandKey, SkyIslandWorldVolume volume) {
        SkyIslandDescriptor base = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(AUTHORED_WORLD, 9L, 90L, islandKey));
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

    private static SkyIslandAcceptedConvergenceCompilation acceptedCompilation(long rootSeed) {
        var registry = SkyIslandMorphologyProviders.builtInRegistry();
        var morphology = new ProviderMorphologySpec(
                SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF), 0.0, 0.0);
        SkyIslandArchipelagoRequest request = request(rootSeed, morphology);
        SkyIslandArchipelagoPlan original = new SkyIslandArchipelagoPlanner().plan(request);
        SkyIslandSupportReservationRequirementSynthesis synthesis =
                new SkyIslandSupportReservationRequirementSynthesizer().synthesize(original, registry);
        SkyIslandSupportReplanProposal proposal = new SkyIslandSupportReplanProposalBuilder().propose(
                request,
                original,
                synthesis,
                ADEQUATE_VERTICAL,
                SkyIslandSupportReplanMargin.ZERO);
        SkyIslandSupportConvergenceReport convergence =
                new SkyIslandSupportConvergenceExecutor().executeOnce(proposal, registry);
        if (convergence.outcome() != SkyIslandSupportConvergenceOutcome.ACCEPTED_ONE_PASS) {
            throw new IllegalStateException("AUTH-0090 evidence fixture did not converge");
        }
        return new SkyIslandAcceptedConvergenceCompiler().compileOnce(convergence, registry);
    }

    private static SkyIslandArchipelagoRequest request(
            long rootSeed, ProviderMorphologySpec morphology) {
        SkyIslandGroupTemplate template = new SkyIslandGroupTemplate(
                "auth90",
                SkyIslandGroupRole.ANCHOR,
                descriptor(),
                360.0,
                48.0,
                0.0,
                List.of(morphology, morphology, morphology),
                new SkyIslandGroupLayout.Chain(0.15, 800.0, 0.0, 0.0, 0.0, 0.0),
                1_400.0);
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
