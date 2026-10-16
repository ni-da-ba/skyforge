package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class SkyIslandJunctionEnergyLossTest {
    private static final double GRAVITY = 9.81;

    @Test
    void usesAverageConveyanceAndExpansionCoefficientWhenVelocityHeadFalls() {
        var upstream = rectangularSection(4.0, 100.0);
        var downstream = rectangularSection(8.0, 200.0);

        SkyIslandStandardStepSolver.JunctionLosses losses =
                SkyIslandStandardStepSolver.combiningJunctionLosses(
                        10.0,
                        upstream,
                        1.0,
                        8.0,
                        0.035,
                        downstream,
                        1.0,
                        8.0,
                        0.035,
                        0.3,
                        0.1);

        double expectedFriction = 10.0 * Math.pow((8.0 + 8.0) / (100.0 + 200.0), 2.0);
        double expectedTransition = 0.1 * Math.abs(
                Math.pow(8.0 / 4.0, 2.0) / (2.0 * GRAVITY)
                        - Math.pow(8.0 / 8.0, 2.0) / (2.0 * GRAVITY));
        assertEquals(expectedFriction, losses.frictionLossMetres(), 1.0e-12);
        assertEquals(expectedTransition, losses.transitionLossMetres(), 1.0e-12);
        assertEquals(expectedFriction + expectedTransition, losses.totalLossMetres(), 1.0e-12);
    }

    @Test
    void usesContractionCoefficientWhenVelocityHeadRisesDownstream() {
        var upstream = rectangularSection(4.0, 100.0);
        var downstream = rectangularSection(8.0, 200.0);

        SkyIslandStandardStepSolver.JunctionLosses losses =
                SkyIslandStandardStepSolver.combiningJunctionLosses(
                        10.0,
                        upstream,
                        1.0,
                        4.0,
                        0.035,
                        downstream,
                        1.0,
                        16.0,
                        0.035,
                        0.3,
                        0.1);

        double expectedTransition = 0.3 * Math.abs(
                Math.pow(16.0 / 8.0, 2.0) / (2.0 * GRAVITY)
                        - Math.pow(4.0 / 4.0, 2.0) / (2.0 * GRAVITY));
        assertEquals(expectedTransition, losses.transitionLossMetres(), 1.0e-12);
    }

    @Test
    void rejectsNegativeJunctionLossCoefficients() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SkyIslandStandardStepSolver.combiningJunctionLosses(
                        10.0,
                        rectangularSection(4.0, 100.0),
                        1.0,
                        4.0,
                        0.035,
                        rectangularSection(8.0, 200.0),
                        1.0,
                        8.0,
                        0.035,
                        -0.3,
                        0.1));
    }

    @Test
    void rejectsCombiningJunctionWithDownstreamFlowBelowItsBranchFlow() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SkyIslandStandardStepSolver.combiningJunctionLosses(
                        10.0,
                        rectangularSection(4.0, 100.0),
                        1.0,
                        9.0,
                        0.035,
                        rectangularSection(8.0, 200.0),
                        1.0,
                        8.0,
                        0.035,
                        0.3,
                        0.1));
    }

    @Test
    void solvesSubcriticalBranchDepthByClosingEnergyWithJunctionLosses() {
        var upstream = rectangularSection(4.0, 100.0);
        var downstream = rectangularSection(8.0, 200.0);
        double downstreamDepth = 2.0;
        double downstreamEnergyGrade = downstreamDepth
                + Math.pow(8.0 / (8.0 * downstreamDepth), 2.0) / (2.0 * GRAVITY);

        SkyIslandStandardStepSolver.JunctionStepResult result =
                SkyIslandStandardStepSolver.solveSubcriticalCombiningJunction(
                        0.0,
                        0.0,
                        downstreamDepth,
                        upstream,
                        downstream,
                        8.0,
                        8.0,
                        0.035,
                        0.035,
                        10.0,
                        0.3,
                        0.1);

        assertEquals(
                downstreamEnergyGrade + result.losses().totalLossMetres(),
                result.upstreamEnergyGradeMetres(),
                1.0e-8);
        assertEquals(
                result.upstreamEnergyGradeMetres(),
                result.upstreamDepthMetres()
                        + Math.pow(8.0 / (4.0 * result.upstreamDepthMetres()), 2.0)
                                / (2.0 * GRAVITY),
                1.0e-12);
    }

    @Test
    void failsClosedWhenTheDownstreamBoundaryIsNotSubcritical() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SkyIslandStandardStepSolver.solveSubcriticalCombiningJunction(
                        0.0,
                        0.0,
                        0.1,
                        rectangularSection(4.0, 100.0),
                        rectangularSection(8.0, 200.0),
                        4.0,
                        8.0,
                        0.035,
                        0.035,
                        10.0,
                        0.3,
                        0.1));
    }


    @Test
    void reproducesHecExample10UpperBranchJunctionEnergyStep() {
        String geometry = readFixture("JUNCTION.g01");
        FixtureSection upper = FixtureSection.read(geometry, "10.106");
        FixtureSection lower = FixtureSection.read(geometry, "10.091");
        double upstreamStage = feet(74.99);
        double downstreamStage = feet(75.04);
        double upstreamDepth = upstreamStage - upper.bedElevationMetres();
        double downstreamDepth = downstreamStage - lower.bedElevationMetres();
        double upstreamFlow = cubicFeetPerSecond(3000.0);
        double downstreamFlow = cubicFeetPerSecond(4100.0);
        double upstreamRoughness = 0.025;
        double downstreamRoughness = 0.025;

        SkyIslandStandardStepSolver.JunctionLosses losses =
                SkyIslandStandardStepSolver.combiningJunctionLosses(
                        feet(80.0),
                        upper,
                        upstreamDepth,
                        upstreamFlow,
                        upstreamRoughness,
                        lower,
                        downstreamDepth,
                        downstreamFlow,
                        downstreamRoughness,
                        0.1,
                        0.3);
        SkyIslandStandardStepSolver.JunctionStepResult solved =
                SkyIslandStandardStepSolver.solveSubcriticalCombiningJunction(
                        upper.bedElevationMetres(),
                        lower.bedElevationMetres(),
                        downstreamDepth,
                        upper,
                        lower,
                        upstreamFlow,
                        downstreamFlow,
                        upstreamRoughness,
                        downstreamRoughness,
                        feet(80.0),
                        0.1,
                        0.3);

        // HEC-RAS Example 10 Standard Table 2 rounds its published entries to 0.01 ft.
        assertEquals(feet(0.09), losses.frictionLossMetres(), feet(0.006));
        assertEquals(feet(0.06), losses.transitionLossMetres(), feet(0.006));
        assertEquals(upstreamDepth, solved.upstreamDepthMetres(), feet(0.015));
        assertEquals(feet(76.03), solved.upstreamEnergyGradeMetres(), feet(0.015));
    }

    private static String readFixture(String name) {
        String resource = "/hydrology/hec-ras-example-10/" + name;
        try (InputStream input = SkyIslandJunctionEnergyLossTest.class.getResourceAsStream(resource)) {
            assertNotNull(input, "missing benchmark fixture " + resource);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static double feet(double value) {
        return value * 0.3048;
    }

    private static double cubicFeetPerSecond(double value) {
        return value * 0.028316846592;
    }

    private static final class FixtureSection implements SkyIslandStandardStepSolver.CrossSection {
        private final double[] stations;
        private final double[] elevations;
        private final double[] roughnessStations;
        private final double[] roughnessValues;
        private final double bedElevation;

        private FixtureSection(
                double[] stations,
                double[] elevations,
                double[] roughnessStations,
                double[] roughnessValues) {
            this.stations = stations;
            this.elevations = elevations;
            this.roughnessStations = roughnessStations;
            this.roughnessValues = roughnessValues;
            double minimum = Double.POSITIVE_INFINITY;
            for (double elevation : elevations) {
                minimum = Math.min(minimum, elevation);
            }
            this.bedElevation = minimum;
        }

        static FixtureSection read(String geometry, String riverStation) {
            String stationPattern = Pattern.quote(riverStation);
            Matcher header = Pattern.compile(
                            "(?m)^Type RM Length L Ch R = 1\\s*,\\s*" + stationPattern + "\\s*,")
                    .matcher(geometry);
            if (!header.find()) {
                throw new IllegalArgumentException("HEC geometry section not found: " + riverStation);
            }
            int nextSection = geometry.indexOf("Type RM Length L Ch R =", header.end());
            int nextReach = geometry.indexOf("River Reach=", header.end());
            int end = nextSection < 0 ? nextReach : nextReach < 0 ? nextSection : Math.min(nextSection, nextReach);
            if (end < 0) {
                end = geometry.length();
            }
            String sectionText = geometry.substring(header.start(), end);
            Matcher points = Pattern.compile("#Sta/Elev=\\s*\\d+\\s*([\\s\\S]*?)#Mann=").matcher(sectionText);
            Matcher roughness = Pattern.compile("#Mann=\\s*\\d+[^\\r\\n]*\\r?\\n([^\\r\\n]+)")
                    .matcher(sectionText);
            if (!points.find() || !roughness.find()) {
                throw new IllegalArgumentException("incomplete HEC geometry section: " + riverStation);
            }
            double[] pointValues = numbers(points.group(1));
            double[] roughnessValues = numbers(roughness.group(1));
            if (pointValues.length < 4 || pointValues.length % 2 != 0 || roughnessValues.length % 3 != 0) {
                throw new IllegalArgumentException("malformed HEC geometry section: " + riverStation);
            }
            double[] stations = new double[pointValues.length / 2];
            double[] elevations = new double[stations.length];
            for (int i = 0; i < stations.length; i++) {
                stations[i] = feet(pointValues[i * 2]);
                elevations[i] = feet(pointValues[i * 2 + 1]);
            }
            double[] roughnessStations = new double[roughnessValues.length / 3];
            double[] roughnessValuesByZone = new double[roughnessStations.length];
            for (int i = 0; i < roughnessStations.length; i++) {
                roughnessStations[i] = feet(roughnessValues[i * 3]);
                roughnessValuesByZone[i] = roughnessValues[i * 3 + 1];
            }
            return new FixtureSection(stations, elevations, roughnessStations, roughnessValuesByZone);
        }

        double bedElevationMetres() {
            return bedElevation;
        }

        @Override
        public double area(double depthMetres) {
            return geometryAt(depthMetres).area;
        }

        @Override
        public double wettedPerimeter(double depthMetres) {
            return geometryAt(depthMetres).wettedPerimeter;
        }

        @Override
        public double topWidth(double depthMetres) {
            return geometryAt(depthMetres).topWidth;
        }

        @Override
        public double conveyance(double depthMetres, double ignoredUniformRoughness) {
            double stage = bedElevation + depthMetres;
            double[] subsectionAreas = new double[roughnessValues.length];
            double[] subsectionPerimeters = new double[roughnessValues.length];
            for (int i = 0; i < stations.length - 1; i++) {
                Segment segment = segmentAt(i, stage);
                int zone = roughnessZone((stations[i] + stations[i + 1]) * 0.5);
                subsectionAreas[zone] += segment.area;
                subsectionPerimeters[zone] += segment.wettedPerimeter;
            }
            double total = 0.0;
            for (int i = 0; i < roughnessValues.length; i++) {
                if (subsectionAreas[i] > 0.0 && subsectionPerimeters[i] > 0.0) {
                    double radius = subsectionAreas[i] / subsectionPerimeters[i];
                    total += subsectionAreas[i] * Math.pow(radius, 2.0 / 3.0) / roughnessValues[i];
                }
            }
            return total;
        }

        private Geometry geometryAt(double depthMetres) {
            double stage = bedElevation + depthMetres;
            double area = 0.0;
            double perimeter = 0.0;
            double width = 0.0;
            for (int i = 0; i < stations.length - 1; i++) {
                Segment segment = segmentAt(i, stage);
                area += segment.area;
                perimeter += segment.wettedPerimeter;
                width += segment.topWidth;
            }
            return new Geometry(area, perimeter, width);
        }

        private Segment segmentAt(int index, double stage) {
            double x1 = stations[index];
            double x2 = stations[index + 1];
            double z1 = elevations[index];
            double z2 = elevations[index + 1];
            double h1 = stage - z1;
            double h2 = stage - z2;
            if (h1 <= 0.0 && h2 <= 0.0) {
                return new Segment(0.0, 0.0, 0.0);
            }
            double dx = x2 - x1;
            double dz = z2 - z1;
            if (h1 >= 0.0 && h2 >= 0.0) {
                return new Segment(
                        dx * (h1 + h2) * 0.5,
                        Math.hypot(dx, dz),
                        dx);
            }
            double wetDepth = Math.max(h1, h2);
            double wetFraction = wetDepth / Math.abs(h1 - h2);
            double wetLength = dx * wetFraction;
            return new Segment(
                    wetDepth * wetLength * 0.5,
                    Math.hypot(wetLength, dz * wetFraction),
                    wetLength);
        }

        private int roughnessZone(double station) {
            int zone = 0;
            for (int i = 1; i < roughnessStations.length; i++) {
                if (station >= roughnessStations[i]) {
                    zone = i;
                }
            }
            return zone;
        }

        private static double[] numbers(String text) {
            List<Double> parsed = new ArrayList<>();
            for (String token : text.trim().split("\\s+")) {
                if (!token.isEmpty()) {
                    parsed.add(Double.parseDouble(token));
                }
            }
            double[] values = new double[parsed.size()];
            for (int i = 0; i < values.length; i++) {
                values[i] = parsed.get(i);
            }
            return values;
        }

        private record Geometry(double area, double wettedPerimeter, double topWidth) {}

        private record Segment(double area, double wettedPerimeter, double topWidth) {}
    }

    private static SkyIslandStandardStepSolver.CrossSection rectangularSection(
            double widthMetres, double conveyance) {
        return new SkyIslandStandardStepSolver.CrossSection() {
            @Override
            public double area(double depthMetres) {
                return widthMetres * depthMetres;
            }

            @Override
            public double wettedPerimeter(double depthMetres) {
                return widthMetres + 2.0 * depthMetres;
            }

            @Override
            public double topWidth(double depthMetres) {
                return widthMetres;
            }

            @Override
            public double conveyance(double depthMetres, double manningRoughness) {
                return conveyance;
            }
        };
    }
}
