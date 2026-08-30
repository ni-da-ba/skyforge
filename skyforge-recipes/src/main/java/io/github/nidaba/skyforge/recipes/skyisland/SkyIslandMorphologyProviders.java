package io.github.nidaba.skyforge.recipes.skyisland;

import io.github.nidaba.skyforge.kernel.graph.ConstantNode;
import io.github.nidaba.skyforge.kernel.graph.GraphValueType;
import io.github.nidaba.skyforge.kernel.graph.NodeId;
import io.github.nidaba.skyforge.kernel.graph.ProceduralGraph;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Factory and registry helpers for Skyforge's accepted built-in morphology providers. */
public final class SkyIslandMorphologyProviders {
    private static final String NAMESPACE = "skyforge";

    private SkyIslandMorphologyProviders() {}

    /** Returns one provider adapter over an accepted built-in morphology family. */
    public static SkyIslandMorphologyProvider builtIn(MorphologyFamily family) {
        return new BuiltInProvider(Objects.requireNonNull(family, "family"));
    }

    /** Returns an immutable registry containing all accepted built-in providers. */
    public static SkyIslandMorphologyProviderRegistry builtInRegistry() {
        SkyIslandMorphologyProviderRegistry.Builder builder =
                SkyIslandMorphologyProviderRegistry.builder();
        for (MorphologyFamily family : MorphologyFamily.values()) {
            builder.register(builtIn(family));
        }
        return builder.build();
    }

    /** Returns the stable provider ID corresponding to one accepted built-in family. */
    public static MorphologyProviderId builtInId(MorphologyFamily family) {
        return new MorphologyProviderId(NAMESPACE, Objects.requireNonNull(family, "family").identifier());
    }

    private static final class BuiltInProvider implements SkyIslandMorphologyProvider {
        private static final NodeId FOOTPRINT = new NodeId("profile.remaining");
        private static final NodeId ALONG = new NodeId("profile.along-normalized");
        private static final NodeId ACROSS = new NodeId("profile.across-normalized");
        private static final NodeId LOBE = new NodeId("family.lobe-directional");
        private static final NodeId UPPER_FACTOR = new NodeId("family.upper-factor");
        private static final NodeId DEPTH_FACTOR = new NodeId("family.depth-factor");
        private static final NodeId GENERIC_SECONDARY_FACTOR = new NodeId("secondary.upper-factor");
        private static final NodeId FAMILY_SECONDARY_FACTOR = new NodeId("family-aware.upper-factor");

        private final MorphologyFamily family;
        private final MorphologyFamilySkyIslandVolumeRecipe primaryRecipe =
                new MorphologyFamilySkyIslandVolumeRecipe();
        private final FamilyAwareMorphologySkyIslandVolumeRecipe familyAwareRecipe =
                new FamilyAwareMorphologySkyIslandVolumeRecipe();

        private BuiltInProvider(MorphologyFamily family) {
            this.family = family;
        }

        @Override
        public MorphologyProviderId id() {
            return builtInId(family);
        }

        @Override
        public PrimaryMorphologyContribution compilePrimary(SkyIslandVolumeDescriptor descriptor) {
            CompiledSkyIslandVolume volume = primaryRecipe.compile(descriptor, family);
            return new PrimaryMorphologyContribution(
                    volume,
                    FOOTPRINT,
                    ALONG,
                    ACROSS,
                    Optional.of(LOBE),
                    UPPER_FACTOR,
                    DEPTH_FACTOR);
        }

        @Override
        public Optional<SecondaryMorphologyContribution> compileSecondaryMorphology(
                SkyIslandVolumeDescriptor descriptor, double amplitude) {
            requireAmplitude(amplitude);
            if (amplitude == 0.0) {
                ProceduralGraph neutral = new ProceduralGraph(
                        List.of(new ConstantNode(
                                new NodeId("provider.secondary.neutral"),
                                GraphValueType.SCALAR_FIELD_2,
                                1.0)),
                        new NodeId("provider.secondary.neutral"));
                return Optional.of(new SecondaryMorphologyContribution(neutral, 1.0, 1.0));
            }

            SkyIslandVolumeDescriptor carrier = withSignalAmplitude(descriptor, amplitude);
            CompiledSkyIslandVolume compiled = familyAwareRecipe.compile(carrier, family);
            NodeId output = family == MorphologyFamily.MASSIF
                    ? GENERIC_SECONDARY_FACTOR
                    : FAMILY_SECONDARY_FACTOR;
            ProceduralGraph factor = new ProceduralGraph(compiled.upperSurfaceGraph().nodes(), output);
            double fullMinimum = FamilyAwareSecondaryMorphologyComposition.minimumUpperFactor(family);
            double fullMaximum = FamilyAwareSecondaryMorphologyComposition.maximumUpperFactor(family);
            double minimum = 1.0 + amplitude * (fullMinimum - 1.0);
            double maximum = 1.0 + amplitude * (fullMaximum - 1.0);
            return Optional.of(new SecondaryMorphologyContribution(factor, minimum, maximum));
        }

        private static SkyIslandVolumeDescriptor withSignalAmplitude(
                SkyIslandVolumeDescriptor descriptor, double amplitude) {
            Objects.requireNonNull(descriptor, "descriptor");
            return new SkyIslandVolumeDescriptor(
                    SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                    descriptor.seed(),
                    descriptor.centerX(),
                    descriptor.centerZ(),
                    descriptor.suspensionElevation(),
                    descriptor.nominalRadius(),
                    descriptor.upperElevation(),
                    descriptor.undersideDepth(),
                    descriptor.coastalFalloff(),
                    descriptor.ridgeAzimuth(),
                    descriptor.ridgeStrength(),
                    descriptor.undersideTaper(),
                    descriptor.undersideAsymmetry(),
                    amplitude,
                    descriptor.signalScale());
        }

        private static void requireAmplitude(double amplitude) {
            if (!Double.isFinite(amplitude) || amplitude < 0.0 || amplitude > 1.0) {
                throw new IllegalArgumentException(
                        "secondary morphology amplitude must be finite and in [0, 1]");
            }
        }
    }
}
