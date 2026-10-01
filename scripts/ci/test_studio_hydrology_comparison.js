"use strict";

const assert = require("node:assert/strict");

globalThis.window = {};
require("../orchestrator/studio/scene.js");

const compare = window.SkyforgeStudioScene.compareHydrologyLayers;
const WORLD_FRAME = {
  centerX: 100,
  centerZ: 200,
  suspensionElevation: 50,
  nominalRadius: 64,
};
const GRID = {
  minimumX: 0,
  minimumZ: 0,
  spacingX: 10,
  spacingZ: 10,
  xSamples: 2,
  zSamples: 1,
  causeStride: 1,
  causeSampleCount: 2,
};
const FIELDS = [
  "runoffPotential",
  "retentionPotential",
  "drainagePotential",
  "outflowPotential",
];

function sample(index, values = {}) {
  const local = [index * 10, 0];
  const world = [100 + local[0], 50, 200 + local[1]];
  const position = [local[0], 8, local[1]];
  return {
    overlayKind: "HYDROLOGY_CAUSE_SAMPLE",
    grid: [index, 0],
    localPosition: local,
    worldPosition: world,
    position,
    displayPosition: [position[0], position[1] + 0.5, position[2]],
    runoffPotential: 0,
    retentionPotential: 0,
    drainagePotential: 0,
    outflowPotential: 0,
    ...values,
  };
}

function fieldSample(index, overrides = {}) {
  const worldX = 100 + index * 10;
  const worldZ = 200;
  const targetUpperY = 8;
  const waterSurfaceY = null;
  return {
    overlayKind: "HYDROLOGY_FIELD_SAMPLE",
    grid: [index, 0],
    position: [worldX, targetUpperY, worldZ],
    waterPosition: null,
    originalUpperY: 10,
    targetUpperY,
    terrainDeltaWorld: -2,
    wet: false,
    waterSurfaceY,
    waterDepthWorld: 0,
    ...overrides,
  };
}

function layer(causeSamples, overrides = {}) {
  return {
    sceneKind: "HYDROLOGY_SEMANTIC_LAYER",
    terrainSemanticSha256: "a".repeat(64),
    binding: {
      associationToken: "sfassoc:v1:comparison-fixture",
      worldFrame: { ...WORLD_FRAME },
    },
    gridBinding: { ...GRID },
    fieldSamples: [],
    causeSamples,
    source: { reviewAuthority: true },
    ...overrides,
  };
}

function expectFailure(label, reference, candidate, expectedMessage) {
  assert.throws(
    () => compare(reference, candidate),
    (error) => error instanceof Error && error.message === expectedMessage,
    label
  );
}

const reference = layer([
  sample(0, {
    runoffPotential: 0.25,
    retentionPotential: 0.5,
    drainagePotential: 0.5,
    outflowPotential: 0.75,
  }),
  sample(1, {
    runoffPotential: 0.75,
    retentionPotential: 0.125,
    drainagePotential: 0.25,
    outflowPotential: 0.375,
  }),
]);
const candidate = layer([
  sample(1, {
    runoffPotential: 0.25,
    retentionPotential: 0.125,
    drainagePotential: 0.25,
    outflowPotential: 0.375,
  }),
  sample(0, {
    runoffPotential: 0.75,
    retentionPotential: 0.25,
    drainagePotential: 0.5,
    outflowPotential: 0.75,
  }),
], { source: { reviewAuthority: false } });

const comparison = compare(reference, candidate);
assert.equal(comparison.sceneKind, "HYDROLOGY_SEMANTIC_COMPARISON");
assert.equal(comparison.reviewAuthority, false);
assert.deepEqual(comparison.samples.map((entry) => entry.grid), [[0, 0], [1, 0]]);
assert.deepEqual({ ...comparison.samples[0].deltas }, {
  runoffPotential: 0.5,
  retentionPotential: -0.25,
  drainagePotential: 0,
  outflowPotential: 0,
});
assert.deepEqual({ ...comparison.samples[1].deltas }, {
  runoffPotential: -0.5,
  retentionPotential: 0,
  drainagePotential: 0,
  outflowPotential: 0,
});
assert.ok(FIELDS.every((field) => Number.isFinite(comparison.samples[0].deltas[field])));

const fieldReference = layer(reference.causeSamples, {
  fieldSamples: [
    fieldSample(0, {
      position: [100, 8, 200],
      waterPosition: [100, 7.5, 200],
      wet: true,
      waterSurfaceY: 7.5,
      waterDepthWorld: 0.5,
    }),
    fieldSample(1),
  ],
});
const fieldCandidate = layer(candidate.causeSamples, {
  fieldSamples: [
    fieldSample(0, {
      position: [100, 6, 200],
      targetUpperY: 6,
      waterPosition: [100, 5, 200],
      wet: true,
      waterSurfaceY: 5,
      waterDepthWorld: 1.25,
    }),
    fieldSample(1, {
      position: [110, 8, 200],
      waterPosition: [110, 7, 200],
      wet: true,
      waterSurfaceY: 7,
      waterDepthWorld: 1,
    }),
  ],
});
const fieldComparison = compare(fieldReference, fieldCandidate).fieldSamples;
assert.deepEqual(fieldComparison.map((entry) => entry.coverage), ["shared", "shared"]);
assert.equal(fieldComparison[0].surfaceDeltaY, -2);
assert.equal(fieldComparison[0].waterSurfaceDeltaY, -2.5);
assert.equal(fieldComparison[0].waterDepthDelta, 0.75);
assert.equal(fieldComparison[0].referenceWet, true);
assert.equal(fieldComparison[0].candidateWet, true);
assert.equal(fieldComparison[0].wetTransition, null);
assert.equal(fieldComparison[1].surfaceDeltaY, 0);
assert.equal(fieldComparison[1].waterSurfaceDeltaY, null);
assert.equal(fieldComparison[1].waterDepthDelta, 1);
assert.equal(fieldComparison[1].referenceWet, false);
assert.equal(fieldComparison[1].candidateWet, true);
assert.equal(fieldComparison[1].wetTransition, "WET_ADDED");

const referenceOnly = compare(
  layer(reference.causeSamples, { fieldSamples: [fieldSample(0)] }),
  layer(candidate.causeSamples, { fieldSamples: [] })
).fieldSamples;
assert.equal(referenceOnly.length, 1);
assert.equal(referenceOnly[0].coverage, "reference-only");
assert.equal(referenceOnly[0].reference.position[0], 100);
assert.equal(referenceOnly[0].candidate, null);

const candidateOnly = compare(
  layer(reference.causeSamples, { fieldSamples: [] }),
  layer(candidate.causeSamples, { fieldSamples: [fieldSample(1)] })
).fieldSamples;
assert.equal(candidateOnly.length, 1);
assert.equal(candidateOnly[0].coverage, "candidate-only");
assert.equal(candidateOnly[0].candidate.position[0], 110);
assert.equal(candidateOnly[0].reference, null);

const wetRemoval = compare(
  layer(reference.causeSamples, {
    fieldSamples: [fieldSample(0, {
      wet: true,
      waterSurfaceY: 7,
      waterPosition: [100, 7, 200],
      waterDepthWorld: 1,
    })],
  }),
  layer(candidate.causeSamples, { fieldSamples: [fieldSample(0)] })
).fieldSamples[0];
assert.equal(wetRemoval.wetTransition, "WET_REMOVED");
assert.equal(wetRemoval.waterSurfaceDeltaY, null);
assert.equal(wetRemoval.waterDepthDelta, -1);

expectFailure(
  "duplicate reference field sample",
  layer(reference.causeSamples, { fieldSamples: [fieldSample(0), fieldSample(0)] }),
  layer(candidate.causeSamples, { fieldSamples: [] }),
  "reference hydrology comparison contains duplicate field samples"
);
expectFailure(
  "duplicate candidate field sample",
  layer(reference.causeSamples, { fieldSamples: [] }),
  layer(candidate.causeSamples, { fieldSamples: [fieldSample(0), fieldSample(0)] }),
  "candidate hydrology comparison contains duplicate field samples"
);
expectFailure(
  "field coordinate mismatch",
  layer(reference.causeSamples, { fieldSamples: [fieldSample(0)] }),
  layer(candidate.causeSamples, {
    fieldSamples: [fieldSample(0, { position: [999, 8, 200] })],
  }),
  "hydrology comparison field coordinates do not match"
);
expectFailure(
  "non-finite field sample",
  layer(reference.causeSamples, { fieldSamples: [fieldSample(0)] }),
  layer(candidate.causeSamples, {
    fieldSamples: [fieldSample(0, { targetUpperY: Number.NaN })],
  }),
  "candidate hydrology comparison contains a non-finite field sample"
);

const unboundReference = layer(reference.causeSamples, {
  source: { reviewAuthority: false },
});
const registeredCandidate = layer(candidate.causeSamples, {
  source: { reviewAuthority: true },
});
assert.equal(
  compare(unboundReference, registeredCandidate).reviewAuthority,
  false,
  "an unbound reference cannot grant review authority to the comparison"
);

expectFailure(
  "terrain digest mismatch",
  reference,
  layer(candidate.causeSamples, { terrainSemanticSha256: "b".repeat(64) }),
  "hydrology comparison requires the same exact terrain semantic SHA"
);
expectFailure(
  "association mismatch",
  reference,
  layer(candidate.causeSamples, {
    binding: { ...candidate.binding, associationToken: "sfassoc:v1:other" },
  }),
  "hydrology comparison requires the same AUTH-0046 association"
);
expectFailure(
  "world-frame mismatch",
  reference,
  layer(candidate.causeSamples, {
    binding: {
      ...candidate.binding,
      worldFrame: { ...WORLD_FRAME, centerX: WORLD_FRAME.centerX + 1 },
    },
  }),
  "hydrology comparison world frames do not match"
);
expectFailure(
  "grid-binding mismatch",
  reference,
  layer(candidate.causeSamples, {
    gridBinding: { ...GRID, spacingX: GRID.spacingX * 2 },
  }),
  "hydrology comparison cause-grid bindings do not match"
);
expectFailure(
  "missing sample",
  reference,
  layer([sample(0), sample(2)]),
  "candidate hydrology comparison is missing cause sample 1:0"
);
expectFailure(
  "short candidate",
  reference,
  layer([sample(0)]),
  "hydrology comparison cause sample counts do not match"
);
expectFailure(
  "duplicate reference sample",
  layer([sample(0), sample(0)]),
  candidate,
  "reference hydrology comparison contains duplicate cause samples"
);
expectFailure(
  "duplicate candidate sample",
  reference,
  layer([sample(0), sample(0)]),
  "candidate hydrology comparison contains duplicate cause samples"
);
expectFailure(
  "local-coordinate mismatch",
  reference,
  layer([sample(0, { localPosition: [999, 0] }), sample(1)]),
  "hydrology comparison cause coordinates do not match"
);
expectFailure(
  "world-coordinate mismatch",
  reference,
  layer([sample(0, { worldPosition: [999, 50, 200] }), sample(1)]),
  "hydrology comparison cause coordinates do not match"
);
expectFailure(
  "non-finite delta",
  reference,
  layer([sample(0, { runoffPotential: Number.POSITIVE_INFINITY }), sample(1)]),
  "hydrology comparison produced a non-finite cause delta"
);

console.log("PASS Studio hydrology candidate comparison behavior");
