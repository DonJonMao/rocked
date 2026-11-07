import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
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

val phoneApplicationId = localSecrets.getProperty("android.phoneApplicationId", "com.inspection.demo.phone")
    .trim()
    .ifBlank { "com.inspection.demo.phone" }

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
    namespace = "com.inspection.demo.phone"
    compileSdk = 34

    defaultConfig {
        applicationId = phoneApplicationId
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "ROKID_APP_ID", buildConfigString("rokid.appId"))
        buildConfigField("String", "ROKID_APP_SECRET", buildConfigString("rokid.appSecret"))
        buildConfigField("String", "ROKID_CLIENT_ID", buildConfigString("rokid.clientId"))
        buildConfigField("String", "REPORT_SYNC_URL", buildConfigString("inspection.syncUrl"))
        buildConfigField("String", "REPORT_SYNC_TOKEN", buildConfigString("inspection.syncToken"))
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    // Room's kapt processor in the current AGP line still runs its stub
    // compiler with Kotlin 1.9 language semantics. Keep the source language
    // explicit while using the Kotlin 2.0.21 toolchain so clean lint runs do
    // not fall back implicitly.
    kotlinOptions {
        languageVersion = "1.9"
        jvmTarget = "1.8"
    }
    buildFeatures { buildConfig = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
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
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.room:room-runtime:2.8.2")
    implementation("androidx.room:room-ktx:2.8.2")
    kapt("androidx.room:room-compiler:2.8.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.rokid.security:phone.sdk:2.2.2-E") { exclude(group = "org.slf4j") }
    implementation("com.google.code.gson:gson:2.10.1")
    testImplementation("junit:junit:4.13.2")
}
