# SPK Paper 0.2 validation — 16 October 2026

- Android assembleDebug and lintDebug completed successfully, with zero lint errors.
- APK signature verified. Signing certificate matches the previous Mana Paper APK; versionCode increased to 2 for an in-place update.
- Application label: SPK Paper. Minimum Android version: 8.0.
- Built-in PDF viewer and custom page/zoom controls removed.
- ACTION_VIEW prefers Google Drive, falling back to an Android PDF app chooser.
- PDF access uses a non-exported content provider, temporary read-only URI grants, and an exact two-file allowlist. Issue date/checksum distinguish each issue URI for external-viewer caching.
- Publisher tests: two passed; Python files compile.
- Sample PDF updated to SPK Paper and external-viewer instructions.

Not yet verified on a physical phone with Google Drive installed. The daily publishing account remains unconnected; live newspaper delivery still requires one-time hosting setup.
