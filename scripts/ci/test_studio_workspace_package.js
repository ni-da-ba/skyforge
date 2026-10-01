"use strict";

const assert = require("node:assert/strict");
const workspacePackage = require("../orchestrator/studio/workspace-package.js");

const sceneJson = '{\n  "artifact_kind": "SKYFORGE_TERRAIN_SEMANTIC_VOLUME",\n  "review_authority": true\n}\n';
const overlayJson = '{"artifact_kind":"SKYFORGE_BOUND_HYDROLOGY_SEMANTIC_LAYER"}';
const input = {
  scene: { title: "terrain.json", artifactJson: sceneJson },
  overlay: { title: "hydrology.json", artifactJson: overlayJson },
  view: {
    mode: "terrain",
    camera: { yaw: -0.72, pitch: 0.5, zoom: 1.4 },
    controls: {
      semanticView: "slice",
      surface: "hydrology",
      sliceIndex: 12,
      causeField: "runoffPotential",
      showFlowVectors: true,
      showChannelWidth: false,
      showHydrologyResponse: true,
      showWaterIntent: true,
    },
  },
};
const created = workspacePackage.create(input);
const serialized = JSON.stringify(created);
const parsed = workspacePackage.parse(serialized);
assert.deepEqual(JSON.parse(JSON.stringify(parsed)), created);
assert.equal(parsed.scene.artifactJson, sceneJson, "source JSON text is preserved exactly");
assert.equal(parsed.overlay.artifactJson, overlayJson);

const adapterCalls = [];
const prepared = workspacePackage.prepare(serialized, {
  adaptScene(artifact, options) {
    adapterCalls.push(["scene", artifact, options]);
    return { sceneKind: "TERRAIN_SEMANTIC_VOLUME", source: options };
  },
  adaptOverlay(artifact, _scene, options) {
    adapterCalls.push(["overlay", artifact, options]);
    return { source: options };
  },
});
assert.equal(prepared.view.controls.semanticView, "slice");
assert.equal(prepared.scene.source.binding, "UNBOUND_LOCAL");
assert.equal(prepared.scene.source.reviewAuthority, false);
assert.equal(prepared.overlay.source.reviewAuthority, false);
assert.equal(adapterCalls.length, 2);
assert.equal(adapterCalls[0][1].review_authority, true, "input provenance text remains intact");
assert.equal(adapterCalls[0][2].reviewAuthority, false, "restored authority is forcibly downgraded");
assert.equal(adapterCalls[1][2].binding, "UNBOUND_LOCAL");

assert.throws(() => workspacePackage.parse("{"), /valid JSON/);
assert.throws(() => workspacePackage.parse({ ...created, format_version: 2 }), /unsupported inspection workspace format version/);
assert.throws(() => workspacePackage.parse({ ...created, unexpected: true }), /unsupported fields/);
assert.throws(() => workspacePackage.parse({ ...created, scene: { title: "scene", artifactJson: "[]" } }), /scene source must be a JSON object/);
assert.throws(() => workspacePackage.parse({ ...created, view: { ...created.view, camera: { ...created.view.camera, zoom: 20 } } }), /camera zoom/);
assert.throws(() => workspacePackage.parse({
  ...created,
  view: { ...created.view, controls: { ...created.view.controls, showFlowVectors: "yes" } },
}), /showFlowVectors must be boolean/);

const mismatched = [];
assert.throws(() => workspacePackage.prepare(serialized, {
  adaptScene(_artifact, options) {
    mismatched.push(options);
    return { sceneKind: "ATMOSPHERE_VECTOR_FIELD", source: options };
  },
  adaptOverlay() { throw new Error("must not adapt overlay for mismatched scene"); },
}), /does not match its scene source/);
assert.equal(mismatched[0].reviewAuthority, false);

let overlayFailureScene = null;
assert.throws(() => workspacePackage.prepare(serialized, {
  adaptScene(_artifact, options) {
    overlayFailureScene = { sceneKind: "TERRAIN_SEMANTIC_VOLUME", source: options };
    return overlayFailureScene;
  },
  adaptOverlay() { throw new Error("overlay binding mismatch"); },
}), /overlay binding mismatch/);
assert.equal(overlayFailureScene.source.reviewAuthority, false, "prepare only returns after every source adapts");

const atmosphere = workspacePackage.create({
  scene: { title: "wind.json", artifactJson: '{"artifact_kind":"ATMOSPHERE"}' },
  overlay: null,
  view: {
    mode: "atmosphere",
    camera: { yaw: 0, pitch: 0.2, zoom: 1 },
    controls: {
      dataset: "opportunity",
      vectorMode: "gust",
      colorMode: "shear",
      altitude: "48",
      frame: 3,
      arrowScale: 1.25,
    },
  },
});
assert.equal(atmosphere.view.controls.frame, 3);
assert.throws(() => workspacePackage.create({
  ...atmosphere,
  view: { ...atmosphere.view, controls: { ...atmosphere.view.controls, colorMode: "minecraft" } },
}), /colorMode is unsupported/);
assert.throws(() => workspacePackage.parse(" ".repeat(workspacePackage.maximumFileBytes + 1)), /25 MB or smaller/);

console.log("PASS Studio inspection workspace package contract");
