(function (root) {
  "use strict";

  const DOCUMENT_TYPE = "SKYFORGE_STUDIO_LOCAL_BACKUP";
  const FORMAT_VERSION = 1;
  const MAXIMUM_FILE_BYTES = 128 * 1024 * 1024;
  const ROOT_KEYS = [
    "document_type", "format_version", "created_at", "world_brief_library",
    "inspection_session", "terrain_comparisons", "regional_comparisons",
  ];
  const INPUT_KEYS = [
    "createdAt", "worldBriefLibrary", "inspectionSession",
    "terrainComparisons", "regionalComparisons",
  ];

  function isRecord(value) {
    return value !== null && typeof value === "object" && !Array.isArray(value);
  }

  function exactKeys(value, keys) {
    return isRecord(value) && Object.keys(value).sort().join(",") === keys.slice().sort().join(",");
  }

  function byteLength(value) {
    return new TextEncoder().encode(value).length;
  }

  function timestamp(value, label) {
    if (typeof value !== "string" || !Number.isFinite(Date.parse(value))) {
      throw new Error(label + " must be a valid timestamp");
    }
    const normalized = new Date(value).toISOString();
    if (normalized !== value) throw new Error(label + " must be a canonical UTC timestamp");
    return normalized;
  }

  function requiredValidators(validators) {
    if (!validators || !validators.worldBrief || !validators.workspaceSession ||
        !validators.workspacePackage || !validators.terrainLibrary || !validators.regionalLibrary ||
        !validators.regionalComparison || !validators.regionalInventory) {
      throw new Error("Studio backup validators are unavailable");
    }
    return validators;
  }

  function validateInspectionSession(value, validators) {
    if (value === null) return null;
    if (!isRecord(value) || value.enabled !== true) {
      throw new Error("backup inspection session must be enabled or null");
    }
    const record = validators.workspaceSession.validateRecord(value);
    if (record.workspace_json !== null) validators.workspacePackage.parse(record.workspace_json);
    return record;
  }

  function validateRegionalRecords(records, validators) {
    const normalized = validators.regionalLibrary.normalizeRecords(records);
    for (const record of normalized) {
      validators.regionalComparison.validatePackageInputs(
        record.package_json, record.package_bytes, validators.regionalInventory
      );
    }
    return normalized;
  }

  function create(input, validators) {
    requiredValidators(validators);
    if (!exactKeys(input, INPUT_KEYS)) throw new Error("Studio backup input has an invalid shape");
    const document = {
      document_type: DOCUMENT_TYPE,
      format_version: FORMAT_VERSION,
      created_at: timestamp(input.createdAt, "backup creation time"),
      world_brief_library: validators.worldBrief.parseLibrary(input.worldBriefLibrary),
      inspection_session: validateInspectionSession(input.inspectionSession, validators),
      terrain_comparisons: validators.terrainLibrary.normalizeRecords(input.terrainComparisons),
      regional_comparisons: validateRegionalRecords(input.regionalComparisons, validators),
    };
    const serialized = JSON.stringify(document);
    const maximumBytes = validators.maximumBytes || MAXIMUM_FILE_BYTES;
    if (byteLength(serialized) > maximumBytes) {
      throw new Error("Studio backup is larger than its supported file size");
    }
    return Object.freeze(document);
  }

  function parse(value, validators) {
    let document = value;
    if (typeof value === "string") {
      const maximumBytes = validators?.maximumBytes || MAXIMUM_FILE_BYTES;
      if (byteLength(value) > maximumBytes) throw new Error("Studio backup is larger than its supported file size");
      try { document = JSON.parse(value); }
      catch { throw new Error("Studio backup must contain valid JSON"); }
    }
    if (!exactKeys(document, ROOT_KEYS)) throw new Error("Studio backup has an unsupported document shape");
    if (document.document_type !== DOCUMENT_TYPE) throw new Error("file is not a Skyforge Studio backup");
    if (document.format_version !== FORMAT_VERSION) throw new Error("unsupported Studio backup version");
    return create({
      createdAt: document.created_at,
      worldBriefLibrary: document.world_brief_library,
      inspectionSession: document.inspection_session,
      terrainComparisons: document.terrain_comparisons,
      regionalComparisons: document.regional_comparisons,
    }, validators);
  }

  function mergeRecords(currentValue, importedValue, library, label) {
    const current = library.normalizeRecords(currentValue);
    const imported = library.normalizeRecords(importedValue);
    const merged = current.slice();
    const byId = new Map(current.map(record => [record.id, record]));
    let added = 0;
    for (const record of imported) {
      const existing = byId.get(record.id);
      if (existing) {
        if (JSON.stringify(existing) !== JSON.stringify(record)) {
          throw new Error(label + " backup contains a different saved item with the same ID");
        }
        continue;
      }
      merged.push(record);
      byId.set(record.id, record);
      added += 1;
    }
    return {records: library.normalizeRecords(merged), added};
  }

  function prepareRestore(importedValue, currentValue, validators, options = {}) {
    const imported = parse(importedValue, validators);
    const current = parse(currentValue, validators);
    const briefs = validators.worldBrief.mergeLibraries(
      current.world_brief_library, imported.world_brief_library
    );
    const terrain = mergeRecords(
      current.terrain_comparisons, imported.terrain_comparisons,
      validators.terrainLibrary, "terrain comparison"
    );
    const regional = mergeRecords(
      current.regional_comparisons, imported.regional_comparisons,
      {
        normalizeRecords: records => validateRegionalRecords(records, validators),
      }, "regional comparison"
    );
    const incomingSession = imported.inspection_session;
    const currentSession = current.inspection_session;
    const inspectionReplacementRequired = Boolean(incomingSession && currentSession &&
      JSON.stringify(incomingSession) !== JSON.stringify(currentSession));
    const inspectionSession = incomingSession && (
      !inspectionReplacementRequired || options.replaceInspection === true
    ) ? incomingSession : currentSession;

    return Object.freeze({
      worldBriefLibrary: briefs,
      inspectionSession,
      terrainComparisons: terrain.records,
      regionalComparisons: regional.records,
      inspectionReplacementRequired,
      counts: Object.freeze({
        briefsAdded: Math.max(0, briefs.briefs.length - current.world_brief_library.briefs.length),
        terrainComparisonsAdded: terrain.added,
        regionalComparisonsAdded: regional.added,
      }),
    });
  }

  const api = Object.freeze({
    create,
    parse,
    prepareRestore,
    documentType: DOCUMENT_TYPE,
    formatVersion: FORMAT_VERSION,
    maximumFileBytes: MAXIMUM_FILE_BYTES,
  });
  if (root && typeof root === "object") root.SkyforgeStudioProfileBackup = api;
  if (typeof module !== "undefined" && module.exports) module.exports = api;
})(typeof globalThis !== "undefined" ? globalThis : this);
