import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-kapt")
    id("androidx.navigation.safeargs.kotlin")
    id("kotlinx-serialization")

    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    defaultConfig {
        versionCode = 253
        versionName = "1.18.0" // Always remember to update Cores Tag!
        applicationId = "com.swordfish.chimeroid"
    }
    flavorDimensions += listOf("opensource")

    // Since some dependencies are closed source we make a completely free as in free speech variant.

    productFlavors {
        create("free") {
            dimension = "opensource"
        }
    }

    defaultConfig {
        ndk {
            abiFilters += setOf("arm64-v8a")
        }
        // chimeroid-shared / chimeroid-touchinput ship ~30 translated locales; this app's
        // own strings (res/values, res/values-en-rUS) are only maintained in English, so
        // drop the rest here to avoid shipping unused translated resources.
        resourceConfigurations += setOf("en")
    }

    splits {
        abi {
            isEnable = true
            reset()
            isUniversalApk = false
        }
    }

    packaging {
        jniLibs {
            // Stripping created some issues with some libretro cores such as ppsspp
            keepDebugSymbols += setOf("*/*/*_libretro_android.so")
            useLegacyPackaging = true
        }
        resources {
            excludes += setOf("META-INF/DEPENDENCIES", "META-INF/library_release.kotlin_module")
        }
    }

    signingConfigs {
        maybeCreate("debug").apply {
            storeFile = file("$rootDir/debug.keystore")
        }

        maybeCreate("release").apply {
            storeFile = file(System.getenv("STORE_FILE") ?: "$rootDir/release.jks")
            keyAlias = System.getenv("KEY_ALIAS") ?: "chimeroid"
            storePassword = System.getenv("STORE_PASSWORD") ?: "chimeroid123"
            keyPassword = System.getenv("KEY_PASSWORD") ?: "chimeroid123"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs["release"]
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
            resValue("string", "chimeroid_name", "Chimeroid")
        }
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
            resValue("string", "chimeroid_name", "ChimeroidDebug")
        }
    }

    lint {
        disable += setOf("MissingTranslation", "ExtraTranslation", "EnsureInitializerMetadata")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = deps.versions.kotlinExtension
    }

    namespace = "com.swordfish.chimeroid"
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(project(":chimeroid-util"))
    implementation(project(":chimeroid-shared"))
    implementation(project(":chimeroid-metadata-libretro-db"))
    implementation(project(":chimeroid-touchinput"))

    "freeImplementation"(project(":chimeroid-app-ext-free"))

    implementation(deps.libs.androidx.navigation.navigationFragment)
    implementation(deps.libs.androidx.navigation.navigationUi)
    implementation(deps.libs.androidx.navigation.compose)
    implementation(deps.libs.material)
    implementation(deps.libs.coil.coil)
    implementation(deps.libs.coil.coilCompose)
    implementation(deps.libs.androidx.appcompat.constraintLayout)
    implementation(deps.libs.androidx.activity.activity)
    implementation(deps.libs.androidx.activity.activityKtx)
    implementation(deps.libs.androidx.activity.compose)
    implementation(deps.libs.androidx.appcompat.appcompat)
    implementation(deps.libs.androidx.preferences.preferencesKtx)
    implementation(deps.libs.arch.work.runtime)
    implementation(deps.libs.arch.work.runtimeKtx)
    implementation(deps.libs.androidx.lifecycle.commonJava8)
    implementation(deps.libs.androidx.lifecycle.reactiveStreams)
    implementation(deps.libs.androidx.lifecycle.runtimeCompose)

    kapt(deps.libs.androidx.lifecycle.processor)

    implementation(deps.libs.androidx.appcompat.recyclerView)
    implementation(deps.libs.androidx.paging.common)
    implementation(deps.libs.androidx.paging.runtime)
    implementation(deps.libs.androidx.room.common)
    implementation(deps.libs.androidx.room.runtime)
    implementation(deps.libs.androidx.room.ktx)
    implementation(deps.libs.dagger.android.core)
    implementation(deps.libs.dagger.android.support)
    implementation(deps.libs.dagger.core)
    implementation(deps.libs.kotlinxCoroutinesAndroid)
    implementation(deps.libs.okHttp3)
    implementation(deps.libs.okio)
    implementation(deps.libs.retrofit)
    implementation(deps.libs.flowPreferences)
    implementation(deps.libs.androidx.documentfile)
    implementation(deps.libs.harmony)
    implementation(deps.libs.startup)
    implementation(deps.libs.kotlin.serialization)
    implementation(deps.libs.kotlin.serializationJson)

    implementation(platform(deps.libs.androidx.compose.composeBom))
    // MaterialExpressiveTheme / MotionScheme / MaterialShapes are still
    // @ExperimentalMaterial3ExpressiveApi and only ship on the 1.5.0-alpha train —
    // composeBom above still resolves material3 to the 1.4.0 stable line, so this
    // explicit version pin overrides the BOM for this one artifact. Deliberately
    // scoped to chimeroid-app only (chimeroid-touchinput keeps the BOM's stable
    // version via deps.libs.androidx.compose.material3) to contain alpha-channel
    // risk to the module that actually needs the new APIs.
    implementation("androidx.compose.material3:material3:1.5.0-alpha24")
    implementation(deps.libs.androidx.compose.constraintLayout)
    debugImplementation(deps.libs.androidx.compose.tooling)
    implementation(deps.libs.androidx.compose.toolingPreview)
    implementation(deps.libs.androidx.compose.extendedIcons)
    implementation(deps.libs.androidx.compose.accompanist.systemUiController)
    implementation(deps.libs.androidx.compose.accompanist.navigationMaterial)
    implementation(deps.libs.androidx.compose.accompanist.drawablePainter)
    implementation(deps.libs.androidx.paging.compose)
    implementation(deps.libs.androidx.lifecycle.viewModelCompose)
    implementation(deps.libs.composeHtmlText)

    implementation(deps.libs.composeSettings.uiTiles)
    implementation(deps.libs.composeSettings.uiTilesExtended)
    implementation(deps.libs.composeSettings.diskStorage)
    implementation(deps.libs.composeSettings.memoryStorage)

    implementation(project(":libretrodroid"))

    // Uncomment this when using a local aar file.
    // implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))

    kapt(deps.libs.dagger.android.processor)
    kapt(deps.libs.dagger.compiler)
}