"use strict";

const assert = require("node:assert/strict");
global.window = {};
require("../orchestrator/studio/scene.js");
const sceneApi = global.window.SkyforgeStudioScene;
delete global.window;
const comparisonApi = require("../orchestrator/studio/terrain-comparison.js");
const reportApi = require("../orchestrator/studio/terrain-comparison-report.js");
const readerApi = require("../orchestrator/studio/terrain-comparison-report-reader.js");

function makeArtifact(names, cells, version) {
  const ordinals = new Map(names.map((name, ordinal) => [name, ordinal]));
  const semantics = Uint8Array.from(cells.map(name => ordinals.get(name)));
  return {
    schema_version: 1,
    artifact_kind: "SKYFORGE_TERRAIN_SEMANTIC_VOLUME",
    semantic_sha256: "recorded-" + version,
    skyforge_version: "test",
    grid: {
      minimum_x: 10, minimum_y: 0, minimum_z: -4,
      spacing_x: 2, spacing_y: 1, spacing_z: 2,
      x_samples: 2, y_samples: 2, z_samples: 2, sample_count: semantics.length,
    },
    semantic_legend: names.map((name, ordinal) => ({ ordinal, name, solid: name !== "AIR" })),
    encoding: {
      kind: "BASE64_UINT8_ORDINAL",
      linear_index: "x + x_samples * (z + z_samples * y)",
      sample_count: semantics.length,
    },
    semantics_base64: btoa(String.fromCharCode(...semantics)),
  };
}

function adapt(artifact, title) {
  return sceneApi.adaptArtifact(artifact, {
    binding: "UNBOUND_LOCAL", artifactTitle: title, reviewAuthority: false,
  });
}

const grid = { xSamples: 2, ySamples: 2, zSamples: 2 };
const reference = adapt(makeArtifact(["AIR", "LAND", "RIDGE"], [
  "AIR", "LAND", "AIR", "AIR", "AIR", "RIDGE", "AIR", "RIDGE",
], 1), "reference.json");
const candidate = adapt(makeArtifact(["AIR", "RIDGE", "LAND"], [
  "AIR", "LAND", "RIDGE", "AIR", "AIR", "AIR", "AIR", "RIDGE",
], 2), "candidate.json");
const comparison = comparisonApi.compare(
  { scene: reference, title: "reference.json" },
  { scene: candidate, title: "candidate.json" }
);

const report = reportApi.create(comparison);
const bytes = reportApi.stringify(report);
assert.equal(bytes, reportApi.stringify(reportApi.create(comparison)), "equivalent comparisons serialize deterministically");
assert.equal(report.document_type, readerApi.documentType);
assert.equal(report.diagnostic_only, true);
assert.equal(report.human_review_evidence, false);
assert.equal(report.review_authority, false);
assert.equal(report.provenance_verified, false);
assert.equal(report.sources.reference.artifactDigest, reference.source.artifactDigest);
assert.equal(report.changed_cells.length, comparison.changedCellCount);
assert.equal(report.columns.length, grid.xSamples * grid.zSamples);
assert.match(reportApi.filename(report), /^skyforge-terrain-comparison-reference-json-to-candidate-json\.json$/);

const parsed = readerApi.parse(bytes);
assert.equal(parsed.sourceProvenanceVerified, false);
assert.equal(parsed.comparison.reviewAuthority, false);
assert.equal(parsed.comparison.changedCellCount, 2);
assert.equal(parsed.comparison.pageChangedCells(0, 1).items.length, 1);
assert.deepEqual(parsed.comparison.pageChangedCells(1, 1).items[0].gridIndex, [0, 0, 1]);
assert.equal(parsed.comparison.columns.find(column => column.x === 1 && column.z === 0).heightDelta, -1);

const claimsAuthority = JSON.parse(bytes);
claimsAuthority.human_review_evidence = true;
assert.throws(() => readerApi.parse(JSON.stringify(claimsAuthority)), /cannot claim verified authority or review evidence/);

const boundSource = JSON.parse(bytes);
boundSource.sources.reference.reviewAuthority = true;
assert.throws(() => readerApi.parse(JSON.stringify(boundSource)), /source must be unbound local diagnostic provenance/);

const inconsistent = JSON.parse(bytes);
inconsistent.changed_cells.pop();
assert.throws(() => readerApi.parse(JSON.stringify(inconsistent)), /summary does not match/);

assert.throws(() => readerApi.parse(bytes, readerApi.maximumFileBytes + 1), /32 MB or smaller/);
assert.throws(() => reportApi.create({ ...comparison, changedCellCount: reportApi.maximumChangedCells + 1 }),
  /too many changed cells/);

console.log("PASS Studio terrain comparison report contract");
