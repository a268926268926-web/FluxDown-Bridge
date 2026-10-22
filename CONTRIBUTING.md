# Contributing

Thanks for improving FluxDown Bridge.

## Before opening an issue

- Search existing issues first.
- Confirm the problem reproduces with the current `main` branch or latest
  release.
- Do not post the bridge token, browser cookies, private download URLs, Root
  manager credentials, or unredacted device identifiers.
- Include the Android version, device model, Root provider and version,
  FluxDown version, browser name/version, and the exact visible error.

## Development setup

Requirements:

- JDK 21
- Android SDK Platform 37
- Android Build Tools 37.0.0
- A connected Android 8.0+ device for optional runtime testing

```bash
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME=/path/to/android-sdk
export ANDROID_SDK_ROOT="$ANDROID_HOME"

./gradlew :app:assembleDebug :app:lintDebug --no-daemon
```

## Pull requests

- Keep changes focused on one behavior or documentation problem.
- Preserve the loopback-only security boundary.
- Do not add a second update path to FluxDown or its browser extension unless
  the PR explains why the existing adapter cannot be extended.
- Update `README.md`, `README.zh-CN.md`, `docs/PROTOCOL.md`, or
  `docs/ARCHITECTURE.md` when behavior changes.
- Run `assembleDebug` and `lintDebug` before opening the PR.
- Describe runtime testing separately from build-only verification.

## Commit style

Clear imperative subjects are preferred, for example:

```text
Add batch handoff timeout
Fix quiet notification channel refresh
Document root troubleshooting
```

## License

By contributing, you agree that your contribution is licensed under the
repository's [MIT License](LICENSE).
