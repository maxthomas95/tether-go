plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.sonarqube)
}

sonar {
  properties {
    property("sonar.projectKey", "maxthomas95_tether-go")
    property("sonar.organization", "maxthomas95")
    property("sonar.host.url", "https://sonarcloud.io")
    // Use the same release version as the APK, not a per-build version.
    property(
      "sonar.projectVersion",
      project(":app").extensions
        .getByType<com.android.build.api.dsl.ApplicationExtension>()
        .defaultConfig.versionName!!,
    )
    // AGP's native debug unit-test report includes untested production classes.
    // An absolute path is inherited correctly by the Android subproject.
    property(
      "sonar.coverage.jacoco.xmlReportPaths",
      layout.projectDirectory.file("app/build/reports/coverage/test/debug/report.xml").asFile.absolutePath,
    )
    // Binary image assets are not source; don't scan them for encoding/issues.
    property("sonar.exclusions", "**/*.png")
    // The theme palette is a 1:1 ported value table (repetitive data, not
    // copy-pasted logic); exclude it from copy/paste detection.
    property("sonar.cpd.exclusions", "**/ui/theme/TetherTheme.kt")
    // RECORD_AUDIO is required for push-to-talk voice input. Treat that manifest
    // permission hotspot (xml:S5604) as reviewed-safe in config rather than
    // re-confirming it in the SonarCloud UI on every analysis.
    property("sonar.issue.ignore.multicriteria", "e1")
    property("sonar.issue.ignore.multicriteria.e1.ruleKey", "xml:S5604")
    property("sonar.issue.ignore.multicriteria.e1.resourceKey", "**/AndroidManifest.xml")
  }
}
