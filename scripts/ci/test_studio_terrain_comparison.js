"use strict";

const assert = require("node:assert/strict");
const sceneApi = require("../orchestrator/studio/scene.js");
const comparisonApi = require("../orchestrator/studio/terrain-comparison.js");

function makeArtifact(grid, names, cells, version = 1) {
  const ordinals = new Map(names.map((name, ordinal) => [name, ordinal]));
  const semantics = Uint8Array.from(cells.map(name => ordinals.get(name)));
  const binary = String.fromCharCode(...semantics);
  return {
    schema_version: 1,
    artifact_kind: "SKYFORGE_TERRAIN_SEMANTIC_VOLUME",
    semantic_sha256: "recorded-" + version,
    skyforge_version: "test",
    grid: {
      minimum_x: grid.minimumX ?? 10,
      minimum_y: grid.minimumY ?? 0,
      minimum_z: grid.minimumZ ?? -4,
      spacing_x: grid.spacingX ?? 2,
      spacing_y: grid.spacingY ?? 1,
      spacing_z: grid.spacingZ ?? 2,
      x_samples: grid.xSamples ?? 2,
      y_samples: grid.ySamples ?? 2,
      z_samples: grid.zSamples ?? 2,
      sample_count: semantics.length,
    },
    semantic_legend: names.map((name, ordinal) => ({ ordinal, name, solid: name !== "AIR" })),
    encoding: {
      kind: "BASE64_UINT8_ORDINAL",
      linear_index: "x + x_samples * (z + z_samples * y)",
      sample_count: semantics.length,
    },
    semantics_base64: btoa(binary),
  };
}

function adapt(artifact, title, reviewAuthority = false) {
  return sceneApi.adaptArtifact(artifact, {
    binding: "UNBOUND_LOCAL",
    artifactTitle: title,
    reviewAuthority,
  });
}

const grid = { xSamples: 2, ySamples: 2, zSamples: 2 };
const reference = adapt(makeArtifact(grid, ["AIR", "LAND", "RIDGE"], [
  "AIR", "LAND", "AIR", "AIR",
  "AIR", "RIDGE", "AIR", "RIDGE",
]), "reference.json");
const candidate = adapt(makeArtifact(grid, ["RIDGE", "AIR", "LAND"], [
  "AIR", "LAND", "RIDGE", "AIR",
  "AIR", "AIR", "AIR", "RIDGE",
], 2), "candidate.json");

const comparison = comparisonApi.compare(
  { scene: reference, title: "reference.json" },
  { scene: candidate, title: "candidate.json" }
);
assert.equal(comparison.changedCellCount, 2);
assert.equal(comparison.changedPercent, 25);
assert.equal(comparison.reviewAuthority, false);
assert.equal(comparison.referenceSource.binding, "UNBOUND_LOCAL");
assert.equal(comparison.candidateSource.reviewAuthority, false);
assert.deepEqual(comparison.semanticTransitions.map(item =>
  [item.referenceName, item.candidateName, item.count]), [
  ["AIR", "RIDGE", 1],
  ["RIDGE", "AIR", 1],
]);
assert.equal(comparison.semanticCounts.find(item => item.name === "RIDGE").reference, 2);
assert.equal(comparison.semanticCounts.find(item => item.name === "RIDGE").candidate, 1);
assert.equal(comparison.changedColumnCount, 2);
assert.equal(comparison.maximumAbsSurfaceDelta, 1);
assert.equal(comparison.columns.find(item => item.x === 1 && item.z === 0).heightDelta, -1);
assert.equal(comparison.columns.find(item => item.x === 0 && item.z === 1).status, "CANDIDATE_ONLY");
const page = comparison.pageChangedCells(0, 1);
assert.equal(page.total, 2);
assert.equal(page.items.length, 1);
assert.deepEqual(page.items[0].gridIndex, [0, 0, 1]);
assert.equal(page.items[0].position[0], 10);
assert.equal(comparison.pageChangedCells(1, 1).items.length, 1);
assert.throws(() => comparison.pageChangedCells(-1, 10), /page is outside/);
assert.throws(() => comparison.pageChangedCells(0, 201), /page is outside/);

const reorderedReference = adapt(makeArtifact(grid, ["AIR", "LAND", "RIDGE"], [
  "AIR", "LAND", "AIR", "AIR", "AIR", "RIDGE", "AIR", "RIDGE",
]), "reordered-reference.json");
const sameByName = comparisonApi.compare(
  { scene: reorderedReference },
  { scene: candidate }
);
assert.equal(sameByName.changedCellCount, 2,
  "name-based comparison still detects the two actual transitions when ordinals differ");

const sameCellsReordered = adapt(makeArtifact(grid, ["RIDGE", "AIR", "LAND"], [
  "AIR", "LAND", "AIR", "AIR", "AIR", "RIDGE", "AIR", "RIDGE",
]), "same-cells-reordered.json");
const ordinalOnly = comparisonApi.compare(
  { scene: reference },
  { scene: sameCellsReordered }
);
assert.equal(ordinalOnly.changedCellCount, 0,
  "a legend reorder alone must not appear as a semantic change");

for (const mismatch of [
  makeArtifact({ ...grid, xSamples: 3 }, ["AIR", "LAND", "RIDGE"], new Array(12).fill("AIR")),
  makeArtifact({ ...grid, minimumX: 10.5 }, ["AIR", "LAND", "RIDGE"], new Array(8).fill("AIR")),
  makeArtifact({ ...grid, spacingZ: 3 }, ["AIR", "LAND", "RIDGE"], new Array(8).fill("AIR")),
]) {
  assert.throws(() => comparisonApi.compare(
    { scene: reference },
    { scene: adapt(mismatch, "mismatched.json") }
  ), /grids do not match exactly/);
}

assert.throws(() => comparisonApi.compare(
  { scene: reference },
  { scene: adapt(makeArtifact(grid, ["AIR", "LAND", "RIDGE"],
    ["AIR", "LAND", "AIR", "AIR", "AIR", "RIDGE", "AIR", "RIDGE"]), "bound.json", true) }
), /unbound local diagnostic/);

console.log("PASS Studio aligned terrain comparison contract");
