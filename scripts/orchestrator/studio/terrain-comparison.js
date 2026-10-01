(function (root) {
  "use strict";

  const GRID_FIELDS = [
    "minimumX", "minimumY", "minimumZ",
    "spacingX", "spacingY", "spacingZ",
    "xSamples", "ySamples", "zSamples", "sampleCount",
  ];

  function requireScene(input, label) {
    const scene = input?.scene;
    if (scene?.sceneKind !== "TERRAIN_SEMANTIC_VOLUME" || !scene.terrain) {
      throw new Error(label + " must be a terrain semantic volume");
    }
    if (scene.source?.binding !== "UNBOUND_LOCAL" || scene.source.reviewAuthority !== false) {
      throw new Error(label + " must remain an unbound local diagnostic");
    }
    return scene;
  }

  function assertAligned(reference, candidate) {
    if (reference.coordinateSystem?.id !== candidate.coordinateSystem?.id) {
      throw new Error("terrain coordinate systems do not match; only exact world-grid comparisons are supported");
    }
    for (const field of GRID_FIELDS) {
      if (reference.terrain.grid[field] !== candidate.terrain.grid[field]) {
        throw new Error("terrain grids do not match exactly (" + field + "); choose outputs with identical dimensions, origin, and spacing");
      }
    }
  }

  function countNames(terrain) {
    const counts = new Map(terrain.legend.map(entry => [entry.name, 0]));
    for (const ordinal of terrain.semantics) {
      const name = terrain.legend[ordinal]?.name;
      if (!name) throw new Error("terrain semantic payload contains an unknown legend ordinal");
      counts.set(name, (counts.get(name) || 0) + 1);
    }
    return counts;
  }

  function indexTopSurface(terrain) {
    const columns = new Array(terrain.grid.xSamples * terrain.grid.zSamples).fill(null);
    for (const point of terrain.topSurface) {
      const [x, , z] = point.gridIndex;
      columns[z * terrain.grid.xSamples + x] = point;
    }
    return columns;
  }

  function compare(referenceInput, candidateInput) {
    const reference = requireScene(referenceInput, "reference");
    const candidate = requireScene(candidateInput, "candidate");
    assertAligned(reference, candidate);
    const grid = reference.terrain.grid;
    const referenceData = reference.terrain.semantics;
    const candidateData = candidate.terrain.semantics;
    if (referenceData.length !== grid.sampleCount || candidateData.length !== grid.sampleCount) {
      throw new Error("terrain semantic payload lengths do not match the aligned grid");
    }
    const referenceCounts = countNames(reference.terrain);
    const candidateCounts = countNames(candidate.terrain);
    const names = [...new Set([...referenceCounts.keys(), ...candidateCounts.keys()])].sort();
    const semanticCounts = names.map(name => Object.freeze({
      name,
      reference: referenceCounts.get(name) || 0,
      candidate: candidateCounts.get(name) || 0,
    }));
    const transitions = new Map();
    let changedCellCount = 0;
    for (let index = 0; index < grid.sampleCount; index++) {
      const before = reference.terrain.legend[referenceData[index]]?.name;
      const after = candidate.terrain.legend[candidateData[index]]?.name;
      if (!before || !after) throw new Error("terrain semantic payload contains an unknown legend ordinal");
      if (before !== after) {
        changedCellCount += 1;
        const key = before + "\u0000" + after;
        transitions.set(key, (transitions.get(key) || 0) + 1);
      }
    }
    const semanticTransitions = [...transitions].map(([key, count]) => {
      const [referenceName, candidateName] = key.split("\u0000");
      return Object.freeze({ referenceName, candidateName, count });
    }).sort((a, b) => b.count - a.count ||
      a.referenceName.localeCompare(b.referenceName) ||
      a.candidateName.localeCompare(b.candidateName));

    const referenceTop = indexTopSurface(reference.terrain);
    const candidateTop = indexTopSurface(candidate.terrain);
    const columns = [];
    let changedColumnCount = 0;
    let maximumAbsSurfaceDelta = 0;
    for (let z = 0; z < grid.zSamples; z++) {
      for (let x = 0; x < grid.xSamples; x++) {
        const index = z * grid.xSamples + x;
        const before = referenceTop[index];
        const after = candidateTop[index];
        let status = "UNCHANGED";
        let heightDelta = null;
        if (!before && after) status = "CANDIDATE_ONLY";
        else if (before && !after) status = "REFERENCE_ONLY";
        else if (before && after) {
          heightDelta = after.position[1] - before.position[1];
          if (heightDelta !== 0) status = "HEIGHT_CHANGED";
          else if (before.semanticName !== after.semanticName) status = "SEMANTIC_ONLY";
        }
        if (status !== "UNCHANGED") changedColumnCount += 1;
        if (Number.isFinite(heightDelta)) {
          maximumAbsSurfaceDelta = Math.max(maximumAbsSurfaceDelta, Math.abs(heightDelta));
        }
        columns.push(Object.freeze({
          x, z,
          worldX: grid.minimumX + grid.spacingX * x,
          worldZ: grid.minimumZ + grid.spacingZ * z,
          referenceTop: before,
          candidateTop: after,
          heightDelta,
          status,
        }));
      }
    }

    function pageChangedCells(pageIndex, pageSize = 30) {
      if (!Number.isInteger(pageIndex) || pageIndex < 0 ||
          !Number.isInteger(pageSize) || pageSize < 1 || pageSize > 200) {
        throw new Error("terrain comparison page is outside its supported range");
      }
      const start = pageIndex * pageSize;
      const end = Math.min(changedCellCount, start + pageSize);
      let changedBeforePage = 0;
      const items = [];
      for (let index = 0; index < grid.sampleCount && changedBeforePage < end; index++) {
        const beforeName = reference.terrain.legend[referenceData[index]].name;
        const afterName = candidate.terrain.legend[candidateData[index]].name;
        if (beforeName === afterName) continue;
        if (changedBeforePage >= start) {
          const x = index % grid.xSamples;
          const plane = Math.floor(index / grid.xSamples);
          const z = plane % grid.zSamples;
          const y = Math.floor(plane / grid.zSamples);
          items.push(Object.freeze({
            index,
            gridIndex: Object.freeze([x, y, z]),
            position: Object.freeze([
              grid.minimumX + grid.spacingX * x,
              grid.minimumY + grid.spacingY * y,
              grid.minimumZ + grid.spacingZ * z,
            ]),
            referenceName: beforeName,
            candidateName: afterName,
          }));
        }
        changedBeforePage += 1;
      }
      const pageCount = Math.ceil(changedCellCount / pageSize);
      return Object.freeze({
        items: Object.freeze(items),
        total: changedCellCount,
        firstIndex: changedCellCount ? start + 1 : 0,
        lastIndex: changedBeforePage,
        pageIndex,
        pageCount,
      });
    }

    return Object.freeze({
      grid,
      referenceTitle: String(referenceInput.title || reference.source.artifactTitle || "Reference terrain"),
      candidateTitle: String(candidateInput.title || candidate.source.artifactTitle || "Candidate terrain"),
      referenceSource: reference.source,
      candidateSource: candidate.source,
      changedCellCount,
      changedPercent: changedCellCount / grid.sampleCount * 100,
      semanticCounts: Object.freeze(semanticCounts),
      semanticTransitions: Object.freeze(semanticTransitions),
      changedColumnCount,
      maximumAbsSurfaceDelta,
      columns: Object.freeze(columns),
      pageChangedCells,
      reviewAuthority: false,
    });
  }

  const api = Object.freeze({ compare });
  if (root && typeof root === "object") root.SkyforgeStudioTerrainComparison = api;
  if (typeof module !== "undefined" && module.exports) module.exports = api;
})(typeof globalThis !== "undefined" ? globalThis : this);
