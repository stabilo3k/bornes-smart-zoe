import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "org.borneneutre.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "org.borneneutre.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 97
        versionName = "1.3.11"
        ndk { abiFilters += listOf("arm64-v8a") }
    }
    // Deux variantes, même versionCode : « fdroid » (aucune vérification de mise à jour ; F-Droid s'en charge) et « github » (vérification de data/version.json).
    flavorDimensions += "dist"
    productFlavors {
        create("fdroid") { dimension = "dist"; buildConfigField("boolean", "UPDATE_CHECK", "false"); buildConfigField("String", "VARIANTE", "\"variante-fdroid\"") }
        // « play » : même code que fdroid (pas de vérification de mise à jour, pas de permission d'installation) ; sert à produire le bundle pour Google Play, plus tard.
        create("play") { dimension = "dist"; buildConfigField("boolean", "UPDATE_CHECK", "false"); buildConfigField("String", "VARIANTE", "\"variante-play\"") }
        create("github") { dimension = "dist"; buildConfigField("boolean", "UPDATE_CHECK", "true"); buildConfigField("String", "VARIANTE", "\"variante-github\"") }
    }
    // Pas de bloc de métadonnées de dépendances dans l'APK (exigé par F-Droid : reproductibilité et transparence).
    dependenciesInfo { includeInApk = false; includeInBundle = false }
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
    // Lecteur de QR des bornes : CameraX et ZXing (Apache 2.0), lecture sur le téléphone, aucune image conservée
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("com.google.zxing:core:3.5.3")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
