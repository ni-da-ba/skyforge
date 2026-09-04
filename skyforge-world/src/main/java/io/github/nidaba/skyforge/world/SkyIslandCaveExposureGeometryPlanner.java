package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Converts accepted AUTH-0028 exposure intent into geology-steered boundary connection geometry.
 *
 * <p>The planner preserves the cave-side anchor and accepted exposure side but may move the actual
 * mouth modestly in x/z. Candidate routes remain inside current naturalized ownership and are scored
 * against continuous geology. The original straight projection is always included as the baseline.
 */
public final class SkyIslandCaveExposureGeometryPlanner {
    private static final int CONNECTION_SAMPLES = 15;
    private static final long EXPOSURE_GEOMETRY_DOMAIN = 0x4558504F5347454FL;
    private static final double MAX_MOUTH_OFFSET_FRACTION = 0.065;
    private static final double MAX_BEND_FRACTION = 0.035;

    private SkyIslandCaveExposureGeometryPlanner() {}

    public static SkyIslandCaveExposureGeometryPlan plan(SkyIslandDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        SkyIslandCaveExposurePlan exposure = SkyIslandCaveExposurePlanner.plan(descriptor);
        if (exposure.intents().isEmpty()) {
            return new SkyIslandCaveExposureGeometryPlan(descriptor, exposure, List.of());
        }

        SkyIslandGeologyFieldSet geology = SkyIslandGeologyFieldSet.create(descriptor);
        SkyIslandSemanticFieldSet semantic = SkyIslandSemanticFieldSet.create(descriptor);
        List<SkyIslandCaveExposureConnectionGeometry> connections = new ArrayList<>();

        for (SkyIslandCaveExposureIntent intent : exposure.intents()) {
            SkyIslandCaveSystemGeometry system = exposure.geometry().systems().stream()
                    .filter(candidate -> candidate.systemId() == intent.systemId())
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("exposure intent lost its cave geometry system"));
            SourceScale sourceScale = sourceScale(system, intent);
            connections.add(buildConnection(
                    descriptor,
                    geology,
                    semantic,
                    intent,
                    sourceScale));
        }

        connections.sort(Comparator.comparingInt(SkyIslandCaveExposureConnectionGeometry::systemId));
        return new SkyIslandCaveExposureGeometryPlan(descriptor, exposure, connections);
    }

    private static SkyIslandCaveExposureConnectionGeometry buildConnection(
            SkyIslandDescriptor descriptor,
            SkyIslandGeologyFieldSet geology,
            SkyIslandSemanticFieldSet semantic,
            SkyIslandCaveExposureIntent intent,
            SourceScale sourceScale) {
        double radius = descriptor.nominalRadius();
        double phase = phase(
                descriptor.authorshipSeed()
                        ^ EXPOSURE_GEOMETRY_DOMAIN
                        ^ ((long) intent.systemId() * 0x9E3779B97F4A7C15L)
                        ^ (intent.side() == SkyIslandCaveExposureSide.UPPER_SURFACE
                                ? 0x55505045524C494EL
                                : 0x554E4445524C494EL));

        Candidate baseline = evaluateCandidate(
                descriptor,
                geology,
                semantic,
                intent,
                intent.boundaryAnchor(),
                midpoint(intent.caveAnchor(), intent.boundaryAnchor()),
                0.0,
                0.0,
                0);
        if (baseline == null) {
            throw new IllegalStateException("accepted exposure intent cannot realize its own projected baseline");
        }

        List<Candidate> candidates = new ArrayList<>();
        candidates.add(baseline);

        double[] mouthFractions = {
            0.018,
            0.035,
            MAX_MOUTH_OFFSET_FRACTION
        };
        double[] bendSigns = {-1.0, 0.0, 1.0};

        int tieBreak = 1;
        for (int direction = 0; direction < 8; direction++) {
            double angle = phase + direction * (Math.PI / 4.0);
            double ux = Math.cos(angle);
            double uz = Math.sin(angle);
            double px = -uz;
            double pz = ux;

            for (double mouthFraction : mouthFractions) {
                double mouthScale = radius
                        * mouthFraction
                        * (0.72 + 0.28 * intent.semanticGap());
                SkyIslandSubsurfacePosition mouth = new SkyIslandSubsurfacePosition(
                        intent.boundaryAnchor().x() + ux * mouthScale,
                        intent.boundaryAnchor().z() + uz * mouthScale,
                        intent.boundaryAnchor().depthFraction());

                for (double bendSign : bendSigns) {
                    double bendScale = radius
                            * MAX_BEND_FRACTION
                            * bendSign
                            * (0.55 + 0.45 * intent.semanticGap());
                    SkyIslandSubsurfacePosition midpoint = midpoint(intent.caveAnchor(), mouth);
                    SkyIslandSubsurfacePosition control = new SkyIslandSubsurfacePosition(
                            midpoint.x() + px * bendScale,
                            midpoint.z() + pz * bendScale,
                            midpoint.depthFraction());

                    Candidate candidate = evaluateCandidate(
                            descriptor,
                            geology,
                            semantic,
                            intent,
                            mouth,
                            control,
                            mouthScale / radius,
                            Math.abs(bendScale) / radius,
                            tieBreak++);
                    if (candidate != null) {
                        candidates.add(candidate);
                    }
                }
            }
        }

        Candidate best = candidates.stream()
                .max(Candidate.ORDER)
                .orElseThrow();

        double mouthFactor = clamp(
                0.56
                        + 0.16 * intent.weatheringSupport()
                        + 0.08 * intent.proximitySupport(),
                0.54,
                0.80);
        double startHorizontalRadius = clamp(
                sourceScale.horizontalRadius() * 0.72,
                radius * 0.008,
                radius * 0.032);
        double startDepthRadius = clamp(
                sourceScale.depthRadius() * 0.72,
                0.008,
                0.055);
        double mouthHorizontalRadius = startHorizontalRadius * mouthFactor;
        double mouthDepthRadius = startDepthRadius * mouthFactor;

        List<SkyIslandCavePassagePoint> points = new ArrayList<>(CONNECTION_SAMPLES);
        for (int sample = 0; sample < CONNECTION_SAMPLES; sample++) {
            double t = sample / (CONNECTION_SAMPLES - 1.0);
            SkyIslandSubsurfacePosition position = quadratic(
                    intent.caveAnchor(),
                    best.control(),
                    best.mouth(),
                    t);
            double taper = smoothstep(0.0, 1.0, t);
            double horizontalRadius = lerp(startHorizontalRadius, mouthHorizontalRadius, taper);
            double depthRadius = lerp(startDepthRadius, mouthDepthRadius, taper);
            points.add(new SkyIslandCavePassagePoint(position, horizontalRadius, depthRadius));
        }

        return new SkyIslandCaveExposureConnectionGeometry(
                intent.systemId(),
                intent.side(),
                intent,
                points,
                best.geologicSupport(),
                baseline.geologicSupport(),
                best.mouthOffsetFraction(),
                normalizedMaxDeviation(points, radius));
    }

    private static Candidate evaluateCandidate(
            SkyIslandDescriptor descriptor,
            SkyIslandGeologyFieldSet geology,
            SkyIslandSemanticFieldSet semantic,
            SkyIslandCaveExposureIntent intent,
            SkyIslandSubsurfacePosition mouth,
            SkyIslandSubsurfacePosition control,
            double mouthOffsetFraction,
            double bendFraction,
            int tieBreak) {
        if (semantic.interiority().sample(mouth.surfacePosition()) <= 0.0
                || semantic.interiority().sample(control.surfacePosition()) <= 0.0) {
            return null;
        }

        double supportSum = 0.0;
        double mouthSupport = 0.0;
        for (int sampleIndex = 0; sampleIndex < CONNECTION_SAMPLES; sampleIndex++) {
            double t = sampleIndex / (CONNECTION_SAMPLES - 1.0);
            SkyIslandSubsurfacePosition position = quadratic(
                    intent.caveAnchor(),
                    control,
                    mouth,
                    t);
            if (position.depthFraction() < 0.0
                    || position.depthFraction() > 1.0
                    || semantic.interiority().sample(position.surfacePosition()) <= 0.0) {
                return null;
            }

            SkyIslandGeologySample sample = geology.sample(position);
            double local = localSupport(intent.side(), descriptor, sample);
            supportSum += local;
            if (sampleIndex == CONNECTION_SAMPLES - 1) {
                double exposure = semantic.exposure().sample(position.surfacePosition());
                mouthSupport = clamp01(
                        0.78 * local
                                + 0.22 * exposure);
            }
        }

        double meanSupport = supportSum / CONNECTION_SAMPLES;
        double geologicSupport = clamp01(0.76 * meanSupport + 0.24 * mouthSupport);
        double score = geologicSupport
                + 0.08 * intent.score()
                - 0.045 * (mouthOffsetFraction / MAX_MOUTH_OFFSET_FRACTION)
                - 0.025 * (bendFraction / MAX_BEND_FRACTION);

        return new Candidate(
                mouth,
                control,
                clamp01(geologicSupport),
                score,
                clamp01(mouthOffsetFraction),
                clamp01(bendFraction),
                tieBreak);
    }

    private static double localSupport(
            SkyIslandCaveExposureSide side,
            SkyIslandDescriptor descriptor,
            SkyIslandGeologySample sample) {
        if (!sample.owned()) {
            return 0.0;
        }
        return side == SkyIslandCaveExposureSide.UPPER_SURFACE
                ? clamp01(
                        0.42 * sample.fractureIntensity()
                                + 0.27 * sample.voidFormationPotential()
                                + 0.18 * sample.groundwaterPotential()
                                + 0.13 * (1.0 - sample.bulkCompetence()))
                : clamp01(
                        0.45 * sample.fractureIntensity()
                                + 0.28 * sample.voidFormationPotential()
                                + 0.18 * (1.0 - sample.bulkCompetence())
                                + 0.09 * descriptor.exposureTendency());
    }

    private static SourceScale sourceScale(
            SkyIslandCaveSystemGeometry system,
            SkyIslandCaveExposureIntent intent) {
        if (intent.sourcePrimitiveKind() == SkyIslandCaveVolumeSample.PrimitiveKind.CHAMBER) {
            SkyIslandCaveChamberGeometry chamber = system.chambers().stream()
                    .filter(candidate -> candidate.nodeId() == intent.sourcePrimitiveId())
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("exposure chamber source no longer exists"));
            return new SourceScale(chamber.horizontalRadius(), chamber.depthRadius());
        }

        SkyIslandCavePassageGeometry passage = system.passages().stream()
                .filter(candidate -> candidate.linkId() == intent.sourcePrimitiveId())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("exposure passage source no longer exists"));
        SkyIslandCavePassagePoint closest = passage.points().stream()
                .min(Comparator.comparingDouble(point ->
                        sourceBoundaryDistance(point, intent)))
                .orElseThrow();
        return new SourceScale(closest.horizontalRadius(), closest.depthRadius());
    }

    private static double sourceBoundaryDistance(
            SkyIslandCavePassagePoint point,
            SkyIslandCaveExposureIntent intent) {
        double boundaryDepth = intent.side() == SkyIslandCaveExposureSide.UPPER_SURFACE
                ? point.position().depthFraction() - point.depthRadius()
                : point.position().depthFraction() + point.depthRadius();
        boundaryDepth = clamp01(boundaryDepth);
        double dx = point.position().x() - intent.caveAnchor().x();
        double dz = point.position().z() - intent.caveAnchor().z();
        double dd = boundaryDepth - intent.caveAnchor().depthFraction();
        return dx * dx + dz * dz + dd * dd;
    }

    private static double normalizedMaxDeviation(
            List<SkyIslandCavePassagePoint> points,
            double radius) {
        SkyIslandSubsurfacePosition first = points.getFirst().position();
        SkyIslandSubsurfacePosition last = points.getLast().position();
        double maximum = 0.0;
        for (int index = 0; index < points.size(); index++) {
            double t = index / (points.size() - 1.0);
            SkyIslandSubsurfacePosition point = points.get(index).position();
            double linearX = lerp(first.x(), last.x(), t);
            double linearZ = lerp(first.z(), last.z(), t);
            double linearDepth = lerp(first.depthFraction(), last.depthFraction(), t);
            double deviation = Math.sqrt(
                    square((point.x() - linearX) / radius)
                            + square((point.z() - linearZ) / radius)
                            + square(point.depthFraction() - linearDepth));
            maximum = Math.max(maximum, deviation);
        }
        return clamp01(maximum);
    }

    private static SkyIslandSubsurfacePosition midpoint(
            SkyIslandSubsurfacePosition first,
            SkyIslandSubsurfacePosition second) {
        return new SkyIslandSubsurfacePosition(
                0.5 * (first.x() + second.x()),
                0.5 * (first.z() + second.z()),
                0.5 * (first.depthFraction() + second.depthFraction()));
    }

    private static SkyIslandSubsurfacePosition quadratic(
            SkyIslandSubsurfacePosition first,
            SkyIslandSubsurfacePosition control,
            SkyIslandSubsurfacePosition second,
            double t) {
        double oneMinus = 1.0 - t;
        double a = oneMinus * oneMinus;
        double b = 2.0 * oneMinus * t;
        double c = t * t;
        return new SkyIslandSubsurfacePosition(
                a * first.x() + b * control.x() + c * second.x(),
                a * first.z() + b * control.z() + c * second.z(),
                a * first.depthFraction() + b * control.depthFraction() + c * second.depthFraction());
    }

    private static double phase(long seed) {
        long bits = mix64(seed);
        return (bits >>> 11) * 0x1.0p-53 * 2.0 * Math.PI;
    }

    private static long mix64(long value) {
        long mixed = value;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        return mixed ^ (mixed >>> 31);
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        double t = clamp01((value - edge0) / (edge1 - edge0));
        return t * t * (3.0 - 2.0 * t);
    }

    private static double lerp(double first, double second, double t) {
        return first + (second - first) * t;
    }

    private static double square(double value) {
        return value * value;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double clamp01(double value) {
        return clamp(value, 0.0, 1.0);
    }

    private record SourceScale(
            double horizontalRadius,
            double depthRadius) {}

    private record Candidate(
            SkyIslandSubsurfacePosition mouth,
            SkyIslandSubsurfacePosition control,
            double geologicSupport,
            double score,
            double mouthOffsetFraction,
            double bendFraction,
            int tieBreak) {

        private static final Comparator<Candidate> ORDER = Comparator
                .comparingDouble(Candidate::score)
                .thenComparingDouble(candidate -> -candidate.mouthOffsetFraction())
                .thenComparingDouble(candidate -> -candidate.bendFraction())
                .thenComparingInt(candidate -> -candidate.tieBreak());
    }
}
