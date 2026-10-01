(function (root) {
  "use strict";

  const DOCUMENT_TYPE = "SKYFORGE_STUDIO_INSPECTION_WORKSPACE";
  const FORMAT_VERSION = 1;
  const MAX_FILE_BYTES = 25 * 1024 * 1024;
  const SOURCE_FIELDS = new Set(["title", "artifactJson"]);
  const ROOT_FIELDS = new Set(["document_type", "format_version", "scene", "overlay", "view"]);
  const VIEW_FIELDS = new Set(["mode", "camera", "controls"]);
  const CAMERA_FIELDS = new Set(["yaw", "pitch", "zoom"]);
  const TERRAIN_CONTROL_FIELDS = new Set([
    "semanticView", "surface", "sliceIndex", "causeField",
    "showFlowVectors", "showChannelWidth", "showHydrologyResponse", "showWaterIntent",
  ]);
  const ATMOSPHERE_CONTROL_FIELDS = new Set([
    "dataset", "vectorMode", "colorMode", "altitude", "frame", "arrowScale",
  ]);

  function isRecord(value) {
    return value !== null && typeof value === "object" && !Array.isArray(value);
  }

  function assertOnlyFields(value, allowed, label) {
    if (Object.keys(value).some(key => !allowed.has(key))) {
      throw new Error(label + " contains unsupported fields");
    }
  }

  function byteLength(text) {
    return new TextEncoder().encode(text).byteLength;
  }

  function parseArtifactJson(value, label) {
    if (typeof value !== "string" || !value.trim()) {
      throw new Error(label + " source must contain the original JSON text");
    }
    let artifact;
    try {
      artifact = JSON.parse(value);
    } catch {
      throw new Error(label + " source must contain valid JSON");
    }
    if (!isRecord(artifact)) throw new Error(label + " source must be a JSON object");
    return artifact;
  }

  function validateSource(value, label) {
    if (!isRecord(value)) throw new Error(label + " source must be an object");
    assertOnlyFields(value, SOURCE_FIELDS, label + " source");
    if (Object.keys(value).length !== SOURCE_FIELDS.size ||
        [...SOURCE_FIELDS].some(key => !Object.prototype.hasOwnProperty.call(value, key))) {
      throw new Error(label + " source is missing required fields");
    }
    if (typeof value.title !== "string" || !value.title.trim() || value.title.length > 200) {
      throw new Error(label + " source title must contain 1 to 200 characters");
    }
    parseArtifactJson(value.artifactJson, label);
    return Object.freeze({ title: value.title, artifactJson: value.artifactJson });
  }

  function finite(value, label, minimum, maximum) {
    if (typeof value !== "number" || !Number.isFinite(value) || value < minimum || value > maximum) {
      throw new Error(label + " is outside its supported range");
    }
    return value;
  }

  function validateView(value) {
    if (!isRecord(value)) throw new Error("workspace view must be an object");
    assertOnlyFields(value, VIEW_FIELDS, "workspace view");
    if (Object.keys(value).length !== VIEW_FIELDS.size ||
        [...VIEW_FIELDS].some(key => !Object.prototype.hasOwnProperty.call(value, key))) {
      throw new Error("workspace view is missing required fields");
    }
    if (value.mode !== "terrain" && value.mode !== "atmosphere") {
      throw new Error("workspace view mode is unsupported");
    }
    if (!isRecord(value.camera)) throw new Error("workspace camera must be an object");
    assertOnlyFields(value.camera, CAMERA_FIELDS, "workspace camera");
    if (Object.keys(value.camera).length !== CAMERA_FIELDS.size) {
      throw new Error("workspace camera is missing required fields");
    }
    const camera = Object.freeze({
      yaw: finite(value.camera.yaw, "workspace camera yaw", -1000000, 1000000),
      pitch: finite(value.camera.pitch, "workspace camera pitch", -1.35, 1.35),
      zoom: finite(value.camera.zoom, "workspace camera zoom", 0.45, 3.5),
    });
    if (!isRecord(value.controls)) throw new Error("workspace controls must be an object");

    let controls;
    if (value.mode === "terrain") {
      assertOnlyFields(value.controls, TERRAIN_CONTROL_FIELDS, "terrain controls");
      if (Object.keys(value.controls).length !== TERRAIN_CONTROL_FIELDS.size) {
        throw new Error("terrain controls are missing required fields");
      }
      const enumValue = (field, allowed) => {
        if (!allowed.includes(value.controls[field])) throw new Error("workspace " + field + " is unsupported");
        return value.controls[field];
      };
      if (!Number.isInteger(value.controls.sliceIndex) || value.controls.sliceIndex < 0 || value.controls.sliceIndex > 1000000) {
        throw new Error("workspace sliceIndex is outside its supported range");
      }
      for (const field of ["showFlowVectors", "showChannelWidth", "showHydrologyResponse", "showWaterIntent"]) {
        if (typeof value.controls[field] !== "boolean") throw new Error("workspace " + field + " must be boolean");
      }
      controls = Object.freeze({
        semanticView: enumValue("semanticView", ["top", "underside", "both", "slice"]),
        surface: enumValue("surface", ["base", "hydrology"]),
        sliceIndex: value.controls.sliceIndex,
        causeField: enumValue("causeField", ["none", "runoffPotential", "retentionPotential", "drainagePotential", "outflowPotential"]),
        showFlowVectors: value.controls.showFlowVectors,
        showChannelWidth: value.controls.showChannelWidth,
        showHydrologyResponse: value.controls.showHydrologyResponse,
        showWaterIntent: value.controls.showWaterIntent,
      });
    } else {
      assertOnlyFields(value.controls, ATMOSPHERE_CONTROL_FIELDS, "atmosphere controls");
      if (Object.keys(value.controls).length !== ATMOSPHERE_CONTROL_FIELDS.size) {
        throw new Error("atmosphere controls are missing required fields");
      }
      if (!["snapshot", "opportunity"].includes(value.controls.dataset)) {
        throw new Error("workspace dataset is unsupported");
      }
      if (!["effective", "mean", "gust"].includes(value.controls.vectorMode)) {
        throw new Error("workspace vectorMode is unsupported");
      }
      if (!["verticalAir", "turbulence", "shear", "speed"].includes(value.controls.colorMode)) {
        throw new Error("workspace colorMode is unsupported");
      }
      if (typeof value.controls.altitude !== "string" ||
          (value.controls.altitude !== "all" && (!Number.isFinite(Number(value.controls.altitude)) || value.controls.altitude.length > 24))) {
        throw new Error("workspace altitude is invalid");
      }
      if (!Number.isInteger(value.controls.frame) || value.controls.frame < 0 || value.controls.frame > 10000) {
        throw new Error("workspace frame is outside its supported range");
      }
      controls = Object.freeze({
        dataset: value.controls.dataset,
        vectorMode: value.controls.vectorMode,
        colorMode: value.controls.colorMode,
        altitude: value.controls.altitude,
        frame: value.controls.frame,
        arrowScale: finite(value.controls.arrowScale, "workspace arrowScale", 0.25, 4),
      });
    }
    return Object.freeze({ mode: value.mode, camera, controls });
  }

  function validateDocument(value) {
    if (!isRecord(value)) throw new Error("inspection workspace must be a JSON object");
    assertOnlyFields(value, ROOT_FIELDS, "inspection workspace");
    if (Object.keys(value).length !== ROOT_FIELDS.size ||
        [...ROOT_FIELDS].some(key => !Object.prototype.hasOwnProperty.call(value, key))) {
      throw new Error("inspection workspace is missing required fields");
    }
    if (value.document_type !== DOCUMENT_TYPE) throw new Error("file is not a Skyforge Studio inspection workspace");
    if (value.format_version !== FORMAT_VERSION) throw new Error("unsupported inspection workspace format version");
    const scene = validateSource(value.scene, "scene");
    const overlay = value.overlay === null ? null : validateSource(value.overlay, "overlay");
    const view = validateView(value.view);
    if (view.mode === "terrain" && view.controls.surface === "hydrology" && !overlay) {
      throw new Error("workspace selects the hydrology surface but has no reference overlay");
    }
    const normalized = {
      document_type: DOCUMENT_TYPE,
      format_version: FORMAT_VERSION,
      scene,
      overlay,
      view,
    };
    const serialized = JSON.stringify(normalized);
    if (byteLength(serialized) > MAX_FILE_BYTES) throw new Error("inspection workspace must be 25 MB or smaller");
    return Object.freeze(normalized);
  }

  function create(value) {
    return validateDocument({
      document_type: DOCUMENT_TYPE,
      format_version: FORMAT_VERSION,
      scene: value?.scene,
      overlay: value?.overlay ?? null,
      view: value?.view,
    });
  }

  function parse(value) {
    let document;
    if (typeof value === "string") {
      if (byteLength(value) > MAX_FILE_BYTES) throw new Error("inspection workspace must be 25 MB or smaller");
      try {
        document = JSON.parse(value);
      } catch {
        throw new Error("inspection workspace must contain valid JSON");
      }
    } else {
      document = value;
    }
    return validateDocument(document);
  }

  function prepare(value, adapters) {
    const document = parse(value);
    if (!adapters || typeof adapters.adaptScene !== "function") {
      throw new Error("inspection workspace scene adapter is unavailable");
    }
    const sceneArtifact = parseArtifactJson(document.scene.artifactJson, "scene");
    const sourceOptions = {
      binding: "UNBOUND_LOCAL",
      artifactTitle: document.scene.title,
      reviewAuthority: false,
    };
    const scene = adapters.adaptScene(sceneArtifact, sourceOptions);
    if (!scene || scene.source?.binding !== "UNBOUND_LOCAL" || scene.source.reviewAuthority !== false) {
      throw new Error("scene adapter did not preserve local diagnostic status");
    }
    const expectedKind = document.view.mode === "terrain"
      ? "TERRAIN_SEMANTIC_VOLUME"
      : "ATMOSPHERE_VECTOR_FIELD";
    if (scene.sceneKind !== expectedKind) throw new Error("workspace view does not match its scene source");

    let overlay = null;
    let overlayArtifact = null;
    if (document.overlay) {
      if (document.view.mode !== "terrain") throw new Error("hydrology overlay requires a terrain scene");
      if (!adapters || typeof adapters.adaptOverlay !== "function") {
        throw new Error("inspection workspace overlay adapter is unavailable");
      }
      overlayArtifact = parseArtifactJson(document.overlay.artifactJson, "overlay");
      overlay = adapters.adaptOverlay(overlayArtifact, scene, sourceOptions);
      if (!overlay || overlay.source?.binding !== "UNBOUND_LOCAL" || overlay.source.reviewAuthority !== false) {
        throw new Error("overlay adapter did not preserve local diagnostic status");
      }
    }
    if (document.view.mode === "terrain" &&
        document.view.controls.surface === "hydrology" && !overlay) {
      throw new Error("workspace selects the hydrology surface but has no reference overlay");
    }
    return Object.freeze({
      scene,
      sceneArtifact,
      sceneJson: document.scene.artifactJson,
      sceneTitle: document.scene.title,
      overlay,
      overlayArtifact,
      overlayJson: document.overlay?.artifactJson || "",
      overlayTitle: document.overlay?.title || "",
      view: document.view,
    });
  }

  const api = Object.freeze({
    create,
    parse,
    prepare,
    documentType: DOCUMENT_TYPE,
    formatVersion: FORMAT_VERSION,
    maximumFileBytes: MAX_FILE_BYTES,
  });
  if (root && typeof root === "object") root.SkyforgeStudioWorkspacePackage = api;
  if (typeof module !== "undefined" && module.exports) module.exports = api;
})(typeof globalThis !== "undefined" ? globalThis : this);
