## Problem

Describe the concrete problem and who it affects.

## Change

Explain the resulting behavior and why this implementation is appropriate.

## Security

State whether the change affects loopback binding, authentication, URL parsing,
Root command construction, request limits, notification visibility, or data
storage.

## Validation

- [ ] `./gradlew :app:assembleDebug --no-daemon`
- [ ] `./gradlew :app:lintDebug --no-daemon`
- [ ] Runtime testing documented separately, if performed
- [ ] Documentation updated when behavior or compatibility changed

## Device context

Complete when the PR involves runtime behavior:

- FluxDown version:
- Android version / device:
- Browser and version:
- Root provider and version:
