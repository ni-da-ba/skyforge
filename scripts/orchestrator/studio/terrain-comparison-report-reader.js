(function (root) {
  "use strict";
  const DOCUMENT_TYPE = "SKYFORGE_STUDIO_TERRAIN_COMPARISON_REPORT";
  const FORMAT_VERSION = 1;
  const MAX_FILE_BYTES = 32 * 1024 * 1024;
  const MAX_CHANGED_CELLS = 250000;
  const MAX_COLUMNS = 250000;
  const PAGE_SIZE = 50;
  const GRID_FIELDS = ["minimumX", "minimumY", "minimumZ", "spacingX", "spacingY", "spacingZ",
    "xSamples", "ySamples", "zSamples", "sampleCount"];

  function isRecord(value) { return value !== null && typeof value === "object" && !Array.isArray(value); }
  function requireRecord(value, label) {
    if (!isRecord(value)) throw new Error("terrain report " + label + " is invalid");
    return value;
  }
  function requireText(value, label) {
    if (typeof value !== "string" || value.length === 0) throw new Error("terrain report " + label + " is invalid");
    return value;
  }
  function finite(value, label) {
    if (typeof value !== "number" || !Number.isFinite(value)) throw new Error("terrain report " + label + " is invalid");
  }
  function validateSource(source, label) {
    requireRecord(source, label + " source");
    if (source.binding !== "UNBOUND_LOCAL" || source.reviewAuthority !== false) {
      throw new Error("terrain report " + label + " source must be unbound local diagnostic provenance");
    }
    for (const key of ["artifactId", "artifactTitle", "artifactKind", "sourceSha", "artifactDigest"]) {
      if (source[key] !== null && source[key] !== undefined && typeof source[key] !== "string") {
        throw new Error("terrain report " + label + " source " + key + " is invalid");
      }
    }
  }
  function validateGrid(grid) {
    requireRecord(grid, "grid");
    for (const field of GRID_FIELDS) finite(grid[field], "grid " + field);
    for (const field of ["spacingX", "spacingY", "spacingZ"]) {
      if (grid[field] <= 0) throw new Error("terrain report grid spacing is invalid");
    }
    for (const field of ["xSamples", "ySamples", "zSamples", "sampleCount"]) {
      if (!Number.isSafeInteger(grid[field]) || grid[field] < 1) throw new Error("terrain report grid dimensions are invalid");
    }
    if (grid.xSamples * grid.ySamples * grid.zSamples !== grid.sampleCount) {
      throw new Error("terrain report grid sample count does not match its dimensions");
    }
  }
  function validateTop(top, label) {
    if (top === null) return;
    requireRecord(top, label);
    requireText(top.semanticName, label + " semantic");
    if (!Array.isArray(top.position) || top.position.length !== 3) throw new Error("terrain report " + label + " position is invalid");
    top.position.forEach((value, index) => finite(value, label + " position " + index));
    if (!Array.isArray(top.gridIndex) || top.gridIndex.length !== 3) throw new Error("terrain report " + label + " grid index is invalid");
    top.gridIndex.forEach((value, index) => {
      if (!Number.isSafeInteger(value) || value < 0) throw new Error("terrain report " + label + " grid index " + index + " is invalid");
    });
  }
  function validateColumns(columns, grid) {
    const expectedCount = grid.xSamples * grid.zSamples;
    if (!Array.isArray(columns) || columns.length > MAX_COLUMNS || columns.length !== expectedCount) {
      throw new Error("terrain report surface column count is invalid or incomplete");
    }
    const seen = new Set();
    let changedColumnCount = 0;
    let maximumAbsSurfaceDelta = 0;
    for (const column of columns) {
      requireRecord(column, "surface column");
      for (const field of ["x", "z"]) {
        if (!Number.isSafeInteger(column[field]) || column[field] < 0 ||
            column[field] >= (field === "x" ? grid.xSamples : grid.zSamples)) {
          throw new Error("terrain report surface column coordinate is invalid");
        }
      }
      const key = column.x + ":" + column.z;
      if (seen.has(key)) throw new Error("terrain report surface columns contain duplicates");
      seen.add(key);
      const expectedWorldX = grid.minimumX + grid.spacingX * column.x;
      const expectedWorldZ = grid.minimumZ + grid.spacingZ * column.z;
      if (column.worldX !== expectedWorldX || column.worldZ !== expectedWorldZ) {
        throw new Error("terrain report surface world position does not match its grid");
      }
      validateTop(column.referenceTop, "reference top");
      validateTop(column.candidateTop, "candidate top");
      const before = column.referenceTop;
      const after = column.candidateTop;
      let expectedStatus = "UNCHANGED";
      let expectedDelta = null;
      if (!before && after) expectedStatus = "CANDIDATE_ONLY";
      else if (before && !after) expectedStatus = "REFERENCE_ONLY";
      else if (before && after) {
        expectedDelta = after.position[1] - before.position[1];
        if (expectedDelta !== 0) expectedStatus = "HEIGHT_CHANGED";
        else if (before.semanticName !== after.semanticName) expectedStatus = "SEMANTIC_ONLY";
      }
      if (column.heightDelta !== expectedDelta || column.status !== expectedStatus) {
        throw new Error("terrain report surface status or height delta does not match its recorded values");
      }
      if (expectedStatus !== "UNCHANGED") changedColumnCount++;
      if (expectedDelta !== null) maximumAbsSurfaceDelta = Math.max(maximumAbsSurfaceDelta, Math.abs(expectedDelta));
    }
    return { changedColumnCount, maximumAbsSurfaceDelta };
  }
  function validateCells(cells, grid) {
    if (!Array.isArray(cells) || cells.length > MAX_CHANGED_CELLS) {
      throw new Error("terrain report changed-cell count is invalid or too large");
    }
    let priorIndex = -1;
    for (const cell of cells) {
      requireRecord(cell, "changed cell");
      if (!Number.isSafeInteger(cell.index) || cell.index <= priorIndex || cell.index >= grid.sampleCount) {
        throw new Error("terrain report changed-cell indices are invalid");
      }
      priorIndex = cell.index;
      if (!Array.isArray(cell.gridIndex) || cell.gridIndex.length !== 3 ||
          !Array.isArray(cell.position) || cell.position.length !== 3) {
        throw new Error("terrain report changed-cell coordinates are invalid");
      }
      const expectedIndex = [
        cell.index % grid.xSamples,
        Math.floor(cell.index / (grid.xSamples * grid.zSamples)),
        Math.floor(cell.index / grid.xSamples) % grid.zSamples,
      ];
      cell.gridIndex.forEach((value, i) => {
        if (!Number.isSafeInteger(value) || value !== expectedIndex[i] ||
            value >= [grid.xSamples, grid.ySamples, grid.zSamples][i]) {
          throw new Error("terrain report changed-cell grid index is inconsistent with its linear index");
        }
      });
      const expectedPosition = [
        grid.minimumX + grid.spacingX * expectedIndex[0],
        grid.minimumY + grid.spacingY * expectedIndex[1],
        grid.minimumZ + grid.spacingZ * expectedIndex[2],
      ];
      cell.position.forEach((value, i) => {
        finite(value, "changed-cell position " + i);
        if (value !== expectedPosition[i]) throw new Error("terrain report changed-cell world position does not match its grid");
      });
      requireText(cell.referenceName, "reference semantic");
      requireText(cell.candidateName, "candidate semantic");
      if (cell.referenceName === cell.candidateName) throw new Error("terrain report contains an unchanged cell");
    }
  }
  function parse(value, fileBytes) {
    if (typeof value !== "string") throw new Error("terrain report must be read as JSON text");
    const size = Number.isFinite(fileBytes) ? fileBytes : new TextEncoder().encode(value).length;
    if (size < 0 || size > MAX_FILE_BYTES) throw new Error("terrain report files must be 32 MB or smaller");
    let report;
    try { report = JSON.parse(value); } catch { throw new Error("terrain report is not valid JSON"); }
    requireRecord(report, "document");
    if (report.document_type !== DOCUMENT_TYPE) throw new Error("file is not a Skyforge Studio terrain comparison report");
    if (report.format_version !== FORMAT_VERSION) throw new Error("unsupported terrain report format version");
    if (report.diagnostic_only !== true || report.human_review_evidence !== false ||
        report.review_authority !== false || report.provenance_verified !== false) {
      throw new Error("terrain report must remain diagnostic and cannot claim verified authority or review evidence");
    }
    requireText(report.notice, "notice");
    const sources = requireRecord(report.sources, "sources");
    validateSource(sources.reference, "reference");
    validateSource(sources.candidate, "candidate");
    requireText(sources.referenceTitle, "reference title");
    requireText(sources.candidateTitle, "candidate title");
    const grid = report.grid;
    validateGrid(grid);
    const summary = requireRecord(report.summary, "summary");
    for (const field of ["changedCellCount", "changedColumnCount"]) {
      if (!Number.isSafeInteger(summary[field]) || summary[field] < 0 || summary[field] > grid.sampleCount) {
        throw new Error("terrain report summary " + field + " is invalid");
      }
    }
    finite(summary.changedPercent, "summary changed percent");
    finite(summary.maximumAbsSurfaceDelta, "summary maximum surface delta");
    if (summary.changedPercent < 0 || summary.changedPercent > 100 || summary.maximumAbsSurfaceDelta < 0) {
      throw new Error("terrain report summary values are outside their valid ranges");
    }
    const counts = report.semantic_counts;
    if (!Array.isArray(counts)) throw new Error("terrain report semantic counts are invalid");
    const semanticNames = new Set();
    let referenceTotal = 0;
    let candidateTotal = 0;
    for (const item of counts) {
      requireRecord(item, "semantic count");
      requireText(item.name, "semantic name");
      if (semanticNames.has(item.name)) throw new Error("terrain report semantic counts contain duplicate names");
      semanticNames.add(item.name);
      for (const field of ["reference", "candidate"]) {
        if (!Number.isSafeInteger(item[field]) || item[field] < 0 || item[field] > grid.sampleCount) {
          throw new Error("terrain report semantic count value is invalid");
        }
      }
      referenceTotal += item.reference;
      candidateTotal += item.candidate;
    }
    if (referenceTotal !== grid.sampleCount || candidateTotal !== grid.sampleCount) {
      throw new Error("terrain report semantic totals do not match the grid sample count");
    }
    const transitions = report.semantic_transitions;
    if (!Array.isArray(transitions)) throw new Error("terrain report semantic transitions are invalid");
    const expectedTransitions = new Map();
    for (const item of transitions) {
      requireRecord(item, "semantic transition");
      requireText(item.referenceName, "transition reference");
      requireText(item.candidateName, "transition candidate");
      if (!semanticNames.has(item.referenceName) || !semanticNames.has(item.candidateName)) {
        throw new Error("terrain report transition refers to an unknown semantic name");
      }
      if (!Number.isSafeInteger(item.count) || item.count < 1 || item.count > grid.sampleCount) {
        throw new Error("terrain report transition count is invalid");
      }
      const key = JSON.stringify([item.referenceName, item.candidateName]);
      if (expectedTransitions.has(key)) throw new Error("terrain report contains duplicate semantic transitions");
      expectedTransitions.set(key, item.count);
    }
    const columnSummary = validateColumns(report.columns, grid);
    validateCells(report.changed_cells, grid);
    if (report.summary.changedCellCount !== report.changed_cells.length) {
      throw new Error("terrain report changed-cell summary does not match its records");
    }
    if (summary.changedPercent !== summary.changedCellCount / grid.sampleCount * 100) {
      throw new Error("terrain report changed percentage does not match its changed-cell count");
    }
    if (summary.changedColumnCount !== columnSummary.changedColumnCount ||
        summary.maximumAbsSurfaceDelta !== columnSummary.maximumAbsSurfaceDelta) {
      throw new Error("terrain report surface summary does not match its columns");
    }
    const actualTransitions = new Map();
    for (const cell of report.changed_cells) {
      const key = JSON.stringify([cell.referenceName, cell.candidateName]);
      actualTransitions.set(key, (actualTransitions.get(key) || 0) + 1);
    }
    if (actualTransitions.size !== expectedTransitions.size ||
        [...expectedTransitions].some(([key, count]) => actualTransitions.get(key) !== count)) {
      throw new Error("terrain report semantic transitions do not match its changed cells");
    }
    const comparison = Object.freeze({
      grid,
      referenceTitle: sources.referenceTitle,
      candidateTitle: sources.candidateTitle,
      referenceSource: sources.reference,
      candidateSource: sources.candidate,
      reviewAuthority: false,
      changedCellCount: summary.changedCellCount,
      changedPercent: summary.changedPercent,
      changedColumnCount: summary.changedColumnCount,
      maximumAbsSurfaceDelta: summary.maximumAbsSurfaceDelta,
      semanticCounts: Object.freeze(counts),
      semanticTransitions: Object.freeze(transitions),
      columns: Object.freeze(report.columns),
      pageChangedCells(pageIndex, pageSize = PAGE_SIZE) {
        if (!Number.isInteger(pageIndex) || pageIndex < 0 ||
            !Number.isInteger(pageSize) || pageSize < 1 || pageSize > 200) {
          throw new Error("terrain comparison page is outside its supported range");
        }
        const pageCount = Math.max(1, Math.ceil(report.changed_cells.length / pageSize));
        if (pageIndex >= pageCount) throw new Error("terrain comparison page is outside its supported range");
        const start = pageIndex * pageSize;
        const items = report.changed_cells.slice(start, start + pageSize);
        return Object.freeze({
          items: Object.freeze(items), total: report.changed_cells.length,
          firstIndex: report.changed_cells.length ? start + 1 : 0,
          lastIndex: start + items.length, pageIndex, pageCount,
        });
      },
    });
    return Object.freeze({ document: report, comparison, fileBytes: size, sourceProvenanceVerified: false });
  }
  root.SkyforgeStudioTerrainComparisonReportReader = Object.freeze({
    parse, documentType: DOCUMENT_TYPE, formatVersion: FORMAT_VERSION,
    maximumFileBytes: MAX_FILE_BYTES, pageSize: PAGE_SIZE,
  });
  if (typeof module !== "undefined" && module.exports) module.exports = root.SkyforgeStudioTerrainComparisonReportReader;
})(typeof window !== "undefined" ? window : globalThis);
