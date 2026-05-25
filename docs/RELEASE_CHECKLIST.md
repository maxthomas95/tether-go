# Release Checklist - Tether Go

This checklist tracks the path from the initial Android scaffold to the first
debug and public releases.

## Before First Debug APK

- [x] Android stack selected and documented.
- [x] Gradle project committed.
- [x] Debug build command documented.
- [ ] SonarQube Cloud project imported, `SONAR_TOKEN` configured, automatic
  analysis disabled, and first CI-based analysis passing.
- [ ] License notices for Android dependencies started.
- [x] Basic smoke test documented.

## Before First Public Release

- [ ] Release signing approach documented.
- [ ] Signing keys excluded from the repo.
- [ ] Dependency licenses reviewed.
- [ ] Host key verification tested.
- [ ] Private key storage reviewed.
- [ ] Logs checked for secret redaction.
- [ ] Release APK built from a clean checkout.
- [ ] Required branch checks reflect the active CI/SonarCloud jobs.
- [ ] GitHub Release notes written.

## Release Commands

Current debug build checks:

```bash
./gradlew test
./gradlew lint
./gradlew assembleDebug
```

Release signing is not configured yet. Do not use or document release APK
commands until the signing approach exists.

## Debug Smoke Test

Build the debug APK with `./gradlew assembleDebug`, install it on an emulator or
device, launch Tether Go, and confirm the terminal screen renders. SSH terminal
rendering and host-key verification have separate manual checks in
`docs/SSH_PTY_SPIKE_TESTING.md`; private-key secure storage is still deferred.
