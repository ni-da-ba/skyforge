(function (root) {
  "use strict";
  const DOCUMENT_TYPE = "SKYFORGE_STUDIO_HYDROLOGY_COMPARISON_REPORT";
  const FORMAT_VERSION = 1;
  const SELECTED_FIELDS = new Set(["causes", "surface", "water-surface", "water-depth"]);
  function isRecord(value) { return value !== null && typeof value === "object" && !Array.isArray(value); }
  function stableJsonValue(value, ancestors = new Set()) {
    if (value === null || typeof value === "string" || typeof value === "boolean") return value;
    if (typeof value === "number") {
      if (!Number.isFinite(value)) throw new Error("comparison report cannot contain non-finite numbers");
      return value;
    }
    if (Array.isArray(value)) {
      if (ancestors.has(value)) throw new Error("comparison report cannot contain circular data");
      ancestors.add(value);
      const copy = value.map((entry) => stableJsonValue(entry, ancestors));
      ancestors.delete(value);
      return copy;
    }
    if (!isRecord(value)) throw new Error("comparison report contains unsupported data");
    if (ancestors.has(value)) throw new Error("comparison report cannot contain circular data");
    ancestors.add(value);
    const copy = {};
    for (const key of Object.keys(value).sort()) {
      if (value[key] === undefined) throw new Error("comparison report contains undefined data");
      copy[key] = stableJsonValue(value[key], ancestors);
    }
    ancestors.delete(value);
    return copy;
  }
  function compareGrid(left, right) { return left[1] - right[1] || left[0] - right[0]; }
  function createHydrologyComparisonReport(input) {
    if (!isRecord(input) || !isRecord(input.comparison) || !isRecord(input.reference) || !isRecord(input.candidate)) {
      throw new Error("comparison report requires a comparison and both source layers");
    }
    const comparison = input.comparison;
    const reference = input.reference;
    const candidate = input.candidate;
    if (comparison.sceneKind !== "HYDROLOGY_SEMANTIC_COMPARISON" ||
        reference.sceneKind !== "HYDROLOGY_SEMANTIC_LAYER" || candidate.sceneKind !== "HYDROLOGY_SEMANTIC_LAYER") {
      throw new Error("comparison report requires adapted hydrology semantic layers");
    }
    if (!reference.source || !candidate.source ||
        comparison.referenceSource !== reference.source || comparison.candidateSource !== candidate.source) {
      throw new Error("comparison report sources do not match the compared layers");
    }
    if (comparison.terrainSemanticSha256 !== reference.terrainSemanticSha256 ||
        comparison.terrainSemanticSha256 !== candidate.terrainSemanticSha256 ||
        comparison.associationToken !== reference.binding?.associationToken ||
        comparison.associationToken !== candidate.binding?.associationToken) {
      throw new Error("comparison report binding does not match the compared layers");
    }
    if (!Array.isArray(comparison.samples) || !Array.isArray(comparison.fieldSamples)) {
      throw new Error("comparison report samples are invalid");
    }
    const selectedField = input.selectedField;
    if (!SELECTED_FIELDS.has(selectedField)) throw new Error("comparison report selected field is invalid");
    const selectedCauseField = selectedField === "causes" ? input.selectedCauseField : null;
    if (selectedField === "causes" &&
        !["runoffPotential", "retentionPotential", "drainagePotential", "outflowPotential"].includes(selectedCauseField)) {
      throw new Error("comparison report selected cause field is invalid");
    }
    if (!reference.binding?.worldFrame || !reference.gridBinding) {
      throw new Error("comparison report is missing its exact world or grid binding");
    }
    const causeSamples = comparison.samples.slice().sort((a, b) => compareGrid(a.grid, b.grid));
    const fieldSamples = comparison.fieldSamples.slice().sort((a, b) => compareGrid(a.grid, b.grid));
    const sourceReviewAuthority = comparison.reviewAuthority === true &&
      reference.source.reviewAuthority === true && candidate.source.reviewAuthority === true;
    return Object.freeze({
      document_type: DOCUMENT_TYPE,
      format_version: FORMAT_VERSION,
      diagnostic_only: true,
      human_review_evidence: false,
      source_review_authority: sourceReviewAuthority,
      notice: "This report records a comparison for diagnosis. It is not independent human-review evidence.",
      binding: stableJsonValue({
        terrainSemanticSha256: comparison.terrainSemanticSha256,
        associationToken: comparison.associationToken,
        worldFrame: reference.binding.worldFrame,
        gridBinding: reference.gridBinding,
      }),
      sources: stableJsonValue({ reference: reference.source, candidate: candidate.source }),
      selected_field: selectedField,
      selected_cause_field: selectedCauseField,
      cause_samples: stableJsonValue(causeSamples),
      projected_field_samples: stableJsonValue(fieldSamples),
    });
  }
  function slug(value) {
    return String(value || "source").toLowerCase().replace(/[^a-z0-9]+/g, "-")
      .replace(/^-+|-+$/g, "").slice(0, 48) || "source";
  }
  function hydrologyComparisonReportFilename(report) {
    if (!isRecord(report) || !isRecord(report.sources) ||
        !report.sources.reference || !report.sources.candidate) {
      throw new Error("comparison report filename requires both source descriptors");
    }
    const reference = report.sources.reference;
    const candidate = report.sources.candidate;
    const referenceName = reference.artifactId || reference.artifactTitle || reference.artifactKind;
    const candidateName = candidate.artifactId || candidate.artifactTitle || candidate.artifactKind;
    return "skyforge-hydrology-comparison-" + slug(referenceName) + "-to-" +
      slug(candidateName) + ".json";
  }
  root.SkyforgeStudioComparisonReport = Object.freeze({
    create: createHydrologyComparisonReport, filename: hydrologyComparisonReportFilename,
    documentType: DOCUMENT_TYPE, formatVersion: FORMAT_VERSION,
  });
})(typeof window !== "undefined" ? window : globalThis);
