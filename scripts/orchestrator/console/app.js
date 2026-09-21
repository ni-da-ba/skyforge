(() => {
  "use strict";

  const POLL_MS = 2500;
  const TOKEN_KEY = "skyforge-development-api-token";
  const WRITE_TOKEN_KEY = "skyforge-development-write-token";
  let token = sessionStorage.getItem(TOKEN_KEY) || "";
  let writeToken = sessionStorage.getItem(WRITE_TOKEN_KEY) || "";
  let lastDigest = "";
  let latestState = null;
  let pollHandle = null;
  let pendingObjectiveRequestId = "";
  let pendingObjectiveText = "";
  let pendingReviewRequestId = "";
  let pendingLifecycleRequestId = "";
  let pendingLifecycleSignature = "";

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

  async function writeApi(path, options = {}) {
    if (!writeToken) throw new Error("Write bearer token is required for development commands.");
    const headers = new Headers(options.headers || {});
    headers.set("Authorization", `Bearer ${writeToken}`);
    headers.set("X-Skyforge-Client", "operations-console");
    return fetch(path, { ...options, headers, cache: "no-store" });
  }

  function textLines(value) {
    return String(value || "")
      .split(/\r?\n/)
      .map((line) => line.trim())
      .filter(Boolean);
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

  function renderScorecard(state) {
    const node = $("scorecard");
    clear(node);
    const score = state.scorecard || {};
    const objectives = score.objectives || {};
    const workers = score.workers || {};
    const scheduler = score.scheduler || {};
    const reviews = score.human_reviews || {};
    const claims = score.external_claims || {};
    const budgets = score.budget || {};
    const localBudget = budgets.local || {};
    const providerBudget = budgets.provider || {};
    const localUsage = localBudget.usage || {};
    const hosted = score.hosted_value || {};
    node.append(kv([
      ["Objectives", objectives.total],
      ["Paused / cancelled", String(objectives.paused ?? "—") + " / " + String(objectives.cancelled ?? "—")],
      ["Workers", workers.total],
      ["Handoff / failed / interrupted", String(workers.handoff_ready ?? "—") + " / " + String(workers.failed ?? "—") + " / " + String(workers.interrupted ?? "—")],
      ["Completions", (score.completions || {}).total],
      ["Scheduler executing / waiting / recovery", String(scheduler.executing ?? "—") + " / " + String(scheduler.waiting ?? "—") + " / " + String(scheduler.recovery_required ?? "—")],
      ["Human review accepted / changes required", String(reviews.accepted ?? "—") + " / " + String(reviews.changes_required ?? "—")],
      ["External claims", claims.active],
      ["Local budget classifier / Luna / Terra", localBudget.available
        ? String(localUsage.classifier_calls ?? "—") + " / " + String(localUsage.luna_worker_calls ?? "—") + " / " + String(localUsage.terra_worker_calls ?? "—")
        : "unavailable: " + (localBudget.reason || "not reported")],
      ["Provider quota telemetry", providerBudget.available
        ? "available"
        : "unavailable: " + (providerBudget.reason || "not durably persisted")],
      ["Hosted value telemetry", hosted.available ? (hosted.report_file || "available") : "unavailable: " + (hosted.reason || "not reported")],
      ["Hosted cost", hosted.available && hosted.cost ? JSON.stringify(hosted.cost) : "unavailable"],
      ["Hosted trailing window", hosted.available && hosted.trailing_window ? JSON.stringify(hosted.trailing_window) : "unavailable"],
      ["Hosted evaluation", hosted.available && hosted.evaluation ? JSON.stringify(hosted.evaluation) : "unavailable"],
    ]));
    if (score.slo_note) node.append(el("div", score.slo_note, "small muted"));
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
      const control = (state.objective_controls || []).find(
        (value) => value.proposal_id === objective.proposal_id
      );
      node.append(kv([
        ["Disposition", objective.disposition],
        ["Lifecycle", control ? control.state : "ACTIVE"],
        ["Last control", control ? control.last_operation : "none"],
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

  function renderObjectiveControl(state) {
    const panel = $("objective-control");
    const enabled = Boolean(
      writeToken
      && state
      && state.runtime
      && state.runtime.development_write_api_enabled
    );
    panel.hidden = !enabled;
    if (!enabled) $("objective-status").textContent = "";
  }

  function updateLifecycleWarning() {
    const cancelling = $("objective-lifecycle-operation").value === "CANCEL";
    $("objective-cancel-confirm-row").hidden = !cancelling;
    if (!cancelling) $("objective-cancel-confirm").checked = false;
  }

  function renderObjectiveLifecycleControl(state) {
    const panel = $("objective-lifecycle-control");
    const objectives = state.objectives || [];
    const enabled = Boolean(
      writeToken
      && objectives.length
      && state.runtime
      && state.runtime.development_write_api_enabled
    );
    panel.hidden = !enabled;
    if (!enabled) {
      $("objective-lifecycle-status").textContent = "";
      return;
    }
    const controls = state.objective_controls || [];
    populateSelect(
      $("objective-lifecycle-id"),
      objectives,
      (objective) => objective.proposal_id,
      (objective) => {
        const source = objective.source || {};
        const control = controls.find((value) => value.proposal_id === objective.proposal_id);
        const stateLabel = control ? control.state : "ACTIVE";
        const label = source.objective_text || objective.proposal_id;
        return stateLabel + " — " + label;
      }
    );
    updateLifecycleWarning();
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

  function populateSelect(select, values, valueOf, labelOf) {
    const previous = select.value;
    clear(select);
    for (const value of values) {
      const option = document.createElement("option");
      option.value = valueOf(value);
      option.textContent = labelOf(value);
      select.append(option);
    }
    if (values.some((value) => valueOf(value) === previous)) select.value = previous;
  }

  function currentPriorReview(gateId) {
    if (!latestState) return null;
    return [...(latestState.human_reviews || [])]
      .reverse()
      .find((review) => review.gate_id === gateId) || null;
  }

  function updateReviewContext() {
    if (!latestState) return;
    const gateId = $("review-gate").value;
    const artifactId = $("review-artifact").value;
    const gate = (latestState.human_gates || []).find((value) => value.gate_id === gateId);
    const artifact = (latestState.artifacts || []).find((value) => value.artifact_id === artifactId);
    const prior = currentPriorReview(gateId);
    const node = $("review-context");
    clear(node);
    if (!gate || !artifact) {
      node.append(el("span", "Select an exact gate and registered artifact.", "muted"));
      return;
    }
    node.append(kv([
      ["Gate", gate.gate_id],
      ["Gate message", gate.message],
      ["Blocked reason", gate.blocked_reason],
      ["Artifact", artifact.artifact_id],
      ["Artifact kind", artifact.kind],
      ["Source SHA", artifact.source_sha],
      ["Prior review", prior ? prior.review_id : "none"],
      ["Prior verdict", prior ? prior.verdict : "none"],
      ["Prior material delta", prior ? prior.material_delta : "none"],
    ]));
  }

  function renderReviewControl(state) {
    const panel = $("review-control");
    const gates = state.human_gates || [];
    const artifacts = state.artifacts || [];
    const enabled = Boolean((state.runtime || {}).development_write_api_enabled);
    panel.hidden = !(enabled && gates.length && artifacts.length);
    if (panel.hidden) return;

    populateSelect(
      $("review-gate"),
      gates,
      (gate) => gate.gate_id,
      (gate) => gate.gate_id + (gate.lane ? " — " + gate.lane : "")
    );
    populateSelect(
      $("review-artifact"),
      artifacts,
      (artifact) => artifact.artifact_id,
      (artifact) => artifact.artifact_id + " — " + artifact.title
    );
    updateReviewContext();
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
    latestState = state;
    $("console-content").hidden = false;
    $("snapshot-id").textContent = `snapshot ${state.snapshot_digest || "—"}`;
    renderSummary(state);
    renderScorecard(state);
    renderRoadmap(state);
    renderObjectives(state);
    renderObjectiveControl(state);
    renderObjectiveLifecycleControl(state);
    renderWorkers(state);
    renderExecution(state);
    renderReviews(state);
    renderReviewControl(state);
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

  function nextObjectiveRequestId() {
    if (globalThis.crypto && typeof globalThis.crypto.randomUUID === "function") {
      return globalThis.crypto.randomUUID();
    }
    return "objective-" + Date.now() + "-" + Math.random().toString(16).slice(2);
  }

  async function submitObjective(event) {
    event.preventDefault();
    const statusNode = $("objective-status");
    statusNode.className = "small";
    statusNode.textContent = "";

    if (!writeToken) {
      statusNode.textContent = "Enter the distinct write bearer token and reconnect first.";
      statusNode.className = "small error";
      return;
    }
    const objective = $("objective-text").value.trim();
    if (!objective) {
      statusNode.textContent = "Objective text is required.";
      statusNode.className = "small error";
      return;
    }
    if (!pendingObjectiveRequestId || pendingObjectiveText !== objective) {
      pendingObjectiveRequestId = nextObjectiveRequestId();
      pendingObjectiveText = objective;
    }

    $("submit-objective").disabled = true;
    try {
      const response = await writeApi("/api/v1/objectives", {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({
          request_id: pendingObjectiveRequestId,
          objective,
        }),
      });
      const result = await response.json();
      if (!response.ok) {
        throw new Error(result.error || "Objective submission failed (" + response.status + ")");
      }
      statusNode.textContent =
        "Durable objective proposal reconciled: "
        + result.objective_disposition
        + " / "
        + result.proposal_id;
      statusNode.className = "small";
      pendingObjectiveRequestId = "";
      pendingObjectiveText = "";
      $("objective-text").value = "";
      lastDigest = "";
      await refresh();
    } catch (error) {
      statusNode.textContent = error.message;
      statusNode.className = "small error";
    } finally {
      $("submit-objective").disabled = false;
    }
  }

  function nextLifecycleRequestId() {
    if (globalThis.crypto && typeof globalThis.crypto.randomUUID === "function") {
      return globalThis.crypto.randomUUID();
    }
    return "objective-control-" + Date.now() + "-" + Math.random().toString(16).slice(2);
  }

  async function submitObjectiveLifecycle(event) {
    event.preventDefault();
    const statusNode = $("objective-lifecycle-status");
    statusNode.className = "small";
    statusNode.textContent = "";
    if (!writeToken || !latestState) {
      statusNode.textContent = "A current snapshot and write bearer token are required.";
      statusNode.className = "small error";
      return;
    }

    const proposalId = $("objective-lifecycle-id").value;
    const operation = $("objective-lifecycle-operation").value;
    const reason = $("objective-lifecycle-reason").value.trim();
    if (!proposalId || !operation || !reason) {
      statusNode.textContent = "Exact objective, operation, and reason are required.";
      statusNode.className = "small error";
      return;
    }
    if (operation === "CANCEL" && !$("objective-cancel-confirm").checked) {
      statusNode.textContent = "Explicitly confirm the terminal CANCEL fence.";
      statusNode.className = "small error";
      return;
    }

    const signature = JSON.stringify({proposal_id: proposalId, operation, reason});
    if (!pendingLifecycleRequestId || pendingLifecycleSignature !== signature) {
      pendingLifecycleRequestId = nextLifecycleRequestId();
      pendingLifecycleSignature = signature;
    }

    $("submit-objective-lifecycle").disabled = true;
    try {
      const response = await writeApi("/api/v1/objective-controls", {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({
          request_id: pendingLifecycleRequestId,
          proposal_id: proposalId,
          operation,
          reason,
        }),
      });
      const result = await response.json();
      if (!response.ok) {
        throw new Error(result.error || "Objective lifecycle command failed (" + response.status + ")");
      }
      statusNode.textContent =
        result.operation + " reconciled for " + result.proposal_id + "; state=" + result.state + ".";
      pendingLifecycleRequestId = "";
      pendingLifecycleSignature = "";
      $("objective-lifecycle-reason").value = "";
      $("objective-cancel-confirm").checked = false;
      lastDigest = "";
      await refresh();
    } catch (error) {
      statusNode.textContent = error.message;
      statusNode.className = "small error";
    } finally {
      $("submit-objective-lifecycle").disabled = false;
    }
  }

  function nextReviewRequestId() {
    if (globalThis.crypto && typeof globalThis.crypto.randomUUID === "function") {
      return globalThis.crypto.randomUUID();
    }
    return "review-" + Date.now() + "-" + Math.random().toString(16).slice(2);
  }

  async function submitHumanReview(event) {
    event.preventDefault();
    const statusNode = $("review-status");
    statusNode.className = "small";
    statusNode.textContent = "";

    if (!latestState) {
      statusNode.textContent = "No current development snapshot is loaded.";
      statusNode.className = "small error";
      return;
    }
    if (!writeToken) {
      statusNode.textContent = "Enter the distinct write bearer token and reconnect first.";
      statusNode.className = "small error";
      return;
    }

    const gateId = $("review-gate").value;
    const artifactId = $("review-artifact").value;
    const verdict = $("review-verdict").value;
    const gate = (latestState.human_gates || []).find((value) => value.gate_id === gateId);
    const artifact = (latestState.artifacts || []).find((value) => value.artifact_id === artifactId);
    if (!gate || !artifact || !verdict) {
      statusNode.textContent = "Select an exact current gate, registered artifact, and verdict.";
      statusNode.className = "small error";
      return;
    }

    const findings = textLines($("review-findings").value);
    const positiveFindings = textLines($("review-positive").value);
    const materialDelta = $("review-delta").value.trim();
    const nextBoundary = $("review-next-boundary").value.trim();
    if (!findings.length || !materialDelta || !nextBoundary) {
      statusNode.textContent = "Findings, material delta, and next boundary are required.";
      statusNode.className = "small error";
      return;
    }

    const prior = currentPriorReview(gateId);
    if (!pendingReviewRequestId) pendingReviewRequestId = nextReviewRequestId();
    const body = {
      request_id: pendingReviewRequestId,
      gate_id: gateId,
      artifact_id: artifactId,
      source_sha: artifact.source_sha,
      verdict,
      findings,
      positive_findings: positiveFindings,
      material_delta: materialDelta,
      next_boundary: nextBoundary,
      deferred_product_work: $("review-deferred").checked,
      prior_review_id: prior ? prior.review_id : null,
    };

    $("submit-review").disabled = true;
    try {
      const response = await writeApi("/api/v1/human-reviews", {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify(body),
      });
      const result = await response.json();
      if (!response.ok) {
        throw new Error(result.error || "Human review submission failed (" + response.status + ")");
      }
      statusNode.textContent =
        "Durable " + result.verdict + " review reconciled: " + result.review_id;
      statusNode.className = "small";
      pendingReviewRequestId = "";
      $("review-verdict").value = "";
      $("review-positive").value = "";
      $("review-findings").value = "";
      $("review-delta").value = "";
      $("review-next-boundary").value = "";
      $("review-deferred").checked = false;
      lastDigest = "";
      await refresh();
    } catch (error) {
      statusNode.textContent = error.message;
      statusNode.className = "small error";
    } finally {
      $("submit-review").disabled = false;
    }
  }

  $("objective-form").addEventListener("submit", submitObjective);
  $("objective-lifecycle-operation").addEventListener("change", updateLifecycleWarning);
  $("objective-lifecycle-form").addEventListener("submit", submitObjectiveLifecycle);
  $("review-gate").addEventListener("change", updateReviewContext);
  $("review-artifact").addEventListener("change", updateReviewContext);
  $("review-form").addEventListener("submit", submitHumanReview);

  $("auth-form").addEventListener("submit", (event) => {
    event.preventDefault();
    token = $("api-token").value.trim();
    writeToken = $("write-token").value.trim();
    if (!token) return;
    sessionStorage.setItem(TOKEN_KEY, token);
    if (writeToken) sessionStorage.setItem(WRITE_TOKEN_KEY, writeToken);
    else sessionStorage.removeItem(WRITE_TOKEN_KEY);
    lastDigest = "";
    refresh();
    schedulePolling();
  });

  $("forget-token").addEventListener("click", () => {
    token = "";
    writeToken = "";
    sessionStorage.removeItem(TOKEN_KEY);
    sessionStorage.removeItem(WRITE_TOKEN_KEY);
    $("api-token").value = "";
    $("write-token").value = "";
    $("console-content").hidden = true;
    latestState = null;
    pendingObjectiveRequestId = "";
    pendingObjectiveText = "";
    pendingReviewRequestId = "";
    pendingLifecycleRequestId = "";
    pendingLifecycleSignature = "";
    lastDigest = "";
    if (pollHandle) clearInterval(pollHandle);
    pollHandle = null;
    setConnection("Disconnected", "muted");
  });

  if (token) {
    $("api-token").value = token;
    $("write-token").value = writeToken;
    refresh();
    schedulePolling();
  }
})();
