plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
}

android {
  namespace = "com.tether.go"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.tether.go"
    minSdk = 26
    targetSdk = 36
    versionCode = 1
    versionName = "0.1.0"
  }

  buildFeatures {
    compose = true
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro",
      )
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

dependencies {
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.compose.foundation)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.bouncycastle.bcprov)
  implementation(libs.connectbot.sshlib)
  implementation(libs.connectbot.termlib)
  implementation(libs.kotlinx.coroutines.android)

  debugImplementation(libs.androidx.compose.ui.tooling)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
}
