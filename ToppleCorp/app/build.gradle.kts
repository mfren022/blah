plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.topplecorp.game"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.topplecorp.game"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // One shared key so every build (local or CI) installs over the previous one and keeps progress.
    // This key is only for sideloading; use a private key for a Play Store release.
    signingConfigs {
        create("sideload") {
            storeFile = file("signing/sideload.keystore")
            storePassword = "toppleCorp"
            keyAlias = "topple"
            keyPassword = "toppleCorp"
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

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":core"))
}
