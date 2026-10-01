"use strict";

const assert = require("node:assert/strict");
const session = require("../orchestrator/studio/workspace-session.js");


const workspacePackage = require("../orchestrator/studio/workspace-package.js");
const authorityWorkspace = workspacePackage.create({
  scene: {
    title: "scene.json",
    artifactJson: JSON.stringify({
      artifact_kind: "SKYFORGE_TERRAIN_SEMANTIC_VOLUME",
      review_authority: true,
    }),
  },
  overlay: null,
  view: {
    mode: "terrain",
    camera: { yaw: 0, pitch: 0.5, zoom: 1 },
    controls: {
      semanticView: "top",
      surface: "base",
      sliceIndex: 0,
      causeField: "none",
      showFlowVectors: true,
      showChannelWidth: true,
      showHydrologyResponse: true,
      showWaterIntent: true,
    },
  },
});
const prepared = workspacePackage.prepare(authorityWorkspace, {
  adaptScene(_artifact, source) {
    return { sceneKind: "TERRAIN_SEMANTIC_VOLUME", source };
  },
  adaptOverlay() { throw new Error("no overlay expected"); },
});
assert.equal(prepared.scene.source.binding, "UNBOUND_LOCAL");
assert.equal(prepared.scene.source.reviewAuthority, false,
  "reopened session sources keep local diagnostic authority");

const records = new Map();
const memoryStore = {
  async read() { return records.has("current") ? records.get("current") : null; },
  async write(value) { records.set("current", structuredClone(value)); },
  async remove() { records.delete("current"); },
};
const controller = session.createController(memoryStore, 256);

(async () => {
  assert.equal(await controller.read(), null);
  await controller.enable();
  assert.deepEqual(await controller.read(), {
    format_version: 1,
    enabled: true,
    workspace_json: null,
  }, "opt-in is explicit and can exist before a source is loaded");

  const workspace = JSON.stringify({
    document_type: "SKYFORGE_STUDIO_INSPECTION_WORKSPACE",
    format_version: 1,
    scene: { title: "scene.json", artifactJson: "{}" },
    overlay: null,
    view: { mode: "terrain", camera: {}, controls: {} },
  });
  assert.equal(await controller.save(workspace), true);
  assert.equal((await controller.read()).workspace_json, workspace, "latest inspection is persisted exactly");
  assert.equal(await controller.forgetWorkspace(), true);
  assert.equal((await controller.read()).enabled, true, "forgetting the data keeps the opt-in");
  assert.equal((await controller.read()).workspace_json, null);

  await assert.rejects(controller.save("not JSON"), /not valid JSON/);
  await assert.rejects(controller.save(" ".repeat(257)), /exceeds its supported size/);
  assert.throws(() => session.validateRecord({
    format_version: 1,
    enabled: true,
    workspace_json: null,
    development_api_token: "must not be persisted",
  }, 256), /unsupported fields/);
  assert.throws(() => session.validateRecord({
    format_version: 2,
    enabled: true,
    workspace_json: null,
  }, 256), /unsupported saved inspection record version/);

  await controller.disable();
  assert.equal(await controller.read(), null);
  assert.equal(await controller.save(workspace), false, "disabled sessions cannot silently recreate storage");

  const failingStore = {
    async read() { throw new Error("quota denied"); },
    async write() { throw new Error("quota denied"); },
    async remove() { throw new Error("storage denied"); },
  };
  const failingController = session.createController(failingStore, 256);
  await assert.rejects(failingController.read(), /quota denied/);
  await assert.rejects(failingController.enable(), /quota denied/);
  await assert.rejects(failingController.disable(), /storage denied/);

  const corruptedStore = {
    async read() {
      return { format_version: 1, enabled: true, workspace_json: null, secret: "unexpected" };
    },
    async write() {},
    async remove() {},
  };
  await assert.rejects(session.createController(corruptedStore, 256).read(), /unsupported fields/);

  console.log("PASS Studio inspection workspace session contract");
})().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
