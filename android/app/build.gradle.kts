import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing is read from keystore.properties in the repository root.
// The file is never committed; CI writes it from repository secrets.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
val hasKeystore = keystoreProps.getProperty("storeFile") != null

android {
    namespace = "org.kcmcelia.bookofmormon"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.kcmcelia.bookofmormon"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        // Replace these three with the real URLs after the feed is published.
        buildConfigField(
            "String", "FEED_URL",
            "\"https://REPLACE-ME.github.io/mormon-cantonese/feed.xml\""
        )
        buildConfigField(
            "String", "LATEST_URL",
            "\"https://REPLACE-ME.github.io/mormon-cantonese/latest.json\""
        )
        buildConfigField(
            "String", "RELEASES_URL",
            "\"https://github.com/REPLACE-ME/mormon-cantonese/releases/latest\""
        )
    }

    signingConfigs {
        if (hasKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

// ---------------------------------------------------------------------------------------
// Keep the bundled manifest in step with the generated one.
//
// The app reads assets/manifest.json; build/manifest.json is the source of truth produced by
// tools/build_manifest.py. Copying at build time means an APK can never ship a stale library,
// and a missing manifest fails the build instead of silently packaging an old one.
// ---------------------------------------------------------------------------------------
val generatedManifest = rootProject.file("../build/manifest.json")
val bundledManifest = layout.projectDirectory.file("src/main/assets/manifest.json")

val syncContentManifest by tasks.registering {
    group = "build"
    description = "Copies build/manifest.json into app assets."
    inputs.files(generatedManifest).optional()
    outputs.file(bundledManifest)

    doLast {
        val target = bundledManifest.asFile
        if (!generatedManifest.exists()) {
            if (!target.exists()) {
                throw GradleException(
                    "No manifest found. Run `python tools/build_manifest.py` in the repository " +
                        "root first."
                )
            }
            logger.warn("build/manifest.json is missing; using the committed asset copy.")
            return@doLast
        }
        val unchanged = target.exists() &&
            generatedManifest.readBytes().contentEquals(target.readBytes())
        if (!unchanged) {
            target.parentFile.mkdirs()
            generatedManifest.copyTo(target, overwrite = true)
            logger.lifecycle(
                "Synced build/manifest.json into assets (${generatedManifest.length()} bytes)"
            )
        }
    }
}

tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(syncContentManifest) }
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    dependsOn(syncContentManifest)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)

    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
}
