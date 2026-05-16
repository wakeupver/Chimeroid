plugins {
    id("com.android.library")
    id("kotlin-android")
}

android {
    namespace = "com.swordfish.libretrodroid"

    kotlinOptions {
        jvmTarget = "17"
    }

    defaultConfig {
        ndk {
            // arm64-only: same as before
            abiFilters += setOf("arm64-v8a")
        }
        externalNativeBuild {
            cmake {
                arguments(
                    "-DANDROID_STL=c++_static",
                    "-DANDROID_ARM_NEON=TRUE",
                    "-DCMAKE_BUILD_TYPE=Release",
                    // AAudio requires API 26+. Set the NDK compile target to 26
                    // so AAudio symbols are visible. The app's declared minSdk (23)
                    // is unaffected — this only governs which NDK APIs are available
                    // to the native library at compile time.
                    "-DANDROID_PLATFORM=android-26"
                )
                cppFlags(
                    "-O3",
                    "-march=armv8-a+simd",
                    "-mtune=cortex-a55",
                    "-ffast-math",
                    "-fomit-frame-pointer",
                    "-funroll-loops",
                    "-fvisibility=hidden",
                    "-flto=thin"
                )
                cFlags(
                    "-O3",
                    "-march=armv8-a+simd",
                    "-mtune=cortex-a55",
                    "-ffast-math",
                    "-fomit-frame-pointer",
                    "-funroll-loops",
                    "-fvisibility=hidden",
                    "-flto=thin"
                )
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    externalNativeBuild {
        cmake {
            version = "3.22.1"
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
}

dependencies {
    implementation(deps.libs.androidx.lifecycle.runtime)
    // Oboe removed: using AAudio directly via NDK
}
