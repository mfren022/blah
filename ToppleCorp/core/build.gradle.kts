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

/**
 * Regenerates the 150 campaign levels into the app's assets:
 *   ./gradlew :core:generateLevels
 */
tasks.register<JavaExec>("generateLevels") {
    group = "topple"
    description = "Generates campaign level JSON files into app/src/main/assets/levels"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.topplecorp.core.tools.GenerateLevelsKt")
    args(rootProject.file("app/src/main/assets/levels").absolutePath)
}
