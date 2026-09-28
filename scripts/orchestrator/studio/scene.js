(() => {
  "use strict";

  const ATMOSPHERE_KIND = "SKYFORGE_ATMOSPHERE_PROBE_VOLUME";
  const TERRAIN_KIND = "SKYFORGE_TERRAIN_SEMANTIC_VOLUME";

  function finite(value, label) {
    const number = Number(value);
    if (!Number.isFinite(number)) throw new Error(label + " must be finite");
    return number;
  }

  function positiveInteger(value, label) {
    const number = Number(value);
    if (!Number.isSafeInteger(number) || number <= 0) {
      throw new Error(label + " must be a positive integer");
    }
    return number;
  }

  function vector(value, label) {
    if (!Array.isArray(value) || value.length !== 3) {
      throw new Error(label + " must be a three-component vector");
    }
    return value.map((component, index) => finite(component, label + "[" + index + "]"));
  }

  function authoritySource(artifactKind, digest, context = {}) {
    return Object.freeze({
      binding: String(context.binding || "UNBOUND_LOCAL"),
      artifactId: context.artifactId ? String(context.artifactId) : null,
      artifactTitle: context.artifactTitle ? String(context.artifactTitle) : null,
      sourceSha: context.sourceSha ? String(context.sourceSha) : null,
      reviewAuthority: context.reviewAuthority === true,
      artifactKind,
      artifactDigest: String(digest || ""),
    });
  }

  function atmosphereSample(record, index) {
    if (!record || typeof record !== "object") {
      throw new Error("sample " + index + " is not an object");
    }
    return Object.freeze({
      position: vector(record.position, "sample " + index + " position"),
      mean: vector(record.mean, "sample " + index + " mean"),
      gust: vector(record.gust, "sample " + index + " gust"),
      effective: vector(record.effective, "sample " + index + " effective"),
      verticalAir: finite(record.signed_vertical_air, "sample " + index + " vertical air"),
      turbulence: finite(record.turbulence, "sample " + index + " turbulence"),
      shear: finite(record.shear, "sample " + index + " shear"),
      confidence: finite(record.confidence, "sample " + index + " confidence"),
      trusted: record.trusted_for_gameplay === true,
      sourceLevel: String(record.source_level || ""),
      authority: String(record.authority || ""),
    });
  }

  function atmosphereFrame(record, label) {
    if (!record || !Array.isArray(record.samples) || !record.samples.length) {
      throw new Error(label + " has no samples");
    }
    return Object.freeze({
      tick: record.game_tick === undefined ? null : finite(record.game_tick, label + " tick"),
      samples: Object.freeze(
        record.samples.map((entry, index) => atmosphereSample(entry, index))
      ),
    });
  }

  function atmosphereProbeVolume(artifact, context = {}) {
    if (!artifact || typeof artifact !== "object") throw new Error("artifact must be an object");
    if (artifact.schema_version !== 1) {
      throw new Error("unsupported atmosphere schema_version " + String(artifact.schema_version));
    }
    if (artifact.artifact_kind !== ATMOSPHERE_KIND) {
      throw new Error("unsupported artifact_kind " + String(artifact.artifact_kind || ""));
    }
    if (!Array.isArray(artifact.samples) || !artifact.samples.length) {
      throw new Error("atmosphere artifact has no spatial samples");
    }

    const provider = artifact.provider_identity || {};
    const specimen = artifact.specimen || {};
    const snapshot = Object.freeze({
      tick: specimen.acquisition_game_tick === undefined
        ? null
        : finite(specimen.acquisition_game_tick, "snapshot acquisition tick"),
      samples: Object.freeze(
        artifact.samples.map((entry, index) => atmosphereSample(entry, index))
      ),
    });
    const opportunity = artifact.opportunity_scan || {};
    const opportunityFrames = Array.isArray(opportunity.frames)
      ? Object.freeze(
          opportunity.frames.map(
            (frame, index) => atmosphereFrame(frame, "opportunity frame " + index)
          )
        )
      : Object.freeze([]);

    return Object.freeze({
      schemaVersion: 1,
      sceneKind: "ATMOSPHERE_VECTOR_FIELD",
      coordinateSystem: Object.freeze({
        id: "MINECRAFT_WORLD_XYZ",
        x: "+X world blocks",
        y: "+Y altitude",
        z: "+Z world blocks",
      }),
      source: authoritySource(
        ATMOSPHERE_KIND,
        artifact.ordered_sample_digest,
        context
      ),
      provider: Object.freeze({
        label: "Aerodynamics4MC",
        modId: String(provider.mod_id || ""),
        version: String(provider.version || ""),
        api: String(provider.api || ""),
      }),
      layers: Object.freeze([
        Object.freeze({
          id: "effective",
          primitive: "VectorField",
          title: "Effective wind",
          units: "m/s",
          semanticOwner: "Aerodynamics4MC",
          vectorProperty: "effective",
        }),
        Object.freeze({
          id: "mean",
          primitive: "VectorField",
          title: "Mean wind",
          units: "m/s",
          semanticOwner: "Aerodynamics4MC",
          vectorProperty: "mean",
        }),
        Object.freeze({
          id: "gust",
          primitive: "VectorField",
          title: "Gust contribution",
          units: "m/s",
          semanticOwner: "Aerodynamics4MC",
          vectorProperty: "gust",
        }),
        Object.freeze({
          id: "samples",
          primitive: "PointSet",
          title: "Atmosphere samples",
          units: "world blocks",
          semanticOwner: "Aerodynamics4MC",
        }),
      ]),
      snapshot,
      opportunity: Object.freeze({
        periodTicks: opportunity.period_ticks === undefined
          ? null
          : finite(opportunity.period_ticks, "opportunity period"),
        frames: opportunityFrames,
      }),
      ownership: Object.freeze({
        semanticOwner: "Aerodynamics4MC gameplay provider",
        persistenceOwner: String(
          artifact.ownership?.provider_persistence_owner || "aerodynamics4mc"
        ),
        backendNeutral: artifact.ownership?.rendering_backend_dependency !== true,
        renderingBackendDependency:
          artifact.ownership?.rendering_backend_dependency === true,
        skyforgePersistsTruth:
          artifact.ownership?.skyforge_persists_atmosphere === true,
      }),
    });
  }

  function decodeBase64(value) {
    if (typeof value !== "string" || !value.length) {
      throw new Error("terrain semantics_base64 is required");
    }
    let binary;
    try {
      binary = atob(value);
    } catch (error) {
      throw new Error("terrain semantics_base64 is invalid");
    }
    const bytes = new Uint8Array(binary.length);
    for (let index = 0; index < binary.length; index++) {
      bytes[index] = binary.charCodeAt(index);
    }
    return bytes;
  }

  function terrainGrid(record) {
    if (!record || typeof record !== "object") {
      throw new Error("terrain grid is required");
    }
    const grid = {
      minimumX: finite(record.minimum_x, "grid minimum_x"),
      minimumY: finite(record.minimum_y, "grid minimum_y"),
      minimumZ: finite(record.minimum_z, "grid minimum_z"),
      spacingX: finite(record.spacing_x, "grid spacing_x"),
      spacingY: finite(record.spacing_y, "grid spacing_y"),
      spacingZ: finite(record.spacing_z, "grid spacing_z"),
      xSamples: positiveInteger(record.x_samples, "grid x_samples"),
      ySamples: positiveInteger(record.y_samples, "grid y_samples"),
      zSamples: positiveInteger(record.z_samples, "grid z_samples"),
      sampleCount: positiveInteger(record.sample_count, "grid sample_count"),
    };
    if (grid.spacingX <= 0 || grid.spacingY <= 0 || grid.spacingZ <= 0) {
      throw new Error("terrain grid spacing must be positive");
    }
    const expected = grid.xSamples * grid.ySamples * grid.zSamples;
    if (!Number.isSafeInteger(expected) || expected !== grid.sampleCount) {
      throw new Error("terrain grid sample_count does not match dimensions");
    }
    return Object.freeze(grid);
  }

  function terrainLegend(value) {
    if (!Array.isArray(value) || !value.length) {
      throw new Error("terrain semantic_legend is required");
    }
    const legend = value.map((entry, index) => {
      if (!entry || Number(entry.ordinal) !== index) {
        throw new Error("terrain semantic legend ordinals must be contiguous");
      }
      const name = String(entry.name || "");
      if (!name) throw new Error("terrain semantic legend name is required");
      return Object.freeze({
        ordinal: index,
        name,
        solid: entry.solid === true,
      });
    });
    if (legend[0]?.name !== "AIR" || legend[0]?.solid) {
      throw new Error("terrain semantic ordinal zero must be non-solid AIR");
    }
    return Object.freeze(legend);
  }

  function terrainLinearIndex(grid, x, y, z) {
    return x + grid.xSamples * (z + grid.zSamples * y);
  }

  function terrainPoint(grid, legend, semantics, x, y, z) {
    const ordinal = semantics[terrainLinearIndex(grid, x, y, z)];
    const semantic = legend[ordinal];
    if (!semantic) {
      throw new Error("terrain semantic ordinal " + ordinal + " is outside legend");
    }
    return Object.freeze({
      position: Object.freeze([
        grid.minimumX + grid.spacingX * x,
        grid.minimumY + grid.spacingY * y,
        grid.minimumZ + grid.spacingZ * z,
      ]),
      gridIndex: Object.freeze([x, y, z]),
      semanticOrdinal: ordinal,
      semanticName: semantic.name,
      solid: semantic.solid,
    });
  }

  function deriveTerrainSurfaces(grid, legend, semantics) {
    const top = [];
    const underside = [];
    for (let z = 0; z < grid.zSamples; z++) {
      for (let x = 0; x < grid.xSamples; x++) {
        let topY = -1;
        let bottomY = -1;
        for (let y = grid.ySamples - 1; y >= 0; y--) {
          const ordinal = semantics[terrainLinearIndex(grid, x, y, z)];
          if (legend[ordinal]?.solid) {
            topY = y;
            break;
          }
        }
        for (let y = 0; y < grid.ySamples; y++) {
          const ordinal = semantics[terrainLinearIndex(grid, x, y, z)];
          if (legend[ordinal]?.solid) {
            bottomY = y;
            break;
          }
        }
        if (topY >= 0) top.push(terrainPoint(grid, legend, semantics, x, topY, z));
        if (bottomY >= 0) {
          underside.push(terrainPoint(grid, legend, semantics, x, bottomY, z));
        }
      }
    }
    return Object.freeze({
      top: Object.freeze(top),
      underside: Object.freeze(underside),
    });
  }

  function terrainSlice(grid, legend, semantics, yIndex) {
    if (!Number.isInteger(yIndex) || yIndex < 0 || yIndex >= grid.ySamples) {
      throw new Error("terrain slice y index is outside grid");
    }
    const points = [];
    for (let z = 0; z < grid.zSamples; z++) {
      for (let x = 0; x < grid.xSamples; x++) {
        const point = terrainPoint(grid, legend, semantics, x, yIndex, z);
        if (point.solid) points.push(point);
      }
    }
    return Object.freeze(points);
  }

  function terrainSemanticVolume(artifact, context = {}) {
    if (!artifact || typeof artifact !== "object") throw new Error("artifact must be an object");
    if (artifact.schema_version !== 1) {
      throw new Error("unsupported terrain schema_version " + String(artifact.schema_version));
    }
    if (artifact.artifact_kind !== TERRAIN_KIND) {
      throw new Error("unsupported artifact_kind " + String(artifact.artifact_kind || ""));
    }
    if (artifact.encoding?.kind !== "BASE64_UINT8_ORDINAL") {
      throw new Error("unsupported terrain semantic encoding");
    }
    if (
      artifact.encoding?.linear_index !==
      "x + x_samples * (z + z_samples * y)"
    ) {
      throw new Error("unsupported terrain linear index contract");
    }

    const grid = terrainGrid(artifact.grid);
    const legend = terrainLegend(artifact.semantic_legend);
    const semantics = decodeBase64(artifact.semantics_base64);
    if (
      semantics.length !== grid.sampleCount ||
      Number(artifact.encoding.sample_count) !== grid.sampleCount
    ) {
      throw new Error("terrain semantic payload length does not match grid");
    }
    for (let index = 0; index < semantics.length; index++) {
      if (semantics[index] >= legend.length) {
        throw new Error("terrain semantic payload contains unknown ordinal");
      }
    }

    const surfaces = deriveTerrainSurfaces(grid, legend, semantics);
    return Object.freeze({
      schemaVersion: 1,
      sceneKind: "TERRAIN_SEMANTIC_VOLUME",
      coordinateSystem: Object.freeze({
        id: "SKYFORGE_WORLD_XYZ",
        x: "+X world units",
        y: "+Y altitude",
        z: "+Z world units",
      }),
      source: authoritySource(TERRAIN_KIND, artifact.semantic_sha256, context),
      provider: Object.freeze({
        label: "Skyforge backend-neutral terrain semantics",
        modId: "",
        version: String(artifact.skyforge_version || ""),
        api: "WorldRegionTerrain",
      }),
      layers: Object.freeze([
        Object.freeze({
          id: "terrain-volume",
          primitive: "SemanticVolume",
          title: "Terrain semantic volume",
          semanticOwner: "Skyforge WorldRegionTerrain",
        }),
        Object.freeze({
          id: "top-surface",
          primitive: "PointSet",
          title: "Top semantic surface",
          semanticOwner: "Skyforge WorldRegionTerrain",
        }),
        Object.freeze({
          id: "underside",
          primitive: "PointSet",
          title: "Underside semantic surface",
          semanticOwner: "Skyforge WorldRegionTerrain",
        }),
      ]),
      terrain: {
        grid,
        legend,
        semantics,
        topSurface: surfaces.top,
        underside: surfaces.underside,
        sliceAtYIndex: (yIndex) =>
          terrainSlice(grid, legend, semantics, yIndex),
      },
      ownership: Object.freeze({
        semanticOwner: "Skyforge WorldRegionTerrain",
        persistenceOwner: "evidence artifact",
        backendNeutral: artifact.ownership?.backend_neutral_semantics === true,
        renderingBackendDependency:
          artifact.ownership?.minecraft_dependency === true,
        skyforgePersistsTruth: true,
      }),
    });
  }

  function adaptArtifact(artifact, context = {}) {
    if (!artifact || typeof artifact !== "object") {
      throw new Error("artifact must be an object");
    }
    if (artifact.artifact_kind === ATMOSPHERE_KIND) {
      return atmosphereProbeVolume(artifact, context);
    }
    if (artifact.artifact_kind === TERRAIN_KIND) {
      return terrainSemanticVolume(artifact, context);
    }
    throw new Error(
      "unsupported artifact_kind " + String(artifact.artifact_kind || "")
    );
  }

  window.SkyforgeStudioScene = Object.freeze({
    ATMOSPHERE_KIND,
    TERRAIN_KIND,
    adaptArtifact,
  });
})();
