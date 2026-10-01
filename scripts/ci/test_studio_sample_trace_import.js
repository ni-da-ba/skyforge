"use strict";

const assert = require("node:assert/strict");
const traceViewer = require("../orchestrator/studio/trace-viewer.js");

function source(binding, reviewAuthority, artifactId) {
  return {
    binding,
    artifactId,
    artifactTitle: null,
    sourceSha: null,
    reviewAuthority,
    artifactKind: "SKYFORGE_TERRAIN_SEMANTIC_VOLUME",
    artifactDigest: "digest-1",
  };
}

function trace(reviewAuthority = false) {
  return {
    document_type: traceViewer.documentType,
    format_version: traceViewer.formatVersion,
    review_authority: reviewAuthority,
    scene_kind: "TERRAIN_SEMANTIC_VOLUME",
    sources: {
      scene: source(reviewAuthority ? "AUTH-BOUND" : "UNBOUND_LOCAL", reviewAuthority, "scene-1"),
      overlay: null,
      reference: null,
      candidate: null,
    },
    coordinate_system: { id: "WORLD_XYZ" },
    provider: { label: "Backend-neutral provider", version: "1.0" },
    overlay: null,
    comparison: null,
    selected_sample: {
      position: [12.5, 4, -8.5],
      semanticName: "SURFACE_MANTLE",
      provenance: { authored_field: "terrain.response" },
    },
  };
}

const localDocument = trace(false);
const parsedLocal = traceViewer.parse(JSON.stringify(localDocument));
assert.deepEqual(parsedLocal, localDocument);
assert.equal(parsedLocal.review_authority, false);
assert.equal(parsedLocal.sources.scene.binding, "UNBOUND_LOCAL");
assert.equal(parsedLocal.selected_sample.position[2], -8.5);

const registeredDocument = trace(true);
const parsedRegistered = traceViewer.parse(JSON.stringify(registeredDocument));
assert.equal(parsedRegistered.review_authority, true);
assert.equal(parsedRegistered.sources.scene.reviewAuthority, true);

assert.throws(() => traceViewer.parse("{"), /valid JSON/);
assert.throws(() => traceViewer.parse("[]"), /JSON object/);
assert.throws(() => traceViewer.parse(JSON.stringify({ document_type: "OTHER" })), /not a selected sample trace/);
assert.throws(() => traceViewer.parse(JSON.stringify({ ...localDocument, format_version: 2 })), /unsupported sample trace format version/);
assert.throws(() => traceViewer.parse(JSON.stringify({ ...localDocument, selected_sample: [] })), /selected_sample must be an object/);
assert.throws(() => traceViewer.parse(JSON.stringify({ ...localDocument, review_authority: "yes" })), /review_authority must be boolean/);
assert.throws(() => traceViewer.parse(JSON.stringify({ ...localDocument, unexpected: true })), /unsupported fields/);
assert.throws(() => traceViewer.parse("x".repeat(traceViewer.maximumFileBytes + 1)), /smaller than 1 MB/);

console.log("PASS Studio selected sample trace import behavior");
