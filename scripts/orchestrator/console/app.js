(() => {
  "use strict";

  const POLL_MS = 2500;
  const TOKEN_KEY = "skyforge-development-api-token";
  let token = sessionStorage.getItem(TOKEN_KEY) || "";
  let lastDigest = "";
  let pollHandle = null;

  const $ = (id) => document.getElementById(id);
  const el = (tag, text, className) => {
    const node = document.createElement(tag);
    if (text !== undefined && text !== null) node.textContent = String(text);
    if (className) node.className = className;
    return node;
  };

  function clear(node) { while (node.firstChild) node.removeChild(node.firstChild); }

  function pill(text, severity = "") {
    return el("span", text || "—", `pill ${severity}`.trim());
  }

  function card(label, value) {
    const node = el("div", null, "summary-card");
    node.append(el("div", label, "label"), el("div", value ?? "—", "value"));
    return node;
  }

  function kv(entries) {
    const dl = el("dl", null, "kv");
    for (const [key, value] of entries) {
      dl.append(el("dt", key), el("dd", value ?? "—"));
    }
    return dl;
  }

  async function api(path, options = {}) {
    const headers = new Headers(options.headers || {});
    headers.set("Authorization", `Bearer ${token}`);
    if (options.etag) headers.set("If-None-Match", `"${options.etag}"`);
    return fetch(path, { ...options, headers, cache: "no-store" });
  }

  function setConnection(text, severity) {
    const node = $("connection");
    node.textContent = text;
    node.className = `pill ${severity || "muted"}`;
  }

  function renderList(container, values, renderer, emptyText) {
    clear(container);
    if (!values || values.length === 0) {
      container.append(el("div", emptyText, "muted small"));
      return;
    }
    for (const value of values) container.append(renderer(value));
  }

  function renderSummary(state) {
    const grid = $("summary-grid");
    clear(grid);
    const runtime = state.runtime || {};
    const driver = runtime.production_execution_driver || {};
    grid.append(
      card("Checkout", state.checkout_head_sha),
      card("Controller", runtime.controller),
      card("Runtime", runtime.runtime_mode),
      card("Status", runtime.status),
      card("Pending events", runtime.pending_event_count),
      card("Execution", driver.last_disposition || (driver.running ? "RUNNING" : "STOPPED"))
    );
  }

  function renderRoadmap(state) {
    const roadmap = state.roadmap || {};
    const active = roadmap.active || {};
    const blocked = roadmap.blocked_nodes || {};
    const node = $("roadmap");
    clear(node);
    node.append(kv([
      ["Roadmap", roadmap.roadmap_id],
      ["Active node", active.node_id || active.issue_number || "none"],
      ["Claims today", roadmap.claims_today],
      ["Blocked nodes", Object.keys(blocked).length],
      ["Completed nodes", Object.keys(roadmap.completed_runs || {}).length],
    ]));
    if (Object.keys(blocked).length) {
      const list = el("ul", null, "clean");
      for (const [name, value] of Object.entries(blocked)) {
        list.append(el("li", `${name}: ${(value && value.reason) || "blocked"}`));
      }
      node.append(list);
    }
  }

  function renderObjectives(state) {
    $("objective-count").textContent = `${state.objective_count || 0} total`;
    renderList($("objectives"), state.objectives || [], (objective) => {
      const node = el("article", null, "item");
      const source = objective.source || {};
      node.append(el("h3", source.objective_text || objective.proposal_id || "Objective"));
      node.append(kv([
        ["Disposition", objective.disposition],
        ["Reason", objective.reason],
        ["Issue", source.issue_number],
        ["Actor", source.actor],
      ]));
      if (objective.candidate_task) {
        node.append(el("div", `Candidate: ${objective.candidate_task.node_id || objective.candidate_task.objective}`, "small muted"));
      }
      if (objective.human_gate) {
        node.append(el("div", `Human gate: ${objective.human_gate.node_id} — ${objective.human_gate.message || ""}`, "small muted"));
      }
      return node;
    }, "No objective proposals recorded.");
  }

  function renderWorkers(state) {
    const execution = state.execution || {};
    $("worker-count").textContent = `${execution.worker_count || 0} total`;
    renderList($("workers"), execution.workers || [], (worker) => {
      const node = el("article", null, "item");
      node.append(el("h3", worker.objective || worker.task_id || "Worker"));
      node.append(kv([
        ["Status", worker.status],
        ["Lane", worker.lane],
        ["Branch", worker.branch],
        ["Model", worker.model],
        ["Summary", worker.summary],
        ["Failure", worker.failure_kind],
      ]));
      return node;
    }, "No worker records.");
  }

  function renderExecution(state) {
    const execution = state.execution || {};
    const runtime = state.runtime || {};
    const node = $("execution");
    clear(node);
    const plan = execution.active_plan || {};
    const admission = execution.admission || {};
    const driver = runtime.production_execution_driver || {};
    const blockers = runtime.production_execution_gate_blockers || [];
    node.append(kv([
      ["Plan", plan.plan_id || "none"],
      ["Plan status", plan.status || "—"],
      ["Admission", admission.outcome || "none"],
      ["Attempt", admission.attempt_id || "—"],
      ["Driver", driver.last_disposition || (driver.running ? "RUNNING" : "STOPPED")],
      ["Gate digest", runtime.production_execution_gate_digest || "—"],
    ]));
    if (blockers.length) {
      const list = el("ul", null, "clean");
      for (const blocker of blockers) list.append(el("li", blocker));
      node.append(list);
    }
  }

  async function downloadArtifact(artifact) {
    const response = await api(`/api/v1/artifacts/${encodeURIComponent(artifact.artifact_id)}/content`);
    if (!response.ok) throw new Error(`Artifact fetch failed (${response.status})`);
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = artifact.file?.repository_path?.split("/").pop() || "skyforge-artifact";
    document.body.append(anchor);
    anchor.click();
    anchor.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  function renderArtifact(artifact) {
    const node = el("div", null, "artifact");
    node.append(el("h3", artifact.title || artifact.artifact_id));
    node.append(kv([
      ["Artifact", artifact.artifact_id],
      ["Kind", artifact.kind],
      ["Source SHA", artifact.source_sha],
      ["Description", artifact.description],
    ]));
    if (artifact.kind === "INTERACTIVE_SPECIMEN" && artifact.interactive) {
      const spec = artifact.interactive;
      node.append(el("div", `Specimen: ${spec.specimen_kind || "interactive"}`, "small muted"));
      for (const command of spec.preparation_entry_points || []) node.append(el("code", command, "command"));
      if (spec.launch_entry_point) node.append(el("code", spec.launch_entry_point, "command"));
      for (const action of spec.review_actions || []) node.append(el("code", action, "command"));
    }
    if (artifact.kind === "FILE" && artifact.file) {
      const actions = el("div", null, "actions");
      const button = el("button", "Fetch file");
      button.type = "button";
      button.addEventListener("click", () => downloadArtifact(artifact).catch((error) => {
        $("auth-error").textContent = error.message;
      }));
      actions.append(button);
      node.append(actions);
    }
    return node;
  }

  function renderReviews(state) {
    $("review-count").textContent = `${state.human_review_count || 0} total`;
    renderList($("reviews"), state.human_reviews || [], (review) => {
      const node = el("article", null, "item");
      const severity = review.verdict === "ACCEPTED" ? "good" : "warn";
      const heading = el("div", null, "section-heading");
      heading.append(el("h3", review.gate_id || "Human review"), pill(review.verdict, severity));
      node.append(heading);
      node.append(kv([
        ["Artifact", review.artifact_id],
        ["Source SHA", review.source_sha],
        ["Material delta", review.material_delta],
        ["Next boundary", review.next_boundary],
        ["Deferred", review.deferred_product_work ? "yes" : "no"],
      ]));
      if ((review.positive_findings || []).length) {
        node.append(el("div", "Positive findings", "small muted"));
        const list = el("ul", null, "clean");
        for (const finding of review.positive_findings) list.append(el("li", finding));
        node.append(list);
      }
      if ((review.findings || []).length) {
        node.append(el("div", "Required changes / findings", "small muted"));
        const list = el("ul", null, "clean");
        for (const finding of review.findings) list.append(el("li", finding));
        node.append(list);
      }
      if (review.artifact) node.append(renderArtifact(review.artifact));
      return node;
    }, "No human reviews recorded.");
  }

  function renderClaims(state) {
    renderList($("claims"), state.external_claims || [], (claim) => {
      const node = el("article", null, "item");
      node.append(kv([
        ["Issue", claim.issue_number], ["Lane", claim.lane], ["Branch", claim.branch],
        ["PR", claim.pr_number], ["Owner", claim.claimed_by], ["State", claim.state],
      ]));
      return node;
    }, "No external ownership claims.");
  }

  function renderCompletions(state) {
    const execution = state.execution || {};
    renderList($("completions"), execution.completions || [], (completion) => {
      const node = el("article", null, "item");
      node.append(kv([
        ["Status", completion.status], ["Issue", completion.issue_number],
        ["Attempt", completion.attempt_id], ["Worker", completion.worker_run_id],
      ]));
      return node;
    }, "No completion records.");
  }

  function render(state) {
    $("console-content").hidden = false;
    $("snapshot-id").textContent = `snapshot ${state.snapshot_digest || "—"}`;
    renderSummary(state);
    renderRoadmap(state);
    renderObjectives(state);
    renderWorkers(state);
    renderExecution(state);
    renderReviews(state);
    renderClaims(state);
    renderCompletions(state);
  }

  async function refresh() {
    if (!token) return;
    try {
      const response = await api("/api/v1/development-state", { etag: lastDigest });
      if (response.status === 304) {
        setConnection("Current", "good");
        return;
      }
      if (response.status === 401 || response.status === 503) {
        throw new Error(`Development API unavailable (${response.status})`);
      }
      if (!response.ok) throw new Error(`Development state fetch failed (${response.status})`);
      const state = await response.json();
      if (state.snapshot_digest !== lastDigest) {
        lastDigest = state.snapshot_digest || "";
        render(state);
      }
      setConnection("Current", "good");
      $("auth-error").textContent = "";
    } catch (error) {
      setConnection("Disconnected", "bad");
      $("auth-error").textContent = error.message;
    }
  }

  function schedulePolling() {
    if (pollHandle) clearInterval(pollHandle);
    pollHandle = setInterval(refresh, POLL_MS);
  }

  $("auth-form").addEventListener("submit", (event) => {
    event.preventDefault();
    token = $("api-token").value.trim();
    if (!token) return;
    sessionStorage.setItem(TOKEN_KEY, token);
    lastDigest = "";
    refresh();
    schedulePolling();
  });

  $("forget-token").addEventListener("click", () => {
    token = "";
    sessionStorage.removeItem(TOKEN_KEY);
    $("api-token").value = "";
    $("console-content").hidden = true;
    lastDigest = "";
    if (pollHandle) clearInterval(pollHandle);
    pollHandle = null;
    setConnection("Disconnected", "muted");
  });

  if (token) {
    $("api-token").value = token;
    refresh();
    schedulePolling();
  }
})();
