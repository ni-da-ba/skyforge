"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const appPath = path.join(__dirname, "..", "orchestrator", "studio", "app.js");
const app = fs.readFileSync(appPath, "utf8");
const startMarker = "  // BEGIN STUDIO SELECTED SAMPLE TRACE DOCUMENT CONTRACT";
const endMarker = "  // END STUDIO SELECTED SAMPLE TRACE DOCUMENT CONTRACT";
const start = app.indexOf(startMarker);
const end = app.indexOf(endMarker);
assert.notEqual(start, -1, "selected sample trace contract start marker must exist");
assert.notEqual(end, -1, "selected sample trace contract end marker must exist");
const contract = app.slice(start, end + endMarker.length);
const window = {};
vm.runInNewContext(contract, { window });
const traceContract = window.SkyforgeStudioSampleTrace;
assert.ok(traceContract, "selected sample trace contract must expose pure helpers");

const localScene = {
  sceneKind: "TERRAIN_SEMANTIC_VOLUME",
  source: {
    binding: "UNBOUND_LOCAL",
    artifactId: null,
    artifactTitle: "terrain specimen.json",
    sourceSha: null,
    reviewAuthority: false,
    artifactKind: "SKYFORGE_TERRAIN_SEMANTIC_VOLUME",
    artifactDigest: "terrain-digest",
  },
  coordinateSystem: { id: "WORLD_XYZ" },
  provider: { label: "Example semantic provider", version: "1.0" },
  samples: Array.from({ length: 20000 }, (_, index) => ({ position: [index, 0, 0] })),
};
const overlay = {
  source: {
    binding: "UNBOUND_LOCAL",
    artifactTitle: "hydrology specimen.json",
    reviewAuthority: false,
    artifactKind: "SKYFORGE_BOUND_HYDROLOGY_SEMANTIC_LAYER",
    artifactDigest: "hydrology-digest",
  },
  terrainSemanticSha256: "terrain-digest",
  binding: { associationToken: "association-example" },
  gridBinding: { xSamples: 3, zSamples: 3 },
  samples: Array.from({ length: 500 }, (_, index) => ({ position: [index, 0, 0] })),
};
const selectedSample = {
  position: [12.5, 4, -8.5],
  grid: [2, 1],
  semanticName: "SURFACE_MANTLE",
  provenance: { authored_field: "terrain.response", source_revision: "r7" },
};
const localTrace = traceContract.create({
  scene: localScene,
  overlay,
  sample: selectedSample,
});
assert.equal(localTrace.document_type, "SKYFORGE_STUDIO_SELECTED_SAMPLE_TRACE");
assert.equal(localTrace.format_version, 1);
assert.equal(localTrace.review_authority, false);
assert.equal(localTrace.sources.scene.binding, "UNBOUND_LOCAL");
assert.equal(localTrace.sources.overlay.artifactDigest, "hydrology-digest");
assert.equal(localTrace.overlay.binding.associationToken, "association-example");
assert.deepEqual(
  JSON.parse(JSON.stringify(localTrace.selected_sample)),
  JSON.parse(JSON.stringify(selectedSample))
);
assert.equal(Object.hasOwn(localTrace, "samples"), false);
assert.equal(Object.hasOwn(localTrace, "source_artifact"), false);
assert.equal(Object.hasOwn(localTrace.overlay, "samples"), false);
assert.equal(
  traceContract.filename(localScene, selectedSample),
  "terrain-specimen-json-sample-12p5-4p0-m8p5.skyforge-trace.json"
);
assert.equal(
  traceContract.filename(localScene, selectedSample),
  traceContract.filename(localScene, selectedSample),
  "filenames are deterministic"
);

const registeredScene = {
  sceneKind: "ATMOSPHERE_VECTOR_FIELD",
  source: {
    binding: "AUTH-BOUND",
    artifactId: "artifact-17",
    reviewAuthority: true,
    artifactKind: "SKYFORGE_ATMOSPHERE_PROBE_VOLUME",
    artifactDigest: "atmosphere-digest",
  },
  coordinateSystem: { id: "WORLD_XYZ" },
  provider: { label: "External atmosphere provider", version: "1.0" },
};
const atmosphereSample = {
  position: [2, 8, -4],
  mean: [1, 0, 0],
  gust: [0, 0.5, 0],
  effective: [1, 0.5, 0],
  verticalAir: 0.5,
  turbulence: 0.12,
  shear: 0.01,
  confidence: 0.9,
  trusted: false,
  sourceLevel: "provider-declared",
  authority: "provider-declared",
};
const atmosphereTrace = traceContract.create({
  scene: registeredScene,
  sample: atmosphereSample,
});
assert.equal(atmosphereTrace.scene_kind, "ATMOSPHERE_VECTOR_FIELD");
assert.equal(atmosphereTrace.review_authority, true);
assert.equal(atmosphereTrace.provider.label, "External atmosphere provider");
assert.deepEqual(
  JSON.parse(JSON.stringify(atmosphereTrace.selected_sample)),
  JSON.parse(JSON.stringify(atmosphereSample))
);

const comparison = {
  associationToken: "association-17",
  referenceSource: {
    binding: "AUTH-BOUND",
    artifactId: "reference-17",
    reviewAuthority: true,
    artifactKind: "SKYFORGE_BOUND_HYDROLOGY_SEMANTIC_LAYER",
    artifactDigest: "reference-digest",
  },
  candidateSource: {
    binding: "AUTH-BOUND",
    artifactId: "candidate-17",
    reviewAuthority: true,
    artifactKind: "SKYFORGE_BOUND_HYDROLOGY_SEMANTIC_LAYER",
    artifactDigest: "candidate-digest",
  },
  reviewAuthority: true,
  field: "runoffPotential",
};
const comparisonSample = {
  position: [4, 2, 9],
  overlayKind: "HYDROLOGY_COMPARISON_SAMPLE",
  reference: { runoffPotential: 0.3 },
  candidate: { runoffPotential: 0.5 },
  deltas: { runoffPotential: 0.2 },
};
const comparisonTrace = traceContract.create({
  scene: registeredScene,
  comparison,
  sample: comparisonSample,
});
assert.equal(comparisonTrace.review_authority, true);
assert.equal(comparisonTrace.sources.reference.artifactId, "reference-17");
assert.equal(comparisonTrace.sources.candidate.artifactId, "candidate-17");
assert.equal(comparisonTrace.comparison.field, "runoffPotential");
assert.deepEqual(
  JSON.parse(JSON.stringify(comparisonTrace.selected_sample.deltas)),
  { runoffPotential: 0.2 }
);

assert.throws(() => traceContract.create({ sample: selectedSample }), /requires a scene/);
assert.throws(() => traceContract.create({ scene: localScene, sample: [] }), /selected sample object/);
assert.throws(() => traceContract.filename(null, selectedSample), /requires scene and sample/);

console.log("PASS Studio selected sample trace document behavior");
