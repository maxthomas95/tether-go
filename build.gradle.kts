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
    // Coverage gates should start when executable product behavior lands.
    property("sonar.coverage.exclusions", "**/*")
    // Binary image assets are not source; don't scan them for encoding/issues.
    property("sonar.exclusions", "**/*.png")
    // The theme palette is a 1:1 ported value table (repetitive data, not
    // copy-pasted logic); exclude it from copy/paste detection.
    property("sonar.cpd.exclusions", "**/ui/theme/TetherTheme.kt")
  }
}
