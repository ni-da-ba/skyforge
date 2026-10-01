"use strict";

const assert = require("node:assert/strict");
global.window = {};
require("../orchestrator/studio/scene.js");
const sceneApi = global.window.SkyforgeStudioScene;
delete global.window;
const demoApi = require("../orchestrator/studio/terrain-comparison-demo.js");
const comparisonApi = require("../orchestrator/studio/terrain-comparison.js");
const reportApi = require("../orchestrator/studio/terrain-comparison-report.js");
const readerApi = require("../orchestrator/studio/terrain-comparison-report-reader.js");

const inputs = demoApi.createInputs();
assert.equal(inputs.length, 2);
assert.ok(inputs.every(input => input.title.includes("Synthetic example")));
assert.equal(inputs[0].artifact.grid.sample_count, 27);
assert.equal(inputs[0].artifact.grid.sample_count, inputs[1].artifact.grid.sample_count);

const scenes = inputs.map(input => sceneApi.adaptArtifact(input.artifact, {
  binding: "UNBOUND_LOCAL",
  artifactTitle: input.title,
  reviewAuthority: false,
}));
assert.ok(scenes.every(scene => scene.source.binding === "UNBOUND_LOCAL"));
assert.ok(scenes.every(scene => scene.source.reviewAuthority === false));
assert.ok(scenes.every(scene => scene.source.artifactDigest === ""));
const comparison = comparisonApi.compare(
  { scene: scenes[0], title: inputs[0].title },
  { scene: scenes[1], title: inputs[1].title }
);
assert.equal(comparison.changedCellCount, 3);
assert.equal(comparison.changedColumnCount, 3);
assert.equal(comparison.maximumAbsSurfaceDelta, 2);
assert.ok(comparison.semanticTransitions.length > 0);

const report = reportApi.create(comparison);
assert.equal(report.human_review_evidence, false);
assert.equal(report.review_authority, false);
assert.equal(report.provenance_verified, false);
assert.equal(report.sources.reference.artifactDigest, "");
assert.equal(report.sources.candidate.artifactDigest, "");
assert.match(report.sources.referenceTitle, /Synthetic example/);
assert.match(report.sources.candidateTitle, /Synthetic example/);
const reopened = readerApi.parse(reportApi.stringify(report));
assert.equal(reopened.comparison.reviewAuthority, false);
assert.equal(reopened.comparison.changedCellCount, 3);
assert.match(reopened.comparison.referenceTitle, /Synthetic example/);
assert.match(reopened.comparison.candidateTitle, /Synthetic example/);

console.log("PASS Studio terrain comparison walkthrough contract");
