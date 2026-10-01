(function (root) {
  "use strict";
  const DOCUMENT_TYPE = "SKYFORGE_STUDIO_TERRAIN_COMPARISON_REPORT";
  const FORMAT_VERSION = 1;
  const MAX_CHANGED_CELLS = 25000;
  const MAX_COLUMNS = 50000;
  const MAX_REPORT_BYTES = 32 * 1024 * 1024;
  const REPORT_PAGE_SIZE = 200;

  function isRecord(value) {
    return value !== null && typeof value === "object" && !Array.isArray(value);
  }

  function stableValue(value, ancestors = new Set()) {
    if (value === null || typeof value === "string" || typeof value === "boolean") return value;
    if (typeof value === "number") {
      if (!Number.isFinite(value)) throw new Error("terrain report cannot contain non-finite numbers");
      return value;
    }
    if (Array.isArray(value)) {
      if (ancestors.has(value)) throw new Error("terrain report cannot contain circular data");
      ancestors.add(value);
      const copy = value.map(entry => stableValue(entry, ancestors));
      ancestors.delete(value);
      return copy;
    }
    if (!isRecord(value)) throw new Error("terrain report contains unsupported data");
    if (ancestors.has(value)) throw new Error("terrain report cannot contain circular data");
    ancestors.add(value);
    const copy = {};
    for (const key of Object.keys(value).sort()) {
      if (value[key] === undefined) throw new Error("terrain report contains undefined data");
      copy[key] = stableValue(value[key], ancestors);
    }
    ancestors.delete(value);
    return copy;
  }

  function requireLocalSource(source, label) {
    if (!isRecord(source) || source.binding !== "UNBOUND_LOCAL" || source.reviewAuthority !== false) {
      throw new Error(label + " source must remain an unbound local diagnostic");
    }
  }

  function create(comparison) {
    if (!isRecord(comparison) || comparison.reviewAuthority !== false ||
        !isRecord(comparison.grid) || !comparison.referenceSource || !comparison.candidateSource) {
      throw new Error("terrain report requires a diagnostic comparison with both source descriptors");
    }
    requireLocalSource(comparison.referenceSource, "reference");
    requireLocalSource(comparison.candidateSource, "candidate");
    if (!Array.isArray(comparison.columns) || comparison.columns.length > MAX_COLUMNS) {
      throw new Error("terrain comparison is too large to include its surface map in a portable report");
    }
    if (!Number.isInteger(comparison.changedCellCount) ||
        comparison.changedCellCount < 0 || comparison.changedCellCount > MAX_CHANGED_CELLS) {
      throw new Error("terrain comparison has too many changed cells for a portable report");
    }
    const changedCells = [];
    const pageCount = Math.ceil(comparison.changedCellCount / REPORT_PAGE_SIZE);
    for (let pageIndex = 0; pageIndex < pageCount; pageIndex++) {
      const page = comparison.pageChangedCells(pageIndex, REPORT_PAGE_SIZE);
      changedCells.push(...page.items);
    }
    if (changedCells.length !== comparison.changedCellCount) {
      throw new Error("terrain comparison changed-cell list is incomplete");
    }
    const document = {
      document_type: DOCUMENT_TYPE,
      format_version: FORMAT_VERSION,
      diagnostic_only: true,
      human_review_evidence: false,
      review_authority: false,
      provenance_verified: false,
      notice: "This saved comparison is a local diagnostic. Source digests are recorded provenance only and are not verified.",
      sources: stableValue({
        reference: comparison.referenceSource,
        candidate: comparison.candidateSource,
        referenceTitle: comparison.referenceTitle,
        candidateTitle: comparison.candidateTitle,
      }),
      grid: stableValue(comparison.grid),
      summary: stableValue({
        changedCellCount: comparison.changedCellCount,
        changedPercent: comparison.changedPercent,
        changedColumnCount: comparison.changedColumnCount,
        maximumAbsSurfaceDelta: comparison.maximumAbsSurfaceDelta,
      }),
      semantic_counts: stableValue(comparison.semanticCounts),
      semantic_transitions: stableValue(comparison.semanticTransitions),
      columns: stableValue(comparison.columns),
      changed_cells: stableValue(changedCells),
    };
    return Object.freeze(document);
  }

  function stringify(document) {
    const text = JSON.stringify(stableValue(document), null, 2) + "\n";
    if (new TextEncoder().encode(text).length > MAX_REPORT_BYTES) {
      throw new Error("portable terrain reports must be 32 MB or smaller");
    }
    return text;
  }

  function slug(value) {
    return String(value || "terrain").toLowerCase().replace(/[^a-z0-9]+/g, "-")
      .replace(/^-+|-+$/g, "").slice(0, 48) || "terrain";
  }

  function filename(document) {
    if (!isRecord(document) || !isRecord(document.sources)) {
      throw new Error("terrain report filename requires both source descriptors");
    }
    return "skyforge-terrain-comparison-" +
      slug(document.sources.referenceTitle) + "-to-" +
      slug(document.sources.candidateTitle) + ".json";
  }

  root.SkyforgeStudioTerrainComparisonReport = Object.freeze({
    create, stringify, filename, documentType: DOCUMENT_TYPE, formatVersion: FORMAT_VERSION,
    maximumChangedCells: MAX_CHANGED_CELLS, maximumColumns: MAX_COLUMNS, maximumFileBytes: MAX_REPORT_BYTES, pageSize: REPORT_PAGE_SIZE,
  });
  if (typeof module !== "undefined" && module.exports) module.exports = root.SkyforgeStudioTerrainComparisonReport;
})(typeof window !== "undefined" ? window : globalThis);
