(function (root) {
  "use strict";

  const DOCUMENT_TYPE = "SKYFORGE_STUDIO_SELECTED_SAMPLE_TRACE";
  const FORMAT_VERSION = 1;
  const MAX_FILE_BYTES = 1_000_000;
  const ROOT_FIELDS = new Set([
    "document_type",
    "format_version",
    "review_authority",
    "scene_kind",
    "sources",
    "coordinate_system",
    "provider",
    "overlay",
    "comparison",
    "selected_sample",
  ]);
  const SOURCE_ROLES = ["scene", "overlay", "reference", "candidate"];
  const SOURCE_FIELDS = new Set([
    "binding",
    "artifactId",
    "artifactTitle",
    "sourceSha",
    "reviewAuthority",
    "artifactKind",
    "artifactDigest",
  ]);

  function isRecord(value) {
    return value !== null && typeof value === "object" && !Array.isArray(value);
  }

  function assertOnlyFields(value, allowed, label) {
    if (Object.keys(value).some((key) => !allowed.has(key))) {
      throw new Error(label + " contains unsupported fields");
    }
  }

  function assertSource(source, label) {
    if (source === null) return;
    if (!isRecord(source)) throw new Error(label + " must be an object or null");
    assertOnlyFields(source, SOURCE_FIELDS, label);
    for (const key of SOURCE_FIELDS) {
      if (!Object.prototype.hasOwnProperty.call(source, key)) {
        throw new Error(label + " is missing " + key);
      }
    }
    for (const key of ["binding", "artifactId", "artifactTitle", "sourceSha", "artifactKind", "artifactDigest"]) {
      if (source[key] !== null && typeof source[key] !== "string") {
        throw new Error(label + " " + key + " must be text or null");
      }
    }
    if (typeof source.reviewAuthority !== "boolean") {
      throw new Error(label + " reviewAuthority must be boolean");
    }
  }

  function assertOptionalRecord(value, label) {
    if (value !== null && !isRecord(value)) {
      throw new Error(label + " must be an object or null");
    }
  }

  function parseSelectedSampleTrace(input) {
    if (typeof input !== "string") throw new Error("sample trace must be JSON text");
    if (input.length > MAX_FILE_BYTES) throw new Error("sample trace files must be smaller than 1 MB");

    let document;
    try {
      document = JSON.parse(input);
    } catch {
      throw new Error("sample trace must contain valid JSON");
    }
    if (!isRecord(document)) throw new Error("sample trace must be a JSON object");
    if (document.document_type !== DOCUMENT_TYPE) {
      throw new Error("file is not a selected sample trace");
    }
    if (document.format_version !== FORMAT_VERSION) {
      throw new Error("unsupported sample trace format version");
    }
    assertOnlyFields(document, ROOT_FIELDS, "sample trace");
    if (typeof document.review_authority !== "boolean") {
      throw new Error("sample trace review_authority must be boolean");
    }
    if (document.scene_kind !== null && typeof document.scene_kind !== "string") {
      throw new Error("sample trace scene_kind must be text or null");
    }
    if (!isRecord(document.sources)) throw new Error("sample trace sources must be an object");
    const roleFields = new Set(SOURCE_ROLES);
    assertOnlyFields(document.sources, roleFields, "sample trace sources");
    for (const role of SOURCE_ROLES) {
      if (!Object.prototype.hasOwnProperty.call(document.sources, role)) {
        throw new Error("sample trace sources is missing " + role);
      }
      assertSource(document.sources[role], "sample trace " + role + " source");
    }
    assertOptionalRecord(document.coordinate_system, "sample trace coordinate_system");
    assertOptionalRecord(document.provider, "sample trace provider");
    assertOptionalRecord(document.overlay, "sample trace overlay");
    assertOptionalRecord(document.comparison, "sample trace comparison");
    if (!isRecord(document.selected_sample)) {
      throw new Error("sample trace selected_sample must be an object");
    }
    return document;
  }

  const api = Object.freeze({
    parse: parseSelectedSampleTrace,
    documentType: DOCUMENT_TYPE,
    formatVersion: FORMAT_VERSION,
    maximumFileBytes: MAX_FILE_BYTES,
  });

  if (root && typeof root === "object") {
    root.SkyforgeStudioTraceViewer = api;
  }
  if (typeof module !== "undefined" && module.exports) {
    module.exports = api;
  }
})(typeof globalThis !== "undefined" ? globalThis : this);
