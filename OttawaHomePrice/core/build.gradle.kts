plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    // The engine tests read the data the app ships, straight from its assets.
    systemProperty("data.dir", rootProject.file("app/src/main/assets/data").path)
}
