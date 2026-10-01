"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const studioRoot = path.join(__dirname, "..", "orchestrator", "studio");
const html = fs.readFileSync(path.join(studioRoot, "index.html"), "utf8");
const app = fs.readFileSync(path.join(studioRoot, "app.js"), "utf8");
const css = fs.readFileSync(path.join(studioRoot, "styles.css"), "utf8");

assert.match(html, /data-workspace-view="home"[^>]*aria-pressed="true"/,
  "Home is the selected entry point");
assert.match(html, /id="studio-home-view"[^>]*aria-labelledby="studio-home-heading"/,
  "Home has an accessible heading");
assert.match(app, /let studioWorkspaceView = "home"/,
  "Studio starts in Home");
assert.match(app, /studio-home-view"\)\.hidden = studioWorkspaceView !== "home"/,
  "navigation visibility includes Home");
assert.match(app, /view !== "home"/,
  "Home is an allowed workspace");
assert.match(app, /querySelectorAll\("\[data-open-workspace\]"\)/,
  "Home actions use the existing workspace navigation");
assert.match(css, /\.studio-home-view\[hidden\]\{display:none\}/,
  "hidden Home view stays hidden");

const actions = [...html.matchAll(/data-open-workspace="([^"]+)"/g)].map(match => match[1]);
assert.deepEqual(actions, ["brief", "inspect", "terrain-comparison", "regional-comparison"]);
for (const view of actions) {
  assert.match(app, new RegExp('studioWorkspaceView !== "' + view + '"'),
    "Home routes to existing workspace: " + view);
}
assert.match(html, /A world generator is not connected yet/,
  "Home states the current backend boundary");
assert.match(html, /does not generate or evaluate a world/,
  "Home explains the limits of a saved brief");

console.log("PASS Studio Home navigation contract");
