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

function layer(causeSamples, overrides = {}) {
  return {
    sceneKind: "HYDROLOGY_SEMANTIC_LAYER",
    terrainSemanticSha256: "a".repeat(64),
    binding: {
      associationToken: "sfassoc:v1:comparison-fixture",
      worldFrame: { ...WORLD_FRAME },
    },
    gridBinding: { ...GRID },
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
assert.deepEqual(comparison.samples[0].deltas, {
  runoffPotential: 0.5,
  retentionPotential: -0.25,
  drainagePotential: 0,
  outflowPotential: 0,
});
assert.deepEqual(comparison.samples[1].deltas, {
  runoffPotential: -0.5,
  retentionPotential: 0,
  drainagePotential: 0,
  outflowPotential: 0,
});
assert.ok(FIELDS.every((field) => Number.isFinite(comparison.samples[0].deltas[field])));

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
