// The engine (core/) as its own module, as in the Android Gradle Plugin build, so the app code is
// compiled against it across a module boundary (no smart casts on its properties, for one).
plugins {
    id("org.jetbrains.kotlin.jvm")
}

val coreDir: File = rootDir.resolve("../../core").canonicalFile

sourceSets {
    main {
        kotlin.setSrcDirs(listOf(coreDir.resolve("src/main/kotlin")))
        java.setSrcDirs(emptyList<File>())
        resources.setSrcDirs(emptyList<File>())
    }
    test {
        kotlin.setSrcDirs(listOf(coreDir.resolve("src/test/kotlin")))
        java.setSrcDirs(emptyList<File>())
        resources.setSrcDirs(emptyList<File>())
    }
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
    systemProperty("data.dir", rootDir.resolve("../../app/src/main/assets/data").canonicalPath)
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
