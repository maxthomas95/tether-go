# SonarQube Cloud and Android coverage

Android CI follows the desktop CI pattern where it fits Android: build/test
first, upload reports, then scan in a separate job. It keeps the Gradle scanner
for Android source, classpath, variant, and bytecode metadata; it does not use
the desktop Node scanner.

The scanner receives the minimal `7.3.0.8198` → `7.3.1.8318` patch update for
[SCANGRADLE-410](https://github.com/SonarSource/sonar-scanner-gradle/releases/tag/7.3.1.8318):
7.3.0 uses an incorrect hardcoded compiled-binary path for AGP 9+. The patch
uses the Java compiler output destination, but its Android adapter still omits
AGP's built-in Kotlin output ([adapter source](https://github.com/SonarSource/sonar-scanner-gradle/blob/7.3.1.8318/src/main/java/org/sonarqube/gradle/AndroidConfig.java)).
The app therefore explicitly supplies main/test
binary directories from the Kotlin and Java compile task providers, retaining
only existing directories after compilation. No private AGP intermediate paths
are hardcoded. Source roots, Android boot classpath and dependency libraries
remain scanner-managed; the scanner also adds main binaries to test libraries.
These bytecode fixes are separate from the token/bootstrap 403.

## Jobs and quality gate

- **Android build** runs unit tests, creates debug unit-test coverage, runs
  Android lint, and builds/uploads the debug APK. Its timeout is 30 minutes.
- **SonarQube scan** needs a successful build. It checks for a nonempty
  `SONAR_TOKEN`, downloads the reports, and compiles debug main/test bytecode
  without rerunning unit tests. It checks out full Git history. Its timeout is
  20 minutes, with 10 minutes for the scan and quality-gate wait.
- **Gradle checks** retains the original check name and effective gate: for a
  trusted run, both build and Sonar must succeed. A failed, canceled, or
  unexpectedly skipped scan fails this aggregate. Do not replace it with a
  build-only required check.

The scanner still uses `-Dsonar.qualitygate.wait=true`: successful submission
is not a passing quality gate. The scan job must also receive a passing gate
from SonarQube Cloud. The aggregate does not soften a failed gate. CI does not
change account settings, required checks, or the existing quality-gate policy.

Fork and Dependabot pull requests run build/tests/coverage but intentionally
skip the authenticated scan because repository secrets are unavailable. Only
that event-based skip is accepted by the aggregate; their merged code is
scanned on `main`. Keep `pull_request`, not `pull_request_target`. Missing
secrets on a trusted run are a failure, not a reason to skip the gate.

Runs on the same ref cancel superseded runs. CI also supports manual runs from
Actions > Android CI > Run workflow.

## Coverage and local commands

AGP 9.2's `enableUnitTestCoverage` is enabled only for the debug build type.
The native task runs debug JVM unit tests and produces JaCoCo XML plus
browsable HTML. The full `test` task is retained so adding another unit-test
variant does not silently remove it from CI.

```bash
./gradlew test :app:createDebugUnitTestCoverageReport --no-daemon
./gradlew lint --no-daemon
./gradlew assembleDebug --no-daemon
```

Reports:

- HTML: `app/build/reports/coverage/test/debug/index.html`
- JaCoCo XML: `app/build/reports/coverage/test/debug/report.xml`
- JUnit XML: `app/build/test-results/testDebugUnitTest/TEST-*.xml`
- Test HTML: `app/build/reports/tests/testDebugUnitTest/index.html`

The `tether-go-test-reports` artifact retains coverage and test reports for
seven days. Upload paths share the `app/build` ancestor; the scan downloads
into `app/build` to restore the exact report locations. Both jobs require a
nonempty coverage XML, and the scan checks that JUnit results exist.

Coverage includes the app's debug production classes, including untested
session, SSH, notification, and Compose UI code. There are no custom coverage
exclusions or local thresholds. The old scaffold-wide `**/*` coverage exclusion
is removed. AGP/JaCoCo apply their own generated-bytecode filtering; this is not
instrumentation/device coverage and does not measure native `libvterm` or
third-party library internals. Unit execution does not prove assertion quality,
Android permission flows, or end-to-end SSH/terminal behavior. Keep the device
checks in the release checklist.

To prepare a separate scan checkout after restoring the reports, run:

```bash
./gradlew :app:compileDebugUnitTestSources --no-daemon
# SONAR_TOKEN must already be supplied securely through the environment.
./gradlew sonar --no-daemon -Dsonar.qualitygate.wait=true
```

Keep compilation and scanning as separate invocations: the binary-property
configuration expects compiler output directories to exist. Do not run `clean`
between restoring the reports and scanning. The scanner
reads the absolute JaCoCo XML path configured in the root Gradle build. Android
source/classpath metadata remains managed by the Gradle plugin; the app supplies
compiler-derived main/test binary directories as described above. Existing image exclusions,
theme-table duplication settings, and manifest hotspot configuration are
unchanged; this refactor adds no security suppressions or source exclusions.

## Release version

The Gradle scanner reads `sonar.projectVersion` directly from the app's
`android.defaultConfig.versionName` (currently `0.1.0`). There is one release
version source; it is not a build number or commit SHA. Advance it through the
normal release process, never merely to reset findings. If the project uses a
Previous version new-code definition, that release version controls its period;
PR analysis still compares with the target branch. This change does not alter
the project's new-code settings.

## Token setup and the existing bootstrap 403

The project remains `maxthomas95_tether-go`, organization `maxthomas95`, host
`https://sonarcloud.io`. Before this CI split, the scanner failed with HTTP 403
while requesting its analysis JRE from SonarQube Cloud. That is an unresolved
scanner authorization/account or service-access problem, not evidence that
tests or Android compilation failed. A nonempty-token check cannot validate
token permissions, and this workflow refactor does not claim to fix that 403.

The repository/SonarQube project owner should:

1. Verify the SonarQube Cloud project and organization above, and that the
   intended account/token has Execute Analysis access to **Tether Go**. A token
   that works for desktop Tether is not proof of access to this project.
2. Open the [Tether Go repository](https://github.com/maxthomas95/tether-go),
   then **Settings → Secrets and variables → Actions**, and check or replace
   `SONAR_TOKEN` there with the repository owner's access.
   Never paste tokens into chat, source, command arguments, or logs. Do not
   print the token when diagnosing the failure.
3. Confirm CI-based analysis is enabled and Automatic Analysis is disabled for
   this project. Do not change its quality gate or branch protection to make a
   failing scan green.
4. Rerun Android CI on the exact PR head. Confirm the scanner passes JRE
   bootstrap, imports the JaCoCo report without unresolved source paths, and
   publishes analysis for that revision. Then check the quality-gate result.
   Coverage below the existing gate is a real failing result, not a setup bug.
5. If 403 persists with verified access, use the sanitized failing endpoint and
   job logs to investigate SonarQube Cloud organization/service restrictions
   with the project administrator or Sonar support. Do not bypass authorization
   or disable JRE provisioning as a substitute for fixing access.

References: [Android coverage reports](https://developer.android.com/studio/test/coverage-report),
[SonarScanner for Gradle](https://docs.sonarsource.com/sonarqube-cloud/analyzing-source-code/scanners/sonarscanner-for-gradle),
[Java/JaCoCo coverage](https://docs.sonarsource.com/sonarqube-cloud/enriching/test-coverage/java-test-coverage).
