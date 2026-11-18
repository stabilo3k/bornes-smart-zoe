import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "org.borneneutre.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "org.borneneutre.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 21
        versionName = "1.2.1"
        ndk { abiFilters += listOf("arm64-v8a") }
    }
    // Signature de publication : clé gardée hors du dépôt, passée par -PBN_KEYSTORE=... -PBN_PROPS=... (F-Droid signe avec sa propre clé).
    signingConfigs {
        if (project.hasProperty("BN_KEYSTORE")) {
            create("release") {
                val props = Properties(); file(project.property("BN_PROPS") as String).inputStream().use { props.load(it) }
                storeFile = file(project.property("BN_KEYSTORE") as String)
                storePassword = props.getProperty("storePassword"); keyAlias = props.getProperty("keyAlias"); keyPassword = props.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.maplibre.gl:android-sdk:13.7.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
