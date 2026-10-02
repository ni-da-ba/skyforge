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

  const api = Object.freeze({ fromProtocol });
  if (root) root.SkyforgeStudioCapabilities = api;
  if (typeof module !== "undefined" && module.exports) module.exports = api;
})(typeof window === "undefined" ? globalThis : window);
