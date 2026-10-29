// Robolectric needs androidx.test:monitor and espresso-idling-resource, which are only published to
// Google's Maven repository. This project compiles both from their open-source release (AndroidX Test
// 1.7.0 stable: monitor 1.8.0, espresso 3.7.0), fetched with git from github.com/android/android-test.
// src/shims stands in for the annotations and tracing calls those sources import.
plugins {
    id("org.jetbrains.kotlin.jvm")
}

val revision = "de417c55bd5226087b97a5121fae1d2ea1d58718" // tag axt_2025_07_31_stable
val checkout: File = gradle.gradleUserHomeDir.resolve("caches/sdkless-android/android-test-$revision")
val monitorDir = checkout.resolve("runner/monitor/java")
val idlingDir = checkout.resolve("espresso/idling_resource/java")

fun git(vararg args: String) {
    val process = ProcessBuilder(listOf("git", "-C", checkout.path) + args).inheritIO().start()
    check(process.waitFor() == 0) { "git ${args.joinToString(" ")} failed" }
}

val fetchSources = tasks.register("fetchSources") {
    val marker = checkout.resolve(".complete")
    outputs.file(marker)
    doLast {
        if (marker.isFile) return@doLast
        checkout.deleteRecursively()
        checkout.mkdirs()
        git("init", "-q")
        git("remote", "add", "origin", "https://github.com/android/android-test.git")
        git("sparse-checkout", "set", "runner/monitor/java", "espresso/idling_resource/java")
        git("fetch", "-q", "--depth", "1", "--filter=blob:none", "origin", revision)
        git("checkout", "-q", "FETCH_HEAD")
        marker.writeText(revision)
    }
}

// MonitoringInstrumentation calls hidden Instrumentation methods. It compiles against a stub class
// that declares them (hidden/) and ships with an empty class of the same name (runtime/), so at run
// time the calls reach the real framework methods.
val hiddenApi = "androidx/test/internal/runner/hidden/ExposedInstrumentationApi.java"
val runtimeApi = "androidx/test/internal/runner/runtime/ExposedInstrumentationApi.java"

val syncMain = tasks.register<Sync>("syncMainSources") {
    dependsOn(fetchSources)
    from(monitorDir) {
        include("**/*.java", "**/*.kt")
        exclude(hiddenApi, runtimeApi)
        // Desugaring helpers that the upstream build also leaves out.
        exclude("**/ActivityInvoker\$\$CC.java", "**/ActivityInvokerDesugar.java")
    }
    from(idlingDir) { include("**/*.java") }
    into(layout.buildDirectory.dir("sources/main"))
}
val syncHidden = tasks.register<Sync>("syncHiddenSources") {
    dependsOn(fetchSources)
    from(monitorDir) { include(hiddenApi) }
    into(layout.buildDirectory.dir("sources/hidden"))
}
val syncRuntime = tasks.register<Sync>("syncRuntimeSources") {
    dependsOn(fetchSources)
    from(monitorDir) { include(runtimeApi) }
    into(layout.buildDirectory.dir("sources/runtime"))
}

val androidJar = rootProject.extra["androidJar"] as FileCollection

sourceSets {
    val shims = create("shims")
    val hidden = create("hidden") {
        java.srcDir(files(layout.buildDirectory.dir("sources/hidden")).builtBy(syncHidden))
        compileClasspath += shims.output
    }
    create("runtimeApi") {
        java.srcDir(files(layout.buildDirectory.dir("sources/runtime")).builtBy(syncRuntime))
        compileClasspath += shims.output
    }
    main {
        val sources = files(layout.buildDirectory.dir("sources/main")).builtBy(syncMain)
        java.setSrcDirs(listOf(sources))
        kotlin.setSrcDirs(listOf(sources))
        compileClasspath += shims.output + hidden.output
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
    "shimsCompileOnly"(androidJar)
    "hiddenCompileOnly"(androidJar)
    "runtimeApiCompileOnly"(androidJar)
    compileOnly(androidJar)
    compileOnly("com.google.errorprone:error_prone_annotations:2.36.0")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:-options")
    options.isWarnings = false
}

tasks.jar {
    from(sourceSets["runtimeApi"].output, sourceSets["shims"].output)
}
