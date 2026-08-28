package io.github.nidaba.skyforge.recipes.skyisland;

import io.github.nidaba.skyforge.kernel.graph.ArithmeticNode;
import io.github.nidaba.skyforge.kernel.graph.ArithmeticOperator;
import io.github.nidaba.skyforge.kernel.graph.ConstantNode;
import io.github.nidaba.skyforge.kernel.graph.CoordinateAxis;
import io.github.nidaba.skyforge.kernel.graph.CoordinateNode;
import io.github.nidaba.skyforge.kernel.graph.GraphNode;
import io.github.nidaba.skyforge.kernel.graph.GraphValueType;
import io.github.nidaba.skyforge.kernel.graph.IntersectionNode;
import io.github.nidaba.skyforge.kernel.graph.NodeId;
import io.github.nidaba.skyforge.kernel.graph.ProceduralGraph;
import io.github.nidaba.skyforge.kernel.seed.SeedDerivation;
import io.github.nidaba.skyforge.kernel.serialization.CanonicalGraphJson;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Experimental signal-free primary morphology-family compiler for SF-IMP-0018.
 *
 * <p>The family is intentionally recipe-layer state rather than descriptor schema. Every family
 * emits one finite positive-inside suspended mass whose upper and underside offsets share the same
 * signed primary-footprint residual. Family construction parameters are derived from stable seed
 * namespaces without introducing local noise.
 */
public final class MorphologyFamilySkyIslandVolumeRecipe {
    /** Recipe version for the first multi-family primary-morphology proof. */
    public static final int RECIPE_VERSION = 4;

    private static final double AZIMUTH_VARIATION = 0.10;
    private static final double MINIMUM_RADIUS_SCALE = 0.97;
    private static final double RADIUS_SCALE_RANGE = 0.06;
    private static final double MAXIMUM_UNDERSIDE_ASYMMETRY = 0.25;

    /**
     * Compiles one experimental family without seeded detail or secondary relief.
     *
     * @throws NullPointerException if either argument is null
     * @throws IllegalArgumentException if local signal enrichment is requested
     */
    public CompiledSkyIslandVolume compile(
            SkyIslandVolumeDescriptor descriptor, MorphologyFamily family) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(family, "family");
        if (descriptor.signalAmplitude() != 0.0) {
            throw new IllegalArgumentException(
                    "signalAmplitude must be zero for the primary morphology-family proof");
        }

        FamilyShape shape = FamilyShape.from(descriptor, family);
        ProceduralGraph upper = surfaceGraph(
                descriptor, family, shape, GraphValueType.SCALAR_FIELD_2, Surface.UPPER);
        ProceduralGraph underside = surfaceGraph(
                descriptor, family, shape, GraphValueType.SCALAR_FIELD_2, Surface.UNDERSIDE);
        ProceduralGraph density = densityGraph(descriptor, family, shape);
        return new CompiledSkyIslandVolume(
                descriptor,
                RECIPE_VERSION,
                CanonicalGraphJson.INTERSECTION_SCHEMA_VERSION,
                upper,
                underside,
                density,
                provenance(family));
    }

    private static ProceduralGraph surfaceGraph(
            SkyIslandVolumeDescriptor descriptor,
            MorphologyFamily family,
            FamilyShape shape,
            GraphValueType type,
            Surface surface) {
        GraphBuilder graph = new GraphBuilder(type);
        ProfileNodes profile = addProfile(graph, descriptor, family, shape);
        NodeId output = switch (surface) {
            case UPPER -> addUpperSurface(graph, descriptor, family, profile);
            case UNDERSIDE -> addUndersideSurface(graph, descriptor, family, profile);
        };
        return graph.build(output);
    }

    private static ProceduralGraph densityGraph(
            SkyIslandVolumeDescriptor descriptor,
            MorphologyFamily family,
            FamilyShape shape) {
        GraphBuilder graph = new GraphBuilder(GraphValueType.SCALAR_FIELD_3);
        ProfileNodes profile = addProfile(graph, descriptor, family, shape);
        NodeId upper = addUpperSurface(graph, descriptor, family, profile);
        NodeId underside = addUndersideSurface(graph, descriptor, family, profile);
        NodeId y = graph.coordinate("position.y", CoordinateAxis.Y);
        NodeId upperConstraint = graph.arithmetic(
                "density.upper-constraint", ArithmeticOperator.SUBTRACT, upper, y);
        NodeId lowerConstraint = graph.arithmetic(
                "density.lower-constraint", ArithmeticOperator.SUBTRACT, y, underside);
        NodeId density = graph.intersection(
                "density.solid-intersection", upperConstraint, lowerConstraint);
        return graph.build(density);
    }

    private static ProfileNodes addProfile(
            GraphBuilder graph,
            SkyIslandVolumeDescriptor descriptor,
            MorphologyFamily family,
            FamilyShape shape) {
        NodeId x = graph.coordinate("position.x", CoordinateAxis.X);
        NodeId z = graph.coordinate("position.z", CoordinateAxis.Z);
        NodeId centerX = graph.constant("descriptor.center-x", descriptor.centerX());
        NodeId centerZ = graph.constant("descriptor.center-z", descriptor.centerZ());
        NodeId dx = graph.arithmetic(
                "position.relative-x", ArithmeticOperator.SUBTRACT, x, centerX);
        NodeId dz = graph.arithmetic(
                "position.relative-z", ArithmeticOperator.SUBTRACT, z, centerZ);

        NodeId cosine = graph.constant("family.cos-azimuth", shape.cosine());
        NodeId sine = graph.constant("family.sin-azimuth", shape.sine());
        NodeId dxCosine = graph.arithmetic(
                "family.dx-cos", ArithmeticOperator.MULTIPLY, dx, cosine);
        NodeId dzSine = graph.arithmetic(
                "family.dz-sin", ArithmeticOperator.MULTIPLY, dz, sine);
        NodeId along = graph.arithmetic(
                "family.along-axis", ArithmeticOperator.ADD, dxCosine, dzSine);
        NodeId dzCosine = graph.arithmetic(
                "family.dz-cos", ArithmeticOperator.MULTIPLY, dz, cosine);
        NodeId dxSine = graph.arithmetic(
                "family.dx-sin", ArithmeticOperator.MULTIPLY, dx, sine);
        NodeId across = graph.arithmetic(
                "family.across-axis", ArithmeticOperator.SUBTRACT, dzCosine, dxSine);

        NodeId majorRadius = graph.constant("family.major-radius", shape.majorRadius());
        NodeId minorRadius = graph.constant("family.minor-radius", shape.minorRadius());
        NodeId alongNormalized = graph.arithmetic(
                "profile.along-normalized", ArithmeticOperator.DIVIDE, along, majorRadius);
        NodeId acrossNormalized = graph.arithmetic(
                "profile.across-normalized", ArithmeticOperator.DIVIDE, across, minorRadius);
        NodeId alongSquared = graph.arithmetic(
                "profile.along-squared",
                ArithmeticOperator.MULTIPLY,
                alongNormalized,
                alongNormalized);
        NodeId acrossSquared = graph.arithmetic(
                "profile.across-squared",
                ArithmeticOperator.MULTIPLY,
                acrossNormalized,
                acrossNormalized);
        NodeId rawRadiusSquared = graph.arithmetic(
                "profile.raw-radius-squared",
                ArithmeticOperator.ADD,
                alongSquared,
                acrossSquared);

        NodeId one = graph.constant("profile.one", 1.0);
        NodeId lobeDirectional = addLobeDirectional(
                graph, alongSquared, acrossSquared, rawRadiusSquared, one);
        NodeId radiusSquared;
        if (family == MorphologyFamily.LOBED) {
            NodeId lobeStrength = graph.constant(
                    "family.lobe-strength", shape.lobeStrength());
            NodeId weightedLobes = graph.arithmetic(
                    "family.weighted-lobes",
                    ArithmeticOperator.MULTIPLY,
                    lobeStrength,
                    lobeDirectional);
            NodeId radialFactor = graph.arithmetic(
                    "family.radial-factor",
                    ArithmeticOperator.ADD,
                    one,
                    weightedLobes);
            radiusSquared = graph.arithmetic(
                    "profile.radius-squared",
                    ArithmeticOperator.DIVIDE,
                    rawRadiusSquared,
                    radialFactor);
        } else {
            NodeId identity = graph.constant("family.radial-identity", 1.0);
            radiusSquared = graph.arithmetic(
                    "profile.radius-squared",
                    ArithmeticOperator.MULTIPLY,
                    rawRadiusSquared,
                    identity);
        }

        NodeId remaining = graph.arithmetic(
                "profile.remaining",
                ArithmeticOperator.SUBTRACT,
                one,
                radiusSquared);
        NodeId suspension = graph.constant(
                "descriptor.suspension-elevation", descriptor.suspensionElevation());
        return new ProfileNodes(
                alongNormalized,
                acrossNormalized,
                alongSquared,
                acrossSquared,
                radiusSquared,
                remaining,
                lobeDirectional,
                one,
                suspension);
    }

    private static NodeId addLobeDirectional(
            GraphBuilder graph,
            NodeId alongSquared,
            NodeId acrossSquared,
            NodeId rawRadiusSquared,
            NodeId one) {
        NodeId difference = graph.arithmetic(
                "family.lobe-difference",
                ArithmeticOperator.SUBTRACT,
                alongSquared,
                acrossSquared);
        NodeId differenceSquared = graph.arithmetic(
                "family.lobe-difference-squared",
                ArithmeticOperator.MULTIPLY,
                difference,
                difference);
        NodeId onePlusRadius = graph.arithmetic(
                "family.lobe-one-plus-radius",
                ArithmeticOperator.ADD,
                one,
                rawRadiusSquared);
        NodeId denominator = graph.arithmetic(
                "family.lobe-denominator",
                ArithmeticOperator.MULTIPLY,
                onePlusRadius,
                onePlusRadius);
        return graph.arithmetic(
                "family.lobe-directional",
                ArithmeticOperator.DIVIDE,
                differenceSquared,
                denominator);
    }

    private static NodeId addUpperSurface(
            GraphBuilder graph,
            SkyIslandVolumeDescriptor descriptor,
            MorphologyFamily family,
            ProfileNodes profile) {
        NodeId familyFactor = switch (family) {
            case MASSIF -> {
                double coastalShape =
                        1.0 - descriptor.coastalFalloff() / descriptor.nominalRadius();
                NodeId coastal = graph.constant("family.massif-coastal-shape", coastalShape);
                NodeId weightedRadius = graph.arithmetic(
                        "family.massif-weighted-radius",
                        ArithmeticOperator.MULTIPLY,
                        coastal,
                        profile.radiusSquared());
                yield graph.arithmetic(
                        "family.upper-factor",
                        ArithmeticOperator.ADD,
                        profile.one(),
                        weightedRadius);
            }
            case TABLELAND -> graph.arithmetic(
                    "family.upper-factor",
                    ArithmeticOperator.ADD,
                    profile.one(),
                    profile.radiusSquared());
            case SPINE -> {
                NodeId flankStrength = graph.constant("family.spine-flank-strength", 0.65);
                NodeId weightedAcross = graph.arithmetic(
                        "family.spine-weighted-across",
                        ArithmeticOperator.MULTIPLY,
                        flankStrength,
                        profile.acrossSquared());
                NodeId denominator = graph.arithmetic(
                        "family.spine-upper-denominator",
                        ArithmeticOperator.ADD,
                        profile.one(),
                        weightedAcross);
                yield graph.arithmetic(
                        "family.upper-factor",
                        ArithmeticOperator.DIVIDE,
                        profile.one(),
                        denominator);
            }
            case BASIN -> {
                NodeId centerFloor = graph.constant("family.basin-center-floor", 0.58);
                NodeId ringStrength = graph.constant("family.basin-ring-strength", 2.20);
                NodeId ring = graph.arithmetic(
                        "family.basin-ring",
                        ArithmeticOperator.MULTIPLY,
                        ringStrength,
                        profile.radiusSquared());
                yield graph.arithmetic(
                        "family.upper-factor",
                        ArithmeticOperator.ADD,
                        centerFloor,
                        ring);
            }
            case LOBED -> {
                NodeId shoulderStrength = graph.constant("family.lobed-shoulder-strength", 0.45);
                NodeId shoulder = graph.arithmetic(
                        "family.lobed-shoulder",
                        ArithmeticOperator.MULTIPLY,
                        shoulderStrength,
                        profile.radiusSquared());
                yield graph.arithmetic(
                        "family.upper-factor",
                        ArithmeticOperator.ADD,
                        profile.one(),
                        shoulder);
            }
        };

        NodeId crownProfile = graph.arithmetic(
                "family.crown-profile",
                ArithmeticOperator.MULTIPLY,
                profile.remaining(),
                familyFactor);
        NodeId elevation = graph.constant(
                "descriptor.upper-elevation", descriptor.upperElevation());
        NodeId offset = graph.arithmetic(
                "upper.offset", ArithmeticOperator.MULTIPLY, elevation, crownProfile);
        return graph.arithmetic(
                "upper.surface", ArithmeticOperator.ADD, profile.suspension(), offset);
    }

    private static NodeId addUndersideSurface(
            GraphBuilder graph,
            SkyIslandVolumeDescriptor descriptor,
            MorphologyFamily family,
            ProfileNodes profile) {
        NodeId taper = graph.constant(
                "descriptor.underside-taper", descriptor.undersideTaper());
        NodeId taperRadius = graph.arithmetic(
                "underside.taper-radius",
                ArithmeticOperator.MULTIPLY,
                taper,
                profile.radiusSquared());
        NodeId taperDenominator = graph.arithmetic(
                "underside.taper-denominator",
                ArithmeticOperator.ADD,
                profile.one(),
                taperRadius);
        NodeId taperedRemaining = graph.arithmetic(
                "underside.tapered-remaining",
                ArithmeticOperator.DIVIDE,
                profile.remaining(),
                taperDenominator);

        double asymmetry = MAXIMUM_UNDERSIDE_ASYMMETRY * descriptor.undersideAsymmetry();
        NodeId asymmetryStrength = graph.constant(
                "descriptor.underside-asymmetry", asymmetry);
        NodeId asymmetryTerm = graph.arithmetic(
                "underside.asymmetry-term",
                ArithmeticOperator.MULTIPLY,
                asymmetryStrength,
                profile.alongNormalized());
        NodeId asymmetrySquared = graph.arithmetic(
                "underside.asymmetry-squared",
                ArithmeticOperator.MULTIPLY,
                asymmetryTerm,
                asymmetryTerm);
        NodeId onePlusAsymmetry = graph.arithmetic(
                "underside.one-plus-asymmetry",
                ArithmeticOperator.ADD,
                profile.one(),
                asymmetryTerm);
        NodeId asymmetryFactor = graph.arithmetic(
                "underside.asymmetry-factor",
                ArithmeticOperator.ADD,
                onePlusAsymmetry,
                asymmetrySquared);

        NodeId familyDepthFactor = addFamilyDepthFactor(graph, family, profile);
        NodeId familyRemaining = graph.arithmetic(
                "underside.family-remaining",
                ArithmeticOperator.MULTIPLY,
                taperedRemaining,
                familyDepthFactor);
        NodeId shapedDepth = graph.arithmetic(
                "underside.shaped-depth",
                ArithmeticOperator.MULTIPLY,
                familyRemaining,
                asymmetryFactor);
        NodeId depth = graph.constant(
                "descriptor.underside-depth", descriptor.undersideDepth());
        NodeId offset = graph.arithmetic(
                "underside.offset", ArithmeticOperator.MULTIPLY, depth, shapedDepth);
        return graph.arithmetic(
                "underside.surface", ArithmeticOperator.SUBTRACT, profile.suspension(), offset);
    }

    private static NodeId addFamilyDepthFactor(
            GraphBuilder graph,
            MorphologyFamily family,
            ProfileNodes profile) {
        return switch (family) {
            case MASSIF -> {
                NodeId strength = graph.constant("family.massif-depth-strength", 0.35);
                NodeId denominator = graph.arithmetic(
                        "family.massif-depth-denominator",
                        ArithmeticOperator.ADD,
                        profile.one(),
                        profile.radiusSquared());
                NodeId concentrated = graph.arithmetic(
                        "family.massif-depth-concentrated",
                        ArithmeticOperator.DIVIDE,
                        strength,
                        denominator);
                yield graph.arithmetic(
                        "family.depth-factor",
                        ArithmeticOperator.ADD,
                        profile.one(),
                        concentrated);
            }
            case TABLELAND -> graph.constant("family.depth-factor", 0.72);
            case SPINE -> {
                NodeId strength = graph.constant("family.spine-depth-strength", 0.45);
                NodeId denominator = graph.arithmetic(
                        "family.spine-depth-denominator",
                        ArithmeticOperator.ADD,
                        profile.one(),
                        profile.acrossSquared());
                NodeId keel = graph.arithmetic(
                        "family.spine-keel",
                        ArithmeticOperator.DIVIDE,
                        strength,
                        denominator);
                yield graph.arithmetic(
                        "family.depth-factor",
                        ArithmeticOperator.ADD,
                        profile.one(),
                        keel);
            }
            case BASIN -> graph.constant("family.depth-factor", 0.85);
            case LOBED -> {
                NodeId strength = graph.constant("family.lobed-depth-strength", 0.20);
                NodeId shoulders = graph.arithmetic(
                        "family.lobed-depth-shoulders",
                        ArithmeticOperator.MULTIPLY,
                        strength,
                        profile.lobeDirectional());
                yield graph.arithmetic(
                        "family.depth-factor",
                        ArithmeticOperator.ADD,
                        profile.one(),
                        shoulders);
            }
        };
    }

    private static Map<String, List<NodeId>> provenance(MorphologyFamily family) {
        LinkedHashMap<String, List<NodeId>> result = new LinkedHashMap<>();
        result.put("morphology-family:" + family.identifier(), ids(
                "family.major-radius",
                "family.minor-radius",
                "profile.radius-squared",
                "family.upper-factor",
                "family.depth-factor"));
        result.put("horizontal-center", ids("descriptor.center-x", "descriptor.center-z"));
        result.put("suspension-elevation", ids("descriptor.suspension-elevation"));
        result.put("upper-elevation", ids("descriptor.upper-elevation", "upper.surface"));
        result.put("underside-depth", ids("descriptor.underside-depth", "underside.surface"));
        result.put("primary-orientation", ids("family.along-axis", "family.across-axis"));
        result.put("underside-taper", ids("descriptor.underside-taper", "underside.tapered-remaining"));
        result.put("underside-asymmetry", ids("descriptor.underside-asymmetry", "underside.asymmetry-factor"));
        result.put("signal-controls", List.of());
        return result;
    }

    private static List<NodeId> ids(String... values) {
        List<NodeId> result = new ArrayList<>(values.length);
        for (String value : values) {
            result.add(new NodeId(value));
        }
        return List.copyOf(result);
    }

    private enum Surface {
        UPPER,
        UNDERSIDE
    }

    private record ProfileNodes(
            NodeId alongNormalized,
            NodeId acrossNormalized,
            NodeId alongSquared,
            NodeId acrossSquared,
            NodeId radiusSquared,
            NodeId remaining,
            NodeId lobeDirectional,
            NodeId one,
            NodeId suspension) {}

    private record FamilyShape(
            double majorRadius,
            double minorRadius,
            double cosine,
            double sine,
            double lobeStrength) {
        private static FamilyShape from(
                SkyIslandVolumeDescriptor descriptor, MorphologyFamily family) {
            String prefix = "sky-island.family." + family.identifier();
            double radiusScale = MINIMUM_RADIUS_SCALE
                    + RADIUS_SCALE_RANGE * unit(descriptor.seed(), prefix + ".radius-scale");
            double angle = descriptor.ridgeAzimuth()
                    + AZIMUTH_VARIATION * signed(descriptor.seed(), prefix + ".azimuth");
            double ridge = descriptor.ridgeStrength();
            double majorFactor;
            double minorFactor;
            switch (family) {
                case MASSIF -> {
                    majorFactor = 1.0 + 0.32 * ridge;
                    minorFactor = 1.0 / (1.0 + 0.22 * ridge);
                }
                case TABLELAND -> {
                    majorFactor = 1.0 + 0.08 * ridge;
                    minorFactor = 1.0 - 0.04 * ridge;
                }
                case SPINE -> {
                    majorFactor = 1.32 + 0.08 * ridge;
                    minorFactor = 0.52 + 0.04 * (1.0 - ridge);
                }
                case BASIN -> {
                    majorFactor = 1.0 + 0.12 * ridge;
                    minorFactor = 0.90 + 0.04 * (1.0 - ridge);
                }
                case LOBED -> {
                    majorFactor = 0.92 + 0.04 * ridge;
                    minorFactor = 0.88 + 0.04 * (1.0 - ridge);
                }
                default -> throw new IllegalStateException("unknown morphology family: " + family);
            }
            double lobeStrength = 1.44
                    + 0.32 * unit(descriptor.seed(), prefix + ".lobe-strength");
            double majorRadius = descriptor.nominalRadius() * radiusScale * majorFactor;
            double minorRadius = descriptor.nominalRadius() * radiusScale * minorFactor;
            requireFinitePositive("family major radius", majorRadius);
            requireFinitePositive("family minor radius", minorRadius);
            requireFinitePositive("family lobe strength", lobeStrength);
            return new FamilyShape(
                    majorRadius,
                    minorRadius,
                    Math.cos(angle),
                    Math.sin(angle),
                    lobeStrength);
        }
    }

    private static double signed(long seed, String namespace) {
        return 2.0 * unit(seed, namespace) - 1.0;
    }

    private static double unit(long seed, String namespace) {
        long value = SeedDerivation.derive(seed, namespace);
        return (value >>> 11) * 0x1.0p-53;
    }

    private static void requireFinitePositive(String property, double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(property + " must be finite and positive");
        }
    }

    private static final class GraphBuilder {
        private final GraphValueType type;
        private final List<GraphNode> nodes = new ArrayList<>();

        private GraphBuilder(GraphValueType type) {
            this.type = type;
        }

        private NodeId coordinate(String id, CoordinateAxis axis) {
            NodeId nodeId = new NodeId(id);
            nodes.add(new CoordinateNode(nodeId, type, axis));
            return nodeId;
        }

        private NodeId constant(String id, double value) {
            NodeId nodeId = new NodeId(id);
            nodes.add(new ConstantNode(nodeId, type, value));
            return nodeId;
        }

        private NodeId arithmetic(
                String id, ArithmeticOperator operator, NodeId left, NodeId right) {
            NodeId nodeId = new NodeId(id);
            nodes.add(new ArithmeticNode(nodeId, type, operator, left, right));
            return nodeId;
        }

        private NodeId intersection(String id, NodeId left, NodeId right) {
            NodeId nodeId = new NodeId(id);
            nodes.add(new IntersectionNode(nodeId, left, right));
            return nodeId;
        }

        private ProceduralGraph build(NodeId output) {
            return new ProceduralGraph(nodes, output);
        }
    }
}
