import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryPlugin

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath(deps.plugins.android)
        classpath(deps.plugins.navigationSafeArgs)
        classpath(deps.plugins.kotlinGradlePlugin)
    }
}

plugins {
    id("org.jetbrains.kotlin.jvm") version deps.versions.kotlin
    id("com.github.ben-manes.versions") version "0.56.0"
    id("org.jetbrains.kotlin.plugin.serialization") version deps.versions.kotlin
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("com.android.application") version deps.versions.agp apply false
    id("com.android.legacy-kapt") version deps.versions.agp apply false
    id("org.jetbrains.kotlin.plugin.compose") version deps.versions.kotlin apply false
}

allprojects {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        maven { setUrl("https://jitpack.io") }
    }

    configurations.all {
        resolutionStrategy.eachDependency {
            when (requested.group) {
                "com.google.android.gms" -> useVersion(deps.versions.gms)
                "org.jetbrains.kotlin" -> {
                    if (requested.name.startsWith("kotlin-stdlib-jre")) {
                        with(requested) {
                            useTarget("$group:${name.replace("jre", "jdk")}:$version")
                        }
                    }
                    useVersion(deps.versions.kotlin)
                }
            }
        }
    }
}

// https://issuetracker.google.com/issues/63150366
val disabledLintChecks = setOf(
    "UnusedResources",
    "InvalidPackage",
    "VectorPath",
    "TrustAllX509TrustManager",
    // androidx.lifecycle's bundled NonNullableMutableLiveDataDetector has previously
    // crashed (IncompatibleClassChangeError against Lint's Kotlin Analysis API) rather
    // than reporting a real finding; kept disabled.
    "NullSafeMutableLiveData",
)

subprojects {
    afterEvaluate {
        if (hasProperty("android")) {
            apply(plugin = "org.jlleitschuh.gradle.ktlint")
        }

        plugins.withType<AppPlugin> {
            extensions.configure<ApplicationExtension> {
                compileSdk = deps.android.compileSdkVersion
                buildToolsVersion = deps.android.buildToolsVersion
                defaultConfig {
                    minSdk = deps.android.minSdkVersion
                    targetSdk = deps.android.targetSdkVersion
                }
                lint {
                    abortOnError = true
                    disable += disabledLintChecks
                }
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
            }
        }

        plugins.withType<LibraryPlugin> {
            extensions.configure<LibraryExtension> {
                compileSdk = deps.android.compileSdkVersion
                buildToolsVersion = deps.android.buildToolsVersion
                defaultConfig {
                    minSdk = deps.android.minSdkVersion
                }
                lint {
                    abortOnError = true
                    disable += disabledLintChecks
                }
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
            }
        }
    }

    configurations {
        all {
            exclude(group = "com.google.code.findbugs", module = "jsr305")
        }
    }
}

tasks {
    "clean"(Delete::class) {
        delete(layout.buildDirectory)
    }
}
