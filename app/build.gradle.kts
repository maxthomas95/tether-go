import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.sonarqube.gradle.SonarExtension

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
    debug {
      enableUnitTestCoverage = true
    }
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

extensions.configure<SonarExtension>("sonar") {
  setAndroidVariant("debug")
  properties {
    // The scanner's AGP 9 adapter currently collects only javac destinations.
    // AGP's built-in Kotlin writes elsewhere. Read both compiler task outputs,
    // never AGP's private intermediate paths. CI compiles before invoking sonar.
    val mainClasses = files(
      tasks.named<KotlinCompile>("compileDebugKotlin").flatMap { it.destinationDirectory },
      tasks.named<JavaCompile>("compileDebugJavaWithJavac").flatMap { it.destinationDirectory },
    ).filter { it.isDirectory }.files
    val testClasses = files(
      tasks.named<KotlinCompile>("compileDebugUnitTestKotlin").flatMap { it.destinationDirectory },
      tasks.named<JavaCompile>("compileDebugUnitTestJavaWithJavac").flatMap { it.destinationDirectory },
    ).filter { it.isDirectory }.files
    check(mainClasses.isNotEmpty() && testClasses.isNotEmpty()) {
      "Compile :app:compileDebugUnitTestSources before running sonar."
    }
    property("sonar.java.binaries", mainClasses)
    property("sonar.binaries", mainClasses)
    property("sonar.java.test.binaries", testClasses)
    // Keep source roots, Android boot classpath and dependency libraries managed
    // by the scanner. It also adds these main binaries to test libraries.
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
