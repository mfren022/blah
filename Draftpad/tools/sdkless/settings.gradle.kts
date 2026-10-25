// Builds the Draftpad APK without an Android SDK install. See "Without the Android SDK" in README.md.
pluginManagement {
    repositories {
        // Google's mirror of Maven Central first: repo.maven.apache.org rate-limits busy networks.
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
    }
}
rootProject.name = "draftpad-sdkless"
include(":androidx-test")
