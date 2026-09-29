(() => {
  "use strict";

  const TOKEN_KEY = "skyforge-development-api-token";
  const TERRAIN_COLORS = Object.freeze({
    AIR: "#f6f4ee",
    EDGE_SHELL: "#b18554",
    SURFACE_MANTLE: "#68975c",
    UNDERSIDE_SHELL: "#555b69",
    SHALLOW_INTERIOR: "#a79c84",
    DEEP_MASS: "#594c44",
  });

  // BEGIN STUDIO WORLD BRIEF DOCUMENT CONTRACT
  const WORLD_BRIEF_DOCUMENT_TYPE = "SKYFORGE_STUDIO_WORLD_BRIEF";
  const WORLD_BRIEF_LIBRARY_TYPE = "SKYFORGE_STUDIO_BRIEF_LIBRARY";
  const WORLD_BRIEF_FORMAT_VERSION = 1;
  const WORLD_BRIEF_MAX_TITLE_LENGTH = 120;
  const WORLD_BRIEF_MAX_INTENT_LENGTH = 8000;
  const WORLD_BRIEF_MAX_LIBRARY_SIZE = 100;

  function canonicalWorldBriefTimestamp(value, label) {
    if (typeof value !== "string" || !Number.isFinite(Date.parse(value))) {
      throw new Error(label + " must be a valid timestamp");
    }
    return new Date(value).toISOString();
  }

  function createWorldBrief(id, title, intent, createdAt, updatedAt) {
    if (typeof id !== "string" || !/^[a-zA-Z0-9-]{8,80}$/.test(id)) throw new Error("world brief id is invalid");
    if (typeof title !== "string" || !title.trim()) throw new Error("world brief title is required");
    const normalizedTitle = title.trim();
    if (normalizedTitle.length > WORLD_BRIEF_MAX_TITLE_LENGTH) {
      throw new Error("world brief title must be " + WORLD_BRIEF_MAX_TITLE_LENGTH + " characters or fewer");
    }
    if (typeof intent !== "string") throw new Error("world brief intent must be text");
    if (intent.length > WORLD_BRIEF_MAX_INTENT_LENGTH) {
      throw new Error("world brief intent must be " + WORLD_BRIEF_MAX_INTENT_LENGTH + " characters or fewer");
    }
    return Object.freeze({
      document_type: WORLD_BRIEF_DOCUMENT_TYPE,
      format_version: WORLD_BRIEF_FORMAT_VERSION,
      id,
      title: normalizedTitle,
      intent,
      created_at: canonicalWorldBriefTimestamp(createdAt, "world brief created_at"),
      updated_at: canonicalWorldBriefTimestamp(updatedAt, "world brief updated_at"),
    });
  }

  function parseWorldBrief(value) {
    const document = typeof value === "string" ? JSON.parse(value) : value;
    if (!document || typeof document !== "object" || Array.isArray(document)) {
      throw new Error("world brief must be a JSON object");
    }
    if (document.document_type !== WORLD_BRIEF_DOCUMENT_TYPE) throw new Error("file is not a Skyforge Studio world brief");
    if (document.format_version !== WORLD_BRIEF_FORMAT_VERSION) throw new Error("unsupported world brief format version");
    const allowed = new Set(["document_type", "format_version", "id", "title", "intent", "created_at", "updated_at"]);
    if (Object.keys(document).some((key) => !allowed.has(key))) throw new Error("world brief contains unsupported fields");
    return createWorldBrief(document.id, document.title, document.intent, document.created_at, document.updated_at);
  }

  function createWorldBriefLibrary(briefs = [], activeBriefId = null) {
    if (!Array.isArray(briefs) || briefs.length > WORLD_BRIEF_MAX_LIBRARY_SIZE) {
      throw new Error("world brief library has an invalid number of drafts");
    }
    const parsedBriefs = briefs.map((brief) => parseWorldBrief(brief));
    const ids = new Set(parsedBriefs.map((brief) => brief.id));
    if (ids.size !== parsedBriefs.length) throw new Error("world brief library contains duplicate draft ids");
    if (activeBriefId !== null && (typeof activeBriefId !== "string" || !ids.has(activeBriefId))) {
      throw new Error("world brief library active draft does not exist");
    }
    return Object.freeze({
      document_type: WORLD_BRIEF_LIBRARY_TYPE,
      format_version: WORLD_BRIEF_FORMAT_VERSION,
      active_brief_id: activeBriefId,
      briefs: Object.freeze(parsedBriefs),
    });
  }

  function parseWorldBriefLibrary(value) {
    const library = typeof value === "string" ? JSON.parse(value) : value;
    if (!library || typeof library !== "object" || Array.isArray(library)) throw new Error("saved world brief library must be a JSON object");
    if (library.document_type !== WORLD_BRIEF_LIBRARY_TYPE) throw new Error("saved browser data is not a Skyforge Studio brief library");
    if (library.format_version !== WORLD_BRIEF_FORMAT_VERSION) throw new Error("unsupported saved world brief library version");
    const required = ["document_type", "format_version", "active_brief_id", "briefs"];
    if (required.some((key) => !Object.prototype.hasOwnProperty.call(library, key))) {
      throw new Error("saved world brief library is missing required fields");
    }
    const allowed = new Set(required);
    if (Object.keys(library).some((key) => !allowed.has(key))) throw new Error("saved world brief library contains unsupported fields");
    return createWorldBriefLibrary(library.briefs, library.active_brief_id);
  }

  function serializeWorldBrief(value) {
    return JSON.stringify(parseWorldBrief(value), null, 2) + "\n";
  }

  function serializeWorldBriefLibrary(value) {
    return JSON.stringify(parseWorldBriefLibrary(value), null, 2);
  }

  window.SkyforgeStudioWorldBrief = Object.freeze({
    create: createWorldBrief,
    parse: parseWorldBrief,
    serialize: serializeWorldBrief,
    createLibrary: createWorldBriefLibrary,
    parseLibrary: parseWorldBriefLibrary,
    serializeLibrary: serializeWorldBriefLibrary,
    documentType: WORLD_BRIEF_DOCUMENT_TYPE,
    libraryType: WORLD_BRIEF_LIBRARY_TYPE,
  });
  // END STUDIO WORLD BRIEF DOCUMENT CONTRACT

  function readStoredToken() {
    try {
      return window.sessionStorage?.getItem(TOKEN_KEY) || "";
    } catch {
      return "";
    }
  }

  let token = readStoredToken();
  let scene = null;
  let overlay = null;
  let hydrologyComparison = null;
  let artifactCatalog = [];
  let selected = null;
  let projected = [];
  let yaw = -0.72;
  let pitch = 0.50;
  let zoom = 1.0;
  let dragging = false;
  let lastX = 0;
  let lastY = 0;
  let playback = null;
  let studioWorkspaceView = "inspect";

  const $ = (id) => document.getElementById(id);
  const canvas = $("viewport");
  const ctx = canvas.getContext("2d");

  function syncWorkspaceVisibility() {
    $("studio-content").hidden = studioWorkspaceView !== "inspect";
    $("world-brief-view").hidden = studioWorkspaceView !== "brief";
    for (const button of document.querySelectorAll("[data-workspace-view]")) {
      button.setAttribute("aria-pressed", String(button.dataset.workspaceView === studioWorkspaceView));
    }
  }

  function selectWorkspaceView(view) {
    if (view !== "inspect" && view !== "brief") return;
    studioWorkspaceView = view;
    syncWorkspaceVisibility();
    if (view === "inspect") window.requestAnimationFrame(() => { resizeCanvas(); draw(); });
  }

  function fmt(value, digits = 3) {
    return Number.isFinite(Number(value)) ? Number(value).toFixed(digits) : "—";
  }

  function shortDigest(value) {
    const text = String(value || "");
    return text.length > 18 ? text.slice(0, 18) + "…" : (text || "—");
  }

  function vectorMagnitude(vector) {
    return Math.hypot(vector[0], vector[1], vector[2]);
  }

  function isAtmosphere() {
    return scene?.sceneKind === "ATMOSPHERE_VECTOR_FIELD";
  }

  function isTerrain() {
    return scene?.sceneKind === "TERRAIN_SEMANTIC_VOLUME";
  }

  function setConnection(text, severity = "muted") {
    const node = $("connection");
    node.textContent = text;
    node.className = "pill " + severity;
  }

  function showLocalMode() {
    $("auth-panel").hidden = false;
    syncWorkspaceVisibility();
    $("registered-artifact-source").hidden = true;
    $("registered-overlay-source").hidden = true;
    $("registered-comparison-source").hidden = true;
    $("console-link").hidden = true;
    setConnection("Local diagnostics", "warn");
  }

  function showConnectedMode() {
    $("auth-panel").hidden = true;
    syncWorkspaceVisibility();
    $("registered-artifact-source").hidden = false;
    $("registered-overlay-source").hidden = false;
    $("registered-comparison-source").hidden = false;
    $("console-link").hidden = false;
    setConnection("Connected", "good");
  }

  async function api(path) {
    const headers = new Headers();
    headers.set("Authorization", "Bearer " + token);
    return fetch(path, { headers, cache: "no-store" });
  }

  function currentVector(sample) {
    return sample[$("vector-mode").value];
  }

  function currentAtmosphereFrame() {
    if (!isAtmosphere()) return null;
    if ($("dataset").value !== "opportunity" || scene.opportunity.frames.length === 0) {
      return scene.snapshot;
    }
    const index = Math.max(
      0,
      Math.min(scene.opportunity.frames.length - 1, Number($("frame").value) || 0)
    );
    return scene.opportunity.frames[index];
  }

  function filteredAtmosphereFrame() {
    const frame = currentAtmosphereFrame();
    if (!frame) return null;
    if ($("altitude").value === "all") return frame;
    const altitude = Number($("altitude").value);
    return {
      tick: frame.tick,
      samples: frame.samples.filter(
        (sample) => Math.abs(sample.position[1] - altitude) < 1e-9
      ),
    };
  }

  function atmosphereColorValue(sample) {
    const mode = $("color-mode").value;
    if (mode === "verticalAir") return sample.verticalAir;
    if (mode === "turbulence") return sample.turbulence;
    if (mode === "shear") return sample.shear;
    return vectorMagnitude(currentVector(sample));
  }

  function diagnosticColor(value, minimum, maximum) {
    const t = maximum > minimum
      ? Math.max(0, Math.min(1, (value - minimum) / (maximum - minimum)))
      : 0.5;
    const first = t < 0.5 ? [62, 133, 200] : [140, 145, 151];
    const second = t < 0.5 ? [140, 145, 151] : [218, 126, 67];
    const k = t < 0.5 ? t * 2 : (t - 0.5) * 2;
    return "rgb(" +
      Math.round(first[0] + (second[0] - first[0]) * k) + "," +
      Math.round(first[1] + (second[1] - first[1]) * k) + "," +
      Math.round(first[2] + (second[2] - first[2]) * k) + ")";
  }

  function rotate(vector) {
    const cy = Math.cos(yaw);
    const sy = Math.sin(yaw);
    const cp = Math.cos(pitch);
    const sp = Math.sin(pitch);
    const x1 = cy * vector[0] - sy * vector[2];
    const z1 = sy * vector[0] + cy * vector[2];
    return [x1, cp * vector[1] - sp * z1, sp * vector[1] + cp * z1];
  }

  function fitTransform(points) {
    const xs = points.map((point) => point.position[0]);
    const ys = points.map((point) => point.position[1]);
    const zs = points.map((point) => point.position[2]);
    const minimum = [Math.min(...xs), Math.min(...ys), Math.min(...zs)];
    const maximum = [Math.max(...xs), Math.max(...ys), Math.max(...zs)];
    const center = [
      (minimum[0] + maximum[0]) / 2,
      (minimum[1] + maximum[1]) / 2,
      (minimum[2] + maximum[2]) / 2,
    ];
    const span = Math.max(
      64,
      maximum[0] - minimum[0],
      maximum[1] - minimum[1],
      maximum[2] - minimum[2]
    );
    return {
      center,
      scale: Math.min(canvas.clientWidth, canvas.clientHeight) * 0.72 / span * zoom,
      width: canvas.clientWidth,
      height: canvas.clientHeight,
    };
  }

  function project(position, transform) {
    const rotated = rotate([
      position[0] - transform.center[0],
      position[1] - transform.center[1],
      position[2] - transform.center[2],
    ]);
    const perspective = 900 / (900 + rotated[2] * 0.35);
    return [
      transform.width / 2 + rotated[0] * transform.scale * perspective,
      transform.height / 2 - rotated[1] * transform.scale * perspective,
      rotated[2],
    ];
  }

  function drawArrow(start, end, color, width) {
    const dx = end[0] - start[0];
    const dy = end[1] - start[1];
    const length = Math.hypot(dx, dy);
    if (length < 0.5) return;
    const ux = dx / length;
    const uy = dy / length;
    const head = Math.min(9, Math.max(4, length * 0.25));

    ctx.strokeStyle = color;
    ctx.fillStyle = color;
    ctx.lineWidth = width;
    ctx.beginPath();
    ctx.moveTo(start[0], start[1]);
    ctx.lineTo(end[0], end[1]);
    ctx.stroke();

    ctx.beginPath();
    ctx.moveTo(end[0], end[1]);
    ctx.lineTo(
      end[0] - ux * head - uy * head * 0.55,
      end[1] - uy * head + ux * head * 0.55
    );
    ctx.lineTo(
      end[0] - ux * head + uy * head * 0.55,
      end[1] - uy * head - ux * head * 0.55
    );
    ctx.closePath();
    ctx.fill();
  }

  function resizeCanvas() {
    const ratio = Math.min(window.devicePixelRatio || 1, 2);
    const rectangle = canvas.getBoundingClientRect();
    canvas.width = Math.max(1, Math.floor(rectangle.width * ratio));
    canvas.height = Math.max(1, Math.floor(rectangle.height * ratio));
    ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
  }

  function rebuildAtmosphereAltitudeOptions() {
    const frame = currentAtmosphereFrame();
    const select = $("altitude");
    const previous = select.value;
    while (select.firstChild) select.removeChild(select.firstChild);

    const all = document.createElement("option");
    all.value = "all";
    all.textContent = "All levels";
    select.append(all);

    if (!frame) return;
    const levels = [...new Set(frame.samples.map((sample) => sample.position[1]))]
      .sort((left, right) => left - right);
    for (const altitude of levels) {
      const option = document.createElement("option");
      option.value = String(altitude);
      option.textContent = "Y = " + fmt(altitude, 0);
      select.append(option);
    }
    if ([...select.options].some((option) => option.value === previous)) {
      select.value = previous;
    }
  }

  function updateBindingPill() {
    if (!scene) return;
    const authoritative =
      scene.source.reviewAuthority &&
      (overlay === null || overlay.source.reviewAuthority) &&
      (hydrologyComparison === null || hydrologyComparison.candidate.source.reviewAuthority);
    $("binding-pill").textContent = authoritative
      ? (overlay ? "Exact bound semantic composition" : "Exact registered artifact")
      : "Unbound local diagnostic";
    $("binding-pill").className = "pill " + (authoritative ? "good" : "warn");
    $("review-warning").hidden = authoritative;
  }

  function clearInspectorValues() {
    $("inspect-position").textContent = "click a sample";
    $("inspect-semantic").textContent = "—";
    $("inspect-vector").textContent = "—";
    $("inspect-updraft").textContent = "—";
    $("inspect-turbulence").textContent = "—";
    $("inspect-shear").textContent = "—";
    $("inspect-confidence").textContent = "—";
    $("inspect-authority").textContent = "—";
  }

  function renderInspector(sample) {
    if (!scene) return;
    $("inspect-binding").textContent = scene.source.binding;
    $("inspect-artifact").textContent =
      scene.source.artifactId || scene.source.artifactTitle || scene.source.artifactKind;
    $("inspect-overlay").textContent = overlay
      ? (overlay.source.artifactId || overlay.source.artifactTitle || overlay.source.artifactKind) +
        (hydrologyComparison
          ? " · candidate " +
            (hydrologyComparison.candidate.source.artifactId ||
              hydrologyComparison.candidate.source.artifactTitle ||
              hydrologyComparison.candidate.source.artifactKind)
          : "")
      : "none";
    $("inspect-sha").textContent = scene.source.sourceSha || "unbound";
    $("inspect-provider").textContent =
      [
        scene.provider?.label,
        scene.provider?.version,
      ].filter(Boolean).join(" · ") || "—";
    updateBindingPill();
    $("owner-semantic").textContent = overlay
      ? scene.ownership.semanticOwner + " · " + overlay.ownership.causeFieldOwner
      : (scene.ownership.semanticOwner || "—");
    $("owner-persistence").textContent = scene.ownership.persistenceOwner || "—";
    $("owner-backend-neutral").textContent =
      scene.ownership.backendNeutral ? "yes" : "no";

    clearInspectorValues();
    if (!sample) return;

    $("inspect-position").textContent =
      "[" + sample.position.map((value) => fmt(value, 1)).join(", ") + "]";

    if (sample.overlayKind === "HYDROLOGY_COMPARISON_SAMPLE") {
      const deltas = sample.deltas;
      $("inspect-semantic").textContent =
        "Candidate − reference · runoff " + fmt(deltas.runoffPotential) +
        " · retention " + fmt(deltas.retentionPotential) +
        " · drainage " + fmt(deltas.drainagePotential) +
        " · outflow " + fmt(deltas.outflowPotential);
      $("inspect-authority").textContent =
        (hydrologyComparison.comparison.reviewAuthority
          ? "Bound hydrology comparison"
          : "UNBOUND LOCAL DIAGNOSTIC comparison — not review authority") +
        " · exact terrain " + scene.source.artifactDigest;
      return;
    }

    if (sample.overlayKind === "HYDROLOGY_FIELD_COMPARISON_SAMPLE") {
      const deltaLabel = $("comparison-mode").selectedOptions[0]?.textContent || "projection";
      const surface = sample.surfaceDeltaY === null
        ? "n/a"
        : fmt(sample.surfaceDeltaY) + " world units";
      const waterSurface = sample.waterSurfaceDeltaY === null
        ? "n/a"
        : fmt(sample.waterSurfaceDeltaY) + " world units";
      const waterDepth = sample.waterDepthDelta === null
        ? "n/a"
        : fmt(sample.waterDepthDelta) + " world units";
      const wetState = sample.referenceWet === null || sample.candidateWet === null
        ? "wet state unavailable"
        : (sample.referenceWet ? "wet" : "dry") + " → " +
          (sample.candidateWet ? "wet" : "dry");
      $("inspect-semantic").textContent =
        sample.coverage + " · " + deltaLabel +
        " · surface Δ " + surface +
        " · water surface Δ " + waterSurface +
        " · water depth Δ " + waterDepth +
        " · " + wetState;
      $("inspect-authority").textContent =
        (hydrologyComparison.comparison.reviewAuthority
          ? "Bound F4B/F4E projection comparison"
          : "UNBOUND LOCAL DIAGNOSTIC comparison — not review authority") +
        " · exact terrain " + scene.source.artifactDigest;
      return;
    }

    if (sample.overlayKind === "HYDROLOGY_CAUSE_SAMPLE") {
      $("inspect-semantic").textContent =
        "runoff " + fmt(sample.runoffPotential) +
        " · retention " + fmt(sample.retentionPotential) +
        " · drainage " + fmt(sample.drainagePotential) +
        " · outflow " + fmt(sample.outflowPotential);
      $("inspect-vector").textContent =
        "[" + fmt(sample.flowX) + ", " + fmt(sample.flowZ) + "] local flow";
      $("inspect-authority").textContent =
        "SkyIslandHydrologyField · local [" +
        sample.localPosition.map((value) => fmt(value, 1)).join(", ") + "]";
      return;
    }

    if (sample.overlayKind === "HYDROLOGY_FIELD_SAMPLE") {
      $("inspect-semantic").textContent =
        sample.zone +
        " · ΔY " + fmt(sample.terrainDeltaWorld) +
        (sample.wet ? " · wet " + fmt(sample.waterDepthWorld) : " · dry");
      $("inspect-authority").textContent =
        "F4B/F4E · " +
        (sample.provenance
          ? sample.provenance.startCell + "→" + sample.provenance.endCell +
            " · " + sample.provenance.profileKind
          : "bound field sample");
      return;
    }

    if (sample.overlayKind === "HYDROLOGY_REACH_POINT") {
      $("inspect-semantic").textContent =
        "reach " + sample.startCell + "→" + sample.endCell +
        " · Q " + fmt(sample.relativeDischarge) +
        " · half-width " + fmt(sample.bankfullHalfWidth);
      $("inspect-authority").textContent = "F4B/F4E reach centerline";
      return;
    }

    if (isTerrain()) {
      $("inspect-semantic").textContent =
        sample.semanticName + " · ordinal " + sample.semanticOrdinal;
      $("inspect-authority").textContent =
        "WorldRegionTerrain · " + scene.source.artifactDigest;
      return;
    }

    const vector = currentVector(sample);
    $("inspect-vector").textContent =
      "[" + vector.map((value) => fmt(value)).join(", ") + "] m/s";
    $("inspect-updraft").textContent = fmt(sample.verticalAir) + " m/s";
    $("inspect-turbulence").textContent = fmt(sample.turbulence);
    $("inspect-shear").textContent = fmt(sample.shear, 5);
    $("inspect-confidence").textContent = fmt(sample.confidence);
    $("inspect-authority").textContent =
      [sample.sourceLevel, sample.authority].filter(Boolean).join(" · ") || "—";
  }

  function drawAtmosphere() {
    const frame = filteredAtmosphereFrame();
    if (!frame || frame.samples.length === 0) return;

    const transform = fitTransform(frame.samples);
    const values = frame.samples.map(atmosphereColorValue);
    let minimum = Math.min(...values);
    let maximum = Math.max(...values);
    if ($("color-mode").value === "verticalAir") {
      const absolute = Math.max(Math.abs(minimum), Math.abs(maximum), 0.001);
      minimum = -absolute;
      maximum = absolute;
    }

    const multiplier = 28 * Number($("arrow-scale").value);
    projected = frame.samples.map((sample) => {
      const vector = currentVector(sample);
      const endpoint = [
        sample.position[0] + vector[0] * multiplier,
        sample.position[1] + vector[1] * multiplier,
        sample.position[2] + vector[2] * multiplier,
      ];
      return {
        sample,
        start: project(sample.position, transform),
        end: project(endpoint, transform),
        value: atmosphereColorValue(sample),
      };
    }).sort((left, right) => left.start[2] - right.start[2]);

    for (const item of projected) {
      const valueColor = diagnosticColor(item.value, minimum, maximum);
      const active = selected === item.sample;
      drawArrow(item.start, item.end, valueColor, active ? 4 : 2);
      ctx.fillStyle = active ? "#ffffff" : valueColor;
      ctx.beginPath();
      ctx.arc(item.start[0], item.start[1], active ? 4.5 : 2.5, 0, Math.PI * 2);
      ctx.fill();
    }

    $("arrow-scale-value").textContent =
      Number($("arrow-scale").value).toFixed(2) + "×";
    $("frame-summary").textContent =
      frame.samples.length + " vectors" +
      (frame.tick === null ? "" : " · tick " + frame.tick);
    if ($("dataset").value === "opportunity") {
      $("frame-value").textContent =
        $("frame").value + " / " + (frame.tick === null ? "—" : frame.tick);
    }
  }

  function terrainDisplayPoints() {
    const view = $("terrain-view").value;
    if (view === "top") {
      if (overlay && $("terrain-surface").value === "hydrology") {
        const targets = new Map(
          overlay.fieldSamples.map((sample) => [
            sample.grid.join(":"),
            sample.targetUpperY,
          ])
        );
        return scene.terrain.topSurface.map((point) => {
          const targetY = targets.get(point.gridIndex[0] + ":" + point.gridIndex[2]);
          return targetY === undefined
            ? point
            : Object.freeze({
                ...point,
                position: Object.freeze([
                  point.position[0],
                  targetY,
                  point.position[2],
                ]),
              });
        });
      }
      return scene.terrain.topSurface;
    }
    if (view === "underside") return scene.terrain.underside;
    if (view === "both") {
      return [...scene.terrain.topSurface, ...scene.terrain.underside];
    }
    return scene.terrain.sliceAtYIndex(Number($("terrain-slice").value));
  }

  function updateHydrologyControlVisibility() {
    const visible = overlay !== null;
    $("hydrology-visual-controls").hidden = !visible;
    $("comparison-controls").hidden = !visible;
    $("terrain-surface-control").hidden =
      !visible || $("terrain-view").value !== "top";
    $("hydrology-legend").hidden = !visible;
    $("legend-channel").hidden = !visible;
    $("legend-channel-width").hidden =
      !visible || !$("show-channel-width").checked;
    $("legend-water-intent").hidden =
      !visible || !$("show-water-intent").checked;
    $("legend-water-note").hidden =
      !visible || !$("show-water-intent").checked;
    $("legend-flow").hidden =
      !visible || !$("show-flow-vectors").checked;
    $("legend-flow-note").hidden =
      !visible || !$("show-flow-vectors").checked;
    $("legend-response").hidden =
      !visible || !$("show-hydrology-response").checked;
    const comparisonActive =
      hydrologyComparison !== null && $("show-hydrology-delta").checked;
    const comparisonMode = $("comparison-mode").value;
    const causeComparison = comparisonActive && comparisonMode === "causes";
    const fieldComparison = comparisonActive && comparisonMode !== "causes";
    $("cause-field-control").hidden = fieldComparison;
    $("legend-potential").hidden =
      !visible || causeComparison || fieldComparison ||
      $("hydrology-potential").value === "none";
    $("legend-potential-label").textContent =
      $("hydrology-potential").selectedOptions[0]?.textContent || "Selected cause field";
    $("legend-delta").hidden = !visible || !comparisonActive;
    $("legend-delta-note").hidden = !visible || !comparisonActive;
    $("legend-delta-label").textContent = fieldComparison
      ? (comparisonMode === "surface"
        ? "Candidate − reference surface elevation: purple − / gray 0 / orange + (world units)"
        : comparisonMode === "water-surface"
          ? "Candidate − reference water surface: purple − / gray 0 / orange + (world units)"
          : "Candidate − reference water depth: purple − / gray 0 / orange + (world units)")
      : "Candidate − reference: purple − / gray 0 / orange +";
    $("legend-delta-note").textContent = fieldComparison
      ? "Sparse affected samples show shared values and one-sided footprint changes; channel, flow, response, and water layers show the reference."
      : "Only the selected cause field is compared; channel, flow, response, and water layers show the reference.";
    $("comparison-mode").disabled = hydrologyComparison === null;
    $("show-hydrology-delta").disabled = hydrologyComparison === null;
    $("clear-hydrology-comparison").disabled = hydrologyComparison === null;
  }

  function potentialColor(value) {
    const amount = Math.max(0, Math.min(1, value));
    const red = Math.round(44 + amount * 206);
    const green = Math.round(92 + amount * 130);
    const blue = Math.round(170 - amount * 116);
    return "rgb(" + red + "," + green + "," + blue + ")";
  }

  function hydrologyDeltaColor(value) {
    const delta = Math.max(-1, Math.min(1, value));
    const neutral = [195, 197, 202];
    const endpoint = delta < 0 ? [112, 95, 186] : [210, 83, 67];
    const amount = Math.abs(delta);
    const color = neutral.map((channel, index) =>
      Math.round(channel + (endpoint[index] - channel) * amount)
    );
    return "rgb(" + color.join(",") + ")";
  }

  function fieldComparisonPosition(sample, mode) {
    if (mode === "surface") {
      return sample.candidatePosition || sample.referencePosition;
    }
    return sample.candidateWaterPosition ||
      sample.referenceWaterPosition ||
      sample.candidatePosition ||
      sample.referencePosition;
  }

  function fieldComparisonDelta(sample, mode) {
    if (sample.coverage === "candidate-only") return 1;
    if (sample.coverage === "reference-only") return -1;
    if (sample.wetTransition === "WET_ADDED") return 1;
    if (sample.wetTransition === "WET_REMOVED") return -1;
    if (mode === "surface") return sample.surfaceDeltaY;
    if (mode === "water-surface") return sample.waterSurfaceDeltaY;
    return sample.waterDepthDelta;
  }

  function fieldComparisonScale(samples, mode) {
    let maximum = 0;
    for (const sample of samples) {
      if (sample.coverage !== "shared" || sample.wetTransition) continue;
      const value = fieldComparisonDelta(sample, mode);
      if (Number.isFinite(value)) maximum = Math.max(maximum, Math.abs(value));
    }
    return maximum || 1;
  }

  function drawTerrain() {
    const points = terrainDisplayPoints();
    if (!points.length) {
      projected = [];
      $("frame-summary").textContent = "No solid semantic samples in this view";
      return;
    }
    const comparisonActive =
      hydrologyComparison !== null && $("show-hydrology-delta").checked;
    const comparisonMode = $("comparison-mode").value;
    const fitPoints = comparisonActive && comparisonMode !== "causes"
      ? points.concat(hydrologyComparison.comparison.fieldSamples.map((sample) => ({
          position: fieldComparisonPosition(sample, comparisonMode),
        })))
      : points;
    const transform = fitTransform(fitPoints);
    const radius = points.length > 15000 ? 1.2 : points.length > 5000 ? 1.8 : 2.8;
    projected = points.map((sample) => ({
      sample,
      start: project(sample.position, transform),
    })).sort((left, right) => left.start[2] - right.start[2]);

    for (const item of projected) {
      const active = selected === item.sample;
      const semanticColor = TERRAIN_COLORS[item.sample.semanticName] || "#d0d4da";
      ctx.fillStyle = active ? "#ffffff" : semanticColor;
      ctx.globalAlpha = active ? 1.0 : 0.90;
      ctx.beginPath();
      ctx.arc(
        item.start[0],
        item.start[1],
        active ? Math.max(4, radius + 1.5) : radius,
        0,
        Math.PI * 2
      );
      ctx.fill();
    }
    ctx.globalAlpha = 1.0;

    const view = $("terrain-view").value;
    const sliceSuffix = view === "slice"
      ? " · Y=" + $("terrain-slice-value").textContent
      : "";
    if (overlay) {
      drawHydrologyOverlay(transform);
    }

    updateHydrologyControlVisibility();
    $("frame-summary").textContent =
      points.length + " semantic samples · " + view + sliceSuffix +
      (overlay
        ? " · hydrology " + overlay.fieldSamples.length + " affected / " +
          overlay.reaches.length + " reaches"
        : "") +
      " · " + shortDigest(scene.source.artifactDigest);
  }

  function drawReachEnvelope(reach, terrainPoints, transform) {
    const left = [];
    const right = [];
    for (let index = 0; index < reach.points.length; index++) {
      const previous = terrainPoints[Math.max(0, index - 1)];
      const next = terrainPoints[Math.min(terrainPoints.length - 1, index + 1)];
      const dx = next[0] - previous[0];
      const dy = next[1] - previous[1];
      const length = Math.hypot(dx, dy) || 1;
      const perspective = 900 / (900 + terrainPoints[index][2] * 0.35);
      const halfWidth = Math.min(
        48,
        Math.max(
          1,
          reach.points[index].bankfullHalfWidth * transform.scale * perspective
        )
      );
      const offsetX = (-dy / length) * halfWidth;
      const offsetY = (dx / length) * halfWidth;
      left.push([terrainPoints[index][0] + offsetX, terrainPoints[index][1] + offsetY]);
      right.push([terrainPoints[index][0] - offsetX, terrainPoints[index][1] - offsetY]);
    }
    ctx.fillStyle = "rgba(225,168,74,0.18)";
    ctx.beginPath();
    left.forEach((point, index) => {
      if (index === 0) ctx.moveTo(point[0], point[1]);
      else ctx.lineTo(point[0], point[1]);
    });
    for (let index = right.length - 1; index >= 0; index--) {
      ctx.lineTo(right[index][0], right[index][1]);
    }
    ctx.closePath();
    ctx.fill();
  }

  function drawHydrologyOverlay(transform) {
    const potential = $("hydrology-potential").value;
    const comparisonActive =
      hydrologyComparison !== null && $("show-hydrology-delta").checked;
    const comparisonMode = $("comparison-mode").value;
    const causeComparison = comparisonActive && comparisonMode === "causes";
    const fieldComparison = comparisonActive && comparisonMode !== "causes";
    if (potential !== "none" && (!comparisonActive || causeComparison)) {
      const causeSamples = causeComparison
        ? hydrologyComparison.comparison.samples
        : overlay.causeSamples;
      for (const sample of causeSamples) {
        const projectedPoint = project(sample.displayPosition, transform);
        const active = selected === sample;
        ctx.fillStyle = active
          ? "#ffffff"
          : (causeComparison
            ? hydrologyDeltaColor(sample.deltas[potential])
            : potentialColor(sample[potential]));
        ctx.globalAlpha = active ? 1.0 : 0.78;
        ctx.beginPath();
        ctx.arc(projectedPoint[0], projectedPoint[1], active ? 4.5 : 2.4, 0, Math.PI * 2);
        ctx.fill();
        projected.push({ sample, start: projectedPoint });
      }
      ctx.globalAlpha = 1.0;
    }

    if ($("show-flow-vectors").checked) {
      const samples = overlay.causeSamples;
      const sampleStep = Math.max(1, Math.ceil(samples.length / 2400));
      const arrowLength = Math.max(
        overlay.gridBinding.spacingX * 2,
        overlay.binding.worldFrame.nominalRadius * 0.035
      );
      for (let index = 0; index < samples.length; index += sampleStep) {
        const sample = samples[index];
        if (Math.hypot(sample.flowX, sample.flowZ) < 1e-9) continue;
        const start = project(sample.displayPosition, transform);
        const end = project([
          sample.displayPosition[0] + sample.flowX * arrowLength,
          sample.displayPosition[1],
          sample.displayPosition[2] + sample.flowZ * arrowLength,
        ], transform);
        drawArrow(start, end, "rgba(240,245,255,0.88)", 1.15);
      }
    }

    if ($("show-hydrology-response").checked) {
      for (const sample of overlay.fieldSamples) {
        const projectedPoint = project(sample.position, transform);
        const active = selected === sample;
        ctx.fillStyle = active
          ? "#ffffff"
          : (sample.wet && $("show-water-intent").checked ? "#42a5f5" : "#d4a64f");
        ctx.globalAlpha = active ? 1.0 : 0.72;
        ctx.beginPath();
        ctx.arc(projectedPoint[0], projectedPoint[1], active ? 4.5 : 2.2, 0, Math.PI * 2);
        ctx.fill();
        projected.push({ sample, start: projectedPoint });
      }
      ctx.globalAlpha = 1.0;
    }

    if (fieldComparison) {
      const samples = hydrologyComparison.comparison.fieldSamples;
      const scale = fieldComparisonScale(samples, comparisonMode);
      for (const sample of samples) {
        const position = fieldComparisonPosition(sample, comparisonMode);
        if (!position) continue;
        const projectedPoint = project(position, transform);
        const referencePosition = comparisonMode === "surface"
          ? sample.referencePosition
          : sample.referenceWaterPosition;
        const candidatePosition = comparisonMode === "surface"
          ? sample.candidatePosition
          : sample.candidateWaterPosition;
        if (
          referencePosition &&
          candidatePosition &&
          (referencePosition[1] !== candidatePosition[1])
        ) {
          const referencePoint = project(referencePosition, transform);
          ctx.strokeStyle = "rgba(220,225,235,0.58)";
          ctx.lineWidth = 1;
          ctx.beginPath();
          ctx.moveTo(referencePoint[0], referencePoint[1]);
          ctx.lineTo(projectedPoint[0], projectedPoint[1]);
          ctx.stroke();
        }
        const delta = fieldComparisonDelta(sample, comparisonMode);
        const active = selected === sample;
        ctx.fillStyle = active
          ? "#ffffff"
          : (Number.isFinite(delta)
            ? hydrologyDeltaColor(delta / scale)
            : "#c3c5ca");
        ctx.globalAlpha = active ? 1 : 0.88;
        ctx.beginPath();
        ctx.arc(projectedPoint[0], projectedPoint[1], active ? 5 : 3.4, 0, Math.PI * 2);
        ctx.fill();
        projected.push({ sample, start: projectedPoint });
      }
      ctx.globalAlpha = 1.0;
    }

    for (const reach of overlay.reaches) {
      const terrainPoints = reach.points.map((point) =>
        project(point.position, transform)
      );
      if ($("show-channel-width").checked) {
        drawReachEnvelope(reach, terrainPoints, transform);
      }
      ctx.strokeStyle = "#e1a84a";
      ctx.lineWidth = 2.4;
      ctx.beginPath();
      terrainPoints.forEach((point, index) => {
        if (index === 0) ctx.moveTo(point[0], point[1]);
        else ctx.lineTo(point[0], point[1]);
      });
      ctx.stroke();

      if ($("show-water-intent").checked) {
        const wetSegments = [];
        let current = [];
        for (const point of reach.points) {
          if (point.waterPosition) {
            current.push(project(point.waterPosition, transform));
          } else if (current.length) {
            wetSegments.push(current);
            current = [];
          }
        }
        if (current.length) wetSegments.push(current);
        ctx.strokeStyle = "#45b9ff";
        ctx.lineWidth = 2.8;
        for (const segment of wetSegments) {
          if (segment.length < 2) continue;
          ctx.beginPath();
          segment.forEach((point, index) => {
            if (index === 0) ctx.moveTo(point[0], point[1]);
            else ctx.lineTo(point[0], point[1]);
          });
          ctx.stroke();
        }
      }

      reach.points.forEach((sample, index) => {
        projected.push({ sample, start: terrainPoints[index] });
      });
    }
    ctx.globalAlpha = 1.0;
  }

  function draw() {
    resizeCanvas();
    ctx.clearRect(0, 0, canvas.clientWidth, canvas.clientHeight);
    projected = [];
    if (!scene) return;
    if (isTerrain()) drawTerrain();
    else drawAtmosphere();
    renderInspector(selected);
  }

  function resetView() {
    yaw = -0.72;
    pitch = 0.50;
    zoom = 1.0;
    draw();
  }

  function stopPlayback() {
    if (playback !== null) clearInterval(playback);
    playback = null;
    $("play").textContent = "Play";
  }

  function togglePlayback() {
    if (!isAtmosphere()) return;
    if (playback !== null) {
      stopPlayback();
      return;
    }
    $("play").textContent = "Pause";
    playback = setInterval(() => {
      const maximum = Number($("frame").max);
      $("frame").value =
        String((Number($("frame").value) + 1) % (maximum + 1));
      selected = null;
      draw();
    }, 500);
  }

  function renderTerrainLegend() {
    const container = $("terrain-legend");
    while (container.firstChild) container.removeChild(container.firstChild);
    if (!isTerrain()) return;
    for (const semantic of scene.terrain.legend) {
      if (!semantic.solid) continue;
      const row = document.createElement("div");
      row.className = "terrain-legend-row";
      const swatch = document.createElement("span");
      swatch.className = "terrain-swatch";
      swatch.style.backgroundColor = TERRAIN_COLORS[semantic.name] || "#d0d4da";
      const label = document.createElement("span");
      label.textContent = semantic.name.replaceAll("_", " ").toLowerCase();
      row.append(swatch, label);
      container.append(row);
    }
  }

  function configureTerrainControls() {
    const grid = scene.terrain.grid;
    $("terrain-slice").min = "0";
    $("terrain-slice").max = String(grid.ySamples - 1);
    $("terrain-slice").value = String(Math.floor((grid.ySamples - 1) / 2));
    updateTerrainSliceLabel();
    $("terrain-view").value = "top";
    $("terrain-slice-control").hidden = true;
    renderTerrainLegend();
  }

  function updateTerrainSliceLabel() {
    if (!isTerrain()) {
      $("terrain-slice-value").textContent = "—";
      return;
    }
    const grid = scene.terrain.grid;
    const index = Number($("terrain-slice").value);
    const y = grid.minimumY + grid.spacingY * index;
    $("terrain-slice-value").textContent = fmt(y, 1);
  }

  function setScene(nextScene) {
    stopPlayback();
    scene = nextScene;
    overlay = null;
    hydrologyComparison = null;
    $("comparison-mode").value = "causes";
    $("show-hydrology-delta").checked = false;
    selected = null;
    $("scene-kind").textContent = scene.sceneKind;
    $("viewport-title").textContent =
      scene.source.artifactTitle ||
      (isTerrain() ? "Terrain semantic volume" : "Atmosphere semantic field");
    $("viewport-subtitle").textContent =
      scene.coordinateSystem.id + " · " + scene.source.artifactKind;
    updateBindingPill();
    $("overlay-status").textContent = "";
    $("comparison-status").textContent = "Attach a reference hydrology artifact first.";
    updateHydrologyControlVisibility();

    $("atmosphere-controls").hidden = !isAtmosphere();
    $("terrain-controls").hidden = !isTerrain();

    if (isAtmosphere()) {
      const hasOpportunity = scene.opportunity.frames.length > 0;
      $("dataset").disabled = !hasOpportunity;
      $("frame").max = String(Math.max(0, scene.opportunity.frames.length - 1));
      $("frame").value = "0";
      $("dataset").value = "snapshot";
      $("time-controls").hidden = true;
      rebuildAtmosphereAltitudeOptions();
    } else {
      configureTerrainControls();
    }

    renderInspector(null);
    draw();
  }

  async function loadRegisteredArtifact() {
    const select = $("artifact-select");
    const artifactId = select.value;
    if (!artifactId) return;
    const option = select.selectedOptions[0];
    $("source-status").textContent = "Loading exact registered artifact…";

    const response = await api(
      "/api/v1/artifacts/" + encodeURIComponent(artifactId) + "/content"
    );
    if (!response.ok) {
      throw new Error("artifact content request failed: HTTP " + response.status);
    }
    const artifact = await response.json();
    const nextScene = window.SkyforgeStudioScene.adaptArtifact(artifact, {
      binding: "REGISTERED_ARTIFACT",
      artifactId,
      artifactTitle: option.dataset.title || artifactId,
      sourceSha: option.dataset.sha || "",
      reviewAuthority: true,
    });
    setScene(nextScene);
    $("source-status").textContent = "Loaded exact artifact " + artifactId + ".";
  }

  async function loadArtifactCatalog() {
    const response = await api("/api/v1/artifacts");
    if (!response.ok) {
      throw new Error("artifact catalog request failed: HTTP " + response.status);
    }
    const payload = await response.json();
    artifactCatalog = payload.artifacts || [];
    const select = $("artifact-select");
    const overlaySelect = $("overlay-artifact-select");
    const comparisonSelect = $("comparison-artifact-select");
    while (select.firstChild) select.removeChild(select.firstChild);
    while (overlaySelect.firstChild) overlaySelect.removeChild(overlaySelect.firstChild);
    while (comparisonSelect.firstChild) comparisonSelect.removeChild(comparisonSelect.firstChild);

    const placeholder = document.createElement("option");
    placeholder.value = "";
    placeholder.textContent = "Select a registered JSON artifact";
    select.append(placeholder);

    const overlayPlaceholder = document.createElement("option");
    overlayPlaceholder.value = "";
    overlayPlaceholder.textContent = "Select a registered hydrology JSON";
    overlaySelect.append(overlayPlaceholder);

    const comparisonPlaceholder = document.createElement("option");
    comparisonPlaceholder.value = "";
    comparisonPlaceholder.textContent = "Select a registered candidate JSON";
    comparisonSelect.append(comparisonPlaceholder);

    for (const artifact of artifactCatalog) {
      if (artifact.kind !== "FILE") continue;
      const mediaType = String(artifact.file?.media_type || "").toLowerCase();
      if (!mediaType.includes("json")) continue;

      const option = document.createElement("option");
      option.value = artifact.artifact_id;
      option.textContent = artifact.title || artifact.artifact_id;
      option.dataset.title = artifact.title || artifact.artifact_id;
      option.dataset.sha = artifact.source_sha || "";
      select.append(option);

      const overlayOption = document.createElement("option");
      overlayOption.value = artifact.artifact_id;
      overlayOption.textContent = artifact.title || artifact.artifact_id;
      overlayOption.dataset.title = artifact.title || artifact.artifact_id;
      overlayOption.dataset.sha = artifact.source_sha || "";
      overlaySelect.append(overlayOption);
      const comparisonOption = overlayOption.cloneNode(true);
      comparisonSelect.append(comparisonOption);
    }

    $("source-status").textContent = select.options.length > 1
      ? "Choose a registered JSON artifact, or import a local diagnostic."
      : "No registered JSON semantic artifacts are currently available; local diagnostics remain available.";
  }

  function setOverlay(nextOverlay) {
    overlay = nextOverlay;
    hydrologyComparison = null;
    $("comparison-mode").value = "causes";
    $("show-hydrology-delta").checked = false;
    selected = null;
    updateHydrologyControlVisibility();
    $("overlay-status").textContent =
      "Attached " +
      (overlay.source.artifactId || overlay.source.artifactTitle || "hydrology overlay") +
      " · " + overlay.binding.associationToken;
    $("comparison-status").textContent =
      "Attach a candidate with the same exact terrain and AUTH-0046 binding.";
    updateBindingPill();
    renderInspector(null);
    draw();
  }

  async function loadRegisteredOverlay() {
    if (!isTerrain()) {
      throw new Error("load a terrain semantic volume before attaching hydrology");
    }
    const select = $("overlay-artifact-select");
    const artifactId = select.value;
    if (!artifactId) return;
    const option = select.selectedOptions[0];
    const response = await api(
      "/api/v1/artifacts/" + encodeURIComponent(artifactId) + "/content"
    );
    if (!response.ok) {
      throw new Error("overlay content request failed: HTTP " + response.status);
    }
    const artifact = await response.json();
    setOverlay(window.SkyforgeStudioScene.adaptOverlayArtifact(artifact, scene, {
      binding: "REGISTERED_ARTIFACT",
      artifactId,
      artifactTitle: option.dataset.title || artifactId,
      sourceSha: option.dataset.sha || "",
      reviewAuthority: true,
    }));
  }

  function displayHydrologyComparison(candidate) {
    const comparison = window.SkyforgeStudioScene.compareHydrologyLayers(
      overlay,
      candidate
    );
    hydrologyComparison = { candidate, comparison };
    $("comparison-mode").value = "causes";
    $("show-hydrology-delta").checked = true;
    if ($("hydrology-potential").value === "none") {
      $("hydrology-potential").value = "runoffPotential";
    }
    selected = null;
    const referenceName =
      overlay.source.artifactId || overlay.source.artifactTitle || overlay.source.artifactKind;
    const candidateName =
      candidate.source.artifactId || candidate.source.artifactTitle || candidate.source.artifactKind;
    $("comparison-status").textContent =
      "Reference: " + referenceName + " · Candidate: " + candidateName +
      (comparison.reviewAuthority
        ? " · registered comparison"
        : " · UNBOUND LOCAL DIAGNOSTIC — not review authority");
    updateHydrologyControlVisibility();
    updateBindingPill();
    renderInspector(null);
    draw();
  }

  async function loadRegisteredHydrologyComparison() {
    if (!isTerrain() || !overlay) {
      throw new Error("attach a reference hydrology artifact before loading a candidate");
    }
    const select = $("comparison-artifact-select");
    const artifactId = select.value;
    if (!artifactId) return;
    const option = select.selectedOptions[0];
    $("comparison-status").textContent = "Loading and checking candidate binding…";
    const response = await api(
      "/api/v1/artifacts/" + encodeURIComponent(artifactId) + "/content"
    );
    if (!response.ok) {
      throw new Error("candidate artifact content request failed: HTTP " + response.status);
    }
    const artifact = await response.json();
    const candidate = window.SkyforgeStudioScene.adaptOverlayArtifact(artifact, scene, {
      binding: "REGISTERED_ARTIFACT",
      artifactId,
      artifactTitle: option.dataset.title || artifactId,
      sourceSha: option.dataset.sha || "",
      reviewAuthority: true,
    });
    displayHydrologyComparison(candidate);
  }

  async function loadLocalHydrologyComparison() {
    if (!isTerrain() || !overlay) {
      throw new Error("attach a reference hydrology artifact before loading a candidate");
    }
    const file = $("local-comparison-file").files?.[0];
    if (!file) {
      throw new Error("choose a local candidate hydrology JSON file first");
    }
    $("comparison-status").textContent = "Loading local candidate and checking exact binding…";
    const artifact = JSON.parse(await file.text());
    const candidate = window.SkyforgeStudioScene.adaptOverlayArtifact(artifact, scene, {
      binding: "UNBOUND_LOCAL",
      artifactTitle: file.name,
      reviewAuthority: false,
    });
    displayHydrologyComparison(candidate);
  }

  function clearHydrologyComparison() {
    hydrologyComparison = null;
    $("comparison-mode").value = "causes";
    $("show-hydrology-delta").checked = false;
    $("local-comparison-file").value = "";
    $("comparison-status").textContent =
      "Attach a candidate with the same exact terrain and AUTH-0046 binding.";
    selected = null;
    updateHydrologyControlVisibility();
    updateBindingPill();
    renderInspector(null);
    draw();
  }

  function clearOverlay() {
    overlay = null;
    hydrologyComparison = null;
    $("show-hydrology-delta").checked = false;
    $("comparison-status").textContent = "Attach a reference hydrology artifact first.";
    selected = null;
    updateHydrologyControlVisibility();
    $("overlay-status").textContent = "";
    updateBindingPill();
    renderInspector(null);
    draw();
  }

  async function connect(event) {
    if (event) event.preventDefault();
    token = $("api-token").value.trim();
    if (!token) return;
    try {
      window.sessionStorage?.setItem(TOKEN_KEY, token);
    } catch {
      // Local diagnostics remain available when browser storage is restricted.
    }
    $("auth-error").textContent = "";

    try {
      const response = await api("/api/v1/development-state");
      if (!response.ok) {
        throw new Error("authorization failed: HTTP " + response.status);
      }
      await loadArtifactCatalog();
      showConnectedMode();
    } catch (error) {
      showLocalMode();
      $("auth-error").textContent = String(error.message || error);
    }
  }

  $("auth-form").addEventListener("submit", connect);
  $("forget-token").addEventListener("click", () => {
    token = "";
    try {
      window.sessionStorage?.removeItem(TOKEN_KEY);
    } catch {
      // Forgetting a token is best-effort when browser storage is restricted.
    }
    $("api-token").value = "";
    $("auth-error").textContent = "";
    showLocalMode();
  });

  $("load-artifact").addEventListener("click", () => {
    loadRegisteredArtifact().catch((error) => {
      $("source-status").textContent = String(error.message || error);
    });
  });

  $("local-file").addEventListener("change", async (event) => {
    const file = event.target.files?.[0];
    if (!file) return;
    try {
      const artifact = JSON.parse(await file.text());
      setScene(window.SkyforgeStudioScene.adaptArtifact(artifact, {
        binding: "UNBOUND_LOCAL",
        artifactTitle: file.name,
        reviewAuthority: false,
      }));
      $("source-status").textContent =
        "Loaded local diagnostic " + file.name + "; not review authority.";
    } catch (error) {
      $("source-status").textContent = String(error.message || error);
    }
  });

  function loadLocalPair(terrainArtifact, terrainTitle, hydrologyArtifact, hydrologyTitle) {
    const nextScene = window.SkyforgeStudioScene.adaptArtifact(terrainArtifact, {
      binding: "UNBOUND_LOCAL",
      artifactTitle: terrainTitle,
      reviewAuthority: false,
    });
    const nextOverlay = window.SkyforgeStudioScene.adaptOverlayArtifact(
      hydrologyArtifact,
      nextScene,
      {
        binding: "UNBOUND_LOCAL",
        artifactTitle: hydrologyTitle,
        reviewAuthority: false,
      }
    );

    setScene(nextScene);
    setOverlay(nextOverlay);
    $("source-status").textContent =
      "Loaded " + terrainTitle + " with " + hydrologyTitle +
      "; paired local diagnostics only, not review authority.";
  }

  $("local-pair-files").addEventListener("change", async (event) => {
    const files = Array.from(event.target.files || []);
    if (files.length === 0) return;

    try {
      if (files.length !== 2) {
        throw new Error(
          "Select both JSON files: the terrain semantic volume and its bound hydrology layer."
        );
      }
      const entries = await Promise.all(files.map(async (file) => ({
        file,
        artifact: JSON.parse(await file.text()),
      })));
      const terrainEntry = entries.find(
        (entry) => entry.artifact?.artifact_kind === "SKYFORGE_TERRAIN_SEMANTIC_VOLUME"
      );
      const hydrologyEntry = entries.find(
        (entry) => entry.artifact?.artifact_kind === "SKYFORGE_BOUND_HYDROLOGY_SEMANTIC_LAYER"
      );
      if (!terrainEntry || !hydrologyEntry) {
        throw new Error(
          "Choose one terrain semantic volume JSON and one bound hydrology layer JSON."
        );
      }

      loadLocalPair(
        terrainEntry.artifact,
        terrainEntry.file.name,
        hydrologyEntry.artifact,
        hydrologyEntry.file.name
      );
    } catch (error) {
      $("source-status").textContent = String(error.message || error);
    } finally {
      event.target.value = "";
    }
  });

  $("open-bundled-sample").addEventListener("click", () => {
    const sample = window.SKYFORGE_STUDIO_SAMPLE;
    if (!sample) {
      $("source-status").textContent = "The included S2 specimen is unavailable.";
      return;
    }
    try {
      loadLocalPair(
        sample.terrain,
        "terrain-semantic-volume.json",
        sample.hydrology,
        "hydrology-semantic-layer.json"
      );
    } catch (error) {
      $("source-status").textContent = String(error.message || error);
    }
  });

  function configureBundledSample() {
    $("bundled-sample-callout").hidden = !(
      window.SKYFORGE_STUDIO_SAMPLE?.terrain &&
      window.SKYFORGE_STUDIO_SAMPLE?.hydrology
    );
  }

  function initializeWorldBrief() {
    const storageKey = "skyforge-studio-world-brief-library-v1";
    const titleInput = $("world-brief-title");
    const intentInput = $("world-brief-intent");
    const librarySelect = $("world-brief-library");
    const form = $("world-brief-form");
    const statePill = $("world-brief-state");
    const status = $("world-brief-status");
    const savedBriefs = $("world-brief-count");
    let library = window.SkyforgeStudioWorldBrief.createLibrary();
    let activeBriefId = null;
    let isDirty = false;
    let storageAvailable = true;
    let saveTimer = null;

    function newId() {
      if (window.crypto && typeof window.crypto.randomUUID === "function") return window.crypto.randomUUID();
      return "brief-" + Date.now().toString(36) + "-" + Math.random().toString(36).slice(2, 12);
    }

    function setStatus(message, saved = false, severity = "muted") {
      status.textContent = message;
      statePill.textContent = saved ? "Saved locally" : (isDirty ? "Unsaved changes" : "Local draft");
      statePill.className = "pill " + (saved ? "good" : "warn");
      status.dataset.severity = severity;
      savedBriefs.textContent = library.briefs.length === 1 ? "1 saved brief in this browser" :
        library.briefs.length + " saved briefs in this browser";
    }

    function refreshLibrarySelect() {
      while (librarySelect.firstChild) librarySelect.removeChild(librarySelect.firstChild);
      const empty = document.createElement("option");
      empty.value = "";
      empty.textContent = "New brief";
      librarySelect.append(empty);
      for (const brief of library.briefs) {
        const option = document.createElement("option");
        option.value = brief.id;
        option.textContent = brief.title;
        librarySelect.append(option);
      }
      librarySelect.value = activeBriefId || "";
    }

    function loadBrief(brief) {
      activeBriefId = brief?.id || null;
      titleInput.value = brief?.title || "Untitled world";
      intentInput.value = brief?.intent || "";
      isDirty = false;
      refreshLibrarySelect();
      setStatus(brief
        ? "Saved in this browser. It has not been sent to a generator."
        : "This brief stays in this browser. It is not evaluated or sent to a service.",
        Boolean(brief));
    }

    function readLibrary() {
      try {
        const storage = window.localStorage;
        if (!storage) throw new Error("browser storage is unavailable");
        const raw = storage.getItem(storageKey);
        if (!raw) { loadBrief(null); return; }
        library = window.SkyforgeStudioWorldBrief.parseLibrary(raw);
        const active = library.briefs.find((brief) => brief.id === library.active_brief_id) || null;
        loadBrief(active);
      } catch (error) {
        storageAvailable = false;
        loadBrief(null);
        setStatus("Saved briefs could not be read (" + String(error.message || error) +
          "). You can still download a JSON copy.", false, "error");
      }
    }

    function saveCurrentBrief(force = false) {
      if (!isDirty && !force) return true;
      if (!storageAvailable) {
        setStatus("Browser storage is unavailable. You can still download this brief as JSON.", false, "error");
        return false;
      }
      try {
        const existing = library.briefs.find((brief) => brief.id === activeBriefId) || null;
        const now = new Date().toISOString();
        const draft = window.SkyforgeStudioWorldBrief.create(activeBriefId || newId(),
          titleInput.value, intentInput.value, existing?.created_at || now, now);
        const nextBriefs = [draft, ...library.briefs.filter((brief) => brief.id !== draft.id)];
        const nextLibrary = window.SkyforgeStudioWorldBrief.createLibrary(nextBriefs, draft.id);
        window.localStorage.setItem(storageKey, window.SkyforgeStudioWorldBrief.serializeLibrary(nextLibrary));
        library = nextLibrary;
        activeBriefId = draft.id;
        isDirty = false;
        refreshLibrarySelect();
        setStatus("Saved automatically in this browser. No generator has evaluated it.", true);
        return true;
      } catch (error) {
        setStatus(String(error.message || error), false, "error");
        return false;
      }
    }

    function scheduleSave() {
      isDirty = true;
      statePill.textContent = "Unsaved changes";
      statePill.className = "pill warn";
      status.textContent = "Saving this brief in your browser…";
      if (saveTimer !== null) window.clearTimeout(saveTimer);
      saveTimer = window.setTimeout(() => { saveTimer = null; saveCurrentBrief(); }, 450);
    }

    function exportBrief() {
      try {
        const existing = library.briefs.find((brief) => brief.id === activeBriefId) || null;
        const now = new Date().toISOString();
        const draft = window.SkyforgeStudioWorldBrief.create(activeBriefId || newId(),
          titleInput.value, intentInput.value, existing?.created_at || now, now);
        const blob = new Blob([window.SkyforgeStudioWorldBrief.serialize(draft)], { type: "application/json" });
        const url = URL.createObjectURL(blob);
        const link = document.createElement("a");
        link.href = url;
        link.download = draft.title.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "") +
          ".skyforge-brief.json";
        link.click();
        window.setTimeout(() => URL.revokeObjectURL(url), 0);
        setStatus("Downloaded a local draft JSON. It is not a generated world or review artifact.");
      } catch (error) {
        setStatus(String(error.message || error), false, "error");
      }
    }

    async function importBrief(event) {
      const file = event.target.files?.[0];
      if (!file) return;
      try {
        if (file.size > 1_000_000) throw new Error("world brief files must be smaller than 1 MB");
        const imported = window.SkyforgeStudioWorldBrief.parse(await file.text());
        if (saveTimer !== null) {
          window.clearTimeout(saveTimer);
          saveTimer = null;
          if (!saveCurrentBrief()) return;
        }
        activeBriefId = null;
        titleInput.value = imported.title;
        intentInput.value = imported.intent;
        isDirty = true;
        setStatus("Imported as a new local draft. It will save in this browser automatically.", false);
        scheduleSave();
      } catch (error) {
        setStatus("Could not import this brief: " + String(error.message || error), false, "error");
      } finally {
        event.target.value = "";
      }
    }

    form.addEventListener("submit", (event) => {
      event.preventDefault();
      if (saveTimer !== null) { window.clearTimeout(saveTimer); saveTimer = null; }
      isDirty = true;
      saveCurrentBrief(true);
    });
    for (const input of [titleInput, intentInput]) input.addEventListener("input", scheduleSave);
    $("world-brief-new").addEventListener("click", () => {
      if (saveTimer !== null) {
        window.clearTimeout(saveTimer); saveTimer = null;
        if (!saveCurrentBrief()) return;
      } else if (isDirty && !saveCurrentBrief()) return;
      activeBriefId = null;
      loadBrief(null);
      titleInput.focus();
    });
    librarySelect.addEventListener("change", () => {
      const requestedId = librarySelect.value;
      if (saveTimer !== null) { window.clearTimeout(saveTimer); saveTimer = null; }
      if (isDirty && !saveCurrentBrief()) { librarySelect.value = activeBriefId || ""; return; }
      loadBrief(library.briefs.find((brief) => brief.id === requestedId) || null);
    });
    $("world-brief-download").addEventListener("click", exportBrief);
    $("world-brief-import-button").addEventListener("click", () => $("world-brief-import-file").click());
    $("world-brief-import-file").addEventListener("change", importBrief);
    readLibrary();
  }

  function configureWorkspaceNavigation() {
    for (const button of document.querySelectorAll("[data-workspace-view]")) {
      button.addEventListener("click", () => selectWorkspaceView(button.dataset.workspaceView));
    }
    syncWorkspaceVisibility();
  }

  $("load-overlay").addEventListener("click", () => {
    loadRegisteredOverlay().catch((error) => {
      $("overlay-status").textContent = String(error.message || error);
    });
  });

  $("load-comparison-overlay").addEventListener("click", () => {
    loadRegisteredHydrologyComparison().catch((error) => {
      hydrologyComparison = null;
      $("show-hydrology-delta").checked = false;
      updateHydrologyControlVisibility();
      $("comparison-status").textContent = String(error.message || error);
      updateBindingPill();
      draw();
    });
  });

  $("load-local-comparison").addEventListener("click", () => {
    loadLocalHydrologyComparison().catch((error) => {
      hydrologyComparison = null;
      $("show-hydrology-delta").checked = false;
      updateHydrologyControlVisibility();
      $("comparison-status").textContent = String(error.message || error);
      updateBindingPill();
      draw();
    });
  });

  $("show-hydrology-delta").addEventListener("change", () => {
    selected = null;
    updateHydrologyControlVisibility();
    draw();
  });

  $("comparison-mode").addEventListener("change", () => {
    selected = null;
    updateHydrologyControlVisibility();
    draw();
  });

  $("clear-hydrology-comparison").addEventListener("click", clearHydrologyComparison);

  $("local-overlay-file").addEventListener("change", async (event) => {
    const file = event.target.files?.[0];
    if (!file) return;
    try {
      if (!isTerrain()) {
        throw new Error("load a terrain semantic volume before attaching hydrology");
      }
      const artifact = JSON.parse(await file.text());
      setOverlay(window.SkyforgeStudioScene.adaptOverlayArtifact(artifact, scene, {
        binding: "UNBOUND_LOCAL",
        artifactTitle: file.name,
        reviewAuthority: false,
      }));
      $("overlay-status").textContent =
        "Attached local hydrology diagnostic " + file.name +
        "; composite is not review authority.";
    } catch (error) {
      $("overlay-status").textContent = String(error.message || error);
    }
  });

  $("clear-overlay").addEventListener("click", clearOverlay);

  $("dataset").addEventListener("change", () => {
    stopPlayback();
    selected = null;
    $("time-controls").hidden = $("dataset").value !== "opportunity";
    rebuildAtmosphereAltitudeOptions();
    draw();
  });
  $("vector-mode").addEventListener("change", () => {
    renderInspector(selected);
    draw();
  });
  $("color-mode").addEventListener("change", draw);
  $("altitude").addEventListener("change", draw);
  $("arrow-scale").addEventListener("input", draw);
  $("frame").addEventListener("input", () => {
    selected = null;
    draw();
  });
  $("play").addEventListener("click", togglePlayback);

  $("terrain-view").addEventListener("change", () => {
    selected = null;
    $("terrain-slice-control").hidden = $("terrain-view").value !== "slice";
    updateHydrologyControlVisibility();
    draw();
  });
  $("terrain-surface").addEventListener("change", draw);
  $("hydrology-potential").addEventListener("change", draw);
  $("show-flow-vectors").addEventListener("change", draw);
  $("show-channel-width").addEventListener("change", draw);
  $("show-hydrology-response").addEventListener("change", draw);
  $("show-water-intent").addEventListener("change", draw);
  $("terrain-slice").addEventListener("input", () => {
    selected = null;
    updateTerrainSliceLabel();
    draw();
  });

  $("reset-view").addEventListener("click", resetView);

  canvas.addEventListener("pointerdown", (event) => {
    dragging = true;
    lastX = event.clientX;
    lastY = event.clientY;
    canvas.setPointerCapture(event.pointerId);
  });
  canvas.addEventListener("pointermove", (event) => {
    if (!dragging) return;
    yaw += (event.clientX - lastX) * 0.008;
    pitch = Math.max(
      -1.35,
      Math.min(1.35, pitch + (event.clientY - lastY) * 0.008)
    );
    lastX = event.clientX;
    lastY = event.clientY;
    draw();
  });
  canvas.addEventListener("pointerup", () => {
    dragging = false;
  });
  canvas.addEventListener("pointercancel", () => {
    dragging = false;
  });
  canvas.addEventListener("wheel", (event) => {
    event.preventDefault();
    zoom = Math.max(
      0.45,
      Math.min(3.5, zoom * (event.deltaY > 0 ? 0.9 : 1.1))
    );
    draw();
  }, { passive: false });
  canvas.addEventListener("click", (event) => {
    const rectangle = canvas.getBoundingClientRect();
    const x = event.clientX - rectangle.left;
    const y = event.clientY - rectangle.top;
    let best = null;
    let distance = 14;
    for (const item of projected) {
      const current = Math.hypot(item.start[0] - x, item.start[1] - y);
      if (current < distance) {
        distance = current;
        best = item;
      }
    }
    if (best) {
      selected = best.sample;
      draw();
    }
  });
  window.addEventListener("resize", draw);

  configureWorkspaceNavigation();
  initializeWorldBrief();
  showLocalMode();
  configureBundledSample();
  if (token) {
    $("api-token").value = token;
  }
})();
