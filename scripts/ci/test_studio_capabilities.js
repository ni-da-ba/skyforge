"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const capabilities = require("../orchestrator/studio/studio-capabilities.js");

const desktop = capabilities.fromProtocol("skyforge:");
assert.deepEqual(desktop, {
  mode: "desktop-preview",
  canConnectRegisteredArtifacts: false,
  canGenerateWorlds: false,
});

const web = capabilities.fromProtocol("https:");
assert.deepEqual(web, {
  mode: "web",
  canConnectRegisteredArtifacts: true,
  canGenerateWorlds: false,
});

const studioRoot = path.join(__dirname, "..", "orchestrator", "studio");
const html = fs.readFileSync(path.join(studioRoot, "index.html"), "utf8");
const app = fs.readFileSync(path.join(studioRoot, "app.js"), "utf8");
assert.ok(html.indexOf('src="studio-capabilities.js"') < html.indexOf('src="app.js"'),
  "capabilities load before Studio startup");
assert.match(html, /id="auth-panel"/, "web connection panel remains available");
assert.match(html, /id="desktop-local-status"[^>]*hidden/, "desktop status starts hidden for web mode");
assert.match(html, /Connected artifact browsing and world generation are not configured/,
  "desktop capability limits are explicit");
assert.match(app, /capabilities\.fromProtocol\(window\.location\.protocol\)/,
  "Studio detects runtime mode from its origin");
assert.match(app, /\$\("auth-panel"\)\.hidden = true/);
assert.match(app, /\$\("desktop-local-status"\)\.hidden = false/);
assert.match(app, /configureRuntimeCapabilities\(\);/,
  "capabilities are applied after the shared local-mode setup");

console.log("PASS Studio desktop and web capability states");
