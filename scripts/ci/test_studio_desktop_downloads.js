"use strict";

const assert = require("node:assert/strict");
const path = require("node:path");
const {
  registerDownloadSaveDialogs,
  safeSuggestedFilename,
} = require("../orchestrator/studio-desktop/downloads.cjs");

assert.equal(safeSuggestedFilename("terrain-report.json"), "terrain-report.json");
assert.equal(safeSuggestedFilename("../reports/terrain:report?.json"), "terrain_report_.json");
assert.equal(safeSuggestedFilename(""), "studio-export");
assert.equal(safeSuggestedFilename("x".repeat(300)).length, 180);

let listenerName = null;
let listener = null;
let removedListener = null;
const session = {
  on(name, handler) {
    listenerName = name;
    listener = handler;
  },
  removeListener(name, handler) {
    removedListener = [name, handler];
  },
};
const mainWindow = { webContents: { id: "studio-window" } };
let requestedPath = null;
const app = {
  getPath(name) {
    assert.equal(name, "downloads");
    return "/user/Downloads";
  },
};
const dispose = registerDownloadSaveDialogs(session, () => mainWindow, app, path);

assert.equal(listenerName, "will-download");
assert.equal(typeof listener, "function");

const item = {
  getFilename: () => "../exports/world brief.json",
  setSaveDialogOptions(options) {
    requestedPath = options;
  },
};

listener({}, item, mainWindow.webContents);
assert.deepEqual(requestedPath, {
  title: "Save a Skyforge Studio export",
  buttonLabel: "Save",
  defaultPath: path.join("/user/Downloads", "world brief.json"),
});

requestedPath = null;
listener({}, item, { id: "other-window" });
assert.equal(requestedPath, null, "downloads from other windows do not receive Studio save options");

requestedPath = null;
listener({}, item, null);
assert.equal(requestedPath, null, "downloads without the Studio window do not receive save options");

dispose();
assert.deepEqual(removedListener, ["will-download", listener]);

console.log("PASS Studio desktop native-save-dialog contract");
