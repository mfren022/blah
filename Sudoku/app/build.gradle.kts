plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ultimatesudoku.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ultimatesudoku.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 17
        versionName = "1.13"
    }

    // One shared key so every build (Android Studio, CI or tools/sdkless) installs over the previous
    // one and keeps saved games and statistics. This key is only for sideloading; use a private key
    // for a Play Store release.
    signingConfigs {
        create("sideload") {
            storeFile = file("signing/sideload.keystore")
            storePassword = "ultimateSudoku"
            keyAlias = "sudoku"
            keyPassword = "ultimateSudoku"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("sideload")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("sideload")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}

dependencies {
    implementation(project(":core"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
}
