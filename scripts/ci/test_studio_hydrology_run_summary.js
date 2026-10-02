"use strict";

const assert = require("node:assert/strict");
const { summarize } = require("../orchestrator/studio/hydrology-run-summary.js");

const scene = {
  sceneKind: "TERRAIN_SEMANTIC_VOLUME",
  terrain: { grid: { xSamples: 8, ySamples: 5, zSamples: 6 } },
};
const overlay = {
  sceneKind: "HYDROLOGY_SEMANTIC_LAYER",
  gridBinding: { xSamples: 8, zSamples: 6, causeStride: 2, causeSampleCount: 12 },
  causeSamples: [
    { runoffPotential: 0.1, retentionPotential: 0.8, drainagePotential: 0.2, outflowPotential: 0.0 },
    { runoffPotential: 0.9, retentionPotential: 0.4, drainagePotential: 0.7, outflowPotential: 1.0 },
    { runoffPotential: 0.5, retentionPotential: 0.6, drainagePotential: 0.3, outflowPotential: 0.25 },
  ],
  fieldSamples: [
    { wet: true, waterSurfaceY: 12, terrainDeltaWorld: -2.5 },
    { wet: false, waterSurfaceY: null, terrainDeltaWorld: 0 },
    { wet: true, waterSurfaceY: 14.5, terrainDeltaWorld: 3 },
  ],
  reaches: [{ points: [{}, {}, {}] }, { points: [{}, {}] }],
};

const summary = summarize(scene, overlay);
assert.deepEqual(summary.terrainGrid, { xSamples: 8, ySamples: 5, zSamples: 6 });
assert.deepEqual(summary.causeGrid, { xSamples: 8, zSamples: 6, stride: 2, sampleCount: 12 });
assert.equal(summary.causeSampleCount, 3);
assert.equal(summary.terrainResponseSampleCount, 3);
assert.equal(summary.wetResponseSampleCount, 2);
assert.equal(summary.waterSurfaceSampleCount, 2);
assert.equal(summary.reachCount, 2);
assert.equal(summary.reachPointCount, 5);
assert.deepEqual(summary.potentialRanges.runoffPotential, { minimum: 0.1, maximum: 0.9 });
assert.deepEqual(summary.potentialRanges.outflowPotential, { minimum: 0, maximum: 1 });
assert.deepEqual(summary.terrainDeltaWorldRange, { minimum: -2.5, maximum: 3 });
assert.equal(summarize(scene, null), null, "the summary stays hidden without an overlay");
assert.equal(summarize({ sceneKind: "ATMOSPHERE_SEMANTIC_FIELD" }, overlay), null);
assert.equal(
  summarize(scene, { ...overlay, fieldSamples: [], causeSamples: [], reaches: [] })
    .terrainDeltaWorldRange,
  null,
  "empty observed values have no fabricated range",
);

console.log("PASS Studio hydrology run summary");
