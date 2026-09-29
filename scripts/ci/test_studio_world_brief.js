"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const appPath = path.join(__dirname, "..", "orchestrator", "studio", "app.js");
const app = fs.readFileSync(appPath, "utf8");
const start = app.indexOf("  // BEGIN STUDIO WORLD BRIEF DOCUMENT CONTRACT");
const end = app.indexOf("  // END STUDIO WORLD BRIEF DOCUMENT CONTRACT");
assert.notEqual(start, -1, "world brief contract start marker must exist");
assert.notEqual(end, -1, "world brief contract end marker must exist");
const contract = app.slice(start, end + "  // END STUDIO WORLD BRIEF DOCUMENT CONTRACT".length);
const window = {};
vm.runInNewContext(contract, { window });
const brief = window.SkyforgeStudioWorldBrief;
assert.ok(brief, "document contract must expose its pure helpers");

const first = brief.create(
  "brief-12345678",
  "  Wind-carved island  ",
  "An isolated volcanic island with spring-fed rivers and deep freshwater lakes.",
  "2026-09-29T12:00:00Z",
  "2026-09-29T12:30:00Z"
);
assert.equal(first.title, "Wind-carved island");
assert.equal(first.document_type, "SKYFORGE_STUDIO_WORLD_BRIEF");
assert.equal(first.format_version, 1);
assert.equal(Object.hasOwn(first, "artifact_kind"), false);
assert.equal(Object.hasOwn(first, "review_authority"), false);
assert.deepEqual(
  JSON.parse(brief.serialize(first)),
  JSON.parse(JSON.stringify(first))
);

const second = brief.create(
  "brief-87654321",
  "Alpine basin",
  "A glacial basin with a braided river.",
  "2026-09-29T12:01:00Z",
  "2026-09-29T12:31:00Z"
);
const library = brief.createLibrary([first, second], second.id);
assert.equal(brief.parseLibrary(brief.serializeLibrary(library)).active_brief_id, second.id);
assert.throws(() => brief.parse({ ...first, format_version: 99 }), /unsupported world brief format version/);
assert.throws(() => brief.parse({ ...first, review_authority: true }), /unsupported fields/);
assert.throws(() => brief.parse({ ...first, title: "  " }), /title is required/);
assert.throws(() => brief.parse({ ...first, id: "short" }), /id is invalid/);
assert.throws(() => brief.createLibrary([first, first], first.id), /duplicate draft ids/);
assert.throws(() => brief.createLibrary([first], "missing-123"), /active draft does not exist/);

console.log("PASS Studio local world brief document behavior");
