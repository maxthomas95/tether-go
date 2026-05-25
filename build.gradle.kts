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
  }
}
