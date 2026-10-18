(function attachHydrologyRunSummary(root) {
  "use strict";

  const CAUSE_FIELDS = Object.freeze([
    "runoffPotential",
    "retentionPotential",
    "drainagePotential",
    "outflowPotential",
  ]);

  function observedRange(records, property) {
    let minimum = Infinity;
    let maximum = -Infinity;
    for (const record of records) {
      const value = record?.[property];
      if (!Number.isFinite(value)) continue;
      minimum = Math.min(minimum, value);
      maximum = Math.max(maximum, value);
    }
    return Number.isFinite(minimum) && Number.isFinite(maximum)
      ? Object.freeze({ minimum, maximum })
      : null;
  }

  function summarize(scene, overlay) {
    if (
      scene?.sceneKind !== "TERRAIN_SEMANTIC_VOLUME" ||
      overlay?.sceneKind !== "HYDROLOGY_SEMANTIC_LAYER"
    ) {
      return null;
    }

    const causeSamples = Array.isArray(overlay.causeSamples) ? overlay.causeSamples : [];
    const fieldSamples = Array.isArray(overlay.fieldSamples) ? overlay.fieldSamples : [];
    const reaches = Array.isArray(overlay.reaches) ? overlay.reaches : [];
    const grid = scene.terrain.grid;
    const causeGrid = overlay.gridBinding;
    const potentialRanges = {};
    for (const field of CAUSE_FIELDS) {
      potentialRanges[field] = observedRange(causeSamples, field);
    }

    return Object.freeze({
      terrainGrid: Object.freeze({
        xSamples: grid.xSamples,
        ySamples: grid.ySamples,
        zSamples: grid.zSamples,
      }),
      causeGrid: Object.freeze({
        xSamples: causeGrid.xSamples,
        zSamples: causeGrid.zSamples,
        stride: causeGrid.causeStride,
        sampleCount: causeGrid.causeSampleCount,
      }),
      causeSampleCount: causeSamples.length,
      terrainResponseSampleCount: fieldSamples.length,
      wetResponseSampleCount: fieldSamples.filter(sample => sample.wet === true).length,
      waterSurfaceSampleCount: fieldSamples.filter(sample =>
        Number.isFinite(sample.waterSurfaceY)
      ).length,
      reachCount: reaches.length,
      reachPointCount: reaches.reduce((count, reach) =>
        count + (Array.isArray(reach.points) ? reach.points.length : 0), 0),
      potentialRanges: Object.freeze(potentialRanges),
      terrainDeltaWorldRange: observedRange(fieldSamples, "terrainDeltaWorld"),
    });
  }

  const api = Object.freeze({ summarize });
  root.SkyforgeStudioHydrologyRunSummary = api;
  if (typeof module !== "undefined" && module.exports) module.exports = api;
})(typeof window !== "undefined" ? window : globalThis);
