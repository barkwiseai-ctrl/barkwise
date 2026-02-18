import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

fun readMapsApiKeyFromLocalProperties(rootDir: File): String {
    val localPropsFile = rootDir.resolve("local.properties")
    if (!localPropsFile.exists()) return ""
    val props = Properties()
    localPropsFile.inputStream().use { props.load(it) }
    return props.getProperty("MAPS_API_KEY", "").trim()
}

fun readFromLocalProperties(rootDir: File, key: String): String? {
    val localPropsFile = rootDir.resolve("local.properties")
    if (!localPropsFile.exists()) return null
    val props = Properties()
    localPropsFile.inputStream().use { props.load(it) }
    return props.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }
}

fun readStringConfig(project: Project, key: String, default: String): String {
    val fromGradleProperty = project.findProperty(key) as String?
    val fromEnv = System.getenv(key)
    val fromLocalProperties = readFromLocalProperties(project.rootDir, key)
    return (fromGradleProperty ?: fromEnv ?: fromLocalProperties ?: default).trim()
}

android {
    namespace = "com.petsocial.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.petsocial.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        val mapsApiKey = (project.findProperty("MAPS_API_KEY") as String?)
            ?: System.getenv("MAPS_API_KEY")
            ?: readMapsApiKeyFromLocalProperties(rootDir)
            ?: ""
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        val escapedMapsApiKey = mapsApiKey.replace("\"", "\\\"")
        buildConfigField("String", "MAPS_API_KEY", "\"$escapedMapsApiKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            resValue("string", "app_name", "BarkWise Dev")
            val devApiUrl = readStringConfig(project, "BARKWISE_DEV_API_BASE_URL", "http://10.0.2.2:8000/")
            val escapedUrl = devApiUrl.replace("\"", "\\\"")
            buildConfigField("String", "API_BASE_URL", "\"$escapedUrl\"")
            buildConfigField("Boolean", "USE_MOCK_DATA", "true")
            buildConfigField("String", "ENVIRONMENT", "\"dev\"")
        }
        create("staging") {
            dimension = "environment"
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            resValue("string", "app_name", "BarkWise (test)")
            val stagingApiUrl = readStringConfig(project, "BARKWISE_STAGING_API_BASE_URL", "http://10.0.2.2:8000/")
            val escapedUrl = stagingApiUrl.replace("\"", "\\\"")
            buildConfigField("String", "API_BASE_URL", "\"$escapedUrl\"")
            buildConfigField("Boolean", "USE_MOCK_DATA", "false")
            buildConfigField("String", "ENVIRONMENT", "\"staging\"")
        }
        create("prod") {
            dimension = "environment"
            resValue("string", "app_name", "BarkWise")
            val prodApiUrl = readStringConfig(project, "BARKWISE_PROD_API_BASE_URL", "https://api.barkwise.app/")
            val escapedUrl = prodApiUrl.replace("\"", "\\\"")
            buildConfigField("String", "API_BASE_URL", "\"$escapedUrl\"")
            buildConfigField("Boolean", "USE_MOCK_DATA", "false")
            buildConfigField("String", "ENVIRONMENT", "\"prod\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2025.01.00")
    implementation(bom)
    androidTestImplementation(bom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("com.google.android.material:material:1.12.0")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation(platform("com.google.firebase:firebase-bom:33.11.0"))
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.android.gms:play-services-maps:19.0.0")
    implementation("com.google.maps.android:maps-compose:6.4.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
}

// Keep CLI/dev automation stable: map legacy debug task names to the dev flavor.
tasks.register("installDebug") {
    dependsOn("installDevDebug")
}

tasks.register("compileDebugKotlin") {
    dependsOn("compileDevDebugKotlin")
}

// Enable google-services plugin only when local Firebase config is present.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}
