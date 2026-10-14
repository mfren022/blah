import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.mfren022.drivein"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.mfren022.drivein"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    // CarUxRestrictionsManager, used to pause video when an Android Automotive OS car starts
    // moving. It is only present on cars, so it is not required at install time.
    useLibrary("android.car", false)

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
