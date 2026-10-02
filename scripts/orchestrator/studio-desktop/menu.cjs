"use strict";

const STUDIO_CONTROLS = Object.freeze({
  "open-semantic-artifact": "local-file",
  "open-terrain-pair": "local-pair-files",
  "open-inspection-workspace": "inspection-workspace-file",
  "open-sample-trace": "sample-trace-file",
  "open-backup": "studio-backup-import",
  "save-backup": "studio-backup-export",
});

function createStudioActionDispatcher(getWindow, reportError = () => {}) {
  if (typeof getWindow !== "function") throw new TypeError("getWindow must be a function");

  return function dispatchStudioAction(action) {
    const controlId = STUDIO_CONTROLS[action];
    if (!controlId) throw new Error("unsupported Studio menu action: " + action);
    const mainWindow = getWindow();
    if (!mainWindow || mainWindow.isDestroyed()) return;
    const contents = mainWindow.webContents;
    if (!contents || contents.isDestroyed()) return;

    const script = "(() => { const control = document.getElementById(" +
      JSON.stringify(controlId) +
      "); if (!control || control.disabled) return false; control.click(); return true; })()";
    return contents.executeJavaScript(script, true).catch(error => {
      reportError("Studio menu action failed:", error);
    });
  };
}

function buildStudioApplicationMenu(Menu, dispatch, platform = process.platform) {
  const template = [];
  if (platform === "darwin") template.push({ role: "appMenu" });
  template.push({
    label: "File",
    submenu: [
      {
        label: "Open local semantic JSON…",
        accelerator: "CmdOrCtrl+O",
        click: () => dispatch("open-semantic-artifact"),
      },
      {
        label: "Open terrain + hydrology pair…",
        click: () => dispatch("open-terrain-pair"),
      },
      {
        label: "Open inspection workspace…",
        click: () => dispatch("open-inspection-workspace"),
      },
      {
        label: "Open sample trace…",
        click: () => dispatch("open-sample-trace"),
      },
      { type: "separator" },
      {
        label: "Open Studio backup…",
        click: () => dispatch("open-backup"),
      },
      {
        label: "Save Studio backup…",
        accelerator: "CmdOrCtrl+Shift+S",
        click: () => dispatch("save-backup"),
      },
      { type: "separator" },
      { role: "close" },
    ],
  });
  return Menu.buildFromTemplate(template);
}

module.exports = { buildStudioApplicationMenu, createStudioActionDispatcher };
