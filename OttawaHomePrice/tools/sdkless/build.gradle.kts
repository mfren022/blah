// Builds a signed Ottawa Home Price APK with only a JDK and internet access: no Android SDK, no Android Gradle
// Plugin. It compiles the same sources as the regular build and reads applicationId, SDK levels,
// version and signing details from app/build.gradle.kts, so the two builds cannot drift apart.
//
//   gradle -p tools/sdkless assembleApk   # build/outputs/OttawaHomePrice.apk
//   gradle -p tools/sdkless test          # engine tests (engine/) + Robolectric UI tests (with screenshots)
//
// Tools come from public mirrors that stay reachable when dl.google.com is not:
//   android.jar  platform 35 stubs (with resources.arsc) from the Sable/android-platforms repository
//   aapt2        the prebuilt binary that apktool publishes to Maven Central
//   D8           r8lib.jar from Google's R8 release bucket
//   apksig       Google's APK signer, from Maven Central
import java.io.FilterOutputStream
import java.io.OutputStream
import java.net.URI
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    repositories {
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:apksig:2.3.0")
    }
}

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.1.21"
}

val projectRoot: File = rootDir.resolve("../..").canonicalFile
val appDir = projectRoot.resolve("app")

// ---- Configuration shared with the Android Gradle Plugin build -------------------------------

val agpScript = appDir.resolve("build.gradle.kts").readText()

fun agpSetting(name: String): String =
    Regex("""\b$name\s*=\s*(?:file\()?"?([^"\s)]+)"?""").find(agpScript)?.groupValues?.get(1)
        ?: error("'$name' not found in app/build.gradle.kts")

val applicationId = agpSetting("applicationId")
val minSdk = agpSetting("minSdk")
val targetSdk = agpSetting("targetSdk")
val versionCode = agpSetting("versionCode")
val versionName = agpSetting("versionName")
val storeFile = appDir.resolve(agpSetting("storeFile"))
val storePassword = agpSetting("storePassword")
val keyAlias = agpSetting("keyAlias")
val keyPassword = agpSetting("keyPassword")

// ---- Tools ------------------------------------------------------------------------------------

val toolCache: File = gradle.gradleUserHomeDir.resolve("caches/sdkless-android")

fun sha256(file: File): String =
    MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

fun registerDownload(name: String, url: String, sha: String, target: File) = tasks.register(name) {
    outputs.file(target)
    doLast {
        if (target.isFile && sha256(target) == sha) return@doLast
        target.parentFile.mkdirs()
        val part = File(target.path + ".part")
        URI(url).toURL().openStream().use { input -> part.outputStream().use { input.copyTo(it) } }
        val actual = sha256(part)
        check(actual == sha) { "Checksum mismatch for $url: expected $sha, got $actual" }
        target.delete()
        check(part.renameTo(target)) { "Could not move $part to $target" }
    }
}

val androidJar = toolCache.resolve("android-35.jar")
val downloadAndroidJar = registerDownload(
    "downloadAndroidJar",
    "https://raw.githubusercontent.com/Sable/android-platforms/master/android-35/android.jar",
    "4566663c3876e022b4fa4ced8c8697c4ab1688267f090114fd92d027b32e619b",
    androidJar,
)

val r8Jar = toolCache.resolve("r8lib-8.13.25.jar")
val downloadR8 = registerDownload(
    "downloadR8",
    "https://storage.googleapis.com/r8-releases/raw/8.13.25/r8lib.jar",
    "0d4f2f68c4fcbc9434eff122f41feb6fb1d98efd2faa530cec1abacab1cea03d",
    r8Jar,
)

val aapt2Tool: Configuration by configurations.creating { isTransitive = false }
dependencies { aapt2Tool("org.apktool:apktool-lib:3.0.3") }

val hostOs: String = System.getProperty("os.name").lowercase()
val aapt2Platform = when {
    hostOs.contains("mac") -> "macosx"
    hostOs.contains("win") -> "windows"
    else -> "linux"
}
val aapt2Dir = layout.buildDirectory.dir("tools/aapt2")
val aapt2 = aapt2Dir.map { it.file(if (aapt2Platform == "windows") "aapt2.exe" else "aapt2").asFile }

val extractAapt2 = tasks.register<Copy>("extractAapt2") {
    from({ zipTree(aapt2Tool.singleFile) }) {
        include("prebuilt/$aapt2Platform/aapt2*")
        eachFile { path = name }
    }
    includeEmptyDirs = false
    into(aapt2Dir)
    filePermissions { unix("rwxr-xr-x") }
}

// ---- Resources --------------------------------------------------------------------------------

val mainDir = appDir.resolve("src/main")
val resDir = mainDir.resolve("res")
val assetsDir = mainDir.resolve("assets")
val intermediates = layout.buildDirectory.dir("intermediates")
val mergedManifest = intermediates.map { it.file("manifest/AndroidManifest.xml") }
val compiledRes = intermediates.map { it.file("res/compiled.zip") }
val resourcesApk = intermediates.map { it.file("res/resources.ap_") }
val rSources = layout.buildDirectory.dir("generated/r")

// Does what AGP's manifest merger does for a single-module app: package, version and SDK levels.
val processManifest = tasks.register("processManifest") {
    val source = mainDir.resolve("AndroidManifest.xml")
    inputs.file(source)
    inputs.property("config", listOf(applicationId, versionCode, versionName, minSdk, targetSdk))
    outputs.file(mergedManifest)
    doLast {
        val manifest = source.readText()
        val tagEnd = Regex("<manifest\\b[^>]*?(/?)>").find(manifest) ?: error("No <manifest> tag")
        val head = manifest.substring(0, tagEnd.range.last).trimEnd() +
            "\n    package=\"$applicationId\"" +
            "\n    android:versionCode=\"$versionCode\"" +
            "\n    android:versionName=\"$versionName\">" +
            "\n    <uses-sdk android:minSdkVersion=\"$minSdk\" android:targetSdkVersion=\"$targetSdk\" />"
        val body = manifest.substring(tagEnd.range.last + 1)
            .replace(Regex("""android:name="\.(\w)"""), "android:name=\"$applicationId.$1")
        val out = mergedManifest.get().asFile
        out.parentFile.mkdirs()
        out.writeText(head + body)
    }
}

val compileResources = tasks.register<Exec>("compileResources") {
    dependsOn(extractAapt2)
    inputs.dir(resDir)
    outputs.file(compiledRes)
    doFirst { compiledRes.get().asFile.parentFile.mkdirs() }
    executable(aapt2.get())
    args("compile", "--dir", resDir, "-o", compiledRes.get().asFile)
}

val linkResources = tasks.register<Exec>("linkResources") {
    dependsOn(compileResources, processManifest, downloadAndroidJar)
    inputs.files(compiledRes, mergedManifest)
    if (assetsDir.isDirectory) inputs.dir(assetsDir)
    outputs.file(resourcesApk)
    outputs.dir(rSources)
    doFirst { delete(rSources) }
    executable(aapt2.get())
    args(
        "link", "-I", androidJar,
        "--manifest", mergedManifest.get().asFile,
        "--min-sdk-version", minSdk, "--target-sdk-version", targetSdk,
        "--java", rSources.get().asFile,
        "-o", resourcesApk.get().asFile,
    )
    if (assetsDir.isDirectory) args("-A", assetsDir)
    args(compiledRes.get().asFile)
}

// ---- Code -------------------------------------------------------------------------------------

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
}

sourceSets {
    main {
        kotlin.srcDirs(mainDir.resolve("java"))
        java.setSrcDirs(listOf(files(rSources).builtBy(linkResources)))
        resources.setSrcDirs(emptyList<File>())
    }
    test {
        kotlin.srcDirs(appDir.resolve("src/test/java"))
        java.setSrcDirs(emptyList<File>())
    }
}

// Like AGP, compile app code against android.jar instead of the JDK, so a JDK-only API is a
// compile error rather than a crash on the phone.
tasks.named<KotlinCompile>("compileKotlin") {
    compilerOptions.noJdk.set(true)
}

val androidJarFiles = files(androidJar).builtBy(downloadAndroidJar)
extra["androidJar"] = androidJarFiles

dependencies {
    // The engine is a module of its own (engine/), as in the Android Gradle Plugin build.
    implementation(project(":engine"))
    compileOnly(androidJarFiles)
    testCompileOnly(androidJarFiles)
    // AGP puts android.jar on the unit-test classpath too; Robolectric resolves types from it.
    testRuntimeOnly(androidJarFiles)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
    // Built from source in androidx-test/, since Google's Maven repository may be unreachable.
    testImplementation(project(":androidx-test"))
}

configurations.all {
    exclude(group = "androidx.test", module = "monitor")
    exclude(group = "androidx.test.espresso", module = "espresso-idling-resource")
}

// What AGP writes for `unitTests.isIncludeAndroidResources = true`.
val robolectricConfig = tasks.register("robolectricConfig") {
    dependsOn(linkResources)
    val outDir = layout.buildDirectory.dir("generated/robolectric")
    outputs.dir(outDir)
    doLast {
        val file = outDir.get().file("com/android/tools/test_config.properties").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            android_merged_manifest=${mergedManifest.get().asFile.invariantSeparatorsPath}
            android_resource_apk=${resourcesApk.get().asFile.invariantSeparatorsPath}
            android_merged_assets=${assetsDir.invariantSeparatorsPath}
            android_custom_package=$applicationId
            """.trimIndent() + "\n",
        )
    }
}
sourceSets.test.get().resources.srcDir(files(layout.buildDirectory.dir("generated/robolectric")).builtBy(robolectricConfig))

tasks.test {
    // Robolectric reads the assets straight from the source folder, so they are an input here.
    if (assetsDir.isDirectory) inputs.dir(assetsDir)
    // Robolectric downloads its Android runtime jars itself; use the same mirror as the build.
    systemProperty("robolectric.dependency.repo.url", "https://maven-central.storage-download.googleapis.com/maven2")
    systemProperty("screenshots.dir", layout.buildDirectory.dir("screenshots").get().asFile.path)
    maxHeapSize = "3g"
    testLogging {
        events("failed")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// ---- Dex, package, sign -----------------------------------------------------------------------

val classesJar = tasks.register<Jar>("classesJar") {
    from(sourceSets.main.get().output.classesDirs)
    archiveFileName.set("classes.jar")
    destinationDirectory.set(intermediates.map { it.dir("classes") })
}

val dexDir = intermediates.map { it.dir("dex") }
val dex = tasks.register<JavaExec>("dex") {
    dependsOn(downloadR8, downloadAndroidJar)
    val runtimeJars = configurations.runtimeClasspath.get()
    inputs.files(classesJar, runtimeJars)
    outputs.dir(dexDir)
    doFirst {
        delete(dexDir)
        dexDir.get().asFile.mkdirs()
    }
    classpath(r8Jar)
    mainClass.set("com.android.tools.r8.D8")
    argumentProviders.add(CommandLineArgumentProvider {
        listOf("--release", "--min-api", minSdk, "--lib", androidJar.path, "--output", dexDir.get().asFile.path) +
            classesJar.get().archiveFile.get().asFile.path +
            runtimeJars.files.map { it.path }
    })
}

/** Counts bytes so local file headers can be padded for 4-byte alignment (what zipalign does). */
class CountingStream(out: OutputStream) : FilterOutputStream(out) {
    var count = 0L
    override fun write(b: Int) { out.write(b); count++ }
    override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len); count += len }
}

/** Zip extra field 0xD935 ("Android ZIP alignment"), sized so the entry data starts aligned. */
fun alignmentExtra(headerOffset: Long, nameLength: Int, alignment: Int = 4): ByteArray {
    val unpadded = headerOffset + 30 + nameLength + 6
    val padding = ((alignment - unpadded % alignment) % alignment).toInt()
    return ByteBuffer.allocate(6 + padding).order(ByteOrder.LITTLE_ENDIAN)
        .putShort(0xD935.toShort()).putShort((2 + padding).toShort()).putShort(alignment.toShort())
        .array()
}

val unsignedApk = intermediates.map { it.file("apk/unsigned.apk") }
val packageApk = tasks.register("packageApk") {
    dependsOn(linkResources, dex)
    inputs.files(resourcesApk, dexDir)
    outputs.file(unsignedApk)
    doLast {
        val out = unsignedApk.get().asFile
        out.parentFile.mkdirs()
        val dexFiles = dexDir.get().asFile.listFiles { f -> f.name.endsWith(".dex") }!!.sortedBy { it.name }
        val time = 1199145600000L // 2008-01-01, a fixed timestamp keeps builds reproducible
        val counter = CountingStream(out.outputStream().buffered())
        ZipOutputStream(counter).use { zip ->
            fun put(name: String, bytes: ByteArray, stored: Boolean) {
                val entry = ZipEntry(name)
                entry.time = time
                if (stored) {
                    entry.method = ZipEntry.STORED
                    entry.size = bytes.size.toLong()
                    entry.compressedSize = bytes.size.toLong()
                    entry.crc = CRC32().apply { update(bytes) }.value
                    entry.extra = alignmentExtra(counter.count, name.toByteArray().size)
                } else {
                    entry.method = ZipEntry.DEFLATED
                }
                zip.putNextEntry(entry)
                zip.write(bytes)
                zip.closeEntry()
            }
            ZipFile(resourcesApk.get().asFile).use { res ->
                val entries = res.entries().toList()
                val manifest = entries.first { it.name == "AndroidManifest.xml" }
                put(manifest.name, res.getInputStream(manifest).readBytes(), stored = false)
                dexFiles.forEach { put(it.name, it.readBytes(), stored = false) }
                for (entry in entries) {
                    if (entry == manifest) continue
                    // resources.arsc must be uncompressed (and aligned) for targetSdk 30+.
                    val stored = entry.method == ZipEntry.STORED || entry.name == "resources.arsc"
                    put(entry.name, res.getInputStream(entry).readBytes(), stored)
                }
            }
        }
    }
}

/** Fails if an uncompressed entry is not 4-byte aligned, which Android rejects for resources.arsc. */
fun checkAlignment(apk: File) {
    val bytes = apk.readBytes()
    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    var eocd = bytes.size - 22
    while (eocd >= 0 && buffer.getInt(eocd) != 0x06054b50) eocd--
    check(eocd >= 0) { "No end of central directory in $apk" }
    val entries = buffer.getShort(eocd + 10).toInt() and 0xFFFF
    var p = buffer.getInt(eocd + 16)
    repeat(entries) {
        check(buffer.getInt(p) == 0x02014b50) { "Bad central directory in $apk" }
        val method = buffer.getShort(p + 10).toInt()
        val nameLength = buffer.getShort(p + 28).toInt() and 0xFFFF
        val extraLength = buffer.getShort(p + 30).toInt() and 0xFFFF
        val commentLength = buffer.getShort(p + 32).toInt() and 0xFFFF
        val local = buffer.getInt(p + 42)
        val name = String(bytes, p + 46, nameLength)
        val dataStart = local + 30 + (buffer.getShort(local + 26).toInt() and 0xFFFF) +
            (buffer.getShort(local + 28).toInt() and 0xFFFF)
        check(method != 0 || dataStart % 4 == 0) { "$name is stored but not 4-byte aligned in $apk" }
        p += 46 + nameLength + extraLength + commentLength
    }
}

val apkFile = layout.buildDirectory.file("outputs/OttawaHomePrice.apk")
tasks.register("assembleApk") {
    group = "build"
    description = "Builds, signs and verifies build/outputs/OttawaHomePrice.apk"
    dependsOn(packageApk)
    inputs.files(unsignedApk, storeFile)
    outputs.file(apkFile)
    doLast {
        val keyStore = KeyStore.getInstance("PKCS12")
        storeFile.inputStream().use { keyStore.load(it, storePassword.toCharArray()) }
        val key = keyStore.getKey(keyAlias, keyPassword.toCharArray()) as PrivateKey
        val chain = keyStore.getCertificateChain(keyAlias).map { it as X509Certificate }
        val signer = com.android.apksig.ApkSigner.SignerConfig.Builder("CERT", key, chain).build()
        val out = apkFile.get().asFile
        out.parentFile.mkdirs()
        com.android.apksig.ApkSigner.Builder(listOf(signer))
            .setInputApk(unsignedApk.get().asFile)
            .setOutputApk(out)
            .setMinSdkVersion(minSdk.toInt())
            // v1 (JAR) signatures only matter below Android 7.0, and minSdk is higher; AGP skips them too.
            .setV1SigningEnabled(false)
            .setV2SigningEnabled(true)
            .build()
            .sign()

        val result = com.android.apksig.ApkVerifier.Builder(out).build().verify()
        check(result.isVerified) { "APK signature does not verify: ${result.errors}" }
        check(result.isVerifiedUsingV2Scheme) { "APK is missing its v2 signature" }
        checkAlignment(out)
        logger.lifecycle("Signed APK (v2, aligned): ${out.path} (${out.length() / 1024} KiB)")
    }
}
