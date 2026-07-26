plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("kotlin-parcelize")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.swordfish.touchinput.controller"

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = deps.versions.kotlinExtension
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

dependencies {
    implementation(project(":chimeroid-util"))

    implementation(platform(deps.libs.androidx.compose.composeBom))
    implementation(deps.libs.androidx.compose.geometry)
    implementation(deps.libs.androidx.compose.runtime)
    implementation(deps.libs.miuix.ui)
    implementation(deps.libs.androidx.compose.coreIcons)

    implementation(deps.libs.androidx.appcompat.constraintLayout)
    implementation(deps.libs.androidx.appcompat.appcompat)
    implementation(deps.libs.androidx.lifecycle.commonJava8)
    implementation(deps.libs.material)
    implementation(deps.libs.androidx.preferences.preferencesKtx)
    implementation(deps.libs.flowPreferences)
    implementation(deps.libs.kotlin.serialization)
    implementation(deps.libs.kotlin.serializationJson)

    api(deps.libs.padkit)
    api(deps.libs.collectionsImmutable)

    implementation(kotlin(deps.libs.kotlin.stdlib))

    kapt(deps.libs.androidx.lifecycle.processor)
}
