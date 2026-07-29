plugins {
    id("com.android.library")
    id("com.android.legacy-kapt")
}

dependencies {
    implementation(project(":chimeroid-util"))
    implementation(project(":chimeroid-shared"))

    implementation(deps.libs.androidx.room.runtime)
    implementation(deps.libs.androidx.room.ktx)
    implementation(deps.libs.dagger.core)
    implementation(deps.libs.kotlinxCoroutinesAndroid)

    kapt(deps.libs.androidx.room.compiler)
    kapt(deps.libs.dagger.compiler)
}

android {
    resourcePrefix = "libretrodb_"
    namespace = "com.swordfish.chimeroid.metadata.libretrodb"
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}
