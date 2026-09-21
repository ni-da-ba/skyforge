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

  function card(label, value, severity = "", subvalue = "") {
    const node = el("div", null, `summary-card ${severity}`.trim());
    node.append(el("div", label, "label"), el("div", value ?? "—", "value health-value"));
    if (subvalue) node.append(el("div", subvalue, "subvalue"));
    return node;
  }

  const FRIENDLY_STATUS = {
    ok: "Healthy",
    IDLE: "Idle",
    RUNNING: "Running",
    STOPPED: "Stopped",
    ACTIVE: "Active",
    PAUSED: "Paused",
    CANCELLED: "Cancelled",
    COMPLETED: "Completed",
    CLEANED: "Cleaned up",
    HANDOFF_READY: "Ready for handoff",
    EXECUTING: "Running",
    RUNNABLE: "Ready",
    WAIT_LIMIT: "Waiting for capacity",
    WAIT_CLAIM: "Waiting for ownership",
    WAIT_QUOTA: "Waiting for quota",
    RECOVERY_REQUIRED: "Recovery required",
    FAILED: "Failed",
    INTERRUPTED: "Interrupted",
    ACCEPTED: "Accepted",
    CHANGES_REQUIRED: "Changes required",
    STALE_BASE: "Safely retired — main advanced",
    STALE_MANAGED_BASE: "Stale PR preserved safely",
    BLOCKED: "Blocked",
    RECLASSIFY: "Reclassified",
    NOT_DISPATCH: "Not dispatched",
    MANAGED_PR_ADVANCED: "PR advanced",
    WAIT_CI: "Waiting for CI",
  };

  function friendlyStatus(value) {
    const raw = String(value || "").trim();
    if (!raw) return "—";
    if (FRIENDLY_STATUS[raw]) return FRIENDLY_STATUS[raw];
    return raw.toLowerCase().replaceAll("_", " ").replace(/\b\w/g, (c) => c.toUpperCase());
  }

  function severityFor(value) {
    const raw = String(value || "").toUpperCase();
    if (["FAILED", "INTERRUPTED", "RECOVERY_REQUIRED", "STOPPED"].includes(raw)) return "bad";
    if (["BLOCKED", "CHANGES_REQUIRED", "WAIT_LIMIT", "WAIT_CLAIM", "WAIT_QUOTA", "WAIT_CI"].includes(raw)) return "warn";
    if (["OK", "HEALTHY", "ACCEPTED", "COMPLETED", "CLEANED", "IDLE", "RUNNING"].includes(raw)) return "good";
    return "";
  }

  function shortSha(value) {
    const text = String(value || "");
    return /^[0-9a-f]{40}$/.test(text) ? text.slice(0, 10) + "…" : (text || "—");
  }

  function truncate(value, limit = 150) {
    const text = String(value || "").trim();
    return text.length > limit ? text.slice(0, limit - 1) + "…" : text;
  }

  function technicalDetails(entries, title = "Technical details") {
    const details = el("details", null, "technical-details");
    details.append(el("summary", title));
    details.append(kv(entries));
    return details;
  }

  function emptyState(text) {
    return el("div", text, "empty-state");
  }

  function latestReview(state) {
    const values = state.human_reviews || [];
    return values.length ? values[values.length - 1] : null;
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
    const score = state.scorecard || {};
    const scheduler = score.scheduler || {};
    const blockers = runtime.production_execution_gate_blockers || [];
    const healthy = runtime.status === "ok"
      && driver.running === true
      && blockers.length === 0
      && Number(scheduler.recovery_required || 0) === 0;

    const executing = Number(scheduler.executing || 0);
    const waiting = Number(scheduler.waiting || 0);
    const activePlan = Boolean((state.execution || {}).active_plan);
    let activity = "Idle";
    let activityDetail = "No worker is currently executing.";
    if (executing > 0) {
      activity = executing === 1 ? "1 worker running" : executing + " workers running";
      activityDetail = "Automation is actively executing work.";
    } else if (activePlan) {
      activity = "Preparing work";
      activityDetail = "A task is admitted or being classified.";
    } else if (waiting > 0) {
      activity = "Waiting";
      activityDetail = waiting + " worker" + (waiting === 1 ? "" : "s") + " waiting for a safe execution condition.";
    }

    const review = latestReview(state);
    let product = "No review recorded";
    let productSeverity = "";
    let productDetail = "No human review history is available.";
    if (review) {
      product = friendlyStatus(review.verdict);
      productSeverity = severityFor(review.verdict);
      productDetail = review.next_boundary || "See Product / human review.";
    } else if ((state.human_gates || []).length) {
      product = "Review needed";
      productSeverity = "warn";
      productDetail = "A human gate is awaiting judgment.";
    }

    grid.append(
      card("System", healthy ? "Healthy" : "Needs attention", healthy ? "good" : "warn",
        healthy ? "Platform-v2 is running normally." : "See Needs attention below."),
      card("Current activity", activity, executing > 0 ? "good" : "", activityDetail),
      card("Product state", product, productSeverity, truncate(productDetail, 100)),
      card("Checkout", shortSha(state.checkout_head_sha), "", "Exact SHA is available in Technical details.")
    );
  }

  function renderAttention(state) {
    const panel = $("attention-panel");
    const node = $("attention");
    const count = $("attention-count");
    clear(node);
    const items = [];
    const runtime = state.runtime || {};
    const driver = runtime.production_execution_driver || {};
    const score = state.scorecard || {};
    const scheduler = score.scheduler || {};
    const blockers = runtime.production_execution_gate_blockers || [];

    if (runtime.status !== "ok") {
      items.push(["Controller health", "The Platform-v2 runtime is not reporting a healthy status.", "bad"]);
    }
    if (driver.running !== true) {
      items.push(["Automation driver stopped", "Production execution is enabled but the driver is not running.", "bad"]);
    }
    for (const blocker of blockers) {
      items.push(["Execution blocked", String(blocker), "warn"]);
    }
    if (Number(scheduler.recovery_required || 0) > 0) {
      items.push(["Worker recovery required", scheduler.recovery_required + " scheduler record(s) require recovery.", "bad"]);
    }

    const review = latestReview(state);
    if (review && review.verdict === "CHANGES_REQUIRED") {
      items.push([
        "Product review requires changes",
        review.next_boundary || "A human-reviewed product issue remains unresolved.",
        "warn",
      ]);
    } else if (!review && (state.human_gates || []).length) {
      const gate = state.human_gates[state.human_gates.length - 1] || {};
      items.push(["Human review needed", gate.message || gate.blocked_reason || gate.gate_id || "A product gate awaits review.", "warn"]);
    }

    count.textContent = items.length ? items.length + " item" + (items.length === 1 ? "" : "s") : "Clear";
    count.className = "pill " + (items.some((item) => item[2] === "bad") ? "bad" : items.length ? "warn" : "good");
    panel.classList.toggle("clear", items.length === 0);
    panel.classList.toggle("needs-attention", items.length > 0);

    if (!items.length) {
      node.append(emptyState("Nothing needs operator attention. Automation is nominal and no unresolved human action is reported."));
      return;
    }
    for (const [title, detail, severity] of items) {
      const item = el("div", null, "attention-item");
      item.append(pill(severity === "bad" ? "!" : "•", severity));
      const body = el("div");
      body.append(el("h3", title), el("div", detail, "small"));
      item.append(body);
      node.append(item);
    }
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

    const metrics = el("div", null, "metric-grid");
    const metric = (label, value) => {
      const box = el("div", null, "metric");
      box.append(el("div", label, "label"), el("div", value ?? "—", "value"));
      return box;
    };
    metrics.append(
      metric("Objectives recorded", objectives.total),
      metric("Workers recorded", workers.total),
      metric("Completions", (score.completions || {}).total),
      metric("Scheduler now", (scheduler.executing || 0) + " running · " + (scheduler.waiting || 0) + " waiting"),
      metric("Human reviews", (reviews.accepted || 0) + " accepted · " + (reviews.changes_required || 0) + " changes required"),
      metric("External claims", claims.active ?? 0),
      metric("Local Codex use", localBudget.available
        ? (localUsage.classifier_calls ?? 0) + " classifier · " + (localUsage.luna_worker_calls ?? 0) + " Luna · " + (localUsage.terra_worker_calls ?? 0) + " Terra"
        : "Unavailable"),
      metric("Provider quota telemetry", providerBudget.available ? "Available" : "Unavailable")
    );
    node.append(metrics);

    const telemetry = el("details", null, "technical-details");
    telemetry.append(el("summary", "Hosted telemetry and raw diagnostic values"));
    const raw = {
      hosted_value: hosted,
      provider_budget: providerBudget,
      slo_note: score.slo_note || null,
    };
    telemetry.append(el("pre", JSON.stringify(raw, null, 2), "raw-json"));
    node.append(telemetry);
  }

  function renderRoadmap(state) {
    const roadmap = state.roadmap || {};
    const active = roadmap.active || {};
    const blocked = roadmap.blocked_nodes || {};
    const node = $("roadmap");
    clear(node);

    const activeLabel = active.node_id || active.issue_number;
    const banner = el("div", null, "state-banner");
    const body = el("div");
    body.append(
      el("div", activeLabel ? "Development node active" : "No roadmap task is currently active", "headline"),
      el("div", activeLabel ? String(activeLabel) : "Skyforge is not presently executing a roadmap node.", "explanation")
    );
    banner.append(body, pill(activeLabel ? "Active" : "Idle", activeLabel ? "good" : ""));
    node.append(banner);

    if (Object.keys(blocked).length) {
      node.append(technicalDetails([
        ["Roadmap", roadmap.roadmap_id],
        ["Blocked nodes", Object.keys(blocked).length],
        ["Completed nodes", Object.keys(roadmap.completed_runs || {}).length],
        ["Claims today", roadmap.claims_today],
      ], "Roadmap bookkeeping"));
    } else {
      node.append(technicalDetails([
        ["Roadmap", roadmap.roadmap_id],
        ["Completed nodes", Object.keys(roadmap.completed_runs || {}).length],
        ["Claims today", roadmap.claims_today],
      ], "Roadmap bookkeeping"));
    }
  }

  function renderObjectives(state) {
    const objectives = state.objectives || [];
    $("objective-count").textContent = objectives.length ? "(" + objectives.length + ")" : "";
    const controls = state.objective_controls || [];
    const scopes = state.objective_scopes || [];
    renderList($("objectives"), [...objectives].reverse(), (objective) => {
      const node = el("article", null, "item compact");
      const source = objective.source || {};
      const control = controls.find((value) => value.proposal_id === objective.proposal_id);
      const scope = scopes.find((value) => value.parent_proposal_id === objective.proposal_id);
      const lifecycle = control ? control.state : "ACTIVE";
      const title = truncate(source.objective_text || objective.proposal_id || "Objective", 180);
      const heading = el("div", null, "primary-line");
      heading.append(el("h3", title), pill(friendlyStatus(lifecycle), severityFor(lifecycle)));
      node.append(heading);
      const issue = (scope && scope.issue_number) || source.issue_number;
      const lane = (scope && scope.lane) || (objective.candidate_task && objective.candidate_task.lane) || "";
      node.append(el("div",
        [lane, issue ? "#" + issue : "", friendlyStatus(objective.disposition)].filter(Boolean).join(" · "),
        "secondary-line"
      ));
      node.append(technicalDetails([
        ["Proposal", objective.proposal_id],
        ["Disposition", objective.disposition],
        ["Scoping", scope ? scope.status : "not started"],
        ["Scope reason", scope ? scope.reason : "—"],
        ["Lifecycle", lifecycle],
        ["Last control", control ? control.last_operation : "none"],
        ["Reason", objective.reason],
        ["Actor", source.actor],
        ["Full objective", source.objective_text],
      ]));
      return node;
    }, "No objective history recorded.");
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
        return friendlyStatus(stateLabel) + " — " + truncate(label, 120);
      }
    );
    updateLifecycleWarning();
  }

  function renderWorkers(state) {
    const execution = state.execution || {};
    const workers = execution.workers || [];
    const activeStates = new Set(["RUNNING", "EXECUTING", "RUNNABLE", "WAIT_LIMIT", "WAIT_CLAIM", "WAIT_QUOTA", "RECOVERY_REQUIRED"]);
    const active = workers.filter((worker) => activeStates.has(String(worker.status || "").toUpperCase()));
    $("worker-count").textContent = active.length + " active";

    renderList($("workers"), active, (worker) => {
      const node = el("article", null, "item compact");
      const heading = el("div", null, "primary-line");
      heading.append(el("h3", truncate(worker.objective || worker.task_id || "Worker", 140)),
        pill(friendlyStatus(worker.status), severityFor(worker.status)));
      node.append(heading);
      node.append(el("div", [worker.lane, worker.model].filter(Boolean).join(" · "), "secondary-line"));
      if (worker.summary) node.append(el("div", truncate(worker.summary, 220), "small"));
      node.append(technicalDetails([
        ["Task", worker.task_id],
        ["Attempt", worker.attempt_id],
        ["Branch", worker.branch],
        ["Base", worker.base_sha],
        ["Failure kind", worker.failure_kind || "none"],
      ]));
      return node;
    }, "No worker is currently running.");

    renderList($("worker-history"), [...workers].reverse(), (worker) => {
      const node = el("article", null, "item compact");
      const heading = el("div", null, "primary-line");
      heading.append(el("h3", truncate(worker.objective || worker.task_id || "Worker", 120)),
        pill(friendlyStatus(worker.status), severityFor(worker.status)));
      node.append(heading);
      node.append(el("div", [worker.lane, worker.model, worker.summary && truncate(worker.summary, 120)].filter(Boolean).join(" · "), "secondary-line"));
      node.append(technicalDetails([
        ["Task", worker.task_id],
        ["Attempt", worker.attempt_id],
        ["Branch", worker.branch],
        ["Failure", worker.failure_kind || "none"],
      ]));
      return node;
    }, "No worker history recorded.");
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

    let headline = "Idle";
    let explanation = "No task is currently executing.";
    let severity = "good";
    if (driver.running !== true) {
      headline = "Driver stopped";
      explanation = "Automation is not currently running.";
      severity = "bad";
    } else if (blockers.length) {
      headline = "Blocked";
      explanation = blockers[0];
      severity = "warn";
    } else if (plan.plan_id) {
      headline = "Preparing or executing work";
      explanation = plan.issue_number ? "Issue #" + plan.issue_number : friendlyStatus(plan.status);
      severity = "good";
    }

    const banner = el("div", null, "state-banner");
    const body = el("div");
    body.append(el("div", headline, "headline"), el("div", explanation, "explanation"));
    banner.append(body, pill(friendlyStatus(driver.last_disposition || (driver.running ? "RUNNING" : "STOPPED")), severity));
    node.append(banner);
    node.append(technicalDetails([
      ["Plan", plan.plan_id || "none"],
      ["Plan status", plan.status || "—"],
      ["Admission", admission.outcome || "none"],
      ["Attempt", admission.attempt_id || "—"],
      ["Driver disposition", driver.last_disposition || "—"],
      ["Gate digest", runtime.production_execution_gate_digest || "—"],
      ["Gate blockers", blockers.length ? blockers.join("; ") : "none"],
    ]));
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

  function renderProductReview(state) {
    const node = $("product-review-summary");
    clear(node);
    const reviews = state.human_reviews || [];
    const review = latestReview(state);
    const gates = state.human_gates || [];
    $("review-count").textContent = reviews.length ? reviews.length + " recorded" : "";

    if (!review) {
      if (!gates.length) {
        node.append(emptyState("No current human review action is recorded."));
        return;
      }
      const gate = gates[gates.length - 1] || {};
      const banner = el("div", null, "state-banner");
      const body = el("div");
      body.append(el("div", "Review needed", "headline"),
        el("div", gate.message || gate.blocked_reason || "A product gate is waiting for human judgment.", "explanation"));
      banner.append(body, pill("Needs review", "warn"));
      node.append(banner);
      node.append(technicalDetails([["Gate", gate.gate_id], ["Lane", gate.lane], ["Blocked reason", gate.blocked_reason]]));
      return;
    }

    const banner = el("div", null, "state-banner");
    const body = el("div");
    body.append(
      el("div", friendlyStatus(review.verdict), "headline"),
      el("div", review.next_boundary || "No next boundary recorded.", "explanation")
    );
    banner.append(body, pill(friendlyStatus(review.verdict), severityFor(review.verdict)));
    node.append(banner);

    if ((review.positive_findings || []).length) {
      node.append(el("h3", "What looked good"));
      const list = el("ul", null, "clean");
      for (const finding of review.positive_findings) list.append(el("li", finding));
      node.append(list);
    }
    if ((review.findings || []).length) {
      node.append(el("h3", review.verdict === "CHANGES_REQUIRED" ? "What still needs work" : "Review notes"));
      const list = el("ul", null, "clean");
      for (const finding of review.findings) list.append(el("li", finding));
      node.append(list);
    }
    node.append(technicalDetails([
      ["Gate", review.gate_id],
      ["Artifact", review.artifact_id],
      ["Source SHA", review.source_sha],
      ["Material delta", review.material_delta],
      ["Deferred", review.deferred_product_work ? "yes" : "no"],
      ["Review ID", review.review_id],
    ]));
    if (review.artifact) node.append(renderArtifact(review.artifact));
  }

  function renderReviews(state) {
    const reviews = state.human_reviews || [];
    renderList($("reviews"), [...reviews].reverse(), (review) => {
      const node = el("article", null, "item compact");
      const heading = el("div", null, "primary-line");
      heading.append(el("h3", review.gate_id || "Human review"), pill(friendlyStatus(review.verdict), severityFor(review.verdict)));
      node.append(heading);
      node.append(el("div", truncate(review.next_boundary || review.material_delta || "", 180), "secondary-line"));
      node.append(technicalDetails([
        ["Artifact", review.artifact_id],
        ["Source SHA", review.source_sha],
        ["Material delta", review.material_delta],
        ["Next boundary", review.next_boundary],
        ["Deferred", review.deferred_product_work ? "yes" : "no"],
        ["Review ID", review.review_id],
      ]));
      return node;
    }, "No human review history recorded.");
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
    renderList($("claims"), [...(state.external_claims || [])].reverse(), (claim) => {
      const node = el("article", null, "item compact");
      const heading = el("div", null, "primary-line");
      heading.append(el("h3", claim.issue_number ? "Issue #" + claim.issue_number : "External work"),
        pill(friendlyStatus(claim.state), severityFor(claim.state)));
      node.append(heading);
      node.append(el("div", [claim.lane, claim.claimed_by].filter(Boolean).join(" · "), "secondary-line"));
      node.append(technicalDetails([
        ["Branch", claim.branch],
        ["PR", claim.pr_number],
        ["Owner", claim.claimed_by],
        ["Raw state", claim.state],
      ]));
      return node;
    }, "No external ownership claims.");
  }

  function renderCompletions(state) {
    const execution = state.execution || {};
    renderList($("completions"), [...(execution.completions || [])].reverse(), (completion) => {
      const node = el("article", null, "item compact");
      const heading = el("div", null, "primary-line");
      heading.append(
        el("h3", completion.issue_number ? "Issue #" + completion.issue_number : "Completed work"),
        pill(friendlyStatus(completion.outcome || completion.status), severityFor(completion.outcome || completion.status))
      );
      node.append(heading);
      const outcome = completion.outcome || completion.status;
      let explanation = "";
      if (outcome === "STALE_MANAGED_BASE") explanation = "A stale managed PR was preserved without rebasing or merging.";
      else if (outcome === "STALE_BASE") explanation = "The task was safely retired because main advanced before handoff.";
      else if (outcome) explanation = friendlyStatus(outcome);
      if (explanation) node.append(el("div", explanation, "secondary-line"));
      node.append(technicalDetails([
        ["Status", completion.status],
        ["Outcome", completion.outcome || "—"],
        ["Attempt", completion.attempt_id],
        ["Worker", completion.worker_run_id],
        ["Completion", completion.completion_id],
      ]));
      return node;
    }, "No completion records.");
  }

  function render(state) {
    latestState = state;
    $("console-content").hidden = false;
    $("snapshot-id").textContent = state.snapshot_digest || "—";
    renderSummary(state);
    renderAttention(state);
    renderExecution(state);
    renderWorkers(state);
    renderProductReview(state);
    renderRoadmap(state);
    renderObjectives(state);
    renderObjectiveControl(state);
    renderObjectiveLifecycleControl(state);
    renderReviews(state);
    renderReviewControl(state);
    renderClaims(state);
    renderCompletions(state);
    renderScorecard(state);
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
        "Objective recorded for scoping. Current state: " + friendlyStatus(result.objective_disposition) + ".";
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
        friendlyStatus(result.operation) + " applied. Current state: " + friendlyStatus(result.state) + ".";
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
        "Review recorded: " + friendlyStatus(result.verdict) + ".";
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
