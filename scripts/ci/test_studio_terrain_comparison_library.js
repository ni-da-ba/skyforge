"use strict";

const assert = require("node:assert/strict");
const library = require("../orchestrator/studio/terrain-comparison-library.js");

const now = "2026-10-01T00:00:00.000Z";
function report(extra = {}) {
  return JSON.stringify({
    document_type: "SKYFORGE_STUDIO_TERRAIN_COMPARISON_REPORT",
    format_version: 1,
    diagnostic_only: true,
    human_review_evidence: false,
    review_authority: false,
    provenance_verified: false,
    ...extra,
  });
}
function record(id, title = "terrain candidate", json = report(), updated = now) {
  return library.createRecord(id, title, json, now, updated);
}

const first = record("terrain-report-0001", "  Ridge candidate 1  ");
assert.equal(first.title, "Ridge candidate 1");
assert.equal(first.report_bytes, new TextEncoder().encode(first.report_json).length);
assert.equal(library.parseRecord(first).id, first.id);
assert.equal(library.normalizeRecords([first])[0].id, first.id);
assert.throws(() => record("short", "invalid id"), /id is invalid/);
assert.throws(() => record("terrain-report-0002", "  "), /name must be between/);
assert.throws(() => record("terrain-report-0002", "x".repeat(121)), /name must be between/);
assert.throws(() => record("terrain-report-0002", "bad json", "{"), /valid JSON/);
assert.throws(() => record("terrain-report-0002", "wrong type", report({document_type:"OTHER"})), /diagnostic-only/);
assert.throws(() => record("terrain-report-0002", "wrong authority", report({human_review_evidence:true})), /diagnostic-only/);
assert.throws(() => library.parseRecord({...first, report_bytes:first.report_bytes+1}), /size does not match/);
assert.throws(() => library.normalizeRecords([first, first]), /duplicate ids/);

let records = library.upsertRecords([], first);
const update = library.createRecord(first.id, "Ridge candidate 1 revised", report({summary:{changedCellCount:2}}),
  "2026-10-01T00:00:00.000Z", "2026-10-02T00:00:00.000Z");
records = library.upsertRecords(records, update);
assert.equal(records.length, 1);
assert.equal(records[0].created_at, first.created_at, "updates retain the original creation time");
assert.equal(records[0].title, "Ridge candidate 1 revised");
assert.equal(records[0].updated_at, "2026-10-02T00:00:00.000Z");

const maxRecords = Array.from({length:library.maximumItems}, (_,i) =>
  record("terrain-report-" + String(i).padStart(4,"0")));
assert.equal(library.upsertRecords([], maxRecords[0]).length, 1);
assert.throws(() => library.upsertRecords(maxRecords, record("terrain-report-overflow")), /library is full/);
assert.equal(library.removeRecords(maxRecords, maxRecords[0].id).length, library.maximumItems - 1);
assert.throws(() => library.removeRecords(maxRecords, "bad"), /id is invalid/);

const badTimestamp = {...first, updated_at:"2026-10-01T00:00:00Z"};
assert.throws(() => library.parseRecord(badTimestamp), /canonical UTC timestamp/);
assert.throws(() => library.normalizeRecords(new Array(library.maximumItems+1).fill(first)), /too many items/);

console.log("PASS Studio terrain comparison library contract");
