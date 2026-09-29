(() => {
  "use strict";

  const ATMOSPHERE_KIND = "SKYFORGE_ATMOSPHERE_PROBE_VOLUME";
  const TERRAIN_KIND = "SKYFORGE_TERRAIN_SEMANTIC_VOLUME";
  const HYDROLOGY_KIND = "SKYFORGE_BOUND_HYDROLOGY_SEMANTIC_LAYER";

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

  function nullableFinite(value, label) {
    if (value === null || value === undefined) return null;
    return finite(value, label);
  }

  function coordinateMatches(actual, expected) {
    return Math.abs(actual - expected) <= Math.max(1e-9, Math.abs(expected) * 1e-12);
  }

  function hydrologyGridBinding(record, terrainGrid) {
    if (!record || typeof record !== "object") {
      throw new Error("hydrology grid_binding is required");
    }
    const grid = {
      minimumX: finite(record.minimum_x, "hydrology grid minimum_x"),
      minimumZ: finite(record.minimum_z, "hydrology grid minimum_z"),
      spacingX: finite(record.spacing_x, "hydrology grid spacing_x"),
      spacingZ: finite(record.spacing_z, "hydrology grid spacing_z"),
      xSamples: positiveInteger(record.x_samples, "hydrology grid x_samples"),
      zSamples: positiveInteger(record.z_samples, "hydrology grid z_samples"),
      causeStride: positiveInteger(record.cause_stride, "hydrology grid cause_stride"),
      causeSampleCount: positiveInteger(
        record.cause_sample_count,
        "hydrology grid cause_sample_count"
      ),
    };
    if (
      !coordinateMatches(grid.minimumX, terrainGrid.minimumX) ||
      !coordinateMatches(grid.minimumZ, terrainGrid.minimumZ) ||
      !coordinateMatches(grid.spacingX, terrainGrid.spacingX) ||
      !coordinateMatches(grid.spacingZ, terrainGrid.spacingZ) ||
      grid.xSamples !== terrainGrid.xSamples ||
      grid.zSamples !== terrainGrid.zSamples
    ) {
      throw new Error("hydrology grid does not match loaded terrain semantic specimen");
    }
    return Object.freeze(grid);
  }

  function gridCoordinates(record, index, label, binding, stride = 1) {
    if (!Array.isArray(record.grid) || record.grid.length !== 2) {
      throw new Error(label + " grid must contain x/z indices");
    }
    const gridX = Number(record.grid[0]);
    const gridZ = Number(record.grid[1]);
    if (
      !Number.isSafeInteger(gridX) ||
      !Number.isSafeInteger(gridZ) ||
      gridX < 0 ||
      gridZ < 0 ||
      gridX >= binding.xSamples ||
      gridZ >= binding.zSamples ||
      gridX % stride !== 0 ||
      gridZ % stride !== 0
    ) {
      throw new Error(label + " grid indices are outside the bound terrain lattice");
    }
    const worldX = finite(record.world_x, label + " world_x");
    const worldZ = finite(record.world_z, label + " world_z");
    if (
      !coordinateMatches(worldX, binding.minimumX + binding.spacingX * gridX) ||
      !coordinateMatches(worldZ, binding.minimumZ + binding.spacingZ * gridZ)
    ) {
      throw new Error(label + " world coordinates do not match the bound terrain lattice");
    }
    return Object.freeze([gridX, gridZ]);
  }

  function hydrologyFieldSample(record, index, binding) {
    if (!record || typeof record !== "object") {
      throw new Error("hydrology field sample " + index + " is not an object");
    }
    const grid = gridCoordinates(record, index, "hydrology field sample " + index, binding);
    const worldX = finite(record.world_x, "hydrology field sample world_x");
    const worldZ = finite(record.world_z, "hydrology field sample world_z");
    const targetY = finite(
      record.target_upper_y,
      "hydrology field sample target_upper_y"
    );
    const waterY = nullableFinite(
      record.water_surface_y,
      "hydrology field sample water_surface_y"
    );
    const provenance = record.provenance;
    return Object.freeze({
      overlayKind: "HYDROLOGY_FIELD_SAMPLE",
      grid,
      position: Object.freeze([worldX, targetY, worldZ]),
      waterPosition: waterY === null
        ? null
        : Object.freeze([worldX, waterY, worldZ]),
      originalUpperY: finite(
        record.original_upper_y,
        "hydrology field sample original_upper_y"
      ),
      targetUpperY: targetY,
      terrainDeltaWorld: finite(
        record.terrain_delta_world,
        "hydrology field sample terrain_delta_world"
      ),
      zone: String(record.zone || ""),
      wet: record.wet === true,
      waterSurfaceY: waterY,
      waterDepthWorld: finite(
        record.water_depth_world,
        "hydrology field sample water_depth_world"
      ),
      provenance: provenance
        ? Object.freeze({
            startCell: Number(provenance.start_cell),
            endCell: Number(provenance.end_cell),
            profileKind: String(provenance.profile_kind || ""),
          })
        : null,
    });
  }

  function hydrologyCauseSample(record, index, binding, worldFrame, topByGrid) {
    if (!record || typeof record !== "object") {
      throw new Error("hydrology cause sample " + index + " is not an object");
    }
    const label = "hydrology cause sample " + index;
    const grid = gridCoordinates(
      record,
      index,
      label,
      binding,
      binding.causeStride
    );
    const worldX = finite(record.world_x, label + " world_x");
    const worldZ = finite(record.world_z, label + " world_z");
    const localX = finite(record.local_x, label + " local_x");
    const localZ = finite(record.local_z, label + " local_z");
    if (
      !coordinateMatches(worldX, localX + worldFrame.centerX) ||
      !coordinateMatches(worldZ, localZ + worldFrame.centerZ)
    ) {
      throw new Error(label + " local/world coordinates do not match specimen transform");
    }
    const key = grid[0] + ":" + grid[1];
    const terrainPoint = topByGrid.get(key);
    if (!terrainPoint) {
      throw new Error(label + " does not bind to a solid terrain column");
    }
    const runoffPotential = finite(record.runoff_potential, label + " runoff_potential");
    const retentionPotential = finite(record.retention_potential, label + " retention_potential");
    const drainagePotential = finite(record.drainage_potential, label + " drainage_potential");
    const outflowPotential = finite(record.outflow_potential, label + " outflow_potential");
    for (const [name, value] of [
      ["runoff_potential", runoffPotential],
      ["retention_potential", retentionPotential],
      ["drainage_potential", drainagePotential],
      ["outflow_potential", outflowPotential],
    ]) {
      if (value < 0 || value > 1) {
        throw new Error(label + " " + name + " must be in [0, 1]");
      }
    }
    const flowX = finite(record.flow_x, label + " flow_x");
    const flowZ = finite(record.flow_z, label + " flow_z");
    if (Math.hypot(flowX, flowZ) > 1.0000001) {
      throw new Error(label + " flow direction magnitude must not exceed one");
    }
    return Object.freeze({
      overlayKind: "HYDROLOGY_CAUSE_SAMPLE",
      grid,
      localPosition: Object.freeze([localX, localZ]),
      worldPosition: Object.freeze([worldX, terrainPoint.position[1], worldZ]),
      position: terrainPoint.position,
      displayPosition: Object.freeze([
        terrainPoint.position[0],
        terrainPoint.position[1] + 0.5,
        terrainPoint.position[2],
      ]),
      runoffPotential,
      retentionPotential,
      drainagePotential,
      outflowPotential,
      flowX,
      flowZ,
    });
  }

  function hydrologyReach(record, index) {
    if (!record || !Array.isArray(record.points) || record.points.length < 2) {
      throw new Error("hydrology reach " + index + " must contain at least two points");
    }
    const startCell = Number(record.start_cell);
    const endCell = Number(record.end_cell);
    if (
      !Number.isSafeInteger(startCell) ||
      !Number.isSafeInteger(endCell) ||
      startCell < 0 ||
      endCell < 0 ||
      startCell === endCell
    ) {
      throw new Error("hydrology reach endpoints are invalid");
    }
    const points = record.points.map((point, pointIndex) => {
      const worldX = finite(point.world_x, "hydrology reach point world_x");
      const worldZ = finite(point.world_z, "hydrology reach point world_z");
      const targetY = finite(
        point.target_upper_y,
        "hydrology reach point target_upper_y"
      );
      const waterY = nullableFinite(
        point.water_surface_y,
        "hydrology reach point water_surface_y"
      );
      const stationFraction = finite(
        point.station_fraction,
        "hydrology reach station_fraction"
      );
      const relativeDischarge = finite(
        point.relative_discharge,
        "hydrology reach relative_discharge"
      );
      const bankfullHalfWidth = finite(
        point.bankfull_half_width,
        "hydrology reach bankfull_half_width"
      );
      if (stationFraction < 0 || stationFraction > 1 || relativeDischarge < 0 || bankfullHalfWidth < 0) {
        throw new Error("hydrology reach point contains an invalid hydraulic value");
      }
      return Object.freeze({
        overlayKind: "HYDROLOGY_REACH_POINT",
        position: Object.freeze([worldX, targetY, worldZ]),
        waterPosition: waterY === null
          ? null
          : Object.freeze([worldX, waterY, worldZ]),
        startCell,
        endCell,
        stationFraction,
        relativeDischarge,
        bankfullHalfWidth,
      });
    });
    return Object.freeze({
      startCell,
      endCell,
      downstreamStreamOrder: positiveInteger(
        record.downstream_stream_order,
        "hydrology downstream stream order"
      ),
      downstreamRelativeDischarge: finite(
        record.downstream_relative_discharge,
        "hydrology downstream relative discharge"
      ),
      maximumBankfullHalfWidth: finite(
        record.maximum_bankfull_half_width,
        "hydrology maximum bankfull half width"
      ),
      points: Object.freeze(points),
    });
  }

  function adaptOverlayArtifact(artifact, baseScene, context = {}) {
    if (!artifact || typeof artifact !== "object") {
      throw new Error("overlay artifact must be an object");
    }
    if (!baseScene || baseScene.sceneKind !== "TERRAIN_SEMANTIC_VOLUME") {
      throw new Error("hydrology overlay requires a terrain semantic base scene");
    }
    if (artifact.schema_version !== 1) {
      throw new Error(
        "unsupported hydrology schema_version " + String(artifact.schema_version)
      );
    }
    if (artifact.artifact_kind !== HYDROLOGY_KIND) {
      throw new Error(
        "unsupported overlay artifact_kind " + String(artifact.artifact_kind || "")
      );
    }
    const terrainDigest = String(artifact.terrain_semantic_sha256 || "");
    if (
      !/^[a-f0-9]{64}$/i.test(terrainDigest) ||
      terrainDigest !== baseScene.source.artifactDigest
    ) {
      throw new Error(
        "hydrology terrain semantic SHA does not match loaded terrain specimen"
      );
    }

    const binding = artifact.specimen_binding || {};
    const associationToken = String(binding.association_token || "");
    const authoredIdentity = binding.authored_identity || {};
    const realizedVolume = binding.realized_volume || {};
    const rawWorldFrame = binding.world_frame || {};
    if (
      binding.association_schema_version !== 1 ||
      !associationToken.startsWith("sfassoc:v1:") ||
      !/^[a-f0-9]{16}$/i.test(String(authoredIdentity.world_seed_hex || "")) ||
      !/^[a-f0-9]{16}$/i.test(String(authoredIdentity.province_key_hex || "")) ||
      !/^[a-f0-9]{16}$/i.test(String(authoredIdentity.cluster_key_hex || "")) ||
      !/^[a-f0-9]{16}$/i.test(String(authoredIdentity.island_key_hex || "")) ||
      !String(realizedVolume.path || "") ||
      !String(realizedVolume.group_identifier || "")
    ) {
      throw new Error("hydrology overlay lacks a complete AUTH-0046 specimen binding");
    }

    const worldFrame = Object.freeze({
      centerX: finite(rawWorldFrame.center_x, "hydrology world frame center_x"),
      centerZ: finite(rawWorldFrame.center_z, "hydrology world frame center_z"),
      suspensionElevation: finite(
        rawWorldFrame.suspension_elevation,
        "hydrology world frame suspension_elevation"
      ),
      nominalRadius: finite(rawWorldFrame.nominal_radius, "hydrology world frame nominal_radius"),
    });
    if (worldFrame.nominalRadius <= 0) {
      throw new Error("hydrology world frame nominal_radius must be positive");
    }

    const gridBinding = hydrologyGridBinding(
      artifact.grid_binding,
      baseScene.terrain.grid
    );
    if (!Array.isArray(artifact.hydrology_causes) || !artifact.hydrology_causes.length) {
      throw new Error("hydrology_causes must contain bounded authored field samples");
    }
    if (artifact.hydrology_causes.length !== gridBinding.causeSampleCount) {
      throw new Error("hydrology cause sample count does not match grid_binding");
    }
    if (!Array.isArray(artifact.field_samples) || !artifact.field_samples.length) {
      throw new Error("hydrology field_samples must contain affected terrain samples");
    }
    if (!Array.isArray(artifact.reaches) || !artifact.reaches.length) {
      throw new Error("hydrology reaches must contain an accepted reach");
    }

    const ownership = artifact.ownership || {};
    if (
      ownership.association_authority !== "AUTH-0046" ||
      ownership.terrain_projection_authority !== "F4B" ||
      ownership.water_projection_authority !== "F4E" ||
      ownership.backend_neutral_semantics !== true ||
      ownership.minecraft_dependency !== false ||
      ownership.studio_recomputes_hydrology !== false
    ) {
      throw new Error("hydrology overlay ownership contract is unsupported");
    }

    const topByGrid = new Map(
      baseScene.terrain.topSurface.map((point) => [
        point.gridIndex[0] + ":" + point.gridIndex[2],
        point,
      ])
    );
    const fieldSamples = Object.freeze(
      artifact.field_samples.map((record, index) =>
        hydrologyFieldSample(record, index, gridBinding)
      )
    );
    const causeSamples = Object.freeze(
      artifact.hydrology_causes.map((record, index) =>
        hydrologyCauseSample(record, index, gridBinding, worldFrame, topByGrid)
      )
    );
    const causeKeys = new Set(causeSamples.map((sample) => sample.grid.join(":")));
    if (causeKeys.size !== causeSamples.length) {
      throw new Error("hydrology cause lattice contains duplicate grid samples");
    }
    const reaches = Object.freeze(artifact.reaches.map(hydrologyReach));
    return Object.freeze({
      schemaVersion: 1,
      sceneKind: "HYDROLOGY_SEMANTIC_LAYER",
      source: authoritySource(
        HYDROLOGY_KIND,
        terrainDigest + "|" + associationToken,
        {
          ...context,
          reviewAuthority:
            context.reviewAuthority === true && baseScene.source.reviewAuthority,
        }
      ),
      terrainSemanticSha256: terrainDigest,
      binding: Object.freeze({
        associationToken,
        authoredIdentity: Object.freeze({ ...authoredIdentity }),
        realizedVolume: Object.freeze({ ...realizedVolume }),
        worldFrame,
      }),
      gridBinding,
      fieldSamples,
      causeSamples,
      reaches,
      ownership: Object.freeze({
        semanticOwner: "Skyforge F4B/F4E world-space hydrology projection",
        causeFieldOwner: "SkyIslandHydrologyField",
        associationAuthority: ownership.association_authority,
        terrainProjectionAuthority: ownership.terrain_projection_authority,
        waterProjectionAuthority: ownership.water_projection_authority,
        backendNeutral: true,
        studioRecomputesHydrology: false,
      }),
    });
  }

  function sameHydrologyGrid(left, right) {
    const keys = [
      "minimumX",
      "minimumZ",
      "spacingX",
      "spacingZ",
      "xSamples",
      "zSamples",
      "causeStride",
      "causeSampleCount",
    ];
    return keys.every((key) => left?.[key] === right?.[key]);
  }

  function sameWorldFrame(left, right) {
    const keys = ["centerX", "centerZ", "suspensionElevation", "nominalRadius"];
    return keys.every((key) => left?.[key] === right?.[key]);
  }

  function compareHydrologyLayers(reference, candidate) {
    if (
      reference?.sceneKind !== "HYDROLOGY_SEMANTIC_LAYER" ||
      candidate?.sceneKind !== "HYDROLOGY_SEMANTIC_LAYER"
    ) {
      throw new Error("comparison requires two adapted hydrology semantic layers");
    }
    if (reference.terrainSemanticSha256 !== candidate.terrainSemanticSha256) {
      throw new Error("hydrology comparison requires the same exact terrain semantic SHA");
    }
    if (reference.binding.associationToken !== candidate.binding.associationToken) {
      throw new Error("hydrology comparison requires the same AUTH-0046 association");
    }
    if (!sameWorldFrame(reference.binding.worldFrame, candidate.binding.worldFrame)) {
      throw new Error("hydrology comparison world frames do not match");
    }
    if (!sameHydrologyGrid(reference.gridBinding, candidate.gridBinding)) {
      throw new Error("hydrology comparison cause-grid bindings do not match");
    }

    const candidateByGrid = new Map();
    for (const sample of candidate.causeSamples) {
      const key = sample.grid.join(":");
      if (candidateByGrid.has(key)) {
        throw new Error("candidate hydrology comparison contains duplicate cause samples");
      }
      candidateByGrid.set(key, sample);
    }
    if (candidateByGrid.size !== reference.causeSamples.length) {
      throw new Error("hydrology comparison cause sample counts do not match");
    }

    const fields = [
      "runoffPotential",
      "retentionPotential",
      "drainagePotential",
      "outflowPotential",
    ];
    const samples = reference.causeSamples.map((referenceSample) => {
      const key = referenceSample.grid.join(":");
      const candidateSample = candidateByGrid.get(key);
      if (!candidateSample) {
        throw new Error("candidate hydrology comparison is missing cause sample " + key);
      }
      if (
        candidateSample.position.some(
          (value, index) => value !== referenceSample.position[index]
        )
      ) {
        throw new Error("hydrology comparison world-space cause coordinates do not match");
      }
      const deltas = Object.create(null);
      for (const field of fields) {
        const delta = candidateSample[field] - referenceSample[field];
        if (!Number.isFinite(delta)) {
          throw new Error("hydrology comparison produced a non-finite cause delta");
        }
        deltas[field] = delta;
      }
      return Object.freeze({
        overlayKind: "HYDROLOGY_COMPARISON_SAMPLE",
        grid: referenceSample.grid,
        position: referenceSample.position,
        displayPosition: referenceSample.displayPosition,
        reference: referenceSample,
        candidate: candidateSample,
        deltas: Object.freeze(deltas),
      });
    });

    return Object.freeze({
      sceneKind: "HYDROLOGY_SEMANTIC_COMPARISON",
      terrainSemanticSha256: reference.terrainSemanticSha256,
      associationToken: reference.binding.associationToken,
      referenceSource: reference.source,
      candidateSource: candidate.source,
      samples: Object.freeze(samples),
      reviewAuthority:
        reference.source.reviewAuthority && candidate.source.reviewAuthority,
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
    HYDROLOGY_KIND,
    adaptArtifact,
    adaptOverlayArtifact,
  });
})();
