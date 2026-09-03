@file:Suppress("UnstableApiUsage")

import java.util.Properties
import com.android.build.api.dsl.ApplicationExtension

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// Read version properties
val versionPropertiesFile = file("version.properties")
if (!versionPropertiesFile.exists()) {
    throw GradleException("version.properties not found in ${versionPropertiesFile.absolutePath}")
}
val versionProperties = Properties()
versionPropertiesFile.inputStream().use {
    versionProperties.load(it)
}

val appVersionCode = versionProperties.getProperty("APP_VERSION_CODE", "1").toInt()
val appVersionName: String = versionProperties.getProperty("APP_VERSION_NAME", "0.1.0")

val enableAsan = project.findProperty("enableAsan") == "true"

configure<ApplicationExtension> {
    namespace = "com.thivyanstudios.hark"
    compileSdk = 37

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    defaultConfig {
        applicationId = "com.thivyanstudios.hark"
        minSdk = 28
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Default status for debug builds
        buildConfigField("String", "BUILD_STATUS", "\"Release-Candidate\"")

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
        
        externalNativeBuild {
            cmake {
                arguments("-DANDROID_STL=c++_shared")
                if (enableAsan) {
                    arguments("-DENABLE_ASAN=ON")
                    cppFlags("-fsanitize=address", "-fno-omit-frame-pointer")
                }
            }
        }
    }

    buildTypes {
        debug {
            if (enableAsan) {
                // ASan requires debuggable to be true for wrap.sh to work
                isDebuggable = true
                signingConfig = signingConfigs.getByName("debug")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // For builds from the MAIN branch
            buildConfigField("String", "BUILD_STATUS", "\"Stable-Release\"")
        }
        create("beta") {
            initWith(getByName("release"))
            matchingFallbacks += "release"
            // For builds from the BETA branch
            buildConfigField("String", "BUILD_STATUS", "\"Pre-Release\"")
        }
    }

    packaging {
        jniLibs {
            if (enableAsan) {
                useLegacyPackaging = true
                keepDebugSymbols.add("**/*.so")
            }
        }
    }

    sourceSets {
        getByName("debug") {
            if (enableAsan) {
                jniLibs.srcDirs("src/debug/asanJniLibs")
                resources.srcDirs("src/debug/asanResources")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        viewBinding = false
        compose = true
        buildConfig = true
        prefab = true // Needed for Oboe
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    // AppCompat is needed for Theme.Material3.DayNight.NoActionBar
    implementation(libs.androidx.appcompat)
    // Material is needed for Material3 themes in XML
    implementation(libs.material)
    
    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    
    // Compose
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Splash Screen
    implementation(libs.androidx.core.splashscreen)
    
    // Networking
    implementation(libs.okhttp)
    
    // Oboe
    implementation(libs.oboe)

    // Sherpa-ONNX STT
    implementation("com.github.k2-fsa:sherpa-onnx:v1.13.0")

    testImplementation(libs.junit)
    testImplementation(libs.turbine)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
