import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties
import java.net.URI
import org.gradle.api.GradleException

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

fun readIntConfig(project: Project, key: String, default: Int): Int {
    val raw = readStringConfig(project, key, default.toString())
    return raw.toIntOrNull() ?: throw GradleException("Invalid $key value: '$raw'. Use an integer.")
}

fun readBooleanConfig(project: Project, key: String, default: Boolean): Boolean {
    return when (readStringConfig(project, key, default.toString()).lowercase()) {
        "1", "true", "yes", "on" -> true
        "0", "false", "no", "off" -> false
        else -> throw GradleException("Invalid $key value. Use true/false.")
    }
}

fun readApiBaseUrlConfig(project: Project, key: String, default: String): String {
    val raw = readStringConfig(project, key, default).trim().trim('"')
    val normalized = if (raw.endsWith("/")) raw else "$raw/"
    val parsed = runCatching { URI(normalized) }.getOrNull()
    val scheme = parsed?.scheme?.lowercase()
    val host = parsed?.host
    if ((scheme != "http" && scheme != "https") || host.isNullOrBlank() || host.any { it.isWhitespace() }) {
        throw GradleException(
            "Invalid $key value: '$raw'. Use a full URL like https://staging-api.barkwise.app/."
        )
    }
    return normalized
}

fun readInstallPageUrlConfig(project: Project): String {
    val fromGradleProperty = (project.findProperty("BARKWISE_INSTALL_PAGE_URL") as String?)?.trim()
    val fromEnv = System.getenv("BARKWISE_INSTALL_PAGE_URL")?.trim()
    val fromLocalProperties = readFromLocalProperties(project.rootDir, "BARKWISE_INSTALL_PAGE_URL")
    val configured = fromGradleProperty ?: fromEnv ?: fromLocalProperties
    if (!configured.isNullOrBlank()) return configured

    val testerInstructions = project.rootDir.resolve("share/mock/tester-instructions.txt")
    if (testerInstructions.exists()) {
        val installUrl = testerInstructions.readLines()
            .map { it.trim() }
            .firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
        if (!installUrl.isNullOrBlank()) return installUrl
    }

    return "https://play.google.com/apps/testing/com.barkwise.app"
}

val releaseStoreFilePath = readStringConfig(project, "BARKWISE_RELEASE_STORE_FILE", "")
val releaseStorePassword = readStringConfig(project, "BARKWISE_RELEASE_STORE_PASSWORD", "")
val releaseKeyAlias = readStringConfig(project, "BARKWISE_RELEASE_KEY_ALIAS", "")
val releaseKeyPassword = readStringConfig(project, "BARKWISE_RELEASE_KEY_PASSWORD", "")
val hasReleaseSigningConfig = listOf(
    releaseStoreFilePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { it.isNotBlank() }

android {
    namespace = "com.petsocial.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.barkwise.app"
        minSdk = 26
        targetSdk = 35
        versionCode = readIntConfig(project, "BARKWISE_VERSION_CODE", 1)
        versionName = readStringConfig(project, "BARKWISE_VERSION_NAME", "0.1.0")

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
        buildConfigField("String", "PRODUCTION_API_BASE_URL", "\"https://api.barkwiseai.com/\"")
        buildConfigField("Boolean", "ONBOARD_FAKE_SIGN_IN", "false")
        buildConfigField("Boolean", "ONBOARD_SCRIPT_ENABLED", "false")
        buildConfigField("String", "ONBOARD_GROUP_TITLE", "\"\"")
        buildConfigField("String", "ONBOARD_EVENT_TITLE", "\"\"")
        val installPageUrl = readInstallPageUrlConfig(project)
        val escapedInstallPageUrl = installPageUrl.replace("\"", "\\\"")
        buildConfigField("String", "INSTALL_PAGE_URL", "\"$escapedInstallPageUrl\"")
        buildConfigField("String", "APP_SURFACE", "\"owner\"")
        manifestPlaceholders["usesCleartextTraffic"] = "true"
        manifestPlaceholders["appDeepLinkScheme"] = "barkwise"
    }

    signingConfigs {
        if (hasReleaseSigningConfig) {
            create("release") {
                storeFile = file(releaseStoreFilePath)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    flavorDimensions += "environment"
    productFlavors {
        create("staging") {
            dimension = "environment"
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            resValue("string", "app_name", "BarkWise Test")
            val stagingApiUrl = readApiBaseUrlConfig(project, "BARKWISE_STAGING_API_BASE_URL", "http://10.0.2.2:8000/")
            val stagingUseMockData = readBooleanConfig(project, "BARKWISE_TEST_USE_MOCK_DATA", false)
            val stagingAllowDemoLogin = readBooleanConfig(
                project,
                "BARKWISE_TEST_ALLOW_DEMO_LOGIN",
                stagingUseMockData,
            )
            val stagingRequireOtpAuth = readBooleanConfig(
                project,
                "BARKWISE_TEST_REQUIRE_INVITE_OTP_AUTH",
                !stagingAllowDemoLogin,
            )
            val stagingFakeSignIn = readBooleanConfig(
                project,
                "BARKWISE_TEST_ONBOARD_FAKE_SIGN_IN",
                stagingUseMockData || stagingAllowDemoLogin,
            )
            val escapedUrl = stagingApiUrl.replace("\"", "\\\"")
            buildConfigField("String", "API_BASE_URL", "\"$escapedUrl\"")
            buildConfigField("Boolean", "USE_MOCK_DATA", stagingUseMockData.toString())
            buildConfigField("String", "ENVIRONMENT", "\"staging\"")
            buildConfigField("Boolean", "ALLOW_DEMO_LOGIN", stagingAllowDemoLogin.toString())
            buildConfigField("Boolean", "REQUIRE_INVITE_OTP_AUTH", stagingRequireOtpAuth.toString())
            buildConfigField("Boolean", "ONBOARD_FAKE_SIGN_IN", stagingFakeSignIn.toString())
            buildConfigField("Boolean", "ONBOARD_SCRIPT_ENABLED", "true")
            buildConfigField("String", "ONBOARD_GROUP_TITLE", "\"Beach onboarding\"")
            buildConfigField("String", "ONBOARD_EVENT_TITLE", "\"Beach Onboarding Party\"")
            buildConfigField("String", "APP_SURFACE", "\"owner\"")
            manifestPlaceholders["usesCleartextTraffic"] = "true"
            manifestPlaceholders["appDeepLinkScheme"] = "barkwise"
        }
        create("prod") {
            dimension = "environment"
            resValue("string", "app_name", "BarkWise")
            val prodApiUrl = readApiBaseUrlConfig(project, "BARKWISE_PROD_API_BASE_URL", "https://api.barkwiseai.com/")
            val escapedUrl = prodApiUrl.replace("\"", "\\\"")
            buildConfigField("String", "API_BASE_URL", "\"$escapedUrl\"")
            buildConfigField("Boolean", "USE_MOCK_DATA", "false")
            buildConfigField("String", "ENVIRONMENT", "\"prod\"")
            buildConfigField("Boolean", "ALLOW_DEMO_LOGIN", "false")
            buildConfigField("Boolean", "REQUIRE_INVITE_OTP_AUTH", "true")
            buildConfigField("Boolean", "ONBOARD_SCRIPT_ENABLED", "true")
            buildConfigField("String", "APP_SURFACE", "\"owner\"")
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            manifestPlaceholders["appDeepLinkScheme"] = "barkwise"
        }
        create("providerStaging") {
            dimension = "environment"
            applicationIdSuffix = ".provider.staging"
            versionNameSuffix = "-provider-staging"
            resValue("string", "app_name", "BarkWise Provider Test")
            val providerStagingDefault = readStringConfig(project, "BARKWISE_STAGING_API_BASE_URL", "http://10.0.2.2:8000/")
            val providerStagingUseMockData = readBooleanConfig(project, "BARKWISE_PROVIDER_TEST_USE_MOCK_DATA", false)
            val providerStagingFakeSignIn = readBooleanConfig(
                project,
                "BARKWISE_PROVIDER_TEST_ONBOARD_FAKE_SIGN_IN",
                providerStagingUseMockData,
            )
            val providerStagingApiUrl = readApiBaseUrlConfig(
                project,
                "BARKWISE_PROVIDER_STAGING_API_BASE_URL",
                providerStagingDefault,
            )
            val escapedUrl = providerStagingApiUrl.replace("\"", "\\\"")
            buildConfigField("String", "API_BASE_URL", "\"$escapedUrl\"")
            buildConfigField("Boolean", "USE_MOCK_DATA", providerStagingUseMockData.toString())
            buildConfigField("String", "ENVIRONMENT", "\"staging\"")
            buildConfigField("String", "APP_SURFACE", "\"provider\"")
            buildConfigField("Boolean", "ALLOW_DEMO_LOGIN", "false")
            buildConfigField("Boolean", "REQUIRE_INVITE_OTP_AUTH", "true")
            buildConfigField("Boolean", "ONBOARD_FAKE_SIGN_IN", providerStagingFakeSignIn.toString())
            buildConfigField("Boolean", "ONBOARD_SCRIPT_ENABLED", "false")
            buildConfigField("String", "ONBOARD_GROUP_TITLE", "\"Beach onboarding\"")
            buildConfigField("String", "ONBOARD_EVENT_TITLE", "\"Beach Onboarding Party\"")
            manifestPlaceholders["usesCleartextTraffic"] = "true"
            manifestPlaceholders["appDeepLinkScheme"] = "barkwise-provider"
        }
        create("providerProd") {
            dimension = "environment"
            applicationIdSuffix = ".provider"
            versionNameSuffix = "-provider"
            resValue("string", "app_name", "BarkWise Provider")
            val providerProdDefault = readStringConfig(project, "BARKWISE_PROD_API_BASE_URL", "https://api.barkwiseai.com/")
            val providerProdApiUrl = readApiBaseUrlConfig(project, "BARKWISE_PROVIDER_PROD_API_BASE_URL", providerProdDefault)
            val escapedUrl = providerProdApiUrl.replace("\"", "\\\"")
            buildConfigField("String", "API_BASE_URL", "\"$escapedUrl\"")
            buildConfigField("Boolean", "USE_MOCK_DATA", "false")
            buildConfigField("String", "ENVIRONMENT", "\"prod\"")
            buildConfigField("Boolean", "ALLOW_DEMO_LOGIN", "false")
            buildConfigField("Boolean", "REQUIRE_INVITE_OTP_AUTH", "true")
            buildConfigField("String", "APP_SURFACE", "\"provider\"")
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            manifestPlaceholders["appDeepLinkScheme"] = "barkwise-provider"
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

    testOptions {
        animationsDisabled = true
        managedDevices {
            localDevices {
                create("pixel8Api35Atd") {
                    device = "Pixel 8"
                    apiLevel = 35
                    systemImageSource = "aosp-atd"
                }
            }
            groups {
                create("composeSmoke") {
                    targetDevices.add(devices["pixel8Api35Atd"])
                }
            }
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
    implementation("com.google.firebase:firebase-crashlytics-ktx")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.android.gms:play-services-maps:19.0.0")
    implementation("com.google.maps.android:maps-compose:6.4.0")
    implementation("androidx.camera:camera-core:1.4.2")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation("io.coil-kt:coil-compose:2.7.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Keep CLI automation stable: map legacy debug task names to the Test flavor.
tasks.register("installDebug") {
    dependsOn("installStagingDebug")
}

tasks.register("compileDebugKotlin") {
    dependsOn("compileStagingDebugKotlin")
}

tasks.register("managedComposeSmoke") {
    group = "verification"
    description = "Runs staging Compose smoke tests on the managed Pixel 8 API 35 device group."
    dependsOn("composeSmokeGroupStagingDebugAndroidTest")
}

val prodReleaseTasksRequiringSigning = setOf(
    "bundleProdRelease",
    "assembleProdRelease",
    "packageProdReleaseBundle",
    "signProdReleaseBundle",
)
tasks.configureEach {
    if (name in prodReleaseTasksRequiringSigning) {
        doFirst {
            if (!hasReleaseSigningConfig) {
                throw GradleException(
                    "Missing release signing config. Provide BARKWISE_RELEASE_STORE_FILE, " +
                        "BARKWISE_RELEASE_STORE_PASSWORD, BARKWISE_RELEASE_KEY_ALIAS, and " +
                        "BARKWISE_RELEASE_KEY_PASSWORD."
                )
            }
        }
    }
}

// Enable google-services plugin only when local Firebase config is present.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}
