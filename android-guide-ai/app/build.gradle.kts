plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android { namespace = "com.guideai.app"; compileSdk = 35
    defaultConfig { applicationId = "com.guideai.app"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "0.1.0"; buildConfigField("String", "GUIDE_API_URL", "\"${project.findProperty("guideApiUrl") ?: ""}\"") }
    buildFeatures { buildConfig = true }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
