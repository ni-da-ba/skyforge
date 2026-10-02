"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const {
  buildStudioApplicationMenu,
  createStudioActionDispatcher,
} = require("../orchestrator/studio-desktop/menu.cjs");

const studioRoot = path.join(__dirname, "..", "orchestrator", "studio");
const desktopRoot = path.join(__dirname, "..", "orchestrator", "studio-desktop");
const studioHtml = fs.readFileSync(path.join(studioRoot, "index.html"), "utf8");
const desktopMain = fs.readFileSync(path.join(desktopRoot, "main.cjs"), "utf8");
const desktopWorkflow = fs.readFileSync(
  path.join(__dirname, "..", "..", ".github", "workflows", "studio-desktop.yml"),
  "utf8",
);
assert.match(desktopMain, /require\("\.\/menu\.cjs"\)/);
assert.match(desktopMain, /Menu\.setApplicationMenu\(/);
assert.match(desktopMain, /configureApplicationMenu\(\);/);
assert.match(desktopWorkflow, /cp scripts\/orchestrator\/studio-desktop\/menu\.cjs/);
for (const controlId of [
  "local-file",
  "local-pair-files",
  "inspection-workspace-file",
  "sample-trace-file",
  "studio-backup-import",
  "studio-backup-export",
]) {
  assert.ok(studioHtml.includes(`id="${controlId}"`), `File menu control exists: ${controlId}`);
}

const builtTemplates = [];
const Menu = {
  buildFromTemplate(template) {
    builtTemplates.push(template);
    return { template };
  },
};
const actions = [];
const menu = buildStudioApplicationMenu(Menu, action => actions.push(action), "win32");
const fileMenu = menu.template.find(item => item.label === "File");
assert.ok(fileMenu, "desktop menu has a File section");
const actionItems = fileMenu.submenu.filter(item => typeof item.click === "function");
assert.deepEqual(actionItems.map(item => ({ label: item.label, accelerator: item.accelerator || null })), [
  { label: "Open local semantic JSON…", accelerator: "CmdOrCtrl+O" },
  { label: "Open terrain + hydrology pair…", accelerator: null },
  { label: "Open inspection workspace…", accelerator: null },
  { label: "Open sample trace…", accelerator: null },
  { label: "Open Studio backup…", accelerator: null },
  { label: "Save Studio backup…", accelerator: "CmdOrCtrl+Shift+S" },
]);
for (const item of actionItems) item.click();
assert.deepEqual(actions, [
  "open-semantic-artifact",
  "open-terrain-pair",
  "open-inspection-workspace",
  "open-sample-trace",
  "open-backup",
  "save-backup",
], "File menu routes to the existing local import and backup controls");

const macMenu = buildStudioApplicationMenu(Menu, () => {}, "darwin");
assert.equal(macMenu.template[0].role, "appMenu", "macOS receives its standard app menu");
assert.equal(builtTemplates.length, 2);

(async () => {
  const scripts = [];
  const mainWindow = {
    isDestroyed: () => false,
    webContents: {
      isDestroyed: () => false,
      executeJavaScript(script, userGesture) {
        scripts.push({ script, userGesture });
        return Promise.resolve(true);
      },
    },
  };
  const dispatch = createStudioActionDispatcher(() => mainWindow);
  const controlIds = [
    "local-file",
    "local-pair-files",
    "inspection-workspace-file",
    "sample-trace-file",
    "studio-backup-import",
    "studio-backup-export",
  ];
  const actionIds = [
    "open-semantic-artifact",
    "open-terrain-pair",
    "open-inspection-workspace",
    "open-sample-trace",
    "open-backup",
    "save-backup",
  ];
  for (const action of actionIds) await dispatch(action);
  for (const [index, controlId] of controlIds.entries()) {
    assert.match(scripts[index].script, new RegExp(controlId));
  }
  assert.ok(scripts.every(item => item.userGesture), "menu actions retain user activation");

  const unavailable = createStudioActionDispatcher(() => null);
  assert.equal(await unavailable("open-semantic-artifact"), undefined, "missing windows are ignored");
  const destroyed = createStudioActionDispatcher(() => ({
    isDestroyed: () => true,
    webContents: null,
  }));
  assert.equal(await destroyed("save-backup"), undefined, "destroyed windows are ignored");

  const errors = [];
  const failing = createStudioActionDispatcher(() => ({
    isDestroyed: () => false,
    webContents: {
      isDestroyed: () => false,
      executeJavaScript: () => Promise.reject(new Error("renderer unavailable")),
    },
  }), (message, error) => errors.push([message, error.message]));
  await failing("open-semantic-artifact");
  assert.deepEqual(errors, [["Studio menu action failed:", "renderer unavailable"]]);

  console.log("PASS Studio desktop native File menu contract");
})().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
