import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.androidx.baselineprofile)
}

val keystoreProperties = Properties().apply {
    val propsFile = rootProject.file("keystore.properties")
    if (propsFile.exists()) {
        propsFile.inputStream().use { load(it) }
    }
}

// M6 recomposition audit only: ./gradlew :app:assembleRelease -PcomposeCompilerReports=true
// writes stability/skippability reports to build/compose_metrics and
// build/compose_reports. Off by default so it adds no cost to normal builds.
if (project.hasProperty("composeCompilerReports")) {
    composeCompiler {
        reportsDestination = layout.buildDirectory.dir("compose_reports")
        metricsDestination = layout.buildDirectory.dir("compose_metrics")
    }
}

android {
    namespace = "com.bookmark"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.bookmark"
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (keystoreProperties.containsKey("storeFile")) {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            optimization {
                enable = true
            }
        }
    }

    // The baseline-profile plugin auto-creates `nonMinifiedRelease` (profile
    // generation) and `benchmarkRelease` (macrobenchmark) build types. Sign
    // them with the same key as `release` -- otherwise they default to the
    // debug key and every install collides with whatever release-signed
    // build is already on the test device (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`).
    buildTypes.matching { it.name == "nonMinifiedRelease" || it.name == "benchmarkRelease" }
        .configureEach { signingConfig = signingConfigs.getByName("release") }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    // MigrationTestHelper loads the exported schemas from the test APK's assets,
    // so the directory KSP writes them to has to be an androidTest asset source.
    sourceSets.getByName("androidTest") {
        assets.srcDir("$projectDir/schemas")
    }

    // The baseline-profile plugin auto-creates `nonMinifiedRelease` (profile
    // generation) and `benchmarkRelease` (macrobenchmark) build types. Both
    // unconditionally inherit all of `src/release/java` in addition to their
    // own (undocumented, discovered empirically -- there is no `sourceSets`
    // wiring for this in this file), which is why StrictModeInit's no-op
    // twin in `src/release/java` covers them for free and needs no per-type
    // duplicate. It's also why BenchmarkSeed.kt lives in `src/main`
    // unconditionally instead of split like StrictModeInit -- see its doc
    // comment for why that split doesn't work for `benchmarkRelease`.
}

// Export Room schemas so migrations stay reviewable in version control.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.palette.ktx)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.okhttp)
    implementation(libs.jsoup)

    // M6: lets the baseline profile generated by :macrobenchmark install on
    // first run instead of waiting for the platform's own JIT sampling.
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":macrobenchmark"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Drives the spec 7.2 request policy -- redirect cap, UA switching, ranged
    // GET, content-type gating, early abort -- with no network.
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}

// Everything here is optional and defaults are used as-is: connected-device
// generation (the default) is what this machine needs anyway -- there's no
// emulator/Gradle Managed Device here, only the physical dev device (API 36,
// which meets the API 33+ floor for non-rooted on-device collection).
baselineProfile {
}
