import com.android.build.gradle.BaseExtension

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
    id("com.github.ben-manes.versions") version "0.51.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.4.0"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.0"
    id("org.jetbrains.kotlin.android") version deps.versions.kotlin apply false
    id("com.android.application") version "8.9.1" apply false
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

subprojects {
    afterEvaluate {
        if (hasProperty("android")) {
            // BaseExtension is common parent for application, library and test modules
            apply(plugin = "org.jlleitschuh.gradle.ktlint")

            extensions.configure(BaseExtension::class.java) {
                compileSdkVersion(deps.android.compileSdkVersion)
                buildToolsVersion(deps.android.buildToolsVersion)
                defaultConfig {
                    minSdk = deps.android.minSdkVersion
                    targetSdk = deps.android.targetSdkVersion
                    multiDexEnabled = true
                }
                lintOptions {
                    isAbortOnError = true
                    // https://issuetracker.google.com/issues/63150366
                    disable("UnusedResources")
                    disable("InvalidPackage")
                    disable("VectorPath")
                    disable("TrustAllX509TrustManager")
                    // androidx.lifecycle's bundled NonNullableMutableLiveDataDetector crashes
                    // (IncompatibleClassChangeError against Lint's Kotlin Analysis API) on
                    // AGP 8.7.2 -- the detector itself fails to execute, so this isn't
                    // silencing a real finding.
                    disable("NullSafeMutableLiveData")
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
