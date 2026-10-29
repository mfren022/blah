// Builds the Ottawa Home Price APK without an Android SDK install. See README.md in this folder.
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
rootProject.name = "ottawa-home-price-sdkless"
include(":androidx-test", ":engine")
