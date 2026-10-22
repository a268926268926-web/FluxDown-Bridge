# Changelog

All notable changes are documented here.

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
