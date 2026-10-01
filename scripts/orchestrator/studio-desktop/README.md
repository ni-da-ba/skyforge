# Skyforge Studio desktop preview

This is an unsigned desktop preview of the existing local-first Studio. It opens in its own app window and bundles the exact Studio web assets and sample evidence produced by the repository's review-package staging script.

## Use

- Windows: run the Skyforge-Studio-Windows-Setup.exe installer.
- macOS: open the Skyforge-Studio-macOS.dmg disk image, then move Skyforge Studio to Applications.
- Linux: install Skyforge-Studio-Linux.deb with your distribution package installer.

When you export a file, the desktop app opens the operating system's Save dialog and starts with Studio's suggested filename. Canceling the dialog cancels only that export.

The first preview is unsigned. Windows and macOS may show an operating-system warning until a code-signing identity is configured for a later release.

## Local data and network use

World briefs, inspection workspaces, and saved comparisons remain in this app's local browser storage on this device. Nothing uploads unless a future connected backend is explicitly configured. The packaged app does not provide a world generator or registered-artifact API; those connected features remain unavailable.

The window uses a stable private skyforge://studio origin so local browser storage survives app restarts. The renderer cannot access Node.js APIs or navigate to external websites. The app serves only its packaged static files through Electron's custom protocol handler. Files downloaded by the Studio interface are saved only after the user confirms the native Save dialog.

The included S2 and regional specimens are project review examples. Reopened diagnostics remain local/unbound and do not gain registered-artifact verification or review authority.

## Development

From this folder, place the staged Studio review package in studio-app/, install dependencies with npm, then run npm start. GitHub Actions stages the package and produces platform-specific review artifacts.
