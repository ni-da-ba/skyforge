(() => {
  "use strict";

  const TOKEN_KEY = "skyforge-development-api-token";
  const WORLD_BRIEF_LIBRARY_STORAGE_KEY = "skyforge-studio-world-brief-library-v1";
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

  function mergeWorldBriefLibraries(currentValue, importedValue) {
    const current = parseWorldBriefLibrary(currentValue);
    const imported = parseWorldBriefLibrary(importedValue);
    const mergedBriefs = current.briefs.slice();
    const byId = new Map(mergedBriefs.map((brief) => [brief.id, brief]));

    for (const brief of imported.briefs) {
      const existing = byId.get(brief.id);
      if (existing) {
        if (JSON.stringify(existing) !== JSON.stringify(brief)) {
          throw new Error("brief library contains a different local draft with the same ID");
        }
        continue;
      }
      mergedBriefs.push(brief);
      byId.set(brief.id, brief);
    }

    const activeBriefId = current.active_brief_id && byId.has(current.active_brief_id)
      ? current.active_brief_id
      : imported.active_brief_id;
    return createWorldBriefLibrary(mergedBriefs, activeBriefId);
  }

  function removeWorldBriefFromLibrary(value, briefId) {
    const current = parseWorldBriefLibrary(value);
    const remaining = current.briefs.filter((brief) => brief.id !== briefId);
    if (remaining.length === current.briefs.length) {
      throw new Error("saved world brief does not exist");
    }
    const activeBriefId = current.active_brief_id === briefId
      ? (remaining[0]?.id || null)
      : current.active_brief_id;
    return createWorldBriefLibrary(remaining, activeBriefId);
  }

  window.SkyforgeStudioWorldBrief = Object.freeze({
    create: createWorldBrief,
    parse: parseWorldBrief,
    serialize: serializeWorldBrief,
    createLibrary: createWorldBriefLibrary,
    parseLibrary: parseWorldBriefLibrary,
    serializeLibrary: serializeWorldBriefLibrary,
    mergeLibraries: mergeWorldBriefLibraries,
    removeBrief: removeWorldBriefFromLibrary,
    documentType: WORLD_BRIEF_DOCUMENT_TYPE,
    libraryType: WORLD_BRIEF_LIBRARY_TYPE,
  });
  // END STUDIO WORLD BRIEF DOCUMENT CONTRACT

  // BEGIN STUDIO SELECTED SAMPLE TRACE DOCUMENT CONTRACT
  const SAMPLE_TRACE_DOCUMENT_TYPE = "SKYFORGE_STUDIO_SELECTED_SAMPLE_TRACE";
  const SAMPLE_TRACE_FORMAT_VERSION = 1;

  function sampleTraceSource(source) {
    if (!source) return null;
    return Object.freeze({
      binding: source.binding || "UNBOUND_LOCAL",
      artifactId: source.artifactId || null,
      artifactTitle: source.artifactTitle || null,
      sourceSha: source.sourceSha || null,
      reviewAuthority: source.reviewAuthority === true,
      artifactKind: source.artifactKind || null,
      artifactDigest: source.artifactDigest || null,
    });
  }

  function createSelectedSampleTrace(input) {
    if (!input || !input.scene || !input.scene.source) {
      throw new Error("selected sample trace requires a scene with source metadata");
    }
    if (!input.sample || typeof input.sample !== "object" || Array.isArray(input.sample)) {
      throw new Error("selected sample trace requires a selected sample object");
    }

    const sceneSource = sampleTraceSource(input.scene.source);
    const overlaySource = sampleTraceSource(input.overlay?.source);
    const comparison = input.comparison ? Object.freeze({
      associationToken: input.comparison.associationToken || null,
      referenceSource: sampleTraceSource(input.comparison.referenceSource),
      candidateSource: sampleTraceSource(input.comparison.candidateSource),
      reviewAuthority: input.comparison.reviewAuthority === true,
      field: input.comparison.field || null,
    }) : null;

    return Object.freeze({
      document_type: SAMPLE_TRACE_DOCUMENT_TYPE,
      format_version: SAMPLE_TRACE_FORMAT_VERSION,
      review_authority: sceneSource.reviewAuthority === true &&
        (!overlaySource || overlaySource.reviewAuthority === true) &&
        (!comparison || comparison.reviewAuthority === true),
      scene_kind: input.scene.sceneKind || null,
      sources: Object.freeze({
        scene: sceneSource,
        overlay: overlaySource,
        reference: comparison?.referenceSource || null,
        candidate: comparison?.candidateSource || null,
      }),
      coordinate_system: input.scene.coordinateSystem || null,
      provider: input.scene.provider || null,
      overlay: input.overlay ? Object.freeze({
        terrainSemanticSha256: input.overlay.terrainSemanticSha256 || null,
        binding: input.overlay.binding || null,
        gridBinding: input.overlay.gridBinding || null,
      }) : null,
      comparison: comparison ? Object.freeze({
        associationToken: comparison.associationToken,
        reviewAuthority: comparison.reviewAuthority,
        field: comparison.field,
      }) : null,
      selected_sample: input.sample,
    });
  }

  function selectedSampleTraceFilename(scene, sample) {
    if (!scene || !scene.source || !sample || typeof sample !== "object") {
      throw new Error("selected sample trace filename requires scene and sample data");
    }
    const base = String(
      scene.source.artifactTitle || scene.source.artifactId || scene.sceneKind || "studio"
    ).toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "").slice(0, 48) || "studio";
    const position = Array.isArray(sample.position) && sample.position.length === 3
      ? sample.position.map((value) => {
        const number = Number(value);
        if (!Number.isFinite(number)) return "na";
        return number.toFixed(1).replace(/-/g, "m").replace(/\./g, "p");
      }).join("-")
      : "selected";
    return base + "-sample-" + position + ".skyforge-trace.json";
  }

  window.SkyforgeStudioSampleTrace = Object.freeze({
    create: createSelectedSampleTrace,
    filename: selectedSampleTraceFilename,
    documentType: SAMPLE_TRACE_DOCUMENT_TYPE,
    formatVersion: SAMPLE_TRACE_FORMAT_VERSION,
  });
  // END STUDIO SELECTED SAMPLE TRACE DOCUMENT CONTRACT

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
  let workspaceSceneArtifact = null;
  let workspaceSceneTitle = "";
  let workspaceSceneJson = "";
  let workspaceOverlayArtifact = null;
  let workspaceOverlayTitle = "";
  let workspaceOverlayJson = "";
  let hydrologyComparison = null;
  let importedHydrologyComparisonReport = null;
  let importedComparisonReportPage = 0;
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
  let studioWorkspaceView = "home";
  let terrainSemanticComparison = null;
  let terrainComparisonLibraryRepository = null;
  let terrainComparisonLibraryRecords = [];
  let terrainComparisonLibraryBusy = false;
  let terrainComparisonPage = 0;
  let terrainComparisonPlot = null;
  let terrainSelectedColumn = null;
  let workspaceSessionEnabled = false;
  let workspaceSessionReady = false;
  let workspaceSessionRestoring = false;
  let workspaceSessionHasSaved = false;
  let workspaceSessionSaveTimer = null;
  let workspaceSessionGeneration = 0;
  let workspaceSessionSaveChain = Promise.resolve();
  const workspaceSessionApi = window.SkyforgeStudioWorkspaceSession;
  const workspaceSessionController = workspaceSessionApi
    ? workspaceSessionApi.createController(
      workspaceSessionApi.createIndexedDbStore(
        window.indexedDB,
        window.SkyforgeStudioWorkspacePackage.maximumFileBytes
      )
    )
    : null;

  const $ = (id) => document.getElementById(id);
  const canvas = $("viewport");
  const ctx = canvas.getContext("2d");

  function syncWorkspaceVisibility() {
    $("studio-home-view").hidden = studioWorkspaceView !== "home";
    $("studio-content").hidden = studioWorkspaceView !== "inspect";
    $("world-brief-view").hidden = studioWorkspaceView !== "brief";
    $("studio-regional-view").hidden = studioWorkspaceView !== "regional";
    $("regional-comparison-view").hidden = studioWorkspaceView !== "regional-comparison";
    $("studio-terrain-comparison-view").hidden = studioWorkspaceView !== "terrain-comparison";
    for (const button of document.querySelectorAll("[data-workspace-view]")) {
      button.setAttribute("aria-pressed", String(button.dataset.workspaceView === studioWorkspaceView));
    }
  }

  function selectWorkspaceView(view) {
    if (view !== "home" && view !== "inspect" && view !== "brief" && view !== "regional" && view !== "regional-comparison" && view !== "terrain-comparison") return;
    studioWorkspaceView = view;
    syncWorkspaceVisibility();
    if (view === "inspect") window.requestAnimationFrame(() => { resizeCanvas(); draw(); });
    if (view === "terrain-comparison") window.requestAnimationFrame(drawTerrainComparisonMap);
  }

  const TERRAIN_COMPARISON_PAGE_SIZE = 30;

  function terrainComparisonColor(delta, scale, status) {
    if (status === "REFERENCE_ONLY" || status === "CANDIDATE_ONLY") return "#42a7bb";
    if (delta === 0 && status === "SEMANTIC_ONLY") return "#d9a847";
    if (!Number.isFinite(delta)) return "#263140";
    const amount = Math.max(0, Math.min(1, Math.abs(delta) / (scale || 1)));
    const start = [190, 193, 199];
    const end = delta < 0 ? [112, 95, 186] : [210, 83, 67];
    return "rgb(" + start.map((channel, i) => Math.round(channel + (end[i] - channel) * amount)).join(",") + ")";
  }

  function drawTerrainComparisonMap() {
    const plot = $("terrain-comparison-map");
    if (!plot || !terrainSemanticComparison) return;
    const width = Math.max(1, Math.floor(plot.clientWidth));
    const height = Math.max(1, Math.floor(plot.clientHeight || 420));
    const ratio = window.devicePixelRatio || 1;
    plot.width = Math.floor(width * ratio);
    plot.height = Math.floor(height * ratio);
    const context = plot.getContext("2d");
    context.setTransform(ratio, 0, 0, ratio, 0, 0);
    context.clearRect(0, 0, width, height);
    const grid = terrainSemanticComparison.grid;
    const margin = { left: 40, right: 18, top: 18, bottom: 32 };
    const mapWidth = Math.max(1, width - margin.left - margin.right);
    const mapHeight = Math.max(1, height - margin.top - margin.bottom);
    const cellWidth = mapWidth / grid.xSamples;
    const cellHeight = mapHeight / grid.zSamples;
    context.fillStyle = "#0a0f15";
    context.fillRect(0, 0, width, height);
    for (const column of terrainSemanticComparison.columns) {
      const x = margin.left + column.x * cellWidth;
      const y = margin.top + column.z * cellHeight;
      const selected = terrainSelectedColumn &&
        terrainSelectedColumn.x === column.x && terrainSelectedColumn.z === column.z;
      context.fillStyle = terrainComparisonColor(
        column.heightDelta,
        terrainSemanticComparison.maximumAbsSurfaceDelta,
        column.status
      );
      context.fillRect(x, y, Math.max(1, cellWidth - 1), Math.max(1, cellHeight - 1));
      if (selected) {
        context.strokeStyle = "#ffffff";
        context.lineWidth = 2;
        context.strokeRect(x + 1, y + 1, Math.max(1, cellWidth - 3), Math.max(1, cellHeight - 3));
      }
    }
    context.strokeStyle = "#526070";
    context.lineWidth = 1;
    context.strokeRect(margin.left, margin.top, mapWidth, mapHeight);
    context.fillStyle = "#aebdca";
    context.font = "12px system-ui, sans-serif";
    context.fillText("X", 12, margin.top + mapHeight / 2);
    context.fillText("Z", margin.left + mapWidth / 2, height - 8);
    terrainComparisonPlot = { left: margin.left, top: margin.top, mapWidth, mapHeight, cellWidth, cellHeight };
  }

  function selectTerrainComparisonColumn(column) {
    terrainSelectedColumn = { x: column.x, z: column.z };
    const reference = column.referenceTop;
    const candidate = column.candidateTop;
    const delta = Number.isFinite(column.heightDelta) ? fmt(column.heightDelta, 3) : "n/a (land added or removed)";
    $("terrain-comparison-column-detail").textContent =
      "Grid X/Z " + column.x + ", " + column.z +
      " · world X/Z " + fmt(column.worldX, 3) + ", " + fmt(column.worldZ, 3) +
      " · reference top " + (reference ? reference.semanticName + " at Y=" + fmt(reference.position[1], 3) : "no land") +
      " · candidate top " + (candidate ? candidate.semanticName + " at Y=" + fmt(candidate.position[1], 3) : "no land") +
      " · candidate − reference Y " + delta + ".";
    drawTerrainComparisonMap();
  }

  function renderTerrainComparisonPage() {
    if (!terrainSemanticComparison) return;
    const comparison = terrainSemanticComparison;
    const countTable = $("terrain-comparison-semantic-counts");
    countTable.replaceChildren();
    const countHead = document.createElement("thead");
    const countHeader = document.createElement("tr");
    for (const label of ["Semantic", "Reference", "Candidate", "Change"]) {
      const cell = document.createElement("th");
      cell.textContent = label;
      countHeader.append(cell);
    }
    countHead.append(countHeader);
    countTable.append(countHead);
    const countBody = document.createElement("tbody");
    for (const item of comparison.semanticCounts) {
      const row = document.createElement("tr");
      for (const value of [item.name, item.reference, item.candidate, item.candidate - item.reference]) {
        const cell = document.createElement("td");
        cell.textContent = String(value);
        row.append(cell);
      }
      countBody.append(row);
    }
    countTable.append(countBody);

    const page = comparison.pageChangedCells(terrainComparisonPage, TERRAIN_COMPARISON_PAGE_SIZE);
    const changes = $("terrain-comparison-changes");
    changes.replaceChildren();
    const changeHead = document.createElement("thead");
    const changeHeader = document.createElement("tr");
    for (const label of ["Grid X/Y/Z", "World X/Y/Z", "Reference", "Candidate", "Inspect"]) {
      const cell = document.createElement("th");
      cell.textContent = label;
      changeHeader.append(cell);
    }
    changeHead.append(changeHeader);
    changes.append(changeHead);
    const changeBody = document.createElement("tbody");
    for (const change of page.items) {
      const row = document.createElement("tr");
      for (const value of [
        change.gridIndex.join(", "),
        change.position.map(value => fmt(value, 3)).join(", "),
        change.referenceName,
        change.candidateName,
      ]) {
        const cell = document.createElement("td");
        cell.textContent = value;
        row.append(cell);
      }
      const actionCell = document.createElement("td");
      const action = document.createElement("button");
      action.type = "button";
      action.className = "secondary";
      action.textContent = "Inspect";
      action.addEventListener("click", () => {
        $("terrain-comparison-cell-detail").textContent =
          "Grid X/Y/Z " + change.gridIndex.join(", ") +
          " · world X/Y/Z " + change.position.map(value => fmt(value, 3)).join(", ") +
          " · " + change.referenceName + " → " + change.candidateName +
          ". Source labels and digests are unverified local provenance.";
      });
      actionCell.append(action);
      row.append(actionCell);
      changeBody.append(row);
    }
    changes.append(changeBody);
    $("terrain-comparison-page-status").textContent = page.total === 0
      ? "No changed semantic cells."
      : "Changed cells " + page.firstIndex + "–" + page.lastIndex + " of " + page.total +
        " · page " + (page.pageIndex + 1) + " of " + page.pageCount;
    $("terrain-comparison-previous").disabled = page.pageIndex === 0;
    $("terrain-comparison-next").disabled = page.pageIndex + 1 >= page.pageCount;
  }

  function renderTerrainComparison(comparison) {
    terrainSemanticComparison = comparison;
    terrainComparisonPage = 0;
    terrainComparisonPlot = null;
    terrainSelectedColumn = null;
    const changedPercent = comparison.changedPercent.toFixed(2);
    $("terrain-comparison-sources").textContent =
      "Reference: " + comparison.referenceTitle + " · Candidate: " + comparison.candidateTitle +
      " · both unbound local diagnostics";
    const summary = $("terrain-comparison-summary");
    summary.replaceChildren();
    const cards = [
      ["Changed semantic cells", comparison.changedCellCount + " / " + comparison.grid.sampleCount +
        " (" + changedPercent + "%)"],
      ["Changed top-surface columns", comparison.changedColumnCount + " / " +
        comparison.columns.length],
      ["World-grid match", comparison.grid.xSamples + " × " + comparison.grid.ySamples + " × " +
        comparison.grid.zSamples + " at exact coordinates"],
    ];
    for (const [title, value] of cards) {
      const card = document.createElement("div");
      card.className = "terrain-comparison-summary-card";
      const heading = document.createElement("h4");
      heading.textContent = title;
      const strong = document.createElement("strong");
      strong.textContent = value;
      card.append(heading, strong);
      summary.append(card);
    }
    $("terrain-comparison-results").hidden = false;
    $("terrain-comparison-report-download").disabled = false;
    renderTerrainComparisonLibraryControls();
    $("terrain-comparison-status").textContent =
      "Compared exact matching grids. No resampling was performed; both files remain unbound local diagnostics.";
    renderTerrainComparisonPage();
    drawTerrainComparisonMap();
  }

  async function loadTerrainComparisonFiles(referenceFile, candidateFile) {
    const status = $("terrain-comparison-status");
    const results = $("terrain-comparison-results");
    $("terrain-comparison-report-download").disabled = true;
    $("terrain-comparison-report-status").textContent = "Saved reports remain diagnostic and cannot satisfy a human review gate.";
    terrainSemanticComparison = null;
    terrainSelectedColumn = null;
    results.hidden = true;
    if (!referenceFile || !candidateFile) {
      status.textContent = referenceFile
        ? "Reference selected. Choose a candidate terrain file to compare."
        : candidateFile
          ? "Candidate selected. Choose a reference terrain file to compare."
          : "Choose one reference and one candidate terrain file.";
      return;
    }
    try {
      const maximumBytes = window.SkyforgeStudioWorkspacePackage.maximumFileBytes;
      for (const file of [referenceFile, candidateFile]) {
        if (file.size > maximumBytes) throw new Error("Each terrain file must be 25 MB or smaller.");
      }
      const parsed = await Promise.all([referenceFile, candidateFile].map(async file => ({
        title: file.name,
        json: await file.text(),
      })));
      const scenes = parsed.map(source => {
        const artifact = JSON.parse(source.json);
        const adapted = window.SkyforgeStudioScene.adaptArtifact(artifact, {
          binding: "UNBOUND_LOCAL",
          artifactTitle: source.title,
          reviewAuthority: false,
        });
        if (adapted.source?.binding !== "UNBOUND_LOCAL" || adapted.source.reviewAuthority !== false) {
          throw new Error("A terrain source did not remain an unbound local diagnostic.");
        }
        return adapted;
      });
      const comparison = window.SkyforgeStudioTerrainComparison.compare(
        { scene: scenes[0], title: parsed[0].title },
        { scene: scenes[1], title: parsed[1].title }
      );
      renderTerrainComparison(comparison);
    } catch (error) {
      terrainSemanticComparison = null;
      results.hidden = true;
      status.textContent = "Could not compare terrain volumes: " + String(error.message || error);
    }
  }

  function loadTerrainComparisonDemo() {
    const status = $("terrain-comparison-status");
    $("terrain-comparison-reference").value = "";
    $("terrain-comparison-candidate").value = "";
    $("terrain-comparison-report-file").value = "";
    try {
      const inputs = window.SkyforgeStudioTerrainComparisonDemo.createInputs();
      const scenes = inputs.map(input => window.SkyforgeStudioScene.adaptArtifact(input.artifact, {
        binding: "UNBOUND_LOCAL",
        artifactTitle: input.title,
        reviewAuthority: false,
      }));
      const comparison = window.SkyforgeStudioTerrainComparison.compare(
        { scene: scenes[0], title: inputs[0].title },
        { scene: scenes[1], title: inputs[1].title }
      );
      renderTerrainComparison(comparison);
      status.textContent =
        "Synthetic demonstration loaded. These sample changes are not generated Skyforge output or project evidence.";
      $("terrain-comparison-report-status").textContent =
        "Synthetic demonstration only; not project evidence. No source digest is supplied.";
    } catch (error) {
      terrainSemanticComparison = null;
      $("terrain-comparison-results").hidden = true;
      status.textContent = "Could not load the synthetic terrain example: " + String(error.message || error);
    }
  }

  function handleTerrainComparisonFilesChanged() {
    const reference = $("terrain-comparison-reference").files?.[0] || null;
    const candidate = $("terrain-comparison-candidate").files?.[0] || null;
    loadTerrainComparisonFiles(reference, candidate);
  }

  function downloadTerrainComparisonReport() {
    if (!terrainSemanticComparison) return;
    try {
      const reportApi = window.SkyforgeStudioTerrainComparisonReport;
      const report = reportApi.create(terrainSemanticComparison);
      const blob = new Blob([reportApi.stringify(report)], { type: "application/json" });
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = reportApi.filename(report);
      link.click();
      window.setTimeout(() => URL.revokeObjectURL(url), 0);
      $("terrain-comparison-report-status").textContent =
        "Downloaded a diagnostic report with recorded, unverified source provenance.";
    } catch (error) {
      $("terrain-comparison-report-status").textContent =
        "Could not save this terrain comparison: " + String(error.message || error);
    }
  }

  async function loadTerrainComparisonReport(file) {
    if (!file) return;
    const status = $("terrain-comparison-status");
    try {
      const reader = window.SkyforgeStudioTerrainComparisonReportReader;
      if (file.size > reader.maximumFileBytes) throw new Error("Terrain reports must be 32 MB or smaller.");
      const parsed = reader.parse(await file.text(), file.size);
      renderTerrainComparison(parsed.comparison);
      status.textContent =
        "Reopened a saved terrain comparison. Source digests are recorded provenance only and have not been verified.";
      $("terrain-comparison-report-status").textContent = parsed.document.notice;
      $("terrain-comparison-reference").value = "";
      $("terrain-comparison-candidate").value = "";
      $("terrain-comparison-report-file").value = "";
    } catch (error) {
      $("terrain-comparison-report-file").value = "";
      status.textContent = "Could not reopen this terrain comparison; the current comparison remains visible: " +
        String(error.message || error);
    }
  }

  function terrainComparisonLibraryStatus(message, isError = false) {
    const status = $("terrain-comparison-library-status");
    status.textContent = localScopeCopy(message);
    status.className = isError ? "small error" : "small muted";
  }

  function renderTerrainComparisonLibraryControls() {
    const select = $("terrain-comparison-library-select");
    if (!select) return;
    const selectedId = select.value;
    select.replaceChildren();
    const placeholder = document.createElement("option");
    placeholder.value = "";
    placeholder.textContent = terrainComparisonLibraryRecords.length
      ? "Choose a saved comparison"
      : localScopeCopy("No saved comparisons in this browser");
    select.append(placeholder);
    for (const record of terrainComparisonLibraryRecords) {
      const option = document.createElement("option");
      option.value = record.id;
      option.textContent = record.title + " · " + new Date(record.updated_at).toLocaleString();
      select.append(option);
    }
    if (terrainComparisonLibraryRecords.some(record => record.id === selectedId)) {
      select.value = selectedId;
    }
    const selected = terrainComparisonLibraryRecords.some(record => record.id === select.value);
    select.disabled = terrainComparisonLibraryBusy || !terrainComparisonLibraryRecords.length;
    $("terrain-comparison-library-open").disabled = terrainComparisonLibraryBusy || !selected;
    $("terrain-comparison-library-remove").disabled = terrainComparisonLibraryBusy || !selected;
    $("terrain-comparison-library-save").disabled = terrainComparisonLibraryBusy ||
      !terrainComparisonLibraryRepository || !terrainSemanticComparison;
  }

  async function refreshTerrainComparisonLibrary() {
    if (!terrainComparisonLibraryRepository) return;
    terrainComparisonLibraryRecords = await terrainComparisonLibraryRepository.list();
    renderTerrainComparisonLibraryControls();
  }

  function newTerrainComparisonId() {
    if (window.crypto && typeof window.crypto.randomUUID === "function") return window.crypto.randomUUID();
    return "terrain-" + Date.now() + "-" + Math.random().toString(16).slice(2);
  }

  async function saveTerrainComparisonToBrowser() {
    if (!terrainSemanticComparison || !terrainComparisonLibraryRepository || terrainComparisonLibraryBusy) return;
    const api = window.SkyforgeStudioTerrainComparisonLibrary;
    terrainComparisonLibraryBusy = true;
    renderTerrainComparisonLibraryControls();
    try {
      const reportApi = window.SkyforgeStudioTerrainComparisonReport;
      const reportJson = reportApi.stringify(reportApi.create(terrainSemanticComparison));
      const now = new Date().toISOString();
      const title = $("terrain-comparison-library-title").value;
      const record = api.createRecord(newTerrainComparisonId(), title, reportJson, now, now);
      await terrainComparisonLibraryRepository.save(record);
      await refreshTerrainComparisonLibrary();
      $("terrain-comparison-library-select").value = record.id;
      $("terrain-comparison-library-title").value = "";
      terrainComparisonLibraryStatus(
        "Saved in this browser profile only. It is not synced, uploaded, or registered.", false);
    } catch (error) {
      terrainComparisonLibraryStatus(
        "Could not save this comparison: " + String(error.message || error) +
        ". You can still download a portable report.", true);
    } finally {
      terrainComparisonLibraryBusy = false;
      renderTerrainComparisonLibraryControls();
    }
  }

  function openTerrainComparisonFromBrowser() {
    if (terrainComparisonLibraryBusy) return;
    try {
      const record = terrainComparisonLibraryRecords.find(item =>
        item.id === $("terrain-comparison-library-select").value);
      if (!record) throw new Error("Choose a saved comparison first");
      const reader = window.SkyforgeStudioTerrainComparisonReportReader;
      const parsed = reader.parse(record.report_json, record.report_bytes);
      renderTerrainComparison(parsed.comparison);
      $("terrain-comparison-status").textContent =
        localScopeCopy("Reopened a comparison from this browser. Its saved source provenance remains unverified.");
      $("terrain-comparison-report-status").textContent = parsed.document.notice;
      $("terrain-comparison-reference").value = "";
      $("terrain-comparison-candidate").value = "";
      $("terrain-comparison-report-file").value = "";
      terrainComparisonLibraryStatus("Opened “" + record.title + "” and validated its diagnostic report.", false);
    } catch (error) {
      terrainComparisonLibraryStatus(
        "Could not open the saved comparison; the current result is unchanged: " +
        String(error.message || error), true);
    }
  }

  async function removeTerrainComparisonFromBrowser() {
    if (terrainComparisonLibraryBusy || !terrainComparisonLibraryRepository) return;
    const record = terrainComparisonLibraryRecords.find(item =>
      item.id === $("terrain-comparison-library-select").value);
    if (!record) return;
    if (typeof window.confirm !== "function" ||
        !window.confirm(localScopeCopy("Remove “" + record.title +
          "” from this browser? This cannot be undone here. Download a portable report first if you may need it later."))) return;
    terrainComparisonLibraryBusy = true;
    renderTerrainComparisonLibraryControls();
    try {
      await terrainComparisonLibraryRepository.remove(record.id);
      await refreshTerrainComparisonLibrary();
      terrainComparisonLibraryStatus("Removed “" + record.title + "” from this browser.", false);
    } catch (error) {
      terrainComparisonLibraryStatus("Could not remove the saved comparison: " + String(error.message || error), true);
    } finally {
      terrainComparisonLibraryBusy = false;
      renderTerrainComparisonLibraryControls();
    }
  }

  async function initializeTerrainComparisonLibrary() {
    try {
      const api = window.SkyforgeStudioTerrainComparisonLibrary;
      if (!api) throw new Error("browser-local storage support is unavailable");
      terrainComparisonLibraryRepository = api.createRepository();
      await refreshTerrainComparisonLibrary();
      terrainComparisonLibraryStatus(
        "Saved items stay in this browser profile. Use Download comparison report to move or back them up.", false);
    } catch (error) {
      terrainComparisonLibraryRepository = null;
      renderTerrainComparisonLibraryControls();
      terrainComparisonLibraryStatus(
        "This browser cannot open local comparison storage: " + String(error.message || error) +
        ". Download and file-based reopen still work.", true);
    }
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

  function localScopeCopy(message) {
    const capabilities = window.SkyforgeStudioCapabilities;
    return capabilities
      ? capabilities.localScopeCopy(window.location.protocol, message)
      : String(message);
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

  function configureRuntimeCapabilities() {
    const capabilities = window.SkyforgeStudioCapabilities;
    if (!capabilities || capabilities.fromProtocol(window.location.protocol).canConnectRegisteredArtifacts) return;
    $("auth-panel").hidden = true;
    $("desktop-local-status").hidden = false;
    const desktopCopy = [
      ["inspect-source-copy", "Open the bundled specimen or import a local semantic file. Provenance remains visible in the inspector."],
      ["brief-storage-copy", "Describe it in your own words. Drafts stay in this app; no generator is connected."],
      ["world-generation-copy", "This Studio preview has no generator connected. Your brief stays in this app; it is not sent to a service, evaluated, or turned into a world."],
      ["terrain-comparison-library-heading", "Saved in this app"],
      ["terrain-comparison-library-description", "Open a saved comparison here at any time. The list stays in this Studio app; it is not synced or uploaded. Load two versions above to enable saving."],
      ["terrain-comparison-library-save", "Save in this app"],
      ["terrain-comparison-library-status", "Loading app-local comparison storage…"],
      ["remember-inspection-copy", "Remember this inspection in this app"],
      ["regional-library-heading", "Saved in this app"],
      ["regional-library-description", "Save named comparisons in this app. Nothing is uploaded. Use a portable package to move a comparison to another device or Studio profile."],
      ["regional-library-save", "Save in this app"],
      ["regional-library-status", "Checking app-local storage…"],
    ];
    for (const [id, copy] of desktopCopy) {
      const node = $(id);
      if (node) node.textContent = copy;
    }
    const terrainPlaceholder = $("terrain-comparison-library-select")?.querySelector('option[value=""]');
    if (terrainPlaceholder) terrainPlaceholder.textContent = localScopeCopy(terrainPlaceholder.textContent);
    setConnection("Local preview", "warn");
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

  function resetComparisonValueInspector() {
    const panel = $("comparison-value-inspector");
    if (!panel) return;
    panel.hidden = true;
    $("comparison-value-context").textContent = "";
    $("comparison-value-field").textContent = "";
    $("comparison-reference-value").textContent = "—";
    $("comparison-candidate-value").textContent = "—";
    $("comparison-delta-value").textContent = "—";
  }

  function clearSampleProvenance() {
    const panel = $("sample-provenance");
    if (!panel) return;
    panel.hidden = true;
    $("provenance-note").textContent = "";
    const fields = $("provenance-fields");
    while (fields.firstChild) fields.removeChild(fields.firstChild);
  }

  function clearInspectorValues() {
    clearSampleProvenance();
    $("inspect-position").textContent = "click a sample";
    $("inspect-semantic").textContent = "—";
    $("inspect-vector").textContent = "—";
    $("inspect-updraft").textContent = "—";
    $("inspect-turbulence").textContent = "—";
    $("inspect-shear").textContent = "—";
    $("inspect-confidence").textContent = "—";
    $("inspect-authority").textContent = "—";
    resetComparisonValueInspector();
  }

  function renderSelectedComparison(sample) {
    const panel = $("comparison-value-inspector");
    if (sample.overlayKind === "HYDROLOGY_COMPARISON_SAMPLE") {
      const potential = $("hydrology-potential").value;
      const field = [
        "runoffPotential",
        "retentionPotential",
        "drainagePotential",
        "outflowPotential",
      ].includes(potential) ? potential : "runoffPotential";
      const fieldLabel = {
        runoffPotential: "Runoff potential",
        retentionPotential: "Retention potential",
        drainagePotential: "Drainage potential",
        outflowPotential: "Outflow potential",
      }[field];
      $("comparison-value-field").textContent = fieldLabel;
      $("comparison-reference-value").textContent = fmt(sample.reference[field]);
      $("comparison-candidate-value").textContent = fmt(sample.candidate[field]);
      $("comparison-delta-value").textContent = fmt(sample.deltas[field]);
      $("comparison-value-context").textContent =
        "Cause-grid sample [" + sample.grid.join(", ") + "]";
      panel.hidden = false;
      return;
    }
    if (sample.overlayKind !== "HYDROLOGY_FIELD_COMPARISON_SAMPLE") return;

    const mode = $("comparison-mode").value;
    const selection = mode === "surface"
      ? { label: "Target surface elevation", field: "targetUpperY", delta: sample.surfaceDeltaY, unit: " world units" }
      : mode === "water-surface"
        ? { label: "Water surface elevation", field: "waterSurfaceY", delta: sample.waterSurfaceDeltaY, unit: " world units" }
        : { label: "Water depth", field: "waterDepthWorld", delta: sample.waterDepthDelta, unit: " world units" };
    const valueText = (record) => {
      if (!record) return "not sampled";
      const value = record[selection.field];
      if (selection.field === "waterSurfaceY" && (value === null || value === undefined)) {
        return "no water surface";
      }
      return Number.isFinite(value) ? fmt(value) + selection.unit : "—";
    };
    $("comparison-value-field").textContent = selection.label;
    $("comparison-reference-value").textContent = valueText(sample.reference);
    $("comparison-candidate-value").textContent = valueText(sample.candidate);
    $("comparison-delta-value").textContent =
      sample.coverage !== "shared"
        ? "not comparable · " + sample.coverage
        : selection.delta === null
          ? "not available · wet/dry transition"
          : fmt(selection.delta) + selection.unit;
    const wetState = sample.referenceWet === null || sample.candidateWet === null
      ? "wet state unavailable"
      : (sample.referenceWet ? "wet" : "dry") + " → " +
        (sample.candidateWet ? "wet" : "dry") +
        (sample.wetTransition ? " · " + sample.wetTransition : "");
    $("comparison-value-context").textContent =
      "Projected-field sample [" + sample.grid.join(", ") + "] · " +
      sample.coverage + " coverage · " + wetState;
    panel.hidden = false;
  }

  function renderSampleProvenance(sample) {
    const panel = $("sample-provenance");
    const fields = $("provenance-fields");
    const note = $("provenance-note");
    while (fields.firstChild) fields.removeChild(fields.firstChild);
    $("sample-export-status").textContent = "";

    function addField(label, value) {
      const term = document.createElement("dt");
      const description = document.createElement("dd");
      term.textContent = label;
      description.textContent = value === null || value === undefined || value === ""
        ? "Not recorded"
        : String(value);
      fields.append(term, description);
    }

    function addSource(prefix, source) {
      if (!source) return;
      addField(prefix, source.artifactTitle || source.artifactId || source.artifactKind);
      addField(prefix + " kind", source.artifactKind);
      addField(prefix + " artifact ID", source.artifactId);
      addField(prefix + " source revision", source.sourceSha);
      addField(prefix + " recorded digest", source.artifactDigest);
      addField(prefix + " binding", source.binding);
      addField(prefix + " review authority",
        source.reviewAuthority ? "Artifact-bound" : "Unbound local diagnostic");
    }

    if (hydrologyComparison) {
      addSource("Terrain source", scene.source);
      addSource("Reference source", hydrologyComparison.comparison.referenceSource);
      addSource("Candidate source", hydrologyComparison.comparison.candidateSource);
      addField("Association token", hydrologyComparison.comparison.associationToken);
    } else {
      addSource("Scene source", scene.source);
      if (overlay) addSource("Overlay source", overlay.source);
    }

    const coordinates = Array.isArray(sample.position)
      ? "[" + sample.position.map((value) => fmt(value, 1)).join(", ") + "]"
      : null;
    addField("Selected world position", coordinates);
    if (Array.isArray(sample.grid)) {
      addField("Sample grid coordinate", "[" + sample.grid.join(", ") + "]");
    } else if (Array.isArray(sample.localPosition)) {
      addField("Sample local coordinate",
        "[" + sample.localPosition.map((value) => fmt(value, 1)).join(", ") + "]");
    }
    if (scene.coordinateSystem?.id) {
      addField("Coordinate frame", scene.coordinateSystem.id);
    }
    if (scene.provider?.label) {
      addField("Recorded provider", scene.provider.label +
        (scene.provider.version ? " · " + scene.provider.version : ""));
    }
    if (overlay?.terrainSemanticSha256) {
      addField("Bound terrain semantic digest", overlay.terrainSemanticSha256);
    }
    if (overlay?.binding?.associationToken) {
      addField("Authorship association", overlay.binding.associationToken);
    }
    if (overlay?.binding?.authoredIdentity) {
      addField("Authored specimen identity", JSON.stringify(overlay.binding.authoredIdentity));
    }
    if (overlay?.binding?.worldFrame) {
      addField("Recorded world frame", JSON.stringify(overlay.binding.worldFrame));
    }
    if (overlay?.gridBinding) {
      addField("Hydrology sample grid binding", JSON.stringify(overlay.gridBinding));
    }

    let sampleLineageRecorded = false;
    if (sample.provenance && typeof sample.provenance === "object") {
      for (const [key, value] of Object.entries(sample.provenance)) {
        addField("Sample provenance · " + key, value);
        sampleLineageRecorded = true;
      }
    }
    if (sample.startCell !== undefined || sample.endCell !== undefined) {
      if (sample.startCell !== undefined) addField("Recorded start cell", sample.startCell);
      if (sample.endCell !== undefined) addField("Recorded end cell", sample.endCell);
      sampleLineageRecorded = true;
    }
    if (sample.coverage) addField("Comparison coverage", sample.coverage);
    if (sample.wetTransition) addField("Wet-state transition", sample.wetTransition);
    if (!sampleLineageRecorded) {
      addField("Sample-specific lineage", "Not recorded by this artifact.");
    }

    note.textContent = (scene.source.reviewAuthority
      ? "Values shown here come from loaded artifact metadata."
      : "Unbound local diagnostic. This view cannot satisfy an artifact-bound human gate.") +
      " Missing lineage is shown as not recorded; Studio does not infer it.";
    panel.hidden = false;
  }

  function renderImportedSampleTrace(trace, fileName) {
    const panel = $("imported-sample-trace");
    const fields = $("imported-sample-trace-fields");
    while (fields.firstChild) fields.removeChild(fields.firstChild);

    function addField(label, value) {
      const term = document.createElement("dt");
      const description = document.createElement("dd");
      term.textContent = label;
      description.textContent = value === null || value === undefined || value === ""
        ? "Not recorded"
        : String(value);
      fields.append(term, description);
    }

    addField("Trace file", fileName);
    addField("Document type", trace.document_type);
    addField("Format version", trace.format_version);
    addField("Scene type", trace.scene_kind);
    addField("Recorded authority",
      trace.review_authority
        ? "Marked artifact-bound in the source record; this imported trace is not independent gate evidence."
        : "Unbound local diagnostic.");
    addField("Selected position",
      Array.isArray(trace.selected_sample.position)
        ? JSON.stringify(trace.selected_sample.position)
        : null);

    $("imported-sample-trace-note").textContent =
      "Read-only record of one selected sample. Opening a trace does not restore its source artifacts or establish review authority.";
    $("imported-sample-trace-context").textContent = JSON.stringify({
      sources: trace.sources,
      coordinate_system: trace.coordinate_system,
      provider: trace.provider,
      overlay: trace.overlay,
      comparison: trace.comparison,
    }, null, 2);
    $("imported-sample-trace-sample").textContent =
      JSON.stringify(trace.selected_sample, null, 2);
    panel.hidden = false;
  }

  function downloadSelectedSampleTrace(sample) {
    if (!scene || !sample) return;
    const comparison = hydrologyComparison ? {
      associationToken: hydrologyComparison.comparison.associationToken,
      referenceSource: hydrologyComparison.comparison.referenceSource,
      candidateSource: hydrologyComparison.comparison.candidateSource,
      reviewAuthority: hydrologyComparison.comparison.reviewAuthority,
      field: $("comparison-mode").value,
    } : null;
    const trace = window.SkyforgeStudioSampleTrace.create({
      scene,
      overlay,
      comparison,
      sample,
    });
    const filename = window.SkyforgeStudioSampleTrace.filename(scene, sample);
    const blob = new Blob([JSON.stringify(trace, null, 2) + "\n"], {
      type: "application/json",
    });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = filename;
    link.click();
    window.setTimeout(() => URL.revokeObjectURL(url), 0);
    $("sample-export-status").textContent = "Downloaded one selected sample trace (" +
      (trace.review_authority ? "artifact-bound source" : "unbound local diagnostic") + ").";
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
    renderSampleProvenance(sample);

    $("inspect-position").textContent =
      "[" + sample.position.map((value) => fmt(value, 1)).join(", ") + "]";

    if (sample.overlayKind === "HYDROLOGY_COMPARISON_SAMPLE") {
      renderSelectedComparison(sample);
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
      renderSelectedComparison(sample);
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
    $("download-comparison-report").disabled = hydrologyComparison === null;
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
    scheduleWorkspaceSessionSave();
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

  function renderHydrologyRunSummary() {
    const panel = $("hydrology-run-summary");
    const summary = window.SkyforgeStudioHydrologyRunSummary.summarize(scene, overlay);
    panel.hidden = !summary;
    if (!summary) return;

    const count = value => new Intl.NumberFormat().format(value);
    const compactValue = value => {
      const trimmed = Number(value).toFixed(3).replace(/0+$/, "").replace(/\.$/, "");
      return trimmed === "-0" ? "0" : trimmed;
    };
    const rangeText = range => range
      ? compactValue(range.minimum) + " to " + compactValue(range.maximum)
      : "No recorded values";

    $("hydrology-summary-terrain").textContent =
      count(summary.terrainGrid.xSamples) + " × " +
      count(summary.terrainGrid.ySamples) + " × " +
      count(summary.terrainGrid.zSamples) + " samples";
    $("hydrology-summary-causes").textContent =
      count(summary.causeGrid.xSamples) + " × " + count(summary.causeGrid.zSamples) +
      " grid · stride " + count(summary.causeGrid.stride) +
      " · " + count(summary.causeSampleCount) + " samples";
    $("hydrology-summary-response").textContent =
      count(summary.terrainResponseSampleCount) + " samples · " +
      count(summary.wetResponseSampleCount) + " wet";
    $("hydrology-summary-reaches").textContent =
      count(summary.reachCount) + " reaches · " + count(summary.reachPointCount) + " points";
    $("hydrology-summary-water").textContent =
      count(summary.waterSurfaceSampleCount) + " of " +
      count(summary.terrainResponseSampleCount) + " response samples";
    $("hydrology-summary-runoff").textContent = rangeText(summary.potentialRanges.runoffPotential);
    $("hydrology-summary-retention").textContent = rangeText(summary.potentialRanges.retentionPotential);
    $("hydrology-summary-drainage").textContent = rangeText(summary.potentialRanges.drainagePotential);
    $("hydrology-summary-outflow").textContent = rangeText(summary.potentialRanges.outflowPotential);
    const deltaRange = summary.terrainDeltaWorldRange;
    $("hydrology-summary-delta").textContent = deltaRange
      ? rangeText(deltaRange)
      : "No recorded values";
  }

  function setScene(nextScene, sourceArtifact = null, sourceTitle = "", sourceJson = null) {
    stopPlayback();
    scene = nextScene;
    $("download-inspection-workspace").disabled = !sourceArtifact;
    workspaceSceneArtifact = sourceArtifact;
    workspaceSceneTitle = String(sourceTitle || nextScene.source.artifactTitle || nextScene.source.artifactId || "semantic-artifact.json").slice(0, 200);
    workspaceSceneJson = sourceArtifact ? (typeof sourceJson === "string" ? sourceJson : JSON.stringify(sourceArtifact)) : "";
    workspaceOverlayArtifact = null;
    workspaceOverlayTitle = "";
    workspaceOverlayJson = "";
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
    renderHydrologyRunSummary();

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

  function captureInspectionView() {
    const camera = { yaw, pitch, zoom };
    if (isTerrain()) {
      return {
        mode: "terrain",
        camera,
        controls: {
          semanticView: $("terrain-view").value,
          surface: overlay ? $("terrain-surface").value : "base",
          sliceIndex: Number($("terrain-slice").value),
          causeField: $("hydrology-potential").value,
          showFlowVectors: $("show-flow-vectors").checked,
          showChannelWidth: $("show-channel-width").checked,
          showHydrologyResponse: $("show-hydrology-response").checked,
          showWaterIntent: $("show-water-intent").checked,
        },
      };
    }
    if (isAtmosphere()) {
      return {
        mode: "atmosphere",
        camera,
        controls: {
          dataset: $("dataset").value,
          vectorMode: $("vector-mode").value,
          colorMode: $("color-mode").value,
          altitude: $("altitude").value,
          frame: Number($("frame").value),
          arrowScale: Number($("arrow-scale").value),
        },
      };
    }
    throw new Error("load a semantic specimen before downloading a workspace");
  }

  function assertInspectionViewFits(plan) {
    if (plan.view.mode === "terrain") {
      if (plan.scene.sceneKind !== "TERRAIN_SEMANTIC_VOLUME") {
        throw new Error("workspace view does not match its terrain scene");
      }
      if (plan.view.controls.surface === "hydrology" && !plan.overlay) {
        throw new Error("workspace selects the hydrology surface but has no reference overlay");
      }
      if (plan.view.controls.sliceIndex >= plan.scene.terrain.grid.ySamples) {
        throw new Error("workspace slice is outside the terrain's vertical range");
      }
      return;
    }
    if (plan.scene.sceneKind !== "ATMOSPHERE_VECTOR_FIELD") {
      throw new Error("workspace view does not match its atmosphere scene");
    }
    const controls = plan.view.controls;
    const frames = plan.scene.opportunity.frames;
    if (controls.frame > 0 && controls.frame >= frames.length) {
      throw new Error("workspace frame is unavailable in this atmosphere specimen");
    }
    if (controls.dataset === "opportunity" && !frames.length) {
      throw new Error("workspace opportunity dataset is unavailable in this atmosphere specimen");
    }
    const frame = controls.dataset === "opportunity"
      ? frames[controls.frame]
      : plan.scene.snapshot;
    if (controls.altitude !== "all" &&
        !frame.samples.some(sample => String(sample.position[1]) === controls.altitude)) {
      throw new Error("workspace altitude is unavailable in this atmosphere specimen");
    }
  }

  function applyInspectionView(view) {
    yaw = view.camera.yaw;
    pitch = view.camera.pitch;
    zoom = view.camera.zoom;
    if (view.mode === "terrain") {
      $("terrain-view").value = view.controls.semanticView;
      $("terrain-surface").value = view.controls.surface;
      $("terrain-slice").value = String(view.controls.sliceIndex);
      $("terrain-slice-value").textContent = fmt(
        scene.terrain.grid.minimumY + scene.terrain.grid.spacingY * view.controls.sliceIndex, 1
      );
      $("terrain-slice-control").hidden = view.controls.semanticView !== "slice";
      $("hydrology-potential").value = view.controls.causeField;
      $("show-flow-vectors").checked = view.controls.showFlowVectors;
      $("show-channel-width").checked = view.controls.showChannelWidth;
      $("show-hydrology-response").checked = view.controls.showHydrologyResponse;
      $("show-water-intent").checked = view.controls.showWaterIntent;
      updateHydrologyControlVisibility();
    } else {
      $("dataset").value = view.controls.dataset;
      $("vector-mode").value = view.controls.vectorMode;
      $("color-mode").value = view.controls.colorMode;
      $("frame").value = String(view.controls.frame);
      $("time-controls").hidden = view.controls.dataset !== "opportunity";
      rebuildAtmosphereAltitudeOptions();
      $("altitude").value = view.controls.altitude;
      $("arrow-scale").value = String(view.controls.arrowScale);
      $("arrow-scale-value").textContent = view.controls.arrowScale.toFixed(2) + "×";
      $("frame-value").textContent = String(view.controls.frame);
      $("frame-summary").textContent = view.controls.dataset === "opportunity"
        ? "Opportunity frame " + view.controls.frame
        : "Spatial snapshot";
    }
    selected = null;
    updateBindingPill();
    renderInspector(null);
    draw();
  }

  function createInspectionWorkspace() {
    if (!workspaceSceneArtifact) {
      throw new Error("the current scene source is not available for workspace export");
    }
    return window.SkyforgeStudioWorkspacePackage.create({
      scene: { title: workspaceSceneTitle, artifactJson: workspaceSceneJson },
      overlay: workspaceOverlayArtifact
        ? { title: workspaceOverlayTitle, artifactJson: workspaceOverlayJson }
        : null,
      view: captureInspectionView(),
    });
  }

  function serializeInspectionWorkspace() {
    return JSON.stringify(createInspectionWorkspace());
  }

  function downloadInspectionWorkspace() {
    const workspace = createInspectionWorkspace();
    const link = document.createElement("a");
    const url = URL.createObjectURL(new Blob(
      [JSON.stringify(workspace, null, 2) + "\n"], { type: "application/json" }
    ));
    link.href = url;
    link.download = "skyforge-studio-inspection-workspace.json";
    link.click();
    window.setTimeout(() => URL.revokeObjectURL(url), 0);
    $("inspection-workspace-status").textContent =
      "Downloaded a portable workspace. Any reopened sources are local diagnostics, not registered-artifact verification.";
    $("inspection-workspace-status").className = "small muted";
  }

  function prepareInspectionWorkspace(serializedWorkspace) {
    const plan = window.SkyforgeStudioWorkspacePackage.prepare(serializedWorkspace, {
      adaptScene: (artifact, source) => window.SkyforgeStudioScene.adaptArtifact(artifact, source),
      adaptOverlay: (artifact, activeScene, source) =>
        window.SkyforgeStudioScene.adaptOverlayArtifact(artifact, activeScene, source),
    });
    assertInspectionViewFits(plan);
    return plan;
  }

  function applyInspectionWorkspacePlan(plan, sourceStatus) {
    setScene(plan.scene, plan.sceneArtifact, plan.sceneTitle, plan.sceneJson);
    if (plan.overlay) setOverlay(plan.overlay, plan.overlayArtifact, plan.overlayTitle, plan.overlayJson);
    applyInspectionView(plan.view);
    $("source-status").textContent = sourceStatus;
  }

  function setWorkspaceSessionStatus(message, failed = false) {
    const node = $("workspace-session-status");
    node.textContent = localScopeCopy(message);
    node.className = failed ? "small error" : "small muted";
    $("workspace-session-forget").disabled = !workspaceSessionHasSaved;
  }

  function scheduleWorkspaceSessionSave() {
    if (!workspaceSessionReady || !workspaceSessionEnabled || workspaceSessionRestoring ||
        !workspaceSceneArtifact || !workspaceSessionController) return;
    if (workspaceSessionSaveTimer !== null) window.clearTimeout(workspaceSessionSaveTimer);
    const generation = workspaceSessionGeneration;
    workspaceSessionSaveTimer = window.setTimeout(() => {
      workspaceSessionSaveTimer = null;
      let serializedWorkspace;
      try {
        serializedWorkspace = serializeInspectionWorkspace();
      } catch (error) {
        setWorkspaceSessionStatus("Could not remember this inspection: " + String(error.message || error), true);
        return;
      }
      workspaceSessionSaveChain = workspaceSessionSaveChain.catch(() => {}).then(async () => {
        if (generation !== workspaceSessionGeneration || !workspaceSessionEnabled) return;
        await workspaceSessionController.save(serializedWorkspace);
        if (generation !== workspaceSessionGeneration || !workspaceSessionEnabled) return;
        workspaceSessionHasSaved = true;
        setWorkspaceSessionStatus("Saved in this browser. Sources remain local diagnostics and are never uploaded.");
      }).catch(error => {
        if (generation === workspaceSessionGeneration) {
          setWorkspaceSessionStatus("Could not save this inspection in the browser: " +
            String(error.message || error) + ". Your current view remains open.", true);
        }
      });
    }, 450);
  }

  async function initializeWorkspaceSession() {
    const content = $("studio-content");
    content.inert = true;
    content.setAttribute("aria-busy", "true");
    setWorkspaceSessionStatus("Checking this browser for a saved inspection…");
    try {
      if (!workspaceSessionController) throw new Error("browser storage is unavailable");
      const record = await workspaceSessionController.read();
      if (record?.enabled) {
        workspaceSessionEnabled = true;
        $("remember-inspection").checked = true;
        if (record.workspace_json !== null) {
          workspaceSessionHasSaved = true;
          workspaceSessionRestoring = true;
          const plan = prepareInspectionWorkspace(record.workspace_json);
          applyInspectionWorkspacePlan(plan,
            "Resumed a saved inspection as local diagnostics; registered verification was not restored.");
          setWorkspaceSessionStatus("Resumed the saved inspection from this browser. It remains an unbound local diagnostic.");
        } else {
          setWorkspaceSessionStatus("Remembering is on for this browser. Load a source to save the current inspection.");
        }
      } else {
        setWorkspaceSessionStatus("Off by default. Turn this on to resume the current inspection after a reload.");
      }
    } catch (error) {
      const message = String(error.message || error);
      const invalidSaved = workspaceSessionEnabled;
      setWorkspaceSessionStatus(
        (invalidSaved ? "Could not restore the saved inspection: " : "Browser storage is unavailable: ") +
        message + ". The inspector remains available in this tab.", true);
    } finally {
      workspaceSessionRestoring = false;
      workspaceSessionReady = true;
      content.inert = false;
      content.removeAttribute("aria-busy");
      if (workspaceSessionEnabled && workspaceSceneArtifact && !workspaceSessionHasSaved) {
        scheduleWorkspaceSessionSave();
      }
    }
  }

  async function changeWorkspaceSessionPreference(enabled) {
    if (!workspaceSessionController) throw new Error("browser storage is unavailable");
    if (!enabled) {
      workspaceSessionGeneration += 1;
      if (workspaceSessionSaveTimer !== null) window.clearTimeout(workspaceSessionSaveTimer);
      workspaceSessionSaveTimer = null;
      await workspaceSessionSaveChain.catch(() => {});
      await workspaceSessionController.disable();
      workspaceSessionEnabled = false;
      workspaceSessionHasSaved = false;
      setWorkspaceSessionStatus("Stopped remembering and removed the saved inspection from this browser.");
      return;
    }

    const initialWorkspace = workspaceSceneArtifact ? serializeInspectionWorkspace() : null;
    await workspaceSessionController.enable(initialWorkspace);
    workspaceSessionGeneration += 1;
    workspaceSessionEnabled = true;
    workspaceSessionHasSaved = initialWorkspace !== null;
    if (initialWorkspace !== null) {
      setWorkspaceSessionStatus("Remembering this inspection in this browser. It remains a local diagnostic.");
    } else {
      setWorkspaceSessionStatus("Remembering is on. Load a source to save the current inspection.");
    }
  }

  async function forgetRememberedWorkspace() {
    if (!workspaceSessionController || !workspaceSessionEnabled) return;
    workspaceSessionGeneration += 1;
    if (workspaceSessionSaveTimer !== null) window.clearTimeout(workspaceSessionSaveTimer);
    workspaceSessionSaveTimer = null;
    await workspaceSessionSaveChain.catch(() => {});
    await workspaceSessionController.forgetWorkspace();
    workspaceSessionHasSaved = false;
    setWorkspaceSessionStatus("Forgot the saved inspection. Remembering stays on for the next loaded view.");
  }

  async function openInspectionWorkspace(file) {
    const status = $("inspection-workspace-status");
    try {
      if (file.size > window.SkyforgeStudioWorkspacePackage.maximumFileBytes) {
        throw new Error("workspace files must be 25 MB or smaller");
      }
      const plan = prepareInspectionWorkspace(await file.text());
      applyInspectionWorkspacePlan(plan, "Opened local inspection workspace; all sources are unbound diagnostics.");
      status.textContent = "Workspace reopened and all sources were revalidated as local diagnostics.";
      status.className = "small muted";
    } catch (error) {
      status.textContent = "Could not open inspection workspace: " + String(error.message || error) +
        ". The current inspector was left unchanged.";
      status.className = "small error";
    } finally {
      $("inspection-workspace-file").value = "";
    }
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
    const sourceJson = await response.text();
    const artifact = JSON.parse(sourceJson);
    const nextScene = window.SkyforgeStudioScene.adaptArtifact(artifact, {
      binding: "REGISTERED_ARTIFACT",
      artifactId,
      artifactTitle: option.dataset.title || artifactId,
      sourceSha: option.dataset.sha || "",
      reviewAuthority: true,
    });
    setScene(nextScene, artifact, option.dataset.title || artifactId, sourceJson);
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

  function setOverlay(nextOverlay, sourceArtifact = null, sourceTitle = "", sourceJson = null) {
    overlay = nextOverlay;
    workspaceOverlayArtifact = sourceArtifact;
    workspaceOverlayTitle = String(sourceTitle || nextOverlay.source.artifactTitle || nextOverlay.source.artifactId || "hydrology-overlay.json").slice(0, 200);
    workspaceOverlayJson = sourceArtifact ? (typeof sourceJson === "string" ? sourceJson : JSON.stringify(sourceArtifact)) : "";
    hydrologyComparison = null;
    $("comparison-mode").value = "causes";
    $("show-hydrology-delta").checked = false;
    selected = null;
    updateHydrologyControlVisibility();
    renderHydrologyRunSummary();
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
    const sourceJson = await response.text();
    const artifact = JSON.parse(sourceJson);
    setOverlay(window.SkyforgeStudioScene.adaptOverlayArtifact(artifact, scene, {
      binding: "REGISTERED_ARTIFACT",
      artifactId,
      artifactTitle: option.dataset.title || artifactId,
      sourceSha: option.dataset.sha || "",
      reviewAuthority: true,
    }), artifact, option.dataset.title || artifactId, sourceJson);
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
    $("comparison-report-status").textContent =
      "Ready to export " + comparison.samples.length + " cause samples and " +
      comparison.fieldSamples.length + " projected-field samples. Exported reports remain diagnostics and cannot replace human review.";
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
    workspaceOverlayArtifact = null;
    workspaceOverlayTitle = "";
    workspaceOverlayJson = "";
    hydrologyComparison = null;
    $("show-hydrology-delta").checked = false;
    $("comparison-status").textContent = "Attach a reference hydrology artifact first.";
    selected = null;
    updateHydrologyControlVisibility();
    renderHydrologyRunSummary();
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

  $("remember-inspection").addEventListener("change", async event => {
    const requested = event.target.checked;
    try {
      await changeWorkspaceSessionPreference(requested);
    } catch (error) {
      event.target.checked = workspaceSessionEnabled;
      setWorkspaceSessionStatus("Could not update browser-local remembering: " +
        String(error.message || error) + ". The current inspector remains open.", true);
    }
  });
  $("workspace-session-forget").addEventListener("click", () => {
    forgetRememberedWorkspace().catch(error => {
      setWorkspaceSessionStatus("Could not forget the saved inspection: " +
        String(error.message || error) + ". The current inspector remains open.", true);
    });
  });

  $("download-inspection-workspace").addEventListener("click", () => {
    try {
      downloadInspectionWorkspace();
    } catch (error) {
      $("inspection-workspace-status").textContent = "Could not download workspace: " + String(error.message || error);
      $("inspection-workspace-status").className = "small error";
    }
  });
  $("open-inspection-workspace").addEventListener("click", () => $("inspection-workspace-file").click());
  $("inspection-workspace-file").addEventListener("change", event => {
    const file = event.target.files?.[0];
    if (file) openInspectionWorkspace(file);
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
      const sourceJson = await file.text();
      const artifact = JSON.parse(sourceJson);
      setScene(window.SkyforgeStudioScene.adaptArtifact(artifact, {
        binding: "UNBOUND_LOCAL",
        artifactTitle: file.name,
        reviewAuthority: false,
      }), artifact, file.name, sourceJson);
      $("source-status").textContent =
        "Loaded local diagnostic " + file.name + "; not review authority.";
    } catch (error) {
      $("source-status").textContent = String(error.message || error);
    }
  });

  function loadLocalPair(terrainArtifact, terrainTitle, hydrologyArtifact, hydrologyTitle, terrainJson = null, hydrologyJson = null) {
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

    setScene(nextScene, terrainArtifact, terrainTitle, terrainJson);
    setOverlay(nextOverlay, hydrologyArtifact, hydrologyTitle, hydrologyJson);
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
      const entries = await Promise.all(files.map(async (file) => {
        const sourceJson = await file.text();
        return { file, sourceJson, artifact: JSON.parse(sourceJson) };
      }));
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
        hydrologyEntry.file.name,
        terrainEntry.sourceJson,
        hydrologyEntry.sourceJson
      );
    } catch (error) {
      $("source-status").textContent = String(error.message || error);
    } finally {
      event.target.value = "";
    }
  });

  async function loadHydrologyRunFolder(files) {
    const jsonFiles = Array.from(files || []).filter(file => file.name.toLowerCase().endsWith(".json"));
    if (jsonFiles.length === 0) {
      throw new Error("Choose a folder containing the terrain and hydrology JSON files.");
    }
    if (jsonFiles.length > 512) {
      throw new Error("This folder contains more than 512 JSON files. Choose a narrower run folder or select the two files directly.");
    }

    const kindCandidates = {
      terrain: [],
      hydrology: [],
    };
    await Promise.all(jsonFiles.map(async file => {
      const prefix = await file.slice(0, 8192).text();
      if (prefix.includes("SKYFORGE_TERRAIN_SEMANTIC_VOLUME")) kindCandidates.terrain.push(file);
      if (prefix.includes("SKYFORGE_BOUND_HYDROLOGY_SEMANTIC_LAYER")) kindCandidates.hydrology.push(file);
    }));

    if (kindCandidates.terrain.length !== 1 || kindCandidates.hydrology.length !== 1) {
      throw new Error(
        "Expected exactly one terrain semantic volume and one bound hydrology layer in this folder; found " +
        kindCandidates.terrain.length + " terrain and " + kindCandidates.hydrology.length +
        " hydrology files. Choose a narrower folder or select the pair directly."
      );
    }

    const [terrainFile, hydrologyFile] = [kindCandidates.terrain[0], kindCandidates.hydrology[0]];
    const [terrainSourceJson, hydrologySourceJson] = await Promise.all([
      terrainFile.text(),
      hydrologyFile.text(),
    ]);
    const terrainArtifact = JSON.parse(terrainSourceJson);
    const hydrologyArtifact = JSON.parse(hydrologySourceJson);
    if (terrainArtifact?.artifact_kind !== "SKYFORGE_TERRAIN_SEMANTIC_VOLUME" ||
        hydrologyArtifact?.artifact_kind !== "SKYFORGE_BOUND_HYDROLOGY_SEMANTIC_LAYER") {
      throw new Error("The selected files changed while Studio was reading the run folder. Choose the folder again.");
    }

    loadLocalPair(
      terrainArtifact,
      terrainFile.name,
      hydrologyArtifact,
      hydrologyFile.name,
      terrainSourceJson,
      hydrologySourceJson
    );
  }

  $("open-hydrology-run-folder").addEventListener("click", () => {
    $("local-hydrology-run-folder").click();
  });
  $("local-hydrology-run-folder").addEventListener("change", async event => {
    const files = Array.from(event.target.files || []);
    if (files.length === 0) return;
    try {
      await loadHydrologyRunFolder(files);
    } catch (error) {
      $("source-status").textContent = String(error.message || error);
    } finally {
      event.target.value = "";
    }
  });

  $("home-open-hydrology-folder").addEventListener("click", () => {
    selectWorkspaceView("inspect");
    $("open-hydrology-run-folder").click();
  });
  $("home-open-hydrology-sample").addEventListener("click", () => {
    selectWorkspaceView("inspect");
    $("open-bundled-sample").click();
  });
  $("home-open-hydrology-files").addEventListener("click", () => {
    selectWorkspaceView("inspect");
    $("local-pair-files").click();
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
        "hydrology-semantic-layer.json",
        JSON.stringify(sample.terrain),
        JSON.stringify(sample.hydrology)
      );
    } catch (error) {
      $("source-status").textContent = String(error.message || error);
    }
  });

  $("preview-bundled-comparison").addEventListener("click", () => {
    const sample = window.SKYFORGE_STUDIO_SAMPLE;
    if (!sample?.terrain || !sample?.hydrology) {
      $("source-status").textContent = "The included S2 comparison walkthrough is unavailable.";
      return;
    }
    try {
      loadLocalPair(
        sample.terrain,
        "terrain-semantic-volume.json",
        sample.hydrology,
        "hydrology-semantic-layer.json",
        JSON.stringify(sample.terrain),
        JSON.stringify(sample.hydrology)
      );
      const candidate = window.SkyforgeStudioScene.adaptOverlayArtifact(
        sample.hydrology,
        scene,
        {
          binding: "UNBOUND_LOCAL",
          artifactTitle: "included S2 specimen · same data",
          reviewAuthority: false,
        }
      );
      displayHydrologyComparison(candidate);
      selected = hydrologyComparison.comparison.samples[0] || null;
      draw();
      $("source-status").textContent =
        "Walkthrough only: this specimen is compared with itself, so the values match and deltas are zero.";
      $("comparison-value-inspector").scrollIntoView({ behavior: "smooth", block: "center" });
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
    const storageKey = WORLD_BRIEF_LIBRARY_STORAGE_KEY;
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
      status.textContent = localScopeCopy(message);
      statePill.textContent = saved ? "Saved locally" : (isDirty ? "Unsaved changes" : "Local draft");
      statePill.className = "pill " + (saved ? "good" : "warn");
      status.dataset.severity = severity;
      savedBriefs.textContent = localScopeCopy(library.briefs.length === 1 ? "1 saved brief in this browser" :
        library.briefs.length + " saved briefs in this browser");
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
      $("world-brief-delete").disabled = !activeBriefId;
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
      status.textContent = localScopeCopy("Saving this brief in your browser…");
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
        setStatus("Downloaded this local brief as JSON. It is not a generated world or review artifact.");
      } catch (error) {
        setStatus(String(error.message || error), false, "error");
      }
    }

    function exportLibrary() {
      try {
        if (!storageAvailable) {
          throw new Error("Saved briefs could not be read; use Download this brief to save the open draft.");
        }
        const existing = library.briefs.find((brief) => brief.id === activeBriefId) || null;
        const includeCurrent = Boolean(activeBriefId || isDirty || intentInput.value ||
          titleInput.value.trim() !== "Untitled world");
        const now = new Date().toISOString();
        let briefs = library.briefs.slice();
        let backupActiveId = activeBriefId || library.active_brief_id;
        if (includeCurrent) {
          const current = window.SkyforgeStudioWorldBrief.create(activeBriefId || newId(),
            titleInput.value, intentInput.value, existing?.created_at || now, now);
          briefs = [current, ...briefs.filter((brief) => brief.id !== current.id)];
          backupActiveId = current.id;
        }
        const backup = window.SkyforgeStudioWorldBrief.createLibrary(briefs, backupActiveId || null);
        const blob = new Blob([window.SkyforgeStudioWorldBrief.serializeLibrary(backup)], {
          type: "application/json",
        });
        const url = URL.createObjectURL(blob);
        const link = document.createElement("a");
        link.href = url;
        link.download = "skyforge-studio-brief-library.json";
        link.click();
        window.setTimeout(() => URL.revokeObjectURL(url), 0);
        setStatus("Downloaded a backup of " + backup.briefs.length +
          (backup.briefs.length === 1 ? " brief" : " briefs") +
          ", including current edits. Nothing was sent to a service.");
      } catch (error) {
        setStatus(String(error.message || error), false, "error");
      }
    }

    function flushPendingSave() {
      if (saveTimer !== null) {
        window.clearTimeout(saveTimer);
        saveTimer = null;
      }
      return !isDirty || saveCurrentBrief();
    }

    async function importBrief(event) {
      const file = event.target.files?.[0];
      if (!file) return;
      try {
        if (file.size > 1_000_000) throw new Error("world brief files must be smaller than 1 MB");
        const document = JSON.parse(await file.text());
        if (document?.document_type === window.SkyforgeStudioWorldBrief.libraryType) {
          const importedLibrary = window.SkyforgeStudioWorldBrief.parseLibrary(document);
          if (!flushPendingSave()) return;
          const previousCount = library.briefs.length;
          const merged = window.SkyforgeStudioWorldBrief.mergeLibraries(library, importedLibrary);
          window.localStorage.setItem(storageKey,
            window.SkyforgeStudioWorldBrief.serializeLibrary(merged));
          storageAvailable = true;
          library = merged;
          activeBriefId = merged.active_brief_id;
          loadBrief(library.briefs.find((brief) => brief.id === activeBriefId) || null);
          const added = library.briefs.length - previousCount;
          setStatus("Imported a library backup; added " + added +
            (added === 1 ? " new brief. " : " new briefs. ") +
            "Identical drafts were skipped; local drafts were not overwritten.", true);
          return;
        }
        const imported = window.SkyforgeStudioWorldBrief.parse(document);
        if (!flushPendingSave()) return;
        activeBriefId = null;
        titleInput.value = imported.title;
        intentInput.value = imported.intent;
        isDirty = true;
        setStatus("Imported as a new local draft. It will save in this browser automatically.", false);
        scheduleSave();
      } catch (error) {
        setStatus("Could not import this brief or library: " + String(error.message || error), false, "error");
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
    $("world-brief-library-download").addEventListener("click", exportLibrary);
    $("world-brief-import-button").addEventListener("click", () => $("world-brief-import-file").click());
    $("world-brief-import-file").addEventListener("change", importBrief);
    $("world-brief-delete").addEventListener("click", () => {
      if (!activeBriefId || !flushPendingSave()) return;
      const selected = library.briefs.find((brief) => brief.id === activeBriefId);
      if (!selected) return;
      if (!window.confirm("Delete saved brief \"" + selected.title +
        "\" from this browser?")) return;
      try {
        const nextLibrary = window.SkyforgeStudioWorldBrief.removeBrief(library, selected.id);
        window.localStorage.setItem(storageKey,
          window.SkyforgeStudioWorldBrief.serializeLibrary(nextLibrary));
        library = nextLibrary;
        activeBriefId = nextLibrary.active_brief_id;
        loadBrief(library.briefs.find((brief) => brief.id === activeBriefId) || null);
        setStatus("Deleted \"" + selected.title + "\" from this browser.",
          Boolean(activeBriefId));
      } catch (error) {
        setStatus("Could not delete this saved brief: " + String(error.message || error), false, "error");
      }
    });
    readLibrary();
    window.SkyforgeStudioWorldBriefWorkspace = Object.freeze({
      flush: flushPendingSave,
      refresh: () => {
        if (!flushPendingSave()) return false;
        readLibrary();
        return storageAvailable;
      },
    });
  }

  function studioBackupValidators() {
    return {
      worldBrief: window.SkyforgeStudioWorldBrief,
      workspaceSession: window.SkyforgeStudioWorkspaceSession,
      workspacePackage: window.SkyforgeStudioWorkspacePackage,
      terrainLibrary: window.SkyforgeStudioTerrainComparisonLibrary,
      terrainReportReader: window.SkyforgeStudioTerrainComparisonReportReader,
      regionalLibrary: window.SkyforgeStudioRegionalComparisonLibrary,
      regionalComparison: window.SkyforgeRegionalComparison,
      regionalInventory: window.SkyforgeRegionalInventory,
    };
  }

  async function flushPendingBackupInspection() {
    if (!workspaceSessionController) {
      throw new Error("inspection storage is unavailable; Studio cannot create a complete backup");
    }
    if (workspaceSessionSaveTimer !== null) window.clearTimeout(workspaceSessionSaveTimer);
    workspaceSessionSaveTimer = null;
    const shouldSaveCurrentInspection = workspaceSessionEnabled && Boolean(workspaceSceneArtifact);
    const serializedWorkspace = shouldSaveCurrentInspection ? serializeInspectionWorkspace() : null;
    const generation = shouldSaveCurrentInspection ? ++workspaceSessionGeneration : workspaceSessionGeneration;
    await workspaceSessionSaveChain.catch(() => {});
    if (!shouldSaveCurrentInspection) return;
    const saved = await workspaceSessionController.save(serializedWorkspace);
    if (!saved) {
      throw new Error("remembered inspection is no longer enabled in browser storage; reload Studio before creating a backup");
    }
    if (generation === workspaceSessionGeneration && workspaceSessionEnabled) {
      workspaceSessionHasSaved = true;
      setWorkspaceSessionStatus("Saved in this browser. Sources remain local diagnostics and are never uploaded.");
    }
  }

  async function readStudioBackupState() {
    await flushPendingBackupInspection();
    const briefApi = window.SkyforgeStudioWorldBrief;
    const rawBriefLibrary = window.localStorage.getItem(WORLD_BRIEF_LIBRARY_STORAGE_KEY);
    const worldBriefLibrary = rawBriefLibrary
      ? briefApi.parseLibrary(rawBriefLibrary)
      : briefApi.createLibrary();
    if (!workspaceSessionController) throw new Error("inspection storage is unavailable; Studio cannot create a complete backup");
    const [inspectionSession, terrainComparisons, regionalComparisons] = await Promise.all([
      workspaceSessionController.read(),
      window.SkyforgeStudioTerrainComparisonLibrary.createRepository().list(),
      window.SkyforgeStudioRegionalComparisonLibrary.createRepository().list(),
    ]);
    return window.SkyforgeStudioProfileBackup.create({
      createdAt: new Date().toISOString(),
      worldBriefLibrary,
      inspectionSession,
      terrainComparisons,
      regionalComparisons,
    }, studioBackupValidators());
  }

  function setStudioBackupStatus(message, failed = false) {
    const node = $("studio-backup-status");
    node.textContent = message;
    node.className = failed ? "small error" : "small muted";
  }

  async function exportStudioBackup() {
    try {
      if (!window.SkyforgeStudioWorldBriefWorkspace?.flush()) {
        throw new Error("save the open world brief before creating a backup");
      }
      const state = await readStudioBackupState();
      const fileText = JSON.stringify(state) + "\n";
      const blob = new Blob([fileText], {type:"application/json"});
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = "skyforge-studio-backup.json";
      link.click();
      window.setTimeout(() => URL.revokeObjectURL(url), 0);
      setStudioBackupStatus(
        "Saved one backup with " + state.world_brief_library.briefs.length + " brief(s), " +
        state.terrain_comparisons.length + " terrain comparison(s), and " +
        state.regional_comparisons.length + " geology comparison(s). Credentials are excluded."
      );
    } catch (error) {
      setStudioBackupStatus("Could not create this backup: " + String(error.message || error), true);
    }
  }

  async function restoreStudioBackup(file) {
    if (!file) return;
    try {
      if (file.size > window.SkyforgeStudioProfileBackup.maximumFileBytes) {
        throw new Error("Studio backup is larger than the supported file size");
      }
      if (!window.SkyforgeStudioWorldBriefWorkspace?.flush()) {
        throw new Error("save the open world brief before restoring a backup");
      }
      const validators = studioBackupValidators();
      const imported = window.SkyforgeStudioProfileBackup.parse(await file.text(), validators);
      const current = await readStudioBackupState();
      let plan = window.SkyforgeStudioProfileBackup.prepareRestore(imported, current, validators);
      let replaceInspection = false;
      if (plan.inspectionReplacementRequired) {
        replaceInspection = window.confirm(
          "This backup contains a different remembered inspection. Replace the one saved on this device? Choose Cancel to keep the current inspection and restore the other backup data."
        );
        if (replaceInspection) {
          plan = window.SkyforgeStudioProfileBackup.prepareRestore(
            imported, current, validators, {replaceInspection:true}
          );
        }
      }

      const inspectionChanged = JSON.stringify(plan.inspectionSession) !==
        JSON.stringify(current.inspection_session);
      if (inspectionChanged && plan.inspectionSession && !workspaceSessionController) {
        throw new Error("inspection storage is unavailable");
      }
      const inspectionPlan = plan.inspectionSession && plan.inspectionSession.workspace_json !== null
        ? prepareInspectionWorkspace(plan.inspectionSession.workspace_json)
        : null;

      const localStorage = window.localStorage;
      localStorage.setItem(
        WORLD_BRIEF_LIBRARY_STORAGE_KEY,
        window.SkyforgeStudioWorldBrief.serializeLibrary(plan.worldBriefLibrary)
      );
      await window.SkyforgeStudioTerrainComparisonLibrary.createRepository()
        .replaceAll(plan.terrainComparisons);
      await window.SkyforgeStudioRegionalComparisonLibrary.createRepository()
        .replaceAll(plan.regionalComparisons);

      if (inspectionChanged && plan.inspectionSession) {
        if (!workspaceSessionController) throw new Error("inspection storage is unavailable");
        workspaceSessionGeneration += 1;
        if (workspaceSessionSaveTimer !== null) window.clearTimeout(workspaceSessionSaveTimer);
        workspaceSessionSaveTimer = null;
        await workspaceSessionSaveChain.catch(() => {});
        await workspaceSessionController.enable(plan.inspectionSession.workspace_json);
        workspaceSessionEnabled = true;
        workspaceSessionReady = true;
        workspaceSessionHasSaved = plan.inspectionSession.workspace_json !== null;
        $("remember-inspection").checked = true;
        setWorkspaceSessionStatus(
          workspaceSessionHasSaved
            ? "Restored a saved inspection as an unbound local diagnostic. Registered verification was not restored."
            : "Restored the remember-inspection preference for this device."
        );
        if (workspaceSessionHasSaved) {
          applyInspectionWorkspacePlan(inspectionPlan,
            "Restored from a local backup as an unbound diagnostic; registered verification was not restored.");
        }
      }

      if (!window.SkyforgeStudioWorldBriefWorkspace.refresh()) {
        throw new Error("brief library was restored, but the open brief could not be refreshed");
      }
      await initializeTerrainComparisonLibrary();
      window.dispatchEvent(new Event("skyforge-studio-backup-restored"));
      const total = plan.counts.briefsAdded + plan.counts.terrainComparisonsAdded +
        plan.counts.regionalComparisonsAdded;
      setStudioBackupStatus(
        "Restored " + total + " new saved item(s). Matching IDs were kept. " +
        (plan.inspectionReplacementRequired && !replaceInspection
          ? "The current remembered inspection was kept."
          : "Your saved work remains on this device.")
      );
    } catch (error) {
      setStudioBackupStatus(
        "Restore stopped: " + String(error.message || error) +
        ". You can safely retry the same backup; matching items are merged by ID.", true
      );
    }
  }

  $("studio-backup-export").addEventListener("click", exportStudioBackup);
  $("studio-backup-import").addEventListener("click", () => $("studio-backup-file").click());
  $("studio-backup-file").addEventListener("change", async event => {
    const file = event.target.files && event.target.files[0];
    try { await restoreStudioBackup(file); }
    finally { event.target.value = ""; }
  });

  function configureWorkspaceNavigation() {
    for (const button of document.querySelectorAll("[data-workspace-view]")) {
      button.addEventListener("click", () => selectWorkspaceView(button.dataset.workspaceView));
    }
    for (const button of document.querySelectorAll("[data-open-workspace]")) {
      button.addEventListener("click", () => selectWorkspaceView(button.dataset.openWorkspace));
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

  function appendComparisonReportSummary(label, value) {
    const term = document.createElement("dt");
    const description = document.createElement("dd");
    term.textContent = label;
    description.textContent = value;
    $("imported-comparison-report-summary").append(term, description);
  }

  function reportValueText(value) {
    if (value === null) return "null";
    return typeof value === "string" ? value : JSON.stringify(value);
  }

  function renderImportedHydrologyComparisonReport() {
    if (!importedHydrologyComparisonReport) return;
    const parsed = importedHydrologyComparisonReport;
    const report = parsed.document;
    const summary = $("imported-comparison-report-summary");
    summary.replaceChildren();
    appendComparisonReportSummary("Format", "Version " + report.format_version);
    appendComparisonReportSummary("Selected field", report.selected_field +
      (report.selected_cause_field ? " · " + report.selected_cause_field : ""));
    appendComparisonReportSummary("Terrain semantic SHA-256", report.binding.terrainSemanticSha256);
    appendComparisonReportSummary("AUTH-0046 association", report.binding.associationToken);
    appendComparisonReportSummary("Cause-grid samples", String(parsed.causeSampleCount));
    appendComparisonReportSummary("Projected-field samples", String(parsed.projectedFieldSampleCount));
    appendComparisonReportSummary("Recorded source authority",
      (parsed.sourceReviewAuthorityRecorded ? "Claimed" : "Not claimed") +
      " · imported data is unverified");

    const kind = $("comparison-report-sample-set").value;
    const page = window.SkyforgeStudioComparisonReportReader.page(
      parsed, kind, importedComparisonReportPage);
    const rows = $("comparison-report-samples");
    rows.replaceChildren();
    for (const sample of page.items) {
      const row = document.createElement("tr");
      const changes = kind === "causes" ? sample.deltas : {
        surfaceDeltaY: sample.surfaceDeltaY,
        waterSurfaceDeltaY: sample.waterSurfaceDeltaY,
        waterDepthDelta: sample.waterDepthDelta,
        wetTransition: sample.wetTransition,
      };
      const values = [
        JSON.stringify(sample.grid),
        kind === "causes" ? "aligned" : sample.coverage,
        JSON.stringify(sample.reference),
        JSON.stringify(sample.candidate),
        JSON.stringify(changes),
      ];
      for (const value of values) {
        const cell = document.createElement("td");
        cell.textContent = reportValueText(value);
        row.append(cell);
      }
      rows.append(row);
    }
    $("comparison-report-page-status").textContent = page.total === 0
      ? "No samples in this set."
      : "Samples " + page.firstIndex + "–" + page.lastIndex + " of " + page.total +
        " · page " + (page.pageIndex + 1) + " of " + page.pageCount +
        ". Values are shown as recorded; Studio does not recalculate them.";
    $("comparison-report-previous").disabled = page.pageIndex === 0;
    $("comparison-report-next").disabled = page.pageIndex + 1 >= page.pageCount;
  }

  function openImportedHydrologyComparisonReport(parsed, filename) {
    importedHydrologyComparisonReport = parsed;
    importedComparisonReportPage = 0;
    $("comparison-report-sample-set").value = "causes";
    $("imported-comparison-report-note").textContent =
      "Opened " + filename + " as a local diagnostic snapshot. Its recorded provenance is unverified; it did not load terrain, change bindings, or establish review authority.";
    $("imported-comparison-report-metadata").textContent = JSON.stringify({
      binding: parsed.document.binding,
      sources: parsed.document.sources,
    }, null, 2);
    $("imported-comparison-report").hidden = false;
    renderImportedHydrologyComparisonReport();
    $("comparison-report-import-status").textContent =
      "Opened the saved report. Source authority is displayed only as recorded metadata.";
  }

  function downloadHydrologyComparisonReport() {
    if (!overlay || !hydrologyComparison) return;
    const mode = $("comparison-mode").value;
    const report = window.SkyforgeStudioComparisonReport.create({
      comparison: hydrologyComparison.comparison,
      reference: overlay,
      candidate: hydrologyComparison.candidate,
      selectedField: mode,
      selectedCauseField: mode === "causes" ? $("hydrology-potential").value : null,
    });
    const blob = new Blob([JSON.stringify(report, null, 2) + "\n"], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = window.SkyforgeStudioComparisonReport.filename(report);
    link.click();
    window.setTimeout(() => URL.revokeObjectURL(url), 0);
    $("comparison-report-status").textContent =
      "Downloaded the complete comparison report. It is diagnostic data, not independent human-review evidence.";
  }

  $("open-comparison-report").addEventListener("click", () => $("comparison-report-file").click());
  $("comparison-report-file").addEventListener("change", async (event) => {
    const file = event.target.files?.[0];
    if (!file) return;
    try {
      const parsed = window.SkyforgeStudioComparisonReportReader.parse(
        await file.text(), file.size);
      openImportedHydrologyComparisonReport(parsed, file.name);
    } catch (error) {
      $("comparison-report-import-status").textContent =
        "Could not open this comparison report: " + String(error.message || error);
    } finally {
      event.target.value = "";
    }
  });
  $("close-comparison-report").addEventListener("click", () => {
    importedHydrologyComparisonReport = null;
    $("imported-comparison-report").hidden = true;
    $("imported-comparison-report-summary").replaceChildren();
    $("imported-comparison-report-metadata").textContent = "";
    $("comparison-report-samples").replaceChildren();
    $("comparison-report-import-status").textContent = "Closed the saved comparison report.";
  });
  $("comparison-report-sample-set").addEventListener("change", () => {
    importedComparisonReportPage = 0;
    renderImportedHydrologyComparisonReport();
  });
  $("comparison-report-previous").addEventListener("click", () => {
    if (importedComparisonReportPage === 0) return;
    importedComparisonReportPage -= 1;
    renderImportedHydrologyComparisonReport();
  });
  $("comparison-report-next").addEventListener("click", () => {
    if (!importedHydrologyComparisonReport) return;
    const page = window.SkyforgeStudioComparisonReportReader.page(
      importedHydrologyComparisonReport,
      $("comparison-report-sample-set").value,
      importedComparisonReportPage);
    if (importedComparisonReportPage + 1 >= page.pageCount) return;
    importedComparisonReportPage += 1;
    renderImportedHydrologyComparisonReport();
  });

  $("clear-hydrology-comparison").addEventListener("click", clearHydrologyComparison);
  $("download-comparison-report").addEventListener("click", () => {
    try {
      downloadHydrologyComparisonReport();
    } catch (error) {
      $("comparison-report-status").textContent =
        "Could not export this comparison: " + String(error.message || error);
    }
  });
  $("open-sample-trace").addEventListener("click", () => {
    $("sample-trace-file").click();
  });
  $("sample-trace-file").addEventListener("change", async (event) => {
    const file = event.target.files?.[0];
    if (!file) return;
    const status = $("sample-trace-import-status");
    try {
      if (file.size > window.SkyforgeStudioTraceViewer.maximumFileBytes) {
        throw new Error("sample trace files must be smaller than 1 MB");
      }
      const trace = window.SkyforgeStudioTraceViewer.parse(await file.text());
      renderImportedSampleTrace(trace, file.name);
      status.textContent =
        "Opened one read-only sample trace. Its source authority remains recorded data only.";
    } catch (error) {
      status.textContent = "Could not open sample trace: " + String(error.message || error);
    } finally {
      event.target.value = "";
    }
  });
  $("clear-imported-sample-trace").addEventListener("click", () => {
    $("imported-sample-trace").hidden = true;
    $("imported-sample-trace-fields").replaceChildren();
    $("imported-sample-trace-context").textContent = "";
    $("imported-sample-trace-sample").textContent = "";
    $("sample-trace-import-status").textContent = "Closed the sample trace record.";
  });
  $("download-sample-trace").addEventListener("click", () => {
    try {
      downloadSelectedSampleTrace(selected);
    } catch (error) {
      $("sample-export-status").textContent = "Could not download this sample trace: " +
        String(error.message || error);
    }
  });

  $("local-overlay-file").addEventListener("change", async (event) => {
    const file = event.target.files?.[0];
    if (!file) return;
    try {
      if (!isTerrain()) {
        throw new Error("load a terrain semantic volume before attaching hydrology");
      }
      const sourceJson = await file.text();
      const artifact = JSON.parse(sourceJson);
      setOverlay(window.SkyforgeStudioScene.adaptOverlayArtifact(artifact, scene, {
        binding: "UNBOUND_LOCAL",
        artifactTitle: file.name,
        reviewAuthority: false,
      }), artifact, file.name, sourceJson);
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
  window.addEventListener("resize", drawTerrainComparisonMap);

  $("terrain-comparison-demo").addEventListener("click", loadTerrainComparisonDemo);
  $("terrain-comparison-reference").addEventListener("change", handleTerrainComparisonFilesChanged);
  $("terrain-comparison-candidate").addEventListener("change", handleTerrainComparisonFilesChanged);
  $("terrain-comparison-report-file").addEventListener("change", event => {
    loadTerrainComparisonReport(event.currentTarget.files?.[0] || null);
  });
  $("terrain-comparison-report-download").addEventListener("click", downloadTerrainComparisonReport);
  $("terrain-comparison-library-title").addEventListener("input", renderTerrainComparisonLibraryControls);
  $("terrain-comparison-library-select").addEventListener("change", renderTerrainComparisonLibraryControls);
  $("terrain-comparison-library-save").addEventListener("click", saveTerrainComparisonToBrowser);
  $("terrain-comparison-library-open").addEventListener("click", openTerrainComparisonFromBrowser);
  $("terrain-comparison-library-remove").addEventListener("click", removeTerrainComparisonFromBrowser);
  $("terrain-comparison-previous").addEventListener("click", () => {
    if (terrainComparisonPage > 0) {
      terrainComparisonPage -= 1;
      renderTerrainComparisonPage();
    }
  });
  $("terrain-comparison-next").addEventListener("click", () => {
    if (!terrainSemanticComparison) return;
    const next = terrainSemanticComparison.pageChangedCells(
      terrainComparisonPage + 1, TERRAIN_COMPARISON_PAGE_SIZE);
    if (next.items.length) {
      terrainComparisonPage += 1;
      renderTerrainComparisonPage();
    }
  });
  $("terrain-comparison-map").addEventListener("click", event => {
    if (!terrainSemanticComparison || !terrainComparisonPlot?.cellWidth) return;
    const rect = event.currentTarget.getBoundingClientRect();
    const x = Math.floor((event.clientX - rect.left - terrainComparisonPlot.left) /
      terrainComparisonPlot.cellWidth);
    const z = Math.floor((event.clientY - rect.top - terrainComparisonPlot.top) /
      terrainComparisonPlot.cellHeight);
    const column = terrainSemanticComparison.columns.find(item => item.x === x && item.z === z);
    if (column) selectTerrainComparisonColumn(column);
  });

  configureWorkspaceNavigation();
  initializeWorldBrief();
  showLocalMode();
  configureRuntimeCapabilities();
  configureBundledSample();
  initializeWorkspaceSession();
  initializeTerrainComparisonLibrary();
  if (token) {
    $("api-token").value = token;
  }
})();
