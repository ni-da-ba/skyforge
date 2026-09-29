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
assert.equal(brief.parseLibrary(brief.serializeLibrary(brief.createLibrary())).briefs.length, 0);
const restored = brief.mergeLibraries(brief.createLibrary([first]), library);
assert.equal(restored.briefs.length, 2);
assert.equal(restored.active_brief_id, first.id, "importing a backup keeps the current brief selected");
const restoredIntoEmpty = brief.mergeLibraries(brief.createLibrary(), library);
assert.equal(restoredIntoEmpty.active_brief_id, second.id, "an empty workspace restores the backup selection");
assert.equal(brief.mergeLibraries(restored, library).briefs.length, 2, "re-importing a backup is idempotent");
const changedFirst = brief.create(
  first.id,
  "Changed copy",
  first.intent,
  first.created_at,
  "2026-09-29T13:00:00Z"
);
assert.throws(
  () => brief.mergeLibraries(
    brief.createLibrary([first], first.id),
    brief.createLibrary([changedFirst], changedFirst.id)
  ),
  /different local draft with the same ID/
);
const fullLibrary = brief.createLibrary(
  Array.from({ length: 100 }, (_, index) => brief.create(
    "brief-" + String(index).padStart(8, "0"),
    "Draft " + index,
    "",
    "2026-09-29T12:00:00Z",
    "2026-09-29T12:00:00Z"
  ))
);
const extraBrief = brief.create(
  "brief-10000000",
  "One too many",
  "",
  "2026-09-29T12:00:00Z",
  "2026-09-29T12:00:00Z"
);
assert.throws(
  () => brief.mergeLibraries(fullLibrary, brief.createLibrary([extraBrief], extraBrief.id)),
  /invalid number of drafts/
);
assert.throws(() => brief.parseLibrary("{"), /JSON/);
assert.throws(() => brief.parseLibrary({ document_type: brief.libraryType, format_version: 1, active_brief_id: null }), /missing required fields/);
assert.throws(() => brief.parse({ ...first, format_version: 99 }), /unsupported world brief format version/);
assert.throws(() => brief.parse({ ...first, review_authority: true }), /unsupported fields/);
assert.throws(() => brief.parse({ ...first, title: "  " }), /title is required/);
assert.throws(() => brief.parse({ ...first, id: "short" }), /id is invalid/);
assert.throws(() => brief.createLibrary([first, first], first.id), /duplicate draft ids/);
assert.throws(() => brief.createLibrary([first], "missing-123"), /active draft does not exist/);

console.log("PASS Studio local world brief document behavior");
