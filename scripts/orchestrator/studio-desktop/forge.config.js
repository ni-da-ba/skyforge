"use strict";

module.exports = {
  packagerConfig: {
    name: "Skyforge Studio",
    appBundleId: "io.github.nidaba.skyforge-studio",
    executableName: "skyforge-studio",
    asar: true,
  },
  makers: [
    {
      name: "@electron-forge/maker-squirrel",
      config: {
        name: "SkyforgeStudio",
        authors: "Skyforge",
        description: "Backend-neutral world authoring and semantic inspection.",
      },
    },
    {
      name: "@electron-forge/maker-dmg",
    },
    {
      name: "@electron-forge/maker-deb",
      config: {
        options: {
          maintainer: "Skyforge",
          homepage: "https://github.com/ni-da-ba/skyforge",
          categories: ["Graphics"],
        },
      },
    },
  ],
};
