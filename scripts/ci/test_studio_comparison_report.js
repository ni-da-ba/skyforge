"use strict";
const assert = require("node:assert/strict");
globalThis.window = {};
require("../orchestrator/studio/comparison-report.js");
const contract = window.SkyforgeStudioComparisonReport;
require("../orchestrator/studio/comparison-report-reader.js");
const reader = window.SkyforgeStudioComparisonReportReader;
const referenceSource = {
  binding: "AUTH-BOUND", artifactId: "reference-17", artifactTitle: "Reference", sourceSha: "a1",
  reviewAuthority: true, artifactKind: "SKYFORGE_BOUND_HYDROLOGY_SEMANTIC_LAYER", artifactDigest: "ref-digest",
};
const candidateSource = {
  binding: "UNBOUND_LOCAL", artifactId: null, artifactTitle: "candidate local.json", sourceSha: null,
  reviewAuthority: false, artifactKind: "SKYFORGE_BOUND_HYDROLOGY_SEMANTIC_LAYER", artifactDigest: "candidate-digest",
};
const worldFrame = { centerX: 100, centerZ: 200, suspensionElevation: 50, nominalRadius: 64 };
const gridBinding = {
  minimumX: 0, minimumZ: 0, spacingX: 10, spacingZ: 10,
  xSamples: 2, zSamples: 1, causeStride: 1, causeSampleCount: 2,
};
const terrainSha = "a".repeat(64);
const reference = {
  sceneKind: "HYDROLOGY_SEMANTIC_LAYER", source: referenceSource, terrainSemanticSha256: terrainSha,
  binding: { associationToken: "sfassoc:v1:fixture", worldFrame }, gridBinding,
};
const candidate = {
  sceneKind: "HYDROLOGY_SEMANTIC_LAYER", source: candidateSource, terrainSemanticSha256: terrainSha,
  binding: { associationToken: "sfassoc:v1:fixture", worldFrame }, gridBinding,
};
const samples = [
  { grid: [1, 0], position: [110, 8, 200],
    reference: { runoffPotential: 0.2, provenance: { field: "runoff" } },
    candidate: { runoffPotential: 0.8, provenance: { field: "runoff" } }, deltas: { runoffPotential: 0.6 } },
  { grid: [0, 0], position: [100, 8, 200],
    reference: { runoffPotential: 0.5 }, candidate: { runoffPotential: 0.25 }, deltas: { runoffPotential: -0.25 } },
];
const fieldSamples = [
  { grid: [1, 0], coverage: "shared", reference: { wet: false, waterSurfaceY: null, waterDepthWorld: 0 },
    candidate: { wet: true, waterSurfaceY: 7, waterDepthWorld: 1 }, surfaceDeltaY: 0,
    waterSurfaceDeltaY: null, waterDepthDelta: 1, wetTransition: "WET_ADDED" },
  { grid: [0, 0], coverage: "candidate-only", reference: null,
    candidate: { wet: true, waterSurfaceY: 7.5, waterDepthWorld: 0.5 }, surfaceDeltaY: null,
    waterSurfaceDeltaY: null, waterDepthDelta: null, wetTransition: null },
];
const comparison = {
  sceneKind: "HYDROLOGY_SEMANTIC_COMPARISON", terrainSemanticSha256: terrainSha,
  associationToken: "sfassoc:v1:fixture", referenceSource, candidateSource,
  samples, fieldSamples, reviewAuthority: false,
};
const input = { comparison, reference, candidate, selectedField: "causes", selectedCauseField: "runoffPotential" };
const before = JSON.stringify({ reference, candidate, comparison });
const report = contract.create(input);
assert.equal(report.document_type, "SKYFORGE_STUDIO_HYDROLOGY_COMPARISON_REPORT");
assert.equal(report.format_version, 1);
assert.equal(report.diagnostic_only, true);
assert.equal(report.human_review_evidence, false);
assert.equal(report.source_review_authority, false, "a local candidate must remain unbound");
assert.equal(report.sources.candidate.binding, "UNBOUND_LOCAL");
assert.equal(report.binding.terrainSemanticSha256, terrainSha);
assert.equal(report.binding.associationToken, "sfassoc:v1:fixture");
assert.deepEqual(report.binding.worldFrame, worldFrame);
assert.deepEqual(report.binding.gridBinding, gridBinding);
assert.equal(report.selected_field, "causes");
assert.equal(report.selected_cause_field, "runoffPotential");
assert.deepEqual(report.cause_samples.map((sample) => sample.grid), [[0, 0], [1, 0]]);
assert.deepEqual(report.cause_samples[1].deltas, { runoffPotential: 0.6 });
assert.deepEqual(report.projected_field_samples.map((sample) => sample.grid), [[0, 0], [1, 0]]);
assert.equal(report.projected_field_samples[0].coverage, "candidate-only");
assert.equal(report.projected_field_samples[1].wetTransition, "WET_ADDED");
assert.equal(JSON.stringify(report), JSON.stringify(contract.create(input)), "serialization is deterministic");
assert.equal(JSON.stringify({ reference, candidate, comparison }), before, "report creation does not mutate inputs");
assert.equal(contract.filename(report), "skyforge-hydrology-comparison-reference-17-to-candidate-local-json.json");

const registeredCandidate = {
  ...candidate,
  source: { ...candidateSource, binding: "AUTH-BOUND", artifactId: "candidate-18",
    artifactTitle: "Candidate", sourceSha: "b2", reviewAuthority: true },
};
const registeredComparison = { ...comparison, candidateSource: registeredCandidate.source, reviewAuthority: true };
const registeredReport = contract.create({
  comparison: registeredComparison, reference, candidate: registeredCandidate, selectedField: "water-depth",
});
assert.equal(registeredReport.source_review_authority, true);
assert.equal(registeredReport.human_review_evidence, false, "artifact-bound exports are still not review evidence");
assert.equal(registeredReport.selected_cause_field, null);
assert.equal(contract.filename(registeredReport), "skyforge-hydrology-comparison-reference-17-to-candidate-18.json");
assert.throws(() => contract.create({ ...input, selectedField: "unknown" }), /selected field is invalid/);
assert.throws(() => contract.create({
  ...input, comparison: { ...comparison, associationToken: "sfassoc:v1:other" },
}), /binding does not match/);
assert.throws(() => contract.create({
  ...input, candidate: { ...candidate, source: { ...candidateSource } },
}), /sources do not match/);

const serializedReport = JSON.stringify(report);
const imported = reader.parse(serializedReport);
assert.equal(imported.causeSampleCount, 2);
assert.equal(imported.projectedFieldSampleCount, 2);
assert.equal(imported.sourceReviewAuthorityRecorded, false);
assert.equal(imported.sourceReviewAuthorityVerified, false, "imported provenance claims are never verified");
assert.deepEqual(reader.page(imported, "causes", 0).items.map((sample) => sample.grid), [[1, 0], [0, 0]]);
assert.throws(() => reader.parse(JSON.stringify({ ...report, format_version: 2 })), /unsupported.*version/);
assert.throws(() => reader.parse("{"), /not valid JSON/);
assert.throws(() => reader.parse(JSON.stringify({ ...report, diagnostic_only: false })), /must remain diagnostic/);
assert.throws(() => reader.parse(JSON.stringify({
  ...report, cause_samples: [{ ...report.cause_samples[0], grid: [0] }],
})), /grid is invalid/);
assert.throws(() => reader.parse(serializedReport, reader.maximumFileBytes + 1), /32 MB or smaller/);

const forgedAuthority = JSON.parse(serializedReport);
forgedAuthority.source_review_authority = true;
const forgedImport = reader.parse(JSON.stringify(forgedAuthority));
assert.equal(forgedImport.sourceReviewAuthorityRecorded, true);
assert.equal(forgedImport.sourceReviewAuthorityVerified, false,
  "a forged local source authority claim remains only recorded text");

const manySamples = Array.from({ length: 123 }, (_, index) => ({
  ...report.cause_samples[index % report.cause_samples.length], grid: [index, 0],
}));
const paginatedJson = JSON.stringify({ ...report, cause_samples: manySamples });
const paginated = reader.parse(paginatedJson);
const firstPage = reader.page(paginated, "causes", 0);
const secondPage = reader.page(paginated, "causes", 1);
const thirdPage = reader.page(paginated, "causes", 2);
assert.equal(firstPage.items.length, 50);
assert.equal(firstPage.firstIndex, 1);
assert.equal(secondPage.firstIndex, 51);
assert.equal(secondPage.items.length, 50);
assert.equal(thirdPage.firstIndex, 101);
assert.equal(thirdPage.items.length, 23);
assert.equal(thirdPage.pageCount, 3);
assert.ok(Object.isFrozen(firstPage.items));
assert.equal(paginatedJson, JSON.stringify({ ...report, cause_samples: manySamples }),
  "reading and paging a report does not mutate source data");
assert.throws(() => reader.page(paginated, "causes", 3), /page index is invalid/);
console.log("PASS Studio hydrology comparison report behavior");

