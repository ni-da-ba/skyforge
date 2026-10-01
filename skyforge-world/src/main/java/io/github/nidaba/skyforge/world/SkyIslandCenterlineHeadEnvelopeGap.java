package io.github.nidaba.skyforge.world;

/** D2 pointwise head-envelope gap evaluator used while ranking bounded centerline candidates. */
@FunctionalInterface
interface SkyIslandCenterlineHeadEnvelopeGap {
    double evaluate(
            SkyIslandLocalPosition position,
            double stationFraction,
            double tangentX,
            double tangentZ,
            double bankfullHalfWidth);
}
