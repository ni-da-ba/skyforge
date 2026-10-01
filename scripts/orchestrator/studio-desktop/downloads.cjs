"use strict";

function safeSuggestedFilename(filename) {
  const normalized = typeof filename === "string" ? filename.replace(/\\/g, "/") : "";
  const candidate = normalized
    .split("/")
    .pop()
    .replace(/[\u0000-\u001f\u007f<>:"|?*]/g, "_")
    .trim();

  if (!candidate || candidate === "." || candidate === "..") return "studio-export";
  return candidate.length <= 180 ? candidate : candidate.slice(0, 180);
}

function registerDownloadSaveDialogs(session, getMainWindow, app, path) {
  if (!session || typeof session.on !== "function") {
    throw new TypeError("download session must support event registration");
  }
  if (typeof getMainWindow !== "function") {
    throw new TypeError("main-window provider must be a function");
  }
  if (!app || typeof app.getPath !== "function") {
    throw new TypeError("app must provide getPath");
  }
  if (!path || typeof path.join !== "function") {
    throw new TypeError("path must provide join");
  }

  const onWillDownload = (_event, item, webContents) => {
    const mainWindow = getMainWindow();
    if (!mainWindow || webContents !== mainWindow.webContents) return;
    if (!item || typeof item.getFilename !== "function" || typeof item.setSaveDialogOptions !== "function") return;

    const filename = safeSuggestedFilename(item.getFilename());
    item.setSaveDialogOptions({
      title: "Save a Skyforge Studio export",
      buttonLabel: "Save",
      defaultPath: path.join(app.getPath("downloads"), filename),
    });
  };

  session.on("will-download", onWillDownload);
  return () => {
    if (typeof session.removeListener === "function") {
      session.removeListener("will-download", onWillDownload);
    }
  };
}

module.exports = Object.freeze({
  registerDownloadSaveDialogs,
  safeSuggestedFilename,
});
