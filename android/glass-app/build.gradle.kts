import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val localSecrets = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

fun buildConfigString(key: String): String {
    val value = localSecrets.getProperty(key, "")
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\r", "\\r")
        .replace("\n", "\\n")
    return "\"$value\""
}

val glassApplicationId = localSecrets.getProperty("android.glassApplicationId", "com.inspection.demo.glass")
    .trim()
    .ifBlank { "com.inspection.demo.glass" }
val signingPropertyKeys = listOf(
    "android.signing.storeFile",
    "android.signing.storePassword",
    "android.signing.keyAlias",
    "android.signing.keyPassword",
)
val signingValues = signingPropertyKeys.map { localSecrets.getProperty(it, "").trim() }
val signingConfigured = signingValues.any(String::isNotBlank)
if (signingConfigured && signingValues.any(String::isBlank)) {
    error("android.signing.* must provide storeFile, storePassword, keyAlias and keyPassword together")
}

android {
    namespace = "com.inspection.demo.glass"
    compileSdk = 34

    defaultConfig {
        applicationId = glassApplicationId
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "ROKID_CLIENT_ID", buildConfigString("rokid.clientId"))
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions { jvmTarget = "1.8" }
    buildFeatures { buildConfig = true }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        jniLibs.pickFirsts += "lib/arm64-v8a/libr2aud.so"
        jniLibs.pickFirsts += "lib/armeabi-v7a/libr2aud.so"
    }
    signingConfigs {
        if (signingConfigured) {
            create("enterpriseRelease") {
                storeFile = rootProject.file(signingValues[0])
                storePassword = signingValues[1]
                keyAlias = signingValues[2]
                keyPassword = signingValues[3]
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (signingConfigured) signingConfig = signingConfigs.getByName("enterpriseRelease")
        }
    }
}

dependencies {
    implementation(project(":protocol"))
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.rokid.security:glass3.open.sdk:2.2.2-E") { exclude(group = "org.slf4j") }
    implementation("com.google.code.gson:gson:2.10.1")
}
