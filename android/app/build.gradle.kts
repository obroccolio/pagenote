plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.compose")
}

android {
  namespace = "io.pagenote.android"
  compileSdk = 35
  defaultConfig {
    applicationId = "io.pagenote.android"
    minSdk = 29
    targetSdk = 35
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    versionCode = 1
    versionName = "1.0.0"
  }
  buildTypes {
    getByName("debug") {
      applicationIdSuffix = ".debug"
    }
    getByName("release") {
      isMinifyEnabled = false
    }
  }
  splits {
    abi {
      isEnable = true
      reset()
      include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
      isUniversalApk = false
    }
  }
  buildFeatures {
    compose = true
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions {
    jvmTarget = "17"
  }
}

dependencies {
  implementation(project(":core-project"))
  implementation(project(":core-reader"))
  implementation(project(":feature-notes"))
  implementation(project(":feature-annotations"))
  implementation(project(":feature-pen"))
  implementation(project(":feature-ocr"))
  implementation(project(":agent"))
  implementation("androidx.core:core-ktx:1.15.0")
  implementation("androidx.activity:activity-compose:1.9.3")
  implementation("androidx.datastore:datastore-preferences:1.2.1")
  implementation(platform("androidx.compose:compose-bom:2024.12.01"))
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.material3:material3")
  implementation("io.noties.markwon:core:4.6.2")
  implementation("io.noties.markwon:editor:4.6.2")
  implementation("io.noties.markwon:ext-tables:4.6.2")
  implementation("io.noties.markwon:image:4.6.2")
  testImplementation("junit:junit:4.13.2")
  androidTestImplementation("androidx.test:runner:1.6.2")
  androidTestImplementation("androidx.test.ext:junit:1.2.1")
  androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.7.6")
}
