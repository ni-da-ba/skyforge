(() => {
  "use strict";

  const ATMOSPHERE_KIND = "SKYFORGE_ATMOSPHERE_PROBE_VOLUME";

  function finite(value, label) {
    const number = Number(value);
    if (!Number.isFinite(number)) throw new Error(label + " must be finite");
    return number;
  }

  function vector(value, label) {
    if (!Array.isArray(value) || value.length !== 3) {
      throw new Error(label + " must be a three-component vector");
    }
    return value.map((component, index) => finite(component, label + "[" + index + "]"));
  }

  function sample(record, index) {
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

  function sampleFrame(record, label) {
    if (!record || !Array.isArray(record.samples) || !record.samples.length) {
      throw new Error(label + " has no samples");
    }
    return Object.freeze({
      tick: record.game_tick === undefined ? null : finite(record.game_tick, label + " tick"),
      samples: Object.freeze(record.samples.map((entry, index) => sample(entry, index))),
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
      samples: Object.freeze(artifact.samples.map((entry, index) => sample(entry, index))),
    });
    const opportunity = artifact.opportunity_scan || {};
    const opportunityFrames = Array.isArray(opportunity.frames)
      ? Object.freeze(opportunity.frames.map(
          (frame, index) => sampleFrame(frame, "opportunity frame " + index)
        ))
      : Object.freeze([]);

    const layers = Object.freeze([
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
    ]);

    return Object.freeze({
      schemaVersion: 1,
      sceneKind: "ATMOSPHERE_VECTOR_FIELD",
      coordinateSystem: Object.freeze({
        id: "MINECRAFT_WORLD_XYZ",
        x: "+X world blocks",
        y: "+Y altitude",
        z: "+Z world blocks",
      }),
      source: Object.freeze({
        binding: String(context.binding || "UNBOUND_LOCAL"),
        artifactId: context.artifactId ? String(context.artifactId) : null,
        artifactTitle: context.artifactTitle ? String(context.artifactTitle) : null,
        sourceSha: context.sourceSha ? String(context.sourceSha) : null,
        reviewAuthority: context.reviewAuthority === true,
        artifactKind: ATMOSPHERE_KIND,
        artifactDigest: String(artifact.ordered_sample_digest || ""),
      }),
      provider: Object.freeze({
        modId: String(provider.mod_id || ""),
        version: String(provider.version || ""),
        api: String(provider.api || ""),
      }),
      layers,
      snapshot,
      opportunity: Object.freeze({
        periodTicks: opportunity.period_ticks === undefined
          ? null
          : finite(opportunity.period_ticks, "opportunity period"),
        frames: opportunityFrames,
      }),
      ownership: Object.freeze({
        serverWorldSampling: artifact.ownership?.server_world_sampling === true,
        skyforgePersistsAtmosphere: artifact.ownership?.skyforge_persists_atmosphere === true,
        providerPersistenceOwner: String(artifact.ownership?.provider_persistence_owner || ""),
        renderingBackendDependency: artifact.ownership?.rendering_backend_dependency === true,
      }),
    });
  }

  function adaptArtifact(artifact, context = {}) {
    return atmosphereProbeVolume(artifact, context);
  }

  window.SkyforgeStudioScene = Object.freeze({
    ATMOSPHERE_KIND,
    adaptArtifact,
  });
})();
