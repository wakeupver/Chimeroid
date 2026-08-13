import com.android.build.gradle.BaseExtension

val projectCompileSdkVersion = 35
val projectBuildToolsVersion = "34.0.0"
val projectMinSdkVersion = 23
val projectTargetSdkVersion = 35

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.7.2")
        classpath("androidx.navigation:navigation-safe-args-gradle-plugin:2.5.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21")
    }
}

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.benmanes.versions)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint.gradle)
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
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
                "com.google.android.gms" -> useVersion(libs.versions.gms.get())
                "org.jetbrains.kotlin" -> {
                    if (requested.name.startsWith("kotlin-stdlib-jre")) {
                        with(requested) {
                            useTarget("$group:${name.replace("jre", "jdk")}:$version")
                        }
                    }
                    useVersion(libs.versions.kotlin.get())
                }
            }
        }
    }
}

subprojects {
    afterEvaluate {
        if (hasProperty("android")) {

            apply(plugin = "org.jlleitschuh.gradle.ktlint")

            extensions.configure(BaseExtension::class.java) {
                compileSdkVersion(projectCompileSdkVersion)
                buildToolsVersion(projectBuildToolsVersion)
                defaultConfig {
                    minSdk = projectMinSdkVersion
                    targetSdk = projectTargetSdkVersion
                    multiDexEnabled = true
                }
                lintOptions {
                    isAbortOnError = true

                    disable("UnusedResources")
                    disable("InvalidPackage")
                    disable("VectorPath")
                    disable("TrustAllX509TrustManager")

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
