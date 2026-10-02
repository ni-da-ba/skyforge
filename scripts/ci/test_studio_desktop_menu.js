"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const {
  buildStudioApplicationMenu,
  createBackupActionDispatcher,
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
assert.match(studioHtml, /id="studio-backup-import"/);
assert.match(studioHtml, /id="studio-backup-export"/);

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
assert.deepEqual(fileMenu.submenu.slice(0, 2).map(item => ({
  label: item.label,
  accelerator: item.accelerator,
})), [
  { label: "Open Studio backup…", accelerator: "CmdOrCtrl+O" },
  { label: "Save Studio backup…", accelerator: "CmdOrCtrl+Shift+S" },
]);
fileMenu.submenu[0].click();
fileMenu.submenu[1].click();
assert.deepEqual(actions, ["open-backup", "save-backup"], "menu routes to existing backup actions");

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
  const dispatch = createBackupActionDispatcher(() => mainWindow);
  await dispatch("open-backup");
  await dispatch("save-backup");
  assert.match(scripts[0].script, /studio-backup-import/);
  assert.match(scripts[1].script, /studio-backup-export/);
  assert.ok(scripts.every(item => item.userGesture), "menu actions run with user activation");

  const unavailable = createBackupActionDispatcher(() => null);
  assert.equal(await unavailable("open-backup"), undefined, "missing windows are ignored");
  const destroyed = createBackupActionDispatcher(() => ({
    isDestroyed: () => true,
    webContents: null,
  }));
  assert.equal(await destroyed("save-backup"), undefined, "destroyed windows are ignored");

  const errors = [];
  const failing = createBackupActionDispatcher(() => ({
    isDestroyed: () => false,
    webContents: {
      isDestroyed: () => false,
      executeJavaScript: () => Promise.reject(new Error("renderer unavailable")),
    },
  }), (message, error) => errors.push([message, error.message]));
  await failing("open-backup");
  assert.deepEqual(errors, [["Studio backup menu action failed:", "renderer unavailable"]]);

  console.log("PASS Studio desktop native backup menu contract");
})().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
