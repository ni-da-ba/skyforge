"use strict";

const assert = require("node:assert/strict");
const backup = require("../orchestrator/studio/profile-backup.js");

const briefLibrary = {
  document_type: "SKYFORGE_STUDIO_BRIEF_LIBRARY",
  format_version: 1,
  active_brief_id: null,
  briefs: [],
};
const worldBrief = {
  parseLibrary(value) {
    assert.equal(value.document_type, briefLibrary.document_type);
    return structuredClone(value);
  },
  mergeLibraries(current, imported) {
    const records = new Map(current.briefs.map(item => [item.id, item]));
    for (const item of imported.briefs) {
      if (records.has(item.id) && JSON.stringify(records.get(item.id)) !== JSON.stringify(item)) {
        throw new Error("brief library contains a different local draft with the same ID");
      }
      records.set(item.id, item);
    }
    return {...current, briefs:[...records.values()]};
  },
};
const workspaceSession = {
  validateRecord(value) {
    if (!value || value.format_version !== 1 || value.enabled !== true ||
        !(value.workspace_json === null || typeof value.workspace_json === "string")) {
      throw new Error("invalid saved inspection session");
    }
    if (value.development_api_token !== undefined) throw new Error("unsupported saved inspection field");
    return structuredClone(value);
  },
};
const workspacePackage = {
  parse(value) {
    const parsed = typeof value === "string" ? JSON.parse(value) : value;
    if (parsed.document_type !== "SKYFORGE_STUDIO_INSPECTION_WORKSPACE") {
      throw new Error("unsupported inspection workspace");
    }
    return parsed;
  },
};
function libraryApi() {
  return {
    normalizeRecords(records) {
      if (!Array.isArray(records)) throw new Error("records must be an array");
      const ids = new Set();
      for (const record of records) {
        if (!record || typeof record.id !== "string" || ids.has(record.id)) throw new Error("invalid or duplicate saved ID");
        ids.add(record.id);
      }
      return records.slice().sort((a,b)=>a.id.localeCompare(b.id));
    },
  };
}
const terrainLibrary = libraryApi();
const regionalLibrary = libraryApi();
const regionalInventory = {
  parseInventoryCsv(value) { return {value}; },
  parseRankingCsv(value) { return {value}; },
};
const regionalComparison = {
  validatePackageInputs(value, fileBytes, inventoryApi) {
    const parsed = JSON.parse(value);
    if (parsed.document_type !== "SKYFORGE_STUDIO_REGIONAL_COMPARISON_PACKAGE" ||
        fileBytes !== new TextEncoder().encode(value).length ||
        !inventoryApi.parseInventoryCsv) {
      throw new Error("invalid regional comparison package");
    }
    return parsed;
  },
};
const validators = {
  worldBrief: worldBrief,
  workspaceSession,
  workspacePackage,
  terrainLibrary,
  regionalLibrary,
  regionalComparison,
  regionalInventory,
  maximumBytes: 10_000,
};
function create(overrides = {}) {
  return backup.create({
    createdAt: "2026-10-01T00:00:00.000Z",
    worldBriefLibrary: briefLibrary,
    inspectionSession: null,
    terrainComparisons: [],
    regionalComparisons: [],
    ...overrides,
  }, validators);
}

const session = {
  format_version: 1,
  enabled: true,
  workspace_json: JSON.stringify({
    document_type: "SKYFORGE_STUDIO_INSPECTION_WORKSPACE",
    format_version: 1,
    scene: {title:"source.json",artifactJson:"{}"},
    overlay: null,
    view: {mode:"terrain",camera:{},controls:{}},
  }),
};
const terrain = [{id:"terrain-0001",report_json:"{\"document_type\":\"diagnostic\"}"}];
const regional = [{id:"regional-0001",package_json:"{\"rows\":[]}"}];
const source = create({
  inspectionSession: session,
  terrainComparisons: terrain,
  regionalComparisons: regional,
});
const reopened = backup.parse(JSON.stringify(source), validators);
assert.deepEqual(reopened, source);
assert.equal(reopened.inspection_session.workspace_json, session.workspace_json);
assert.throws(() => backup.parse("{", validators), /valid JSON/);
assert.throws(() => backup.parse({...source, format_version:2}, validators), /unsupported Studio backup version/);
assert.throws(() => backup.parse({...source, extra:"credential"}, validators), /unsupported document shape/);
assert.throws(() => backup.parse({...source, inspection_session:{...session,development_api_token:"secret"}}, validators), /unsupported saved inspection field/);
assert.throws(() => create({terrainComparisons:[terrain[0],terrain[0]]}), /invalid or duplicate saved ID/);
assert.throws(() => create({regionalComparisons:[{...regional[0],package_json:"{}"}]}), /invalid regional comparison package/);

const current = create({worldBriefLibrary:{...briefLibrary,briefs:[{id:"brief-0001",title:"Existing"}]}});
const incoming = create({worldBriefLibrary:{...briefLibrary,briefs:[{id:"brief-0002",title:"Imported"}]},terrainComparisons:terrain});
const plan = backup.prepareRestore(incoming,current,validators);
assert.equal(plan.worldBriefLibrary.briefs.length,2);
assert.equal(plan.counts.terrainComparisonsAdded,1);
assert.equal(plan.inspectionReplacementRequired,false);
assert.deepEqual(backup.prepareRestore(incoming,current,validators).terrainComparisons,terrain);

const inspectionCurrent = create({inspectionSession:{...session,workspace_json:"{\"local\":\"different\"}"}});
const inspectionIncoming = create({inspectionSession:session});
const keep = backup.prepareRestore(inspectionIncoming,inspectionCurrent,validators);
assert.equal(keep.inspectionReplacementRequired,true);
assert.equal(keep.inspectionSession.workspace_json,inspectionCurrent.inspection_session.workspace_json);
const replace = backup.prepareRestore(inspectionIncoming,inspectionCurrent,validators,{replaceInspection:true});
assert.equal(replace.inspectionSession.workspace_json,session.workspace_json);
assert.throws(() => backup.prepareRestore(
  {...incoming,terrain_comparisons:[...terrain,terrain[0]]},current,validators
), /invalid or duplicate saved ID/);
assert.throws(() => backup.parse(JSON.stringify({...source,created_at:"bad"}),validators), /valid timestamp/);
assert.throws(() => backup.parse(JSON.stringify(source)+" ".repeat(10_000),validators), /supported file size/);

console.log("PASS Studio local backup contract");
