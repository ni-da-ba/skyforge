"use strict";

const path = require("node:path");
const fs = require("node:fs/promises");
const { app, BrowserWindow, protocol } = require("electron");

app.setAppUserModelId("com.squirrel.SkyforgeStudio.skyforge-studio");

protocol.registerSchemesAsPrivileged([
  {
    scheme: "skyforge",
    privileges: {
      standard: true,
      secure: true,
      supportFetchAPI: true,
    },
  },
]);

const APP_HOST = "studio";
const APP_URL = "skyforge://studio/index.html";
const CSP = [
  "default-src 'self'",
  "script-src 'self'",
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data: blob:",
  "font-src 'self' data:",
  "connect-src 'self'",
  "object-src 'none'",
  "base-uri 'none'",
  "frame-ancestors 'none'",
  "form-action 'self'",
].join("; ");

let mainWindow = null;
let protocolRegistered = false;

function staticRoot() {
  return path.join(app.getAppPath(), "studio-app");
}

function response(status, body, contentType = "text/plain; charset=utf-8") {
  return new Response(body, {
    status,
    headers: {
      "content-type": contentType,
      "content-security-policy": CSP,
      "x-content-type-options": "nosniff",
      "referrer-policy": "no-referrer",
      "cache-control": "no-store",
    },
  });
}

function contentTypeFor(filePath) {
  const extension = path.extname(filePath).toLowerCase();
  return ({
    ".html": "text/html; charset=utf-8",
    ".js": "text/javascript; charset=utf-8",
    ".css": "text/css; charset=utf-8",
    ".json": "application/json; charset=utf-8",
    ".csv": "text/csv; charset=utf-8",
    ".svg": "image/svg+xml",
    ".png": "image/png",
    ".jpg": "image/jpeg",
    ".jpeg": "image/jpeg",
    ".webp": "image/webp",
    ".txt": "text/plain; charset=utf-8",
  })[extension] || "application/octet-stream";
}

async function handleAppRequest(request) {
  let url;
  try {
    url = new URL(request.url);
  } catch {
    return response(400, "Invalid application URL");
  }

  if (url.protocol !== "skyforge:" || url.hostname !== APP_HOST) {
    return response(404, "Not found");
  }
  if (request.method !== "GET" && request.method !== "HEAD") {
    return response(405, "Method not allowed");
  }

  let decodedPath;
  try {
    decodedPath = decodeURIComponent(url.pathname);
  } catch {
    return response(400, "Invalid application path");
  }

  const segments = decodedPath.split(/[\\/]+/).filter((segment) => segment && segment !== ".");
  if (segments.some((segment) => segment === ".." || segment.includes("\0"))) {
    return response(400, "Invalid application path");
  }
  if (segments.length === 0) segments.push("index.html");

  const root = staticRoot();
  const filePath = path.resolve(root, ...segments);
  const relativePath = path.relative(root, filePath);
  if (!relativePath || relativePath.startsWith("..") || path.isAbsolute(relativePath)) {
    return response(400, "Invalid application path");
  }

  try {
    const file = await fs.readFile(filePath);
    const headers = {
      "content-type": contentTypeFor(filePath),
      "content-security-policy": CSP,
      "x-content-type-options": "nosniff",
      "referrer-policy": "no-referrer",
      "cache-control": "no-store",
    };
    return new Response(request.method === "HEAD" ? null : file, { status: 200, headers });
  } catch (error) {
    if (error && error.code === "ENOENT") return response(404, "Not found");
    console.error("Studio asset could not be read:", error);
    return response(500, "Studio asset unavailable");
  }
}

function registerApplicationProtocol() {
  if (protocolRegistered) return;
  protocol.handle("skyforge", handleAppRequest);
  protocolRegistered = true;
}

function createMainWindow() {
  mainWindow = new BrowserWindow({
    title: "Skyforge Studio",
    width: 1440,
    height: 960,
    minWidth: 1000,
    minHeight: 680,
    show: false,
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      webSecurity: true,
    },
  });

  mainWindow.webContents.setWindowOpenHandler(() => ({ action: "deny" }));
  mainWindow.webContents.on("will-navigate", (event, target) => {
    let next;
    try {
      next = new URL(target);
    } catch {
      event.preventDefault();
      return;
    }
    if (next.protocol !== "skyforge:" || next.hostname !== APP_HOST) {
      event.preventDefault();
    }
  });
  mainWindow.once("ready-to-show", () => mainWindow && mainWindow.show());
  mainWindow.on("closed", () => {
    mainWindow = null;
  });
  mainWindow.loadURL(APP_URL).catch((error) => {
    console.error("Skyforge Studio failed to open:", error);
  });
}

if (require("electron-squirrel-startup")) {
  app.quit();
} else {
  const hasSingleInstanceLock = app.requestSingleInstanceLock();
if (!hasSingleInstanceLock) {
  app.quit();
} else {
  app.on("second-instance", () => {
    if (!mainWindow) createMainWindow();
    if (mainWindow && mainWindow.isMinimized()) mainWindow.restore();
    if (mainWindow) mainWindow.focus();
  });

  app.whenReady().then(() => {
    registerApplicationProtocol();
    createMainWindow();
    app.on("activate", () => {
      if (BrowserWindow.getAllWindows().length === 0) createMainWindow();
    });
  });

  app.on("window-all-closed", () => {
    if (process.platform !== "darwin") app.quit();
  });
}

}
