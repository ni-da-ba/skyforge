(function (root) {
  "use strict";
  const DOCUMENT_TYPE = "SKYFORGE_STUDIO_HYDROLOGY_COMPARISON_REPORT";
  const FORMAT_VERSION = 1;
  const MAX_FILE_BYTES = 32 * 1024 * 1024;
  const MAX_SAMPLE_COUNT = 100000;
  const PAGE_SIZE = 50;
  const SELECTED_FIELDS = new Set(["causes", "surface", "water-surface", "water-depth"]);
  const COVERAGE = new Set(["shared", "candidate-only", "reference-only"]);
  const WET_TRANSITIONS = new Set(["WET_ADDED", "WET_REMOVED"]);
  function isRecord(value) { return value !== null && typeof value === "object" && !Array.isArray(value); }
  function requiredRecord(value, label) {
    if (!isRecord(value)) throw new Error("comparison report " + label + " is invalid");
    return value;
  }
  function requiredText(value, label) {
    if (typeof value !== "string" || !value) throw new Error("comparison report " + label + " is invalid");
    return value;
  }
  function validateSource(source, label) {
    requiredRecord(source, label + " source");
    requiredText(source.binding, label + " source binding");
    requiredText(source.artifactKind, label + " source kind");
    if (typeof source.reviewAuthority !== "boolean") {
      throw new Error("comparison report " + label + " source authority claim is invalid");
    }
    for (const key of ["artifactId", "artifactTitle", "sourceSha", "artifactDigest"]) {
      if (source[key] !== null && typeof source[key] !== "string") {
        throw new Error("comparison report " + label + " source " + key + " is invalid");
      }
    }
  }
  function validateSamples(samples, kind) {
    if (!Array.isArray(samples) || samples.length > MAX_SAMPLE_COUNT) {
      throw new Error("comparison report " + kind + " sample count is invalid or too large");
    }
    for (const sample of samples) {
      requiredRecord(sample, kind + " sample");
      if (!Array.isArray(sample.grid) || sample.grid.length !== 2 ||
          sample.grid.some((value) => !Number.isFinite(value))) {
        throw new Error("comparison report " + kind + " sample grid is invalid");
      }
      if (kind === "cause") {
        requiredRecord(sample.reference, "cause reference values");
        requiredRecord(sample.candidate, "cause candidate values");
        requiredRecord(sample.deltas, "cause deltas");
        continue;
      }
      if (!COVERAGE.has(sample.coverage)) throw new Error("comparison report projected-field coverage is invalid");
      if (sample.reference !== null && !isRecord(sample.reference)) {
        throw new Error("comparison report projected-field reference values are invalid");
      }
      if (sample.candidate !== null && !isRecord(sample.candidate)) {
        throw new Error("comparison report projected-field candidate values are invalid");
      }
      for (const key of ["surfaceDeltaY", "waterSurfaceDeltaY", "waterDepthDelta"]) {
        if (sample[key] !== null && !Number.isFinite(sample[key])) {
          throw new Error("comparison report projected-field delta is invalid");
        }
      }
      if (sample.wetTransition !== null && !WET_TRANSITIONS.has(sample.wetTransition)) {
        throw new Error("comparison report wet transition is invalid");
      }
    }
  }
  function parseHydrologyComparisonReport(value, fileBytes) {
    if (typeof value !== "string") throw new Error("comparison report must be read as JSON text");
    const size = Number.isFinite(fileBytes) ? fileBytes : new TextEncoder().encode(value).length;
    if (size < 0 || size > MAX_FILE_BYTES) throw new Error("comparison report files must be 32 MB or smaller");
    let report;
    try {
      report = JSON.parse(value);
    } catch {
      throw new Error("comparison report is not valid JSON");
    }
    requiredRecord(report, "document");
    if (report.document_type !== DOCUMENT_TYPE) throw new Error("file is not a Skyforge Studio hydrology comparison report");
    if (report.format_version !== FORMAT_VERSION) throw new Error("unsupported comparison report format version");
    if (report.diagnostic_only !== true || report.human_review_evidence !== false) {
      throw new Error("comparison report must remain diagnostic and cannot claim human-review evidence");
    }
    if (typeof report.source_review_authority !== "boolean") {
      throw new Error("comparison report source authority claim is invalid");
    }
    const binding = requiredRecord(report.binding, "binding");
    requiredText(binding.terrainSemanticSha256, "terrain digest");
    requiredText(binding.associationToken, "AUTH-0046 association");
    requiredRecord(binding.worldFrame, "world frame");
    requiredRecord(binding.gridBinding, "grid binding");
    const sources = requiredRecord(report.sources, "sources");
    validateSource(sources.reference, "reference");
    validateSource(sources.candidate, "candidate");
    if (!SELECTED_FIELDS.has(report.selected_field)) throw new Error("comparison report selected field is invalid");
    if (report.selected_field === "causes" &&
        !["runoffPotential", "retentionPotential", "drainagePotential", "outflowPotential"].includes(report.selected_cause_field)) {
      throw new Error("comparison report selected cause field is invalid");
    }
    if (report.selected_field !== "causes" && report.selected_cause_field !== null) {
      throw new Error("comparison report selected cause field must be empty for this comparison");
    }
    requiredText(report.notice, "diagnostic notice");
    validateSamples(report.cause_samples, "cause");
    validateSamples(report.projected_field_samples, "projected-field");
    return Object.freeze({
      document: report,
      causeSampleCount: report.cause_samples.length,
      projectedFieldSampleCount: report.projected_field_samples.length,
      sourceReviewAuthorityRecorded: report.source_review_authority,
      sourceReviewAuthorityVerified: false,
      fileBytes: size,
    });
  }
  function getSamplePage(parsedReport, sampleKind, pageIndex) {
    if (!isRecord(parsedReport) || !isRecord(parsedReport.document)) {
      throw new Error("comparison report page requires a parsed report");
    }
    if (sampleKind !== "causes" && sampleKind !== "projected-fields") {
      throw new Error("comparison report sample set is invalid");
    }
    const samples = sampleKind === "causes"
      ? parsedReport.document.cause_samples
      : parsedReport.document.projected_field_samples;
    const total = samples.length;
    const pageCount = Math.max(1, Math.ceil(total / PAGE_SIZE));
    if (!Number.isInteger(pageIndex) || pageIndex < 0 || pageIndex >= pageCount) {
      throw new Error("comparison report page index is invalid");
    }
    const start = pageIndex * PAGE_SIZE;
    const items = samples.slice(start, start + PAGE_SIZE);
    return Object.freeze({
      items: Object.freeze(items),
      pageIndex,
      pageCount,
      total,
      firstIndex: total === 0 ? 0 : start + 1,
      lastIndex: start + items.length,
    });
  }
  root.SkyforgeStudioComparisonReportReader = Object.freeze({
    parse: parseHydrologyComparisonReport,
    page: getSamplePage,
    documentType: DOCUMENT_TYPE,
    formatVersion: FORMAT_VERSION,
    maximumFileBytes: MAX_FILE_BYTES,
    pageSize: PAGE_SIZE,
  });
})(typeof window !== "undefined" ? window : globalThis);
