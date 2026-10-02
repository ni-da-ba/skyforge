(function (root) {
  "use strict";

  function fromProtocol(protocol) {
    const desktopPreview = protocol === "skyforge:";
    return Object.freeze({
      mode: desktopPreview ? "desktop-preview" : "web",
      canConnectRegisteredArtifacts: !desktopPreview,
      canGenerateWorlds: false,
    });
  }

  function localScopeCopy(protocol, message) {
    const text = String(message);
    if (protocol !== "skyforge:") return text;
    return text
      .replace(/\bthis browser session\b/gi, "this app session")
      .replace(/\bthis browser profile\b/gi, "this Studio app")
      .replace(/\banother browser\b/gi, "another device or Studio profile")
      .replace(/\byour browser\b/gi, "this app")
      .replace(/\bthis browser\b/gi, "this app")
      .replace(/\bbrowser storage\b/gi, "app storage")
      .replace(/\bbrowser-local\b/gi, "app-local")
      .replace(/\bbrowser library\b/gi, "local library")
      .replace(/\bsaved browser data\b/gi, "saved local data");
  }

  const api = Object.freeze({ fromProtocol, localScopeCopy });
  if (root) root.SkyforgeStudioCapabilities = api;
  if (typeof module !== "undefined" && module.exports) module.exports = api;
})(typeof window === "undefined" ? globalThis : window);
