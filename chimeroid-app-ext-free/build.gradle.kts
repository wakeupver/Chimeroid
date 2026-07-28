plugins {
    id("com.android.library")
}

android {
    namespace = "com.swordfish.chimeroid.ext"
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    implementation(project(":chimeroid-util"))
    implementation(project(":chimeroid-shared"))

    implementation(deps.libs.retrofit)
    implementation(deps.libs.kotlinxCoroutinesAndroid)
}
