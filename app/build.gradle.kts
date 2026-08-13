import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    id("kotlin-android")
    id("kotlin-kapt")
    id("kotlin-parcelize")
    id("androidx.navigation.safeargs.kotlin")
    id("kotlinx-serialization")
    alias(libs.plugins.kotlin.compose)
}

android {
    defaultConfig {
        versionCode = 253
        versionName = "1.18.0"
        applicationId = "com.swordfish.chimeroid"

        javaCompileOptions {
            annotationProcessorOptions {
                argument("room.schemaLocation", "$projectDir/schemas")
            }
        }
    }
    flavorDimensions += listOf("opensource")

    productFlavors {
        create("free") {
            dimension = "opensource"
        }
    }

    defaultConfig {
        ndk {
            abiFilters += setOf("arm64-v8a")
        }

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
        kotlinCompilerExtensionVersion = libs.versions.kotlinExtension.get()
    }

    namespace = "com.swordfish.chimeroid"
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        freeCompilerArgs.add("-Xcontext-receivers")
    }
}

dependencies {
    implementation(libs.androidx.navigation.navigationFragment)
    implementation(libs.androidx.navigation.navigationUi)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.material)
    implementation(libs.coil.coil)
    implementation(libs.coil.coilCompose)
    implementation(libs.androidx.appcompat.constraintLayout)
    implementation(libs.androidx.activity.activity)
    implementation(libs.androidx.activity.activityKtx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat.appcompat)
    implementation(libs.androidx.appcompat.recyclerView)
    implementation(libs.androidx.preferences.preferencesKtx)
    implementation(libs.arch.work.runtime)
    implementation(libs.arch.work.runtimeKtx)
    implementation(libs.androidx.lifecycle.commonJava8)
    implementation(libs.androidx.lifecycle.reactiveStreams)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.androidx.lifecycle.viewModelCompose)
    kapt(libs.androidx.lifecycle.processor)

    implementation(libs.androidx.paging.common)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.room.common)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    kapt(libs.androidx.room.compiler)

    implementation(libs.androidx.fragment.fragment)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.ktx.core)
    implementation(libs.androidx.ktx.collection)
    implementation(libs.androidx.documentfile)

    implementation(libs.dagger.android.core)
    implementation(libs.dagger.android.support)
    implementation(libs.dagger.core)
    kapt(libs.dagger.android.processor)
    kapt(libs.dagger.compiler)

    implementation(libs.kotlinxCoroutinesAndroid)
    implementation(libs.okHttp3)
    implementation(libs.okio)
    implementation(libs.retrofit)
    implementation(libs.flowPreferences)
    implementation(libs.harmony)
    implementation(libs.startup)
    implementation(libs.timber)
    implementation(libs.kotlin.serialization)
    implementation(libs.kotlin.serializationJson)
    implementation(kotlin("stdlib"))

    implementation(platform(libs.androidx.compose.composeBom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.constraintLayout)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.geometry)
    implementation(libs.androidx.compose.unit)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.coreIcons)
    implementation(libs.androidx.compose.extendedIcons)
    implementation(libs.androidx.compose.toolingPreview)
    debugImplementation(libs.androidx.compose.tooling)
    implementation(libs.androidx.compose.accompanist.systemUiController)
    implementation(libs.androidx.compose.accompanist.navigationMaterial)
    implementation(libs.androidx.compose.accompanist.drawablePainter)
    implementation(libs.composeHtmlText)
    implementation(libs.composeSettings.uiTiles)
    implementation(libs.composeSettings.uiTilesExtended)
    implementation(libs.composeSettings.diskStorage)
    implementation(libs.composeSettings.memoryStorage)

    implementation(libs.padkit)
    implementation(libs.collectionsImmutable)

    implementation(project(":libretrodroid"))
}
