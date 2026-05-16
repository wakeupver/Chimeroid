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
        // AAudio requires API 26. Override minSdk for this native-only module.
        // The parent app's declared minSdk (23) is what Google Play enforces;
        // this minSdk only governs which NDK APIs this library can call.
        minSdk = 26

        ndk {
            abiFilters += setOf("arm64-v8a")
        }
        externalNativeBuild {
            cmake {
                arguments(
                    "-DANDROID_STL=c++_static",
                    "-DANDROID_ARM_NEON=TRUE",
                    "-DCMAKE_BUILD_TYPE=Release",
                    // Explicit NDK API level — belt-and-suspenders with CMakeLists
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
}
