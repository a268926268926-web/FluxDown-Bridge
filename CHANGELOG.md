# Changelog

All notable changes are documented here.

## [1.5.2] - 2026-10-23

### Added

- Detect when Android notifications or the active bridge notification channel
  are disabled and show an in-app troubleshooting hint.
- Log whether the foreground notification is enabled without recording message
  contents or credentials.

### Changed

- Present notification visibility as a diagnostic choice rather than a hard
  requirement: standard notification, quiet notification, or system-disabled.

## [1.5.1] - 2026-10-23

### Fixed

- Prevented idle Chromium preconnections from exhausting the four old request
  workers and causing occasional `/ping` and download timeouts.
- Increased the bounded request worker pool to 12 and reduced idle socket reads
  to 3 seconds.
- Added request timing, status, and active-client logs without recording URLs,
  cookies, tokens, or request bodies.

### Compatibility

- Documented Android 8.0 through Android 17 / API 26 through API 37 as the
  current configured range.

## [1.5.0] - 2026-10-22

### Added

- Official Material 3 Expressive theme and motion scheme.
- Compact section grouping for connection, app settings, and first-time setup.
- Project documentation, protocol reference, architecture notes, and CI.

### Changed

- Status animations now use the app's Material motion scheme.
- Buttons use native Material 3 Expressive shapes and state motion.
- The header hierarchy is more compact on narrow screens.
- The server version now comes from `BuildConfig.VERSION_NAME`.
- Gradle uses the standard wrapper distribution and official repositories.

### Security

- Disabled Android backups and documented the loopback trust boundary.

## [1.4.0] - 2026-10-22

### Added

- Dynamic notification color and Lawnicons-style monochrome artwork.
- Quiet foreground-notification channel.

### Fixed

- Background service and notification behavior on newer Android releases.
